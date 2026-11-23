package com.studioquack.nodelibrary.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * extraHeaders merge + {@code {placeholder}} interpolation for Official-API
 * requests. Never logs header values (they may be secrets).
 *
 * <p>Merge order (later wins): spec-level → op-level → {@code stream.extraHeaders}
 * when the op declares a stream block (protocol headers such as
 * {@code anthropic-version} apply to unary and streaming alike).
 *
 * <p>Placeholders: {@code {configKey}} resolved from input then
 * configurationParameters (same lookup as path/query), plus
 * {@code {$now:rfc1123}} (RFC-1123 GMT) and {@code {$now:iso8601}}.
 * If any placeholder in a value is unresolvable the whole header is omitted
 * rather than sent blank.
 */
final class OfficialApiHeaders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");
    private static final Pattern CONFIG_KEY = Pattern.compile("[a-zA-Z0-9_]+");
    private static final DateTimeFormatter RFC1123_GMT = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private OfficialApiHeaders() {
        // Utility — never instantiated.
    }

    /**
     * Overlay extraHeaders. {@code stream.extraHeaders} merge last whenever the
     * op has a stream block, matching the previous unary+stream behaviour.
     */
    static Map<String, String> merge(OfficialApiSpec spec, OfficialApiSpec.Operation op) {
        Map<String, String> out = new LinkedHashMap<>();
        if (spec != null && spec.extraHeaders != null) {
            out.putAll(spec.extraHeaders);
        }
        if (op != null && op.extraHeaders != null) {
            out.putAll(op.extraHeaders);
        }
        if (op != null && op.stream != null && op.stream.extraHeaders != null) {
            out.putAll(op.stream.extraHeaders);
        }
        return out;
    }

    /**
     * Resolve placeholders. Unresolvable → header dropped. Empty results dropped.
     * {@code now} is truncated to seconds so RFC-1123 / ISO-8601 stay stable.
     */
    static Map<String, String> interpolate(Map<String, String> headers,
                                           Map<String, Object> in,
                                           Map<String, Object> cfg,
                                           Instant now) {
        Map<String, String> out = new LinkedHashMap<>();
        if (headers == null || headers.isEmpty()) {
            return out;
        }
        Instant ts = (now == null ? Instant.now() : now).truncatedTo(ChronoUnit.SECONDS);
        for (var e : headers.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                continue;
            }
            String resolved = interpolateValue(e.getValue(), in, cfg, ts);
            if (resolved != null && !resolved.isBlank()) {
                out.put(e.getKey(), resolved);
            }
        }
        return out;
    }

    /**
     * {@code null} means omit (unresolvable placeholder). Otherwise the
     * interpolated string, which may still be blank if the template was blank.
     */
    static String interpolateValue(String template, Map<String, Object> in,
                                   Map<String, Object> cfg, Instant now) {
        if (template == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String token = m.group(1);
            String val = resolveToken(token, in, cfg, now);
            if (val == null) {
                return null;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String resolveToken(String token, Map<String, Object> in,
                                       Map<String, Object> cfg, Instant now) {
        if ("$now:rfc1123".equals(token)) {
            return RFC1123_GMT.format(now);
        }
        if ("$now:iso8601".equals(token)) {
            return now.toString();
        }
        if (CONFIG_KEY.matcher(token).matches()) {
            String fromIn = DataSupport.inStr(in, token, null);
            if (fromIn != null) {
                return fromIn;
            }
            return DataSupport.cfgStr(cfg, token, null);
        }
        return null;
    }
}
