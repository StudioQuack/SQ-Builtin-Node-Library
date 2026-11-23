package com.studioquack.nodelibrary.nodes.localllm;

import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.Json;
import studioquack.SseStream;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local LLM provider node. All inference goes through {@code helper.llm()}
 * (daemon proxy). Never call a provider URL and never read API keys.
 *
 * <p>Streaming (default when {@code stream}/{@code streaming} is unset) uses
 * {@code llm().chatStream} and live {@code emitPartial("thinking"|"answer")}.
 * If {@link SseStream#transportFailed()} the node falls back to
 * {@code llm().completion(messages, model, prompt, env)}.
 *
 * <p>Non-stream ({@code stream:false}) goes through {@code chatRaw} with the full
 * parameter body — tools and sampling fields are forwarded exactly like the
 * streaming path, and the envelope is normalized to the same v2 assistant shape.
 *
 * <p>Thread-safe: no mutable static request state. Concurrent dispatches each
 * get their own helper and collectors.
 */
public final class LocalLlmNode implements NodeHandler {

    /** Must match {@code nodes/local-llm/node_definition.json} {@code _id}. */
    public static final String DEFAULT_TEMPLATE_ID = "9462f700-00cc-4e9c-872d-97a97aba984c";

    private final String templateId;

    public LocalLlmNode() {
        this(DEFAULT_TEMPLATE_ID);
    }

    public LocalLlmNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? DEFAULT_TEMPLATE_ID
                : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Always returns {@code {llm_output: {result: …, outputFiles?}}}. Failures are
     * {@code {error, type}} inside {@code result} — never thrown to dispatch.
     * Never logs {@code helperToken} or file bytes.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        try {
            Object payload = LocalLlmInput.read(request, helper);
            if (payload == null) {
                return ports(error("No input received on llm_input", "ValueError"));
            }
            LocalLlmConfig cfg = LocalLlmConfig.load(request, helper, payload);
            Object messages = LocalLlmInput.messagesOf(payload);
            Object prompt = LocalLlmInput.promptOf(payload);
            Object tools = LocalLlmInput.toolsOf(payload);
            Object inputFiles = LocalLlmInput.inputFilesOf(payload);
            // Capability gate: a text-only model given an image does not fail fast — the
            // local server accepts the body and then stalls until the sandbox deadline
            // ("stateful request deadline exceeded"). Refuse up-front with guidance instead.
            if (containsImage(messages, inputFiles) && !isVisionModel(cfg.model)) {
                return ports(error("Model '" + cfg.model + "' is not a known vision model, but the "
                        + "input carries image file(s). Pick a vision-capable local model "
                        + "(e.g. qwen2.5-vl / qwen3-vl, llava, llama3.2-vision, gemma3, pixtral, "
                        + "minicpm-v) or remove the image from the input.", "CapabilityError"));
            }
            Object result = cfg.stream
                    ? streamThenMaybeComplete(helper, cfg, messages, prompt, tools, inputFiles)
                    : complete(helper, cfg, messages, prompt, tools, inputFiles);
            return ports(result);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return ports(error(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /**
     * Prefer SSE. Transport failure (no frames / curl down) → non-stream completion.
     */
    private static Object streamThenMaybeComplete(StudioQuackHelper helper, LocalLlmConfig cfg,
                                                  Object messages, Object prompt, Object tools,
                                                  Object inputFiles) {
        if (helper == null || helper.llm() == null) {
            return complete(helper, cfg, messages, prompt, tools, inputFiles);
        }
        SseStream stream = helper.llm().chatStream(cfg.streamBody(messages, prompt, tools, inputFiles));
        Object assembled = LocalLlmStream.collect(stream, helper);
        if (assembled instanceof Map<?, ?> m && !m.isEmpty()) {
            Map<String, Object> assistant = Json.asMap(m);
            // A clean stream with zero text/tool_calls/thoughts/files means the provider
            // generated nothing (e.g. a zero token cap) — that is a failed turn, not an
            // answer. Surface it so callers (AI Agent, DuckBot) can react instead of
            // storing an empty reply.
            if (isBlankAssistant(assistant)) {
                return error("LLM returned an empty response — no text, tool calls, or thoughts "
                        + "(check max_tokens/max_completion_tokens and the model)", "EmptyResponseError");
            }
            return assistant;
        }
        return complete(helper, cfg, messages, prompt, tools, inputFiles);
    }

    /**
     * Non-stream path: full parameter body via {@code chatRaw} (tools and sampling
     * are forwarded — the daemon proxy relays the body verbatim), then the provider
     * envelope is normalized through the stream accumulator so the result has the
     * same v2 assistant shape as a streamed turn (content blocks, agent-style
     * {@code tool_calls:{id,name,args}}, thoughts, usage, finish_reason).
     * Provider error envelopes pass through untouched.
     */
    private static Object complete(StudioQuackHelper helper, LocalLlmConfig cfg,
                                   Object messages, Object prompt, Object tools,
                                   Object inputFiles) {
        if (helper == null || helper.llm() == null) {
            return error("LLM helper channel is unavailable", "RuntimeError");
        }
        Map<String, Object> body = cfg.streamBody(messages, prompt, tools, inputFiles);
        body.remove("stream");
        Object envelope = helper.llm().chatRaw(body);
        Map<String, Object> map = Json.asMap(envelope);
        if (map.isEmpty()) {
            return Map.of();
        }
        if (map.get("error") != null) {
            return envelope;
        }
        Object rejected = providerRejection(map);
        if (rejected != null) {
            return rejected;
        }
        LocalLlmStream acc = new LocalLlmStream();
        acc.ingest(envelope, null);
        Map<String, Object> assistant = acc.toAssistant();
        // Files may sit on the raw envelope (not just the extracted message).
        mergeOutputFiles(assistant, envelope);
        // Degenerate envelope (no text/tool_calls/thoughts/files) — keep the legacy raw
        // extract so nothing the provider said is hidden from the caller.
        if (isBlankAssistant(assistant)) {
            Object legacy = extractMessage(envelope);
            if (legacy instanceof Map<?, ?>) {
                Map<String, Object> copy = new LinkedHashMap<>(Json.asMap(legacy));
                mergeOutputFiles(copy, envelope);
                legacy = copy;
            }
            // The raw extract is empty too → the provider generated nothing at all.
            if (isBlankMessage(legacy)) {
                return error("LLM returned an empty response — no text, tool calls, or thoughts "
                        + "(check max_tokens/max_completion_tokens and the model)", "EmptyResponseError");
            }
            return legacy;
        }
        return assistant;
    }

    /**
     * OpenAI-compatible servers (SGLang, vLLM) answer a rejected request with
     * {@code {object:"error", message, type, code}}; without this it read as an empty answer.
     *
     * @return {@code null} for a normal envelope; a rejection fails the node (see {@link #error})
     *         with the server's own reason
     */
    static Object providerRejection(Map<String, Object> envelope) {
        if (envelope == null || !"error".equals(Json.str(envelope.get("object")))) {
            return null;
        }
        String message = Json.str(envelope.get("message"));
        String type = Json.str(envelope.get("type"));
        return error("The model server rejected the request (HTTP " + Json.str(envelope.get("code")) + "): "
                        + (message.length() > 600 ? message.substring(0, 600) + "…" : message),
                type.isBlank() ? "ProviderError" : type);
    }

    /** True when a provider-shaped message carries no text, tool calls, or reasoning. */
    static boolean isBlankMessage(Object msg) {
        Map<String, Object> m = Json.asMap(msg);
        if (m.isEmpty()) {
            return true;
        }
        if (!outputFilesOf(m).isEmpty()) {
            return false;
        }
        if (!Json.asList(m.get("tool_calls")).isEmpty()) {
            return false;
        }
        String reasoning = Json.str(m.get("reasoning_content"));
        if (reasoning != null && !reasoning.isBlank()) {
            return false;
        }
        Object content = m.get("content");
        if (content instanceof String s) {
            return s.isBlank();
        }
        Map<String, Object> contentMap = Json.asMap(content);
        if (!contentMap.isEmpty()) {
            String t = Json.str(contentMap.get("text"));
            return t == null || t.isBlank();
        }
        if (content instanceof List<?>) {
            for (Object block : Json.asList(content)) {
                String t = Json.str(Json.asMap(block).get("text"));
                if (t != null && !t.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True when the normalized assistant carries no text, tool calls, or thoughts. */
    static boolean isBlankAssistant(Map<String, Object> assistant) {
        if (assistant.get("tool_calls") != null || assistant.get("thoughts") != null) {
            return false;
        }
        if (!outputFilesOf(assistant).isEmpty()) {
            return false;
        }
        for (Object block : Json.asList(assistant.get("content"))) {
            String t = Json.str(Json.asMap(block).get("text"));
            if (t != null && !t.isBlank()) {
                return false;
            }
        }
        return true;
    }

    static Object extractMessage(Object envelope) {
        Map<String, Object> map = Json.asMap(envelope);
        List<Object> choices = Json.asList(map.get("choices"));
        if (!choices.isEmpty()) {
            Map<String, Object> choice = Json.asMap(choices.get(0));
            Object msg = choice.get("message");
            if (msg instanceof Map<?, ?> m && !m.isEmpty()) {
                return msg;
            }
        }
        return envelope == null ? Map.of() : envelope;
    }

    private static String promptToString(Object prompt) {
        if (prompt == null) {
            return null;
        }
        if (prompt instanceof String s) {
            return s;
        }
        return Json.write(prompt);
    }

    static Map<String, Object> error(String message, String type) {
        throw new studioquack.StudioQuackError("PROVIDER_ERROR",
                message == null ? "" : message,
                Map.of("exceptionType", type == null || type.isBlank() ? "Error" : type));
    }

    /**
     * Known vision-capable local model id patterns (lowercase substring match):
     * Qwen VL line, LLaVA, Llama 3.2 Vision, Gemma 3 (multimodal sizes), Pixtral,
     * MiniCPM-V, Moondream, InternVL, SmolVLM, Idefics, CogVLM. A blank model id
     * is NOT gated — the server then decides (and errors) itself.
     */
    private static final List<String> VISION_MODEL_HINTS = List.of(
            "-vl", "vlm", "vision", "llava", "pixtral", "moondream",
            "minicpm-v", "gemma3", "internvl", "idefics", "cogvlm");

    static boolean isVisionModel(String model) {
        if (model == null || model.isBlank()) {
            return true; // unknown — let the provider answer (and error) itself
        }
        String id = model.toLowerCase(java.util.Locale.ROOT);
        for (String hint : VISION_MODEL_HINTS) {
            if (id.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /** True when any input file or message content block is an image. */
    static boolean containsImage(Object messages, Object inputFiles) {
        for (Object f : fileListOf(inputFiles)) {
            if (isImageBlock(f)) {
                return true;
            }
        }
        for (Object msg : Json.asList(messages)) {
            Map<String, Object> m = Json.asMap(msg);
            Object content = m.get("content");
            if (content instanceof List<?>) {
                for (Object block : Json.asList(content)) {
                    if (isImageBlock(block)) {
                        return true;
                    }
                }
            }
            for (Object f : fileListOf(m.get("files"))) {
                if (isImageBlock(f)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Object> fileListOf(Object o) {
        if (o instanceof List<?>) {
            return Json.asList(o);
        }
        if (o instanceof Map<?, ?>) {
            return List.of(o);
        }
        return List.of();
    }

    /** Image when kind/type is image or mime_type starts with {@code image/} (case-insensitive). */
    private static boolean isImageBlock(Object block) {
        Map<String, Object> m = Json.asMap(block);
        if (m.isEmpty()) {
            return false;
        }
        for (String key : List.of("kind", "type")) {
            String v = Json.str(m.get(key));
            if (v != null && "image".equalsIgnoreCase(v)) {
                return true;
            }
        }
        String mime = Json.str(m.get("mime_type"));
        if (mime == null) {
            mime = Json.str(m.get("mimeType"));
        }
        return mime != null && mime.toLowerCase(java.util.Locale.ROOT).startsWith("image/");
    }

    /**
     * Envelope {@code {llm_output: {result, outputFiles?}}}. {@code outputFiles}
     * is copied from {@code result.output_files} / {@code result.outputFiles}
     * and omitted when empty. Files publish ONCE: the {@code result} copy drops both file keys so
     * the same file never renders twice (inside {@code result} and on the file field). Never logs
     * file bytes.
     */
    static Map<String, Object> ports(Object result) {
        Map<String, Object> inner = new LinkedHashMap<>();
        List<Object> files = outputFilesOf(result);
        if (!files.isEmpty()) {
            Map<String, Object> stripped = new LinkedHashMap<>(Json.asMap(result));
            stripped.remove("output_files");
            stripped.remove("outputFiles");
            inner.put("result", stripped);
            inner.put("outputFiles", files);
        } else {
            inner.put("result", result);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("llm_output", inner);
        return out;
    }

    /** First non-empty {@code outputFiles} / {@code output_files} list on {@code obj}. */
    static List<Object> outputFilesOf(Object obj) {
        Map<String, Object> map = Json.asMap(obj);
        Object files = map.get("outputFiles");
        if (files == null) {
            files = map.get("output_files");
        }
        if (files instanceof List<?> list && !list.isEmpty()) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    /**
     * Copy files from the raw provider envelope onto {@code dest} when dest
     * does not already carry a non-empty file list.
     */
    static void mergeOutputFiles(Map<String, Object> dest, Object raw) {
        if (dest == null || !outputFilesOf(dest).isEmpty()) {
            return;
        }
        List<Object> files = outputFilesOf(raw);
        if (files.isEmpty()) {
            files = outputFilesOf(extractMessage(raw));
        }
        if (!files.isEmpty()) {
            dest.put("output_files", files);
        }
    }
}
