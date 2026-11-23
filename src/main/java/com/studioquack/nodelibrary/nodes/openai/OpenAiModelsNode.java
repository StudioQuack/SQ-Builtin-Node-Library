package com.studioquack.nodelibrary.nodes.openai;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.OpenAiContentParts;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Models handler of the Built-in Node Library image.
 *
 * <p>Stateful BYO: daemon POSTs invoke JSON; reply keys match OUTPUT ports
 * ({@code llm_output}). Talks to {@code https://api.openai.com} over JDK
 * {@code HttpClient} — never {@code helper.llm()} / {@code executeStream}.
 * Thread-safe (shared client, no request-scoped statics). Never logs
 * {@code api_key} or {@code helperToken}. Never throws past the dispatcher.
 */
public final class OpenAiModelsNode implements NodeHandler {

    /** Must match {@code nodes/openai-models/node_definition.json} {@code _id}. */
    static final String SELF_TEMPLATE_ID = "4f682e07-d874-4d10-abb3-0174ca65ec19";

    private final String templateId;

    public OpenAiModelsNode() {
        this(SELF_TEMPLATE_ID);
    }

    public OpenAiModelsNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? SELF_TEMPLATE_ID
                : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        try {
            OpenAiConfig cfg = OpenAiConfig.from(request, helper);
            if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
                return ports(OpenAiChat.errorResponse("Missing api_key configuration."));
            }
            Map<String, Object> input = readLlmInput(request, helper);
            if (input == null) {
                return ports(OpenAiChat.errorResponse("No input received on 'llm_input' port."));
            }
            List<Object> messages = NodeParams.asList(input.get("messages"));
            List<Object> tools = NodeParams.asList(input.get("tools"));
            Map<String, Object> options = mergeOptions(input);
            String ml = cfg.modelLower();
            // Image-gen/TTS/STT keep native PDF (lastUserText ignores file parts).
            // Chat models without native-doc support extract PDF instead of a file part.
            boolean nativePdf = !OpenAiCapabilities.shouldGate(ml)
                    || OpenAiCapabilities.acceptsNativeDocuments(ml);
            messages = OpenAiContentParts.appendFilesToLastUserMessage(
                    messages, NodeParams.asList(input.get("inputFiles")),
                    OpenAiContentParts.Profile.FULL, nativePdf);
            // Fold is a no-op when inputFiles is empty; convert every message so
            // office docs embedded in content are extracted before the gate / HTTP.
            messages = new ArrayList<>(OpenAiContentParts.messagesToOpenAi(
                    messages, OpenAiContentParts.Profile.FULL, nativePdf));

            if (OpenAiConfig.isImageModel(ml)) {
                return ports(OpenAiMedia.images(cfg.apiKey(), cfg.model(), messages, options));
            }
            if (OpenAiConfig.isTtsModel(ml)) {
                return ports(OpenAiMedia.tts(cfg.apiKey(), cfg.model(), messages, options));
            }
            if (OpenAiConfig.isSttModel(ml)) {
                return ports(OpenAiMedia.stt(cfg.apiKey(), cfg.model(), messages, options));
            }

            if (OpenAiCapabilities.shouldGate(ml)) {
                OpenAiCapabilities.GateShape shape = OpenAiCapabilities.inspect(messages);
                boolean rejectImages = shape.images > 0 && !OpenAiCapabilities.acceptsVision(ml);
                OpenAiCapabilities.logDecision(cfg.model(), shape, rejectImages ? "images" : "none");
                if (rejectImages) {
                    return ports(OpenAiChat.errorResponse(OpenAiCapabilities.rejectImagesMessage(cfg.model())));
                }
            }

            List<Map<String, Object>> openaiMessages;
            try {
                openaiMessages = OpenAiChat.buildOpenaiMessages(messages);
            } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
                return ports(OpenAiChat.errorResponse("Message normalisation failed: " + OpenAiChat.redact(e.getMessage())));
            }

            boolean stream = cfg.shouldStream(options);
            // Reasoning models (gpt-5*, o-series) route to the Responses API: Chat Completions
            // never streams their reasoning, so the thinking channel would stay silent. The
            // Responses stream carries reasoning_summary_text deltas → thinking partials.
            boolean responsesApi = OpenAiResponses.supports(cfg, ml, options);
            Map<String, Object> call = responsesApi
                    ? OpenAiResponses.buildKwargs(cfg, openaiMessages, tools)
                    : cfg.buildChatKwargs(openaiMessages, tools, options);
            Map<String, Object> result = null;
            Exception last = null;
            long t0 = System.nanoTime();
            for (int attempt = 0; attempt < 4; attempt++) {
                try {
                    result = responsesApi
                            ? (stream
                                    ? OpenAiResponses.stream(call, cfg.apiKey(), helper,
                                            cfg.readTimeout(), cfg.streamIdleTimeout())
                                    : OpenAiResponses.nonStream(call, cfg.apiKey(), cfg.readTimeout()))
                            : (stream
                                    ? OpenAiSse.stream(call, cfg.apiKey(), helper,
                                            cfg.readTimeout(), cfg.streamIdleTimeout())
                                    : OpenAiSse.nonStream(call, cfg.apiKey(), cfg.readTimeout()));
                    OpenAiConfig.timing(stream ? "openai.stream" : "openai.nonstream", t0,
                            "model=" + cfg.model() + " attempt=" + attempt + " stream=" + stream
                                    + " api=" + (responsesApi ? "responses" : "chat"));
                    break;
                } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
                    last = e;
                    if (OpenAiConfig.isTimeoutFailure(e)) {
                        return ports(OpenAiChat.errorResponse(OpenAiChat.redact(
                                e.getMessage() == null
                                        ? OpenAiConfig.timeoutMessage(cfg.model(),
                                                responsesApi ? "/responses" : "/chat/completions",
                                                OpenAiConfig.elapsedSeconds(t0), "timed out")
                                        : e.getMessage())));
                    }
                    String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    // A model the Responses endpoint refuses outright (legacy o1-mini class)
                    // falls back to Chat Completions once — answer still streams, reasoning
                    // summaries simply stay unavailable there.
                    if (responsesApi && e instanceof OpenAiConfig.ApiException ae
                            && OpenAiResponses.isResponsesRejection(ae.status, err)) {
                        responsesApi = false;
                        call = cfg.buildChatKwargs(openaiMessages, tools, options);
                        continue;
                    }
                    if (attempt < 3 && OpenAiConfig.stripUnsupported(call, err)) {
                        System.err.println("DEBUG: stripping unsupported parameter and retrying.");
                        continue;
                    }
                    return ports(OpenAiChat.errorResponse("OpenAI API error: " + OpenAiChat.redact(err)));
                }
            }
            if (result == null) {
                String err = last == null ? "unknown" : OpenAiChat.redact(last.getMessage());
                return ports(OpenAiChat.errorResponse("OpenAI streaming failed: " + err));
            }
            return ports(result);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (OpenAiConfig.isTimeoutFailure(e)) {
                return ports(OpenAiChat.errorResponse(OpenAiChat.redact(
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            }
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ports(OpenAiChat.errorResponse(OpenAiChat.redact(msg)));
        }
    }

    /**
     * Helper {@code getInputValuesByPortValue("llm_input")} first; then
     * {@code inputVariables.llm_input}; then any input row whose value has {@code messages}.
     * A bare string becomes a single user message. Schema-default stubs (null messages) are skipped.
     */
    static Map<String, Object> readLlmInput(Map<String, Object> request, StudioQuackHelper helper) {
        Object raw = null;
        if (helper != null) {
            try {
                List<Object> vals = helper.getInputValuesByPortValue("llm_input");
                if (vals != null && !vals.isEmpty()) {
                    raw = vals.get(0);
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        if (!usable(raw) && request != null) {
            Object fromVars = NodeParams.asMap(request.get("inputVariables")).get("llm_input");
            if (usable(fromVars)) {
                raw = fromVars;
            }
        }
        if (!usable(raw) && helper != null) {
            try {
                for (Object item : helper.getInput()) {
                    Map<String, Object> row = NodeParams.asMap(item);
                    Object value = row.get("value");
                    if (value instanceof Map<?, ?> m && m.containsKey("messages") && usable(value)) {
                        raw = value;
                        break;
                    }
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return coercePayload(raw);
    }

    static boolean usable(Object raw) {
        if (raw instanceof String s) {
            return !s.isBlank();
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return usable(list.get(0));
        }
        if (raw instanceof Map<?, ?> m) {
            Object messages = m.get("messages");
            if (messages instanceof List<?> l && !l.isEmpty()) {
                return true;
            }
            if (messages instanceof String s && !s.isBlank()) {
                return true;
            }
            if (m.get("tools") instanceof List<?> t && !t.isEmpty()) {
                return true;
            }
            if (m.get("inputFiles") instanceof List<?> f && !f.isEmpty()) {
                return true;
            }
            Object nested = m.get("value");
            if (nested instanceof Map<?, ?> && usable(nested)) {
                return true;
            }
            return m.containsKey("stream") || m.containsKey("streaming");
        }
        return false;
    }

    static Map<String, Object> coercePayload(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return coercePayload(list.get(0));
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
                Object parsed = NodeParams.parseJson(t);
                if (parsed instanceof Map<?, ?>) {
                    return coercePayload(parsed);
                }
                if (parsed instanceof List<?>) {
                    Map<String, Object> wrap = new LinkedHashMap<>();
                    wrap.put("messages", parsed);
                    return wrap;
                }
            }
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("messages", List.of(Map.of("role", "user", "content", s)));
            return wrap;
        }
        Map<String, Object> map = NodeParams.mutableMap(raw);
        if (!map.containsKey("messages") && map.get("value") instanceof Map<?, ?>) {
            return coercePayload(map.get("value"));
        }
        Object messages = map.get("messages");
        if (messages instanceof String ms) {
            Object parsed = NodeParams.parseJson(ms);
            map.put("messages", parsed instanceof List<?> ? parsed
                    : List.of(Map.of("role", "user", "content", ms)));
        }
        Object tools = map.get("tools");
        if (tools instanceof String ts) {
            Object parsed = NodeParams.parseJson(ts);
            map.put("tools", parsed instanceof List<?> ? parsed : List.of());
        }
        Object inputFiles = map.get("inputFiles");
        if (inputFiles instanceof String fs) {
            Object parsed = NodeParams.parseJson(fs);
            map.put("inputFiles", parsed instanceof List<?> ? parsed : List.of());
        }
        return map;
    }

    /** Top-level {@code stream}/{@code streaming} and media kwargs merge into {@code options}. */
    static Map<String, Object> mergeOptions(Map<String, Object> payload) {
        Map<String, Object> options = NodeParams.mutableMap(payload.get("options"));
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            String k = e.getKey();
            if ("messages".equals(k) || "tools".equals(k) || "options".equals(k)
                    || "result".equals(k) || "inputFiles".equals(k) || "outputFiles".equals(k)) {
                continue;
            }
            options.putIfAbsent(k, e.getValue());
        }
        return options;
    }

    /**
     * Envelope {@code {llm_output: {result, outputFiles?}}}. {@code outputFiles}
     * mirrors {@code result.output_files} when that list is non-empty; omitted
     * when missing or empty. The result object is unchanged.
     */
    static Map<String, Object> ports(Map<String, Object> result) {
        Map<String, Object> assistant = result == null ? Map.of() : result;
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("result", assistant);
        Object files = assistant.get("output_files");
        if (files instanceof List<?> list && !list.isEmpty()) {
            inner.put("outputFiles", files);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("llm_output", inner);
        return out;
    }
}
