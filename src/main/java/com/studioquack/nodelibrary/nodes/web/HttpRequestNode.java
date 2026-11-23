package com.studioquack.nodelibrary.nodes.web;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.CHROME_UA;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.hasHeader;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.helperClient;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.jsonObject;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.log;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.mutableMap;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.readConfig;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.readInput;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.send;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.str;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.stringHeaders;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.toInt;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.trim;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.utf8;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.verifyTls;

/**
 * Generic HTTP client (JDK {@link java.net.http.HttpClient}).
 *
 * <p>OUTPUT {@code output} is a map {@code {result, http_status?}} on 2xx.
 * Validation, transport, and non-2xx failures raise {@link StudioQuackError}.
 * Thread-safe. Never logs {@code helperToken} or {@code Authorization} values.
 */
public final class HttpRequestNode implements NodeHandler {

    /** Matches {@code nodes/http-request/node_definition.json} {@code _id}. */
    static final String DEFAULT_TEMPLATE_ID = "30f59c3b-afa1-463a-9a60-9cda6ccae790";

    /**
     * Response-body byte cap and preview-character cap (Python {@code SAFE_BODY_PREVIEW}).
     * Java caps the read (DoS bound); Python truncated after a full read.
     */
    static final int SAFE_BODY_PREVIEW = 200_000;

    /** Case-insensitive input is stored upper-case; anything else is rejected (no send). */
    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private static final String BROWSER_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,"
                    + "image/apng,*/*;q=0.8";
    private static final String BROWSER_ACCEPT_LANGUAGE = "en-US,en;q=0.9";

    private final String templateId;

    public HttpRequestNode() {
        this(DEFAULT_TEMPLATE_ID);
    }

    public HttpRequestNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? DEFAULT_TEMPLATE_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Success returns OUTPUT {@code output}. Failures raise {@link StudioQuackError}.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper _helper) {
        HelperClient logHelper = helperClient(request);
        try {
            return execute(request, logHelper);
        } catch (StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Class name only — messages can echo URLs or header names; never dump headers.
            log(logHelper, "http-request failed: " + e.getClass().getSimpleName());
            return DataSupport.fail("Error: " + e.getClass().getSimpleName() + ": " + exceptionMessage(e));
        }
    }

    private static Map<String, Object> execute(Map<String, Object> request, HelperClient logHelper) {
        Map<String, Object> input = readInput(request);
        Map<String, Object> config = readConfig(request);

        int timeoutSeconds = toInt(config.get("timeoutSecondsDefault"), 20);
        boolean tls = verifyTls(config.get("verifyTLSDefault"), true);

        String method = trim(input.get("method")).toUpperCase(Locale.ROOT);
        if (method.isEmpty()) {
            method = "GET";
        }

        Map<String, String> headers = mergeHeaders(config.get("defaultHeaders"), input.get("headers"));
        applyBrowserDefaults(headers);
        String[] secrets = credentialSecrets(headers);

        String url = trim(input.get("url"));
        if (url.isEmpty()) {
            return DataSupport.invalidInput("URL is missing");
        }
        if (!ALLOWED_METHODS.contains(method)) {
            return DataSupport.invalidInput("HTTP Request Error: method not allowed: " + method);
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(DataSupport.redact(
                    "HTTP Request Error: " + exceptionMessage(e), secrets));
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return DataSupport.invalidInput("HTTP Request Error: unsupported URL scheme: "
                    + (scheme == null || scheme.isBlank() ? "(none)" : scheme));
        }

        byte[] bodyBytes = resolveBody(input, headers);

        // Method + timeout only. Do not log URL (query may hold tokens) or headers.
        log(logHelper, "http-request " + method + " timeoutSeconds=" + timeoutSeconds);

        WebNodeSupport.HttpResult resp;
        try {
            resp = send(method, uri, headers, bodyBytes, timeoutSeconds, tls,
                    SAFE_BODY_PREVIEW, HttpClient.Redirect.NORMAL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DataSupport.fail(DataSupport.redact(
                    "HTTP Request Error: " + exceptionMessage(e), secrets));
        } catch (StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail(DataSupport.redact(
                    "HTTP Request Error: " + exceptionMessage(e), secrets));
        }

        if (resp.statusCode < 200 || resp.statusCode >= 300) {
            return DataSupport.fail(DataSupport.redact(
                    "HTTP " + resp.statusCode + " for " + method + " " + url, secrets));
        }

        String contentType = contentTypeOf(resp).toLowerCase(Locale.ROOT);
        String preview = bodyPreview(resp.body, resp.truncated);
        String resultText = "HTTP " + method + " " + url + "\n"
                + "Status: " + resp.statusCode + "\n"
                + "Content-Type: " + contentType + "\n\n"
                + "Body:\n" + preview;
        log(logHelper, "http-request done status=" + resp.statusCode);
        return resultOutput(resultText);
    }

    /**
     * {@code defaultHeaders} (JSON object string or map; invalid → {}) then call {@code headers}
     * on top (call wins).
     */
    private static Map<String, String> mergeHeaders(Object defaultHeaders, Object callHeaders) {
        Map<String, String> merged = stringHeaders(jsonObject(defaultHeaders));
        merged.putAll(stringHeaders(jsonObject(callHeaders)));
        return merged;
    }

    /**
     * When User-Agent is missing (case-insensitive), set Chrome/120 and, only in that case,
     * fill Accept / Accept-Language if those are also missing (Python behaviour).
     */
    private static void applyBrowserDefaults(Map<String, String> headers) {
        if (hasHeader(headers, "User-Agent")) {
            return;
        }
        headers.put("User-Agent", CHROME_UA);
        if (!hasHeader(headers, "Accept")) {
            headers.put("Accept", BROWSER_ACCEPT);
        }
        if (!hasHeader(headers, "Accept-Language")) {
            headers.put("Accept-Language", BROWSER_ACCEPT_LANGUAGE);
        }
    }

    /**
     * Body precedence (Python): {@code body} if dict → JSON;
     * {@code body_text} if str, else {@code body} if str, else {@code str(body)} when body
     * is non-null and not a dict. If both JSON and text resolve, JSON wins (one HttpClient body;
     * Python could pass both {@code json=} and {@code data=}).
     */
    private static byte[] resolveBody(Map<String, Object> input, Map<String, String> headers) {
        Object bodyField = input.get("body");
        Object bodyTextField = input.get("body_text");

        Map<String, Object> jsonBody = null;
        if (bodyField instanceof Map<?, ?>) {
            jsonBody = mutableMap(bodyField);
        }

        String dataBody = null;
        if (bodyTextField instanceof String s) {
            dataBody = s;
        } else if (bodyField instanceof String s) {
            dataBody = s;
        } else if (bodyField != null && !(bodyField instanceof Map<?, ?>)) {
            dataBody = str(bodyField);
        }

        if (jsonBody != null) {
            if (!hasHeader(headers, "Content-Type")) {
                headers.put("Content-Type", "application/json");
            }
            return utf8(Json.write(jsonBody));
        }
        if (dataBody != null) {
            return utf8(dataBody);
        }
        return null;
    }

    private static String contentTypeOf(WebNodeSupport.HttpResult resp) {
        return resp.header("Content-Type").orElse(resp.contentType == null ? "" : resp.contentType);
    }

    /**
     * Prefer compact JSON for object/array bodies; otherwise raw text. Cap at
     * {@link #SAFE_BODY_PREVIEW} chars and mark byte-capped reads as truncated.
     */
    private static String bodyPreview(String body, boolean truncated) {
        String preview;
        try {
            Object parsed = Json.parse(body);
            if (parsed instanceof Map<?, ?> || parsed instanceof List<?>) {
                preview = Json.write(parsed);
            } else {
                preview = body == null ? "" : body;
            }
        } catch (RuntimeException e) {
            preview = body == null ? "" : body;
        }
        if (preview.length() > SAFE_BODY_PREVIEW) {
            preview = preview.substring(0, SAFE_BODY_PREVIEW) + "... [truncated]";
        } else if (truncated && !preview.endsWith("... [truncated]")) {
            preview = preview + "... [truncated]";
        }
        return preview;
    }

    /** Port map: {@code output} → {@code {result, http_status?}} for 2xx only. */
    private static Map<String, Object> resultOutput(String resultText) {
        String text = resultText == null ? "" : resultText;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", text);
        Integer status = parseHttpStatusLine(text);
        if (status != null) {
            payload.put("http_status", status);
        }
        Map<String, Object> ports = new LinkedHashMap<>();
        ports.put("output", payload);
        return ports;
    }

    private static final Set<String> CREDENTIAL_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "x-api-key", "api-key", "x-auth-token", "x-access-token");

    private static String[] credentialSecrets(Map<String, String> headers) {
        List<String> secrets = new ArrayList<>();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null
                        && CREDENTIAL_HEADERS.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                    secrets.add(e.getValue());
                }
            }
        }
        return secrets.toArray(String[]::new);
    }

    /** Reads {@code Status: {code}} from a success preview ({@code execute} shape). */
    private static Integer parseHttpStatusLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (String line : text.split("\n", -1)) {
            if (!line.regionMatches(true, 0, "Status:", 0, "Status:".length())) {
                continue;
            }
            String rest = line.substring("Status:".length()).trim();
            if (rest.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(rest);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String exceptionMessage(Exception e) {
        if (e == null) {
            return "";
        }
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
