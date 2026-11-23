package studioquack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code studioquack.llm} namespace — OpenAI-style calls via the daemon LLM proxy.
 *
 * <p>Provider credentials never enter the container. Every method POSTs to
 * {@code /v1/helper/llm/...} over {@link HelperTransport} and is a silent no-op
 * (empty map / empty string) when the helper channel is absent. Never throws.
 *
 * <pre>{@code
 * Object envelope = helper.llm().completion(
 *         List.of(Map.of("role", "user", "content", "hello")),
 *         "local-model",
 *         null);
 * String text = helper.llm().chat(null, "local-model",
 *         List.of(Map.of("role", "user", "content", "hello")),
 *         null, null, null, false);
 * }</pre>
 */
public final class LlmHelper {

    private final HelperTransport transport;

    /**
     * @param transport shared Unix-socket client (token rebound per stateful request)
     */
    public LlmHelper(HelperTransport transport) {
        this.transport = transport;
    }

    /**
     * Legacy Local LLM entry point. POSTs {@code /v1/helper/llm/completion} and returns
     * the full chat-completions envelope (or an empty map when the channel is down).
     *
     * <p>Use this when a node calls {@code studioquack.llm.completion(...)} and expects
     * {@code choices[0].message}. {@code env} names a sibling environment; omit it
     * ({@code null}) to use this environment. The provider key never leaves the target daemon.
     *
     * <pre>{@code
     * Object reply = helper.llm().completion(
     *         List.of(Map.of("role", "user", "content", "summarise this")),
     *         "qwen2.5", null);
     * }</pre>
     *
     * @param messages OpenAI-style {@code {role, content}} maps; {@code null} if {@code prompt} is set
     * @param model    optional model id
     * @param prompt   optional single-prompt shorthand (folded into {@code messages} when messages is null)
     * @return provider envelope as a parsed object; never {@code null}
     */
    public Object completion(List<Map<String, Object>> messages, String model, String prompt) {
        return completion(messages, model, prompt, null);
    }

    /**
     * {@link #completion(List, String, String)} with an explicit target environment id/name.
     *
     * @param env target environment id or name; {@code null} = this environment
     */
    public Object completion(List<Map<String, Object>> messages, String model, String prompt, String env) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (messages != null) {
            body.put("messages", new ArrayList<>(messages));
        } else if (prompt != null) {
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        }
        if (prompt != null) {
            body.put("prompt", prompt);
        }
        if (model != null && !model.isBlank()) {
            body.put("model", model);
        }
        if (env != null && !env.isBlank()) {
            body.put("envId", env);
        }
        Object resp = transport.post("/v1/helper/llm/completion", body, HelperTransport.NO_TIME_LIMIT);
        return resp != null ? resp : Map.of();
    }

    /**
     * OpenAI-compatible chat against this (or a granted sibling) environment.
     * POSTs {@code /v1/helper/llm/chat/completions}.
     *
     * <p>Use this for chat-style calls. By default the assistant text is extracted;
     * pass {@code returnEnvelope=true} for the raw provider body.
     *
     * <pre>{@code
     * String answer = String.valueOf(helper.llm().chat(
     *         null, "qwen2.5",
     *         List.of(Map.of("role", "user", "content", "2+2?")),
     *         null, null, null, false));
     * }</pre>
     *
     * @param envId           legacy leading positional; {@code env} wins when both are set
     * @param model           optional model id
     * @param messages        OpenAI-style messages
     * @param prompt          shorthand user turn when {@code messages} is null
     * @param temperature     optional sampling temperature
     * @param maxTokens       optional max tokens
     * @param returnEnvelope  {@code true} to return the full provider JSON
     * @return extracted content string, or the envelope map when {@code returnEnvelope}
     */
    public Object chat(String envId, String model, List<Map<String, Object>> messages, String prompt,
                       Number temperature, Number maxTokens, boolean returnEnvelope) {
        return chat(envId, model, messages, prompt, temperature, maxTokens, returnEnvelope, null);
    }

    /**
     * {@link #chat(String, String, List, String, Number, Number, boolean)} with explicit {@code env}.
     *
     * @param env target environment id or name; wins over {@code envId}
     */
    public Object chat(String envId, String model, List<Map<String, Object>> messages, String prompt,
                       Number temperature, Number maxTokens, boolean returnEnvelope, String env) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (model != null && !model.isBlank()) {
            body.put("model", model);
        }
        if (messages != null) {
            body.put("messages", new ArrayList<>(messages));
        } else if (prompt != null) {
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        }
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }
        String effectiveEnv = (env != null && !env.isBlank()) ? env : envId;
        if (effectiveEnv != null && !effectiveEnv.isBlank()) {
            body.put("envId", effectiveEnv);
        }
        Object envelope = transport.post("/v1/helper/llm/chat/completions", body, HelperTransport.NO_TIME_LIMIT);
        if (returnEnvelope) {
            return envelope != null ? envelope : Map.of();
        }
        return extractContent(envelope);
    }

    /**
     * Alias of {@link #chat(String, String, List, String, Number, Number, boolean)} —
     * on this daemon both go through the same provider proxy.
     *
     * <pre>{@code
     * Object text = helper.llm().local(null, "qwen2.5",
     *         List.of(Map.of("role", "user", "content", "hi")),
     *         null, null, null, false);
     * }</pre>
     */
    public Object local(String envId, String model, List<Map<String, Object>> messages, String prompt,
                        Number temperature, Number maxTokens, boolean returnEnvelope) {
        return chat(envId, model, messages, prompt, temperature, maxTokens, returnEnvelope, null);
    }

    /**
     * Full-body non-stream chat: POSTs {@code body} verbatim to
     * {@code /v1/helper/llm/chat/completions} and returns the provider envelope.
     * Unlike {@link #chat} this forwards the complete OpenAI-style parameter set
     * (tools, sampling, response_format, …) — the daemon proxy relays the body
     * verbatim, so nothing is dropped on the non-stream path. {@code stream} is
     * forced {@code false} and {@code stream_options} is stripped.
     *
     * @param body chat body (model/messages/tools/sampling…); {@code null} becomes {@code {}}
     * @return provider envelope as a parsed object; empty map when the channel is down
     */
    public Object chatRaw(Map<String, Object> body) {
        Map<String, Object> copy = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        copy.put("stream", false);
        copy.remove("stream_options");
        Object envelope = transport.post("/v1/helper/llm/chat/completions", copy, HelperTransport.NO_TIME_LIMIT);
        return envelope != null ? envelope : Map.of();
    }

    /**
     * Stream an OpenAI-compatible chat ({@code POST /v1/helper/llm/chat} with {@code stream:true}).
     *
     * <p>Not {@code /v1/helper/llm/chat/completions} — that remains the buffered
     * {@link #chat} path. The daemon relays provider SSE verbatim; iterate
     * {@link SseFrame} data payloads. Channel-down yields a failed empty stream —
     * never throws. Never log the body (it may carry prompts).
     *
     * <pre>{@code
     * try (SseStream stream = helper.llm().chatStream(Map.of(
     *         "model", "qwen2.5",
     *         "messages", List.of(Map.of("role", "user", "content", "hi"))))) {
     *     for (SseFrame frame : stream) {
     *         // frame.data is one provider SSE payload
     *     }
     * }
     * }</pre>
     *
     * @param body chat body; {@code null} becomes {@code {stream:true}}
     * @return live or already-failed stream; never {@code null}
     */
    public SseStream chatStream(Map<String, Object> body) {
        try {
            Map<String, Object> copy = new LinkedHashMap<>();
            if (body != null) {
                copy.putAll(body);
            }
            copy.put("stream", true);
            return transport.postSse("/v1/helper/llm/chat", copy);
        } catch (Exception ignored) {
            return SseStream.failed("chatStream");
        }
    }

    /**
     * Models the engine's provider reports ({@code GET /v1/helper/llm/models}). Same operation
     * as Python {@code llm.models()} and JS {@code llm.models()}; empty map when the channel is down.
     */
    public Object models() {
        Object resp = transport.get("/v1/helper/llm/models", 30);
        return resp != null ? resp : Map.of();
    }

    /**
     * Load a model on the engine's provider ({@code POST /v1/helper/llm/load}). {@code params}
     * are forwarded verbatim; {@code model} is sent as {@code model} and {@code model_id}.
     *
     * <pre>{@code
     * helper.llm().load("qwen2.5", null);
     * }</pre>
     */
    public Object load(String model, Map<String, Object> params) {
        return modelOp("load", model, params, false);
    }

    /** Unload a model ({@code POST /v1/helper/llm/unload}); same body rules as {@link #load}. */
    public Object unload(String model, Map<String, Object> params) {
        return modelOp("unload", model, params, false);
    }

    /**
     * Start a model download ({@code POST /v1/helper/llm/download}); {@code model} is also sent
     * as {@code repo}. Poll {@link #downloadStatus(String)} for progress.
     */
    public Object download(String model, Map<String, Object> params) {
        return modelOp("download", model, params, true);
    }

    /**
     * Download progress ({@code GET /v1/helper/llm/download/status}).
     *
     * @param jobId optional download job id; {@code null} asks for the current download
     */
    public Object downloadStatus(String jobId) {
        String path = "/v1/helper/llm/download/status";
        if (jobId != null && !jobId.isBlank()) {
            path += "?job_id=" + java.net.URLEncoder.encode(jobId, java.nio.charset.StandardCharsets.UTF_8);
        }
        Object resp = transport.get(path, 30);
        return resp != null ? resp : Map.of();
    }

    private Object modelOp(String op, String model, Map<String, Object> params, boolean withRepo) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((k, v) -> {
                if (v != null) {
                    body.put(k, v);
                }
            });
        }
        if (model != null && !model.isBlank()) {
            body.putIfAbsent("model", model);
            body.putIfAbsent("model_id", model);
            if (withRepo) {
                body.putIfAbsent("repo", model);
            }
        }
        Object resp = transport.post("/v1/helper/llm/" + op, body, HelperTransport.NO_TIME_LIMIT);
        return resp != null ? resp : Map.of();
    }

    /**
     * Pull assistant text out of an OpenAI-style envelope. Empty string on a missing body.
     */
    static String extractContent(Object envelope) {
        Map<String, Object> map = Json.asMap(envelope);
        if (map.containsKey("content")) {
            return Json.str(map.get("content"));
        }
        List<Object> choices = Json.asList(map.get("choices"));
        if (!choices.isEmpty() && choices.get(0) instanceof Map<?, ?>) {
            Map<String, Object> first = Json.asMap(choices.get(0));
            Map<String, Object> msg = Json.asMap(first.get("message"));
            if (!msg.isEmpty()) {
                return Json.str(msg.get("content"));
            }
            if (first.get("text") != null) {
                return Json.str(first.get("text"));
            }
        }
        return "";
    }
}
