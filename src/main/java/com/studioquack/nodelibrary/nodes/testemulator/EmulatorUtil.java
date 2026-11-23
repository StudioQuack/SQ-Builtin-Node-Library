package com.studioquack.nodelibrary.nodes.testemulator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared coercions used by the emulator (request parsing, reports, helper-channel payloads).
 *
 * <p>Kept tiny and side-effect free so every class can reuse them without dragging
 * HTTP or process logic along.
 */
public final class EmulatorUtil {

    private EmulatorUtil() {
        // Utility — never instantiated.
    }

    /** Env var or {@code defaultValue} when unset/blank. */
    public static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    public static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Empty map when {@code o} is not a JSON object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** Mutable copy of {@link #asMap(Object)} so callers can put keys safely. */
    public static Map<String, Object> mutableMap(Object o) {
        return new LinkedHashMap<>(asMap(o));
    }

    /** Empty list when {@code o} is not a JSON array. */
    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return o instanceof List<?> list ? (List<Object>) list : List.of();
    }

    public static int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Number or numeric string → int; otherwise {@code defaultValue}. */
    public static int toInt(Object o, int defaultValue) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            return parseInt(s, defaultValue);
        }
        return defaultValue;
    }

    /** True for boolean true or the strings {@code 1}/{@code true}/{@code yes} (any case). */
    public static boolean isTruthy(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o == null) {
            return false;
        }
        String s = String.valueOf(o).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }

    /** Lower-case trim; empty string when null. */
    public static String lower(Object o) {
        return str(o).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Truncate a log blob to {@code maxChars} and append a marker so callers know it
     * was capped (DoS / payload-bound). {@code maxChars <= 0} means "no cap".
     */
    public static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n…[truncated, max_log_chars=" + maxChars + "]";
    }

    /** Walk a nested map by successive keys; null if any hop is missing or not a map. */
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
}
