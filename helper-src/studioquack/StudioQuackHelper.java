package studioquack;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java sibling of the in-sandbox Python {@code StudioQuackHelper} ({@code helper.py}).
 *
 * <p>Copy this {@code studioquack} package into a Build-tab workspace. JDK 21 + {@code curl}
 * only — no Maven deps. Context is local (decoded {@code SQ_CONTEXT} or the per-request
 * map); side effects go through {@link HelperTransport} (Unix socket + bearer). Helper
 * calls never throw: missing token/socket is a silent no-op so a laptop dry-run still
 * prints stdout markers.
 *
 * <h2>Quick start — stateless job (default)</h2>
 * <pre>{@code
 * public static void main(String[] args) {
 *     StudioQuackHelper helper = StudioQuackHelper.fromEnv();
 *     List&lt;Object&gt; ins = helper.getInputValuesByPortValue("in");
 *     Map&lt;String, Object&gt; in = ins.isEmpty() ? Map.of() : Json.asMap(ins.get(0));
 *     int sum = toInt(in.get("a")) + toInt(in.get("b"));
 *     helper.execute("log", Map.of("sum", sum));
 *     helper.returnResult("sum", sum); // marker + POST /result, then exit 0
 * }
 * }</pre>
 *
 * <h2>Quick start — stateful listener</h2>
 * <pre>{@code
 * // Config param runtime_mode = "stateful" (or env SQ_STATEFUL_RUNNER=1).
 * HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
 * s.createContext(path, ex -&gt; {
 *     Map&lt;String, Object&gt; req = Json.readObject(new String(ex.getRequestBody().readAllBytes()));
 *     helper.setRequestContext(Json.asMap(req.get("executionContext")),
 *             Json.str(req.get("helperToken")));
 *     helper.log("invoke " + req.get("requestId"), "info");
 *     int sum = ...; // from inputVariables
 *     byte[] out = Json.write(Map.of("sum", sum)).getBytes(UTF_8);
 *     ex.sendResponseHeaders(200, out.length);
 *     ex.getResponseBody().write(out);
 * });
 * s.start(); // do not exit
 * }</pre>
 *
 * <p>Bearer: {@code helperToken} field / {@code SQ_HELPER_BEARER} / {@code SQVM_HELPER_TOKEN}.
 * Socket: {@code SQ_HELPER_SOCKET}. Never log those values. Context accessors are inherited
 * from {@link ExecutionContextStore}.
 *
 * <p>Typed failures: throw {@link StudioQuackError} or call {@link #fail}. The
 * dispatcher folds either into {@code POST /v1/helper/fail} so the daemon raises
 * a BPMN error. Stateless callers must {@code System.exit(1)} after {@code fail}
 * — this method never exits the process.
 */
public final class StudioQuackHelper extends ExecutionContextStore {

    /** Process-level cancel used by the SIGTERM shutdown hook (stateless jobs). */
    private static final RequestCancellation PROCESS_CANCEL = new RequestCancellation();
    private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean(false);

    private final HelperTransport transport = new HelperTransport();
    private final LlmHelper llm = new LlmHelper(transport);

    /** Per-request cancel (stateful). Null → fall back to {@link #PROCESS_CANCEL}. */
    private RequestCancellation requestCancel;

    /** Last GET /v1/helper/cancelled result; poll is capped at 1/s. */
    private volatile long lastCancelPollMs;
    private volatile boolean lastCancelPollResult;

    /** Maximum events accepted per single webhook POST invocation (DoS / fan-out bound). */
    public static final int MAX_WEBHOOK_EVENTS = 100;

    /** Invocation envelope fields populated for webhook ingress mode (or normal). */
    private volatile String invocationMode = "normal";
    private volatile String activeSelectRole = "";
    private volatile Map<String, Object> webhookRequest;
    private volatile String recordedCorrelationKey;
    private volatile Map<String, Object> recordedWebhookOutcome;

    /**
     * Wrap an already-decoded execution-context map (stateful per-request use).
     *
     * <p>Use this when the daemon POSTed {@code executionContext} on {@code /invoke}.
     *
     * <pre>{@code
     * StudioQuackHelper helper = new StudioQuackHelper(Json.asMap(req.get("executionContext")));
     * }</pre>
     *
     * @param executionContext decoded context; {@code null} becomes an empty map
     */
    public StudioQuackHelper(Map<String, Object> executionContext) {
        super(executionContext);
        bindIngressFromMap(executionContext);
    }

    /**
     * Decode {@code SQ_CONTEXT} = Base64(JSON) from the process environment (stateless jobs).
     *
     * <p>Use this as {@code main}'s first call on a one-shot image. Missing/invalid context
     * yields an empty map (never throws) so a stateful entrypoint can still start.
     *
     * <pre>{@code
     * StudioQuackHelper helper = StudioQuackHelper.fromEnv();
     * }</pre>
     *
     * @return helper bound to the baked context
     */
    public static StudioQuackHelper fromEnv() {
        installShutdownHook();
        return new StudioQuackHelper(readContextFromEnv());
    }

    /** Install the SIGTERM hook once so stateless {@link #isCancelled()} sees process death. */
    private static void installShutdownHook() {
        if (!HOOK_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(PROCESS_CANCEL::markCancelled, "sq-helper-cancel"));
        } catch (IllegalStateException ignored) {
            // JVM already shutting down
            PROCESS_CANCEL.markCancelled();
        }
    }

    /**
     * Bind per-request cancellation (stateful dispatcher). Return {@code this}.
     *
     * @param cancellation request-scoped record; {@code null} clears the bind
     */
    public StudioQuackHelper bindCancellation(RequestCancellation cancellation) {
        this.requestCancel = cancellation;
        return this;
    }

    /**
     * Rebind context + bearer for one stateful invocation (Python {@code set_request_context}).
     *
     * <p>Use this at the top of every {@code POST /invoke} handler. Tokens are per-request
     * and revoked when the request finishes — never cache them.
     *
     * <pre>{@code
     * helper.setRequestContext(Json.asMap(req.get("executionContext")),
     *         Json.str(req.get("helperToken")));
     * }</pre>
     *
     * @param executionContext request body's {@code executionContext}
     * @param helperToken      request body's {@code helperToken} (may be blank)
     * @return {@code this} for chaining
     */
    public StudioQuackHelper setRequestContext(Map<String, Object> executionContext, String helperToken) {
        if (executionContext != null) {
            this.executionContext = copyMap(executionContext);
            bindIngressFromMap(executionContext);
        }
        if (helperToken != null && !helperToken.isBlank()) {
            transport.setHelperToken(helperToken);
        }
        return this;
    }

    /**
     * Bind top-level stateful {@code /invoke} envelope fields ({@code invocationMode},
     * {@code activeSelectRole}, {@code webhookRequest}). Top-level values win over nested
     * {@code executionContext} keys.
     */
    public StudioQuackHelper setInvocationEnvelope(Map<String, Object> invokeBody) {
        if (invokeBody != null) {
            bindIngressFromMap(invokeBody);
        }
        return this;
    }

    private void bindIngressFromMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        String mode = Json.str(source.get("invocationMode")).trim();
        if (!mode.isEmpty()) {
            this.invocationMode = mode;
        }
        String role = Json.str(source.get("activeSelectRole")).trim();
        if (!role.isEmpty()) {
            this.activeSelectRole = role;
        }
        Object wr = source.get("webhookRequest");
        if (wr instanceof Map<?, ?>) {
            this.webhookRequest = copyMap(Json.asMap(wr));
            if (this.invocationMode == null || this.invocationMode.isBlank() || "normal".equals(this.invocationMode)) {
                this.invocationMode = "ingress";
            }
        }
    }

    // ---- Universal Webhook Ingress & Correlation SDK (TASK-06) ----

    /** True when invoked by an inbound webhook on the node's {@code webhook_key}. */
    public boolean isWebhookInvocation() {
        return "ingress".equalsIgnoreCase(invocationMode) || webhookRequest != null;
    }

    /** Current invocation mode ({@code "ingress"} or {@code "normal"}). */
    public String getInvocationMode() {
        return isWebhookInvocation() ? "ingress" : "normal";
    }

    /** Active select role for this invocation ({@code listen | message_catch | send_and_wait}, or empty). */
    public String getActiveSelectRole() {
        return activeSelectRole == null ? "" : activeSelectRole;
    }

    /**
     * Raw webhook request envelope ({@code method, path, query, queryMulti, headers, rawBody,
     * rawBodyBase64, rawBodyEncoding, body}) or {@code null} outside ingress mode.
     */
    public Map<String, Object> getWebhookRequest() {
        return webhookRequest == null ? null : copyMap(webhookRequest);
    }

    /** Case-insensitive lookup of an inbound webhook HTTP header; returns {@code null} if absent. */
    public String getWebhookHeader(String name) {
        if (name == null || name.isBlank() || webhookRequest == null) {
            return null;
        }
        Map<String, Object> headers = Json.asMap(webhookRequest.get("headers"));
        for (Map.Entry<String, Object> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name.trim())) {
                return e.getValue() == null ? null : String.valueOf(e.getValue());
            }
        }
        return null;
    }

    /** Exact raw webhook body bytes for cryptographic signature verification. */
    public byte[] getWebhookRawBodyBytes() {
        if (webhookRequest == null) {
            return new byte[0];
        }
        String b64 = Json.str(webhookRequest.get("rawBodyBase64"));
        if (!b64.isEmpty()) {
            try {
                return Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException ignored) {
                // fall through to rawBody
            }
        }
        String raw = webhookRequest.get("rawBody") == null ? "" : String.valueOf(webhookRequest.get("rawBody"));
        String enc = Json.str(webhookRequest.get("rawBodyEncoding"));
        if ("base64".equalsIgnoreCase(enc) && !raw.isEmpty()) {
            try {
                return Base64.getDecoder().decode(raw);
            } catch (IllegalArgumentException ignored) {
                // fall through to UTF-8
            }
        }
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    /** Full parsed webhook payload (JSON object/array or parsed form-urlencoded map). */
    public Object getWebhookPayload() {
        return getWebhookPayload(null, null);
    }

    /** Read a dot/bracket path from the parsed webhook payload (e.g. {@code entry[0].changes[0].value.messages[0]}). */
    public Object getWebhookPayload(String path) {
        return getWebhookPayload(path, null);
    }

    /** Read a dot/bracket path from the parsed webhook payload with fallback default. */
    public Object getWebhookPayload(String path, Object defaultValue) {
        if (webhookRequest == null) {
            return defaultValue;
        }
        Object root = resolveParsedWebhookBody(webhookRequest);
        if (root == null) {
            return defaultValue;
        }
        if (path == null || path.isBlank()) {
            return root;
        }
        Object val = navigateSimplePath(root, path);
        return val == null ? defaultValue : val;
    }

    /** Constant-time HMAC-SHA256 verification against exact raw webhook bytes (prefix {@code "sha256="}). */
    public boolean verifyHmacSha256(String secret, String signatureHeader) {
        return verifyHmacSha256(secret, signatureHeader, "sha256=", null);
    }

    /** Constant-time HMAC-SHA256 verification against exact raw webhook bytes with custom prefix. */
    public boolean verifyHmacSha256(String secret, String signatureHeader, String prefix) {
        return verifyHmacSha256(secret, signatureHeader, prefix, null);
    }

    /**
     * Constant-time HMAC-SHA256 verification using {@link MessageDigest#isEqual}.
     * Never logs the secret or raw body.
     */
    public boolean verifyHmacSha256(String secret, String signatureHeader, String prefix, String payloadOverride) {
        if (secret == null || secret.isEmpty() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String presented = getWebhookHeader(signatureHeader);
        if (presented == null || presented.isBlank()) {
            // Allow caller to pass an already-extracted signature string directly when no header matches.
            presented = signatureHeader.contains("=") || signatureHeader.length() >= 40 ? signatureHeader : null;
        }
        if (presented == null || presented.isBlank()) {
            return false;
        }
        String pfx = prefix == null ? "" : prefix;
        String candidate = presented.trim();
        if (!pfx.isEmpty()) {
            if (!candidate.startsWith(pfx)) {
                return false;
            }
            candidate = candidate.substring(pfx.length());
        }
        byte[] payloadBytes = payloadOverride != null
                ? payloadOverride.getBytes(StandardCharsets.UTF_8)
                : getWebhookRawBodyBytes();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payloadBytes);
            String expectedHex = toHexLower(digest);
            byte[] a = expectedHex.getBytes(StandardCharsets.US_ASCII);
            byte[] b = candidate.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(a, b);
        } catch (Exception e) {
            return false;
        }
    }

    /** Immediate handshake challenge reply (HTTP 200 {@code text/plain}). */
    public Map<String, Object> respondWebhookChallenge(String body) {
        return respondWebhookChallenge(body, 200, "text/plain");
    }

    /**
     * Immediate handshake challenge reply. Posts best-effort to {@code /v1/helper/webhook-challenge}
     * and returns the structured {@code __sq_webhook_challenge} response map.
     */
    public Map<String, Object> respondWebhookChallenge(String body, int status, String contentType) {
        return respondWebhookChallenge(body, status, contentType, null);
    }

    /** Immediate handshake challenge reply with optional extra response headers (e.g. Asana {@code X-Hook-Secret}). */
    public Map<String, Object> respondWebhookChallenge(String body, int status, String contentType,
                                                       Map<String, String> headers) {
        Map<String, Object> challenge = new LinkedHashMap<>();
        challenge.put("status", status > 0 ? status : 200);
        challenge.put("contentType", contentType == null || contentType.isBlank() ? "text/plain" : contentType);
        challenge.put("body", body == null ? "" : body);
        if (headers != null && !headers.isEmpty()) {
            challenge.put("headers", new LinkedHashMap<>(headers));
        }
        transport.post("/v1/helper/webhook-challenge", challenge, 15);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("__sq_webhook_challenge", challenge);
        this.recordedWebhookOutcome = envelope;
        return envelope;
    }

    /**
     * Emit 1..N normalized inbound webhook events (capped at {@link #MAX_WEBHOOK_EVENTS}).
     * Posts best-effort to {@code /v1/helper/webhook-events} and returns {@code __sq_webhook_events}.
     */
    public Map<String, Object> emitWebhookEvents(List<Map<String, Object>> events) {
        List<Map<String, Object>> bounded = new ArrayList<>();
        if (events != null) {
            int limit = Math.min(events.size(), MAX_WEBHOOK_EVENTS);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> item = events.get(i);
                if (item != null) {
                    bounded.add(new LinkedHashMap<>(item));
                }
            }
        }
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("events", bounded);
        transport.post("/v1/helper/webhook-events", wire, 15);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("__sq_webhook_events", bounded);
        this.recordedWebhookOutcome = envelope;
        return envelope;
    }

    /**
     * Reject an unauthorized or invalid inbound webhook cleanly ({@code quotaCharge=false}).
     * Posts best-effort to {@code /v1/helper/webhook-reject} and returns {@code __sq_webhook_reject}.
     */
    public Map<String, Object> rejectWebhook(int status, String reason) {
        Map<String, Object> reject = new LinkedHashMap<>();
        reject.put("status", status > 0 ? status : 401);
        reject.put("reason", reason == null || reason.isBlank() ? "unauthorized" : reason);
        transport.post("/v1/helper/webhook-reject", reject, 15);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("__sq_webhook_reject", reject);
        this.recordedWebhookOutcome = envelope;
        return envelope;
    }

    /**
     * Record the correlation key emitted during Phase 1 of {@code send_and_wait}.
     * Posts best-effort to {@code /v1/helper/correlation-key} and attaches to the stateful reply.
     */
    public void setCorrelationKey(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String trimmed = key.trim();
        this.recordedCorrelationKey = trimmed;
        transport.post("/v1/helper/correlation-key", Map.of("correlationKey", trimmed), 15);
    }

    /** Package-visible for {@link StudioQuackDispatch} to merge helper-recorded correlation key. */
    String recordedCorrelationKey() {
        return recordedCorrelationKey;
    }

    /** Package-visible for {@link StudioQuackDispatch} to merge helper-recorded webhook outcome. */
    Map<String, Object> recordedWebhookOutcome() {
        return recordedWebhookOutcome;
    }

    private Object resolveParsedWebhookBody(Map<String, Object> req) {
        Object body = req.get("body");
        if (body != null) {
            return body;
        }
        byte[] rawBytes = getWebhookRawBodyBytes();
        if (rawBytes.length == 0) {
            return null;
        }
        String rawText = new String(rawBytes, StandardCharsets.UTF_8);
        String ct = getWebhookHeader("content-type");
        if (ct != null && ct.toLowerCase(Locale.ROOT).contains("application/x-www-form-urlencoded")) {
            return parseFormUrlEncoded(rawText);
        }
        String trimmed = rawText.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return Json.parse(trimmed);
            } catch (Exception ignored) {
                return rawText;
            }
        }
        return rawText;
    }

    private static Map<String, Object> parseFormUrlEncoded(String raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            try {
                k = URLDecoder.decode(k, StandardCharsets.UTF_8);
                v = URLDecoder.decode(v, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // keep literal
            }
            map.put(k, v);
        }
        return map;
    }

    private static Object navigateSimplePath(Object root, String path) {
        Object cur = root;
        int i = 0;
        int len = path.length();
        while (i < len && cur != null) {
            while (i < len && path.charAt(i) == '.') {
                i++;
            }
            if (i >= len) {
                break;
            }
            if (path.charAt(i) == '[') {
                int end = path.indexOf(']', i + 1);
                if (end < 0) {
                    return null;
                }
                String idxStr = path.substring(i + 1, end).trim();
                if (cur instanceof List<?> list) {
                    try {
                        int idx = Integer.parseInt(idxStr);
                        cur = (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
                    } catch (NumberFormatException e) {
                        return null;
                    }
                } else {
                    return null;
                }
                i = end + 1;
            } else {
                int start = i;
                while (i < len && path.charAt(i) != '.' && path.charAt(i) != '[') {
                    i++;
                }
                String key = path.substring(start, i);
                if (cur instanceof Map<?, ?> m) {
                    cur = m.get(key);
                } else {
                    return null;
                }
            }
        }
        return cur;
    }

    private static String toHexLower(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = digits[v >>> 4];
            hex[i * 2 + 1] = digits[v & 0x0F];
        }
        return new String(hex);
    }

    /**
     * LLM proxy — {@code helper.llm().completion(...)} / {@code .chat(...)}.
     *
     * <p>Use this instead of calling a provider URL; keys stay on the daemon.
     *
     * <pre>{@code
     * Object envelope = helper.llm().completion(
     *         List.of(Map.of("role", "user", "content", "hi")), "local-model", null);
     * }</pre>
     */
    public LlmHelper llm() {
        return llm;
    }

    /**
     * Invoke a connected sub-workflow through a FUNCTION port
     * ({@code POST /v1/helper/execute} — this <em>is</em> {@code studioquack.execute}).
     *
     * <p>Use this method to call the FUNCTION port {@code log} (or {@code model}, etc.).
     * Empty list means no connection matched <em>or</em> the helper channel is down
     * (Java never throws; Python raises on transport faults).
     *
     * <pre>{@code
     * Object result = helper.execute("log", Map.of("message", "hello"), null, null);
     * }</pre>
     *
     * @param portValue   FUNCTION port name (e.g. {@code log})
     * @param nodeRequest payload forwarded to the connected node
     * @param nodeId      optional target node id
     * @param env         optional sibling environment id/name
     * @return {@code result} member (scalar or list), {@code {error}} when the target node
     *         failed, or an empty list
     */
    public Object execute(String portValue, Object nodeRequest, String nodeId, String env) {
        if (portValue == null || portValue.isBlank()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("portValue", portValue);
        body.put("payload", nodeRequest == null ? Map.of() : nodeRequest);
        if (nodeId != null && !nodeId.isBlank()) {
            body.put("nodeId", nodeId);
        }
        if (env != null && !env.isBlank()) {
            body.put("envId", env);
        }
        Object resp = transport.post("/v1/helper/execute", body, HelperTransport.NO_TIME_LIMIT);
        if (!(resp instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object result = map.get("result");
        if (result == null && map.get("error") != null) {
            // The target node failed (it raised a BPMN error): {error} lets the caller react.
            Object detail = map.get("detail");
            return Map.of("error", String.valueOf(detail != null && !String.valueOf(detail).isBlank()
                    ? detail : map.get("error")));
        }
        return result != null ? result : List.of();
    }

    /** {@link #execute(String, Object, String, String)} against this environment. */
    public Object execute(String portValue, Object nodeRequest) {
        return execute(portValue, nodeRequest, null, null);
    }

    /**
     * Streaming {@link #execute(String, Object, String, String)} —
     * {@code POST /v1/helper/execute/stream} as SSE ({@code event: partial|complete|error|done}).
     *
     * <p>Same JSON body as {@code execute}. No time limit. Blank {@code portValue} returns a
     * failed empty stream (no curl). Never throws; a down channel is {@link SseStream#transportFailed()}.
     * Close the stream (try-with-resources) to destroy curl. Never log the bearer.
     *
     * <pre>{@code
     * try (SseStream stream = helper.executeStream("log", Map.of("message", "hello"), null, null)) {
     *     for (SseFrame frame : stream) {
     *         if ("partial".equals(frame.event)) {
     *             // frame.data is the parsed chunk
     *         }
     *         if ("done".equals(frame.event)) {
     *             break;
     *         }
     *     }
     *     if (stream.transportFailed()) {
     *         helper.log("stream down: " + stream.failureHint(), "warn");
     *     }
     * }
     * }</pre>
     *
     * @param portValue FUNCTION port name (e.g. {@code log})
     * @param payload   forwarded to the connected node; {@code null} becomes {@code {}}
     * @param nodeId    optional target node id
     * @param env       optional sibling environment id/name
     * @return live or already-failed stream; never {@code null}
     */
    public SseStream executeStream(String portValue, Object payload, String nodeId, String env) {
        try {
            if (portValue == null || portValue.isBlank()) {
                return SseStream.failed("blank portValue");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("portValue", portValue);
            body.put("payload", payload == null ? Map.of() : payload);
            if (nodeId != null && !nodeId.isBlank()) {
                body.put("nodeId", nodeId);
            }
            if (env != null && !env.isBlank()) {
                body.put("envId", env);
            }
            return transport.postSse("/v1/helper/execute/stream", body);
        } catch (Exception ignored) {
            return SseStream.failed("executeStream");
        }
    }

    /** {@link #executeStream(String, Object, String, String)} against this environment. */
    public SseStream executeStream(String portValue, Object payload) {
        return executeStream(portValue, payload, null, null);
    }

    /**
     * Live thinking/answer delta ({@code POST /v1/helper/emit-partial}
     * {@code {channel, delta}}). Best-effort: never throws into the node. Blank channel
     * or {@code null} delta is a no-op ({@code false}). {@link #emitPartial(Object)} is the
     * {@code answer}-channel shorthand.
     *
     * <pre>{@code
     * helper.emitPartial("thinking", "planning...");
     * helper.emitPartial("answer", token);
     * }</pre>
     *
     * @param channel {@code thinking} or {@code answer}
     * @param delta   text fragment (empty string is sent; {@code null} is not)
     * @return {@code delivered} from the JSON when present, else {@code true} on a
     *         non-error map body, else {@code false}
     */
    public boolean emitPartial(String channel, String delta) {
        return emitPartial(null, channel, delta);
    }

    /**
     * Port-tagged live delta ({@code POST /v1/helper/emit-partial}
     * {@code {port, channel, delta}}). Naming one of the node's OWN ports lets the daemon
     * route the frame along that port's workflow edges — through any edge JOLT — to a
     * live-render target (e.g. Duckbot end), instead of bypassing the graph straight to
     * the conversation. A {@code null}/{@code blank} port keeps the legacy side-channel
     * behaviour. Best-effort: never throws into the node.
     *
     * <pre>{@code
     * helper.emitPartial("thoughts", "thinking", "planning...");
     * helper.emitPartial("main_output", "answer", token);
     * }</pre>
     *
     * @param port    the emitting node's own source port value, or {@code null}
     * @param channel {@code thinking} or {@code answer}
     * @param delta   text fragment (empty string is sent; {@code null} is not)
     * @return {@code delivered} from the JSON when present, else {@code true} on a
     *         non-error map body, else {@code false}
     */
    public boolean emitPartial(String port, String channel, String delta) {
        if (channel == null || channel.isBlank() || delta == null) {
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (port != null && !port.isBlank()) {
                body.put("port", port);
            }
            body.put("channel", channel);
            body.put("delta", delta);
            Object resp = transport.post("/v1/helper/emit-partial", body, 15);
            if (!(resp instanceof Map<?, ?> map)) {
                return false;
            }
            if (map.containsKey("delivered")) {
                Object d = map.get("delivered");
                if (d instanceof Boolean b) {
                    return b;
                }
                if (d instanceof Number n) {
                    return n.intValue() != 0;
                }
                return Boolean.parseBoolean(String.valueOf(d));
            }
            // 401/500 helper bodies are {"error":...} — not a 2xx-shaped success.
            if (map.containsKey("error")) {
                return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Open a daemon-mediated tunnel to a named stateful instance.
     *
     * <p>Use this instead of dialing a sibling container IP (the sandbox has no route).
     *
     * <pre>{@code
     * HelperTransport.ConnectTunnel redis = helper.connect("my-redis", 6379);
     * // redis.getHost(), redis.getPort() → 127.0.0.1:&lt;ephemeral&gt;
     * }</pre>
     *
     * @param containerName instance name from Engine Settings
     * @param port          TCP port inside that container
     * @return local listener, or {@code null} when the helper channel is absent
     */
    public HelperTransport.ConnectTunnel connect(String containerName, int port) {
        return transport.connect(containerName, port);
    }

    /**
     * Resolve an engine id to its current overlay address ({@code POST /v1/helper/engine-ip}).
     *
     * <p>Use this before dialing a published reverse port — addresses change on reconnect.
     *
     * <pre>{@code
     * String ip = helper.engineIP("engine-1-id");
     * }</pre>
     *
     * @param engineId same-owner engine id
     * @return overlay IPv4/IPv6 string, or {@code null} when unresolved
     */
    public String engineIP(String engineId) {
        if (engineId == null || engineId.isBlank()) {
            return null;
        }
        Object resp = transport.post("/v1/helper/engine-ip", Map.of("engineId", engineId), 15);
        String ip = Json.str(Json.asMap(resp).get("ip"));
        return ip.isBlank() ? null : ip;
    }

    /**
     * POST the current context to {@code /v1/helper/transform-context}.
     *
     * <p>Use this after {@link #updateInput} / {@link #updateExecutionContext} so the
     * daemon sees the patch.
     *
     * <pre>{@code
     * helper.returnExecutionContext();
     * }</pre>
     */
    public Map<String, Object> returnExecutionContext() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("executionContext", executionContext);
        transport.post("/v1/helper/transform-context", body, 15);
        return getExecutionContext();
    }

    /**
     * Deliver one OUTPUT port: stdout marker + {@code POST /v1/helper/result}, then
     * {@code System.exit(0)} unless {@code SQ_STATEFUL_RUNNER} is truthy.
     *
     * <p>Use this method to write the value of port {@code sum}:
     *
     * <pre>{@code
     * helper.returnResult("sum", a + b);
     * }</pre>
     */
    public void returnResult(String port, Object value) {
        returnResult(List.of(new ResultItem(port, value)));
    }

    /**
     * Python-shaped {@code returnResult(items)} — each item is a {@link ResultItem}.
     *
     * <pre>{@code
     * helper.returnResult(List.of(new StudioQuackHelper.ResultItem("sum", 8)));
     * }</pre>
     */
    public void returnResult(List<ResultItem> items) {
        if (items == null) {
            return;
        }
        List<Map<String, Object>> wire = new ArrayList<>();
        for (ResultItem item : items) {
            if (item == null || item.portValue == null) {
                continue;
            }
            System.out.println("portValue:" + item.portValue + ":::result:" + item.wireValue());
            wire.add(item.toWire());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("items", wire);
        transport.post("/v1/helper/result", body, 15);
        if (isStatefulRunner()) {
            return;
        }
        System.exit(0);
    }

    /**
     * Fail the node with a typed business error ({@code POST /v1/helper/fail}).
     *
     * <p>Use this instead of HTTP 4xx/5xx. Wins over exit 0. Blank {@code errorCode}
     * and reserved {@code SQ_SKIP} throw {@link IllegalArgumentException}. Prints
     * {@code SQ_FAIL <code>: <message>} to stderr. Does <em>not</em> {@code System.exit}
     * — a stateless caller must exit 1 after this method returns.
     *
     * <pre>{@code
     * helper.fail("Error_BadInput", "a and b must be numbers", null);
     * System.exit(1); // stateless only
     * }</pre>
     */
    public void fail(String errorCode, String message, Map<String, Object> data) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode is required");
        }
        if ("SQ_SKIP".equals(errorCode)) {
            throw new IllegalArgumentException("SQ_SKIP is reserved");
        }
        System.err.println("SQ_FAIL " + errorCode + ": " + (message == null ? "" : message));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", errorCode);
        if (message != null) {
            body.put("message", message);
        }
        if (data != null) {
            body.put("data", data);
        }
        transport.post("/v1/helper/fail", body, 15);
    }

    /** {@link #fail(String, String, Map)} without extra data. */
    public void fail(String errorCode, String message) {
        fail(errorCode, message, null);
    }

    /**
     * Best-effort escalation ({@code POST /v1/helper/escalate}
     * {@code {escalationCode, message, data}}). Never throws. Blank code is a no-op.
     */
    public void escalate(String escalationCode, String message, Map<String, Object> data) {
        try {
            if (escalationCode == null || escalationCode.isBlank()) {
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("escalationCode", escalationCode);
            if (message != null) {
                body.put("message", message);
            }
            if (data != null) {
                body.put("data", data);
            }
            transport.post("/v1/helper/escalate", body, 15);
        } catch (Exception ignored) {
            // never throw
        }
    }

    /** {@link #sendSignal(String, String)} with scope {@code global}. */
    public void sendSignal(String signalName) {
        sendSignal(signalName, "global");
    }

    /**
     * Broadcast a signal ({@code POST /v1/helper/signal} {@code {signalName, scope}}).
     * Scope {@code "global"} (default) or {@code "instance"}. No payload.
     * Names starting with {@code __sq_} (and blank names) throw
     * {@link IllegalArgumentException}. Transport faults are swallowed.
     */
    public void sendSignal(String signalName, String scope) {
        if (signalName == null || signalName.isBlank()) {
            throw new IllegalArgumentException("signalName is required");
        }
        if (signalName.startsWith("__sq_")) {
            throw new IllegalArgumentException("signal names starting with __sq_ are reserved");
        }
        String sc = (scope == null || scope.isBlank()) ? "global" : scope;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("signalName", signalName);
            body.put("scope", sc);
            transport.post("/v1/helper/signal", body, 15);
        } catch (Exception ignored) {
            // never throw on transport
        }
    }

    /**
     * Correlate a message ({@code POST /v1/helper/message}
     * {@code {messageName, correlationKey}}). {@code correlationKey == null} omits
     * the key (current run). Blank {@code messageName} is a no-op. Never throws.
     */
    public void sendMessage(String messageName, String correlationKey) {
        try {
            if (messageName == null || messageName.isBlank()) {
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messageName", messageName);
            if (correlationKey != null) {
                body.put("correlationKey", correlationKey);
            }
            transport.post("/v1/helper/message", body, 15);
        } catch (Exception ignored) {
            // never throw
        }
    }

    /**
     * Whether this request (or the process, in stateless SIGTERM) has been cancelled.
     *
     * <p>Sources, OR-ed: (a) the bound {@link RequestCancellation}, (b) the process
     * SIGTERM flag, (c) {@code GET /v1/helper/cancelled} polled at most once per second.
     */
    public boolean isCancelled() {
        if (requestCancel != null && requestCancel.isCancelled()) {
            return true;
        }
        if (PROCESS_CANCEL.isCancelled()) {
            return true;
        }
        return pollHelperCancelled();
    }

    /**
     * Run {@code callback} once when cancellation is observed. Already-cancelled
     * → run immediately. {@code null} is a no-op.
     */
    public void onCancel(Runnable callback) {
        if (callback == null) {
            return;
        }
        RequestCancellation target = requestCancel != null ? requestCancel : PROCESS_CANCEL;
        target.onCancel(callback);
    }

    /** GET /v1/helper/cancelled at most once per second. Channel-down → false. */
    private boolean pollHelperCancelled() {
        long now = System.currentTimeMillis();
        if (now - lastCancelPollMs < 1000L) {
            return lastCancelPollResult;
        }
        lastCancelPollMs = now;
        Object resp = transport.get("/v1/helper/cancelled", 5);
        boolean result = false;
        if (resp instanceof Map<?, ?> map) {
            result = truthy(map.get("cancelled")) || truthy(map.get("canceled"));
        }
        lastCancelPollResult = result;
        return result;
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        if (v instanceof String s) {
            return "true".equalsIgnoreCase(s.trim()) || "1".equals(s.trim());
        }
        return false;
    }

    /**
     * Resolve a secret from the per-job tmpfs env ({@code SQVM_SECRET__KEY}) or
     * {@code SQVM_SECRETS_FILE}. Never log the return value.
     *
     * <pre>{@code
     * String token = helper.getSecret("openai");
     * }</pre>
     *
     * @param key secret name (case-insensitive; looked up as {@code SQVM_SECRET__<KEY>})
     */
    public String getSecret(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String envKey = "SQVM_SECRET__" + key.toUpperCase();
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null) {
            return fromEnv;
        }
        String path = System.getenv("SQVM_SECRETS_FILE");
        if (path == null || path.isBlank() || !Files.isRegularFile(Path.of(path))) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(Path.of(path), StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (envKey.equals(line.substring(0, eq))) {
                    return unquoteDotenv(line.substring(eq + 1));
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /**
     * Best-effort metric ({@code POST /v1/helper/metric}). Never throws.
     *
     * <pre>{@code
     * helper.metric("sum.computed", 1);
     * }</pre>
     */
    public void metric(String name, Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("value", value);
        transport.post("/v1/helper/metric", body, 15);
    }

    /**
     * Best-effort log line ({@code POST /v1/helper/log}). Never throws. Do not log tokens.
     *
     * <pre>{@code
     * helper.log("computed sum=" + sum, "info");
     * }</pre>
     */
    public void log(String message, String level) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message == null ? "" : message);
        body.put("level", level == null || level.isBlank() ? "info" : level);
        transport.post("/v1/helper/log", body, 15);
    }

    /**
     * Python {@code emit_partial(chunk)}: pushes {@code chunk} on the {@code answer} channel to a
     * caller waiting on {@link #executeStream} ({@code POST /v1/helper/emit-partial}). Strings go
     * as text; other values as JSON. Best-effort: never throws.
     *
     * <pre>{@code
     * helper.emitPartial("token...");
     * }</pre>
     *
     * @return {@code true} when the daemon reports the chunk delivered
     */
    public boolean emitPartial(Object chunk) {
        if (chunk == null) {
            return false;
        }
        return emitPartial(null, "answer", chunk instanceof String s ? s : Json.write(chunk));
    }

    /**
     * Python {@code llm_local}: chat completion on this engine's provider, returning the full
     * envelope. Throws {@link IllegalArgumentException} on an empty {@code messages} list; extra
     * {@code params} (temperature, max_tokens, ...) are forwarded verbatim.
     *
     * <pre>{@code
     * Object envelope = helper.llmLocal(List.of(Map.of("role", "user", "content", "hi")), null, null, null);
     * }</pre>
     */
    public Object llmLocal(List<Map<String, Object>> messages, String model, Object tools,
                           Map<String, Object> params) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("llmLocal requires a non-empty messages list");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((k, v) -> {
                if (v != null) {
                    body.put(k, v);
                }
            });
        }
        body.put("messages", new ArrayList<>(messages));
        if (model != null && !model.isBlank()) {
            body.put("model", model);
        }
        if (tools != null) {
            body.put("tools", tools);
        }
        Object resp = transport.post("/v1/helper/llm/completion", body, HelperTransport.NO_TIME_LIMIT);
        return resp != null ? resp : Map.of();
    }

    /**
     * True when {@code urlPath} (query ignored) is the stateful cancel route:
     * {@code {SQ_APP_PATH or /invoke}/cancel}, or {@code /cancel}.
     */
    public boolean isCancelPath(String urlPath) {
        String path = urlPath == null ? "" : urlPath;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        String app = System.getenv("SQ_APP_PATH");
        app = (app == null || app.isBlank() ? "/invoke" : app).replaceAll("/+$", "");
        return path.equals(app + "/cancel") || path.equals("/cancel");
    }

    /**
     * Mark a request cancelled (Python {@code handleCancel}). A blank id, or a stateless job with
     * no bound request, cancels this step.
     */
    public void handleCancel(String requestId, String reason) {
        if (requestId == null || requestId.isBlank()) {
            (requestCancel != null ? requestCancel : PROCESS_CANCEL).markCancelled();
            return;
        }
        CancellationRegistry.cancel(requestId.trim());
        if (requestCancel == null) {
            PROCESS_CANCEL.markCancelled();
        }
    }

    // ---- short accessors (same names as the Python and JS/TS helpers) ----

    /** {@link #getExecutionContext()}. */
    public Map<String, Object> fullContext() {
        return getExecutionContext();
    }

    /** {@link #getCurrentNode()}. */
    public Map<String, Object> currentNode() {
        return getCurrentNode();
    }

    /** {@link #getCurrentNodeId()}. */
    public String currentNodeId() {
        return getCurrentNodeId();
    }

    /** {@link #getWorkflowExecutionId()}. */
    public String workflowExecutionId() {
        return getWorkflowExecutionId();
    }

    /** {@link #getParameters()}. */
    public List<Object> parameters() {
        return getParameters();
    }

    /** {@link #getConnections()}. */
    public List<Object> connections() {
        return getConnections();
    }

    /** INPUT port → value map ({@code targetPortValue}, else {@code portValue}). */
    public Map<String, Object> inputs() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object item : getInput()) {
            Map<String, Object> entry = Json.asMap(item);
            Object key = entry.get("targetPortValue");
            if (key == null || String.valueOf(key).isBlank()) {
                key = entry.get("portValue");
            }
            if (key != null && !String.valueOf(key).isBlank()) {
                out.put(String.valueOf(key), entry.get("value"));
            }
        }
        return out;
    }

    /** One input by port name, or {@code defaultValue} when the port carries no value. */
    public Object input(String key, Object defaultValue) {
        Map<String, Object> all = inputs();
        return all.containsKey(key) ? all.get(key) : defaultValue;
    }

    /**
     * One output-port binding, matching Python {@code StudioQuackHelper.ResultItem}.
     */
    public static final class ResultItem {
        /** OUTPUT port name (wire field {@code port}). */
        public final String portValue;
        /** Value printed on the marker line and JSON-encoded when not a String. */
        public final Object result;

        /**
         * @param portValue OUTPUT port name, e.g. {@code sum}
         * @param result    value delivered to that port
         */
        public ResultItem(String portValue, Object result) {
            this.portValue = portValue;
            this.result = result;
        }

        Map<String, Object> toWire() {
            return Map.of("port", portValue == null ? "" : portValue, "value", wireValue());
        }

        String wireValue() {
            if (result == null) {
                return "";
            }
            if (result instanceof String s) {
                return s;
            }
            return Json.write(result);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readContextFromEnv() {
        String b64 = System.getenv("SQ_CONTEXT");
        if (b64 == null || b64.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            byte[] raw = Base64.getDecoder().decode(b64.trim());
            Object parsed = Json.parse(new String(raw, StandardCharsets.UTF_8));
            if (parsed instanceof Map<?, ?> m) {
                return new LinkedHashMap<>((Map<String, Object>) m);
            }
        } catch (Exception ignored) {
            // empty context — callers read ports and get defaults
        }
        return new LinkedHashMap<>();
    }

    /**
     * {@code SQ_STATEFUL_RUNNER} in {@code {1,true,yes,on}} (case-insensitive).
     *
     * <p>Use this in {@code main} to decide whether to start a long-lived listener when
     * the daemon launched the container as a stateful instance.
     *
     * <pre>{@code
     * if (StudioQuackHelper.isStatefulRunner()) { runStateful(helper); }
     * }</pre>
     */
    public static boolean isStatefulRunner() {
        String v = System.getenv("SQ_STATEFUL_RUNNER");
        if (v == null) {
            return false;
        }
        String t = v.trim().toLowerCase();
        return "1".equals(t) || "true".equals(t) || "yes".equals(t) || "on".equals(t);
    }

    private static String unquoteDotenv(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            String body = raw.substring(1, raw.length() - 1);
            return body.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return raw;
    }
}
