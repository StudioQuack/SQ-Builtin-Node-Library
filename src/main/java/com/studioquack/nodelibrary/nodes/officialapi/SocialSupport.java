package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.nodes.testemulator.EmulatorConfig;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared invoke-body parsing and output-port mapping for social-tier nodes.
 *
 * <p>Reads {@code configurationParameters} the same way the Node Test Emulator does
 * ({@link EmulatorConfig#readNodeParams}). INPUT values come from
 * {@code inputVariables.input} or {@code currentNode.inputList}. Reply maps are
 * keyed by the OUTPUT port name {@code output} only.
 *
 * <p>Never logs helper tokens, API keys, app passwords, JWTs, or bearer tokens.
 */
public final class SocialSupport {

    private SocialSupport() {
        // Utility — never instantiated.
    }

    /**
     * Instance config keyed by {@code shortCode}. Empty when the invoke omitted
     * {@code executionContext.currentNode.data.configurationParameters}.
     */
    public static Map<String, Object> nodeParams(Map<String, Object> request) {
        return EmulatorConfig.readNodeParams(request == null ? Map.of() : request);
    }

    /**
     * Unwrap the INPUT port {@code input} (stateful contract). A list-wrapped
     * helper value is reduced to its first element. Missing → empty map.
     */
    public static Map<String, Object> inputPort(Map<String, Object> request) {
        if (request == null) {
            return new LinkedHashMap<>();
        }
        Object raw = EmulatorUtil.asMap(request.get("inputVariables")).get("input");
        if (raw == null) {
            Object list = EmulatorUtil.dig(request, "executionContext", "currentNode", "inputList");
            for (Object item : EmulatorUtil.asList(list)) {
                Map<String, Object> row = EmulatorUtil.asMap(item);
                if ("input".equals(EmulatorUtil.str(row.get("targetPortValue")))) {
                    raw = row.get("value");
                    break;
                }
            }
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            raw = list.get(0);
        }
        return EmulatorUtil.mutableMap(raw);
    }

    /** OUTPUT-port map with the success payload under {@code output}. */
    public static Map<String, Object> ports(Object outputPayload) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("output", outputPayload == null ? Map.of() : outputPayload);
        return out;
    }


    public static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    public static String lower(Object o) {
        return str(o).toLowerCase(Locale.ROOT);
    }

    public static boolean isBlank(Object o) {
        return str(o).isEmpty();
    }

    public static String firstNonBlank(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object v : values) {
            String s = str(v);
            if (!s.isEmpty()) {
                return s;
            }
        }
        return "";
    }

    public static int toInt(Object o, int defaultValue) {
        return EmulatorUtil.toInt(o, defaultValue);
    }

    /** Bounded int; non-numeric / missing → {@code defaultValue}, then clamped. */
    public static int clampInt(Object o, int defaultValue, int min, int max) {
        int v = toInt(o, defaultValue);
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    /**
     * YouTube/Reddit counters can exceed {@code Integer.MAX_VALUE}. Missing or
     * unparsable → {@code 0}.
     */
    public static long toLong(Object o, long defaultValue) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        String s = str(o);
        if (s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(s);
            } catch (NumberFormatException e2) {
                return defaultValue;
            }
        }
    }

    /**
     * Accept a JSON array, a comma-separated string, or a single scalar as a
     * list of trimmed non-blank strings (video ids, etc.).
     */
    public static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String s = str(item);
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
            return out;
        }
        String s = str(raw);
        if (s.isEmpty()) {
            return out;
        }
        if (s.startsWith("[") && s.endsWith("]")) {
            // Schema-default seeding may stringify arrays.
            try {
                Object parsed = studioquack.Json.parse(s);
                if (parsed instanceof List<?>) {
                    return stringList(parsed);
                }
            } catch (RuntimeException ignored) {
                // fall through to comma split
            }
        }
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static Map<String, Object> asMap(Object o) {
        return EmulatorUtil.asMap(o);
    }

    public static List<Object> asList(Object o) {
        return EmulatorUtil.asList(o);
    }

    /**
     * Nested map lookup that never returns null maps. Missing hops → empty map.
     */
    public static Map<String, Object> childMap(Map<String, Object> parent, String key) {
        if (parent == null || key == null) {
            return Map.of();
        }
        return EmulatorUtil.asMap(parent.get(key));
    }

    /** Truncate text the same way the Python nodes slice descriptions ({@code [:500]}). */
    public static String cap(Object o, int maxChars) {
        String s = o == null ? "" : String.valueOf(o);
        if (maxChars <= 0 || s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }

    /**
     * Positive timeout from config, with a floor of 1s so HttpClient is not given
     * a zero/negative duration.
     */
    public static int timeoutSeconds(Map<String, Object> params, int defaultSeconds) {
        int t = toInt(params.get("timeout_seconds"), defaultSeconds);
        return t > 0 ? t : defaultSeconds;
    }

    /**
     * Config string; blank → {@code fallback}. Does not log the value (callers
     * must not log password-typed shortCodes).
     */
    public static String cfg(Map<String, Object> params, String shortCode, String fallback) {
        String v = str(params.get(shortCode));
        return v.isEmpty() ? (fallback == null ? "" : fallback) : v;
    }

    /** Template id from {@code SQ_TEMPLATE_ID_<KEY>} or the bundled definition {@code _id}. */
    public static String templateId(String nodeKey, String fallbackId) {
        String envName = com.studioquack.nodelibrary.core.NodeCatalog.envName(nodeKey);
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return fallbackId;
    }
}
