package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reddit Search ({@code 309005be-14d0-4b62-8b0c-ac65d43df7d0}): listing or full-text search.
 *
 * <p>OAuth2 when both {@code client_id} and {@code client_secret} are set
 * (password grant if username+password, else client_credentials); otherwise
 * public JSON on {@code old.reddit.com} then {@code www.reddit.com}. Access
 * tokens are fetched per invoke and never cached, logged, or returned.
 *
 * <p>{@code handle} never throws. Reply is keyed only by OUTPUT port {@code output}.
 * Thread-safe: no mutable instance state besides {@code templateId}.
 */
public final class RedditNode implements NodeHandler {

    /** Bundled definition {@code _id}. */
    static final String DEFAULT_ID = "309005be-14d0-4b62-8b0c-ac65d43df7d0";
    static final String TOKEN_URL = "https://www.reddit.com/api/v1/access_token";
    static final String OAUTH_BASE = "https://oauth.reddit.com";
    static final List<String> PUBLIC_BASES = List.of(
            "https://old.reddit.com", "https://www.reddit.com");
    static final String DEFAULT_UA = "StudioQuack:v1.0 (by /u/studioquack)";
    static final int DEFAULT_TIMEOUT = 25;
    static final Set<String> LISTINGS = Set.of("hot", "new", "top", "rising");
    static final String UNAUTH_HINT =
            " | Reddit blocks unauthenticated access. Configure OAuth2 (client_id, client_secret) for reliable results.";
    static final String PUBLIC_ALL_FAILED =
            "All Reddit public endpoints returned errors. Configure OAuth2 credentials for reliable access.";
    static final String NO_TOKEN = "Reddit OAuth returned no access_token";

    private final String templateId;

    public RedditNode() {
        this(DEFAULT_ID);
    }

    public RedditNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * One search or listing. {@code helper} is unused. Never logs
     * {@code helperToken}, {@code access_token}, {@code password}, or {@code client_secret}.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper _helper) {
        try {
            return invoke(request);
        } catch (SocialHttp.ApiException e) {
            return redditApiError(e);
        } catch (IllegalArgumentException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            return DataSupport.fail(m);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            String detail = SocialHttp.sanitize(e.getMessage());
            return DataSupport.fail("Error: " + e.getClass().getSimpleName() + ": " + detail);
        }
    }

    private static Map<String, Object> invoke(Map<String, Object> request) throws SocialHttp.ApiException {
        Map<String, Object> cfg = SocialSupport.nodeParams(request);
        Map<String, Object> in = SocialSupport.inputPort(request);

        String clientId = SocialSupport.cfg(cfg, "client_id", "");
        String clientSecret = SocialSupport.cfg(cfg, "client_secret", "");
        String username = SocialSupport.cfg(cfg, "username", "");
        String password = SocialSupport.cfg(cfg, "password", "");
        String userAgent = SocialSupport.cfg(cfg, "user_agent", DEFAULT_UA);
        int timeout = SocialSupport.timeoutSeconds(cfg, DEFAULT_TIMEOUT);
        boolean useOauth = !clientId.isEmpty() && !clientSecret.isEmpty();

        String actionRaw = SocialSupport.str(in.get("action"));
        String action = actionRaw.isEmpty() ? "search" : actionRaw;
        String query = SocialSupport.firstNonBlank(in.get("query"), in.get("q"));
        String subreddit = SocialSupport.str(in.get("subreddit"));
        String sort = firstOr(in.get("sort"), "relevance");
        String timeFilter = firstOr(in.get("time"), "day");
        // Python: min(limit, 100) — no floor.
        int limit = Math.min(SocialSupport.toInt(in.get("limit"), 25), 100);
        String listing = firstOr(in.get("listing"), "hot");
        String after = SocialSupport.str(in.get("after"));

        String path;
        Map<String, String> q = new LinkedHashMap<>();
        if ("search".equals(action)) {
            if (query.isEmpty()) {
                return DataSupport.invalidInput("query is required for search action.");
            }
            if (!subreddit.isEmpty()) {
                path = "/r/" + subreddit + "/search";
                putSearch(q, query, sort, timeFilter, limit, true);
            } else {
                path = "/search";
                putSearch(q, query, sort, timeFilter, limit, false);
            }
        } else if ("listing".equals(action)) {
            if (subreddit.isEmpty()) {
                return DataSupport.invalidInput("subreddit is required for listing action.");
            }
            if (!LISTINGS.contains(listing)) {
                listing = "hot";
            }
            path = "/r/" + subreddit + "/" + listing;
            q.put("limit", Integer.toString(limit));
            q.put("t", timeFilter);
        } else {
            return DataSupport.invalidInput("Unknown action: " + action + ". Use 'search' or 'listing'.");
        }
        if (!after.isEmpty()) {
            q.put("after", after);
        }

        Map<String, Object> data;
        String authMode;
        if (useOauth) {
            String token = accessToken(clientId, clientSecret, userAgent, username, password, timeout);
            data = fetchOauth(path, q, token, userAgent, timeout);
            authMode = "oauth";
        } else {
            data = fetchPublic(path, q, userAgent, timeout);
            authMode = "public";
        }

        Parsed parsed = parsePosts(data);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("auth_mode", authMode);
        out.put("posts_returned", parsed.posts.size());
        out.put("after", parsed.after);
        out.put("posts", parsed.posts);
        return SocialSupport.ports(out);
    }

    private static void putSearch(Map<String, String> q, String query, String sort,
                                  String timeFilter, int limit, boolean restrictSr) {
        q.put("q", query);
        q.put("sort", sort);
        q.put("t", timeFilter);
        q.put("limit", Integer.toString(limit));
        if (restrictSr) {
            q.put("restrict_sr", "true");
        }
        q.put("type", "link");
    }

    /**
     * POST {@link #TOKEN_URL}. Password grant when both username and password
     * are set; otherwise {@code client_credentials}. Basic auth is
     * {@link SocialHttp#basicAuthHeader}; User-Agent is required.
     */
    private static String accessToken(String clientId, String clientSecret, String userAgent,
                                      String username, String password, int timeout)
            throws SocialHttp.ApiException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", SocialHttp.basicAuthHeader(clientId, clientSecret));
        headers.put("User-Agent", userAgent);
        Map<String, String> form = new LinkedHashMap<>();
        if (!username.isEmpty() && !password.isEmpty()) {
            form.put("grant_type", "password");
            form.put("username", username);
            form.put("password", password);
        } else {
            form.put("grant_type", "client_credentials");
        }
        Map<String, Object> body = SocialHttp.postForm(TOKEN_URL, headers, form, timeout);
        String token = SocialSupport.str(body.get("access_token"));
        if (token.isEmpty()) {
            // Never echo the token JSON — it can contain secrets.
            throw new IllegalArgumentException(NO_TOKEN);
        }
        return token;
    }

    /** GET {@code OAUTH_BASE + path} (no {@code .json} suffix) with Bearer + User-Agent. */
    private static Map<String, Object> fetchOauth(String path, Map<String, String> query,
                                                  String token, String userAgent, int timeout)
            throws SocialHttp.ApiException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("User-Agent", userAgent);
        return SocialHttp.getJson(OAUTH_BASE + path, query, headers, timeout);
    }

    /**
     * Public JSON: {@code base + path + ".json"}, two attempts per domain.
     * 429 → {@link SocialHttp#sleepSeconds} {@code 2*(attempt+1)} then retry;
     * 403 → next domain; other HTTP errors raise; status 0 (transport) → next domain.
     */
    private static Map<String, Object> fetchPublic(String path, Map<String, String> query,
                                                   String userAgent, int timeout)
            throws SocialHttp.ApiException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept", "application/json");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        for (String base : PUBLIC_BASES) {
            String url = base + path + ".json";
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    return SocialHttp.getJson(url, query, headers, timeout);
                } catch (SocialHttp.ApiException e) {
                    if (e.status() == 429) {
                        SocialHttp.sleepSeconds(2 * (attempt + 1));
                        continue;
                    }
                    if (e.status() == 403 || e.status() == 0) {
                        break;
                    }
                    throw e;
                }
            }
        }
        throw new SocialHttp.ApiException(0, PUBLIC_ALL_FAILED);
    }

    /** {@code children[].data} plus listing {@code after}. {@code link_flair_text} may be null. */
    private static Parsed parsePosts(Map<String, Object> data) {
        Map<String, Object> inner = SocialSupport.childMap(data, "data");
        List<Map<String, Object>> posts = new ArrayList<>();
        for (Object child : SocialSupport.asList(inner.get("children"))) {
            Map<String, Object> d = SocialSupport.childMap(SocialSupport.asMap(child), "data");
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("title", SocialSupport.str(d.get("title")));
            p.put("selftext", SocialSupport.cap(d.get("selftext"), 500));
            p.put("url", SocialSupport.str(d.get("url")));
            p.put("permalink", "https://www.reddit.com" + SocialSupport.str(d.get("permalink")));
            p.put("score", SocialSupport.toLong(d.get("score"), 0L));
            p.put("num_comments", SocialSupport.toLong(d.get("num_comments"), 0L));
            Object created = d.get("created_utc");
            p.put("created_utc", created == null ? 0 : created);
            p.put("subreddit", SocialSupport.str(d.get("subreddit")));
            p.put("author", SocialSupport.str(d.get("author")));
            p.put("is_self", bool(d.get("is_self")));
            p.put("link_flair_text", d.get("link_flair_text"));
            posts.add(p);
        }
        return new Parsed(posts, inner.get("after"));
    }

    private static Map<String, Object> redditApiError(SocialHttp.ApiException e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String msg = "Reddit API Error: " + raw;
        if (e.status() == 403 || raw.contains("403") || raw.contains("Blocked")) {
            msg += UNAUTH_HINT;
        }
        return DataSupport.fail(msg);
    }

    private static String firstOr(Object raw, String fallback) {
        String s = SocialSupport.str(raw);
        return s.isEmpty() ? fallback : s;
    }

    private static boolean bool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        String s = SocialSupport.str(o);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private record Parsed(List<Map<String, Object>> posts, Object after) {
    }
}
