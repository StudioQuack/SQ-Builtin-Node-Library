package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAlex scholarly catalog node ({@code 58e4aa32-8830-4a65-b5df-849823282972}).
 *
 * <p>In-process GET client for {@code https://api.openalex.org}: search works, fetch one
 * work, or search authors. Thread-safe singleton — the only instance field is the
 * resolved template id. Secrets ({@code api_key}) come only from configurationParameters,
 * are never logged or returned, and are passed through {@link DataSupport#redact} when
 * interpolating error text. Reply is always keyed by OUTPUT port {@code output}.
 */
public final class OpenalexNode implements NodeHandler {

    public static final String DEFAULT_TEMPLATE_ID = "58e4aa32-8830-4a65-b5df-849823282972";

    private static final String API_BASE = "https://api.openalex.org";
    private static final int DEFAULT_TIMEOUT = 25;
    private static final int DEFAULT_PER_PAGE = 50;
    private static final int MAX_PER_PAGE = 100;
    private static final int ABSTRACT_MAX_CHARS = 2000;
    private static final String ACTION_SEARCH_WORKS = "search_works";
    private static final String ACTION_GET_WORK = "get_work";
    private static final String ACTION_SEARCH_AUTHORS = "search_authors";
    private static final String DEFAULT_SORT = "relevance_score:desc";

    private final String templateId;

    public OpenalexNode() {
        this.templateId = DataSupport.templateId("SQ_TEMPLATE_ID_OPENALEX", DEFAULT_TEMPLATE_ID);
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Failures raise a BPMN error via {@link DataSupport#fail}.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> req, StudioQuackHelper helper) {
        String apiKey = null;
        try {
            if (req == null) {
                return DataSupport.invalidInput("request body must be a JSON object");
            }
            Map<String, Object> cfg = DataSupport.readConfig(req);
            Map<String, Object> in = DataSupport.readInput(req, helper);
            apiKey = DataSupport.trimToNull(DataSupport.cfgStr(cfg, "api_key", ""));
            // email is still a config field for backward compatibility but is ignored:
            // OpenAlex retired mailto= in February 2026.
            int timeout = DataSupport.cfgInt(cfg, "timeout_seconds", DEFAULT_TIMEOUT);
            String action = DataSupport.inStr(in, "action", ACTION_SEARCH_WORKS);
            String query = DataSupport.inStrAny(in, null, "query", "q");
            String workId = DataSupport.inStr(in, "work_id", null);
            String filter = DataSupport.inStr(in, "filter", null);
            String sort = DataSupport.inStr(in, "sort", DEFAULT_SORT);
            int perPage = DataSupport.inInt(in, "per_page", DEFAULT_PER_PAGE);
            int page = DataSupport.inInt(in, "page", 1);

            return switch (action) {
                case ACTION_SEARCH_WORKS -> searchWorks(query, filter, sort, perPage, page,
                        apiKey, timeout);
                case ACTION_GET_WORK -> getWork(workId, apiKey, timeout);
                case ACTION_SEARCH_AUTHORS -> searchAuthors(query, filter, perPage, page,
                        apiKey, timeout);
                default -> DataSupport.invalidInput("Unknown action: " + action
                        + ". Use 'search_works', 'get_work', or 'search_authors'.");
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            String detail = e.getMessage() == null ? "" : e.getMessage();
            return DataSupport.fail(DataSupport.redact(
                    "Error: " + e.getClass().getSimpleName() + ": " + detail, apiKey));
        }
    }

    private static Map<String, Object> searchWorks(String query, String filter, String sort,
                                                   int perPage, int page, String apiKey,
                                                   int timeout) {
        if (query == null) {
            return DataSupport.invalidInput("query is required for search_works action.");
        }
        Map<String, String> params = authParams(apiKey);
        params.put("search", query);
        params.put("per_page", String.valueOf(Math.min(perPage, MAX_PER_PAGE)));
        params.put("page", String.valueOf(page));
        if (sort != null) {
            params.put("sort", sort);
        }
        if (filter != null) {
            params.put("filter", filter);
        }
        JsonOrError got = apiGet("/works", params, timeout, apiKey);
        if (got.failed()) {
            return got.ports;
        }
        List<Map<String, Object>> works = new ArrayList<>();
        for (Object row : EmulatorUtil.asList(got.json.get("results"))) {
            if (row instanceof Map<?, ?>) {
                works.add(shapeWork(EmulatorUtil.asMap(row)));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", ACTION_SEARCH_WORKS);
        out.put("query", query);
        out.put("total_results", metaCount(got.json));
        out.put("works_returned", works.size());
        out.put("page", page);
        out.put("works", works);
        return DataSupport.ports(out);
    }

    private static Map<String, Object> getWork(String workId, String apiKey, int timeout) {
        if (workId == null) {
            return DataSupport.invalidInput("work_id is required. Use OpenAlex ID, DOI URL, or PMID.");
        }
        JsonOrError got = apiGet("/works/" + DataSupport.urlEncode(workId),
                authParams(apiKey), timeout, apiKey);
        if (got.failed()) {
            return got.ports;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", ACTION_GET_WORK);
        out.put("work", got.json);
        return DataSupport.ports(out);
    }

    private static Map<String, Object> searchAuthors(String query, String filter, int perPage,
                                                     int page, String apiKey, int timeout) {
        if (query == null) {
            return DataSupport.invalidInput("query is required for search_authors action.");
        }
        Map<String, String> params = authParams(apiKey);
        params.put("search", query);
        params.put("per_page", String.valueOf(Math.min(perPage, MAX_PER_PAGE)));
        params.put("page", String.valueOf(page));
        if (filter != null) {
            params.put("filter", filter);
        }
        JsonOrError got = apiGet("/authors", params, timeout, apiKey);
        if (got.failed()) {
            return got.ports;
        }
        List<Map<String, Object>> authors = new ArrayList<>();
        for (Object row : EmulatorUtil.asList(got.json.get("results"))) {
            if (row instanceof Map<?, ?>) {
                authors.add(shapeAuthor(EmulatorUtil.asMap(row)));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", ACTION_SEARCH_AUTHORS);
        out.put("query", query);
        out.put("total_results", metaCount(got.json));
        out.put("authors_returned", authors.size());
        out.put("authors", authors);
        return DataSupport.ports(out);
    }

    /**
     * Sends {@code api_key} when configured. Never sends {@code mailto} — OpenAlex
     * retired the polite pool in February 2026 and ignores that query param.
     */
    static Map<String, String> authParams(String apiKey) {
        Map<String, String> params = new LinkedHashMap<>();
        if (apiKey != null) {
            params.put("api_key", apiKey);
        }
        return params;
    }

    private static JsonOrError apiGet(String path, Map<String, String> params, int timeout, String apiKey) {
        String qs = DataSupport.query(params);
        String url = API_BASE + path + (qs.isEmpty() ? "" : "?" + qs);
        DataSupport.HttpResp resp = DataSupport.get(url, DataSupport.headers(), timeout);
        if (resp.error() != null) {
            return JsonOrError.fail(DataSupport.redact("OpenAlex API Error: " + resp.error(), apiKey));
        }
        if (!resp.ok()) {
            return JsonOrError.fail(DataSupport.redact(
                    httpError(resp.status(), resp.body()), apiKey));
        }
        Map<String, Object> json = resp.jsonObject();
        if (json == null) {
            return JsonOrError.fail(DataSupport.redact(
                    "OpenAlex API Error: response was not a JSON object", apiKey));
        }
        return JsonOrError.ok(json);
    }

    /** 403/429 append current-docs hints; no legacy 10k/1k quota text. */
    static String httpError(int status, String body) {
        String msg = "OpenAlex API Error: " + DataSupport.httpStatusMessage(status, body);
        if (status == 403) {
            msg += " | API key may be invalid. Get a free key at openalex.org/settings/api.";
        } else if (status == 429) {
            msg += " | Rate limited (daily budget or 100 requests/s).";
        }
        return msg;
    }

    private static Map<String, Object> shapeWork(Map<String, Object> item) {
        List<String> authors = new ArrayList<>();
        // First-seen unique institutions (Python used an unordered set).
        Set<String> institutionSet = new LinkedHashSet<>();
        for (Object row : EmulatorUtil.asList(item.get("authorships"))) {
            Map<String, Object> authorship = EmulatorUtil.asMap(row);
            String name = DataSupport.trimToNull(EmulatorUtil.str(
                    EmulatorUtil.asMap(authorship.get("author")).get("display_name")));
            if (name != null) {
                authors.add(name);
            }
            for (Object inst : EmulatorUtil.asList(authorship.get("institutions"))) {
                String iname = DataSupport.trimToNull(EmulatorUtil.str(
                        EmulatorUtil.asMap(inst).get("display_name")));
                if (iname != null) {
                    institutionSet.add(iname);
                }
            }
        }
        List<String> institutions = new ArrayList<>();
        for (String n : institutionSet) {
            if (institutions.size() >= 10) {
                break;
            }
            institutions.add(n);
        }
        String doi = EmulatorUtil.str(item.get("doi")).replace("https://doi.org/", "");
        String abstractText = reconstructAbstract(item.get("abstract_inverted_index"));
        if (abstractText.length() > ABSTRACT_MAX_CHARS) {
            abstractText = abstractText.substring(0, ABSTRACT_MAX_CHARS);
        }
        Map<String, Object> work = new LinkedHashMap<>();
        work.put("openalex_id", EmulatorUtil.str(item.get("id")));
        work.put("doi", doi);
        work.put("title", EmulatorUtil.str(item.get("title")));
        work.put("authors", authors);
        work.put("institutions", institutions);
        work.put("publication_date", EmulatorUtil.str(item.get("publication_date")));
        work.put("publication_year", item.get("publication_year"));
        work.put("source", EmulatorUtil.str(
                EmulatorUtil.dig(item, "primary_location", "source", "display_name")));
        work.put("type", EmulatorUtil.str(item.get("type")));
        work.put("cited_by_count", EmulatorUtil.toInt(item.get("cited_by_count"), 0));
        work.put("is_oa", EmulatorUtil.isTruthy(EmulatorUtil.dig(item, "open_access", "is_oa")));
        work.put("oa_url", EmulatorUtil.str(EmulatorUtil.dig(item, "open_access", "oa_url")));
        work.put("topics", firstDisplayNames(item.get("topics"), 5));
        work.put("keywords", firstDisplayNames(item.get("keywords"), 10));
        work.put("abstract", abstractText);
        work.put("referenced_works_count", EmulatorUtil.asList(item.get("referenced_works")).size());
        return work;
    }

    private static Map<String, Object> shapeAuthor(Map<String, Object> item) {
        List<String> affiliations = new ArrayList<>();
        for (Object row : EmulatorUtil.asList(item.get("affiliations"))) {
            if (affiliations.size() >= 5) {
                break;
            }
            affiliations.add(EmulatorUtil.str(
                    EmulatorUtil.asMap(EmulatorUtil.asMap(row).get("institution")).get("display_name")));
        }
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("openalex_id", EmulatorUtil.str(item.get("id")));
        author.put("display_name", EmulatorUtil.str(item.get("display_name")));
        author.put("works_count", EmulatorUtil.toInt(item.get("works_count"), 0));
        author.put("cited_by_count", EmulatorUtil.toInt(item.get("cited_by_count"), 0));
        author.put("h_index", EmulatorUtil.toInt(EmulatorUtil.dig(item, "summary_stats", "h_index"), 0));
        author.put("affiliations", affiliations);
        author.put("topics", firstDisplayNames(item.get("topics"), 5));
        return author;
    }

    /**
     * OpenAlex stores abstracts as word → [positions]. Sort by position and join.
     */
    private static String reconstructAbstract(Object raw) {
        Map<String, Object> index = EmulatorUtil.asMap(raw);
        if (index.isEmpty()) {
            return "";
        }
        List<AbsToken> tokens = new ArrayList<>();
        for (Map.Entry<String, Object> e : index.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            for (Object pos : EmulatorUtil.asList(e.getValue())) {
                tokens.add(new AbsToken(EmulatorUtil.toInt(pos, Integer.MAX_VALUE), e.getKey()));
            }
        }
        tokens.sort(Comparator.comparingInt(t -> t.pos));
        StringBuilder sb = new StringBuilder();
        for (AbsToken t : tokens) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(t.word);
        }
        return sb.toString();
    }

    private static List<String> firstDisplayNames(Object raw, int limit) {
        List<String> names = new ArrayList<>();
        for (Object row : EmulatorUtil.asList(raw)) {
            if (names.size() >= limit) {
                break;
            }
            names.add(EmulatorUtil.str(EmulatorUtil.asMap(row).get("display_name")));
        }
        return names;
    }

    private static Object metaCount(Map<String, Object> data) {
        Object count = EmulatorUtil.dig(data, "meta", "count");
        if (count instanceof Number) {
            return count;
        }
        return EmulatorUtil.toInt(count, 0);
    }

    private record AbsToken(int pos, String word) {
    }

    private record JsonOrError(Map<String, Object> json, Map<String, Object> ports) {
        static JsonOrError ok(Map<String, Object> json) {
            return new JsonOrError(json, null);
        }

        static JsonOrError fail(String message) {
            return new JsonOrError(null, DataSupport.fail(message));
        }

        boolean failed() {
            return ports != null;
        }
    }
}
