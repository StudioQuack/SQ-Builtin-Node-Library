package com.studioquack.nodelibrary.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * v2 LLM request → native provider body, keyed by the operation's {@code stream.format}.
 *
 * <p>The v2 contract arriving on {@code llm_input} is OpenAI-shaped
 * ({@code messages}, {@code tools:[{name, description, request}]}, sampling knobs).
 * Non-OpenAI providers reject that envelope, so every operation that declares a
 * stream block runs this mapper on the request body for <em>both</em> the
 * streaming POST and the single-shot POST. Response handling is unchanged.
 *
 * <p>Pure: {@code Map} in → new {@code Map} out. Never logs the body (prompts,
 * file bytes and tool args may be PHI). Idempotent per format: {@code map(map(x))}
 * equals {@code map(x)}, and an already-native body is passed through.
 */
public final class ProviderRequestMapper {

    /** Anthropic Messages requires {@code max_tokens}; 8192 is the documented safe default. */
    static final int ANTHROPIC_DEFAULT_MAX_TOKENS = 8192;

    private ProviderRequestMapper() {
        // Utility — never instantiated.
    }

    /**
     * Translate {@code body} into the native request shape for {@code format}.
     * Unknown / blank format is identity.
     */
    public static Map<String, Object> map(String format, Map<String, Object> body) {
        return map(format, body, body);
    }

    /**
     * As {@link #map(String, Map)} with a read-only context consulted for gating signals
     * that are not part of the wire body — today only {@code model}, which Gemini puts
     * in the URL path rather than the body. Nothing is copied out of the context.
     */
    public static Map<String, Object> map(String format, Map<String, Object> body,
                                          Map<String, Object> context) {
        Map<String, Object> in = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        Map<String, Object> ctx = context == null ? in : context;
        // Port-level inputFiles fold onto the last user message before format mapping so
        // gemini/anthropic/ollama/cohere/openai all reuse their message-level files path.
        foldInputFiles(in, ctx);
        String f = format == null ? "" : format.toLowerCase(Locale.ROOT).trim();
        return switch (f) {
            case "gemini-sse" -> gemini(in, ctx);
            case "anthropic-sse" -> anthropic(in, ctx);
            case "ollama-ndjson" -> ollama(in);
            case "cohere-sse" -> cohere(in);
            case "responses-sse" -> responses(in);
            case "openai-sse" -> openaiCompatible(in, ctx);
            default -> in;
        };
    }

    private static String modelOf(Map<String, Object> body, Map<String, Object> ctx) {
        String m = ProviderStream.trim(body == null ? null : body.get("model")).toLowerCase(Locale.ROOT);
        if (m.isEmpty() && ctx != null && ctx != body) {
            m = ProviderStream.trim(ctx.get("model")).toLowerCase(Locale.ROOT);
        }
        return m;
    }

    private static long longOf(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null) {
            try {
                return Long.parseLong(ProviderStream.trim(v));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return -1;
    }

    // ---- gemini-sse --------------------------------------------------------

    /**
     * {@code messages} → {@code contents[{role:user|model, parts}]};
     * system → {@code systemInstruction}; sampling → {@code generationConfig};
     * tools → {@code [{functionDeclarations}]}. If {@code contents} is already
     * present the body is returned untouched (already native).
     *
     * <p>Thinking models (gemini-2.5 / gemini-3+) get {@code thinkingConfig
     * {includeThoughts:true, thinkingBudget:-1}} when the caller set no thinkingConfig —
     * without it the API thinks silently and the stream carries no {@code thought:true}
     * parts for the collector to fan out.
     */
    private static Map<String, Object> gemini(Map<String, Object> body, Map<String, Object> ctx) {
        if (hasNonEmptyList(body.get("contents"))) {
            return body;
        }
        Map<String, Object> out = new LinkedHashMap<>(body);
        List<Object> messages = ProviderStream.asList(out.remove("messages"));
        if (!messages.isEmpty()) {
            List<String> system = new ArrayList<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            for (Object raw : messages) {
                Map<String, Object> msg = ProviderStream.asMap(raw);
                if (msg.isEmpty()) {
                    continue;
                }
                String role = roleOf(msg);
                if ("system".equals(role) || "developer".equals(role)) {
                    String text = textOf(msg.get("content"));
                    if (!text.isEmpty()) {
                        system.add(text);
                    }
                    continue;
                }
                List<Map<String, Object>> parts = geminiParts(msg);
                if (parts.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", "assistant".equals(role) || "model".equals(role) ? "model" : "user");
                row.put("parts", parts);
                contents.add(row);
            }
            out.put("contents", contents);
            if (!system.isEmpty() && isBlank(out.get("systemInstruction"))
                    && isBlank(out.get("system_instruction"))) {
                out.put("systemInstruction", Map.of("parts", List.of(Map.of("text", join(system)))));
            }
        }
        mergeGeminiConfig(out);
        if (!geminiToolsNative(out.get("tools"))) {
            List<Map<String, Object>> decls = geminiDeclarations(out.get("tools"));
            if (!decls.isEmpty()) {
                out.put("tools", List.of(Map.of("functionDeclarations", decls)));
            }
        }
        maybeEnableGeminiThinking(out, modelOf(out, ctx));
        return out;
    }

    private static void maybeEnableGeminiThinking(Map<String, Object> out, String model) {
        if (!geminiThinkingCapable(model)) {
            return;
        }
        Map<String, Object> cfg = new LinkedHashMap<>(ProviderStream.asMap(out.get("generationConfig")));
        if (!ProviderStream.asMap(cfg.get("thinkingConfig")).isEmpty()
                || !ProviderStream.asMap(cfg.get("thinking_config")).isEmpty()) {
            return; // caller set an explicit thinking policy
        }
        // includeThoughts surfaces thought summaries as thought:true parts (the collector
        // maps them to the thinking channel); -1 = dynamic budget (provider default).
        cfg.put("thinkingConfig", Map.of("includeThoughts", true, "thinkingBudget", -1));
        out.put("generationConfig", cfg);
    }

    static boolean geminiThinkingCapable(String model) {
        return model.contains("gemini-2.5") || model.matches(".*gemini-[3-9].*");
    }

    private static List<Map<String, Object>> geminiParts(Map<String, Object> msg) {
        List<Map<String, Object>> parts = new ArrayList<>();
        if ("tool".equals(roleOf(msg))) {
            Map<String, Object> fr = new LinkedHashMap<>();
            String name = firstStr(msg, "name", "tool_name");
            fr.put("name", name.isEmpty() ? "tool" : name);
            Object payload = msg.get("content");
            Object parsed = payload instanceof String s ? ProviderStream.parseJsonArgs(s) : payload;
            fr.put("response", parsed instanceof Map<?, ?> ? parsed : Map.of("result", parsed == null ? "" : parsed));
            parts.add(Map.of("functionResponse", fr));
            return parts;
        }
        addGeminiContent(parts, msg.get("content"));
        for (Object f : ProviderStream.asList(msg.get("files"))) {
            Map<String, Object> part = geminiMedia(ProviderStream.asMap(f));
            if (part != null) {
                parts.add(part);
            }
        }
        for (Object tc : ProviderStream.asList(msg.get("tool_calls"))) {
            Map<String, Object> call = ProviderStream.asMap(tc);
            Map<String, Object> fn = ProviderStream.asMap(call.get("function"));
            String name = firstStr(call, "name");
            if (name.isEmpty()) {
                name = ProviderStream.trim(fn.get("name"));
            }
            Object args = call.get("args") != null ? call.get("args")
                    : (fn.get("arguments") != null ? fn.get("arguments") : call.get("arguments"));
            if (args instanceof String s) {
                args = ProviderStream.parseJsonArgs(s);
            }
            Map<String, Object> fc = new LinkedHashMap<>();
            fc.put("name", name.isEmpty() ? "unknown_tool" : name);
            fc.put("args", args instanceof Map<?, ?> || args instanceof List<?> ? args : Map.of());
            parts.add(Map.of("functionCall", fc));
        }
        return parts;
    }

    private static void addGeminiContent(List<Map<String, Object>> parts, Object content) {
        if (content == null) {
            return;
        }
        if (content instanceof String s) {
            if (!s.isEmpty()) {
                parts.add(Map.of("text", s));
            }
            return;
        }
        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    if (!s.isEmpty()) {
                        parts.add(Map.of("text", s));
                    }
                    continue;
                }
                Map<String, Object> block = ProviderStream.asMap(item);
                Map<String, Object> part = geminiBlock(block);
                if (part != null) {
                    parts.add(part);
                }
            }
            return;
        }
        parts.add(Map.of("text", String.valueOf(content)));
    }

    private static Map<String, Object> geminiBlock(Map<String, Object> block) {
        if (block.isEmpty()) {
            return null;
        }
        if (isNativeGeminiPart(block)) {
            return new LinkedHashMap<>(block);
        }
        String type = typeOf(block);
        if ("text".equals(type) || type.isEmpty() && block.get("text") != null) {
            String text = ProviderStream.str(block.get("text"));
            return text.isEmpty() ? null : Map.of("text", text);
        }
        if ("image".equals(type) || "image_url".equals(type) || "audio".equals(type)
                || "document".equals(type) || "file".equals(type) || "video".equals(type)) {
            return geminiMedia(block);
        }
        Map<String, Object> media = geminiMedia(block);
        return media;
    }

    /**
     * REST Blob fields use proto JSON names ({@code inline_data}/{@code mime_type})
     * as in the current generateContent curl examples.
     */
    private static Map<String, Object> geminiMedia(Map<String, Object> block) {
        Media media = Media.from(block);
        if (media == null) {
            Map<String, Object> notice = noPayloadNotice(block);
            // Gemini parts have no `type` field — the notice rides as a bare text part.
            return notice == null ? null : Map.of("text", ProviderStream.str(notice.get("text")));
        }
        // files-list items carry `kind` (not `type`) — honor both.
        String kind = typeOf(block);
        if (kind.isEmpty()) {
            kind = ProviderStream.trim(block.get("kind")).toLowerCase(Locale.ROOT);
        }
        String mime = media.mime == null ? "" : media.mime.split(";", 2)[0].toLowerCase(Locale.ROOT).trim();
        // Gemini inline_data accepts images/audio/video and PDF-or-text documents only —
        // an xlsx/ods binary would 400 at the API, so deliver extracted text instead.
        if (("document".equals(kind) || "file".equals(kind)) && !media.url && !mime.isEmpty()
                && !"application/pdf".equals(mime)
                && !OpenAiContentParts.isTextLike(mime, ProviderStream.trim(block.get("name")))) {
            String name = ProviderStream.trim(block.get("name"));
            String extracted = OpenAiContentParts.extractDocumentText(mime, name, media.data);
            if (extracted != null) {
                return Map.of("text", "[File '" + (name.isEmpty() ? "document" : name) + "' (" + mime
                        + "), extracted text]\n" + extracted);
            }
            Object notice = OpenAiContentParts.droppedNotice(block, "document",
                    "Gemini inline data accepts only PDF or text documents; "
                            + "run the file through the Docling node and wire its text output").get("text");
            return Map.of("text", ProviderStream.str(notice));
        }
        Map<String, Object> blob = new LinkedHashMap<>();
        blob.put("mime_type", media.mime);
        if (media.url) {
            blob.put("file_uri", media.data);
            return Map.of("file_data", blob);
        }
        blob.put("data", media.data);
        return Map.of("inline_data", blob);
    }

    /**
     * A block that was clearly MEANT to carry a file (it has a name, a MIME, or a store handle)
     * but arrived with no payload must not vanish silently — an unhydrated attachment otherwise
     * looks exactly like "the model ignored my file". Genuine non-media blocks (no attachment
     * identity at all) still return null and drop as before.
     */
    private static Map<String, Object> noPayloadNotice(Map<String, Object> block) {
        boolean looksLikeAttachment = !ProviderStream.trim(block.get("name")).isEmpty()
                || !ProviderStream.trim(block.get("mime_type")).isEmpty()
                || !ProviderStream.trim(block.get("mimeType")).isEmpty()
                || block.get("fileId") != null;
        if (!looksLikeAttachment) {
            return null;
        }
        String kind = typeOf(block);
        return OpenAiContentParts.droppedNotice(block, kind.isEmpty() ? "attachment" : kind,
                "no payload reached the node");
    }

    private static boolean isNativeGeminiPart(Map<String, Object> block) {
        return block.containsKey("inline_data") || block.containsKey("inlineData")
                || block.containsKey("file_data") || block.containsKey("fileData")
                || block.containsKey("functionCall") || block.containsKey("function_call")
                || block.containsKey("functionResponse") || block.containsKey("function_response");
    }

    private static void mergeGeminiConfig(Map<String, Object> out) {
        Map<String, Object> cfg = new LinkedHashMap<>(ProviderStream.asMap(
                out.get("generationConfig") != null ? out.get("generationConfig") : out.get("generation_config")));
        putIfAbsent(cfg, "temperature", first(out, "temperature"));
        putIfAbsent(cfg, "topP", first(out, "top_p", "topP"));
        putIfAbsent(cfg, "maxOutputTokens", first(out, "max_tokens", "max_completion_tokens", "maxOutputTokens"));
        Object stop = first(out, "stop", "stop_sequences", "stopSequences");
        if (cfg.get("stopSequences") == null && stop != null) {
            cfg.put("stopSequences", stringList(stop));
        }
        if (!cfg.isEmpty()) {
            out.put("generationConfig", cfg);
        }
        out.remove("generation_config");
        for (String k : List.of("temperature", "top_p", "topP", "top_k", "topK",
                "max_tokens", "max_completion_tokens", "maxOutputTokens",
                "stop", "stop_sequences", "stopSequences")) {
            out.remove(k);
        }
    }

    private static boolean geminiToolsNative(Object tools) {
        List<Object> list = ProviderStream.asList(tools);
        if (list.isEmpty()) {
            return true;
        }
        Map<String, Object> first = ProviderStream.asMap(list.get(0));
        return first.containsKey("functionDeclarations") || first.containsKey("function_declarations");
    }

    private static List<Map<String, Object>> geminiDeclarations(Object tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object raw : ProviderStream.asList(tools)) {
            Map<String, Object> tool = unwrapFunction(ProviderStream.asMap(raw));
            if (tool.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            String name = firstStr(tool, "name");
            row.put("name", name.isEmpty() ? "tool" : name);
            String desc = firstStr(tool, "description");
            if (!desc.isEmpty()) {
                row.put("description", desc);
            }
            row.put("parameters", schemaOf(tool));
            out.add(row);
        }
        return out;
    }

    // ---- anthropic-sse -----------------------------------------------------

    /**
     * System messages → top-level {@code system}; tools → {@code input_schema};
     * {@code max_tokens} defaults to {@value #ANTHROPIC_DEFAULT_MAX_TOKENS} when absent.
     * {@code thinking} is left untouched when caller-supplied; otherwise thinking-capable
     * models (claude-3-7, claude-*-4+) get it auto-enabled — without {@code thinking
     * {type:enabled}} the API never emits {@code thinking_delta} events and the thinking
     * channel stays silent. Sampling knobs already named
     * {@code temperature}/{@code top_p}/{@code top_k}/{@code stop_sequences} pass through;
     * OpenAI {@code stop} is aliased to {@code stop_sequences} when the latter is absent.
     */
    private static Map<String, Object> anthropic(Map<String, Object> body, Map<String, Object> ctx) {
        Map<String, Object> out = new LinkedHashMap<>(body);
        List<Object> messages = ProviderStream.asList(out.get("messages"));
        if (!messages.isEmpty()) {
            List<String> system = new ArrayList<>();
            absorbSystem(out.get("system"), system);
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Object raw : messages) {
                Map<String, Object> msg = ProviderStream.asMap(raw);
                if (msg.isEmpty()) {
                    continue;
                }
                String role = roleOf(msg);
                if ("system".equals(role) || "developer".equals(role)) {
                    String text = textOf(msg.get("content"));
                    if (!text.isEmpty()) {
                        system.add(text);
                    }
                    continue;
                }
                kept.add(anthropicMessage(msg, role));
            }
            out.put("messages", kept);
            if (!system.isEmpty()) {
                out.put("system", join(system));
            }
        }
        if (!anthropicToolsNative(out.get("tools"))) {
            List<Map<String, Object>> mapped = anthropicTools(out.get("tools"));
            if (!mapped.isEmpty()) {
                out.put("tools", mapped);
            }
        }
        if (first(out, "max_tokens", "max_completion_tokens") == null) {
            out.put("max_tokens", ANTHROPIC_DEFAULT_MAX_TOKENS);
        } else if (out.get("max_tokens") == null && out.get("max_completion_tokens") != null) {
            out.put("max_tokens", out.remove("max_completion_tokens"));
        }
        Object stop = out.get("stop");
        if (stop != null && out.get("stop_sequences") == null) {
            out.put("stop_sequences", stringList(stop));
            out.remove("stop");
        }
        maybeEnableAnthropicThinking(out, modelOf(out, ctx));
        return out;
    }

    /**
     * Auto-enable extended thinking for thinking-capable Claude models. Budget: half of
     * max_tokens clamped to [1024, 16384] and always strictly below max_tokens (API
     * requirement, along with budget &ge; 1024). Skipped when max_tokens leaves no room.
     */
    private static void maybeEnableAnthropicThinking(Map<String, Object> out, String model) {
        if (out.get("thinking") != null || !anthropicThinkingCapable(model)) {
            return;
        }
        long max = longOf(out.get("max_tokens"));
        if (max <= 1024) {
            return;
        }
        long budget = Math.min(max - 1, Math.max(1024, Math.min(max / 2, 16384)));
        Map<String, Object> thinking = new LinkedHashMap<>();
        thinking.put("type", "enabled");
        thinking.put("budget_tokens", budget);
        out.put("thinking", thinking);
        // Extended thinking rejects temperature/top_p/top_k overrides (HTTP 400) — drop
        // them only when WE enabled thinking; a caller-supplied thinking block stays as-is.
        out.remove("temperature");
        out.remove("top_p");
        out.remove("top_k");
    }

    static boolean anthropicThinkingCapable(String model) {
        // claude-3-7-sonnet, claude-{sonnet|opus|haiku}-4*, future claude-4+/5+ shorthands.
        return model.startsWith("claude-3-7") || model.matches("^claude-(?:[a-z]+-[4-9]|[4-9]).*");
    }

    private static Map<String, Object> anthropicMessage(Map<String, Object> msg, String role) {
        Map<String, Object> row = new LinkedHashMap<>();
        if ("tool".equals(role)) {
            row.put("role", "user");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "tool_result");
            String id = firstStr(msg, "tool_call_id", "id");
            result.put("tool_use_id", id);
            result.put("content", textOf(msg.get("content")));
            row.put("content", List.of(result));
            return row;
        }
        row.put("role", "assistant".equals(role) || "model".equals(role) ? "assistant" : "user");
        List<Map<String, Object>> blocks = new ArrayList<>();
        addAnthropicContent(blocks, msg.get("content"));
        for (Object f : ProviderStream.asList(msg.get("files"))) {
            // File-list items carry `kind`, not `type` — normalize first or a
            // document would be emitted as an image block with a PDF MIME.
            Map<String, Object> b = anthropicBlock(OpenAiContentParts.fileAsBlock(ProviderStream.asMap(f)));
            if (b != null) {
                blocks.add(b);
            }
        }
        for (Object tc : ProviderStream.asList(msg.get("tool_calls"))) {
            Map<String, Object> call = ProviderStream.asMap(tc);
            Map<String, Object> fn = ProviderStream.asMap(call.get("function"));
            Map<String, Object> use = new LinkedHashMap<>();
            use.put("type", "tool_use");
            String id = firstStr(call, "id");
            use.put("id", id.isEmpty() ? "call_" + ProviderStream.uuid24() : id);
            String name = firstStr(call, "name");
            if (name.isEmpty()) {
                name = ProviderStream.trim(fn.get("name"));
            }
            use.put("name", name.isEmpty() ? "unknown_tool" : name);
            Object args = call.get("args") != null ? call.get("args")
                    : (call.get("input") != null ? call.get("input")
                    : (fn.get("arguments") != null ? fn.get("arguments") : call.get("arguments")));
            if (args instanceof String s) {
                args = ProviderStream.parseJsonArgs(s);
            }
            use.put("input", args instanceof Map<?, ?> ? args : Map.of());
            blocks.add(use);
        }
        if (blocks.isEmpty()) {
            row.put("content", "");
        } else if (blocks.size() == 1 && "text".equals(blocks.get(0).get("type"))
                && ProviderStream.asList(msg.get("tool_calls")).isEmpty()
                && ProviderStream.asList(msg.get("files")).isEmpty()) {
            row.put("content", blocks.get(0).get("text"));
        } else {
            row.put("content", blocks);
        }
        return row;
    }

    private static void addAnthropicContent(List<Map<String, Object>> blocks, Object content) {
        if (content == null) {
            return;
        }
        if (content instanceof String s) {
            if (!s.isEmpty()) {
                blocks.add(Map.of("type", "text", "text", s));
            }
            return;
        }
        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    if (!s.isEmpty()) {
                        blocks.add(Map.of("type", "text", "text", s));
                    }
                    continue;
                }
                Map<String, Object> block = ProviderStream.asMap(item);
                Map<String, Object> converted = anthropicBlock(block);
                if (converted != null) {
                    blocks.add(converted);
                }
            }
            return;
        }
        blocks.add(Map.of("type", "text", "text", String.valueOf(content)));
    }

    private static Map<String, Object> anthropicBlock(Map<String, Object> block) {
        if (block.isEmpty()) {
            return null;
        }
        String type = typeOf(block);
        if ("text".equals(type) || type.isEmpty() && block.get("text") != null) {
            String text = ProviderStream.str(block.get("text"));
            return text.isEmpty() ? null : Map.of("type", "text", "text", text);
        }
        if ("tool_use".equals(type) || "tool_result".equals(type) || "thinking".equals(type)
                || "redacted_thinking".equals(type)) {
            return new LinkedHashMap<>(block);
        }
        if ("image".equals(type) || "image_url".equals(type)) {
            // Native Anthropic media already has source as an object; v2 uses source as "url"|"base64".
            if (block.get("source") instanceof Map<?, ?>) {
                return new LinkedHashMap<>(block);
            }
            return anthropicImage(block);
        }
        if ("document".equals(type) || "file".equals(type)) {
            if (block.get("source") instanceof Map<?, ?>) {
                return new LinkedHashMap<>(block);
            }
            return anthropicDocument(block);
        }
        if ("audio".equals(type) || "video".equals(type)) {
            // Anthropic Messages has no audio/video block — an image block with an
            // audio MIME is a provider 400. Say what was dropped instead.
            String name = ProviderStream.trim(block.get("name"));
            return Map.of("type", "text", "text",
                    "[Attachment '" + (name.isEmpty() ? type : name) + "' was not sent to the model: "
                            + "Anthropic Messages accepts only text, image and document blocks.]");
        }
        return anthropicMedia(block, type.isEmpty() ? "image" : type);
    }

    /** Anthropic image sources accept only jpeg/png/gif/webp — anything else 400s. */
    private static final Set<String> ANTHROPIC_IMAGE_MIMES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    /**
     * v2 image block → Anthropic {@code image} block. URL sources pass through (no
     * media_type needed); base64 is gated to the four supported MIMEs — an unsupported
     * raster MIME becomes an explicit notice, a text-like one (e.g. SVG) is decoded and
     * inlined as text.
     */
    private static Map<String, Object> anthropicImage(Map<String, Object> block) {
        String mime = ProviderStream.trim(block.get("mime_type"))
                .split(";", 2)[0].toLowerCase(Locale.ROOT).trim();
        boolean isUrl = "url".equals(ProviderStream.trim(block.get("source")))
                || OpenAiContentParts.looksLikeUrl(OpenAiContentParts.mediaPayload(block));
        if (isUrl || mime.isEmpty() || ANTHROPIC_IMAGE_MIMES.contains(mime)) {
            return anthropicMedia(block, "image");
        }
        String name = ProviderStream.trim(block.get("name"));
        if (OpenAiContentParts.isTextLike(mime, name)) {
            String text = OpenAiContentParts.decodeText(OpenAiContentParts.mediaPayload(block));
            if (text != null) {
                return Map.of("type", "text", "text",
                        "[File '" + (name.isEmpty() ? "image" : name) + "' (" + mime
                                + "), inlined as text]\n" + text);
            }
        }
        return OpenAiContentParts.droppedNotice(block, "image",
                "Anthropic image blocks accept only jpeg/png/gif/webp");
    }

    /**
     * v2 document block → Anthropic {@code document} block. Base64 sources accept
     * {@code application/pdf} only; text-like payloads use the {@code source.type:"text"}
     * form whose {@code data} is PLAIN UTF-8 (not base64) with the filename as
     * {@code title}; URL sources are PDFs hosted online. Anything else (docx, xlsx, …)
     * becomes an explicit notice pointing at Docling.
     */
    private static Map<String, Object> anthropicDocument(Map<String, Object> block) {
        String mime = ProviderStream.trim(block.get("mime_type"))
                .split(";", 2)[0].toLowerCase(Locale.ROOT).trim();
        String name = ProviderStream.trim(block.get("name"));
        boolean isUrl = "url".equals(ProviderStream.trim(block.get("source")))
                || OpenAiContentParts.looksLikeUrl(OpenAiContentParts.mediaPayload(block));
        if (isUrl) {
            if (mime.isEmpty() || "application/pdf".equals(mime)) {
                return anthropicMedia(block, "document");
            }
            return OpenAiContentParts.droppedNotice(block, "document",
                    "Anthropic document URLs must be PDF");
        }
        if (mime.isEmpty() || "application/pdf".equals(mime)) {
            return anthropicMedia(block, "document");
        }
        if (OpenAiContentParts.isTextLike(mime, name)) {
            String text = OpenAiContentParts.decodeText(OpenAiContentParts.mediaPayload(block));
            if (text != null) {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("type", "text");
                source.put("media_type", "text/plain");
                source.put("data", text);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("type", "document");
                out.put("source", source);
                if (!name.isEmpty()) {
                    out.put("title", name);
                }
                return out;
            }
            return OpenAiContentParts.droppedNotice(block, "document", "payload is not valid base64");
        }
        // Binary the Messages API refuses (xlsx/ods/…): deliver extracted text in the
        // source.type:"text" document form instead of dropping the attachment.
        String extracted = OpenAiContentParts.extractDocumentText(
                mime, name, OpenAiContentParts.mediaPayload(block));
        if (extracted != null) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "text");
            source.put("media_type", "text/plain");
            source.put("data", extracted);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("type", "document");
            out.put("source", source);
            out.put("title", name.isEmpty() ? "document" : name + " (extracted text)");
            return out;
        }
        return OpenAiContentParts.droppedNotice(block, "document",
                "Anthropic document blocks accept application/pdf or plain text; "
                        + "run the file through the Docling node and wire its text output");
    }

    private static Map<String, Object> anthropicMedia(Map<String, Object> block, String kind) {
        Media media = Media.from(block);
        if (media == null) {
            return noPayloadNotice(block);
        }
        String type = "document".equals(kind) || "file".equals(kind) ? "document" : "image";
        Map<String, Object> source = new LinkedHashMap<>();
        if (media.url) {
            source.put("type", "url");
            source.put("url", media.data);
        } else {
            source.put("type", "base64");
            source.put("media_type", media.mime);
            source.put("data", media.data);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        out.put("source", source);
        return out;
    }

    private static boolean anthropicToolsNative(Object tools) {
        List<Object> list = ProviderStream.asList(tools);
        if (list.isEmpty()) {
            return true;
        }
        Map<String, Object> first = unwrapFunction(ProviderStream.asMap(list.get(0)));
        return first.containsKey("input_schema") && !first.containsKey("request");
    }

    private static List<Map<String, Object>> anthropicTools(Object tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object raw : ProviderStream.asList(tools)) {
            Map<String, Object> tool = unwrapFunction(ProviderStream.asMap(raw));
            if (tool.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            String name = firstStr(tool, "name");
            row.put("name", name.isEmpty() ? "tool" : name);
            String desc = firstStr(tool, "description");
            if (!desc.isEmpty()) {
                row.put("description", desc);
            }
            Object schema = tool.get("input_schema") != null ? tool.get("input_schema") : schemaOf(tool);
            row.put("input_schema", schema);
            out.add(row);
        }
        return out;
    }

    // ---- ollama-ndjson -----------------------------------------------------

    /**
     * Messages / {@code think} pass through. OpenAI-style sampling knobs move into
     * {@code options} ({@code temperature}, {@code top_p}, {@code seed}, {@code stop},
     * {@code num_predict} and aliases) per the current Chat API.
     *
     * <p>The Chat API wants {@code content} as a plain string and images as a
     * message-level {@code images:[base64]} array, so v2 media blocks are flattened:
     * text parts concatenate, image blocks land in {@code images} (data-URL prefix
     * stripped), text-like documents decode inline, and binary attachments become an
     * explicit notice line.
     */
    private static Map<String, Object> ollama(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);
        List<Object> messages = ProviderStream.asList(out.get("messages"));
        if (!messages.isEmpty()) {
            List<Map<String, Object>> flattened = new ArrayList<>();
            for (Object raw : messages) {
                flattened.add(ollamaMessage(ProviderStream.asMap(raw)));
            }
            out.put("messages", flattened);
        }
        Map<String, Object> options = new LinkedHashMap<>(ProviderStream.asMap(out.get("options")));
        liftOption(out, options, "temperature", "temperature");
        liftOption(out, options, "top_p", "top_p");
        liftOption(out, options, "top_k", "top_k");
        liftOption(out, options, "min_p", "min_p");
        liftOption(out, options, "seed", "seed");
        liftOption(out, options, "stop", "stop");
        liftOption(out, options, "num_predict", "num_predict");
        liftOption(out, options, "frequency_penalty", "frequency_penalty");
        liftOption(out, options, "presence_penalty", "presence_penalty");
        liftOption(out, options, "repeat_penalty", "repeat_penalty");
        liftOption(out, options, "num_ctx", "num_ctx");
        if (options.get("num_predict") == null) {
            Object max = first(out, "max_tokens", "max_completion_tokens");
            if (max != null) {
                options.put("num_predict", max);
            }
        }
        out.remove("max_tokens");
        out.remove("max_completion_tokens");
        if (!options.isEmpty()) {
            out.put("options", options);
        }
        return out;
    }

    /** One v2 message → Ollama {role, content:string, images?:[base64]}; {@code files} fold in too. */
    private static Map<String, Object> ollamaMessage(Map<String, Object> msg) {
        if (msg.isEmpty()) {
            return msg;
        }
        Object content = msg.get("content");
        List<Object> files = ProviderStream.asList(msg.get("files"));
        if (!OpenAiContentParts.hasMediaBlock(content) && files.isEmpty()) {
            return msg;
        }
        StringBuilder text = new StringBuilder();
        List<String> images = new ArrayList<>();
        java.util.function.Consumer<Map<String, Object>> foldBlock = block -> {
            if (block.isEmpty()) {
                return;
            }
            String type = ProviderStream.trim(block.get("type")).toLowerCase(Locale.ROOT);
            if (type.isEmpty() || "text".equals(type)) {
                String t = ProviderStream.str(block.get("text"));
                if (!t.isEmpty()) {
                    text.append(t);
                }
                return;
            }
            if ("image".equals(type)) {
                String payload = OpenAiContentParts.mediaPayload(block);
                if (!payload.isEmpty()) {
                    images.add(OpenAiContentParts.stripDataUrl(payload));
                } else {
                    // Ollama's images array cannot carry a notice — fold it into the text so an
                    // unhydrated attachment is visible instead of silently absent.
                    Map<String, Object> notice = noPayloadNotice(block);
                    if (notice != null) {
                        if (!text.isEmpty()) {
                            text.append('\n');
                        }
                        text.append(ProviderStream.str(notice.get("text")));
                    }
                }
                return;
            }
            Map<String, Object> part = OpenAiContentParts.blockToOpenAi(block,
                    OpenAiContentParts.Profile.BASIC);
            if (part != null) {
                text.append(ProviderStream.str(part.get("text")));
            }
        };
        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    if (!s.isEmpty()) {
                        text.append(s);
                    }
                } else {
                    foldBlock.accept(ProviderStream.asMap(item));
                }
            }
        } else if (content != null) {
            text.append(ProviderStream.str(content));
        }
        for (Object f : files) {
            foldBlock.accept(OpenAiContentParts.fileAsBlock(ProviderStream.asMap(f)));
        }
        Map<String, Object> row = new LinkedHashMap<>(msg);
        row.put("content", text.toString());
        row.remove("files");
        if (!images.isEmpty()) {
            row.put("images", images);
        }
        return row;
    }

    private static void liftOption(Map<String, Object> body, Map<String, Object> options,
                                   String from, String to) {
        Object v = body.get(from);
        if (v != null && options.get(to) == null) {
            options.put(to, v);
        }
        if (!"options".equals(from)) {
            body.remove(from);
        }
    }

    // ---- cohere-sse --------------------------------------------------------

    /**
     * Cohere Chat v2 is messages-native. OpenAI-isms: {@code stop} → {@code stop_sequences},
     * {@code top_p} → {@code p}, {@code top_k} → {@code k}. v2 tools
     * ({@code name}/{@code request}) become {@code type:function} + {@code parameters}.
     *
     * <p>Chat v2 content is text-only, so v2 media blocks flatten: text-like documents
     * decode inline, everything else becomes an explicit notice line.
     */
    private static Map<String, Object> cohere(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);
        List<Object> messages = ProviderStream.asList(out.get("messages"));
        if (!messages.isEmpty()) {
            List<Map<String, Object>> flattened = new ArrayList<>();
            for (Object raw : messages) {
                Map<String, Object> msg = ProviderStream.asMap(raw);
                if (msg.isEmpty()) {
                    continue;
                }
                Object content = msg.get("content");
                List<Object> files = ProviderStream.asList(msg.get("files"));
                if (!OpenAiContentParts.hasMediaBlock(content) && files.isEmpty()) {
                    flattened.add(msg);
                    continue;
                }
                Object folded = OpenAiContentParts.contentToOpenAi(content, files,
                        OpenAiContentParts.Profile.BASIC);
                Map<String, Object> row = new LinkedHashMap<>(msg);
                row.remove("files");
                if (folded instanceof List<?> parts) {
                    StringBuilder sb = new StringBuilder();
                    for (Object p : parts) {
                        sb.append(ProviderStream.str(ProviderStream.asMap(p).get("text")));
                    }
                    row.put("content", sb.toString());
                } else {
                    row.put("content", folded);
                }
                flattened.add(row);
            }
            out.put("messages", flattened);
        }
        if (out.get("stop_sequences") == null && out.get("stop") != null) {
            out.put("stop_sequences", stringList(out.remove("stop")));
        } else {
            out.remove("stop");
        }
        if (out.get("p") == null && out.get("top_p") != null) {
            out.put("p", out.remove("top_p"));
        } else {
            out.remove("top_p");
        }
        if (out.get("k") == null && out.get("top_k") != null) {
            out.put("k", out.remove("top_k"));
        } else {
            out.remove("top_k");
        }
        if (!cohereToolsNative(out.get("tools"))) {
            List<Map<String, Object>> mapped = cohereTools(out.get("tools"));
            if (!mapped.isEmpty()) {
                out.put("tools", mapped);
            }
        }
        return out;
    }

    private static boolean cohereToolsNative(Object tools) {
        List<Object> list = ProviderStream.asList(tools);
        if (list.isEmpty()) {
            return true;
        }
        Map<String, Object> first = ProviderStream.asMap(list.get(0));
        return "function".equals(first.get("type")) && first.get("function") instanceof Map<?, ?>;
    }

    private static List<Map<String, Object>> cohereTools(Object tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object raw : ProviderStream.asList(tools)) {
            Map<String, Object> tool = unwrapFunction(ProviderStream.asMap(raw));
            if (tool.isEmpty()) {
                continue;
            }
            Map<String, Object> fn = new LinkedHashMap<>();
            String name = firstStr(tool, "name");
            fn.put("name", name.isEmpty() ? "tool" : name);
            String desc = firstStr(tool, "description");
            if (!desc.isEmpty()) {
                fn.put("description", desc);
            }
            fn.put("parameters", schemaOf(tool));
            out.add(Map.of("type", "function", "function", fn));
        }
        return out;
    }

    // ---- responses-sse -----------------------------------------------------

    /**
     * v2 {@code messages} → Responses {@code input} items
     * ({@code {type:message, role, content:[{type:input_text, text}]}});
     * system/developer → top-level {@code instructions} when that field is blank
     * (Responses create docs), otherwise kept as system-role input items.
     * v2 tools → {@code {type:function, name, description, parameters}};
     * {@code max_tokens} → {@code max_output_tokens}. {@code temperature}/{@code top_p}
     * pass through. Already-native {@code input} is left untouched.
     */
    private static Map<String, Object> responses(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);
        if (!hasInput(out.get("input"))) {
            List<Object> messages = ProviderStream.asList(out.remove("messages"));
            if (!messages.isEmpty()) {
                List<String> system = new ArrayList<>();
                List<Map<String, Object>> input = new ArrayList<>();
                for (Object raw : messages) {
                    Map<String, Object> msg = ProviderStream.asMap(raw);
                    if (msg.isEmpty()) {
                        continue;
                    }
                    String role = roleOf(msg);
                    if ("system".equals(role) || "developer".equals(role)) {
                        String text = textOf(msg.get("content"));
                        if (!text.isEmpty()) {
                            system.add(text);
                        }
                        if (!isBlank(out.get("instructions"))) {
                            Map<String, Object> row = responsesMessage(msg, role);
                            if (row != null) {
                                input.add(row);
                            }
                        }
                        continue;
                    }
                    if ("tool".equals(role)) {
                        input.add(responsesToolResult(msg));
                        continue;
                    }
                    Map<String, Object> row = responsesMessage(msg, role);
                    if (row != null) {
                        input.add(row);
                    }
                    for (Map<String, Object> call : responsesFunctionCalls(msg)) {
                        input.add(call);
                    }
                }
                if (!input.isEmpty()) {
                    out.put("input", input);
                }
                if (!system.isEmpty() && isBlank(out.get("instructions"))) {
                    out.put("instructions", join(system));
                }
            }
        }
        if (out.get("max_output_tokens") == null) {
            Object max = first(out, "max_tokens", "max_completion_tokens");
            if (max != null) {
                out.put("max_output_tokens", max);
            }
        }
        out.remove("max_tokens");
        out.remove("max_completion_tokens");
        if (!responsesToolsNative(out.get("tools"))) {
            List<Map<String, Object>> mapped = responsesTools(out.get("tools"));
            if (!mapped.isEmpty()) {
                out.put("tools", mapped);
            }
        }
        return out;
    }

    private static boolean hasInput(Object v) {
        if (v instanceof String s) {
            return !s.isBlank();
        }
        if (v instanceof List<?> list) {
            return !list.isEmpty();
        }
        return v instanceof Map<?, ?> m && !m.isEmpty();
    }

    private static Map<String, Object> responsesMessage(Map<String, Object> msg, String role) {
        List<Map<String, Object>> parts = responsesContentParts(msg.get("content"));
        for (Object f : ProviderStream.asList(msg.get("files"))) {
            Map<String, Object> part = responsesMediaPart(
                    OpenAiContentParts.fileAsBlock(ProviderStream.asMap(f)));
            if (part != null) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "message");
        row.put("role", "model".equals(role) ? "assistant" : (role.isEmpty() ? "user" : role));
        row.put("content", parts);
        return row;
    }

    private static List<Map<String, Object>> responsesContentParts(Object content) {
        List<Map<String, Object>> parts = new ArrayList<>();
        if (content == null) {
            return parts;
        }
        if (content instanceof String s) {
            if (!s.isEmpty()) {
                parts.add(inputText(s));
            }
            return parts;
        }
        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    if (!s.isEmpty()) {
                        parts.add(inputText(s));
                    }
                    continue;
                }
                Map<String, Object> block = ProviderStream.asMap(item);
                if (block.isEmpty()) {
                    continue;
                }
                String type = typeOf(block);
                if ("input_text".equals(type) || "input_image".equals(type) || "input_file".equals(type)) {
                    parts.add(new LinkedHashMap<>(block));
                    continue;
                }
                Map<String, Object> media = responsesMediaPart(block);
                if (media != null) {
                    parts.add(media);
                    continue;
                }
                String text = ProviderStream.str(block.get("text"));
                if (text.isEmpty()) {
                    text = ProviderStream.str(block.get("thinking"));
                }
                if (!text.isEmpty()) {
                    parts.add(inputText(text));
                }
            }
            return parts;
        }
        parts.add(inputText(String.valueOf(content)));
        return parts;
    }

    /**
     * v2 media block → Responses input part: {@code image} → {@code input_image}
     * (data URL or URL), PDF {@code document} → {@code input_file} with base64
     * {@code file_data}, text-like documents decode to {@code input_text}, and
     * unsupported payloads (audio, video, other binaries) become an explicit notice.
     * {@code null} when the block is not media (plain text blocks stay with the caller).
     */
    private static Map<String, Object> responsesMediaPart(Map<String, Object> block) {
        String type = typeOf(block);
        if (!List.of("image", "audio", "video", "document", "file").contains(type)) {
            return null;
        }
        if ("image".equals(type)) {
            String data = OpenAiContentParts.mediaPayload(block);
            if (data.isEmpty()) {
                return null;
            }
            String source = ProviderStream.trim(block.get("source"));
            if (!"url".equals(source) && !data.startsWith("data:") && !data.startsWith("http")) {
                String mime = ProviderStream.trim(block.get("mime_type"));
                data = "data:" + (mime.isEmpty() ? "image/png" : mime) + ";base64," + data;
            }
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("type", "input_image");
            part.put("image_url", data);
            return part;
        }
        String mime = ProviderStream.trim(block.get("mime_type"));
        String name = ProviderStream.trim(block.get("name"));
        if (("document".equals(type) || "file".equals(type))
                && "application/pdf".equalsIgnoreCase(mime.split(";", 2)[0].trim())) {
            String data = OpenAiContentParts.mediaPayload(block);
            if (data.isEmpty()) {
                return null;
            }
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("filename", name.isEmpty() ? "document.pdf" : name);
            file.put("file_data", data.startsWith("data:") ? data
                    : "data:application/pdf;base64," + OpenAiContentParts.stripDataUrl(data));
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("type", "input_file");
            part.putAll(file);
            return part;
        }
        Map<String, Object> asText = OpenAiContentParts.blockToOpenAi(block,
                OpenAiContentParts.Profile.BASIC);
        return asText == null ? null : inputText(ProviderStream.str(asText.get("text")));
    }

    private static Map<String, Object> inputText(String text) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "input_text");
        part.put("text", text);
        return part;
    }

    private static Map<String, Object> responsesToolResult(Map<String, Object> msg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "function_call_output");
        String id = firstStr(msg, "tool_call_id", "call_id", "id");
        row.put("call_id", id);
        row.put("output", textOf(msg.get("content")));
        return row;
    }

    private static List<Map<String, Object>> responsesFunctionCalls(Map<String, Object> msg) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object tc : ProviderStream.asList(msg.get("tool_calls"))) {
            Map<String, Object> call = ProviderStream.asMap(tc);
            Map<String, Object> fn = ProviderStream.asMap(call.get("function"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "function_call");
            String id = firstStr(call, "call_id", "id");
            row.put("call_id", id.isEmpty() ? "call_" + ProviderStream.uuid24() : id);
            String name = firstStr(call, "name");
            if (name.isEmpty()) {
                name = ProviderStream.trim(fn.get("name"));
            }
            row.put("name", name.isEmpty() ? "unknown_tool" : name);
            Object args = call.get("args") != null ? call.get("args")
                    : (call.get("arguments") != null ? call.get("arguments") : fn.get("arguments"));
            if (args instanceof String s) {
                row.put("arguments", s);
            } else if (args != null) {
                row.put("arguments", studioquack.Json.write(args));
            } else {
                row.put("arguments", "{}");
            }
            out.add(row);
        }
        return out;
    }

    private static boolean responsesToolsNative(Object tools) {
        List<Object> list = ProviderStream.asList(tools);
        if (list.isEmpty()) {
            return true;
        }
        Map<String, Object> first = ProviderStream.asMap(list.get(0));
        String type = typeOf(first);
        if (type.isEmpty()) {
            return false;
        }
        if ("function".equals(type)) {
            return first.get("name") != null && !(first.get("function") instanceof Map<?, ?>)
                    && !first.containsKey("request");
        }
        return true;
    }

    private static List<Map<String, Object>> responsesTools(Object tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object raw : ProviderStream.asList(tools)) {
            Map<String, Object> tool = ProviderStream.asMap(raw);
            if (tool.isEmpty()) {
                continue;
            }
            String type = typeOf(tool);
            if (!type.isEmpty() && !"function".equals(type)) {
                out.add(new LinkedHashMap<>(tool));
                continue;
            }
            if ("function".equals(type) && tool.get("name") != null
                    && !(tool.get("function") instanceof Map<?, ?>) && !tool.containsKey("request")) {
                out.add(new LinkedHashMap<>(tool));
                continue;
            }
            Map<String, Object> src = unwrapFunction(tool);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "function");
            String name = firstStr(src, "name");
            row.put("name", name.isEmpty() ? "tool" : name);
            String desc = firstStr(src, "description");
            if (!desc.isEmpty()) {
                row.put("description", desc);
            }
            row.put("parameters", schemaOf(src));
            out.add(row);
        }
        return out;
    }

    // ---- openai-sse ----------------------------------------------------------

    /**
     * openai-sse serves the OpenAI-compatible fleet (azure-openai, groq, mistral,
     * deepseek, xai, together, openrouter, fireworks, vllm, lm-studio, …). v2 media
     * blocks and {@code files} arrays are folded into OpenAI content parts under the
     * BASIC profile — these servers accept only {@code text} / {@code image_url}, so
     * images become {@code image_url} data URLs, text-like documents decode to inlined
     * text, and binary documents/audio/video degrade to an explicit in-band notice
     * (never a silent drop, never a provider 400). Text-only messages pass through
     * untouched, keeping {@code map} idempotent.
     *
     * <p>The only other shaping is reasoning enablement for Groq-hosted families whose
     * default {@code reasoning_format} is {@code raw} (inline {@code <think>} tags the
     * answer channel would surface as text). {@code parsed} moves reasoning into
     * {@code delta.reasoning}, which the collector already fans out to the thinking
     * channel. gpt-oss is excluded: it streams {@code delta.reasoning} by default and
     * rejects {@code reasoning_format}. DeepSeek's own API, Mistral, xAI and friends
     * emit {@code reasoning_content} with no flag, and qwen-cloud uses unprefixed ids —
     * none of them match this gate.
     */
    private static Map<String, Object> openaiCompatible(Map<String, Object> body, Map<String, Object> ctx) {
        Map<String, Object> out = body;
        List<Object> messages = ProviderStream.asList(body.get("messages"));
        if (messages.stream().anyMatch(m -> {
            Map<String, Object> msg = ProviderStream.asMap(m);
            return OpenAiContentParts.hasMediaBlock(msg.get("content"))
                    || !ProviderStream.asList(msg.get("files")).isEmpty();
        })) {
            out = new LinkedHashMap<>(out);
            out.put("messages", OpenAiContentParts.messagesToOpenAi(messages, OpenAiContentParts.Profile.BASIC));
        }
        String model = modelOf(out, ctx);
        if (out.get("reasoning_format") == null && groqRawReasoningFamily(model)) {
            if (out == body) {
                out = new LinkedHashMap<>(body);
            }
            out.put("reasoning_format", "parsed");
        }
        return out;
    }

    static boolean groqRawReasoningFamily(String model) {
        if (model.isEmpty() || model.contains("gpt-oss")) {
            return false;
        }
        return model.startsWith("qwen/") || model.contains("qwq")
                || model.contains("deepseek-r1-distill");
    }

    // ---- shared ------------------------------------------------------------

    /**
     * When {@code inputFiles} is on the body or the full input context and the payload
     * already has a {@code messages} list, append those files to the last user message
     * as v2 file blocks. No messages list → leave the payload unchanged (schema-only).
     * Never logs file bytes.
     */
    private static void foldInputFiles(Map<String, Object> body, Map<String, Object> context) {
        Object raw = body.get("inputFiles");
        if (raw == null && context != null) {
            raw = context.get("inputFiles");
        }
        List<Object> files = ProviderStream.asList(raw);
        if (files.isEmpty()) {
            return;
        }
        List<Object> messages = ProviderStream.asList(body.get("messages"));
        if (messages.isEmpty()) {
            return;
        }
        int idx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(roleOf(ProviderStream.asMap(messages.get(i))))) {
                idx = i;
                break;
            }
        }
        Map<String, Object> last;
        if (idx < 0) {
            last = new LinkedHashMap<>();
            last.put("role", "user");
            last.put("content", "");
            messages = new ArrayList<>(messages);
            messages.add(last);
            idx = messages.size() - 1;
        } else {
            last = new LinkedHashMap<>(ProviderStream.asMap(messages.get(idx)));
        }
        List<Object> existing = new ArrayList<>(ProviderStream.asList(last.get("files")));
        for (Object f : files) {
            Map<String, Object> block = OpenAiContentParts.fileAsBlock(ProviderStream.asMap(f));
            if (!block.isEmpty()) {
                existing.add(block);
            }
        }
        last.put("files", existing);
        List<Object> next = new ArrayList<>(messages);
        next.set(idx, last);
        body.put("messages", next);
        body.remove("inputFiles");
    }

    private static Map<String, Object> unwrapFunction(Map<String, Object> tool) {
        if (tool.isEmpty()) {
            return tool;
        }
        Map<String, Object> fn = ProviderStream.asMap(tool.get("function"));
        if (fn.isEmpty()) {
            return tool;
        }
        Map<String, Object> out = new LinkedHashMap<>(fn);
        if (out.get("name") == null) {
            out.put("name", tool.get("name"));
        }
        if (out.get("description") == null) {
            out.put("description", tool.get("description"));
        }
        return out;
    }

    private static Object schemaOf(Map<String, Object> tool) {
        Object schema = first(tool, "request", "parameters", "input_schema");
        if (schema instanceof String s) {
            Object parsed = ProviderStream.parseJsonArgs(s);
            return parsed instanceof Map<?, ?> || parsed instanceof List<?> ? parsed : Map.of("type", "object");
        }
        return schema instanceof Map<?, ?> || schema instanceof List<?> ? schema
                : Map.of("type", "object", "properties", Map.of());
    }

    private static void absorbSystem(Object system, List<String> sink) {
        if (system == null) {
            return;
        }
        if (system instanceof String s) {
            if (!s.isBlank()) {
                sink.add(s);
            }
            return;
        }
        String text = textOf(system);
        if (!text.isEmpty()) {
            sink.add(text);
        }
    }

    private static String textOf(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof String s) {
                    sb.append(s);
                    continue;
                }
                Map<String, Object> m = ProviderStream.asMap(item);
                String t = ProviderStream.str(m.get("text"));
                if (t.isEmpty()) {
                    t = ProviderStream.str(m.get("thinking"));
                }
                sb.append(t);
            }
            return sb.toString();
        }
        Map<String, Object> m = ProviderStream.asMap(content);
        if (!m.isEmpty()) {
            return ProviderStream.str(m.get("text"));
        }
        return String.valueOf(content);
    }

    private static String roleOf(Map<String, Object> msg) {
        return ProviderStream.trim(msg.get("role")).toLowerCase(Locale.ROOT);
    }

    private static String typeOf(Map<String, Object> block) {
        return ProviderStream.trim(block.get("type")).toLowerCase(Locale.ROOT);
    }

    private static boolean hasNonEmptyList(Object v) {
        return v instanceof List<?> list && !list.isEmpty();
    }

    private static boolean isBlank(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return ProviderStream.trim(v).isEmpty();
    }

    private static void putIfAbsent(Map<String, Object> m, String key, Object v) {
        if (v != null && m.get(key) == null) {
            m.put(key, v);
        }
    }

    private static Object first(Map<String, Object> m, String... keys) {
        if (m == null || keys == null) {
            return null;
        }
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String firstStr(Map<String, Object> m, String... keys) {
        Object v = first(m, keys);
        return v == null ? "" : ProviderStream.trim(v);
    }

    private static List<String> stringList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object item : list) {
                String s = ProviderStream.trim(item);
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        } else if (v != null) {
            String s = ProviderStream.trim(v);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static String join(List<String> parts) {
        return String.join("\n\n", parts);
    }

    /** URL vs raw/base64 payload extracted from a v2 media block. Never logged. */
    private static final class Media {
        final String mime;
        final String data;
        final boolean url;

        private Media(String mime, String data, boolean url) {
            this.mime = mime;
            this.data = data;
            this.url = url;
        }

        static Media from(Map<String, Object> block) {
            if (block == null || block.isEmpty()) {
                return null;
            }
            Map<String, Object> nested = nestedMedia(block);
            Map<String, Object> src = nested.isEmpty() ? block : nested;
            String source = ProviderStream.trim(src.getOrDefault("source", block.get("source")));
            Object raw = first(src, "data", "url", "file_uri", "fileUri", "uri", "file_data", "fileData");
            if (raw == null) {
                raw = first(block, "data", "url", "file_uri", "fileUri", "uri");
            }
            if (raw == null) {
                return null;
            }
            String payload = ProviderStream.trim(raw);
            if (payload.isEmpty() || "[redacted]".equalsIgnoreCase(payload)) {
                return null;
            }
            String mime = firstStr(src, "mime_type", "mimeType", "media_type", "mediaType");
            if (mime.isEmpty()) {
                mime = firstStr(block, "mime_type", "mimeType", "media_type", "mediaType");
            }
            boolean asUrl = "url".equalsIgnoreCase(source) || looksLikeUrl(payload);
            if (payload.regionMatches(true, 0, "data:", 0, 5)) {
                int comma = payload.indexOf(',');
                String header = comma < 0 ? payload.substring(5) : payload.substring(5, comma);
                String b64 = comma < 0 ? "" : payload.substring(comma + 1);
                int sc = header.indexOf(';');
                String headerMime = (sc < 0 ? header : header.substring(0, sc)).trim();
                if (mime.isEmpty() && headerMime.contains("/")) {
                    mime = headerMime;
                }
                payload = b64;
                asUrl = false;
            }
            if (mime.isEmpty()) {
                mime = defaultMime(typeOf(block));
            }
            return new Media(mime, payload, asUrl);
        }

        private static Map<String, Object> nestedMedia(Map<String, Object> block) {
            for (String k : List.of("image_url", "imageUrl", "image", "file", "input_audio", "inputAudio")) {
                Map<String, Object> n = ProviderStream.asMap(block.get(k));
                if (!n.isEmpty()) {
                    return n;
                }
            }
            Map<String, Object> source = ProviderStream.asMap(block.get("source"));
            return source;
        }

        private static boolean looksLikeUrl(String s) {
            String t = s.toLowerCase(Locale.ROOT);
            return t.startsWith("http://") || t.startsWith("https://")
                    || t.startsWith("gs://") || t.startsWith("file://");
        }

        private static String defaultMime(String type) {
            return switch (type) {
                case "audio" -> "audio/wav";
                case "video" -> "video/mp4";
                case "document", "file" -> "application/pdf";
                default -> "image/png";
            };
        }
    }
}
