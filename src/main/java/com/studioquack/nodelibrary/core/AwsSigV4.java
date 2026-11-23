package com.studioquack.nodelibrary.core;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * AWS Signature Version 4 (zero dependencies). Never logs credentials, headers,
 * or the payload — callers pass secrets in and receive an {@code Authorization}
 * string plus the {@code x-amz-*} headers to attach.
 *
 * <p>Two layers:
 * <ul>
 *   <li>{@link #authorization} / {@link #canonicalRequest} / {@link #stringToSign}
 *       match the published test-suite fixtures (headers you supply are the ones
 *       that get signed — used by {@code get-vanilla}).</li>
 *   <li>{@link #sign} is the production path: it stamps {@code x-amz-date},
 *       {@code x-amz-content-sha256}, optional {@code x-amz-security-token},
 *       then {@code Authorization}, mutating {@code headers} in place.</li>
 * </ul>
 */
public final class AwsSigV4 {

    /** SHA-256 of the empty payload; GET/HEAD and empty POST use this. */
    public static final String EMPTY_PAYLOAD_HASH =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String HMAC = "HmacSHA256";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";

    private AwsSigV4() {
        // Utility — never instantiated.
    }

    /**
     * Production signing. Mutates {@code headers} with {@code x-amz-date},
     * {@code x-amz-content-sha256}, optional {@code x-amz-security-token}, and
     * {@code Authorization}. Host is taken from {@code uri} (the HTTP client
     * will send the same Host). Returns an error string or {@code null}.
     *
     * <p>Signed headers are {@code host}, {@code content-type} when present,
     * and every {@code x-amz-*} already on the map after stamping. User-Agent
     * and Accept stay unsigned so the JDK client can add them later.
     */
    public static String sign(Map<String, String> headers, String method, URI uri, byte[] body,
                              String accessKey, String secretKey, String region, String service,
                              String sessionToken, Instant now) {
        if (headers == null || uri == null) {
            return "sigv4 request is incomplete";
        }
        if (blank(accessKey) || blank(secretKey) || blank(region) || blank(service)) {
            return "sigv4 credentials, region and service are required";
        }
        Instant ts = now == null ? Instant.now() : now;
        byte[] payload = body == null ? new byte[0] : body;
        String payloadHash = hexSha256(payload);
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", AMZ_DATE.format(ts));
        if (!blank(sessionToken)) {
            headers.put("x-amz-security-token", sessionToken);
        }
        Map<String, String> signed = signingHeaders(headers, uri);
        String auth = authorization(method, uri, signed, payloadHash, accessKey, secretKey,
                region, service, ts);
        headers.put("Authorization", auth);
        return null;
    }

    /**
     * Build the Authorization header for an already-canonicalised header set.
     * {@code signedHeaders} must include {@code host} (lowercase names). Used
     * by the test-suite vectors which do not auto-add {@code x-amz-content-sha256}.
     */
    public static String authorization(String method, URI uri, Map<String, String> signedHeaders,
                                       String payloadHash, String accessKey, String secretKey,
                                       String region, String service, Instant now) {
        String creq = canonicalRequest(method, uri, signedHeaders, payloadHash);
        String sts = stringToSign(now, region, service, creq);
        String dateStamp = DATE_STAMP.format(now);
        byte[] key = signingKey(secretKey, dateStamp, region, service);
        String sig = hex(hmac(key, sts));
        String signedNames = signedHeaderNames(signedHeaders);
        return ALGORITHM + " Credential=" + accessKey + "/" + dateStamp + "/" + region + "/"
                + service + "/aws4_request, SignedHeaders=" + signedNames + ", Signature=" + sig;
    }

    public static String canonicalRequest(String method, URI uri, Map<String, String> signedHeaders,
                                          String payloadHash) {
        String m = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        String hash = payloadHash == null || payloadHash.isBlank() ? EMPTY_PAYLOAD_HASH : payloadHash;
        return m + "\n"
                + canonicalUri(uri) + "\n"
                + canonicalQuery(uri) + "\n"
                + canonicalHeaders(signedHeaders) + "\n"
                + signedHeaderNames(signedHeaders) + "\n"
                + hash;
    }

    public static String stringToSign(Instant now, String region, String service, String canonicalRequest) {
        String dateStamp = DATE_STAMP.format(now);
        String scope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        return ALGORITHM + "\n"
                + AMZ_DATE.format(now) + "\n"
                + scope + "\n"
                + hexSha256(canonicalRequest.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] signingKey(String secretKey, String dateStamp, String region, String service) {
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, service);
        return hmac(kService, "aws4_request");
    }

    public static String hexSha256(byte[] payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(payload == null ? new byte[0] : payload));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return EMPTY_PAYLOAD_HASH;
        }
    }

    public static String amzDate(Instant now) {
        return AMZ_DATE.format(now == null ? Instant.now() : now);
    }

    static String canonicalUri(URI uri) {
        if (uri == null) {
            return "/";
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }
        StringBuilder sb = new StringBuilder();
        // Encode each segment; keep slashes. Decode first so %20 is not double-encoded.
        String[] parts = path.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(uriEncode(parts[i], false));
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    static String canonicalQuery(URI uri) {
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isEmpty()) {
            return "";
        }
        List<String[]> pairs = new ArrayList<>();
        for (String part : uri.getRawQuery().split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            String v = eq < 0 ? "" : part.substring(eq + 1);
            pairs.add(new String[]{uriEncode(urlDecode(k), true), uriEncode(urlDecode(v), true)});
        }
        pairs.sort((a, b) -> {
            int c = a[0].compareTo(b[0]);
            return c != 0 ? c : a[1].compareTo(b[1]);
        });
        StringBuilder sb = new StringBuilder();
        for (String[] p : pairs) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(p[0]).append('=').append(p[1]);
        }
        return sb.toString();
    }

    static String canonicalHeaders(Map<String, String> headers) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (headers != null) {
            for (var e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String name = e.getKey().toLowerCase(Locale.ROOT).trim();
                if (name.isEmpty()) {
                    continue;
                }
                sorted.put(name, collapseSpace(e.getValue().trim()));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (var e : sorted.entrySet()) {
            sb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    static String signedHeaderNames(Map<String, String> headers) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (headers != null) {
            for (var e : headers.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String name = e.getKey().toLowerCase(Locale.ROOT).trim();
                if (!name.isEmpty()) {
                    sorted.put(name, "");
                }
            }
        }
        return String.join(";", sorted.keySet());
    }

    /**
     * Host from the URI plus every content-type / x-amz-* already on {@code headers}.
     * Authorization is excluded (it is the output).
     */
    static Map<String, String> signingHeaders(Map<String, String> headers, URI uri) {
        Map<String, String> signed = new TreeMap<>();
        signed.put("host", hostHeader(uri));
        if (headers == null) {
            return signed;
        }
        for (var e : headers.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String name = e.getKey().toLowerCase(Locale.ROOT).trim();
            if ("content-type".equals(name) || name.startsWith("x-amz-")) {
                signed.put(name, e.getValue());
            }
        }
        return signed;
    }

    static String hostHeader(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port > 0) {
            boolean def = ("https".equalsIgnoreCase(uri.getScheme()) && port == 443)
                    || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80);
            if (!def) {
                return host + ":" + port;
            }
        }
        return host;
    }

    /**
     * RFC 3986 as required by SigV4: unreserved stay, space is {@code %20},
     * hex is uppercase. {@code encodeSlash} true encodes {@code /}.
     */
    static String uriEncode(String raw, boolean encodeSlash) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            int c = b & 0xff;
            if (isUnreserved(c) || (c == '/' && !encodeSlash)) {
                sb.append((char) c);
            } else {
                sb.append('%');
                sb.append(HEX.charAt(c >>> 4));
                sb.append(HEX.charAt(c & 0x0f));
            }
        }
        return sb.toString();
    }

    private static boolean isUnreserved(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '.' || c == '_' || c == '~';
    }

    private static String urlDecode(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        byte[] in = s.getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = new byte[in.length];
        int n = 0;
        for (int i = 0; i < in.length; i++) {
            if (in[i] == '%' && i + 2 < in.length) {
                int hi = hexVal(in[i + 1]);
                int lo = hexVal(in[i + 2]);
                if (hi >= 0 && lo >= 0) {
                    out[n++] = (byte) ((hi << 4) + lo);
                    i += 2;
                    continue;
                }
            }
            if (in[i] == '+') {
                out[n++] = ' ';
            } else {
                out[n++] = in[i];
            }
        }
        return new String(out, 0, n, StandardCharsets.UTF_8);
    }

    private static int hexVal(byte c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static String collapseSpace(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean prevSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                if (!prevSpace) {
                    sb.append(' ');
                }
                prevSpace = true;
            } else {
                sb.append(c);
                prevSpace = false;
            }
        }
        return sb.toString();
    }

    private static byte[] hmac(byte[] key, String data) {
        return hmac(key, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(data);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return new byte[32];
        }
    }

    private static final String HEX = "0123456789ABCDEF";
    private static final char[] HEX_LC = "0123456789abcdef".toCharArray();

    static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX_LC[v >>> 4];
            out[i * 2 + 1] = HEX_LC[v & 0x0f];
        }
        return new String(out);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
