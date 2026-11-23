package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;

import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared plumbing for the local codec / security / file nodes: operation
 * resolution, output-port shaping, and byte↔text encodings.
 *
 * <p>Package-private utility. No logging anywhere in this package — secrets and
 * generated codes flow through outputs only, never through log statements.
 */
final class CodecSupport {

    private CodecSupport() {
        // utility — never instantiated
    }

    /** Operation name: INPUT {@code operation} overrides the config select. */
    static String op(Map<String, Object> cfg, Map<String, Object> in) {
        String v = DataSupport.inStr(in, "operation", null);
        if (v == null) {
            v = DataSupport.cfgStr(cfg, "operation", null);
        }
        return v == null ? null : v.trim().toLowerCase(Locale.ROOT);
    }

    /** {@code {"status":"ok","data":...}} on the OUTPUT port. */
    static Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("data", data == null ? new LinkedHashMap<>() : data);
        return DataSupport.ports(out);
    }

    /** Success with a produced platform file (ContentBlock on {@code file}). */
    static Map<String, Object> okWithFile(Map<String, Object> data, String name, String mime, byte[] bytes) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        if (data != null) {
            out.put("data", data);
        }
        out.put("file", FilesSupport.fileBlock(name, mime, bytes));
        return DataSupport.ports(out);
    }

    /** Failure with every collected secret literal redacted. */
    static Map<String, Object> error(String message, List<String> secrets) {
        return DataSupport.fail(DataSupport.redact(message,
                secrets == null ? new String[0] : secrets.toArray(String[]::new)));
    }

    static Map<String, Object> error(String message) {
        return DataSupport.fail(message);
    }

    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Encode per the {@code encoding} param: hex | base64 | utf8. */
    static String encode(byte[] bytes, String encoding) {
        String e = encoding == null ? "hex" : encoding.trim().toLowerCase(Locale.ROOT);
        return switch (e) {
            case "base64", "b64" -> b64(bytes);
            case "utf8", "utf-8", "text" -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            default -> hex(bytes);
        };
    }

    /**
     * Decode hex/base64. {@code utf8} is treated as base64 — binary payloads
     * (IV-prefixed ciphertext) are never valid lossy UTF-8 text.
     */
    static byte[] decode(String text, String encoding) {
        String e = encoding == null ? "hex" : encoding.trim().toLowerCase(Locale.ROOT);
        String t = text == null ? "" : text.replaceAll("\\s+", "");
        try {
            return switch (e) {
                case "base64", "b64", "utf8", "utf-8", "text" -> Base64.getDecoder().decode(t);
                default -> HexFormat.of().parseHex(t);
            };
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Lower-cased config/input string or fallback; never null when fallback non-null. */
    static String lower(String s, String fallback) {
        String t = DataSupport.trimToNull(s);
        return t == null ? fallback : t.toLowerCase(Locale.ROOT);
    }

    static Map<String, Object> data() {
        return new LinkedHashMap<>();
    }
}
