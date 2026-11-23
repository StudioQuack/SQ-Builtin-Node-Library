package com.studioquack.nodelibrary.nodes.codec;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Invoke-body builders for codec node tests: the daemon POST shape is
 * {@code executionContext.currentNode.data.configurationParameters[]} for
 * config and {@code inputVariables.input} for the INPUT port.
 */
final class CodecTestSupport {

    private CodecTestSupport() {
        // utility — never instantiated
    }

    static Map<String, Object> request(Map<String, Object> cfg, Map<String, Object> input) {
        List<Map<String, Object>> params = new ArrayList<>();
        if (cfg != null) {
            cfg.forEach((k, v) -> {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("shortCode", k);
                p.put("value", v);
                params.add(p);
            });
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configurationParameters", params);
        Map<String, Object> currentNode = new LinkedHashMap<>();
        currentNode.put("data", data);
        Map<String, Object> executionContext = new LinkedHashMap<>();
        executionContext.put("currentNode", currentNode);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", executionContext);
        if (input != null) {
            request.put("inputVariables", Map.of("input", input));
        }
        return request;
    }

    /** Alternating key/value pairs; values may be any JSON-shaped object. */
    static Map<String, Object> cfg(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    static Map<String, Object> input(Object... kv) {
        return cfg(kv);
    }


    /**
     * Invoke a codec handler; {@code StudioQuackError} becomes an output.error map
     * so existing assertions keep working.
     */
    static Map<String, Object> run(com.studioquack.nodelibrary.core.NodeHandler node,
                                   Map<String, Object> request) {
        try {
            return node.handle(request, null);
        } catch (studioquack.StudioQuackError e) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("error", e.getMessage());
            out.put("errorCode", e.errorCode());
            return Map.of("output", out);
        }
    }

    /** The {@code output} port body of a handler result. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> output(Map<String, Object> result) {
        return (Map<String, Object>) result.get("output");
    }

    /** The {@code data} object inside a success output. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> data(Map<String, Object> result) {
        return (Map<String, Object>) output(result).get("data");
    }

    static String error(Map<String, Object> result) {
        Object e = output(result).get("error");
        return e == null ? null : String.valueOf(e);
    }

    static boolean isOk(Map<String, Object> result) {
        return "ok".equals(output(result).get("status"));
    }

    /** ContentBlock-shaped platform file value for type-file inputs. */
    static Map<String, Object> fileValue(String name, byte[] bytes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "file");
        m.put("name", name);
        m.put("mime_type", "application/octet-stream");
        m.put("source", "base64");
        m.put("data", Base64.getEncoder().encodeToString(bytes));
        return m;
    }

    /** Decoded bytes of a {@code file} ContentBlock on a handler output. */
    @SuppressWarnings("unchecked")
    static byte[] outputFileBytes(Map<String, Object> result) {
        Map<String, Object> file = (Map<String, Object>) output(result).get("file");
        return Base64.getDecoder().decode(String.valueOf(file.get("data")));
    }

    @SuppressWarnings("unchecked")
    static String outputFileName(Map<String, Object> result) {
        Map<String, Object> file = (Map<String, Object>) output(result).get("file");
        return String.valueOf(file.get("name"));
    }
}
