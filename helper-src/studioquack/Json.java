package studioquack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal zero-dependency JSON reader/writer for the {@code studioquack} helper package (JDK 21).
 *
 * <p>Supports objects, arrays, strings, numbers, booleans, and {@code null} for the
 * helper channel and execution-context payloads. Not a full RFC 8259 implementation
 * (no comments, no NaN).
 *
 * <pre>{@code
 * Map&lt;String, Object&gt; obj = Json.readObject("{\"a\":1}");
 * String wire = Json.write(Map.of("port", "sum", "value", "8"));
 * }</pre>
 */
public final class Json {

    /** Utility class — no instances. */
    private Json() {
    }

    /**
     * Serialise any JSON-shaped Java value to a compact UTF-8 JSON string.
     *
     * <p>Use this when building a helper-channel POST body. Maps become objects, Lists
     * become arrays, Numbers/Booleans are unquoted, everything else is a string.
     *
     * <pre>{@code
     * String body = Json.write(Map.of("ok", true, "items", List.of(Map.of("port", "sum", "value", "8"))));
     * }</pre>
     *
     * @param value map, list, string, number, boolean, or {@code null}
     * @return compact JSON; never {@code null}
     */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    /**
     * Parse a JSON object. Use this for helper-channel responses and {@code SQ_CONTEXT}.
     *
     * <pre>{@code
     * Map&lt;String, Object&gt; ctx = Json.readObject(new String(decodedBytes, UTF_8));
     * }</pre>
     *
     * @param json object text; blank/null yields an empty map rather than throwing
     * @return parsed map, or empty map when the root is not an object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readObject(String json) {
        Object parsed = parse(json);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    /**
     * Parse any JSON value (object, array, scalar). Prefer {@link #readObject} for
     * helper bodies. Returns {@code null} on blank input; throws only on malformed JSON
     * so callers can decide to swallow.
     *
     * @param json UTF-8 JSON text
     * @return parsed value
     */
    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return new Parser(json).parseValue();
    }

    /**
     * Coerce a parsed JSON value to a map. Missing/wrong type → empty map (never null).
     *
     * @param value parsed JSON node
     * @return mutable empty map when {@code value} is not a map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    /**
     * Coerce a parsed JSON value to a list. Missing/wrong type → empty list (never null).
     *
     * @param value parsed JSON node
     * @return mutable empty list when {@code value} is not a list
     */
    public static List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    /**
     * Stringify a JSON node the way port values arrive (Number, String, or nested object).
     *
     * @param value any parsed value
     * @return empty string for {@code null}
     */
    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /* ------------------------------------------------------------------ */
    /* Writer                                                             */
    /* ------------------------------------------------------------------ */

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Double || value instanceof Float) {
            sb.append(integralNumber((Number) value));
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, item);
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    /** {@code 1.0} is written as {@code 1}. Fractional values stay {@code Double.toString}. */
    private static String integralNumber(Number n) {
        double d = n.doubleValue();
        if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) <= 9_007_199_254_740_992d) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /* ------------------------------------------------------------------ */
    /* Parser                                                             */
    /* ------------------------------------------------------------------ */

    /** Recursive-descent parser; index {@code i} walks the source string. */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) {
                i++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                map.put(key, parseValue());
                skipWs();
                if (peek('}')) {
                    i++;
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek(']')) {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (peek(']')) {
                    i++;
                    return list;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (i >= s.length()) {
                        throw new IllegalArgumentException("truncated escape");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw new IllegalArgumentException("truncated unicode escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new IllegalArgumentException("bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        private Object parseNumber() {
            int start = i;
            if (peek('-')) {
                i++;
            }
            digits();
            boolean dbl = false;
            if (peek('.')) {
                dbl = true;
                i++;
                digits();
            }
            if (peek('e') || peek('E')) {
                dbl = true;
                i++;
                if (peek('+') || peek('-')) {
                    i++;
                }
                digits();
            }
            String raw = s.substring(start, i);
            if (dbl) {
                return Double.parseDouble(raw);
            }
            long n = Long.parseLong(raw);
            if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) {
                return (int) n;
            }
            return n;
        }

        private Object parseLiteral(String lit, Object value) {
            if (!s.startsWith(lit, i)) {
                throw new IllegalArgumentException("expected " + lit);
            }
            i += lit.length();
            return value;
        }

        private void digits() {
            int start = i;
            while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                i++;
            }
            if (i == start) {
                throw new IllegalArgumentException("expected digit at " + i);
            }
        }

        private void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    i++;
                } else {
                    break;
                }
            }
        }

        private boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        private void expect(char c) {
            skipWs();
            if (!peek(c)) {
                throw new IllegalArgumentException("expected '" + c + "' at " + i);
            }
            i++;
        }
    }
}
