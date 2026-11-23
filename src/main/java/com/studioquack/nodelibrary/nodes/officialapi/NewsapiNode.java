package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.officialapi.DataNodeSupport.DataHttpException;
import com.studioquack.nodelibrary.nodes.officialapi.DataNodeSupport.HttpResult;
import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NewsAPI.org search node ({@code /v2/everything} and {@code /v2/top-headlines}).
 *
 * <p>{@code api_key} is required, sent only as {@code X-Api-Key}, and is never
 * logged or returned on the output port.
 *
 * <p>Thread-safe: immutable {@code templateId}; HTTP goes through the shared
 * {@link DataNodeSupport} client. Failures raise {@link studioquack.StudioQuackError}.
 */
public final class NewsapiNode implements NodeHandler {

    /** Bundled definition {@code _id}; {@code SQ_TEMPLATE_ID_NEWSAPI} may override at register. */
    static final String DEFAULT_ID = "6c601741-a3ce-4a08-86c8-a684cfda7b58";

    private static final String DEFAULT_BASE = "https://newsapi.org/v2";
    private static final String MISSING_KEY =
            "Missing NewsAPI API key. Provide it in node parameters (api_key).";
    private static final String MISSING_QUERY =
            "At least one of query, q_in_title, sources, or domains is required.";

    private final String templateId;

    public NewsapiNode() {
        this(DEFAULT_ID);
    }

    public NewsapiNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId.trim();
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * One NewsAPI search. Never logs {@code helperToken} or {@code api_key}.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper unused) {
        HelperClient logs = DataNodeSupport.logs(request);
        int timeout = 25;
        String apiKey = "";
        try {
            Map<String, Object> cfg = DataNodeSupport.configParams(request);
            Map<String, Object> in = DataNodeSupport.inputPort(request);

            apiKey = DataNodeSupport.str(cfg.get("api_key")).trim();
            String base = rstripSlash(DataNodeSupport.str(cfg.get("base_url")).trim());
            if (base.isEmpty()) {
                base = DEFAULT_BASE;
            }
            String defaultLanguage = DataNodeSupport.firstNonBlank(cfg, "default_language");
            if (defaultLanguage.isEmpty()) {
                defaultLanguage = "en";
            }
            int defaultPageSize = DataNodeSupport.positiveInt(cfg.get("default_page_size"), 20);
            String defaultSortBy = DataNodeSupport.firstNonBlank(cfg, "default_sort_by");
            if (defaultSortBy.isEmpty()) {
                defaultSortBy = "relevancy";
            }
            timeout = DataNodeSupport.positiveInt(cfg.get("timeout_seconds"), 25);

            // Blank key is a config error — do not call NewsAPI.
            if (apiKey.isEmpty()) {
                return DataSupport.invalidInput(MISSING_KEY);
            }

            // Schema marks query required; runtime matches Python (any of four is enough).
            String query = DataNodeSupport.firstNonBlank(in, "query", "q");
            String sources = DataNodeSupport.str(in.get("sources")).trim();
            String domains = DataNodeSupport.str(in.get("domains")).trim();
            String qInTitle = DataNodeSupport.str(in.get("q_in_title")).trim();
            if (query.isEmpty() && sources.isEmpty() && domains.isEmpty() && qInTitle.isEmpty()) {
                return DataSupport.invalidInput(MISSING_QUERY);
            }

            String endpoint = DataNodeSupport.firstNonBlank(in, "endpoint");
            if (endpoint.isEmpty()) {
                endpoint = "everything";
            }
            String language = DataNodeSupport.firstNonBlank(in, "language");
            if (language.isEmpty()) {
                language = defaultLanguage;
            }
            String sortBy = DataNodeSupport.firstNonBlank(in, "sort_by");
            if (sortBy.isEmpty()) {
                sortBy = defaultSortBy;
            }
            int pageSize = DataNodeSupport.positiveInt(in.get("page_size"), defaultPageSize);
            int page = DataNodeSupport.positiveInt(in.get("page"), 1);

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Api-Key", apiKey);
            headers.put("User-Agent", DataNodeSupport.USER_AGENT);

            boolean headlines = "top-headlines".equals(endpoint);
            String url = base + (headlines ? "/top-headlines" : "/everything");
            Map<String, Object> params = headlines
                    ? headlinesParams(in, query, sources, language, pageSize, page)
                    : everythingParams(in, query, qInTitle, sources, domains, language, sortBy, pageSize, page);

            logs.log(Map.of("message", "newsapi start", "endpoint", headlines ? "top-headlines" : "everything"));
            HttpResult r = DataNodeSupport.get(url, headers, params, timeout);

            if (r.status == 429) {
                return limitError();
            }
            if (r.status == 401) {
                return invalidKeyError();
            }
            if (!r.ok()) {
                return DataSupport.fail(
                        "NewsAPI HTTP Error: HTTP " + r.status + " for url: " + r.url);
            }

            Object parsedJson = DataNodeSupport.parseJson(r.body);
            if (!(parsedJson instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("NewsAPI response is not a JSON object");
            }
            Map<String, Object> data = DataNodeSupport.asObject(parsedJson);
            List<Object> articles = parseArticles(data.get("articles"));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "ok");
            out.put("total_results", DataNodeSupport.toInt(data.get("totalResults"), 0));
            out.put("articles_returned", articles.size());
            out.put("articles", articles);
            logs.log(Map.of("message", "newsapi done", "articles_returned", articles.size()));
            return DataNodeSupport.outputPorts(out);
        } catch (DataHttpException e) {
            logs.log("newsapi transport: " + e.getClass().getSimpleName());
            if (e.timeout) {
                return DataSupport.fail("NewsAPI request timed out after " + timeout + "s.");
            }
            return DataSupport.fail("NewsAPI HTTP Error: " + redact(e.getMessage(), apiKey));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            logs.log("newsapi internal: " + e.getClass().getSimpleName());
            return DataSupport.fail(
                    "Error: " + e.getClass().getSimpleName() + ": " + redact(e.getMessage(), apiKey));
        }
    }

    /** GET {@code /top-headlines} query string (pageSize/page always; others when non-blank). */
    private static Map<String, Object> headlinesParams(Map<String, Object> in, String query,
                                                       String sources, String language,
                                                       int pageSize, int page) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pageSize", pageSize);
        params.put("page", page);
        putIfPresent(params, "q", query);
        putIfPresent(params, "sources", sources);
        putIfPresent(params, "category", in.get("category"));
        putIfPresent(params, "country", in.get("country"));
        putIfPresent(params, "language", language);
        return params;
    }

    /** GET {@code /everything} query string (language/sortBy/pageSize/page always). */
    private static Map<String, Object> everythingParams(Map<String, Object> in, String query,
                                                        String qInTitle, String sources, String domains,
                                                        String language, String sortBy,
                                                        int pageSize, int page) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("language", language);
        params.put("sortBy", sortBy);
        params.put("pageSize", pageSize);
        params.put("page", page);
        putIfPresent(params, "q", query);
        putIfPresent(params, "qInTitle", qInTitle);
        putIfPresent(params, "from", in.get("from"));
        putIfPresent(params, "to", in.get("to"));
        putIfPresent(params, "sources", sources);
        putIfPresent(params, "domains", domains);
        return params;
    }

    private static void putIfPresent(Map<String, Object> params, String key, Object value) {
        String s = DataNodeSupport.str(value).trim();
        if (!s.isEmpty()) {
            params.put(key, s);
        }
    }

    private static List<Object> parseArticles(Object raw) {
        List<Object> parsed = new ArrayList<>();
        for (Object item : DataNodeSupport.asArray(raw)) {
            Map<String, Object> a = DataNodeSupport.asObject(item);
            Map<String, Object> source = DataNodeSupport.asObject(a.get("source"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", strip(a.get("title")));
            row.put("description", strip(a.get("description")));
            row.put("url", strip(a.get("url")));
            row.put("source", strip(source.get("name")));
            row.put("author", strip(a.get("author")));
            row.put("published_at", strip(a.get("publishedAt")));
            row.put("content", strip(a.get("content")));
            parsed.add(row);
        }
        return parsed;
    }

    private static Map<String, Object> limitError() {
        return DataSupport.fail(
                "NewsAPI free tier daily limit exceeded. HTTP 429. Resume tomorrow.");
    }

    private static Map<String, Object> invalidKeyError() {
        return DataSupport.fail("Invalid NewsAPI API key. HTTP 401.");
    }

    private static String rstripSlash(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') {
            end--;
        }
        return s.substring(0, end);
    }

    private static String strip(Object o) {
        return DataNodeSupport.str(o).trim();
    }

    /** Never echo the API key in a thrown message if a transport message contains it. */
    private static String redact(String message, String apiKey) {
        String msg = message == null ? "" : message;
        if (apiKey != null && !apiKey.isEmpty() && msg.contains(apiKey)) {
            return msg.replace(apiKey, "***");
        }
        return msg;
    }
}
