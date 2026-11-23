package com.studioquack.nodelibrary.nodes.testemulator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal zero-dependency JSON reader/writer.
 *
 * <p>Handles objects, arrays, strings, numbers, booleans and null — enough for the
 * stateful invoke body, helper-channel payloads and the test-report object. No
 * pretty-print; output is compact. Numbers without a fraction/exponent become
 * {@link Long}; others become {@link Double}.
 */
public final class Json {

    private Json() {
        // Utility — never instantiated.
    }

    /** Serialise a map as a JSON object. */
    public static String write(Map<String, Object> obj) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, obj);
        return sb.toString();
    }

    /** Serialise any supported JSON value (object, array, scalar, null). */
    public static String writeValue(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    /** Parse {@code src} and require the root to be a JSON object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readObject(String src) {
        Object v = parse(src);
        if (!(v instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("JSON object required");
        }
        return (Map<String, Object>) v;
    }

    /** Parse {@code src} into a Java value (Map, List, String, Number, Boolean, null). */
    public static Object parse(String src) {
        Parser p = new Parser(src);
        return p.parseValue();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
            return;
        }
        if (v instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
            return;
        }
        if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
            return;
        }
        if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append('"').append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (v instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeValue(sb, list.get(i));
            }
            sb.append(']');
            return;
        }
        // Unknown types are stringified so a report never blows up mid-serialise.
        sb.append('"').append(escape(String.valueOf(v))).append('"');
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
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
        return sb.toString();
    }

    /** Recursive-descent parser. Index {@code i} is the next character to consume. */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) {
                throw err("eof");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBool();
                case 'n' -> parseNull();
                case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber();
                default -> throw err("unexpected '" + c + "'");
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (take('}')) {
                return m;
            }
            while (true) {
                String key = parseString();
                expect(':');
                m.put(key, parseValue());
                skipWs();
                if (take('}')) {
                    return m;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (take(']')) {
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (take(']')) {
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
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    throw err("bad escape");
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
                            throw err("bad \\u");
                        }
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw err("bad escape");
                }
            }
            throw err("unterminated string");
        }

        private Object parseNumber() {
            int start = i;
            if (take('-')) {
                /* leading sign */
            }
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            boolean dbl = false;
            if (take('.')) {
                dbl = true;
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                dbl = true;
                i++;
                if (take('+') || take('-')) {
                    /* exponent sign */
                }
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            // take() skips whitespace even on mismatch, so the cursor may sit past
            // trailing spaces (e.g. "[ 1 , 2 ]") — never let them into the token.
            while (i > start && Character.isWhitespace(s.charAt(i - 1))) {
                i--;
            }
            String n = s.substring(start, i);
            // Not a ternary: `dbl ? Double : Long` would numeric-promote both to double.
            if (dbl) {
                return Double.valueOf(n);
            }
            return Long.valueOf(n);
        }

        private Boolean parseBool() {
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw err("bad bool");
        }

        private Object parseNull() {
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            throw err("bad null");
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

        private boolean take(char c) {
            skipWs();
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!take(c)) {
                throw err("expected '" + c + "'");
            }
        }

        private IllegalArgumentException err(String msg) {
            return new IllegalArgumentException("JSON at " + i + ": " + msg);
        }
    }
}
