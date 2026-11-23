package com.studioquack.nodelibrary.nodes.ai;

import com.studioquack.nodelibrary.nodes.testemulator.EmulatorConfig;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Instance config + INPUT-port reads shared by the three AI nodes.
 *
 * <p>Instance {@code configurationParameters[]} on {@code currentNode.data} win
 * over template defaults from {@link StudioQuackHelper#getParameterValueByShortCode}.
 * INPUT falls back to {@code inputVariables} when the helper input list is empty.
 */
public final class NodeParams {

    private NodeParams() {
    }

    /** shortCode → value from the invoke body's instance parameters. */
    public static Map<String, Object> instanceParams(Map<String, Object> request) {
        return new LinkedHashMap<>(EmulatorConfig.readNodeParams(request));
    }

    /**
     * Config value: instance param, then helper template param, then {@code fallback}.
     * Empty strings are treated as missing. Never returns a sentinel empty map.
     */
    public static Object param(Map<String, Object> instance, StudioQuackHelper helper,
                               String shortCode, Object fallback) {
        if (instance != null && instance.containsKey(shortCode)) {
            Object v = instance.get(shortCode);
            if (v != null && !(v instanceof String s && s.isBlank())) {
                return v;
            }
        }
        if (helper != null) {
            Object v = helper.getParameterValueByShortCode(shortCode, null);
            if (v != null && !(v instanceof Map<?, ?> m && m.isEmpty())
                    && !(v instanceof String s && s.isBlank())) {
                return v;
            }
        }
        return fallback;
    }

    public static String paramStr(Map<String, Object> instance, StudioQuackHelper helper,
                                  String shortCode, String fallback) {
        Object v = param(instance, helper, shortCode, fallback);
        return v == null ? fallback : String.valueOf(v);
    }

    /**
     * First INPUT-port value. Helper list first; then {@code inputVariables.<port>};
     * then {@code currentNode.inputList}.
     */
    public static Object readInput(Map<String, Object> request, StudioQuackHelper helper, String port) {
        if (helper != null) {
            List<Object> vals = helper.getInputValuesByPortValue(port);
            if (vals != null && !vals.isEmpty() && vals.get(0) != null) {
                return unwrap(vals.get(0));
            }
        }
        Map<String, Object> vars = Json.asMap(request == null ? null : request.get("inputVariables"));
        if (vars.containsKey(port)) {
            return unwrap(vars.get(port));
        }
        // executionContext.currentNode.inputList[] carries port values when
        // inputVariables is absent (daemon invoke path)
        Map<String, Object> ctx = Json.asMap(request == null ? null : request.get("executionContext"));
        Map<String, Object> node = Json.asMap(ctx.get("currentNode"));
        for (Object row : Json.asList(node.get("inputList"))) {
            Map<String, Object> entry = Json.asMap(row);
            if (port.equals(Json.str(entry.get("targetPortValue")))) {
                return unwrap(entry.get("value"));
            }
        }
        return null;
    }

    public static Object unwrap(Object raw) {
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.get(0);
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
                try {
                    return Json.parse(t);
                } catch (Exception ignored) {
                    return raw;
                }
            }
        }
        return raw;
    }

    public static boolean boolish(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return false;
        }
        String s = String.valueOf(v).trim().toLowerCase();
        return "1".equals(s) || "true".equals(s) || "yes".equals(s) || "on".equals(s)
                || "stream".equals(s) || "streaming".equals(s);
    }

    /** {@code null} when unset / unparseable (so callers can omit the wire field). */
    public static Boolean boolOrNull(Object v) {
        if (v == null || (v instanceof String s && s.isBlank())) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).trim().toLowerCase();
        if ("1".equals(s) || "true".equals(s) || "yes".equals(s) || "on".equals(s)
                || "stream".equals(s) || "streaming".equals(s)) {
            return true;
        }
        if ("0".equals(s) || "false".equals(s) || "no".equals(s) || "off".equals(s)
                || "nonstream".equals(s) || "non-stream".equals(s) || "sync".equals(s)) {
            return false;
        }
        return null;
    }

    public static Integer intOrNull(Object v) {
        if (v == null || v instanceof Boolean || (v instanceof String s && s.isBlank())) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            try {
                double d = Double.parseDouble(String.valueOf(v).trim());
                if (d == (int) d) {
                    return (int) d;
                }
            } catch (Exception ignored) {
                // omit
            }
            return null;
        }
    }

    public static Double floatOrNull(Object v) {
        if (v == null || v instanceof Boolean || (v instanceof String s && s.isBlank())) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    public static Object jsonOrRaw(Object v, boolean keepRaw) {
        if (v == null || v instanceof Map<?, ?> || v instanceof List<?>) {
            return v;
        }
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return keepRaw ? s : null;
            }
            try {
                return Json.parse(t);
            } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
                return keepRaw ? s : null;
            }
        }
        return v;
    }
}
