package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.web.WebNodeSupport;
import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MCP client for one remote server over Streamable HTTP (protocol 2025-06-18).
 *
 * <p>Each invoke opens a session ({@code initialize} + {@code notifications/initialized}),
 * runs one JSON-RPC method, then {@code DELETE}s the session when the server issued one.
 * A response may be a JSON object or a single {@code text/event-stream} body on that same
 * POST. The legacy SSE transport (GET plus a second message URL) and stdio are not
 * implemented: stdio would spawn a local process, which is a second command-exec path.
 * The auth token is password-typed config and is never logged.
 * One invoke equals one session, so no session state is kept between workflow runs.
 */
public final class McpServerNode implements NodeHandler {

    public static final String DEFAULT_ID = "82fea967-578c-4d83-96a4-fed18627f764";

    static final int DEFAULT_TIMEOUT = 30;
    static final int MAX_TIMEOUT = 60;
    static final int MAX_BODY = 1_048_576;
    static final int MAX_ARGUMENTS = 262_144;
    static final String DEFAULT_PROTOCOL = "2025-06-18";

    private static final Set<String> PROTOCOLS = Set.of(
            "2024-11-05", "2025-03-26", "2025-06-18");
    private static final Set<String> OPS = Set.of(
            "list_tools", "call_tool", "list_resources", "read_resource",
            "list_resource_templates", "list_prompts", "get_prompt", "ping");
    private static final Set<String> AUTH = Set.of("none", "bearer", "header");
    private static final Set<String> DENY_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "accept",
            "content-type", "authorization", "mcp-protocol-version", "mcp-session-id",
            "cookie", "proxy-authorization", "upgrade", "origin");

    private final String templateId;

    public McpServerNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    public McpServerNode() {
        this(DEFAULT_ID);
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        String token = null;
        try {
            if (request == null) {
                return DataSupport.invalidInput("request body must be a JSON object");
            }
            Map<String, Object> cfg = DataSupport.readConfig(request);
            Map<String, Object> in = DataSupport.readInput(request, helper);
            token = DataSupport.cfgStr(cfg, "auth_token", null);
            return execute(cfg, in, token);
        } catch (StudioQuackError e) {
            throw redactError(e, token);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return DataSupport.fail(DataSupport.redact(safeMsg(e), token));
        }
    }

    private static Map<String, Object> execute(Map<String, Object> cfg, Map<String, Object> in, String token)
            throws Exception {
        String op = operation(in, cfg);
        URI uri = parseServerUrl(firstNonBlank(
                DataSupport.inStr(in, "server_url", null),
                DataSupport.cfgStr(cfg, "server_url", null)), token);
        String authType = authType(cfg);
        String headerName = authHeader(cfg, authType);
        requireToken(authType, token);
        rejectControl(token, "auth_token");
        String protocol = protocol(cfg);
        int timeout = clampTimeout(DataSupport.cfgInt(cfg, "timeout_seconds", DEFAULT_TIMEOUT));
        boolean tls = WebNodeSupport.verifyTls(cfg.get("verify_tls"), true);
        Map<String, Object> params = paramsFor(op, in);
        String origin = originOf(uri);

        Budget budget = new Budget(timeout);
        String session = null;
        try {
            WebNodeSupport.HttpResult init = post(uri, headers(protocol, origin, null, authType, token, headerName),
                    rpc(1, "initialize", initParams(protocol)), budget.take(), tls);
            ensure2xx(init, token);
            session = sessionId(init);
            Map<String, Object> initResult = rpcResult(init, 1);
            protocol = negotiated(initResult, protocol);

            WebNodeSupport.HttpResult ack = post(uri, headers(protocol, origin, session, authType, token, headerName),
                    notifyInitialized(), budget.take(), tls);
            assertNotify(ack, token);

            WebNodeSupport.HttpResult opResp = post(uri,
                    headers(protocol, origin, session, authType, token, headerName),
                    rpc(2, methodOf(op), params), budget.take(), tls);
            ensure2xx(opResp, token);
            return ok(rpcResult(opResp, 2), opResp.statusCode);
        } finally {
            if (session != null) {
                deleteSession(uri, headers(protocol, origin, session, authType, token, headerName),
                        budget, tls);
            }
        }
    }

    static String operation(Map<String, Object> in, Map<String, Object> cfg) {
        String op = firstNonBlank(DataSupport.inStr(in, "operation", null),
                DataSupport.cfgStr(cfg, "operation", "list_tools"));
        if (op == null || op.isBlank()) {
            op = "list_tools";
        }
        op = op.trim().toLowerCase(Locale.ROOT);
        if (!OPS.contains(op)) {
            throw invalid("Unknown operation: " + op
                    + ". Accepted: list_tools, call_tool, list_resources, read_resource, "
                    + "list_resource_templates, list_prompts, get_prompt, ping");
        }
        return op;
    }

    static URI parseServerUrl(String raw, String token) {
        if (raw == null || raw.isBlank()) {
            throw invalid("server_url is required");
        }
        String t = raw.trim();
        if (t.length() > 2048 || hasControl(t)) {
            throw invalid("server_url is invalid");
        }
        if (token != null && token.length() >= 4 && t.contains(token)) {
            throw invalid("server_url must not contain the auth token");
        }
        URI uri;
        try {
            uri = URI.create(t);
        } catch (IllegalArgumentException e) {
            throw invalid("server_url is invalid");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw invalid("server_url must be http or https");
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid("server_url must not contain userinfo");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid("server_url host is required");
        }
        return uri;
    }

    private static Map<String, Object> paramsFor(String op, Map<String, Object> in) {
        return switch (op) {
            case "call_tool" -> callParams(bounded(in, "tool_name", 256, "tool_name"),
                    objectField(in, "arguments"));
            case "read_resource" -> Map.of("uri", bounded(in, "uri", 2048, "uri"));
            case "get_prompt" -> promptParams(bounded(in, "prompt_name", 256, "prompt_name"),
                    objectField(in, "prompt_arguments"));
            case "list_tools", "list_resources", "list_resource_templates", "list_prompts" -> cursorParams(in);
            default -> Map.of();
        };
    }

    private static Map<String, Object> callParams(String name, Map<String, Object> arguments) {
        rejectHuge(arguments, "arguments");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments);
        return params;
    }

    private static Map<String, Object> promptParams(String name, Map<String, Object> arguments) {
        rejectHuge(arguments, "prompt_arguments");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        if (!arguments.isEmpty()) {
            params.put("arguments", arguments);
        }
        return params;
    }

    private static Map<String, Object> cursorParams(Map<String, Object> in) {
        String cursor = DataSupport.inStr(in, "cursor", null);
        if (cursor == null || cursor.isBlank()) {
            return Map.of();
        }
        if (cursor.length() > 1024 || hasControl(cursor)) {
            throw invalid("cursor is invalid");
        }
        return Map.of("cursor", cursor);
    }

    private static String methodOf(String op) {
        return switch (op) {
            case "list_tools" -> "tools/list";
            case "call_tool" -> "tools/call";
            case "list_resources" -> "resources/list";
            case "read_resource" -> "resources/read";
            case "list_resource_templates" -> "resources/templates/list";
            case "list_prompts" -> "prompts/list";
            case "get_prompt" -> "prompts/get";
            default -> "ping";
        };
    }

    private static Map<String, Object> initParams(String protocol) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", protocol);
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", "studioquack", "version", "1"));
        return params;
    }

    private static String rpc(int id, String method, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        body.put("params", params);
        return Json.write(body);
    }

    private static String notifyInitialized() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("method", "notifications/initialized");
        body.put("params", Map.of());
        return Json.write(body);
    }

    static String authType(Map<String, Object> cfg) {
        String raw = DataSupport.cfgStr(cfg, "auth_type", "none");
        String type = raw == null || raw.isBlank() ? "none" : raw.trim().toLowerCase(Locale.ROOT);
        if (!AUTH.contains(type)) {
            throw invalid("Unknown auth_type: " + type + ". Accepted: none, bearer, header");
        }
        return type;
    }

    static String authHeader(Map<String, Object> cfg, String authType) {
        if (!"header".equals(authType)) {
            return "X-Api-Key";
        }
        String name = DataSupport.cfgStr(cfg, "auth_header", "X-Api-Key");
        if (name == null || name.isBlank()) {
            name = "X-Api-Key";
        }
        name = name.trim();
        if (!name.matches("[A-Za-z0-9-]{1,64}") || DENY_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            throw invalid("auth_header is invalid");
        }
        return name;
    }

    private static void requireToken(String authType, String token) {
        if (!"none".equals(authType) && (token == null || token.isBlank())) {
            throw invalid("auth_token is required for auth_type " + authType);
        }
    }

    static String protocol(Map<String, Object> cfg) {
        String raw = DataSupport.cfgStr(cfg, "protocol_version", DEFAULT_PROTOCOL);
        String version = raw == null || raw.isBlank() ? DEFAULT_PROTOCOL : raw.trim();
        if (!PROTOCOLS.contains(version)) {
            throw invalid("protocol_version is not supported. Accepted: "
                    + "2024-11-05, 2025-03-26, 2025-06-18");
        }
        return version;
    }

    private static String negotiated(Map<String, Object> initResult, String requested) {
        Object pv = initResult.get("protocolVersion");
        if (pv == null || String.valueOf(pv).isBlank()) {
            return requested;
        }
        String got = String.valueOf(pv).trim();
        if (!PROTOCOLS.contains(got)) {
            throw new StudioQuackError("REQUEST_FAILED", "MCP server protocol is not supported");
        }
        return got;
    }

    private static Map<String, String> headers(String protocol, String origin, String session,
                                               String authType, String token, String headerName) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/event-stream");
        headers.put("Content-Type", "application/json");
        headers.put("MCP-Protocol-Version", protocol);
        headers.put("Origin", origin);
        if (session != null && !session.isBlank()) {
            headers.put("Mcp-Session-Id", session);
        }
        if ("bearer".equals(authType)) {
            headers.put("Authorization", "Bearer " + token);
        } else if ("header".equals(authType)) {
            headers.put(headerName, token);
        }
        return headers;
    }

    private static WebNodeSupport.HttpResult post(URI uri, Map<String, String> headers, String json,
                                                  int timeout, boolean tls) throws Exception {
        return WebNodeSupport.send("POST", uri, headers, WebNodeSupport.utf8(json), timeout, tls,
                MAX_BODY, HttpClient.Redirect.NEVER);
    }

    private static void deleteSession(URI uri, Map<String, String> headers, Budget budget, boolean tls) {
        int seconds = budget.remainingSeconds();
        if (seconds <= 0) {
            return;
        }
        Map<String, String> h = new LinkedHashMap<>(headers);
        h.remove("Content-Type");
        try {
            WebNodeSupport.send("DELETE", uri, h, null, Math.min(seconds, 5), tls,
                    MAX_BODY, HttpClient.Redirect.NEVER);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // Session end is best-effort. A failed DELETE must not hide the tool result.
        }
    }

    private static void ensure2xx(WebNodeSupport.HttpResult resp, String token) {
        if (resp.truncated) {
            throw new StudioQuackError("REQUEST_FAILED", "MCP response exceeded 1 MiB");
        }
        if (resp.statusCode >= 200 && resp.statusCode < 300) {
            return;
        }
        if (resp.statusCode >= 300 && resp.statusCode < 400) {
            throw new StudioQuackError("HTTP_" + resp.statusCode,
                    "HTTP " + resp.statusCode, Map.of("http_status", resp.statusCode));
        }
        throw httpFail(resp, token);
    }

    private static void assertNotify(WebNodeSupport.HttpResult resp, String token) {
        ensure2xx(resp, token);
        if (resp.body == null || resp.body.isBlank()) {
            return;
        }
        for (Map<String, Object> msg : tryMessages(resp)) {
            if (msg.get("error") != null) {
                throw rpcFail(msg.get("error"));
            }
        }
    }

    static Map<String, Object> rpcResult(WebNodeSupport.HttpResult resp, int id) {
        if (resp.truncated) {
            throw new StudioQuackError("REQUEST_FAILED", "MCP response exceeded 1 MiB");
        }
        List<Map<String, Object>> messages = messages(resp);
        Map<String, Object> match = null;
        for (Map<String, Object> msg : messages) {
            if (sameId(msg.get("id"), id)) {
                match = msg;
            }
        }
        if (match == null) {
            throw new StudioQuackError("REQUEST_FAILED", "MCP response did not include jsonrpc id " + id);
        }
        if (!"2.0".equals(String.valueOf(match.get("jsonrpc")))) {
            throw new StudioQuackError("REQUEST_FAILED", "MCP response was not JSON-RPC");
        }
        if (match.get("error") != null) {
            throw rpcFail(match.get("error"));
        }
        if (!match.containsKey("result")) {
            throw new StudioQuackError("REQUEST_FAILED", "MCP response had no result");
        }
        Object result = match.get("result");
        if (result instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(Json.asMap(map));
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        if (result != null) {
            wrap.put("value", result);
        }
        return wrap;
    }

    private static List<Map<String, Object>> messages(WebNodeSupport.HttpResult resp) {
        List<Map<String, Object>> messages = tryMessages(resp);
        if (messages.isEmpty()) {
            throw new StudioQuackError("REQUEST_FAILED", "MCP response was not JSON-RPC");
        }
        return messages;
    }

    private static List<Map<String, Object>> tryMessages(WebNodeSupport.HttpResult resp) {
        String body = resp.body == null ? "" : resp.body;
        try {
            if (sse(resp.contentType, body)) {
                return parseSse(body);
            }
            Map<String, Object> one = Json.readObject(body);
            if (one.isEmpty() || !one.containsKey("jsonrpc")) {
                return List.of();
            }
            return List.of(one);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    static List<Map<String, Object>> parseSse(String body) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return out;
        }
        String norm = body.replace("\r\n", "\n").replace('\r', '\n');
        for (String block : norm.split("\n\n")) {
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                String rest = line.substring(5);
                if (rest.startsWith(" ")) {
                    rest = rest.substring(1);
                }
                data.append(rest);
            }
            String payload = data.toString().trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                continue;
            }
            try {
                Object parsed = Json.parse(payload);
                if (parsed instanceof Map<?, ?> map && map.containsKey("jsonrpc")) {
                    out.add(new LinkedHashMap<>(Json.asMap(map)));
                }
            } catch (RuntimeException ignored) {
                // A non-JSON SSE frame (comment, endpoint noise) is not a result.
            }
        }
        return out;
    }

    private static boolean sse(String contentType, String body) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/event-stream")) {
            return true;
        }
        String t = body.stripLeading();
        return t.startsWith("data:") || t.startsWith("event:");
    }

    static boolean sameId(Object actual, int expected) {
        if (actual instanceof Number n) {
            return n.longValue() == expected && n.doubleValue() == expected;
        }
        if (actual instanceof String s) {
            try {
                return Long.parseLong(s.trim()) == expected;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static String sessionId(WebNodeSupport.HttpResult resp) {
        String raw = resp.header("Mcp-Session-Id").orElse("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.length() > 256 || hasControl(raw)) {
            throw new StudioQuackError("REQUEST_FAILED", "MCP session id is invalid");
        }
        return raw;
    }

    private static StudioQuackError rpcFail(Object error) {
        String message = "MCP request failed";
        if (error instanceof Map<?, ?> map) {
            Object msg = map.get("message");
            if (msg != null && !String.valueOf(msg).isBlank()) {
                message = String.valueOf(msg).trim();
            } else if (map.get("code") != null) {
                message = "MCP error " + map.get("code");
            }
        }
        return new StudioQuackError("REQUEST_FAILED", cap(message));
    }

    private static StudioQuackError httpFail(WebNodeSupport.HttpResult resp, String token) {
        String msg = DataSupport.httpStatusMessage(resp.statusCode, resp.body);
        if (!msg.contains("HTTP " + resp.statusCode)) {
            msg = "HTTP " + resp.statusCode + (msg.isBlank() ? "" : ": " + msg);
        }
        return DataSupport.failError(cap(DataSupport.redact(msg, token)));
    }

    private static String originOf(URI uri) {
        String host = uri.getHost();
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        String origin = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host;
        if (uri.getPort() > 0) {
            origin += ":" + uri.getPort();
        }
        return origin;
    }

    static Map<String, Object> objectField(Map<String, Object> in, String key) {
        Object v = in == null ? null : in.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            return new LinkedHashMap<>();
        }
        if (v instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(Json.asMap(map));
        }
        if (v instanceof String s) {
            try {
                Object parsed = Json.parse(s);
                if (parsed instanceof Map<?, ?> map) {
                    return new LinkedHashMap<>(Json.asMap(map));
                }
            } catch (RuntimeException e) {
                throw invalid(key + " must be a JSON object");
            }
            throw invalid(key + " must be a JSON object");
        }
        throw invalid(key + " must be a JSON object");
    }

    private static void rejectHuge(Map<String, Object> value, String key) {
        if (Json.write(value).length() > MAX_ARGUMENTS) {
            throw invalid(key + " is too large");
        }
    }

    private static String bounded(Map<String, Object> in, String key, int max, String label) {
        String value = DataSupport.inStr(in, key, null);
        if (value == null || value.isBlank()) {
            throw invalid(label + " is required");
        }
        if (value.length() > max || hasControl(value)) {
            throw invalid(label + " is invalid");
        }
        return value;
    }

    private static void rejectControl(String value, String label) {
        if (value != null && hasControl(value)) {
            throw invalid(label + " is invalid");
        }
    }

    private static boolean hasControl(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isISOControl(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    static int clampTimeout(int seconds) {
        if (seconds < 1) {
            return DEFAULT_TIMEOUT;
        }
        return Math.min(seconds, MAX_TIMEOUT);
    }

    private static Map<String, Object> ok(Map<String, Object> data, int httpStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("data", data);
        body.put("http_status", httpStatus);
        return DataSupport.ports(body);
    }

    private static StudioQuackError invalid(String message) {
        return new StudioQuackError("INVALID_INPUT",
                message == null || message.isBlank() ? "invalid input" : message);
    }

    private static StudioQuackError redactError(StudioQuackError e, String token) {
        String msg = DataSupport.redact(e.getMessage(), token);
        if (msg.equals(e.getMessage())) {
            return e;
        }
        return new StudioQuackError(e.errorCode(), msg, e.data());
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + m;
    }

    private static String cap(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= 500 ? t : t.substring(0, 500);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    /** Whole-invoke budget. Each HTTP call receives only the seconds still left. */
    private static final class Budget {
        private final long deadlineNanos;

        Budget(int seconds) {
            this.deadlineNanos = System.nanoTime() + seconds * 1_000_000_000L;
        }

        int take() {
            int left = remainingSeconds();
            if (left <= 0) {
                throw new StudioQuackError("REQUEST_FAILED", "MCP call timed out");
            }
            return left;
        }

        int remainingSeconds() {
            long leftNs = deadlineNanos - System.nanoTime();
            if (leftNs < 1_000_000_000L) {
                return 0;
            }
            return (int) Math.min(leftNs / 1_000_000_000L, MAX_TIMEOUT);
        }
    }
}
