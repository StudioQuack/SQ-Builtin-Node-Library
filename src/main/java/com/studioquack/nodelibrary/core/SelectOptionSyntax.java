package com.studioquack.nodelibrary.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure parser for {@code select}-type configuration parameter options in the
 * {@code value:label:role} CSV grammar.
 *
 * <p>Mirrors {@code SelectOptionParser} across backend, daemons, and frontend:
 * <ul>
 *   <li>Supports {@code \:} escaping for literal colons.</li>
 *   <li>Preserves {@code ://} URL scheme separators intact.</li>
 *   <li>1 segment → {@code (v, v, "normal")}.</li>
 *   <li>2 segments → {@code (v, label.isEmpty() ? v : label, "normal")}.</li>
 *   <li>3+ segments → when the last segment matches a known role token
 *       ({@code normal | listen | message_catch | send_and_wait | message_throw},
 *       case-insensitive, hyphen or underscore), takes it as {@code role},
 *       first segment as {@code value}, middle joined with {@code :} as {@code label};
 *       otherwise treats remainder as {@code label} with {@code role = "normal"}.</li>
 * </ul>
 */
public final class SelectOptionSyntax {

    public static final Set<String> KNOWN_ROLES = Set.of(
            "normal", "listen", "message_catch", "send_and_wait", "message_throw");

    private SelectOptionSyntax() {
    }

    /** Parsed representation of a single select option entry. */
    public record ParsedOption(String value, String label, String role) {
        public boolean isIngressRole() {
            return "listen".equals(role) || "message_catch".equals(role) || "send_and_wait".equals(role);
        }
    }

    /** Parse one raw select option string into {@code (value, label, role)}. */
    public static ParsedOption parseOption(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedOption("", "", "normal");
        }
        List<String> segments = splitUnescapedColons(raw.trim());
        int count = segments.size();
        if (count == 1) {
            String v = segments.get(0);
            return new ParsedOption(v, v, "normal");
        }
        if (count == 2) {
            String v = segments.get(0);
            String label = segments.get(1).isEmpty() ? v : segments.get(1);
            return new ParsedOption(v, label, "normal");
        }
        String v = segments.get(0);
        String candidateRole = normalizeRole(segments.get(count - 1));
        if (candidateRole != null) {
            String middle = joinSegments(segments, 1, count - 1);
            String label = middle.isEmpty() ? v : middle;
            return new ParsedOption(v, label, candidateRole);
        }
        String remainder = joinSegments(segments, 1, count);
        String label = remainder.isEmpty() ? v : remainder;
        return new ParsedOption(v, label, "normal");
    }

    /** Extract only the pure wire value from a select option string. */
    public static String extractValue(String raw) {
        return parseOption(raw).value();
    }

    /** Parse all non-blank select options in order. */
    public static List<ParsedOption> parseOptions(List<String> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) {
            return List.of();
        }
        List<ParsedOption> out = new ArrayList<>(rawOptions.size());
        for (String raw : rawOptions) {
            if (raw != null && !raw.isBlank()) {
                out.add(parseOption(raw));
            }
        }
        return out;
    }

    /** Normalize a role token, or return {@code null} when unknown. */
    public static String normalizeRole(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return KNOWN_ROLES.contains(normalized) ? normalized : null;
    }

    private static List<String> splitUnescapedColons(String input) {
        List<String> parts = new ArrayList<>(3);
        StringBuilder current = new StringBuilder(input.length());
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < len && input.charAt(i + 1) == ':') {
                current.append(':');
                i++;
                continue;
            }
            if (c == ':') {
                if (i + 2 < len && input.charAt(i + 1) == '/' && input.charAt(i + 2) == '/') {
                    current.append("://");
                    i += 2;
                    continue;
                }
                parts.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString().trim());
        return parts;
    }

    private static String joinSegments(List<String> segments, int fromInclusive, int toExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (i > fromInclusive) {
                sb.append(':');
            }
            sb.append(segments.get(i));
        }
        return sb.toString().trim();
    }
}
