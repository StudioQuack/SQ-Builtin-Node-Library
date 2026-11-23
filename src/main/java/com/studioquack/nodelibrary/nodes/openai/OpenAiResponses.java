package com.studioquack.nodelibrary.nodes.openai;

import com.studioquack.nodelibrary.core.ProviderStream;
import com.studioquack.nodelibrary.core.StreamCollector;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI Responses API ({@code POST /v1/responses}) path for reasoning models.
 *
 * <p>Chat Completions never streams reasoning content for GPT-5 / o-series models — the
 * reasoning summary only streams from the Responses API, and only when the request carries
 * {@code reasoning: {summary: "auto"}} plus {@code include: ["reasoning.encrypted_content"]}.
 * This class translates the already-normalised Chat-shaped messages/tools into the Responses
 * wire shape and parses the event stream with the shared {@code responses-sse} collector, so
 * {@code response.reasoning_summary_text.delta} reaches the {@code thinking} channel exactly
 * like a local-llm {@code thinking} field — the daemon graph-routes both identically.
 */
final class OpenAiResponses {

    private OpenAiResponses() {
        // Utility — never instantiated.
    }

    /**
     * The Responses path is taken for reasoning models only. Audio output stays on Chat
     * Completions: the {@code modalities}/{@code audio} knobs have no Responses equivalent
     * on this node, and no reasoning model emits audio today.
     */
    static boolean supports(OpenAiConfig cfg, String modelLower, Map<String, Object> options) {
        if (!OpenAiConfig.isReasoning(modelLower)) {
            return false;
        }
        Object mod = cfg.get("modalities");
        if (!OpenAiConfig.present(mod) && options != null) {
            mod = options.get("modalities");
        }
        return !NodeParams.trim(mod).toLowerCase(Locale.ROOT).contains("audio");
    }

    /**
     * A 4xx that means "this model is not served by /v1/responses" (legacy o1-mini/-preview
     * class), as opposed to a parameter-level 400 the strip-retry can repair. The caller
     * falls back to Chat Completions on a true rejection.
     */
    static boolean isResponsesRejection(int status, String err) {
        if (status == 404) {
            return true;
        }
        if (status != 400 || err == null) {
            return false;
        }
        String low = err.toLowerCase(Locale.ROOT);
        if (low.contains("unsupported parameter") || low.contains("unsupported_parameter")) {
            return false;
        }
        return low.contains("not supported on") || low.contains("does not support")
                || (low.contains("model") && (low.contains("not supported") || low.contains("unsupported")));
    }

    /**
     * Responses request body. {@code messages}/{@code tools} arrive Chat-shaped from
     * {@link OpenAiChat#buildOpenaiMessages} / {@link OpenAiChat#buildOpenaiTools} and are
     * re-shaped here; sampling keys are omitted (reasoning models reject them everywhere).
     * {@code stream} is NOT set — the caller adds it.
     */
    static Map<String, Object> buildKwargs(OpenAiConfig cfg, List<Map<String, Object>> chatMessages,
                                           List<Object> tools) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", cfg.model());
        req.put("input", toResponsesInput(chatMessages));

        Object re = cfg.get("reasoning_effort");
        String effort = OpenAiConfig.present(re)
                ? NodeParams.trim(re) : OpenAiConfig.DEFAULT_REASONING_EFFORT;
        Map<String, Object> reasoning = new LinkedHashMap<>();
        reasoning.put("effort", effort);
        // "auto" asks the API to stream reasoning_summary_text.delta events; without the
        // encrypted-content include the summary streams empty on the current API revision.
        reasoning.put("summary", "auto");
        req.put("reasoning", reasoning);
        req.put("include", List.of("reasoning.encrypted_content"));

        Integer mct = NodeParams.toIntOrNull(cfg.get("max_completion_tokens"));
        if (mct != null && mct > 0) {
            req.put("max_output_tokens", mct);
        }

        Map<String, Object> text = new LinkedHashMap<>();
        Object vb = cfg.get("verbosity");
        if (OpenAiConfig.present(vb)) {
            text.put("verbosity", NodeParams.trim(vb));
        }
        Object format = responsesFormat(cfg.get("response_format"));
        if (format != null) {
            text.put("format", format);
        }
        if (!text.isEmpty()) {
            req.put("text", text);
        }

        for (String p : List.of("prompt_cache_key", "safety_identifier", "service_tier")) {
            Object v = cfg.get(p);
            if (OpenAiConfig.present(v)) {
                req.put(p, NodeParams.str(v));
            }
        }

        List<Map<String, Object>> chatTools = OpenAiChat.buildOpenaiTools(tools);
        if (!chatTools.isEmpty()) {
            req.put("tools", flattenTools(chatTools));
            req.put("tool_choice", "auto");
            Boolean ptc = NodeParams.boolish(cfg.get("parallel_tool_calls"));
            if (Boolean.TRUE.equals(ptc)) {
                req.put("parallel_tool_calls", true);
            }
        }
        return req;
    }

    /**
     * Streaming call: SSE events feed the shared {@code responses-sse} collector.
     * Header wait is {@link HttpRequest#timeout}; idle-after-headers uses the
     * process-wide watchdog (same as Chat Completions SSE).
     */
    static Map<String, Object> stream(Map<String, Object> body, String apiKey, StudioQuackHelper helper)
            throws Exception {
        return stream(body, apiKey, helper, OpenAiConfig.REQUEST_TIMEOUT, OpenAiConfig.STREAM_IDLE_TIMEOUT);
    }

    static Map<String, Object> stream(Map<String, Object> body, String apiKey, StudioQuackHelper helper,
                                      Duration readTimeout, Duration idleTimeout)
            throws Exception {
        Map<String, Object> req = new LinkedHashMap<>(body);
        req.put("stream", true);
        String model = NodeParams.str(body.get("model"));
        long t0 = System.nanoTime();
        HttpResponse<InputStream> resp = postStream(req, apiKey, readTimeout, model, t0);
        if (resp.statusCode() >= 400) {
            String err = utf8(resp.body());
            throw new OpenAiConfig.ApiException(resp.statusCode(),
                    OpenAiChat.openaiErrorMessage(resp.statusCode(), err), err);
        }
        String ct = resp.headers().firstValue("content-type").orElse("");
        StreamCollector collector = ProviderStream.collector("responses-sse", "llm_output", helper);
        AtomicBoolean stalled = new AtomicBoolean();
        try (InputStream watched = OpenAiIdleStream.watch(resp.body(), idleTimeout, stalled)) {
            ProviderStream.consume(collector, "responses-sse", watched, ct, 0);
        } catch (java.io.IOException e) {
            if (stalled.get() || e instanceof OpenAiIdleStream.StreamStalledException) {
                throw new OpenAiConfig.TimeoutException(OpenAiConfig.timeoutMessage(
                        model, "/responses", OpenAiConfig.elapsedSeconds(t0), "stream stalled"), e);
            }
            throw e;
        }
        if (stalled.get()) {
            throw new OpenAiConfig.TimeoutException(OpenAiConfig.timeoutMessage(
                    model, "/responses", OpenAiConfig.elapsedSeconds(t0), "stream stalled"));
        }
        OpenAiConfig.timing("openai.responses.stream", t0, "model=" + body.get("model"));
        return collector.toAssistant();
    }

    /** Non-stream call: the unary Response object is ingested as one envelope, no partials. */
    static Map<String, Object> nonStream(Map<String, Object> body, String apiKey) throws Exception {
        return nonStream(body, apiKey, OpenAiConfig.REQUEST_TIMEOUT);
    }

    static Map<String, Object> nonStream(Map<String, Object> body, String apiKey, Duration readTimeout)
            throws Exception {
        long t0 = System.nanoTime();
        String model = NodeParams.str(body.get("model"));
        Map<String, Object> resp = OpenAiConfig.postJson("/responses", apiKey, body, readTimeout, model);
        StreamCollector collector = ProviderStream.collector("responses-sse", "llm_output", null);
        collector.ingest(null, resp);
        OpenAiConfig.timing("openai.responses.nonstream", t0, "model=" + body.get("model"));
        return collector.toAssistant();
    }

    /**
     * Chat-shaped messages → Responses {@code input} items. Assistant {@code tool_calls}
     * become standalone {@code function_call} items and {@code tool} results become
     * {@code function_call_output} items — the Responses API has no tool role.
     */
    static List<Object> toResponsesInput(List<Map<String, Object>> messages) {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            if (m == null || m.isEmpty()) {
                continue;
            }
            String role = NodeParams.trim(m.get("role")).toLowerCase(Locale.ROOT);
            if ("system".equals(role) || "developer".equals(role)) {
                String text = textOnly(m.get("content"));
                if (!text.isEmpty()) {
                    out.add(Map.of("role", "developer", "content", text));
                }
                continue;
            }
            if ("assistant".equals(role)) {
                List<Map<String, Object>> parts = new ArrayList<>();
                Object content = m.get("content");
                if (content instanceof String s && !s.isEmpty()) {
                    parts.add(Map.of("type", "output_text", "text", s));
                } else if (content instanceof List<?> list) {
                    for (Object p : list) {
                        Map<String, Object> pm = NodeParams.asMap(p);
                        String text = NodeParams.str(pm.getOrDefault("text", pm.get("content")));
                        if (!text.isEmpty()) {
                            parts.add(Map.of("type", "output_text", "text", text));
                        }
                    }
                }
                if (!parts.isEmpty()) {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", "assistant");
                    msg.put("content", parts);
                    out.add(msg);
                }
                for (Object tcObj : NodeParams.asList(m.get("tool_calls"))) {
                    Map<String, Object> tc = NodeParams.asMap(tcObj);
                    Map<String, Object> fn = NodeParams.asMap(tc.get("function"));
                    String name = NodeParams.trim(fn.get("name"));
                    if (name.isEmpty()) {
                        continue;
                    }
                    String callId = NodeParams.trim(tc.get("id"));
                    Map<String, Object> call = new LinkedHashMap<>();
                    call.put("type", "function_call");
                    call.put("call_id", callId.isEmpty() ? "call_" + NodeParams.uuid24() : callId);
                    call.put("name", name);
                    call.put("arguments", NodeParams.str(fn.get("arguments")));
                    out.add(call);
                }
                continue;
            }
            if ("tool".equals(role)) {
                String callId = NodeParams.trim(m.get("tool_call_id"));
                if (callId.isEmpty()) {
                    callId = NodeParams.trim(m.get("id"));
                }
                if (callId.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", "function_call_output");
                row.put("call_id", callId);
                Object content = m.get("content");
                row.put("output", content instanceof String s ? s : Json.write(content));
                out.add(row);
                continue;
            }
            // user (default): string content passes through; parts map to input_* types.
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", role.isEmpty() ? "user" : role);
            Object content = m.get("content");
            if (content instanceof List<?> list) {
                List<Map<String, Object>> parts = new ArrayList<>();
                for (Object p : list) {
                    Map<String, Object> conv = toInputPart(NodeParams.asMap(p));
                    if (conv != null) {
                        parts.add(conv);
                    }
                }
                msg.put("content", parts);
            } else {
                msg.put("content", content == null ? "" : NodeParams.str(content));
            }
            out.add(msg);
        }
        return out;
    }

    /** One Chat content part → one Responses input part; null when nothing sendable remains. */
    private static Map<String, Object> toInputPart(Map<String, Object> part) {
        if (part.isEmpty()) {
            return null;
        }
        String type = NodeParams.trim(part.get("type")).toLowerCase(Locale.ROOT);
        if ("text".equals(type) || "input_text".equals(type)) {
            String text = NodeParams.str(part.get("text"));
            return text.isEmpty() ? null : Map.of("type", "input_text", "text", text);
        }
        if ("image_url".equals(type) || "input_image".equals(type)) {
            Object raw = part.get("image_url");
            String url = raw instanceof Map<?, ?>
                    ? NodeParams.trim(NodeParams.asMap(raw).get("url")) : NodeParams.trim(raw);
            if (url.isEmpty()) {
                return null;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "input_image");
            row.put("image_url", url);
            String detail = raw instanceof Map<?, ?>
                    ? NodeParams.trim(NodeParams.asMap(raw).get("detail")) : "";
            if (!detail.isEmpty()) {
                row.put("detail", detail);
            }
            return row;
        }
        if ("input_audio".equals(type)) {
            Map<String, Object> audio = NodeParams.asMap(part.get("input_audio"));
            return audio.isEmpty() ? null : Map.of("type", "input_audio", "input_audio", audio);
        }
        if ("file".equals(type) || "input_file".equals(type)) {
            Map<String, Object> file = NodeParams.asMap(part.get("file"));
            String fileData = NodeParams.trim(file.get("file_data"));
            if (fileData.isEmpty()) {
                return null;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "input_file");
            row.put("file_data", fileData);
            String filename = NodeParams.trim(file.get("filename"));
            if (!filename.isEmpty()) {
                row.put("filename", filename);
            }
            return row;
        }
        String fallback = NodeParams.str(part.getOrDefault("text", part.get("content")));
        return fallback.isEmpty() ? null : Map.of("type", "input_text", "text", fallback);
    }

    /** Chat {@code {type:function, function:{…}}} → Responses flat {@code {type:function, …}}. */
    static List<Map<String, Object>> flattenTools(List<Map<String, Object>> chatTools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> tool : chatTools) {
            Map<String, Object> fn = NodeParams.asMap(tool.get("function"));
            if (fn.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "function");
            row.put("name", NodeParams.str(fn.get("name")));
            Object desc = fn.get("description");
            if (desc != null && !NodeParams.str(desc).isEmpty()) {
                row.put("description", NodeParams.str(desc));
            }
            Object params = fn.get("parameters");
            row.put("parameters", params == null ? Map.of() : params);
            if (fn.get("strict") != null) {
                row.put("strict", fn.get("strict"));
            }
            out.add(row);
        }
        return out;
    }

    /** Chat {@code response_format} → Responses {@code text.format}; null when unset/plain text. */
    private static Object responsesFormat(Object rf) {
        if (rf == null) {
            return null;
        }
        Object format = rf;
        if (!(rf instanceof Map<?, ?>)) {
            String s = NodeParams.trim(rf);
            if (s.isEmpty()) {
                return null;
            }
            Object parsed = NodeParams.parseJson(s);
            if (parsed instanceof Map<?, ?>) {
                format = parsed;
            } else if ("json".equals(s.toLowerCase(Locale.ROOT))) {
                format = Map.of("type", "json_object");
            } else {
                return null;
            }
        }
        String type = NodeParams.trim(NodeParams.asMap(format).get("type")).toLowerCase(Locale.ROOT);
        return "text".equals(type) || type.isEmpty() ? null : format;
    }

    /** Join a text-only content value; parts collapse to their text (system/developer lane). */
    private static String textOnly(Object content) {
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object p : list) {
                Map<String, Object> pm = NodeParams.asMap(p);
                String t = NodeParams.str(pm.getOrDefault("text", pm.get("content")));
                if (!t.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(t);
                }
            }
            return sb.toString();
        }
        return content == null ? "" : NodeParams.str(content);
    }

    private static HttpResponse<InputStream> postStream(Map<String, Object> body, String apiKey,
                                                         Duration readTimeout, String model, long startedNanos)
            throws Exception {
        String json = Json.write(body);
        HttpRequest req = OpenAiConfig.authed("/responses", apiKey, readTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        try {
            return OpenAiConfig.HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.net.http.HttpTimeoutException e) {
            throw OpenAiConfig.wrapHttpTimeout(e, model, "/responses", startedNanos);
        }
    }

    private static String utf8(InputStream in) throws java.io.IOException {
        if (in == null) {
            return "";
        }
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
