package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YouTube Data API v3 node (search, video details, channel info, trending).
 *
 * <p>Auth is the Google Cloud API key as query {@code key=}, never an HTTP header.
 * Never log {@code api_key} or {@code helperToken}. Thread-safe: only {@code final}
 * instance state; HTTP goes through the shared {@link SocialHttp} client.
 */
public final class YoutubeNode implements NodeHandler {

    static final String DEFAULT_TEMPLATE_ID = "5fa127c0-dceb-4397-8432-2cfe24beeb19";
    static final String API_BASE = "https://www.googleapis.com/youtube/v3";
    static final int DEFAULT_TIMEOUT_SECONDS = 20;
    static final int DEFAULT_MAX_RESULTS = 25;
    static final int API_MAX_RESULTS = 50;
    static final int DESCRIPTION_CAP = 500;
    static final int TAGS_CAP = 20;

    private final String templateId;

    public YoutubeNode() {
        this(DEFAULT_TEMPLATE_ID);
    }

    public YoutubeNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? DEFAULT_TEMPLATE_ID
                : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * One invoke. {@code _helper} is unused (no FUNCTION-port calls). Failures
     * always return {@code output.error}; this definition has no error OUTPUT port.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper _helper) {
        if (request == null) {
            return DataSupport.invalidInput("request body must be a JSON object");
        }
        try {
            Map<String, Object> params = SocialSupport.nodeParams(request);
            String apiKey = SocialSupport.str(params.get("api_key"));
            if (apiKey.isEmpty()) {
                return DataSupport.invalidInput(
                        "api_key is required. Get one from Google Cloud Console with YouTube Data API v3 enabled.");
            }
            int timeout = SocialSupport.timeoutSeconds(params, DEFAULT_TIMEOUT_SECONDS);
            Map<String, Object> in = SocialSupport.inputPort(request);
            String action = SocialSupport.str(in.get("action"));
            if (action.isEmpty()) {
                action = "search";
            }
            return switch (action) {
                case "search" -> search(apiKey, in, timeout);
                case "video_details" -> videoDetails(apiKey, in, timeout);
                case "channel_info" -> channelInfo(apiKey, in, timeout);
                case "trending" -> trending(apiKey, in, timeout);
                default -> DataSupport.invalidInput(
                        "Unknown action: " + action
                                + ". Use 'search', 'video_details', 'channel_info', or 'trending'.");
            };
        } catch (SocialHttp.ApiException e) {
            return DataSupport.fail(youtubeApiError(e));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail(genericError(e));
        }
    }

    private static Map<String, Object> search(String apiKey, Map<String, Object> in, int timeout)
            throws SocialHttp.ApiException {
        String query = SocialSupport.firstNonBlank(in.get("query"), in.get("q"));
        if (query.isEmpty()) {
            return DataSupport.invalidInput("query is required for search action.");
        }
        Map<String, String> q = keyed(apiKey);
        q.put("part", "snippet");
        q.put("q", query);
        q.put("type", SocialSupport.firstNonBlank(in.get("type"), "video"));
        q.put("order", SocialSupport.firstNonBlank(in.get("order"), "relevance"));
        q.put("maxResults", String.valueOf(capMaxResults(in.get("maxResults"))));
        q.put("relevanceLanguage", SocialSupport.firstNonBlank(in.get("relevanceLanguage"), "en"));
        putOpt(q, "publishedAfter", in.get("publishedAfter"));
        putOpt(q, "publishedBefore", in.get("publishedBefore"));
        // Search filter is YouTube's channelId; also accept channel_id from channel_info callers.
        putOpt(q, "channelId", SocialSupport.firstNonBlank(in.get("channelId"), in.get("channel_id")));
        putOpt(q, "pageToken", in.get("pageToken"));
        putOpt(q, "regionCode", in.get("regionCode"));
        putOpt(q, "videoDuration", in.get("videoDuration"));

        Map<String, Object> data = SocialHttp.getJson(API_BASE + "/search", q, null, timeout);
        List<Map<String, Object>> videos = new ArrayList<>();
        for (Object raw : SocialSupport.asList(data.get("items"))) {
            videos.add(mapSearchItem(SocialSupport.asMap(raw)));
        }
        Map<String, Object> page = SocialSupport.childMap(data, "pageInfo");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "search");
        out.put("query", query);
        out.put("videos_returned", videos.size());
        out.put("total_results", SocialSupport.toLong(page.get("totalResults"), 0L));
        out.put("next_page_token", data.get("nextPageToken"));
        out.put("videos", videos);
        return SocialSupport.ports(out);
    }

    private static Map<String, Object> videoDetails(String apiKey, Map<String, Object> in, int timeout)
            throws SocialHttp.ApiException {
        List<String> ids = SocialSupport.stringList(in.get("video_ids"));
        if (ids.isEmpty()) {
            String one = SocialSupport.str(in.get("video_id"));
            if (!one.isEmpty()) {
                ids = List.of(one);
            }
        }
        if (ids.isEmpty()) {
            return DataSupport.invalidInput("video_ids or video_id is required for video_details action.");
        }
        int n = Math.min(ids.size(), API_MAX_RESULTS);
        Map<String, String> q = keyed(apiKey);
        q.put("part", "snippet,statistics,contentDetails");
        q.put("id", String.join(",", ids.subList(0, n)));

        Map<String, Object> data = SocialHttp.getJson(API_BASE + "/videos", q, null, timeout);
        List<Map<String, Object>> videos = new ArrayList<>();
        for (Object raw : SocialSupport.asList(data.get("items"))) {
            videos.add(mapDetailsItem(SocialSupport.asMap(raw)));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "video_details");
        out.put("videos_returned", videos.size());
        out.put("videos", videos);
        return SocialSupport.ports(out);
    }

    private static Map<String, Object> channelInfo(String apiKey, Map<String, Object> in, int timeout)
            throws SocialHttp.ApiException {
        String channelId = SocialSupport.str(in.get("channel_id"));
        if (channelId.isEmpty()) {
            return DataSupport.invalidInput("channel_id is required for channel_info action.");
        }
        Map<String, String> q = keyed(apiKey);
        // brandingSettings is requested to match Python; the mapped payload uses snippet+statistics only.
        q.put("part", "snippet,statistics,brandingSettings");
        q.put("id", channelId);

        Map<String, Object> data = SocialHttp.getJson(API_BASE + "/channels", q, null, timeout);
        List<Object> items = SocialSupport.asList(data.get("items"));
        if (items.isEmpty()) {
            throw DataSupport.failError("HTTP 404: Channel not found: " + channelId);
        }
        Map<String, Object> channel = mapChannel(SocialSupport.asMap(items.get(0)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "channel_info");
        out.put("data", channel);
        return SocialSupport.ports(out);
    }

    private static Map<String, Object> trending(String apiKey, Map<String, Object> in, int timeout)
            throws SocialHttp.ApiException {
        String region = SocialSupport.firstNonBlank(in.get("regionCode"), "US");
        Map<String, String> q = keyed(apiKey);
        q.put("part", "snippet,statistics,contentDetails");
        q.put("chart", "mostPopular");
        q.put("regionCode", region);
        q.put("maxResults", String.valueOf(capMaxResults(in.get("maxResults"))));
        putOpt(q, "videoCategoryId", in.get("categoryId"));

        Map<String, Object> data = SocialHttp.getJson(API_BASE + "/videos", q, null, timeout);
        List<Map<String, Object>> videos = new ArrayList<>();
        for (Object raw : SocialSupport.asList(data.get("items"))) {
            videos.add(mapTrendingItem(SocialSupport.asMap(raw)));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", "trending");
        out.put("region", region);
        out.put("videos_returned", videos.size());
        out.put("videos", videos);
        return SocialSupport.ports(out);
    }

    private static Map<String, Object> mapSearchItem(Map<String, Object> item) {
        Map<String, Object> snippet = SocialSupport.childMap(item, "snippet");
        Map<String, Object> id = SocialSupport.childMap(item, "id");
        String videoId = SocialSupport.str(id.get("videoId"));
        String channelId = SocialSupport.firstNonBlank(id.get("channelId"), snippet.get("channelId"));
        Map<String, Object> high = SocialSupport.childMap(SocialSupport.childMap(snippet, "thumbnails"), "high");
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("video_id", videoId);
        v.put("title", SocialSupport.str(snippet.get("title")));
        v.put("description", SocialSupport.cap(snippet.get("description"), DESCRIPTION_CAP));
        v.put("channel_title", SocialSupport.str(snippet.get("channelTitle")));
        v.put("channel_id", channelId);
        v.put("published_at", SocialSupport.str(snippet.get("publishedAt")));
        v.put("thumbnail", SocialSupport.str(high.get("url")));
        v.put("url", watchUrl(videoId));
        return v;
    }

    private static Map<String, Object> mapDetailsItem(Map<String, Object> item) {
        Map<String, Object> snippet = SocialSupport.childMap(item, "snippet");
        Map<String, Object> stats = SocialSupport.childMap(item, "statistics");
        Map<String, Object> content = SocialSupport.childMap(item, "contentDetails");
        String videoId = SocialSupport.str(item.get("id"));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("video_id", videoId);
        v.put("title", SocialSupport.str(snippet.get("title")));
        v.put("description", SocialSupport.cap(snippet.get("description"), DESCRIPTION_CAP));
        v.put("channel_title", SocialSupport.str(snippet.get("channelTitle")));
        v.put("channel_id", SocialSupport.str(snippet.get("channelId")));
        v.put("published_at", SocialSupport.str(snippet.get("publishedAt")));
        v.put("tags", firstTags(snippet.get("tags")));
        v.put("category_id", SocialSupport.str(snippet.get("categoryId")));
        // YouTube counters are strings and can exceed Integer.MAX_VALUE.
        v.put("view_count", SocialSupport.toLong(stats.get("viewCount"), 0L));
        v.put("like_count", SocialSupport.toLong(stats.get("likeCount"), 0L));
        v.put("comment_count", SocialSupport.toLong(stats.get("commentCount"), 0L));
        v.put("duration", SocialSupport.str(content.get("duration")));
        v.put("definition", SocialSupport.str(content.get("definition")));
        v.put("url", "https://www.youtube.com/watch?v=" + videoId);
        return v;
    }

    private static Map<String, Object> mapTrendingItem(Map<String, Object> item) {
        Map<String, Object> snippet = SocialSupport.childMap(item, "snippet");
        Map<String, Object> stats = SocialSupport.childMap(item, "statistics");
        Map<String, Object> content = SocialSupport.childMap(item, "contentDetails");
        String videoId = SocialSupport.str(item.get("id"));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("video_id", videoId);
        v.put("title", SocialSupport.str(snippet.get("title")));
        v.put("description", SocialSupport.cap(snippet.get("description"), DESCRIPTION_CAP));
        v.put("channel_title", SocialSupport.str(snippet.get("channelTitle")));
        v.put("published_at", SocialSupport.str(snippet.get("publishedAt")));
        v.put("view_count", SocialSupport.toLong(stats.get("viewCount"), 0L));
        v.put("like_count", SocialSupport.toLong(stats.get("likeCount"), 0L));
        v.put("comment_count", SocialSupport.toLong(stats.get("commentCount"), 0L));
        v.put("duration", SocialSupport.str(content.get("duration")));
        v.put("url", "https://www.youtube.com/watch?v=" + videoId);
        return v;
    }

    private static Map<String, Object> mapChannel(Map<String, Object> item) {
        Map<String, Object> snippet = SocialSupport.childMap(item, "snippet");
        Map<String, Object> stats = SocialSupport.childMap(item, "statistics");
        Map<String, Object> ch = new LinkedHashMap<>();
        ch.put("channel_id", SocialSupport.str(item.get("id")));
        ch.put("title", SocialSupport.str(snippet.get("title")));
        ch.put("description", SocialSupport.cap(snippet.get("description"), DESCRIPTION_CAP));
        ch.put("custom_url", SocialSupport.str(snippet.get("customUrl")));
        ch.put("published_at", SocialSupport.str(snippet.get("publishedAt")));
        ch.put("country", SocialSupport.str(snippet.get("country")));
        ch.put("subscriber_count", SocialSupport.toLong(stats.get("subscriberCount"), 0L));
        ch.put("video_count", SocialSupport.toLong(stats.get("videoCount"), 0L));
        ch.put("view_count", SocialSupport.toLong(stats.get("viewCount"), 0L));
        return ch;
    }

    private static List<String> firstTags(Object raw) {
        List<String> tags = new ArrayList<>();
        for (Object t : SocialSupport.asList(raw)) {
            tags.add(t == null ? "" : String.valueOf(t));
            if (tags.size() >= TAGS_CAP) {
                break;
            }
        }
        return tags;
    }

    /** Python: {@code min(int(maxResults, 25), 50)} — no lower bound. */
    private static int capMaxResults(Object raw) {
        return Math.min(SocialSupport.toInt(raw, DEFAULT_MAX_RESULTS), API_MAX_RESULTS);
    }

    private static String watchUrl(String videoId) {
        return videoId == null || videoId.isEmpty() ? "" : "https://www.youtube.com/watch?v=" + videoId;
    }

    /** API key is a query param named {@code key}, never an Authorization header. */
    private static Map<String, String> keyed(String apiKey) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("key", apiKey);
        return q;
    }

    private static void putOpt(Map<String, String> query, String name, Object value) {
        String s = value instanceof Number
                ? DataSupport.integralText(value)
                : SocialSupport.str(value);
        if (!s.isEmpty()) {
            query.put(name, s);
        }
    }

    private static String youtubeApiError(SocialHttp.ApiException e) {
        String msg = "YouTube API Error: " + (e.getMessage() == null ? "" : e.getMessage());
        if (e.status() == 403) {
            msg += " | API key may be invalid or YouTube Data API v3 not enabled in Google Cloud Console.";
        } else if (e.status() == 429) {
            msg += " | Daily quota exceeded (10,000 units/day on free tier).";
        }
        return msg;
    }

    /**
     * Generic failures. {@link SocialHttp#sanitize} strips query strings so an
     * accidental URL in {@code getMessage()} cannot echo {@code key=}.
     */
    private static String genericError(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return "Error: " + e.getClass().getSimpleName() + ": " + SocialHttp.sanitize(msg);
    }
}
