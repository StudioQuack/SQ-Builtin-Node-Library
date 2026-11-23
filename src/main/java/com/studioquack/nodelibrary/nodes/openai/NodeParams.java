package com.studioquack.nodelibrary.nodes.openai;

import studioquack.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Instance {@code configurationParameters[]} keyed by {@code shortCode}.
 *
 * <p>Copied from the emulator's {@code readNodeParams}: values live on
 * {@code executionContext.currentNode.data.configurationParameters}. Password
 * fields (e.g. {@code api_key}) may be resolved secrets — never log them.
 */
public final class NodeParams {

    private NodeParams() {
        // Utility — never instantiated.
    }

    /**
     * Read {@code configurationParameters[]} from the invoke executionContext.
     *
     * @param invokeRequest daemon POST body; null/missing path → empty map
     * @return shortCode → value (insertion order); blank shortCodes skipped
     */
    public static Map<String, Object> readNodeParams(Map<String, Object> invokeRequest) {
        Object list = dig(invokeRequest, "executionContext", "currentNode", "data",
                "configurationParameters");
        Map<String, Object> out = new LinkedHashMap<>();
        if (!(list instanceof List<?> params)) {
            return out;
        }
        for (Object item : params) {
            if (!(item instanceof Map<?, ?> p)) {
                continue;
            }
            String shortCode = str(p.get("shortCode"));
            Object value = p.get("value");
            if (!shortCode.isBlank() && value != null) {
                out.put(shortCode, value);
            }
        }
        return out;
    }

    /** Empty map when {@code o} is not a JSON object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** Mutable copy of {@link #asMap(Object)}. */
    public static Map<String, Object> mutableMap(Object o) {
        return new LinkedHashMap<>(asMap(o));
    }

    /** Empty list when {@code o} is not a JSON array. */
    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        if (o instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    public static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static String trim(Object o) {
        return str(o).trim();
    }

    public static boolean isBlank(Object o) {
        return o == null || trim(o).isEmpty();
    }

    /** Walk nested maps; null if any hop is missing or not a map. */
    public static Object dig(Map<String, Object> root, String... keys) {
        Object cur = root;
        for (String k : keys) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(k);
        }
        return cur;
    }

    /** Number or numeric string → int; otherwise {@code defaultValue}. */
    public static int toInt(Object o, int defaultValue) {
        Integer n = toIntOrNull(o);
        return n == null ? defaultValue : n;
    }

    public static Integer toIntOrNull(Object o) {
        if (o instanceof Boolean) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        String s = trim(o);
        if (s.isEmpty()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double toDoubleOrNull(Object o) {
        if (o instanceof Boolean) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        String s = trim(o);
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Python {@code _boolish}: true/false/unknown. {@code stream}/{@code streaming}
     * count as true so a stringly designer value still enables SSE.
     */
    public static Boolean boolish(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String s = trim(value).toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        if (s.equals("1") || s.equals("true") || s.equals("yes") || s.equals("on")
                || s.equals("stream") || s.equals("streaming")) {
            return true;
        }
        if (s.equals("0") || s.equals("false") || s.equals("no") || s.equals("off")
                || s.equals("nonstream") || s.equals("non-stream") || s.equals("sync")) {
            return false;
        }
        return null;
    }

    /** Parse JSON text; malformed → null (never throws). */
    public static Object parseJson(String json) {
        try {
            return Json.parse(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String uuid24() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    static String uuid8() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    static Object firstNonNull(Object... vs) {
        if (vs == null) {
            return null;
        }
        for (Object v : vs) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
