package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;

import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Semantic Scholar Academic Graph node (template {@code 6509231e-2d5e-4a8c-9734-cac633074330}).
 *
 * <p>Ports papers, citations, references, authors, and batch ids from
 * {@code https://api.semanticscholar.org/graph/v1}. Optional config {@code api_key}
 * is sent only as HTTP header {@code x-api-key} and is never logged. Thread-safe:
 * no mutable instance state besides the resolved template id. Failures return
 * {@link DataSupport#fail(String)} (a BPMN error for the error boundary event).
 */
public final class SemanticScholarNode implements NodeHandler {

    public static final String DEFAULT_TEMPLATE_ID = "6509231e-2d5e-4a8c-9734-cac633074330";

    private static final String API_BASE = "https://api.semanticscholar.org/graph/v1";
    private static final String PAPER_FIELDS =
            "title,authors,year,abstract,citationCount,influentialCitationCount,externalIds,"
                    + "venue,openAccessPdf,tldr,publicationDate,journal,fieldsOfStudy,"
                    + "s2FieldsOfStudy,isOpenAccess,referenceCount";
    private static final String AUTHOR_FIELDS =
            "name,affiliations,paperCount,citationCount,hIndex,externalIds";
    private static final String CITE_FIELDS =
            "title,authors,year,citationCount,externalIds,venue";
    private static final int DEFAULT_TIMEOUT = 25;
    private static final int SEARCH_LIMIT_MAX = 100;
    private static final int CITE_LIMIT_MAX = 1000;
    private static final int BATCH_MAX = 500;

    private final String templateId;

    public SemanticScholarNode() {
        this.templateId = DataSupport.templateId("SQ_TEMPLATE_ID_SEMANTIC_SCHOLAR", DEFAULT_TEMPLATE_ID);
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        String apiKey = "";
        try {
            if (request == null) {
                return DataSupport.invalidInput("request body must be a JSON object");
            }
            Map<String, Object> cfg = DataSupport.readConfig(request);
            Map<String, Object> in = DataSupport.readInput(request, helper);
            apiKey = DataSupport.cfgStr(cfg, "api_key", "");
            int timeout = DataSupport.cfgInt(cfg, "timeout_seconds", DEFAULT_TIMEOUT);
            Map<String, String> headers = authHeaders(apiKey);
            String action = DataSupport.inStr(in, "action", "search");
            String fields = DataSupport.inStr(in, "fields", PAPER_FIELDS);
            int limit = DataSupport.inInt(in, "limit", 100);
            int offset = DataSupport.inInt(in, "offset", 0);
            return switch (action) {
                case "search" -> doSearch(in, fields, limit, offset, headers, timeout, apiKey);
                case "paper_details" -> doPaper(in, fields, headers, timeout, apiKey);
                case "citations" -> doCiteGraph(in, "citations", "citingPaper", limit, offset,
                        headers, timeout, apiKey);
                case "references" -> doCiteGraph(in, "references", "citedPaper", limit, offset,
                        headers, timeout, apiKey);
                case "author" -> doAuthor(in, headers, timeout, apiKey);
                case "batch" -> doBatch(in, fields, headers, timeout, apiKey);
                default -> DataSupport.invalidInput("Unknown action: " + action
                        + ". Use 'search', 'paper_details', 'citations', 'references', 'author', or 'batch'.");
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            String detail = e.getMessage() == null ? "" : e.getMessage();
            return DataSupport.fail("Error: " + e.getClass().getSimpleName() + ": "
                    + DataSupport.redact(detail, apiKey));
        }
    }

    /** Keyword search: GET /paper/search. */
    private static Map<String, Object> doSearch(Map<String, Object> in, String fields, int limit,
                                                int offset, Map<String, String> headers, int timeout,
                                                String apiKey) {
        String query = DataSupport.inStrAny(in, null, "query", "q");
        if (query == null) {
            return DataSupport.invalidInput("query is required for search action.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("fields", fields);
        params.put("limit", String.valueOf(Math.min(limit, SEARCH_LIMIT_MAX)));
        params.put("offset", String.valueOf(offset));
        String year = DataSupport.inStr(in, "year", null);
        if (year != null) {
            params.put("year", year);
        }
        String venue = DataSupport.inStr(in, "venue", null);
        if (venue != null) {
            params.put("venue", venue);
        }
        // Honored even when absent from the definition schema (Python openAccessPdf).
        if (DataSupport.inBool(in, "openAccessPdf", false)) {
            params.put("openAccessPdf", "");
        }
        DataSupport.HttpResp resp = DataSupport.get(API_BASE + "/paper/search?" + DataSupport.query(params),
                headers, timeout);
        Map<String, Object> fail = failIfNotOk(resp, apiKey);
        if (fail != null) {
            return fail;
        }
        Map<String, Object> data = resp.jsonObject();
        if (data == null) {
            return DataSupport.fail("Semantic Scholar API Error: expected JSON object");
        }
        List<Object> papers = asObjectList(data.get("data"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "search");
        out.put("query", query);
        out.put("total_results", num(data.get("total"), 0));
        out.put("papers_returned", papers.size());
        out.put("offset", num(data.get("offset"), 0));
        out.put("papers", papers);
        return DataSupport.ports(out);
    }

    /** Full paper record: GET /paper/{encoded}. */
    private static Map<String, Object> doPaper(Map<String, Object> in, String fields,
                                               Map<String, String> headers, int timeout, String apiKey) {
        String paperId = normalizePaperId(DataSupport.inStr(in, "paper_id", null));
        if (paperId == null || paperId.isBlank()) {
            return DataSupport.invalidInput(
                    "paper_id is required. Use DOI:xxx, ArXiv:xxx, PMID:xxx, or S2 paper ID.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("fields", fields);
        DataSupport.HttpResp resp = DataSupport.get(
                API_BASE + "/paper/" + DataSupport.urlEncode(paperId) + "?" + DataSupport.query(params),
                headers, timeout);
        Map<String, Object> fail = failIfNotOk(resp, apiKey);
        if (fail != null) {
            return fail;
        }
        Map<String, Object> paper = resp.jsonObject();
        if (paper == null || paperPayloadEmpty(paper)) {
            return DataSupport.fail(
                    "Semantic Scholar API returned no paper fields for id " + paperId
                            + ". Check the id format (e.g. ArXiv:1706.03762) or retry after rate limiting.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "paper_details");
        out.put("paper", paper);
        return DataSupport.ports(out);
    }

    /** Citations or references list, unwrapping citingPaper / citedPaper. */
    private static Map<String, Object> doCiteGraph(Map<String, Object> in, String action, String nestedKey,
                                                   int limit, int offset, Map<String, String> headers,
                                                   int timeout, String apiKey) {
        String paperId = normalizePaperId(DataSupport.inStr(in, "paper_id", null));
        if (paperId == null || paperId.isBlank()) {
            return DataSupport.invalidInput("paper_id is required for " + action + " action.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("fields", CITE_FIELDS);
        params.put("limit", String.valueOf(Math.min(limit, CITE_LIMIT_MAX)));
        params.put("offset", String.valueOf(offset));
        DataSupport.HttpResp resp = DataSupport.get(
                API_BASE + "/paper/" + DataSupport.urlEncode(paperId) + "/" + action + "?"
                        + DataSupport.query(params),
                headers, timeout);
        Map<String, Object> fail = failIfNotOk(resp, apiKey);
        if (fail != null) {
            return fail;
        }
        Map<String, Object> data = resp.jsonObject();
        if (data == null) {
            return DataSupport.fail("Semantic Scholar API Error: expected JSON object");
        }
        List<Object> items = unwrapNested(data.get("data"), nestedKey);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", action);
        out.put("paper_id", paperId);
        out.put(action + "_returned", items.size());
        out.put(action, items);
        return DataSupport.ports(out);
    }

    /** Author profile: GET /author/{encoded} with AUTHOR_FIELDS (input fields ignored). */
    private static Map<String, Object> doAuthor(Map<String, Object> in, Map<String, String> headers,
                                                int timeout, String apiKey) {
        String authorId = DataSupport.inStr(in, "author_id", null);
        if (authorId == null) {
            return DataSupport.invalidInput("author_id is required for author action.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("fields", AUTHOR_FIELDS);
        DataSupport.HttpResp resp = DataSupport.get(
                API_BASE + "/author/" + DataSupport.urlEncode(authorId) + "?" + DataSupport.query(params),
                headers, timeout);
        Map<String, Object> fail = failIfNotOk(resp, apiKey, "author");
        if (fail != null) {
            return fail;
        }
        Map<String, Object> author = resp.jsonObject();
        if (author == null || authorProfileEmpty(author)) {
            return DataSupport.fail(
                    "Semantic Scholar API returned no author fields for author_id " + authorId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "author");
        out.put("author", author);
        return DataSupport.ports(out);
    }

    /** Bulk papers: POST /paper/batch?fields= with body {"ids":[...]} (max 500). */
    private static Map<String, Object> doBatch(Map<String, Object> in, String fields,
                                               Map<String, String> headers, int timeout, String apiKey) {
        List<String> ids = DataSupport.inStringList(in, "paper_ids");
        if (ids.isEmpty()) {
            return DataSupport.invalidInput("paper_ids array is required for batch action. Max 500.");
        }
        List<String> normalized = new ArrayList<>(ids.size());
        for (String id : ids) {
            String n = normalizePaperId(id);
            if (n != null && !n.isBlank()) {
                normalized.add(n);
            }
        }
        ids = normalized;
        if (ids.size() > BATCH_MAX) {
            ids = new ArrayList<>(ids.subList(0, BATCH_MAX));
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("fields", fields);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", ids);
        DataSupport.HttpResp resp = DataSupport.post(
                API_BASE + "/paper/batch?" + DataSupport.query(params),
                headers, Json.write(body), timeout);
        Map<String, Object> fail = failIfNotOk(resp, apiKey);
        if (fail != null) {
            return fail;
        }
        Object parsed = DataSupport.parseJson(resp.body());
        if (!(parsed instanceof List<?> list)) {
            return DataSupport.fail("Semantic Scholar API Error: expected JSON array");
        }
        List<Object> papers = new ArrayList<>(list);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "batch");
        out.put("papers_returned", papers.size());
        out.put("papers", papers);
        return DataSupport.ports(out);
    }

    /** {@code x-api-key} only when the password config is non-blank. Never log the map. */
    private static Map<String, String> authHeaders(String apiKey) {
        String key = DataSupport.trimToNull(apiKey);
        if (key == null) {
            return DataSupport.headers();
        }
        return DataSupport.headers("x-api-key", key);
    }

    private static Map<String, Object> failIfNotOk(DataSupport.HttpResp resp, String apiKey) {
        return failIfNotOk(resp, apiKey, "paper");
    }

    private static Map<String, Object> failIfNotOk(DataSupport.HttpResp resp, String apiKey,
                                                 String notFoundKind) {
        if (resp != null && resp.ok()) {
            return null;
        }
        return apiError(resp, apiKey, notFoundKind);
    }

    /** HTTP / transport failures; 429 and 404 get the Python suffix text. */
    private static Map<String, Object> apiError(DataSupport.HttpResp resp, String apiKey,
                                              String notFoundKind) {
        String detail;
        int status = -1;
        if (resp == null) {
            detail = "request failed";
        } else if (resp.error() != null && !resp.error().isBlank()) {
            detail = resp.error();
            status = resp.status();
        } else {
            status = resp.status();
            detail = DataSupport.httpStatusMessage(status, resp.body());
        }
        String msg = "Semantic Scholar API Error: " + detail;
        if (status == 429 || detail.contains("429")) {
            msg += " | Rate limited. Get a free API key at api.semanticscholar.org for higher limits.";
        } else if (status == 404 || detail.contains("404")) {
            if ("author".equals(notFoundKind)) {
                msg += " | Author not found. Check the author_id.";
            } else {
                msg += " | Paper not found. Check the paper ID format.";
            }
        }
        return DataSupport.fail(DataSupport.redact(msg, apiKey));
    }

    /**
     * Canonicalize typed external ids (S2 is case-sensitive on {@code ArXiv:} vs {@code ARXIV:}).
     */
    static String normalizePaperId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return s;
        }
        int colon = s.indexOf(':');
        if (colon <= 0) {
            return s;
        }
        String prefix = s.substring(0, colon);
        String rest = s.substring(colon + 1).strip();
        String canon = switch (prefix.toUpperCase(Locale.ROOT)) {
            case "ARXIV" -> "ArXiv";
            case "DOI" -> "DOI";
            case "PMID" -> "PMID";
            case "CORPUSID" -> "CorpusId";
            case "MAG" -> "MAG";
            case "ACL" -> "ACL";
            default -> prefix;
        };
        return rest.isEmpty() ? canon + ":" : canon + ":" + rest;
    }

    private static boolean paperPayloadEmpty(Map<String, Object> paper) {
        if (paper == null || paper.isEmpty()) {
            return true;
        }
        Object title = paper.get("title");
        boolean noTitle = title == null || String.valueOf(title).isBlank();
        return paper.get("paperId") == null && noTitle;
    }

    private static boolean authorProfileEmpty(Map<String, Object> author) {
        if (author == null || author.isEmpty()) {
            return true;
        }
        return author.get("authorId") == null
                && DataSupport.inStr(author, "name", null) == null;
    }

    private static List<Object> unwrapNested(Object data, String nestedKey) {
        List<Object> out = new ArrayList<>();
        for (Object item : asObjectList(data)) {
            Object nested = Json.asMap(item).get(nestedKey);
            out.add(nested instanceof Map<?, ?> ? Json.asMap(nested) : new LinkedHashMap<>());
        }
        return out;
    }

    private static List<Object> asObjectList(Object v) {
        if (v instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private static int num(Object v, int fallback) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
