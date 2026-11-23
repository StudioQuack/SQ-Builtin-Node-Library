package com.studioquack.nodelibrary.nodes.openai;

import studioquack.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Canonical v2 ↔ OpenAI chat-completions conversion and assistant payload assembly.
 *
 * <p>Ports Python {@code _block_to_openai} / {@code _build_openai_messages} /
 * {@code _make_response} / {@code _error_response}. Empty file ports are ignored
 * so an unbound optional file does not force a multimodal request.
 */
public final class OpenAiChat {

    private static final Map<String, String> TYPE_MAP = Map.ofEntries(
            Map.entry("list", "array"), Map.entry("dict", "object"), Map.entry("int", "integer"),
            Map.entry("float", "number"), Map.entry("bool", "boolean"), Map.entry("str", "string"),
            Map.entry("text", "string"), Map.entry("long", "integer"), Map.entry("double", "number"),
            Map.entry("map", "object"), Map.entry("hashmap", "object"), Map.entry("datetime", "string"),
            Map.entry("date", "string"), Map.entry("timestamp", "string"));

    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+\\S+");
    private static final Pattern SK = Pattern.compile("sk-[A-Za-z0-9_-]+");

    private OpenAiChat() {
        // Utility — never instantiated.
    }

    /**
     * Provider failure. Throws {@link studioquack.StudioQuackError} so dispatch
     * posts {@code /v1/helper/fail} instead of an {@code error} OUTPUT.
     */
    public static Map<String, Object> errorResponse(String message) {
        throw new studioquack.StudioQuackError("PROVIDER_ERROR", redact(message));
    }

    public static Map<String, Object> errorResponse(String message, String finishReason) {
        String msg = redact(message);
        if (finishReason == null || finishReason.isBlank() || "error".equals(finishReason)) {
            throw new studioquack.StudioQuackError("PROVIDER_ERROR", msg);
        }
        throw new studioquack.StudioQuackError("PROVIDER_ERROR", msg,
                Map.of("finish_reason", finishReason));
    }

    /** Strip Bearer tokens and sk- keys from provider/error text. Never include api_key. */
    public static String redact(String message) {
        if (message == null) {
            return "";
        }
        String s = BEARER.matcher(message).replaceAll("Bearer [redacted]");
        return SK.matcher(s).replaceAll("sk-[redacted]");
    }

    static String openaiErrorMessage(int status, String body) {
        Object parsed = NodeParams.parseJson(body);
        Map<String, Object> err = NodeParams.asMap(NodeParams.asMap(parsed).get("error"));
        String msg = NodeParams.trim(err.get("message"));
        if (msg.isEmpty()) {
            msg = "HTTP " + status;
        }
        return redact(msg);
    }

    /** Python {@code _make_response}. {@code thinking} mirrors {@code thoughts} for the v1 agent. */
    public static Map<String, Object> makeResponse(List<String> textParts, List<Map<String, Object>> toolCalls,
                                                   List<Map<String, Object>> thoughts,
                                                   List<Map<String, Object>> outputFiles,
                                                   String finishReason, Map<String, Object> usage) {
        String text = textParts == null ? "" : String.join("", textParts);
        if (toolCalls != null && !toolCalls.isEmpty() && text.isBlank()) {
            text = "I am calling a tool to answer your request.";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", "assistant");
        out.put("content", List.of(Map.of("type", "text", "text", text)));
        if (toolCalls != null && !toolCalls.isEmpty()) {
            out.put("tool_calls", toolCalls);
        }
        if (thoughts != null && !thoughts.isEmpty()) {
            out.put("thoughts", thoughts);
            List<Map<String, Object>> thinking = new ArrayList<>();
            for (Map<String, Object> t : thoughts) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", "thinking");
                row.put("thinking", NodeParams.str(t.getOrDefault("text", t.get("thinking"))));
                thinking.add(row);
            }
            out.put("thinking", thinking);
        }
        if (outputFiles != null && !outputFiles.isEmpty()) {
            out.put("output_files", outputFiles);
        }
        if (finishReason != null && !finishReason.isBlank()) {
            out.put("finish_reason", finishReason);
        }
        if (usage != null && !usage.isEmpty()) {
            out.put("usage", usage);
        }
        return out;
    }

    /** Recursive JSON-schema type normaliser; injects {@code items} for arrays (OpenAI rejects {@code list}). */
    @SuppressWarnings("unchecked")
    static Object normalizeSchema(Object schema) {
        if (schema instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object value = e.getValue();
                if ("type".equals(key) && value instanceof String s) {
                    out.put(key, TYPE_MAP.getOrDefault(s.toLowerCase(Locale.ROOT), s));
                } else if (("properties".equals(key) || "patternProperties".equals(key))
                        && value instanceof Map<?, ?> props) {
                    Map<String, Object> nested = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> p : props.entrySet()) {
                        nested.put(String.valueOf(p.getKey()), normalizeSchema(p.getValue()));
                    }
                    out.put(key, nested);
                } else if ("items".equals(key)) {
                    out.put(key, normalizeSchema(value));
                } else if (("allOf".equals(key) || "anyOf".equals(key) || "oneOf".equals(key))
                        && value instanceof List<?> list) {
                    List<Object> n = new ArrayList<>();
                    for (Object s : list) {
                        n.add(normalizeSchema(s));
                    }
                    out.put(key, n);
                } else if ("additionalProperties".equals(key) && value instanceof Map<?, ?>) {
                    out.put(key, normalizeSchema(value));
                } else {
                    out.put(key, value);
                }
            }
            if ("array".equals(out.get("type")) && !out.containsKey("items")) {
                out.put("items", Map.of("type", "string"));
            }
            return out;
        }
        if (schema instanceof List<?> list) {
            List<Object> n = new ArrayList<>();
            for (Object item : list) {
                n.add(normalizeSchema(item));
            }
            return n;
        }
        return schema;
    }

    static String stripDataUrl(Object data) {
        if (data instanceof String s && s.startsWith("data:") && s.contains(",")) {
            return s.substring(s.indexOf(',') + 1);
        }
        return data instanceof String s ? s : "";
    }

    static String mediaPayload(Map<String, Object> block) {
        if (block == null) {
            return "";
        }
        String v = NodeParams.trim(block.get("data"));
        if (v.isEmpty()) {
            v = NodeParams.trim(block.get("file_data"));
        }
        if (v.isEmpty()) {
            v = NodeParams.trim(block.get("url"));
        }
        return v;
    }

    static boolean hasSendableMedia(Object block) {
        if (!(block instanceof Map<?, ?>)) {
            return false;
        }
        Map<String, Object> b = NodeParams.asMap(block);
        String btype = NodeParams.str(b.get("type"));
        if ("image".equals(btype) || "audio".equals(btype) || "document".equals(btype)) {
            return !mediaPayload(b).isEmpty();
        }
        // Native OpenAI parts after inputFiles fold (image_url / input_audio / file).
        if ("image_url".equals(btype) && b.get("image_url") instanceof Map<?, ?>) {
            return true;
        }
        if ("input_audio".equals(btype) && b.get("input_audio") instanceof Map<?, ?>) {
            return true;
        }
        if ("file".equals(btype) && b.get("file") instanceof Map<?, ?>) {
            return true;
        }
        return false;
    }

    static boolean hasMedia(List<Object> messages) {
        for (Object mObj : messages) {
            Map<String, Object> m = NodeParams.asMap(mObj);
            if (m.isEmpty()) {
                continue;
            }
            Object content = m.get("content");
            if (content instanceof List<?> blocks) {
                for (Object b : blocks) {
                    if (hasSendableMedia(b)) {
                        return true;
                    }
                }
            }
            for (Object f : NodeParams.asList(m.get("files"))) {
                Map<String, Object> fm = NodeParams.asMap(f);
                String kind = NodeParams.trim(fm.getOrDefault("kind", fm.get("type"))).toLowerCase(Locale.ROOT);
                if ("file".equals(kind)) {
                    kind = "document";
                }
                Map<String, Object> synth = new LinkedHashMap<>();
                synth.put("type", kind.isEmpty() ? "document" : kind);
                synth.put("data", fm.get("data") != null ? fm.get("data") : NodeParams.firstNonNull(fm.get("file_data"), fm.get("url")));
                if (hasSendableMedia(synth)) {
                    return true;
                }
            }
        }
        return false;
    }

    static Map<String, Object> blockToOpenai(Map<String, Object> block) {
        if (block == null) {
            return null;
        }
        String btype = NodeParams.str(block.get("type"));
        if ("text".equals(btype)) {
            return Map.of("type", "text", "text", NodeParams.str(block.get("text")));
        }
        // Already-converted OpenAI parts (e.g. after appendFilesToLastUserMessage) pass through.
        if (("image_url".equals(btype) && block.get("image_url") instanceof Map<?, ?>)
                || ("input_audio".equals(btype) && block.get("input_audio") instanceof Map<?, ?>)
                || ("file".equals(btype) && block.get("file") instanceof Map<?, ?>)) {
            return new LinkedHashMap<>(block);
        }
        if ("image".equals(btype)) {
            String source = NodeParams.str(block.get("source"));
            if (source.isEmpty()) {
                source = "base64";
            }
            String data = mediaPayload(block);
            if (data.isEmpty()) {
                return null;
            }
            if ("url".equals(source)) {
                return Map.of("type", "image_url", "image_url", Map.of("url", data, "detail", "auto"));
            }
            String mime = NodeParams.str(block.get("mime_type"));
            if (mime.isEmpty()) {
                mime = "image/png";
            }
            String url = data.startsWith("data:") ? data : "data:" + mime + ";base64," + stripDataUrl(data);
            return Map.of("type", "image_url", "image_url", Map.of("url", url, "detail", "auto"));
        }
        if ("audio".equals(btype)) {
            String data = mediaPayload(block);
            if (data.isEmpty()) {
                return null;
            }
            String fmt = NodeParams.str(block.get("mime_type"));
            fmt = fmt.contains("/") ? fmt.substring(fmt.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT) : fmt.toLowerCase(Locale.ROOT);
            if (!"wav".equals(fmt) && !"mp3".equals(fmt)) {
                fmt = "wav";
            }
            Map<String, Object> audio = new LinkedHashMap<>();
            audio.put("data", stripDataUrl(data));
            audio.put("format", fmt);
            return Map.of("type", "input_audio", "input_audio", audio);
        }
        if ("document".equals(btype)) {
            String data = mediaPayload(block);
            if (data.isEmpty()) {
                return null;
            }
            String mime = NodeParams.str(block.get("mime_type"));
            mime = mime.isEmpty() ? "application/pdf" : mime.split(";", 2)[0].toLowerCase(Locale.ROOT);
            String filename = NodeParams.str(block.get("name"));
            if (filename.isEmpty()) {
                filename = "document.pdf";
            }
            if (!"application/pdf".equals(mime)) {
                return Map.of("type", "text", "text",
                        "[Document attachment '" + filename + "' has MIME type '" + mime
                                + "', but OpenAI chat file input only accepts application/pdf data URLs. The file was not sent.]");
            }
            String fileData = data.startsWith("data:") ? data : "data:application/pdf;base64," + stripDataUrl(data);
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("file_data", fileData);
            file.put("filename", filename);
            return Map.of("type", "file", "file", file);
        }
        if ("video".equals(btype)) {
            String name = NodeParams.str(block.getOrDefault("name", block.get("mime_type")));
            return Map.of("type", "text", "text",
                    "[Video attachment '" + name
                            + "' not supported by OpenAI chat completions; switch to a video-capable provider or extract frames.]");
        }
        return null;
    }

    /** String when text-only (cheaper); list of parts otherwise. */
    static Object contentToOpenai(Object content, List<Object> files) {
        List<Map<String, Object>> parts = new ArrayList<>();
        boolean allText = true;
        if (content instanceof String s) {
            if (!s.isEmpty()) {
                parts.add(Map.of("type", "text", "text", s));
            }
        } else if (content instanceof List<?> list) {
            for (Object b : list) {
                if (b instanceof Map<?, ?>) {
                    Map<String, Object> conv = blockToOpenai(NodeParams.asMap(b));
                    if (conv != null) {
                        if (!"text".equals(conv.get("type"))) {
                            allText = false;
                        }
                        parts.add(conv);
                    }
                } else if (b instanceof String s && !s.isEmpty()) {
                    parts.add(Map.of("type", "text", "text", s));
                }
            }
        } else if (content != null) {
            parts.add(Map.of("type", "text", "text", String.valueOf(content)));
        }
        if (files != null) {
            for (Object f : files) {
                Map<String, Object> fm = NodeParams.asMap(f);
                if (fm.isEmpty()) {
                    continue;
                }
                String kind = NodeParams.trim(fm.getOrDefault("kind", fm.get("type"))).toLowerCase(Locale.ROOT);
                if ("file".equals(kind)) {
                    kind = "document";
                }
                Map<String, Object> synth = new LinkedHashMap<>();
                synth.put("type", kind.isEmpty() ? "document" : kind);
                synth.put("mime_type", fm.get("mime_type"));
                String source = NodeParams.str(fm.get("source"));
                synth.put("source", source.isEmpty() ? (fm.get("url") != null ? "url" : "base64") : source);
                synth.put("data", NodeParams.firstNonNull(fm.get("data"), fm.get("file_data"), fm.get("url")));
                synth.put("name", fm.get("name"));
                Map<String, Object> conv = blockToOpenai(synth);
                if (conv != null) {
                    if (!"text".equals(conv.get("type"))) {
                        allText = false;
                    }
                    parts.add(conv);
                }
            }
        }
        if (allText) {
            StringBuilder joined = new StringBuilder();
            for (Map<String, Object> p : parts) {
                joined.append(NodeParams.str(p.get("text")));
            }
            if (joined.length() > 0) {
                return joined.toString();
            }
            return content instanceof String s ? s : "";
        }
        return parts;
    }

    static List<Map<String, Object>> convertToolCalls(Object toolCalls) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object tcObj : NodeParams.asList(toolCalls)) {
            Map<String, Object> tc = NodeParams.asMap(tcObj);
            if (tc.isEmpty()) {
                continue;
            }
            if (tc.containsKey("function") && "function".equals(tc.get("type"))) {
                out.add(new LinkedHashMap<>(tc));
                continue;
            }
            Object args = tc.containsKey("args") ? tc.get("args") : tc.get("arguments");
            String argStr;
            if (args instanceof Map<?, ?> || args instanceof List<?>) {
                argStr = Json.write(args);
            } else if (args instanceof String s) {
                argStr = s;
            } else {
                argStr = "{}";
            }
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", NodeParams.str(tc.get("name")));
            fn.put("arguments", argStr);
            Map<String, Object> row = new LinkedHashMap<>();
            String id = NodeParams.str(tc.get("id"));
            row.put("id", id.isEmpty() ? "call_" + NodeParams.uuid24() : id);
            row.put("type", "function");
            row.put("function", fn);
            out.add(row);
        }
        return out;
    }

    static List<Map<String, Object>> buildOpenaiMessages(List<Object> messages) {
        List<String> systemBuf = new ArrayList<>();
        List<Map<String, Object>> body = new ArrayList<>();
        for (Object mObj : messages) {
            Map<String, Object> m = NodeParams.asMap(mObj);
            if (m.isEmpty()) {
                continue;
            }
            String role = NodeParams.str(m.get("role"));
            if ("system".equals(role)) {
                Object text = contentToOpenai(m.get("content"), null);
                if (text instanceof List<?> parts) {
                    StringBuilder sb = new StringBuilder();
                    for (Object p : parts) {
                        Map<String, Object> pm = NodeParams.asMap(p);
                        if ("text".equals(pm.get("type"))) {
                            if (sb.length() > 0) {
                                sb.append('\n');
                            }
                            sb.append(NodeParams.str(pm.get("text")));
                        }
                    }
                    text = sb.toString();
                }
                if (text instanceof String s && !s.isEmpty()) {
                    systemBuf.add(s);
                }
                continue;
            }
            if ("assistant".equals(role)) {
                Map<String, Object> newMsg = new LinkedHashMap<>();
                newMsg.put("role", "assistant");
                Object content = contentToOpenai(m.get("content"), null);
                boolean emptyContent = content == null || (content instanceof String s && s.isEmpty())
                        || (content instanceof List<?> l && l.isEmpty());
                newMsg.put("content", emptyContent ? null : content);
                if (m.get("tool_calls") != null && !NodeParams.asList(m.get("tool_calls")).isEmpty()) {
                    newMsg.put("tool_calls", convertToolCalls(m.get("tool_calls")));
                    if (emptyContent) {
                        newMsg.put("content", null);
                    }
                }
                body.add(newMsg);
                continue;
            }
            if ("tool".equals(role)) {
                Object content = m.get("content");
                String text;
                if (content instanceof String s) {
                    text = s;
                } else if (content instanceof Map<?, ?> || content instanceof List<?>) {
                    text = Json.write(content);
                } else {
                    text = String.valueOf(content);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", "tool");
                row.put("content", text);
                String tcid = NodeParams.str(m.get("tool_call_id"));
                if (tcid.isEmpty()) {
                    tcid = NodeParams.str(m.get("id"));
                }
                row.put("tool_call_id", tcid);
                row.put("name", NodeParams.str(m.get("name")));
                body.add(row);
                continue;
            }
            List<Object> files = m.get("files") instanceof List<?> ? NodeParams.asList(m.get("files")) : null;
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("role", role.isEmpty() ? "user" : role);
            user.put("content", contentToOpenai(m.get("content"), files));
            body.add(user);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        if (!systemBuf.isEmpty()) {
            out.add(Map.of("role", "system", "content", String.join("\n\n", systemBuf)));
        }
        out.addAll(body);
        if (out.isEmpty()) {
            throw new IllegalArgumentException("Empty message list after normalisation.");
        }
        return out;
    }

    static List<Map<String, Object>> buildOpenaiTools(List<Object> tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object tObj : tools) {
            Map<String, Object> tool = NodeParams.asMap(tObj);
            if (tool.isEmpty()) {
                continue;
            }
            if ("function".equals(tool.get("type")) && tool.containsKey("function")) {
                out.add(new LinkedHashMap<>(tool));
                continue;
            }
            Object params = tool.get("request") != null ? tool.get("request") : tool.get("parameters");
            if (params == null) {
                params = Map.of();
            }
            Map<String, Object> fn = new LinkedHashMap<>();
            String name = NodeParams.str(tool.get("name"));
            fn.put("name", name.isEmpty() ? "tool" : name);
            fn.put("description", NodeParams.str(tool.get("description")));
            fn.put("parameters", normalizeSchema(params));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "function");
            row.put("function", fn);
            out.add(row);
        }
        return out;
    }
}
