package com.studioquack.nodelibrary.nodes.localllm;

import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads INPUT port {@code llm_input}. Helper list first, then
 * {@code inputVariables.llm_input} / {@code inputVariables.llm_input}.
 *
 * <p>A bare string is JSON-parsed when possible, otherwise wrapped as a user
 * message. Never logs the payload (it may contain PHI).
 */
final class LocalLlmInput {

    private LocalLlmInput() {
    }

    /**
     * First value on {@code llm_input}, or {@code null} when the port is empty.
     */
    static Object read(Map<String, Object> request, StudioQuackHelper helper) {
        Object raw = fromHelper(helper);
        if (raw == null) {
            raw = fromRequestVars(request, "inputVariables");
        }
        return normalize(raw);
    }

    private static Object fromHelper(StudioQuackHelper helper) {
        if (helper == null) {
            return null;
        }
        List<Object> vals = helper.getInputValuesByPortValue("llm_input");
        if (vals == null || vals.isEmpty() || vals.get(0) == null) {
            return null;
        }
        return vals.get(0);
    }

    private static Object fromRequestVars(Map<String, Object> request, String key) {
        if (request == null) {
            return null;
        }
        Map<String, Object> vars = Json.asMap(request.get(key));
        if (!vars.containsKey("llm_input")) {
            return null;
        }
        return vars.get("llm_input");
    }

    /**
     * Python {@code _input()}: parse JSON strings, wrap bare text as a user turn.
     * A one-element list of a map is unwrapped (helper sometimes list-wraps).
     */
    static Object normalize(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            // Single map: helper list wrap. A messages array stays a list (prompt path).
            if (list.size() == 1 && list.get(0) instanceof Map<?, ?>) {
                return normalize(list.get(0));
            }
            return raw;
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return null;
            }
            try {
                return Json.parse(t);
            } catch (Exception ignored) {
                Map<String, Object> wrapped = new LinkedHashMap<>();
                wrapped.put("messages", List.of(Map.of("role", "user", "content", s)));
                return wrapped;
            }
        }
        return raw;
    }

    static Object messagesOf(Object payload) {
        if (payload instanceof Map<?, ?>) {
            return Json.asMap(payload).get("messages");
        }
        return null;
    }

    static Object promptOf(Object payload) {
        if (payload instanceof Map<?, ?>) {
            return Json.asMap(payload).get("prompt");
        }
        return payload;
    }

    static Object toolsOf(Object payload) {
        if (payload instanceof Map<?, ?>) {
            return Json.asMap(payload).get("tools");
        }
        return null;
    }

    /**
     * Top-level {@code inputFiles} (camelCase) or {@code input_files} on the
     * {@code llm_input} payload. Never logs the list (file bytes / PHI).
     */
    static Object inputFilesOf(Object payload) {
        if (!(payload instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> map = Json.asMap(payload);
        Object files = map.get("inputFiles");
        return files != null ? files : map.get("input_files");
    }
}
