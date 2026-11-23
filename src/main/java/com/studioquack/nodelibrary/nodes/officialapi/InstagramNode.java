package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.StudioQuackHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Instagram Connector — Meta Graph API client for an Instagram Professional
 * account linked to a Facebook Page.
 *
 * <p>Live calls go to {@code https://graph.facebook.com/{version}/...} with the
 * Page Access Token as the {@code access_token} query parameter (never an
 * {@code Authorization} header). Sandbox mode returns stub responses with no HTTP.
 *
 * <p><b>Secrets:</b> the PAT is read only from {@code configurationParameters}
 * ({@code page_access_token}); {@code input.page_access_token} is ignored, and
 * the token is never logged or returned.
 *
 * <p><b>Version guard:</b> {@code version} must match {@code v} plus digits/dots
 * or it defaults to {@code v26.0} to prevent path injection.
 *
 * <p>Thread-safe: no mutable instance state besides the final template id.
 * {@link #handle} never throws.
 */
public final class InstagramNode implements NodeHandler {

    /** Bundled definition {@code _id}; {@code SQ_TEMPLATE_ID_INSTAGRAM} may override at register time. */
    static final String DEFAULT_ID = "a733c45d-a5b1-42e6-ac5c-2dc7a7770b65";

    private static final String GRAPH_BASE = "https://graph.facebook.com";
    private static final String DEFAULT_VERSION = "v26.0";
    private static final String MISSING_PAT =
            "page_access_token required for live mode (derive from /me/accounts with a User Token holding instagram_manage_messages scope).";

    /** Alphabetically sorted — matches Python {@code sorted(ACTIONS.keys())}. */
    private static final List<String> VALID_ACTIONS = List.of(
            "get_account_info",
            "list_comments",
            "list_conversations",
            "list_messages",
            "reply_comment",
            "send_message",
            "stats");

    /** Graph path version: {@code v26.0}, {@code v25.0}, {@code v23.0}. Rejects {@code ../} and extra path. */
    private static final Pattern GRAPH_VERSION = Pattern.compile("^v[0-9]+(?:\\.[0-9]+)*$");

    /** Python {@code time.strftime("%Y-%m-%dT%H:%M:%S+0000", time.gmtime())}. Immutable / thread-safe. */
    private static final DateTimeFormatter GRAPH_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss+0000")
            .withZone(ZoneOffset.UTC);

    private final String templateId;

    public InstagramNode() {
        this(DEFAULT_ID);
    }

    public InstagramNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Dispatch one invoke. Reply is keyed only by OUTPUT port {@code output}.
     * Never logs {@code helperToken} or the Page Access Token.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper unused) {
        try {
            Map<String, Object> params = SocialSupport.nodeParams(request);
            Map<String, Object> in = SocialSupport.inputPort(request);

            String mode = SocialSupport.lower(SocialSupport.cfg(params, "mode", "sandbox"));
            String modeOverride = SocialSupport.lower(in.get("mode"));
            if (!modeOverride.isEmpty()) {
                mode = modeOverride;
            }

            // PAT: configurationParameters only. input.page_access_token is intentionally ignored.
            String token = SocialSupport.str(params.get("page_access_token"));
            String version = sanitizeVersion(SocialSupport.cfg(params, "version", DEFAULT_VERSION));
            int timeout = SocialSupport.timeoutSeconds(params, 25);
            String defaultPageId = SocialSupport.str(params.get("default_page_id"));
            String defaultIgId = SocialSupport.str(params.get("default_ig_business_id"));

            String action = SocialSupport.lower(in.get("action"));
            if (action.isEmpty()) {
                action = "stats";
            }

            boolean live = "live".equals(mode);
            if (live && token.isEmpty() && !"stats".equals(action)) {
                return DataSupport.invalidInput(MISSING_PAT);
            }
            if (!VALID_ACTIONS.contains(action)) {
                return DataSupport.invalidInput(unknownAction(action));
            }

            Cfg cfg = new Cfg(mode, token, version, timeout, defaultPageId, defaultIgId);
            Map<String, Object> result = dispatch(action, cfg, in);
            return SocialSupport.ports(ok(action, result));
        } catch (ValueError e) {
            return DataSupport.fail("Error: ValueError: " + e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            // SocialHttp.ApiException and transport failures share Python's Exception handler shape.
            return DataSupport.fail(
                    "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static Map<String, Object> dispatch(String action, Cfg cfg, Map<String, Object> in)
            throws ValueError, SocialHttp.ApiException {
        return switch (action) {
            case "get_account_info" -> getAccountInfo(cfg, in);
            case "list_conversations" -> listConversations(cfg, in);
            case "list_messages" -> listMessages(cfg, in);
            case "send_message" -> sendMessage(cfg, in);
            case "list_comments" -> listComments(cfg, in);
            case "reply_comment" -> replyComment(cfg, in);
            default -> stats(cfg);
        };
    }

    private static Map<String, Object> getAccountInfo(Cfg cfg, Map<String, Object> in)
            throws ValueError, SocialHttp.ApiException {
        String igId = SocialSupport.firstNonBlank(in.get("ig_business_id"), cfg.defaultIgId);
        if (!cfg.live()) {
            Map<String, Object> stub = new LinkedHashMap<>();
            stub.put("mode", "sandbox");
            stub.put("id", igId.isEmpty() ? "stub-ig-id" : igId);
            stub.put("username", "studioquack_ig");
            stub.put("name", "StudioQuack");
            stub.put("followers_count", 1234);
            stub.put("media_count", 42);
            return stub;
        }
        if (igId.isEmpty()) {
            throw new ValueError("ig_business_id required (or default_ig_business_id config).");
        }
        return graphGet(cfg, seg(igId), Map.of(
                "fields", "id,username,name,followers_count,media_count,profile_picture_url,biography"));
    }

    private static Map<String, Object> listConversations(Cfg cfg, Map<String, Object> in)
            throws ValueError, SocialHttp.ApiException {
        String pageId = SocialSupport.firstNonBlank(in.get("page_id"), cfg.defaultPageId);
        int limit = SocialSupport.toInt(in.get("limit"), 25);
        if (!cfg.live()) {
            Map<String, Object> stub = new LinkedHashMap<>();
            stub.put("mode", "sandbox");
            stub.put("page_id", pageId.isEmpty() ? "stub-page" : pageId);
            stub.put("data", sandboxConversations(limit));
            return stub;
        }
        if (pageId.isEmpty()) {
            throw new ValueError("page_id required (or default_page_id config).");
        }
        Map<String, String> q = new LinkedHashMap<>();
        q.put("platform", "instagram");
        q.put("fields", "id,participants,updated_time");
        q.put("limit", String.valueOf(limit));
        return graphGet(cfg, seg(pageId) + "/conversations", q);
    }

    private static Map<String, Object> listMessages(Cfg cfg, Map<String, Object> in)
            throws ValueError, SocialHttp.ApiException {
        String convId = SocialSupport.str(in.get("conversation_id"));
        if (convId.isEmpty()) {
            throw new ValueError("conversation_id required.");
        }
        if (!cfg.live()) {
            Map<String, Object> from = new LinkedHashMap<>();
            from.put("id", "igsid-stub");
            from.put("username", "prospect");
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("id", "sandbox-msg-1");
            msg.put("message", "Sandbox inbound DM");
            msg.put("from", from);
            msg.put("created_time", GRAPH_TIME.format(Instant.now()));
            Map<String, Object> stub = new LinkedHashMap<>();
            stub.put("mode", "sandbox");
            stub.put("conversation_id", convId);
            stub.put("data", List.of(msg));
            return stub;
        }
        return graphGet(cfg, seg(convId), Map.of("fields", "messages{id,message,from,to,created_time}"));
    }

    private static Map<String, Object> sendMessage(Cfg cfg, Map<String, Object> in)
            throws ValueError, SocialHttp.ApiException {
        String pageId = SocialSupport.firstNonBlank(in.get("page_id"), cfg.defaultPageId);
        String igsid = SocialSupport.str(in.get("recipient_igsid"));
        String text = rawStr(in.get("body"));
        if (igsid.isEmpty() || text.isEmpty()) {
            throw new ValueError("recipient_igsid and body required for send_message.");
        }
        if (!cfg.live()) {
            Map<String, Object> stub = new LinkedHashMap<>();
            stub.put("mode", "sandbox");
            stub.put("page_id", pageId.isEmpty() ? "stub-page" : pageId);
            stub.put("recipient_igsid", igsid);
            stub.put("message_id", "sandbox-" + Instant.now().getEpochSecond());
            stub.put("text", text);
            return stub;
        }
        if (pageId.isEmpty()) {
            throw new ValueError("page_id required (or default_page_id config).");
        }
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("id", igsid);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("text", text);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipient", recipient);
        body.put("message", message);
        return graphPost(cfg, seg(pageId) + "/messages", body);
    }

    private static Map<String, Object> listComments(Cfg cfg, Map<String, Object> in)
            throws ValueError, SocialHttp.ApiException {
        String mediaId = SocialSupport.str(in.get("media_id"));
        if (!cfg.live()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "sandbox-comment-1");
            row.put("text", "Sandbox comment");
            row.put("username", "prospect");
            Map<String, Object> stub = new LinkedHashMap<>();
            stub.put("mode", "sandbox");
            stub.put("media_id", mediaId.isEmpty() ? "stub-media" : mediaId);
            stub.put("data", List.of(row));
            return stub;
        }
        if (mediaId.isEmpty()) {
            throw new ValueError("media_id required for list_comments.");
        }
        Map<String, String> q = new LinkedHashMap<>();
        q.put("fields", "id,text,username,timestamp,like_count");
        q.put("limit", String.valueOf(SocialSupport.toInt(in.get("limit"), 25)));
        return graphGet(cfg, seg(mediaId) + "/comments", q);
    }

    private static Map<String, Object> replyComment(Cfg cfg, Map<String, Object> in)
            throws ValueError, SocialHttp.ApiException {
        String commentId = SocialSupport.str(in.get("comment_id"));
        String text = rawStr(in.get("body"));
        if (commentId.isEmpty() || text.isEmpty()) {
            throw new ValueError("comment_id and body required for reply_comment.");
        }
        if (!cfg.live()) {
            Map<String, Object> stub = new LinkedHashMap<>();
            stub.put("mode", "sandbox");
            stub.put("comment_id", commentId);
            stub.put("text", text);
            stub.put("reply_id", "sandbox-reply-" + Instant.now().getEpochSecond());
            return stub;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", text);
        return graphPost(cfg, seg(commentId) + "/replies", body);
    }

    /** Never includes the PAT. */
    private static Map<String, Object> stats(Cfg cfg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", cfg.mode);
        result.put("base_url", cfg.live() ? GRAPH_BASE + "/" + cfg.version : "sandbox://instagram");
        result.put("default_page_id", cfg.defaultPageId.isEmpty() ? null : cfg.defaultPageId);
        result.put("default_ig_business_id", cfg.defaultIgId.isEmpty() ? null : cfg.defaultIgId);
        return result;
    }

    /**
     * GET Graph. Token is a query param only — no {@code Authorization} header.
     */
    private static Map<String, Object> graphGet(Cfg cfg, String path, Map<String, String> extra)
            throws SocialHttp.ApiException {
        Map<String, String> query = new LinkedHashMap<>();
        if (extra != null) {
            query.putAll(extra);
        }
        // Token last so extra query keys cannot replace it. Never Authorization header.
        query.put("access_token", cfg.token);
        return SocialHttp.getJson(GRAPH_BASE + "/" + cfg.version + "/" + path, query, Map.of(), cfg.timeout);
    }

    /**
     * POST JSON to Graph. Token is a query param only — no {@code Authorization} header.
     */
    private static Map<String, Object> graphPost(Cfg cfg, String path, Object jsonBody)
            throws SocialHttp.ApiException {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("access_token", cfg.token);
        return SocialHttp.postJson(
                GRAPH_BASE + "/" + cfg.version + "/" + path, query, Map.of(), jsonBody, cfg.timeout);
    }

    private static List<Object> sandboxConversations(int limit) {
        int n = Math.min(limit, 3);
        if (n < 0) {
            n = 0;
        }
        String now = GRAPH_TIME.format(Instant.now());
        List<Object> data = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> participant = new LinkedHashMap<>();
            participant.put("id", "igsid-stub-" + i);
            participant.put("username", "prospect" + i);
            Map<String, Object> participants = new LinkedHashMap<>();
            participants.put("data", List.of(participant));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "sandbox-conv-" + i);
            row.put("participants", participants);
            row.put("updated_time", now);
            data.add(row);
        }
        return data;
    }

    private static Map<String, Object> ok(String action, Map<String, Object> result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("action", action);
        out.put("result", result);
        return out;
    }

    /** Python {@code f"Unknown action '{a}'. Valid: {sorted(ACTIONS.keys())}"}. */
    private static String unknownAction(String action) {
        StringBuilder sb = new StringBuilder("Unknown action '").append(action).append("'. Valid: [");
        for (int i = 0; i < VALID_ACTIONS.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('\'').append(VALID_ACTIONS.get(i)).append('\'');
        }
        return sb.append(']').toString();
    }

    /** Accept only {@code v} + digits/dots; otherwise {@code v26.0}. */
    static String sanitizeVersion(String raw) {
        String v = SocialSupport.str(raw);
        return GRAPH_VERSION.matcher(v).matches() ? v : DEFAULT_VERSION;
    }

    /** Path-segment encode so ids cannot inject extra Graph path components. */
    private static String seg(String raw) {
        return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Body text without trim — Python treats whitespace-only {@code body} as present. */
    private static String rawStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Per-invoke snapshot. Do not log — {@code token} is the PAT. */
    private record Cfg(String mode, String token, String version, int timeout,
                       String defaultPageId, String defaultIgId) {
        boolean live() {
            return "live".equals(mode);
        }
    }

    /** Action-level validation; handler maps this to {@code Error: ValueError: ...}. */
    private static final class ValueError extends Exception {
        ValueError(String message) {
            super(message);
        }
    }
}
