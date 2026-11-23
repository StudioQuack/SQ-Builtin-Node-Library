package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.StudioQuackHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * X (Twitter) Connector — stateless X API v2 client at {@code https://api.x.com/2}.
 *
 * <p>Secrets constraint (stricter than the Python tool): {@code bearer_token} and
 * {@code user_access_token} are read ONLY from {@code configurationParameters}.
 * {@code input.bearer_token} and {@code input.user_access_token} are ignored.
 * {@code input.mode} may override {@code sandbox}|{@code live}. Tokens are never
 * logged or returned; {@code stats} exposes {@code has_bearer} /
 * {@code has_user_token} booleans only.
 *
 * <p>Reply is the OUTPUT port {@code output} only. {@link #handle} never throws.
 * Thread-safe: immutable template id, no other instance state.
 */
public final class TwitterNode implements NodeHandler {

    /** Bundled definition {@code _id}; {@code SQ_TEMPLATE_ID_TWITTER} may override at register time. */
    static final String DEFAULT_TEMPLATE_ID = "f534a86d-ebd3-475b-a7e9-0d8b650751b1";

    static final String X_BASE = "https://api.x.com/2";

    private static final int DEFAULT_TIMEOUT = 25;

    /**
     * Python {@code repr(sorted(ACTIONS.keys()))} including aliases
     * {@code search_recent} and {@code reply}.
     */
    private static final String VALID_ACTIONS_REPR =
            "['get_user', 'list_dm_events', 'list_mentions', 'post_tweet', 'reply', "
                    + "'reply_tweet', 'search_recent', 'search_tweets', 'send_dm', 'stats']";

    private static final Set<String> ACTIONS = Set.of(
            "get_user", "list_mentions", "list_dm_events", "send_dm",
            "search_tweets", "search_recent", "reply_tweet", "reply",
            "post_tweet", "stats");

    private final String templateId;

    public TwitterNode() {
        this(DEFAULT_TEMPLATE_ID);
    }

    public TwitterNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? DEFAULT_TEMPLATE_ID
                : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Invoke. Failures stay on {@code output.error}. Never logs tokens.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper _helper) {
        try {
            Map<String, Object> params = SocialSupport.nodeParams(request);
            Map<String, Object> in = SocialSupport.inputPort(request);
            Cfg cfg = Cfg.from(params, in);
            String action = SocialSupport.lower(in.get("action"));
            if (action.isEmpty()) {
                action = "stats";
            }
            // Live gate matches Python: unknown actions still hit this first.
            if (cfg.live() && !cfg.hasBearer() && !cfg.hasUserToken() && !"stats".equals(action)) {
                return DataSupport.invalidInput(
                        "bearer_token or user_access_token required for live mode.");
            }
            if (!ACTIONS.contains(action)) {
                return DataSupport.invalidInput(
                        "Unknown action '" + action + "'. Valid: " + VALID_ACTIONS_REPR);
            }
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("status", "ok");
            ok.put("action", action); // requested alias, e.g. search_recent stays search_recent
            ok.put("result", dispatch(action, cfg, in));
            return SocialSupport.ports(ok);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String msg = e.getMessage() == null ? "" : e.getMessage();
            return DataSupport.fail("Error: " + e.getClass().getSimpleName() + ": " + msg);
        }
    }

    private static Map<String, Object> dispatch(String action, Cfg cfg, Map<String, Object> in)
            throws Exception {
        return switch (action) {
            case "get_user" -> getUser(cfg, in);
            case "list_mentions" -> listMentions(cfg, in);
            case "list_dm_events" -> listDmEvents(cfg, in);
            case "send_dm" -> sendDm(cfg, in);
            case "search_tweets", "search_recent" -> searchTweets(cfg, in);
            case "reply_tweet", "reply" -> replyTweet(cfg, in);
            case "post_tweet" -> postTweet(cfg, in);
            case "stats" -> stats(cfg);
            default -> throw new ValueError("Unknown action '" + action + "'.");
        };
    }

    private static Map<String, Object> getUser(Cfg cfg, Map<String, Object> in) throws Exception {
        String handle = SocialSupport.str(in.get("handle"));
        if (handle.isEmpty()) {
            throw new ValueError("handle required for get_user.");
        }
        if (!cfg.live()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", "9999999");
            data.put("username", handle);
            data.put("name", "Sandbox User");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", "sandbox");
            out.put("data", data);
            return out;
        }
        String token = cfg.hasUserToken() ? cfg.userToken : cfg.bearer;
        Map<String, String> query = new LinkedHashMap<>();
        query.put("user.fields", "id,name,username,description,public_metrics,verified,created_at");
        return SocialHttp.getJson(X_BASE + "/users/by/username/" + pathSegment(handle),
                query, authHeaders(token), cfg.timeout);
    }

    private static Map<String, Object> listMentions(Cfg cfg, Map<String, Object> in) throws Exception {
        String userId = SocialSupport.firstNonBlank(in.get("user_id"), cfg.defaultUserId);
        if (userId.isEmpty()) {
            throw new ValueError("user_id required (or default_user_id config).");
        }
        int limit = SocialSupport.toInt(in.get("limit"), 25);
        if (!cfg.live()) {
            List<Object> data = new ArrayList<>();
            int n = Math.min(limit, 3);
            for (int i = 0; i < n; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", "sandbox-tweet-" + i);
                row.put("author_id", "100" + i);
                row.put("text", "Stub mention #" + i + " of @studioquack");
                data.add(row);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", "sandbox");
            out.put("user_id", userId);
            out.put("data", data);
            return out;
        }
        String token = cfg.hasBearer() ? cfg.bearer : cfg.userToken;
        Map<String, String> query = new LinkedHashMap<>();
        query.put("max_results", String.valueOf(Math.min(Math.max(limit, 5), 100)));
        query.put("tweet.fields",
                "author_id,created_at,public_metrics,lang,in_reply_to_user_id,conversation_id");
        String since = SocialSupport.str(in.get("since"));
        if (!since.isEmpty()) {
            query.put("start_time", since);
        }
        return SocialHttp.getJson(X_BASE + "/users/" + pathSegment(userId) + "/mentions",
                query, authHeaders(token), cfg.timeout);
    }

    private static Map<String, Object> listDmEvents(Cfg cfg, Map<String, Object> in) throws Exception {
        String convId = SocialSupport.str(in.get("conversation_id"));
        if (!cfg.live()) {
            // Sandbox does not require conversation_id.
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "sandbox-event-1");
            row.put("event_type", "MessageCreate");
            row.put("text", "Sandbox inbound DM");
            row.put("sender_id", "1234567");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", "sandbox");
            out.put("conversation_id", convId.isEmpty() ? "sandbox-conv" : convId);
            out.put("data", List.of(row));
            return out;
        }
        if (!cfg.hasUserToken()) {
            throw new ValueError(
                    "user_access_token required for DM endpoints (scopes: dm.read, dm.write, tweet.read, users.read).");
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("max_results", String.valueOf(SocialSupport.toInt(in.get("limit"), 25)));
        query.put("dm_event.fields", "id,event_type,text,created_at,sender_id,attachments");
        String url = convId.isEmpty()
                ? X_BASE + "/dm_events"
                : X_BASE + "/dm_conversations/" + pathSegment(convId) + "/dm_events";
        return SocialHttp.getJson(url, query, authHeaders(cfg.userToken), cfg.timeout);
    }

    private static Map<String, Object> sendDm(Cfg cfg, Map<String, Object> in) throws Exception {
        String recipientId = SocialSupport.str(in.get("recipient_id"));
        String text = raw(in.get("body"));
        if (recipientId.isEmpty() || text.isEmpty()) {
            throw new ValueError("recipient_id and body required for send_dm.");
        }
        if (!cfg.live()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", "sandbox");
            out.put("recipient_id", recipientId);
            out.put("text", text);
            out.put("dm_event_id", "sandbox-" + epochSeconds());
            return out;
        }
        if (!cfg.hasUserToken()) {
            throw new ValueError(
                    "user_access_token required for send_dm (scopes: dm.write, dm.read, tweet.read, users.read).");
        }
        return SocialHttp.postJson(
                X_BASE + "/dm_conversations/with/" + pathSegment(recipientId) + "/messages",
                null, authHeaders(cfg.userToken), Map.of("text", text), cfg.timeout);
    }

    private static Map<String, Object> searchTweets(Cfg cfg, Map<String, Object> in) throws Exception {
        String queryText = raw(in.get("query"));
        if (queryText.isEmpty()) {
            throw new ValueError("query required for search_tweets.");
        }
        if (!cfg.live()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "sandbox-tweet-search-1");
            row.put("author_id", "100");
            row.put("text", "Stub tweet matching '" + queryText + "'");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", "sandbox");
            out.put("query", queryText);
            out.put("data", List.of(row));
            return out;
        }
        String token = cfg.hasBearer() ? cfg.bearer : cfg.userToken;
        int limit = SocialSupport.toInt(in.get("limit"), 25);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("query", queryText);
        query.put("max_results", String.valueOf(Math.min(Math.max(limit, 10), 100)));
        query.put("tweet.fields", "author_id,created_at,public_metrics,lang,conversation_id");
        return SocialHttp.getJson(X_BASE + "/tweets/search/recent",
                query, authHeaders(token), cfg.timeout);
    }

    private static Map<String, Object> postTweet(Cfg cfg, Map<String, Object> in) throws Exception {
        String text = raw(in.get("body"));
        if (text.isEmpty()) {
            throw new ValueError("body required for post_tweet.");
        }
        if (!cfg.live()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", "sandbox");
            out.put("text", text);
            out.put("tweet_id", "sandbox-tw-" + epochSeconds());
            return out;
        }
        if (!cfg.hasUserToken()) {
            throw new ValueError("user_access_token with tweet.write scope required for post_tweet.");
        }
        return SocialHttp.postJson(X_BASE + "/tweets", null, authHeaders(cfg.userToken),
                Map.of("text", text), cfg.timeout);
    }

    private static Map<String, Object> replyTweet(Cfg cfg, Map<String, Object> in) throws Exception {
        String text = raw(in.get("body"));
        String parentId = SocialSupport.firstNonBlank(
                in.get("in_reply_to_tweet_id"), in.get("tweet_id"), in.get("parent_id"));
        if (text.isEmpty() || parentId.isEmpty()) {
            throw new ValueError(
                    "body and in_reply_to_tweet_id (or tweet_id/parent_id) required for reply_tweet.");
        }
        if (!cfg.live()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", "sandbox");
            out.put("text", text);
            out.put("in_reply_to_tweet_id", parentId);
            out.put("tweet_id", "sandbox-reply-" + epochSeconds());
            return out;
        }
        if (!cfg.hasUserToken()) {
            throw new ValueError("user_access_token with tweet.write scope required for reply_tweet.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("reply", Map.of("in_reply_to_tweet_id", parentId));
        return SocialHttp.postJson(X_BASE + "/tweets", null, authHeaders(cfg.userToken),
                body, cfg.timeout);
    }

    private static Map<String, Object> stats(Cfg cfg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", cfg.mode);
        out.put("base_url", cfg.live() ? X_BASE : "sandbox://x");
        out.put("has_bearer", cfg.hasBearer());
        out.put("has_user_token", cfg.hasUserToken());
        out.put("default_user_id", cfg.defaultUserId.isEmpty() ? null : cfg.defaultUserId);
        return out;
    }

    /** Authorization + JSON content type. Never log the returned map. */
    private static Map<String, String> authHeaders(String token) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Bearer " + (token == null ? "" : token));
        h.put("Content-Type", "application/json");
        return h;
    }

    /** Path-segment encode (Python concatenated the raw handle). */
    private static String pathSegment(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    /** Non-trimmed string so tweet/DM body whitespace is preserved. */
    private static String raw(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long epochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Python {@code ValueError} so {@code handle} prefixes {@code Error: ValueError: }.
     */
    static final class ValueError extends Exception {
        ValueError(String message) {
            super(message);
        }
    }

    /** Resolved config. Do not log; fields include secrets. */
    private static final class Cfg {
        final String mode;
        final String bearer;
        final String userToken;
        final int timeout;
        final String defaultUserId;

        private Cfg(String mode, String bearer, String userToken, int timeout, String defaultUserId) {
            this.mode = mode;
            this.bearer = bearer;
            this.userToken = userToken;
            this.timeout = timeout;
            this.defaultUserId = defaultUserId;
        }

        static Cfg from(Map<String, Object> params, Map<String, Object> in) {
            String mode = SocialSupport.lower(params.get("mode"));
            if (mode.isEmpty()) {
                mode = "sandbox";
            }
            // Per-call override only for mode — never for tokens.
            String override = SocialSupport.lower(in.get("mode"));
            if (!override.isEmpty()) {
                mode = override;
            }
            return new Cfg(
                    mode,
                    SocialSupport.str(params.get("bearer_token")),
                    SocialSupport.str(params.get("user_access_token")),
                    SocialSupport.timeoutSeconds(params, DEFAULT_TIMEOUT),
                    SocialSupport.str(params.get("default_user_id")));
        }

        boolean live() {
            return "live".equals(mode);
        }

        boolean hasBearer() {
            return !bearer.isEmpty();
        }

        boolean hasUserToken() {
            return !userToken.isEmpty();
        }
    }
}
