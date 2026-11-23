package com.studioquack.nodelibrary.nodes.aiagent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Envelope unwrap for provider / execute responses (Python {@code _looks_like_assistant_payload},
 * {@code _find_assistant_payload}, {@code _is_empty_execute_response}).
 *
 * <p>Do not classify generic StudioQuack envelopes as assistant messages just because they
 * contain a {@code content} key — nested {@code result.output_files} must survive.
 */
final class PayloadUnwrap {

    private static final String[] ENVELOPE_KEYS = {
            "result", "response", "llm_output", "output", "data", "value"
    };
    private static final String[] ASSISTANT_KEYS = {
            "tool_calls", "thinking", "thoughts", "output_files", "outputFiles", "finish_reason", "error"
    };

    /** Substrings providers use for context-too-long (lowercase). */
    static final String[] CONTEXT_OVERFLOW_HINTS = {
            "context_length_exceeded",
            "context length",
            "maximum context length",
            "maximum context",
            "prompt is too long",
            "input is too long",
            "too many input tokens",
            "exceeds maximum input",
            "exceeds the maximum input",
            "context window",
            "context_too_long",
            "context too long",
            "request too large",
            "input tokens",
            "max tokens",
            "max_tokens",
            "token limit",
            "tokens exceeds"
    };

    private PayloadUnwrap() {
        // namespace — never instantiated
    }

    static boolean looksLikeAssistantPayload(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        if ("assistant".equals(map.get("role"))) {
            return true;
        }
        for (String k : ASSISTANT_KEYS) {
            if (map.containsKey(k)) {
                return true;
            }
        }
        if (!map.containsKey("content")) {
            return false;
        }
        for (String k : ENVELOPE_KEYS) {
            if (map.containsKey(k)) {
                return false;
            }
        }
        return true;
    }

    static Object findAssistantPayload(Object value) {
        return findAssistantPayload(value, 0);
    }

    static Object findAssistantPayload(Object value, int depth) {
        if (depth > 10) {
            return null;
        }
        value = AgentJson.maybeParseString(value);
        while (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            if (list.size() == 1) {
                value = AgentJson.maybeParseString(list.get(0));
                continue;
            }
            for (Object item : list) {
                Object found = findAssistantPayload(item, depth + 1);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        if ("assistant".equals(map.get("role"))) {
            return value;
        }
        for (String key : ENVELOPE_KEYS) {
            if (map.containsKey(key)) {
                Object found = findAssistantPayload(map.get(key), depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        if (looksLikeAssistantPayload(value)) {
            return value;
        }
        for (Object v : map.values()) {
            Object found = findAssistantPayload(v, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Deep search for {@code output_files} and {@code outputFiles} lists in the transport
     * payload. Unique items are merged ({@code output_files} first, then {@code outputFiles})
     * so generated files survive either key and either envelope shape.
     */
    static List<Object> findOutputFiles(Object value) {
        List<Object> out = new ArrayList<>();
        collectOutputFiles(value, 0, out);
        return out;
    }

    private static void collectOutputFiles(Object value, int depth, List<Object> out) {
        if (depth > 10) {
            return;
        }
        value = AgentJson.maybeParseString(value);
        if (value instanceof Map<?, ?> map) {
            addUniqueFiles(out, map.get("output_files"));
            addUniqueFiles(out, map.get("outputFiles"));
            for (Object v : map.values()) {
                collectOutputFiles(v, depth + 1, out);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                collectOutputFiles(item, depth + 1, out);
            }
        }
    }

    /** Merge assistant + envelope file lists onto {@code assistant.output_files}. */
    static void mergeOutputFilesInto(Map<String, Object> assistant, Object raw) {
        if (assistant == null) {
            return;
        }
        List<Object> merged = new ArrayList<>();
        addUniqueFiles(merged, assistant.get("output_files"));
        addUniqueFiles(merged, assistant.get("outputFiles"));
        addUniqueFiles(merged, findOutputFiles(raw));
        if (!merged.isEmpty()) {
            assistant.put("output_files", merged);
        }
    }

    static void addUniqueFiles(List<Object> dest, Object files) {
        if (dest == null || !(files instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item != null && !dest.contains(item)) {
                dest.add(item);
            }
        }
    }

    /**
     * True when the FUNCTION-port transport returned no payload (including
     * recursively empty containers). Distinct from a provider empty assistant message.
     */
    static boolean isEmptyExecuteResponse(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return true;
            }
            for (Object item : list) {
                if (!isEmptyExecuteResponse(item)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** Compact log-friendly shape; truncates strings so base64 never hits logs. */
    static String describeRawShape(Object value) {
        return describeRawShape(value, 0, 4);
    }

    static String describeRawShape(Object value, int depth, int maxDepth) {
        if (depth > maxDepth) {
            return "<" + typeName(value) + ">";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            int n = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (n > 0) {
                    sb.append(", ");
                }
                if (n >= 12) {
                    break;
                }
                sb.append(quote(AgentJson.str(e.getKey()))).append(": ")
                        .append(describeRawShape(e.getValue(), depth + 1, maxDepth));
                n++;
            }
            sb.append('}');
            return sb.toString();
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return "[]";
            }
            String sample = describeRawShape(list.get(0), depth + 1, maxDepth);
            return "list[" + list.size() + ", first=" + sample + "]";
        }
        if (value instanceof String s) {
            if (s.length() <= 120) {
                return quote(s);
            }
            return quote(s.substring(0, 120)) + "...<" + s.length() + "B>";
        }
        if (value instanceof Number || value instanceof Boolean || value == null) {
            return String.valueOf(value);
        }
        return "<" + typeName(value) + ">";
    }

    static boolean payloadIndicatesContextOverflow(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return false;
        }
        Object errorMsg = map.get("error");
        if (errorMsg == null) {
            errorMsg = "";
        }
        String fr = AgentJson.str(map.get("finish_reason")).toLowerCase(Locale.ROOT);
        if (AgentJson.str(errorMsg).isBlank() && !("error".equals(fr) || "length".equals(fr))) {
            return false;
        }
        String haystack = AgentJson.str(errorMsg);
        if (haystack.isBlank()) {
            haystack = AgentJson.extractTextFromContent(map.get("content"));
        }
        haystack = haystack.toLowerCase(Locale.ROOT);
        if (haystack.isBlank()) {
            return false;
        }
        for (String hint : CONTEXT_OVERFLOW_HINTS) {
            if (haystack.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /** The error a round ended on, or {@code ""} when it carried none. */
    static String payloadError(Object payload) {
        return payload instanceof Map<?, ?> map ? AgentJson.str(map.get("error")).trim() : "";
    }

    /**
     * The declared type for {@link #payloadError} — model nodes tag their failed turns (e.g.
     * {@code EmptyResponseError}) so the cause survives into the agent's own error envelope.
     */
    static String payloadErrorType(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return "Error";
        }
        String type = AgentJson.str(map.get("type")).trim();
        return type.isBlank() ? "Error" : type;
    }

    static String finalText(Object payload) {
        if (!(payload instanceof Map<?, ?>)) {
            return AgentJson.str(payload);
        }
        return AgentJson.extractTextFromContent(((Map<?, ?>) payload).get("content"));
    }

    static List<Object> finalFiles(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<Object> merged = new ArrayList<>();
        addUniqueFiles(merged, map.get("output_files"));
        addUniqueFiles(merged, map.get("outputFiles"));
        return merged;
    }

    static Map<String, Object> errorAssistant(String message, String finishReason) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", message == null ? "" : message);
        List<Object> content = new ArrayList<>();
        content.add(block);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", "assistant");
        out.put("content", content);
        if (finishReason != null) {
            out.put("finish_reason", finishReason);
        }
        if (message != null) {
            out.put("error", message);
        }
        return out;
    }

    static Map<String, Object> textAssistant(String text) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", text == null ? "" : text);
        List<Object> content = new ArrayList<>();
        content.add(block);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", "assistant");
        out.put("content", content);
        return out;
    }

    static boolean looks404or503(Object data, String raw) {
        int status = statusCode(data);
        if (status == 404 || status == 503) {
            return true;
        }
        String hay = (AgentJson.str(data) + " " + AgentJson.str(raw)).toLowerCase(Locale.ROOT);
        return hay.contains("404") || hay.contains("503") || hay.contains("not found")
                || hay.contains("service unavailable") || hay.contains("not sse")
                || hay.contains("non-sse") || hay.contains("endpoint missing");
    }

    static int statusCode(Object data) {
        if (!(data instanceof Map<?, ?> map)) {
            return 0;
        }
        for (String k : List.of("status", "statusCode", "status_code", "code", "httpStatus", "http_status")) {
            Object v = map.get(k);
            if (v instanceof Number n) {
                return n.intValue();
            }
            if (v instanceof String s) {
                try {
                    return Integer.parseInt(s.strip());
                } catch (NumberFormatException ignored) {
                    // keep scanning
                }
            }
        }
        return 0;
    }

    static boolean truthy(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            return !s.isBlank();
        }
        if (o instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (o instanceof List<?> l) {
            return !l.isEmpty();
        }
        return true;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** Bridge names used by {@link AgentLoop} / {@link AiAgentNode}. */
    static String extractText(Object payload) {
        if (payload instanceof Map<?, ?> map && map.containsKey("content")) {
            return AgentJson.extractTextFromContent(map.get("content"));
        }
        return AgentJson.extractTextFromContent(payload);
    }

    static boolean overflow(Object payload) {
        return payloadIndicatesContextOverflow(payload);
    }

    static boolean isEmpty(Object value) {
        return isEmptyExecuteResponse(value);
    }

    static Map<String, Object> findAssistant(Object value) {
        Object found = findAssistantPayload(value);
        return found instanceof Map<?, ?> ? AgentJson.asMap(found) : null;
    }
}
