package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * DEV.to Search (Forem REST API v0). Public article lists; optional {@code api-key} header.
 *
 * <p>Thread-safe: only {@link #templateId} is instance state and it is final. HTTP goes through
 * the shared {@link DataNodeSupport} client. Never logs or returns {@code api_key}.
 */
public final class DevtoNode implements NodeHandler {

    static final String DEFAULT_ID = "9f73791e-776d-44ef-8c5e-3e459b776410";

    private static final String API_BASE = "https://dev.to/api";

    /** Python {@code re.split(r"[,\s+]+", ...)} — commas, whitespace, or plus. */
    private static final Pattern TAG_SPLIT = Pattern.compile("[,\\s+]+");

    private final String templateId;

    public DevtoNode() {
        this(DataNodeSupport.templateId("devto", DEFAULT_ID));
    }

    public DevtoNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * One Forem GET. Failures stay on {@code output.error} (no error OUTPUT port).
     * {@code _helper} is unused; logs go through {@link DataNodeSupport#logs}.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper _helper) {
        HelperClient logs = DataNodeSupport.logs(request);
        String apiKey = "";
        try {
            Map<String, Object> cfg = DataNodeSupport.configParams(request);
            Map<String, Object> in = DataNodeSupport.inputPort(request);
            apiKey = DataNodeSupport.str(cfg.get("api_key")).trim();
            int timeout = intOr(cfg.get("timeout_seconds"), 25);
            int defaultPerPage = intOr(cfg.get("default_per_page"), 30);

            String action = DataNodeSupport.str(in.get("action")).trim();
            if (action.isEmpty() || "null".equalsIgnoreCase(action)) {
                action = "articles";
            }

            int perPage = intOr(in.get("per_page"), defaultPerPage);
            int page = intOr(in.get("page"), 1);
            Map<String, String> headers = requestHeaders(apiKey);

            if ("articles".equals(action)) {
                return articles(in, headers, perPage, page, timeout, logs);
            }
            if ("latest".equals(action)) {
                return latest(headers, perPage, page, timeout);
            }
            return DataSupport.invalidInput(
                    "Unknown action: " + action + ". Use articles or latest.");
        } catch (DataNodeSupport.DataHttpException e) {
            String msg = "DEV.to API Error: " + redact(e.getMessage(), apiKey);
            logs.log(msg);
            return DataSupport.fail(msg);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            String msg = "Error: " + e.getClass().getSimpleName() + ": " + redact(e.getMessage(), apiKey);
            logs.log(e.getClass().getSimpleName());
            return DataSupport.fail(msg);
        }
    }

    /**
     * GET {@code /api/articles}. Tag / state pairing matches Forem v0 and the Python node.
     * {@code state} is sent only when {@code username} is also present.
     */
    private static Map<String, Object> articles(Map<String, Object> in, Map<String, String> headers,
                                                int perPage, int page, int timeout, HelperClient logs) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("per_page", Math.min(perPage, 1000));
        params.put("page", page);

        if (DataNodeSupport.present(in.get("tags"))) {
            List<String> normalized = normalizeTags(in.get("tags"));
            if (!normalized.isEmpty()) {
                params.put("tags", String.join(", ", normalized));
            }
        } else if (DataNodeSupport.present(in.get("tag"))) {
            List<String> normalized = normalizeTags(in.get("tag"));
            if (normalized.size() == 1) {
                params.put("tag", normalized.get(0));
            } else if (normalized.size() > 1) {
                params.put("tags", String.join(", ", normalized));
            }
        }

        if (DataNodeSupport.present(in.get("tags_exclude"))) {
            List<String> excluded = normalizeTags(in.get("tags_exclude"));
            if (!excluded.isEmpty()) {
                params.put("tags_exclude", String.join(", ", excluded));
            }
        }

        if (DataNodeSupport.present(in.get("username"))) {
            params.put("username", DataNodeSupport.str(in.get("username")).trim());
        }

        if (DataNodeSupport.present(in.get("top"))) {
            params.put("top", intOr(in.get("top"), 0));
        }

        Object state = in.get("state");
        boolean hasUser = DataNodeSupport.present(in.get("username"));
        if (DataNodeSupport.present(state) && hasUser) {
            params.put("state", DataNodeSupport.str(state).trim());
        } else if (DataNodeSupport.present(state) && !hasUser) {
            logs.log("[WARN] 'state=" + DataNodeSupport.str(state).trim()
                    + "' ignored: Forem API allows 'state' only in conjunction with 'username'.");
        }

        if (DataNodeSupport.present(in.get("collection_id"))) {
            params.put("collection_id", intOr(in.get("collection_id"), 0));
        }

        return fetch(API_BASE + "/articles", headers, params, timeout, true);
    }

    /** GET {@code /api/articles/latest}. No tag / username / top / state filters. Cap 100. */
    private static Map<String, Object> latest(Map<String, String> headers, int perPage, int page, int timeout) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("per_page", Math.min(perPage, 100));
        params.put("page", page);
        return fetch(API_BASE + "/articles/latest", headers, params, timeout, false);
    }

    private static Map<String, Object> fetch(String url, Map<String, String> headers,
                                             Map<String, Object> query, int timeout, boolean full) {
        DataNodeSupport.HttpResult http = DataNodeSupport.get(url, headers, query, timeout);
        if (!http.ok()) {
            return DataSupport.fail(
                    "DEV.to API Error: HTTP " + http.status + " for url: " + http.url);
        }
        List<Object> raw = DataNodeSupport.asArray(DataNodeSupport.parseJson(http.body));
        List<Object> articles = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?>) {
                articles.add(mapArticle(DataNodeSupport.asObject(item), full));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("articles_returned", articles.size());
        out.put("articles", articles);
        return DataNodeSupport.outputPorts(out);
    }

    /** User-Agent always; {@code api-key} only when a key is present — never Authorization. */
    private static Map<String, String> requestHeaders(String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", DataNodeSupport.USER_AGENT);
        if (DataNodeSupport.present(apiKey)) {
            headers.put("api-key", apiKey);
        }
        return headers;
    }

    /**
     * Split on commas, whitespace, or plus; lowercase; drop empties.
     * A JSON list is joined with commas first so designer arrays still work.
     */
    static List<String> normalizeTags(Object raw) {
        if (raw == null) {
            return List.of();
        }
        String s;
        if (raw instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                String t = DataNodeSupport.str(item).trim();
                if (!t.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(t);
                }
            }
            s = sb.toString();
        } else {
            s = DataNodeSupport.str(raw).trim();
        }
        if (s.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : TAG_SPLIT.split(s)) {
            String t = part.toLowerCase(Locale.ROOT).strip();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * {@code full} is the articles action (canonical_url, reading_time_minutes, author_username).
     * {@code latest} omits those three fields — same as Python.
     */
    private static Map<String, Object> mapArticle(Map<String, Object> a, boolean full) {
        Map<String, Object> user = DataNodeSupport.asObject(a.get("user"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.get("id"));
        row.put("title", orDefault(a, "title", ""));
        row.put("description", orDefault(a, "description", ""));
        row.put("url", orDefault(a, "url", ""));
        if (full) {
            row.put("canonical_url", orDefault(a, "canonical_url", ""));
        }
        row.put("published_at", orDefault(a, "published_at", ""));
        row.put("positive_reactions_count", orDefault(a, "positive_reactions_count", 0));
        row.put("comments_count", orDefault(a, "comments_count", 0));
        if (full) {
            row.put("reading_time_minutes", orDefault(a, "reading_time_minutes", 0));
        }
        row.put("tag_list", orDefault(a, "tag_list", List.of()));
        row.put("author_name", DataNodeSupport.str(user.get("name")));
        if (full) {
            row.put("author_username", DataNodeSupport.str(user.get("username")));
        }
        return row;
    }

    private static Object orDefault(Map<String, Object> m, String key, Object fallback) {
        Object v = m.get(key);
        return v == null ? fallback : v;
    }

    private static int intOr(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = DataNodeSupport.str(raw).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return fallback;
        }
        return Integer.parseInt(s);
    }

    private static String redact(String message, String apiKey) {
        String m = message == null ? "" : message;
        if (apiKey != null && apiKey.length() >= 4 && m.contains(apiKey)) {
            return m.replace(apiKey, "***");
        }
        return m;
    }
}
