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
 * Hacker News Algolia search node ({@code /search} and {@code /search_by_date}).
 *
 * <p>Unauthenticated search client. Thread-safe: immutable after construction;
 * HTTP goes through {@link DataNodeSupport}. Never logs {@code helperToken} or
 * query text.
 */
public final class HackernewsNode implements NodeHandler {

    static final String DEFAULT_ID = "ea8652a8-a1f5-4101-aa7d-65a2f9c097d4";

    /** Hacker News Algolia search API base URL. */
    private static final String API_BASE = "http://hn.algolia.com/api/v1";

    private static final int MAX_HITS_PER_PAGE = 50;
    private static final int TEXT_TRUNCATE = 500;

    private final String templateId;

    public HackernewsNode() {
        this(DEFAULT_ID);
    }

    public HackernewsNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * One Algolia search. Failures return {@code output.error}; never thrown.
     * {@code unused} is the per-request helper channel — logs go through
     * {@link DataNodeSupport#logs} so the bearer is never printed.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper unused) {
        HelperClient logs = DataNodeSupport.logs(request);
        try {
            if (request == null) {
                return DataSupport.invalidInput("request body must be a JSON object");
            }
            Map<String, Object> cfg = DataNodeSupport.configParams(request);
            Map<String, Object> in = DataNodeSupport.inputPort(request);
            int timeout = DataNodeSupport.positiveInt(cfg.get("timeout_seconds"), 25);
            int defaultHits = Math.min(
                    DataNodeSupport.positiveInt(cfg.get("default_hits_per_page"), 30),
                    MAX_HITS_PER_PAGE);

            // query or q; empty/missing fails like Python `if not query`.
            String query = DataNodeSupport.str(in.get("query"));
            if (query.isEmpty()) {
                query = DataNodeSupport.str(in.get("q"));
            }
            if (query.isEmpty()) {
                return DataSupport.invalidInput("query is required.");
            }

            // Unknown actions still hit /search (Python equality check, not an allow-list).
            String action = DataNodeSupport.str(in.get("action"));
            if (action.isEmpty()) {
                action = "search";
            }
            String endpoint = "search_by_date".equals(action) ? "search_by_date" : "search";

            String tags = DataNodeSupport.str(in.get("tags"));
            if (tags.isEmpty()) {
                tags = "story";
            }

            int hitsPerPage = Math.min(
                    DataNodeSupport.toInt(in.get("hits_per_page"), defaultHits),
                    MAX_HITS_PER_PAGE);
            // 0-indexed; do not use positiveInt (that would replace 0 with the default).
            int page = DataNodeSupport.toInt(in.get("page"), 0);

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("query", query);
            params.put("tags", tags);
            params.put("hitsPerPage", hitsPerPage);
            params.put("page", page);
            Object numericFilters = in.get("numeric_filters");
            if (DataNodeSupport.present(numericFilters)) {
                params.put("numericFilters", DataNodeSupport.str(numericFilters));
            }

            logs.log(Map.of("message", "hackernews start", "action", endpoint));

            // Algolia tolerates anonymous clients; send the product UA anyway so
            // traffic is identifiable (and consistent with the other data nodes).
            HttpResult r = DataNodeSupport.get(API_BASE + "/" + endpoint,
                    Map.of("User-Agent", DataNodeSupport.USER_AGENT), params, timeout);
            if (!r.ok()) {
                return DataSupport.fail(
                        "HackerNews Algolia API Error: HTTP " + r.status + " for url: " + r.url);
            }

            Map<String, Object> data = DataNodeSupport.asObject(DataNodeSupport.parseJson(r.body));
            List<Object> stories = new ArrayList<>();
            for (Object hitObj : DataNodeSupport.asArray(data.get("hits"))) {
                stories.add(mapHit(DataNodeSupport.asObject(hitObj)));
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("status", "ok");
            output.put("total_hits", DataNodeSupport.toInt(data.get("nbHits"), 0));
            output.put("total_pages", DataNodeSupport.toInt(data.get("nbPages"), 0));
            output.put("stories_returned", stories.size());
            output.put("stories", stories);

            logs.log(Map.of("message", "hackernews done", "action", endpoint,
                    "stories_returned", stories.size()));
            return DataNodeSupport.outputPorts(output);
        } catch (DataHttpException e) {
            logs.log("hackernews transport: " + e.getClass().getSimpleName());
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return DataSupport.fail("HackerNews Algolia API Error: " + detail);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            logs.log("hackernews internal: " + e.getClass().getSimpleName());
            return DataSupport.fail(
                    "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Map one Algolia hit to the Python story object (texts capped at 500). */
    private static Map<String, Object> mapHit(Map<String, Object> hit) {
        String objectId = DataNodeSupport.str(hit.get("objectID"));
        String title = DataNodeSupport.str(hit.get("title"));
        if (title.isEmpty()) {
            title = DataNodeSupport.str(hit.get("story_title"));
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("object_id", objectId);
        row.put("title", title);
        row.put("url", DataNodeSupport.str(hit.get("url")));
        row.put("points", DataNodeSupport.toInt(hit.get("points"), 0));
        row.put("num_comments", DataNodeSupport.toInt(hit.get("num_comments"), 0));
        row.put("author", DataNodeSupport.str(hit.get("author")));
        row.put("created_at", DataNodeSupport.str(hit.get("created_at")));
        row.put("story_text", DataNodeSupport.truncate(
                DataNodeSupport.str(hit.get("story_text")), TEXT_TRUNCATE));
        row.put("comment_text", DataNodeSupport.truncate(
                DataNodeSupport.str(hit.get("comment_text")), TEXT_TRUNCATE));
        row.put("hn_url", "https://news.ycombinator.com/item?id=" + objectId);
        return row;
    }
}
