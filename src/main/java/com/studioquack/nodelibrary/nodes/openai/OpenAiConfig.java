package com.studioquack.nodelibrary.nodes.openai;

import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI Models instance config + shared HTTPS client.
 *
 * <p>Reads {@code configurationParameters} first, then
 * {@code helper.getParameterValueByShortCode}. Never logs {@code api_key}.
 * The {@link HttpClient} is a process-wide singleton (thread-safe; 8 concurrent
 * dispatches share it). No request-scoped mutable statics.
 */
public final class OpenAiConfig {

    /** Python {@code _REASONING_MODEL_PREFIXES}. */
    static final String[] REASONING_PREFIXES = {"o1", "o3", "o4", "o-"};
    /** Python {@code _STOP_UNSUPPORTED_PREFIXES}. */
    static final String[] STOP_UNSUPPORTED_PREFIXES = {"gpt-5", "computer-use"};
    /** Images API ids. Prefix {@code gpt-image} also matches future gpt-image-* snapshots. */
    static final Set<String> IMAGE_GEN_MODELS = Set.of(
            "gpt-image-2", "gpt-image-1.5", "gpt-image-1", "gpt-image-1-mini", "dall-e-3", "dall-e-2");
    static final Set<String> TTS_MODELS = Set.of("tts-1", "tts-1-hd", "gpt-4o-mini-tts");
    static final Set<String> STT_MODELS = Set.of("whisper-1", "gpt-4o-transcribe", "gpt-4o-mini-transcribe");

    static final String API_BASE = "https://api.openai.com/v1";
    static final String DEFAULT_MODEL = "gpt-5.6-sol";
    static final String DEFAULT_REASONING_EFFORT = "medium";
    /** Shared-client connect budget. Not overridable per request (one HttpClient). */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** HttpRequest.timeout — headers only; body reads use STREAM_IDLE_TIMEOUT. */
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(300);
    /** After headers, abort if no body byte arrives in this window (default 600s). */
    static final Duration STREAM_IDLE_TIMEOUT = Duration.ofSeconds(600);

    static final int READ_TIMEOUT_MIN_SEC = 30;
    static final int READ_TIMEOUT_MAX_SEC = 600;
    static final int IDLE_TIMEOUT_MIN_SEC = 5;
    static final int IDLE_TIMEOUT_MAX_SEC = 600;

    private static final Pattern UNSUPPORTED_PARAM =
            Pattern.compile("Unsupported parameter: '(\\w+)'");

    /**
     * Shared client: HTTP/1.1 so SSE line-reads stay well-defined.
     * Connect timeout 10s (singleton — {@code connect_timeout_seconds} is ignored).
     * Per-call header wait is set on each {@link HttpRequest}.
     */
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final Map<String, Object> params;
    private final String model;
    private final String apiKey;
    private final Duration readTimeout;
    private final Duration streamIdleTimeout;

    private OpenAiConfig(Map<String, Object> params, String model, String apiKey,
                         Duration readTimeout, Duration streamIdleTimeout) {
        this.params = params;
        this.model = model;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.readTimeout = readTimeout == null ? REQUEST_TIMEOUT : readTimeout;
        this.streamIdleTimeout = streamIdleTimeout == null ? STREAM_IDLE_TIMEOUT : streamIdleTimeout;
    }

    /**
     * Resolve instance knobs. Node-instance params win over helper lookups;
     * blank values fall through. Default model {@code gpt-5.6-sol}.
     */
    public static OpenAiConfig from(Map<String, Object> request, StudioQuackHelper helper) {
        Map<String, Object> nodeParams = NodeParams.readNodeParams(request);
        Map<String, Object> resolved = new LinkedHashMap<>();
        String[] keys = {
                "model", "api_key", "temperature", "top_p", "max_completion_tokens",
                "frequency_penalty", "presence_penalty", "stop", "n", "reasoning_effort", "verbosity",
                "response_format", "modalities", "audio", "parallel_tool_calls", "prompt_cache_key",
                "seed", "service_tier", "safety_identifier", "stream", "logprobs",
                "top_logprobs",
                // Optional; not in node_definition.json (no designer UUID churn).
                "connect_timeout_seconds", "read_timeout_seconds", "stream_idle_timeout_seconds"
        };
        for (String key : keys) {
            Object v = pick(nodeParams, helper, key);
            if (v != null) {
                resolved.put(key, v);
            }
        }
        String model = NodeParams.trim(resolved.get("model"));
        if (model.isEmpty()) {
            model = DEFAULT_MODEL;
        }
        String apiKey = NodeParams.str(resolved.get("api_key"));
        // connect_timeout_seconds is accepted but unused: the shared client is
        // process-wide and already built with CONNECT_TIMEOUT (10s).
        Duration readTimeout = clampSeconds(resolved.get("read_timeout_seconds"),
                (int) REQUEST_TIMEOUT.toSeconds(), READ_TIMEOUT_MIN_SEC, READ_TIMEOUT_MAX_SEC);
        Duration idleTimeout = clampSeconds(resolved.get("stream_idle_timeout_seconds"),
                (int) STREAM_IDLE_TIMEOUT.toSeconds(), IDLE_TIMEOUT_MIN_SEC, IDLE_TIMEOUT_MAX_SEC);
        return new OpenAiConfig(resolved, model, apiKey, readTimeout, idleTimeout);
    }

    /**
     * configurationParameters first; helper second. Blank / empty-map values are
     * treated as unset so a designer empty string does not mask a helper value.
     */
    static Object pick(Map<String, Object> nodeParams, StudioQuackHelper helper, String shortCode) {
        if (nodeParams != null && nodeParams.containsKey(shortCode) && present(nodeParams.get(shortCode))) {
            return nodeParams.get(shortCode);
        }
        if (helper != null) {
            try {
                Object v = helper.getParameterValueByShortCode(shortCode);
                if (present(v)) {
                    return v;
                }
            } catch (RuntimeException ignored) {
                // helper lookup is best-effort
            }
        }
        return null;
    }

    static boolean present(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof String s) {
            return !s.isBlank();
        }
        if (v instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (v instanceof List<?> l) {
            return !l.isEmpty();
        }
        return true;
    }

    public String model() {
        return model;
    }

    /** Raw API key. Never log, never put in responses. */
    public String apiKey() {
        return apiKey;
    }

    public Object get(String shortCode) {
        return params.get(shortCode);
    }

    public String modelLower() {
        return model.toLowerCase(Locale.ROOT);
    }

    /** Per-request header wait ({@link HttpRequest#timeout}); clamped [30, 600] s. */
    public Duration readTimeout() {
        return readTimeout;
    }

    /** After headers, no-byte abort window; clamped [5, 600] s. */
    public Duration streamIdleTimeout() {
        return streamIdleTimeout;
    }

    /**
     * Missing/invalid → {@code defaultSec}. Otherwise clamp to {@code [min, max]}.
     */
    static Duration clampSeconds(Object raw, int defaultSec, int min, int max) {
        Integer n = NodeParams.toIntOrNull(raw);
        if (n == null) {
            return Duration.ofSeconds(defaultSec);
        }
        int clamped = Math.max(min, Math.min(max, n));
        return Duration.ofSeconds(clamped);
    }

    /**
     * Fail-fast timeout / stall text: model + endpoint + elapsed seconds.
     * Never includes api_key or response bytes.
     */
    static String timeoutMessage(String model, String endpoint, long elapsedSeconds, String kind) {
        String m = model == null || model.isBlank() ? "?" : model;
        String ep = endpoint == null || endpoint.isBlank() ? "?" : endpoint;
        String k = kind == null || kind.isBlank() ? "timed out" : kind;
        return "OpenAI " + k + ": model=" + m + " endpoint=" + ep + " elapsed=" + elapsedSeconds + "s";
    }

    static long elapsedSeconds(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000_000L);
    }

    static RuntimeException wrapHttpTimeout(Throwable t, String model, String endpoint, long startedNanos) {
        long elapsed = elapsedSeconds(startedNanos);
        String kind = t instanceof java.net.http.HttpConnectTimeoutException
                ? "connect timed out" : "request timed out";
        return new TimeoutException(timeoutMessage(model, endpoint, elapsed, kind), t);
    }

    static boolean isTimeoutFailure(Throwable t) {
        while (t != null) {
            if (t instanceof TimeoutException
                    || t instanceof java.net.http.HttpTimeoutException
                    || t instanceof OpenAiIdleStream.StreamStalledException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * User spec: o1/o3/o4/o- prefixes OR name contains {@code reasoning} OR {@code gpt-5*}
     * are reasoning models (sampling params stripped; effort/verbosity injected).
     * Broader than Python, which did not treat gpt-5* as reasoning for sampling.
     */
    public static boolean isReasoning(String modelLower) {
        if (modelLower == null) {
            return false;
        }
        String m = modelLower.toLowerCase(Locale.ROOT);
        if (m.contains("reasoning") || m.startsWith("gpt-5")) {
            return true;
        }
        for (String p : REASONING_PREFIXES) {
            if (m.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean stopUnsupported(String modelLower) {
        if (isReasoning(modelLower)) {
            return true;
        }
        String m = modelLower == null ? "" : modelLower.toLowerCase(Locale.ROOT);
        for (String p : STOP_UNSUPPORTED_PREFIXES) {
            if (m.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isImageModel(String modelLower) {
        String m = modelLower == null ? "" : modelLower.toLowerCase(Locale.ROOT);
        return IMAGE_GEN_MODELS.contains(m) || m.startsWith("dall-e") || m.startsWith("gpt-image");
    }

    public static boolean isTtsModel(String modelLower) {
        String m = modelLower == null ? "" : modelLower.toLowerCase(Locale.ROOT);
        return TTS_MODELS.contains(m) || m.endsWith("-tts");
    }

    public static boolean isSttModel(String modelLower) {
        String m = modelLower == null ? "" : modelLower.toLowerCase(Locale.ROOT);
        return STT_MODELS.contains(m) || m.contains("transcribe") || m.startsWith("whisper");
    }

    /**
     * Chat Completions kwargs (no {@code stream} flag — the caller adds it).
     * Reasoning models drop sampling keys and gain {@code reasoning_effort}/{@code verbosity}.
     */
    public Map<String, Object> buildChatKwargs(List<Map<String, Object>> messages,
                                               List<Object> tools, Map<String, Object> options) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("model", model);
        cfg.put("messages", messages);
        boolean reasoning = isReasoning(modelLower());
        boolean noStop = stopUnsupported(modelLower());

        if (!reasoning) {
            putFloat(cfg, "temperature");
            putFloat(cfg, "top_p");
            putFloat(cfg, "frequency_penalty");
            putFloat(cfg, "presence_penalty");
            Object lp = get("logprobs");
            boolean logprobsOn = false;
            if (lp != null) {
                Boolean b = NodeParams.boolish(lp);
                if (b != null) {
                    cfg.put("logprobs", b);
                    logprobsOn = b;
                }
            }
            // The API 400s on top_logprobs unless logprobs=true — never send it on its own.
            Integer tlp = logprobsOn ? NodeParams.toIntOrNull(get("top_logprobs")) : null;
            if (tlp != null) {
                cfg.put("top_logprobs", tlp);
            }
        }

        // Token caps accept positive integers only — instances created from the old
        // definition carry '0' defaults, and a zero cap makes the API generate zero
        // tokens (empty assistant). Treat a non-positive cap as unset. The deprecated
        // max_tokens is never sent: reasoning models reject it.
        Integer mct = NodeParams.toIntOrNull(get("max_completion_tokens"));
        if (mct != null && mct > 0) {
            cfg.put("max_completion_tokens", mct);
        }

        Object stop = get("stop");
        if (stop != null && !noStop) {
            if (stop instanceof List<?> list) {
                cfg.put("stop", list.size() > 4 ? new ArrayList<>(list.subList(0, 4)) : list);
            } else {
                String s = NodeParams.trim(stop);
                if (!s.isEmpty()) {
                    if (s.contains(",")) {
                        List<String> parts = new ArrayList<>();
                        for (String p : s.split(",")) {
                            String t = p.trim();
                            if (!t.isEmpty() && parts.size() < 4) {
                                parts.add(t);
                            }
                        }
                        cfg.put("stop", parts);
                    } else {
                        cfg.put("stop", s);
                    }
                }
            }
        }

        Integer n = NodeParams.toIntOrNull(get("n"));
        if (n != null) {
            cfg.put("n", n);
        }

        if (reasoning) {
            Object re = get("reasoning_effort");
            cfg.put("reasoning_effort", present(re) ? NodeParams.str(re) : DEFAULT_REASONING_EFFORT);
            Object vb = get("verbosity");
            if (present(vb)) {
                cfg.put("verbosity", NodeParams.str(vb));
            }
        }

        putResponseFormat(cfg);
        putModalities(cfg, options);
        putAudio(cfg);
        Integer seed = NodeParams.toIntOrNull(get("seed"));
        if (seed != null) {
            cfg.put("seed", seed);
        }
        for (String p : List.of("prompt_cache_key", "safety_identifier", "service_tier")) {
            Object v = get(p);
            if (present(v)) {
                cfg.put(p, NodeParams.str(v));
            }
        }
        if (tools != null && !tools.isEmpty()) {
            cfg.put("tools", OpenAiChat.buildOpenaiTools(tools));
            cfg.put("tool_choice", "auto");
            Boolean ptc = NodeParams.boolish(get("parallel_tool_calls"));
            if (Boolean.TRUE.equals(ptc)) {
                cfg.put("parallel_tool_calls", true);
            }
        }
        return cfg;
    }

    /**
     * Stream when input {@code stream}/{@code streaming} or config {@code stream} is true.
     * Explicit false selects the non-stream fast path. Unset defaults to
     * <strong>true</strong> (user spec; Python defaulted simple text to non-stream).
     */
    public boolean shouldStream(Map<String, Object> options) {
        Boolean explicit = streamPref(options);
        if (explicit != null) {
            return explicit;
        }
        return true;
    }

    Boolean streamPref(Map<String, Object> options) {
        for (String key : List.of("stream", "streaming")) {
            if (options != null && options.containsKey(key)) {
                Boolean parsed = NodeParams.boolish(options.get(key));
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return NodeParams.boolish(get("stream"));
    }

    /** Strip {@code Unsupported parameter: 'foo'} from cfg. Returns true when a key was removed. */
    static boolean stripUnsupported(Map<String, Object> cfg, String err) {
        if (cfg == null || err == null) {
            return false;
        }
        String low = err.toLowerCase(Locale.ROOT);
        if (!low.contains("unsupported parameter") && !low.contains("unsupported_parameter")) {
            return false;
        }
        Matcher m = UNSUPPORTED_PARAM.matcher(err);
        if (m.find() && cfg.containsKey(m.group(1))) {
            cfg.remove(m.group(1));
            return true;
        }
        return false;
    }

    static HttpRequest.Builder authed(String path, String apiKey) {
        return authed(path, apiKey, REQUEST_TIMEOUT);
    }

    static HttpRequest.Builder authed(String path, String apiKey, Duration timeout) {
        Duration t = timeout == null ? REQUEST_TIMEOUT : timeout;
        return HttpRequest.newBuilder(URI.create(API_BASE + path))
                .timeout(t)
                .header("Authorization", "Bearer " + apiKey);
    }

    static Map<String, Object> postJson(String path, String apiKey, Object body)
            throws ApiException, java.io.IOException, InterruptedException {
        return postJson(path, apiKey, body, REQUEST_TIMEOUT, null);
    }

    static Map<String, Object> postJson(String path, String apiKey, Object body,
                                        Duration timeout, String model)
            throws ApiException, java.io.IOException, InterruptedException {
        String json = Json.write(body == null ? Map.of() : body);
        HttpRequest req = authed(path, apiKey, timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        long t0 = System.nanoTime();
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw wrapHttpTimeout(e, model, path, t0);
        }
        int status = resp.statusCode();
        String respBody = resp.body() == null ? "" : resp.body();
        if (status >= 400) {
            throw new ApiException(status, OpenAiChat.openaiErrorMessage(status, respBody), respBody);
        }
        Object parsed = NodeParams.parseJson(respBody);
        if (parsed instanceof Map<?, ?>) {
            return NodeParams.mutableMap(parsed);
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("raw", respBody);
        return wrap;
    }

    static byte[] postBytes(String path, String apiKey, Object jsonBody, String accept)
            throws ApiException, java.io.IOException, InterruptedException {
        return postBytes(path, apiKey, jsonBody, accept, REQUEST_TIMEOUT, null);
    }

    static byte[] postBytes(String path, String apiKey, Object jsonBody, String accept,
                            Duration timeout, String model)
            throws ApiException, java.io.IOException, InterruptedException {
        String json = Json.write(jsonBody == null ? Map.of() : jsonBody);
        HttpRequest req = authed(path, apiKey, timeout)
                .header("Content-Type", "application/json")
                .header("Accept", accept == null ? "application/octet-stream" : accept)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        long t0 = System.nanoTime();
        HttpResponse<byte[]> resp;
        try {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.net.http.HttpTimeoutException e) {
            throw wrapHttpTimeout(e, model, path, t0);
        }
        if (resp.statusCode() >= 400) {
            String text = new String(resp.body() == null ? new byte[0] : resp.body(), StandardCharsets.UTF_8);
            throw new ApiException(resp.statusCode(), OpenAiChat.openaiErrorMessage(resp.statusCode(), text), text);
        }
        return resp.body() == null ? new byte[0] : resp.body();
    }

    static void timing(String label, long startedNanos, String extra) {
        try {
            double ms = (System.nanoTime() - startedNanos) / 1_000_000.0;
            System.err.printf("[sq-timing] llm-model %s elapsed_ms=%.1f %s%n",
                    label, ms, extra == null ? "" : extra);
        } catch (Exception ignored) {
            // diagnostics must not break the node
        }
    }

    private void putFloat(Map<String, Object> cfg, String key) {
        Double v = NodeParams.toDoubleOrNull(get(key));
        if (v != null) {
            cfg.put(key, v);
        }
    }

    private void putResponseFormat(Map<String, Object> cfg) {
        Object rf = get("response_format");
        if (rf == null) {
            return;
        }
        if (rf instanceof Map<?, ?>) {
            cfg.put("response_format", rf);
            return;
        }
        String s = NodeParams.trim(rf);
        if (s.isEmpty()) {
            return;
        }
        Object parsed = NodeParams.parseJson(s);
        if (parsed instanceof Map<?, ?>) {
            cfg.put("response_format", parsed);
            return;
        }
        String low = s.toLowerCase(Locale.ROOT);
        if ("json".equals(low)) {
            cfg.put("response_format", Map.of("type", "json_object"));
        } else if ("text".equals(low)) {
            cfg.put("response_format", Map.of("type", "text"));
        }
    }

    private void putModalities(Map<String, Object> cfg, Map<String, Object> options) {
        Object mod = get("modalities");
        if (mod == null && options != null) {
            mod = options.get("modalities");
        }
        if (mod == null) {
            return;
        }
        List<Object> raw;
        if (mod instanceof List<?> list) {
            raw = new ArrayList<>(list);
        } else {
            String s = NodeParams.trim(mod);
            Object parsed = NodeParams.parseJson(s);
            if (parsed instanceof List<?> list) {
                raw = new ArrayList<>(list);
            } else {
                raw = new ArrayList<>();
                for (String p : s.split(",")) {
                    raw.add(p.trim());
                }
            }
        }
        List<String> filt = new ArrayList<>();
        for (Object m : raw) {
            String low = NodeParams.trim(m).toLowerCase(Locale.ROOT);
            if (("text".equals(low) || "audio".equals(low)) && !filt.contains(low)) {
                filt.add(low);
            }
        }
        if (!filt.isEmpty()) {
            cfg.put("modalities", filt);
        }
    }

    private void putAudio(Map<String, Object> cfg) {
        Object aud = get("audio");
        if (aud instanceof Map<?, ?>) {
            cfg.put("audio", aud);
            return;
        }
        if (aud instanceof String s && !s.isBlank()) {
            Object parsed = NodeParams.parseJson(s);
            if (parsed instanceof Map<?, ?>) {
                cfg.put("audio", parsed);
            }
        }
    }

    /** HTTP failure. {@link #getMessage()} is redacted (no Bearer / sk-). */
    static final class ApiException extends Exception {
        final int status;
        final String body;

        ApiException(int status, String message, String body) {
            super(message == null ? "" : message);
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }

    /** Connect / request / idle-stall failure. Message names model + endpoint + elapsed. */
    static final class TimeoutException extends RuntimeException {
        TimeoutException(String message, Throwable cause) {
            super(message == null ? "" : message, cause);
        }

        TimeoutException(String message) {
            super(message == null ? "" : message);
        }
    }
}
