package com.studioquack.nodelibrary.nodes.web;

import com.studioquack.nodelibrary.nodes.testemulator.EmulatorConfig;
import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import studioquack.Json;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared invoke parsing and JDK {@link HttpClient} exchange for web-tier nodes.
 *
 * <p>Thread-safe. Never logs {@code helperToken}, API keys, or Authorization headers.
 */
public final class WebNodeSupport {

    /** Chrome/120 UA copied from the Python web-browser / http-request tools. */
    public static final String CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";

    private WebNodeSupport() {
        // Utility — never instantiated.
    }

    /**
     * INPUT port {@code input}: {@code inputVariables.input}, else
     * {@code executionContext.currentNode.inputList[]} with {@code targetPortValue=input}.
     * A one-element list (helper {@code getInputValuesByPortValue} shape) is unwrapped.
     */
    public static Map<String, Object> readInput(Map<String, Object> invokeRequest) {
        Object raw = asMap(invokeRequest == null ? null : invokeRequest.get("inputVariables")).get("input");
        if (raw == null) {
            raw = findInputListValue(invokeRequest);
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            raw = list.get(0);
        }
        if (raw instanceof String s && looksLikeJsonObject(s)) {
            try {
                raw = Json.readObject(s);
            } catch (RuntimeException ignored) {
                // keep the raw string; callers treat non-maps as empty input
            }
        }
        return mutableMap(raw);
    }

    /** Instance {@code configurationParameters[]} keyed by {@code shortCode}. */
    public static Map<String, Object> readConfig(Map<String, Object> invokeRequest) {
        return EmulatorConfig.readNodeParams(invokeRequest);
    }

    /** Helper-channel logs. Bearer is taken from the invoke body and never logged. */
    public static HelperClient helperClient(Map<String, Object> invokeRequest) {
        String token = invokeRequest == null ? "" : str(invokeRequest.get("helperToken"));
        return new HelperClient(token.isBlank() ? null : token);
    }

    public static void log(HelperClient helper, String message) {
        if (helper == null) {
            return;
        }
        helper.log(message == null ? "" : message);
    }

    public static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static String trim(Object o) {
        return str(o).trim();
    }

    /** Number or numeric string → int; otherwise {@code defaultValue}. */
    public static int toInt(Object o, int defaultValue) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        String s = trim(o);
        if (s.isEmpty()) {
            return defaultValue;
        }
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Boolean-like config: {@code true}/{@code 1}/{@code yes} vs {@code false}/{@code 0}/{@code no}.
     * Unknown / blank → {@code defaultValue}.
     */
    public static boolean asBool(Object o, boolean defaultValue) {
        if (o instanceof Boolean b) {
            return b;
        }
        String s = trim(o).toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return defaultValue;
        }
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) {
            return false;
        }
        return defaultValue;
    }

    /** TLS verify flag: only the string {@code false} disables verification (Python http-request). */
    public static boolean verifyTls(Object o, boolean defaultValue) {
        if (o == null) {
            return defaultValue;
        }
        return !"false".equalsIgnoreCase(trim(o));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public static Map<String, Object> mutableMap(Object o) {
        return new LinkedHashMap<>(asMap(o));
    }

    /** Parse a JSON object string or accept an already-decoded map. Invalid JSON → empty map. */
    public static Map<String, Object> jsonObject(Object raw) {
        if (raw instanceof Map<?, ?>) {
            return mutableMap(raw);
        }
        String s = trim(raw);
        if (s.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(Json.readObject(s));
        } catch (RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * One HTTP exchange. Connect + request timeouts both use {@code timeoutSeconds}.
     * Body is capped at {@code maxBodyBytes} (DoS bound). Caller maps exceptions to output ports.
     */
    public static HttpResult send(String method, URI uri, Map<String, String> headers, byte[] body,
                                  int timeoutSeconds, boolean verifyTls, int maxBodyBytes,
                                  HttpClient.Redirect redirect) throws Exception {
        int timeout = Math.max(1, timeoutSeconds);
        Duration dur = Duration.ofSeconds(timeout);
        HttpRequest.Builder rb = HttpRequest.newBuilder(uri).timeout(dur);
        HttpRequest.BodyPublisher pub = (body == null || body.length == 0)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        rb.method(method, pub);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                    continue;
                }
                try {
                    // JDK forbids Host/Content-Length/Connection/etc. Skip rather than fail the node.
                    rb.header(e.getKey(), e.getValue());
                } catch (IllegalArgumentException ignored) {
                    // restricted header — omit
                }
            }
        }
        HttpClient.Builder cb = HttpClient.newBuilder()
                .connectTimeout(dur)
                .followRedirects(redirect == null ? HttpClient.Redirect.NORMAL : redirect);
        if (!verifyTls) {
            cb.sslContext(insecureSslContext());
            SSLParameters params = new SSLParameters();
            params.setEndpointIdentificationAlgorithm("");
            cb.sslParameters(params);
        }
        HttpRequest request = rb.build();
        try (HttpClient client = cb.build()) {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int cap = maxBodyBytes > 0 ? maxBodyBytes : 2_000_000;
            LimitedBody limited = readLimited(response.body(), cap);
            Charset charset = charsetOf(response.headers().firstValue("Content-Type").orElse(""));
            String text = new String(limited.bytes, charset);
            Map<String, String> respHeaders = new LinkedHashMap<>();
            response.headers().map().forEach((k, vals) -> {
                if (k != null && vals != null && !vals.isEmpty()) {
                    respHeaders.put(k, vals.get(0));
                }
            });
            URI finalUri = response.uri();
            return new HttpResult(
                    response.statusCode(),
                    finalUri == null ? uri.toString() : finalUri.toString(),
                    text,
                    response.headers().firstValue("Content-Type").orElse(""),
                    respHeaders,
                    limited.truncated);
        }
    }

    public static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    public static boolean hasHeader(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return false;
        }
        for (String k : headers.keySet()) {
            if (k != null && k.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, String> stringHeaders(Map<String, Object> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                continue;
            }
            out.put(e.getKey(), str(e.getValue()));
        }
        return out;
    }

    private static Object findInputListValue(Map<String, Object> invokeRequest) {
        Object cur = invokeRequest;
        for (String k : new String[] {"executionContext", "currentNode", "inputList"}) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(k);
        }
        if (!(cur instanceof List<?> list)) {
            return null;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            if ("input".equals(str(row.get("targetPortValue")))) {
                return row.get("value");
            }
        }
        return null;
    }

    private static boolean looksLikeJsonObject(String s) {
        String t = s.trim();
        return t.startsWith("{") && t.endsWith("}");
    }

    private static LimitedBody readLimited(InputStream in, int maxBytes) throws Exception {
        if (in == null) {
            return new LimitedBody(new byte[0], false);
        }
        try (InputStream stream = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            boolean truncated = false;
            int n;
            while ((n = stream.read(chunk)) >= 0) {
                if (n == 0) {
                    continue;
                }
                int room = maxBytes - buf.size();
                if (room <= 0) {
                    truncated = true;
                    break;
                }
                int take = Math.min(n, room);
                buf.write(chunk, 0, take);
                if (take < n) {
                    truncated = true;
                    break;
                }
            }
            return new LimitedBody(buf.toByteArray(), truncated);
        }
    }

    private static Charset charsetOf(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        int i = lower.indexOf("charset=");
        if (i < 0) {
            return StandardCharsets.UTF_8;
        }
        String raw = contentType.substring(i + 8).trim();
        int sc = raw.indexOf(';');
        if (sc >= 0) {
            raw = raw.substring(0, sc).trim();
        }
        raw = raw.replace("\"", "");
        try {
            return Charset.forName(raw);
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    /** Trust-all context used only when the instance sets verifyTLSDefault=false. */
    private static SSLContext insecureSslContext() throws Exception {
        TrustManager[] managers = {new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] c, String a) {
                // lab-only: verifyTLSDefault=false
            }

            @Override
            public void checkServerTrusted(X509Certificate[] c, String a) {
                // lab-only: verifyTLSDefault=false
            }

            @Override
            public void checkClientTrusted(X509Certificate[] c, String a, java.net.Socket s) {
                // lab-only
            }

            @Override
            public void checkServerTrusted(X509Certificate[] c, String a, java.net.Socket s) {
                // lab-only
            }

            @Override
            public void checkClientTrusted(X509Certificate[] c, String a, javax.net.ssl.SSLEngine e) {
                // lab-only
            }

            @Override
            public void checkServerTrusted(X509Certificate[] c, String a, javax.net.ssl.SSLEngine e) {
                // lab-only
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, managers, new SecureRandom());
        return ctx;
    }

    /** Outcome of {@link #send}. */
    public static final class HttpResult {
        public final int statusCode;
        public final String url;
        public final String body;
        public final String contentType;
        public final Map<String, String> headers;
        public final boolean truncated;

        public HttpResult(int statusCode, String url, String body, String contentType,
                          Map<String, String> headers, boolean truncated) {
            this.statusCode = statusCode;
            this.url = url == null ? "" : url;
            this.body = body == null ? "" : body;
            this.contentType = contentType == null ? "" : contentType;
            this.headers = headers == null ? Map.of() : headers;
            this.truncated = truncated;
        }

        public Optional<String> header(String name) {
            if (name == null) {
                return Optional.empty();
            }
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    return Optional.ofNullable(e.getValue());
                }
            }
            return Optional.empty();
        }
    }

    private record LimitedBody(byte[] bytes, boolean truncated) {
    }
}
