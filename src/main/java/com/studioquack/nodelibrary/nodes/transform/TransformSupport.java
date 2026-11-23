package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Shared helpers for the in-process data-transform nodes (aggregate … xml).
 *
 * <p>Pure functions over parsed JSON values (Map / List / String / Number / Boolean /
 * null). No network, no file I/O, no shell. Thread-safe: all state lives on the stack.
 */
public final class TransformSupport {

    /** ReDoS guard: maximum char accesses one regex match may make. */
    static final long REGEX_BUDGET = 5_000_000L;

    /** Texts larger than this are rejected before regex work (payload bound). */
    static final int MAX_TEXT_CHARS = 1_000_000;

    private TransformSupport() {
        // utility — never instantiated
    }

    /** Raised when a regex exceeds {@link #REGEX_BUDGET} char accesses (likely ReDoS). */
    public static final class RegexBudgetExceededException extends RuntimeException {
        public RegexBudgetExceededException() {
            super("regex evaluation budget exceeded");
        }
    }

    /** Operation name: INPUT field {@code operation} wins over the instance config. */
    public static String operation(Map<String, Object> cfg, Map<String, Object> in) {
        String op = DataSupport.trimToNull(EmulatorUtil.str(in == null ? null : in.get("operation")));
        if (op == null) {
            op = DataSupport.trimToNull(EmulatorUtil.str(cfg == null ? null : cfg.get("operation")));
        }
        return op == null ? "" : op.toLowerCase(Locale.ROOT);
    }

    /** Success ports: {@code {"output":{"status":"ok","data":data}}}. */
    public static Map<String, Object> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("data", data);
        return DataSupport.ports(body);
    }

    /**
     * List value at {@code in[key]}. A JSON array string is parsed; a blank string is
     * treated as absent. Returns {@code null} when absent; throws
     * {@link IllegalArgumentException} when present but not an array.
     */
    public static List<Object> listAt(Map<String, Object> in, String key) {
        if (in == null) {
            return null;
        }
        Object raw = in.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list);
            if (copy.size() == 1) {
                Object only = copy.get(0);
                if (only instanceof String s) {
                    String t = s.trim();
                    if (t.startsWith("[")) {
                        try {
                            Object parsed = Json.parse(t);
                            if (parsed instanceof List<?> nested) {
                                return new ArrayList<>(nested);
                            }
                        } catch (studioquack.StudioQuackError e) {
                            throw e;
                        } catch (Exception e) {
                            throw new IllegalArgumentException(key + " must be a JSON array");
                        }
                    }
                }
            }
            return copy;
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return null;
            }
            if (t.startsWith("[")) {
                try {
                    Object parsed = Json.parse(t);
                    if (parsed instanceof List<?> list) {
                        return new ArrayList<>(list);
                    }
                } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
                    throw new IllegalArgumentException(key + " must be a JSON array");
                }
            }
        }
        throw new IllegalArgumentException(key + " must be a JSON array");
    }

    /**
     * String list from a List, a JSON array string, or a comma-separated string.
     * Never null.
     */
    public static List<String> stringList(Object v) {
        List<String> out = new ArrayList<>();
        if (v == null) {
            return out;
        }
        Object val = v;
        if (val instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return out;
            }
            if (t.startsWith("[")) {
                try {
                    val = Json.parse(t);
                } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
                    return out;
                }
            } else {
                for (String part : t.split(",")) {
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        out.add(p);
                    }
                }
                return out;
            }
        }
        if (val instanceof List<?> list) {
            for (Object item : list) {
                String s = DataSupport.trimToNull(EmulatorUtil.str(item));
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Dotted-path read; numeric segments index into lists. Null when any hop misses. */
    public static Object getPath(Object root, String dotted) {
        if (root == null || dotted == null || dotted.isEmpty()) {
            return root;
        }
        Object cur = root;
        for (String seg : dotted.split("\\.")) {
            if (cur instanceof Map<?, ?> m) {
                cur = m.get(seg);
            } else if (cur instanceof List<?> list && seg.matches("\\d+")) {
                int idx = Integer.parseInt(seg);
                cur = idx < list.size() ? list.get(idx) : null;
            } else {
                return null;
            }
        }
        return cur;
    }

    /**
     * Dotted-path set, creating intermediate objects. An existing non-map hop is
     * replaced. Numeric segments address existing lists (set in place, or append at
     * the next free index).
     */
    @SuppressWarnings("unchecked")
    public static void setPath(Map<String, Object> root, String dotted, Object value) {
        String[] segs = dotted.split("\\.");
        Object cur = root;
        for (int i = 0; i < segs.length - 1; i++) {
            String seg = segs[i];
            Object next;
            if (cur instanceof Map<?, ?> m) {
                next = m.get(seg);
                if (!(next instanceof Map) && !(next instanceof List)) {
                    next = new LinkedHashMap<String, Object>();
                    ((Map<String, Object>) m).put(seg, next);
                }
            } else if (cur instanceof List<?> list && seg.matches("\\d+")) {
                int idx = Integer.parseInt(seg);
                List<Object> l = (List<Object>) list;
                while (l.size() <= idx) {
                    l.add(new LinkedHashMap<String, Object>());
                }
                next = l.get(idx);
                if (!(next instanceof Map) && !(next instanceof List)) {
                    next = new LinkedHashMap<String, Object>();
                    l.set(idx, next);
                }
            } else {
                return;
            }
            cur = next;
        }
        String last = segs[segs.length - 1];
        if (cur instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).put(last, value);
        } else if (cur instanceof List<?> list && last.matches("\\d+")) {
            int idx = Integer.parseInt(last);
            List<Object> l = (List<Object>) list;
            while (l.size() <= idx) {
                l.add(null);
            }
            l.set(idx, value);
        }
    }

    /** Removes a leaf at a dotted path; missing hops are ignored. */
    @SuppressWarnings("unchecked")
    public static void removePath(Map<String, Object> root, String dotted) {
        if (root == null || dotted == null || dotted.isBlank()) {
            return;
        }
        String[] segs = dotted.split("\\.");
        Object cur = root;
        for (int i = 0; i < segs.length - 1; i++) {
            if (!(cur instanceof Map<?, ?> m)) {
                return;
            }
            cur = m.get(segs[i]);
            if (cur == null) {
                return;
            }
        }
        String last = segs[segs.length - 1];
        if (cur instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).remove(last);
        }
    }

    /** Mutable deep copy of a parsed JSON value (maps/lists copied, scalars shared). */
    public static Object deepCopy(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(String.valueOf(e.getKey()), deepCopy(e.getValue()));
            }
            return copy;
        }
        if (v instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return v;
    }

    /**
     * Stable identity string: object keys sorted, integral doubles rendered as longs
     * so {@code 1} and {@code 1.0} identify equally.
     */
    public static String canonical(Object v) {
        StringBuilder sb = new StringBuilder();
        writeCanonical(sb, v);
        return sb.toString();
    }

    private static void writeCanonical(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            sb.append(Json.write(s));
        } else if (v instanceof Number n) {
            sb.append(numberString(n));
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof Map<?, ?> m) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), e.getValue());
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(Json.write(e.getKey())).append(':');
                writeCanonical(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeCanonical(sb, list.get(i));
            }
            sb.append(']');
        } else {
            sb.append(Json.write(String.valueOf(v)));
        }
    }

    /** Integral doubles/floats render without the fraction so identity is numeric. */
    public static String numberString(Number n) {
        if (n instanceof Double d) {
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return Long.toString((long) d.doubleValue());
            }
            return n.toString();
        }
        if (n instanceof Float f) {
            if (f == Math.rint(f) && !Float.isInfinite(f) && Math.abs(f) < 1e15) {
                return Long.toString((long) f.floatValue());
            }
            return n.toString();
        }
        return n.toString();
    }

    /**
     * Natural ordering: numbers numerically, strings lexicographically, booleans
     * false&lt;true, nulls last, anything else by canonical form. Mixed-type pairs
     * fall back to canonical comparison so the comparator never throws.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compareValues(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        if (a instanceof String sa && b instanceof String sb) {
            return sa.compareTo(sb);
        }
        if (a instanceof Boolean ba && b instanceof Boolean bb) {
            return ba.compareTo(bb);
        }
        if (a instanceof Comparable ca && a.getClass().isInstance(b)) {
            return ca.compareTo(b);
        }
        return canonical(a).compareTo(canonical(b));
    }

    /** Deep equality; {@code fuzzy} compares strings trimmed and case-insensitively. */
    public static boolean deepEquals(Object a, Object b, boolean fuzzy) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }
        if (a instanceof String sa && b instanceof String sb) {
            return fuzzy ? sa.trim().equalsIgnoreCase(sb.trim()) : sa.equals(sb);
        }
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            if (ma.size() != mb.size()) {
                return false;
            }
            for (Map.Entry<?, ?> e : ma.entrySet()) {
                if (!mb.containsKey(e.getKey()) || !deepEquals(e.getValue(), mb.get(e.getKey()), fuzzy)) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            if (la.size() != lb.size()) {
                return false;
            }
            for (int i = 0; i < la.size(); i++) {
                if (!deepEquals(la.get(i), lb.get(i), fuzzy)) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }

    /** Regex flags from the {@code flags} config: i → CASE_INSENSITIVE, m → MULTILINE, s → DOTALL. */
    public static int regexFlags(String flags) {
        int out = 0;
        if (flags != null) {
            for (char c : flags.toCharArray()) {
                switch (c) {
                    case 'i' -> out |= Pattern.CASE_INSENSITIVE;
                    case 'm' -> out |= Pattern.MULTILINE;
                    case 's' -> out |= Pattern.DOTALL;
                    default -> {
                        // unknown flags ignored by design
                    }
                }
            }
        }
        return out;
    }

    /**
     * CharSequence that throws {@link RegexBudgetExceededException} after
     * {@link #REGEX_BUDGET} char accesses — a thread-free ReDoS guard.
     */
    public static CharSequence bounded(String s) {
        return new CharSequence() {
            private long budget = REGEX_BUDGET;

            @Override
            public int length() {
                return s.length();
            }

            @Override
            public char charAt(int index) {
                if (--budget < 0) {
                    throw new RegexBudgetExceededException();
                }
                return s.charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return s.subSequence(start, end);
            }

            @Override
            public String toString() {
                return s;
            }
        };
    }

    /** Pretty-printed JSON (2-space indent) for the stringify operation. */
    public static String pretty(Object v) {
        StringBuilder sb = new StringBuilder();
        writePretty(sb, v, 0);
        return sb.toString();
    }

    private static void writePretty(StringBuilder sb, Object v, int indent) {
        if (v == null) {
            sb.append("null");
            return;
        }
        if (v instanceof String s) {
            sb.append(Json.write(s));
            return;
        }
        if (v instanceof Number n) {
            sb.append(numberString(n));
            return;
        }
        if (v instanceof Boolean b) {
            sb.append(b);
            return;
        }
        String pad = "  ".repeat(indent);
        String child = "  ".repeat(indent + 1);
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                sb.append(child).append(Json.write(String.valueOf(e.getKey()))).append(": ");
                writePretty(sb, e.getValue(), indent + 1);
            }
            sb.append('\n').append(pad).append('}');
            return;
        }
        if (v instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(",\n");
                }
                sb.append(child);
                writePretty(sb, list.get(i), indent + 1);
            }
            sb.append('\n').append(pad).append(']');
            return;
        }
        sb.append(Json.write(String.valueOf(v)));
    }

    /** Truthy config value ({@code true}/{@code "1"}/{@code "true"}/{@code "yes"}) with fallback. */
    public static boolean cfgBool(Map<String, Object> cfg, String key, boolean fallback) {
        Object v = cfg == null ? null : cfg.get(key);
        return v == null ? fallback : EmulatorUtil.isTruthy(v);
    }

    /** Number or numeric string → double; {@code null} when not numeric. */
    public static Double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Number or numeric string → long; {@code null} when not numeric. */
    public static Long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
