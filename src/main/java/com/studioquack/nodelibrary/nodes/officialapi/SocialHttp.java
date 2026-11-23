package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import studioquack.Json;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * JDK 21 {@link HttpClient} wrapper for social-tier nodes. Zero extra Maven deps.
 *
 * <p>Thread-safe (shared client). Connect + request timeouts are honored per call.
 * Error messages never include query strings or {@code Authorization} values so
 * API keys (YouTube {@code key=}), Page tokens, Reddit/Bluesky/X secrets cannot
 * leak into {@code output.error}.
 */
public final class SocialHttp {

    /** Hard cap against unbounded third-party payloads (DoS). */
    static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

    private static final Pattern SECRET_QUERY = Pattern.compile(
            "(?i)(key|access_token|password|token|bearer|jwt|secret)=[^&\\s]*");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private SocialHttp() {
        // Utility — never instantiated.
    }

    /**
     * HTTP failure with status code. {@link #getMessage()} is already sanitized.
     */
    public static final class ApiException extends Exception {
        private final int status;

        public ApiException(int status, String message) {
            super(message == null ? "" : message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** GET JSON object. Non-object 2xx bodies become {@code {"raw": "&lt;text&gt;"}}. */
    public static Map<String, Object> getJson(String url, Map<String, String> query,
                                              Map<String, String> headers, int timeoutSeconds)
            throws ApiException {
        return exchange("GET", buildUri(url, query), headers, null, null, timeoutSeconds);
    }

    /** POST JSON body. */
    public static Map<String, Object> postJson(String url, Map<String, String> query,
                                               Map<String, String> headers, Object jsonBody,
                                               int timeoutSeconds) throws ApiException {
        String body = Json.write(jsonBody == null ? Map.of() : jsonBody);
        Map<String, String> h = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        h.putIfAbsent("Content-Type", "application/json");
        return exchange("POST", buildUri(url, query), h, body, "application/json", timeoutSeconds);
    }

    /** POST {@code application/x-www-form-urlencoded} (Reddit token). */
    public static Map<String, Object> postForm(String url, Map<String, String> headers,
                                               Map<String, String> form, int timeoutSeconds)
            throws ApiException {
        String body = formEncode(form);
        Map<String, String> h = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        h.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
        return exchange("POST", buildUri(url, null), h, body, "application/x-www-form-urlencoded",
                timeoutSeconds);
    }

    /** HTTP Basic {@code client_id:client_secret} (Reddit OAuth). Never log the result. */
    public static String basicAuthHeader(String user, String password) {
        String raw = (user == null ? "" : user) + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static URI buildUri(String base, Map<String, String> query) {
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        if (query == null || query.isEmpty()) {
            return URI.create(base);
        }
        StringBuilder sb = new StringBuilder(base);
        boolean hasQuery = base.indexOf('?') >= 0;
        sb.append(hasQuery ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return URI.create(sb.toString());
    }

    static String formEncode(Map<String, String> form) {
        if (form == null || form.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String s = SECRET_QUERY.matcher(message).replaceAll("$1=***");
        // Drop absolute URLs that might still carry a redacted query.
        s = s.replaceAll("https?://\\S+", "[url]");
        return s;
    }

    private static Map<String, Object> exchange(String method, URI uri, Map<String, String> headers,
                                                String body, String contentType, int timeoutSeconds)
            throws ApiException {
        int timeout = Math.max(1, timeoutSeconds);
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(timeout));
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getKey().isBlank()) {
                    continue;
                }
                b.header(e.getKey(), e.getValue());
            }
        }
        if ("POST".equals(method)) {
            boolean hasCt = headers != null && headers.keySet().stream()
                    .anyMatch(k -> k != null && "content-type".equalsIgnoreCase(k));
            if (!hasCt) {
                b.header("Content-Type", contentType == null ? "application/json" : contentType);
            }
            b.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
        } else {
            b.GET();
        }
        HttpResponse<byte[]> resp;
        try {
            resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "HTTP interrupted");
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(0, sanitize(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        byte[] bytes = resp.body() == null ? new byte[0] : resp.body();
        if (bytes.length > MAX_BODY_BYTES) {
            throw new ApiException(resp.statusCode(), "response too large (" + bytes.length + " bytes)");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        int status = resp.statusCode();
        if (status >= 400) {
            throw new ApiException(status, apiErrorMessage(status, text));
        }
        return parseBody(text, status);
    }

    /**
     * Prefer the provider's JSON {@code error.message} (YouTube, Graph, X) so the
     * caller sees a useful string without the request URL.
     */
    static String apiErrorMessage(int status, String text) {
        String extracted = extractErrorMessage(text);
        if (!extracted.isBlank()) {
            return "HTTP " + status + ": " + sanitize(extracted);
        }
        return "HTTP " + status;
    }

    static String extractErrorMessage(String text) {
        return DataSupport.httpErrorDetail(text);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseBody(String text, int status) {
        if (text == null || text.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = Json.parse(text);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("raw", parsed);
            wrap.put("status_code", status);
            return wrap;
        } catch (RuntimeException e) {
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("raw", text);
            wrap.put("status_code", status);
            return wrap;
        }
    }

    /** Blocking sleep used for 429 backoff (matches the Python retry loops). */
    public static void sleepSeconds(int seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
