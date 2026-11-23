package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.StudioQuackHelper;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bluesky AT Protocol search node ({@code 24a7c5c6-9070-4d0b-91c4-2e386f1c8dee}).
 *
 * <p>Stateless and thread-safe: each invoke creates a fresh session (when credentials
 * are set) and never caches {@code accessJwt}. App passwords and JWTs are never
 * logged or written to {@code output}.
 *
 * <p>{@link #handle} never throws; failures are {@code output.error} only.
 */
public final class BlueskyNode implements NodeHandler {

    /** Bundled definition {@code _id}. */
    public static final String DEFAULT_TEMPLATE_ID = "24a7c5c6-9070-4d0b-91c4-2e386f1c8dee";

    static final String PUBLIC_BASE = "https://public.api.bsky.app/xrpc";
    static final String APPVIEW_BASE = "https://api.bsky.app/xrpc";
    static final String ENTRYWAY = "https://bsky.social/xrpc";
    static final String CREATE_SESSION = "com.atproto.server.createSession";

    static final String NSID_SEARCH_POSTS = "app.bsky.feed.searchPosts";
    static final String NSID_SEARCH_ACTORS = "app.bsky.actor.searchActors";
    static final String NSID_AUTHOR_FEED = "app.bsky.feed.getAuthorFeed";

    /** Used when config has no {@code user_agent} shortCode (bundled definition omits it). */
    static final String DEFAULT_UA = "StudioQuack-BlueskySearch/1.0 (+https://studioquack.com)";

    private static final DateTimeFormatter MILLIS_Z =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final String templateId;

    public BlueskyNode() {
        this(DEFAULT_TEMPLATE_ID);
    }

    public BlueskyNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? DEFAULT_TEMPLATE_ID
                : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        boolean hadToken = false;
        try {
            Map<String, Object> params = SocialSupport.nodeParams(request);
            Map<String, Object> input = SocialSupport.inputPort(request);
            int timeout = SocialSupport.timeoutSeconds(params, 25);
            int defaultLimit = SocialSupport.toInt(params.get("default_limit"), 50);
            // Honor user_agent when an instance adds it; otherwise DEFAULT_UA.
            String userAgent = SocialSupport.cfg(params, "user_agent", DEFAULT_UA);

            String handle = SocialSupport.str(params.get("bluesky_handle"));
            String appPassword = SocialSupport.str(params.get("bluesky_app_password"));
            String accessToken = null;
            if (!handle.isEmpty() && !appPassword.isEmpty()) {
                accessToken = createSession(handle, appPassword, userAgent, timeout);
            }
            hadToken = accessToken != null && !accessToken.isEmpty();

            String action = SocialSupport.str(input.get("action"));
            if (action.isEmpty()) {
                action = "search_posts";
            }
            String query = SocialSupport.firstNonBlank(input.get("query"), input.get("q"));
            String sort = SocialSupport.str(input.get("sort"));
            if (sort.isEmpty()) {
                sort = "latest";
            }
            String since = normalizeDatetimeFilter(input.get("since"), false);
            String until = normalizeDatetimeFilter(input.get("until"), true);
            String lang = SocialSupport.str(input.get("lang"));
            int limit = SocialSupport.toInt(input.get("limit"), defaultLimit);
            if (limit > 100) {
                limit = 100;
            }
            String cursor = SocialSupport.str(input.get("cursor"));
            String actor = SocialSupport.str(input.get("actor"));

            return switch (action) {
                case "search_posts" -> searchPosts(query, sort, since, until, lang, limit, cursor,
                        userAgent, timeout, accessToken);
                case "search_actors" -> searchActors(query, limit, cursor, userAgent, timeout,
                        accessToken);
                case "get_author_feed" -> getAuthorFeed(actor, limit, cursor, userAgent, timeout,
                        accessToken);
                default -> DataSupport.invalidInput(
                        "Unknown action: " + action
                                + ". Use search_posts, search_actors, or get_author_feed.");
            };
        } catch (AuthException e) {
            return DataSupport.fail("Bluesky Auth Error: " + safeMsg(e));
        } catch (SocialHttp.ApiException e) {
            return DataSupport.fail(apiError(e, hadToken));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail(
                    "Error: " + e.getClass().getSimpleName() + ": " + safeMsg(e));
        }
    }

    /**
     * POST {@code com.atproto.server.createSession}. 401 → auth error with handle
     * (never the app password). Missing {@code accessJwt} does not dump the body.
     */
    private static String createSession(String handle, String appPassword, String userAgent,
                                        int timeout) throws AuthException, SocialHttp.ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("identifier", handle);
        body.put("password", appPassword);
        try {
            Map<String, Object> data = SocialHttp.postJson(
                    ENTRYWAY + "/" + CREATE_SESSION, null, jsonHeaders(userAgent, null), body, timeout);
            String jwt = SocialSupport.str(data.get("accessJwt"));
            if (jwt.isEmpty()) {
                throw new AuthException("Bluesky session response missing accessJwt");
            }
            return jwt;
        } catch (SocialHttp.ApiException e) {
            if (e.status() == 401) {
                throw new AuthException(
                        "Bluesky authentication failed (401). "
                                + "Check your handle ('" + handle + "') and app password. "
                                + "Generate an app password at: https://bsky.app/settings/app-passwords");
            }
            throw e;
        }
    }

    /**
     * Authenticated: AppView, retry 429 only (no public fallback). Public: public AppView,
     * retry 429 and 403. Backoff {@code 2*(attempt+1)} seconds.
     */
    private static Map<String, Object> xrpcGet(String nsid, Map<String, String> query,
                                               String userAgent, int timeout, String accessToken)
            throws SocialHttp.ApiException {
        if (accessToken != null && !accessToken.isEmpty()) {
            String url = APPVIEW_BASE + "/" + nsid;
            Map<String, String> headers = jsonHeaders(userAgent, accessToken);
            SocialHttp.ApiException last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    return SocialHttp.getJson(url, query, headers, timeout);
                } catch (SocialHttp.ApiException e) {
                    last = e;
                    if (e.status() == 429) {
                        SocialHttp.sleepSeconds(2 * (attempt + 1));
                        continue;
                    }
                    throw e;
                }
            }
            throw last;
        }
        String url = PUBLIC_BASE + "/" + nsid;
        Map<String, String> headers = jsonHeaders(userAgent, null);
        SocialHttp.ApiException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return SocialHttp.getJson(url, query, headers, timeout);
            } catch (SocialHttp.ApiException e) {
                last = e;
                if (e.status() == 429 || e.status() == 403) {
                    SocialHttp.sleepSeconds(2 * (attempt + 1));
                    continue;
                }
                throw e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new SocialHttp.ApiException(0, "All Bluesky public API attempts returned errors.");
    }

    private static Map<String, Object> searchPosts(String query, String sort, String since,
                                                   String until, String lang, int limit, String cursor,
                                                   String userAgent, int timeout, String accessToken)
            throws SocialHttp.ApiException {
        if (query.isEmpty()) {
            return DataSupport.invalidInput("query is required for search_posts action.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("sort", sort);
        params.put("limit", Integer.toString(limit));
        putIfPresent(params, "since", since);
        putIfPresent(params, "until", until);
        putIfPresent(params, "lang", lang);
        putIfPresent(params, "cursor", cursor);
        Map<String, Object> data = xrpcGet(NSID_SEARCH_POSTS, params, userAgent, timeout, accessToken);
        List<Object> posts = new ArrayList<>();
        for (Object row : SocialSupport.asList(data.get("posts"))) {
            posts.add(normalizePost(SocialSupport.asMap(row), true));
        }
        return SocialSupport.ports(okList("posts_returned", "posts", posts, data.get("cursor")));
    }

    private static Map<String, Object> searchActors(String query, int limit, String cursor,
                                                    String userAgent, int timeout, String accessToken)
            throws SocialHttp.ApiException {
        if (query.isEmpty()) {
            return DataSupport.invalidInput("query is required for search_actors action.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("limit", Integer.toString(limit));
        putIfPresent(params, "cursor", cursor);
        Map<String, Object> data = xrpcGet(NSID_SEARCH_ACTORS, params, userAgent, timeout, accessToken);
        List<Object> actors = new ArrayList<>();
        for (Object row : SocialSupport.asList(data.get("actors"))) {
            Map<String, Object> a = SocialSupport.asMap(row);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("handle", SocialSupport.str(a.get("handle")));
            out.put("display_name", SocialSupport.str(a.get("displayName")));
            out.put("description", SocialSupport.str(a.get("description")));
            out.put("followers_count", SocialSupport.toInt(a.get("followersCount"), 0));
            out.put("follows_count", SocialSupport.toInt(a.get("followsCount"), 0));
            out.put("posts_count", SocialSupport.toInt(a.get("postsCount"), 0));
            out.put("avatar", SocialSupport.str(a.get("avatar")));
            actors.add(out);
        }
        return SocialSupport.ports(okList("actors_returned", "actors", actors, data.get("cursor")));
    }

    private static Map<String, Object> getAuthorFeed(String actor, int limit, String cursor,
                                                     String userAgent, int timeout, String accessToken)
            throws SocialHttp.ApiException {
        if (actor.isEmpty()) {
            return DataSupport.invalidInput(
                    "actor (handle or DID) is required for get_author_feed.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("actor", actor);
        params.put("limit", Integer.toString(limit));
        putIfPresent(params, "cursor", cursor);
        Map<String, Object> data = xrpcGet(NSID_AUTHOR_FEED, params, userAgent, timeout, accessToken);
        List<Object> posts = new ArrayList<>();
        for (Object item : SocialSupport.asList(data.get("feed"))) {
            Map<String, Object> post = SocialSupport.childMap(SocialSupport.asMap(item), "post");
            posts.add(normalizePost(post, false));
        }
        return SocialSupport.ports(okList("posts_returned", "posts", posts, data.get("cursor")));
    }

    /** {@code searchFields}: search_posts includes display name, indexed_at, embed. */
    private static Map<String, Object> normalizePost(Map<String, Object> post, boolean searchFields) {
        Map<String, Object> record = SocialSupport.childMap(post, "record");
        Map<String, Object> author = SocialSupport.childMap(post, "author");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uri", SocialSupport.str(post.get("uri")));
        out.put("text", SocialSupport.str(record.get("text")));
        out.put("created_at", SocialSupport.str(record.get("createdAt")));
        out.put("like_count", SocialSupport.toInt(post.get("likeCount"), 0));
        out.put("repost_count", SocialSupport.toInt(post.get("repostCount"), 0));
        out.put("reply_count", SocialSupport.toInt(post.get("replyCount"), 0));
        out.put("author_handle", SocialSupport.str(author.get("handle")));
        if (searchFields) {
            out.put("author_display_name", SocialSupport.str(author.get("displayName")));
            out.put("indexed_at", SocialSupport.str(post.get("indexedAt")));
            out.put("embed", record.get("embed"));
        }
        return out;
    }

    /**
     * Date-only {@code YYYY-MM-DD} → UTC midnight; {@code until} adds one day so that
     * calendar day is inclusive. Other ISO values: naive → UTC; output milliseconds + Z.
     */
    static String normalizeDatetimeFilter(Object raw, boolean isUntil) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String)) {
            String coerced = SocialSupport.str(raw);
            return coerced.isEmpty() ? null : coerced;
        }
        String value = ((String) raw).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            if (value.length() == 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
                Instant inst = LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
                if (isUntil) {
                    inst = inst.plus(1, ChronoUnit.DAYS);
                }
                return MILLIS_Z.format(inst);
            }
            return MILLIS_Z.format(parseIsoInstant(value).truncatedTo(ChronoUnit.MILLIS));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid ISO datetime filter: " + value, e);
        }
    }

    private static Instant parseIsoInstant(String value) {
        String v = value.replace("Z", "+00:00");
        if (v.length() > 10 && v.charAt(10) == ' ') {
            v = v.substring(0, 10) + "T" + v.substring(11);
        }
        try {
            return OffsetDateTime.parse(v, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeException ignored) {
            return LocalDateTime.parse(v, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC);
        }
    }

    private static Map<String, Object> okList(String countKey, String listKey, List<Object> items,
                                              Object cursor) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "ok");
        output.put(countKey, items.size());
        output.put("cursor", cursor);
        output.put(listKey, items);
        return output;
    }

    private static Map<String, String> jsonHeaders(String userAgent, String accessToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", userAgent == null || userAgent.isBlank() ? DEFAULT_UA : userAgent);
        headers.put("Accept", "application/json");
        if (accessToken != null && !accessToken.isEmpty()) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        return headers;
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isEmpty()) {
            params.put(key, value);
        }
    }

    private static String apiError(SocialHttp.ApiException e, boolean hadToken) {
        String msg = safeMsg(e);
        if (msg.isEmpty()) {
            msg = "HTTP " + e.status();
        }
        String out = "Bluesky API Error: " + msg;
        boolean forbidden = e.status() == 403 || msg.contains("403");
        if (forbidden && !hadToken) {
            out += " | Public API is returning 403 (Bluesky load-reduction). "
                    + "Fix: set bluesky_handle and bluesky_app_password in the node config. "
                    + "Generate an app password at bsky.app > Settings > App Passwords.";
        }
        return out;
    }

    private static String safeMsg(Throwable e) {
        return e == null || e.getMessage() == null ? "" : e.getMessage();
    }

    /** Session / JWT validation failures (Python {@code ValueError} path). */
    private static final class AuthException extends Exception {
        AuthException(String message) {
            super(message);
        }
    }
}
