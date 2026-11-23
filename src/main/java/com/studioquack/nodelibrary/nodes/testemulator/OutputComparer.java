package com.studioquack.nodelibrary.nodes.testemulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deep-compare of actual vs expected output: checks type, missing/unexpected keys,
 * array length, and scalar equality, reporting differences as paths ({@code root.foo[0]}).
 *
 * <p>{@code expected == null} means "no assertion" — the caller treats that as a match.
 */
public final class OutputComparer {

    private OutputComparer() {
        // Utility — never instantiated.
    }

    /**
     * @return {@code true} when every expected value is present and equal; differences
     *         is empty on success
     */
    public static CompareResult compare(Object actual, Object expected) {
        List<String> differences = new ArrayList<>();
        if (expected == null) {
            return new CompareResult(true, differences);
        }
        walk(actual, expected, "root", differences);
        return new CompareResult(differences.isEmpty(), differences);
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object actual, Object expected, String path, List<String> diffs) {
        if (expected == null && actual == null) {
            return;
        }
        if (typeName(actual) != null && typeName(expected) != null
                && !Objects.equals(typeName(actual), typeName(expected))) {
            diffs.add("Type mismatch at " + path + ": expected " + typeName(expected)
                    + ", got " + typeName(actual));
            return;
        }
        if (expected instanceof Map<?, ?> expMap) {
            if (!(actual instanceof Map<?, ?> actMap)) {
                diffs.add("Type mismatch at " + path + ": expected object, got " + typeName(actual));
                return;
            }
            Map<String, Object> exp = (Map<String, Object>) expMap;
            Map<String, Object> act = (Map<String, Object>) actMap;
            for (String key : exp.keySet()) {
                if (!act.containsKey(key)) {
                    diffs.add("Missing key at " + path + "." + key);
                }
            }
            for (String key : act.keySet()) {
                if (!exp.containsKey(key)) {
                    diffs.add("Unexpected key at " + path + "." + key);
                }
            }
            for (String key : exp.keySet()) {
                if (act.containsKey(key)) {
                    walk(act.get(key), exp.get(key), path + "." + key, diffs);
                }
            }
            return;
        }
        if (expected instanceof List<?> expList) {
            if (!(actual instanceof List<?> actList)) {
                diffs.add("Type mismatch at " + path + ": expected array, got " + typeName(actual));
                return;
            }
            if (actList.size() != expList.size()) {
                diffs.add("Array length mismatch at " + path + ": expected " + expList.size()
                        + ", got " + actList.size());
                return;
            }
            for (int i = 0; i < expList.size(); i++) {
                walk(actList.get(i), expList.get(i), path + "[" + i + "]", diffs);
            }
            return;
        }
        if (!Objects.equals(normaliseNumber(actual), normaliseNumber(expected))) {
            diffs.add("Value mismatch at " + path + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Treat {@code 1} (Long) and {@code 1.0} (Double) as equal so JSON number
     * round-trips from Python/JS do not fail the compare.
     */
    private static Object normaliseNumber(Object o) {
        if (o instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return n.longValue();
            }
            return d;
        }
        return o;
    }

    private static String typeName(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof Map<?, ?>) {
            return "object";
        }
        if (o instanceof List<?>) {
            return "array";
        }
        if (o instanceof String) {
            return "string";
        }
        if (o instanceof Number) {
            return "number";
        }
        if (o instanceof Boolean) {
            return "boolean";
        }
        return o.getClass().getSimpleName();
    }

    /** Result of one deep compare. */
    public record CompareResult(boolean matches, List<String> differences) {
    }
}
