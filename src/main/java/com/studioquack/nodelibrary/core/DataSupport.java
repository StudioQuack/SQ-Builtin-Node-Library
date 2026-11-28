package com.studioquack.nodelibrary.core;

import com.studioquack.nodelibrary.nodes.testemulator.EmulatorConfig;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared invoke parsing and outbound HTTP for data/API nodes.
 *
 * <p>Thread-safe. Secrets must be passed in only to {@link #redact} — never logged.
 * Timeouts apply per request. Bodies are capped to bound memory.
 */
public final class DataSupport {

    /** Default User-Agent for public APIs that require one. */
    public static final String USER_AGENT = "StudioQuack-NodeLibrary/1.0";

    /** Cap response bodies so a runaway API cannot OOM the sandbox. */
    static final int MAX_BODY_CHARS = 2_000_000;

    /** Cap for multipart {@code url} fetches (matches FilesSupport's 8 MiB). */
    static final int MAX_FETCH_BYTES = 8 * 1024 * 1024;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private DataSupport() {
        // utility — never instantiated
    }

    /** Shared JDK client (no cookie store — safe across tenants). */
    public static HttpClient httpClient() {
        return CLIENT;
    }

    /**
     * Instance config keyed by {@code shortCode}. Source:
     * {@code executionContext.currentNode.data.configurationParameters[]}.
     */
    public static Map<String, Object> readConfig(Map<String, Object> request) {
        return EmulatorConfig.readNodeParams(request);
    }

    /** Declared {@code type} per config {@code shortCode} (same source as {@link #readConfig}). */
    public static Map<String, String> readConfigTypes(Map<String, Object> request) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (request == null) {
            return out;
        }
        Object list = EmulatorUtil.dig(request, "executionContext", "currentNode", "data", "configurationParameters");
        for (Object item : EmulatorUtil.asList(list)) {
            Map<String, Object> p = EmulatorUtil.asMap(item);
            String code = EmulatorUtil.str(p.get("shortCode"));
            String type = EmulatorUtil.str(p.get("type"));
            if (!code.isBlank() && !type.isBlank()) {
                out.put(code, type);
            }
        }
        return out;
    }

    /**
     * INPUT port {@code input} as a map. Prefers {@code inputVariables.input}, then the
     * helper's inputList, then {@code currentNode.inputList}. Unwraps a one-element list
     * (Python {@code getInputValuesByPortValue} shape). A JSON string is parsed.
     */
    public static Map<String, Object> readInput(Map<String, Object> request, StudioQuackHelper helper) {
        return readNamedPort(request, helper, "input");
    }

    /**
     * Same as {@link #readInput} for an arbitrary port value ({@code llm_input}, …).
     */
    public static Map<String, Object> readNamedPort(Map<String, Object> request, StudioQuackHelper helper,
                                                    String port) {
        String p = port == null || port.isBlank() ? "input" : port;
        Object raw = null;
        if (request != null) {
            raw = EmulatorUtil.asMap(request.get("inputVariables")).get(p);
        }
        if (raw == null && helper != null) {
            List<Object> vals = helper.getInputValuesByPortValue(p);
            if (vals != null && !vals.isEmpty()) {
                raw = vals.get(0);
            }
        }
        if (raw == null && request != null) {
            Object list = EmulatorUtil.dig(request, "executionContext", "currentNode", "inputList");
            for (Object item : EmulatorUtil.asList(list)) {
                Map<String, Object> row = EmulatorUtil.asMap(item);
                if (p.equals(EmulatorUtil.str(row.get("targetPortValue")))) {
                    raw = row.get("value");
                    break;
                }
            }
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            raw = list.get(0);
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.startsWith("{") || t.startsWith("[")) {
                try {
                    raw = Json.parse(t);
                } catch (Exception ignored) {
                    // keep the raw string; callers treat non-maps as empty
                }
            }
        }
        return EmulatorUtil.mutableMap(raw);
    }

    public static String cfgStr(Map<String, Object> cfg, String key, String fallback) {
        String v = trimToNull(EmulatorUtil.str(cfg == null ? null : cfg.get(key)));
        return v == null ? fallback : v;
    }

    public static int cfgInt(Map<String, Object> cfg, String key, int fallback) {
        if (cfg == null || cfg.get(key) == null || EmulatorUtil.str(cfg.get(key)).isBlank()) {
            return fallback;
        }
        int n = EmulatorUtil.toInt(cfg.get(key), fallback);
        return n > 0 ? n : fallback;
    }

    public static String inStr(Map<String, Object> in, String key, String fallback) {
        Object v = in == null ? null : in.get(key);
        if (v == null) {
            return fallback;
        }
        String s = EmulatorUtil.str(v).trim();
        return s.isEmpty() ? fallback : s;
    }

    /**
     * Copy {@code in} and fill missing keys from instance config. A wired INPUT
     * value wins. Blank string, empty list, and empty map count as missing so
     * the node form can supply a value the flow did not wire.
     */
    public static Map<String, Object> overlayConfig(Map<String, Object> in, Map<String, Object> cfg, String... keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (in != null) {
            out.putAll(in);
        }
        if (cfg == null || keys == null) {
            return out;
        }
        for (String key : keys) {
            if (key == null || key.isBlank() || !absent(out.get(key))) {
                continue;
            }
            Object fallback = cfg.get(key);
            if (!absent(fallback)) {
                out.put(key, fallback);
            }
        }
        return out;
    }

    private static boolean absent(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.isBlank();
        }
        if (v instanceof List<?> list) {
            return list.isEmpty();
        }
        if (v instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    /**
     * Wire form of a number. Integral {@link Double}/{@link Float} values
     * ({@code 1.0}) are written as {@code 1}. Fractional values stay as-is.
     */
    public static String integralText(Object v) {
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) <= 9_007_199_254_740_992d) {
                return Long.toString((long) d);
            }
        }
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** First non-blank among aliases (e.g. query / q). */
    public static String inStrAny(Map<String, Object> in, String fallback, String... keys) {
        if (keys != null) {
            for (String k : keys) {
                String s = inStr(in, k, null);
                if (s != null) {
                    return s;
                }
            }
        }
        return fallback;
    }

    public static int inInt(Map<String, Object> in, String key, int fallback) {
        if (in == null || in.get(key) == null || EmulatorUtil.str(in.get(key)).isBlank()) {
            return fallback;
        }
        return EmulatorUtil.toInt(in.get(key), fallback);
    }

    public static boolean inBool(Map<String, Object> in, String key, boolean fallback) {
        if (in == null || in.get(key) == null) {
            return fallback;
        }
        return EmulatorUtil.isTruthy(in.get(key));
    }

    /** JSON array, or a comma-separated string split into trimmed tokens. */
    public static List<String> inStringList(Map<String, Object> in, String key) {
        List<String> out = new ArrayList<>();
        if (in == null || in.get(key) == null) {
            return out;
        }
        Object v = in.get(key);
        if (v instanceof List<?> list) {
            for (Object item : list) {
                String s = trimToNull(EmulatorUtil.str(item));
                if (s != null) {
                    out.add(s);
                }
            }
            return out;
        }
        String raw = EmulatorUtil.str(v).trim();
        if (raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** OUTPUT-port map with the payload on {@code output}. */
    public static Map<String, Object> ports(Object output) {
        return ports("output", output);
    }

    /**
     * Port map with the payload on {@code key}. Blank key falls back to {@code output}
     * so unstructured / files / transform callers keep the historical contract.
     */
    public static Map<String, Object> ports(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key == null || key.isBlank() ? "output" : key, value);
        return m;
    }

    private static final Pattern HTTP_STATUS_IN_MESSAGE = Pattern.compile("\\bHTTP (\\d{3})\\b");

    /**
     * Raise a node failure as a BPMN error so the workflow's error boundary event catches it
     * (an {@code {error}} on the output port would count as success). A message carrying
     * {@code HTTP <status>} maps to {@link #httpErrorCode} with {@code http_status} data;
     * anything else is {@code REQUEST_FAILED}. Declared as {@code Map} only so
     * {@code return} / switch-arm call sites compile — it never returns.
     */
    public static Map<String, Object> fail(String message) {
        throw failError(message);
    }

    /** The {@link StudioQuackError} {@link #fail} raises, for {@code throw} sites. */
    public static StudioQuackError failError(String message) {
        String msg = message == null || message.isBlank() ? "request failed" : message;
        Matcher m = HTTP_STATUS_IN_MESSAGE.matcher(msg);
        if (m.find()) {
            int status = Integer.parseInt(m.group(1));
            if (status >= 400) {
                return new StudioQuackError(httpErrorCode(status), msg, Map.of("http_status", status));
            }
        }
        return new StudioQuackError("REQUEST_FAILED", msg);
    }

    /** Pre-request validation failure ({@code INVALID_INPUT}); same contract as {@link #fail}. */
    public static Map<String, Object> invalidInput(String message) {
        throw new StudioQuackError("INVALID_INPUT",
                message == null || message.isBlank() ? "invalid input" : message);
    }

    /**
     * Throw {@link StudioQuackError} with {@code errorCode} (blank → {@code NODE_ERROR}).
     * Message falls back to {@code request failed}.
     */
    public static StudioQuackError failure(String errorCode, String message) {
        String code = errorCode == null || errorCode.isBlank() ? "NODE_ERROR" : errorCode;
        String msg = message == null || message.isBlank() ? "request failed" : message;
        throw new StudioQuackError(code, msg);
    }

    /** Map an upstream HTTP status to a BPMN error code. */
    public static String httpErrorCode(int status) {
        if (status == 401 || status == 403) {
            return "AUTH_FAILED";
        }
        if (status == 429) {
            return "RATE_LIMITED";
        }
        return "HTTP_" + status;
    }

    /**
     * {@code HTTP <status>} plus a short provider detail when the body has one.
     * JSON {@code message} / {@code error} / {@code errors} / {@code info} win,
     * including a list of {@code {message}} objects (Crossref). Otherwise a trimmed
     * snippet so a bare status is not the only thing the caller sees.
     */
    public static String httpStatusMessage(int status, String body) {
        String detail = httpErrorDetail(body);
        if (detail.isEmpty() || detail.startsWith("HTTP ")) {
            return detail.isEmpty() ? "HTTP " + status : detail;
        }
        return "HTTP " + status + ": " + detail;
    }

    public static String httpErrorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        Object parsed = parseJson(body);
        if (parsed instanceof Map<?, ?> map) {
            String from = mapText(map);
            if (!from.isEmpty()) {
                return capDetail(from);
            }
        } else if (parsed instanceof String s && !s.isBlank()) {
            return capDetail(s.trim());
        }
        String flat = body.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (looksLikeHtmlErrorPage(body)) {
            String hint = body.contains("404")
                    ? " (not found — check path parameters such as vintage and dataset)"
                    : "";
            return capDetail("Provider returned an HTML error page" + hint);
        }
        return capDetail(flat);
    }

    private static boolean looksLikeHtmlErrorPage(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("<html") || lower.contains("<!doctype") || lower.contains("apache tomcat");
    }

    private static String mapText(Map<?, ?> map) {
        String fromErr = textValue(map.get("error"), 0);
        if (!fromErr.isEmpty()) {
            return fromErr;
        }
        String fromErrors = textValue(map.get("errors"), 0);
        if (!fromErrors.isEmpty()) {
            return fromErrors;
        }
        return firstText(map, 0, "message", "exception", "detail", "info", "title", "error_description");
    }

    private static String firstText(Map<?, ?> map, int depth, String... keys) {
        for (String key : keys) {
            String from = textValue(map.get(key), depth + 1);
            if (!from.isEmpty()) {
                return from;
            }
        }
        return "";
    }

    /** String, {@code {message|info|detail|title|description|code}} (plus JSON:API {@code source.pointer}), or a list of those. */
    private static String textValue(Object v, int depth) {
        if (v == null || depth > 4) {
            return "";
        }
        if (v instanceof String s) {
            return s.isBlank() ? "" : s.trim();
        }
        if (v instanceof Map<?, ?> nested) {
            String msg = firstText(nested, depth, "message", "info", "detail", "title", "description", "code");
            String pointer = extractSourcePointer(nested);
            if (!msg.isEmpty() && !pointer.isEmpty() && !msg.contains(pointer)) {
                return msg + " (" + pointer + ")";
            }
            return msg;
        }
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                String part = textValue(item, depth + 1);
                if (part.isEmpty()) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append("; ");
                }
                sb.append(part);
                if (sb.length() >= 240) {
                    break;
                }
            }
            return sb.toString();
        }
        return "";
    }

    /** Extract JSON:API {@code source.pointer} or {@code source.parameter} when present. */
    private static String extractSourcePointer(Map<?, ?> nested) {
        Object srcObj = nested.get("source");
        if (!(srcObj instanceof Map<?, ?> src)) {
            return "";
        }
        Object pointer = src.get("pointer");
        if (pointer instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        Object parameter = src.get("parameter");
        if (parameter instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return "";
    }

    private static String capDetail(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.length() > 240) {
            return flat.substring(0, 240);
        }
        return flat;
    }

    private static final Pattern CAL_BOOKING_REQUIRED_FIELD =
            Pattern.compile("\\{([a-zA-Z0-9_-]+)\\}error_required_field");

    /**
     * Cal.com returns an unrendered template for missing custom booking fields, e.g.
     * {@code responses - {notes}error_required_field}. Expand it for agents/operators.
     */
    public static String clarifyBitbucketAuthDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return detail;
        }
        String lower = detail.toLowerCase(Locale.ROOT);
        if (lower.contains("atlassian registered email") || lower.contains("api token must be used")) {
            return "Set node config email to the Atlassian account email that owns the API token "
                    + "(not the Bitbucket workspace username). Upstream: " + detail.trim();
        }
        return detail;
    }

    /** Bare 404 from api.atlassian.com/ex/jira|confluence usually means bad {@code cloud_id} or no site access. */
    public static String clarifyNewRelicAuthDetail(String detail, int httpStatus) {
        if (detail == null) {
            detail = "";
        }
        String trimmed = detail.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (httpStatus == 401 || (httpStatus == 403 && lower.contains("region"))) {
            if (trimmed.isEmpty() || lower.contains("authentication required") || lower.contains("incorrect region")) {
                return "Check instance config user_api_key (NerdGraph/User key in API-Key header, not Bearer) "
                        + "and data_region us vs eu for the account. Upstream: "
                        + (trimmed.isEmpty() ? "authentication required" : trimmed);
            }
        }
        return trimmed.isEmpty() ? detail : detail;
    }

    public static String clarifyAtlassianCloudHttpDetail(String detail, int httpStatus) {
        if (detail == null) {
            detail = "";
        }
        String trimmed = detail.trim();
        if (httpStatus == 404 && (trimmed.isEmpty()
                || "not found".equalsIgnoreCase(trimmed)
                || trimmed.toLowerCase(Locale.ROOT).startsWith("page not found"))) {
            return "Verify cloud_id (Atlassian Cloud site UUID) and that the API token account can access that site. "
                    + "Set cloud_id in instance configuration or on INPUT. "
                    + (trimmed.isEmpty() ? "Upstream returned an empty body." : "Upstream: " + trimmed);
        }
        return clarifyBitbucketAuthDetail(detail);
    }

    public static String clarifyInvoiceNinjaTierDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return detail;
        }
        String lower = detail.toLowerCase(Locale.ROOT);
        if (lower.contains("feature not available") && (lower.contains("free") || lower.contains("unpaid"))) {
            return "Invoice Ninja API requires a paid or qualifying trial account (hosted or licensed self-host). "
                    + "Upgrade the workspace or supply a paid API token. Upstream: " + detail.trim();
        }
        return detail;
    }

    public static String clarifyCalComBookingFieldDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return detail;
        }
        Matcher m = CAL_BOOKING_REQUIRED_FIELD.matcher(detail);
        if (m.find()) {
            String slug = m.group(1);
            return "bookingFieldsResponses." + slug
                    + " is required for this event type (Cal.com custom booking field; "
                    + "see get-event-type bookingFields). Upstream: " + detail.trim();
        }
        if (detail.contains("error_required_field")) {
            return "A bookingFieldsResponses value is required for this event type "
                    + "(use get-event-type to list booking field slugs). Upstream: " + detail.trim();
        }
        return detail;
    }

    /** Hostname, optional numeric port. No userinfo ({@code @}). */
    private static final Pattern HOST_SEGMENT = Pattern.compile(
            "[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::[0-9]{1,5})?");

    /** True when {@code value} can be a URL host without encoding. */
    public static boolean isHostLiteral(String value) {
        return value != null && HOST_SEGMENT.matcher(value).matches();
    }

    public static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Encode a multi-segment path. {@code /} stays a separator; each segment is encoded.
     * A slash inside one id must use {@link #urlEncode} instead.
     */
    public static String urlEncodeSegments(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String[] parts = s.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(urlEncode(parts[i]));
        }
        return sb.toString();
    }

    /**
     * RSS {@code host_and_path}: host + path + optional query, no scheme.
     * Slashes, {@code ?}, {@code &} and {@code =} stay structural. The host is
     * literal only when it is a hostname (optional port); {@code @} is encoded.
     */
    public static String urlEncodeHostAndPath(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String value = raw;
        String query = null;
        int q = value.indexOf('?');
        if (q >= 0) {
            query = value.substring(q + 1);
            value = value.substring(0, q);
        }
        int hash = value.indexOf('#');
        if (hash >= 0) {
            value = value.substring(0, hash);
        }
        String[] parts = value.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            String seg = parts[i];
            if (i == 0 && HOST_SEGMENT.matcher(seg).matches()) {
                sb.append(seg);
            } else {
                sb.append(urlEncode(seg));
            }
        }
        if (query != null) {
            sb.append('?').append(encodeQueryPreservingDelimiters(query));
        }
        return sb.toString();
    }

    private static String encodeQueryPreservingDelimiters(String query) {
        String[] pairs = query.split("&", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                sb.append('&');
            }
            String pair = pairs[i];
            int eq = pair.indexOf('=');
            if (eq < 0) {
                sb.append(urlEncode(pair));
            } else {
                sb.append(urlEncode(pair.substring(0, eq)));
                sb.append('=');
                sb.append(urlEncode(pair.substring(eq + 1)));
            }
        }
        return sb.toString();
    }

    public static String query(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        return sb.toString();
    }

    /**
     * HTTP call. {@code extraHeaders} may include Authorization — never log this object.
     * {@code client} null uses the shared no-cookie client. GET/HEAD never send a body;
     * DELETE sends a body only when {@code body} is non-empty (empty/null stays body-less
     * so existing DELETE callers are byte-identical).
     */
    public static HttpResp exchange(HttpClient client, String method, String url, Map<String, String> extraHeaders,
                                    String body, int timeoutSeconds) {
        byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        return exchangeBytes(client, method, url, extraHeaders, bytes, timeoutSeconds);
    }

    /**
     * Same as {@link #exchange} with a raw-byte entity (multipart / SigV4 payload
     * hashing). Never logs {@code extraHeaders} or {@code body}.
     */
    public static HttpResp exchangeBytes(HttpClient client, String method, String url,
                                         Map<String, String> extraHeaders, byte[] body, int timeoutSeconds) {
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : 25;
        HttpClient http = client == null ? CLIENT : client;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json");
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null
                            || e.getKey().isBlank() || e.getValue().isBlank()) {
                        continue;
                    }
                    // User-Agent / Accept already set; caller may override.
                    b.header(e.getKey(), e.getValue());
                }
            }
            String m = method == null ? "GET" : method.toUpperCase();
            boolean hasEntity = body != null && body.length > 0
                    && !"GET".equals(m) && !"HEAD".equals(m);
            if ("GET".equals(m)) {
                b.GET();
            } else if ("HEAD".equals(m)) {
                b.method("HEAD", HttpRequest.BodyPublishers.noBody());
            } else if ("DELETE".equals(m) && !hasEntity) {
                b.DELETE();
            } else {
                // POST/PUT/PATCH/DELETE-with-body plus WebDAV verbs.
                byte[] payload = body == null ? new byte[0] : body;
                String ct = extraHeaders != null && extraHeaders.get("Content-Type") != null
                        ? extraHeaders.get("Content-Type") : "application/json";
                if (extraHeaders == null || extraHeaders.get("Content-Type") == null) {
                    b.header("Content-Type", ct);
                }
                b.method(m, HttpRequest.BodyPublishers.ofByteArray(payload));
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String respBody = resp.body() == null ? "" : resp.body();
            if (respBody.length() > MAX_BODY_CHARS) {
                respBody = respBody.substring(0, MAX_BODY_CHARS);
            }
            return new HttpResp(resp.statusCode(), respBody, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HttpResp(-1, "", "request interrupted");
        } catch (IllegalArgumentException e) {
            return new HttpResp(-1, "", "invalid URL");
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return new HttpResp(-1, "", "HTTP transport failed" + transportFailureDetail(e));
        }
    }

    /**
     * Short, triage-friendly suffix for JDK client failures (DNS/TLS/connect/timeout).
     * Application HTTP errors never use this path.
     */
    public static String transportFailureDetail(Throwable e) {
        if (e == null) {
            return "";
        }
        if (e instanceof UnknownHostException) {
            return " (DNS: host not found)";
        }
        if (e instanceof SSLException) {
            return " (TLS handshake failed)";
        }
        if (e instanceof ConnectException) {
            return connectFailureDetail(e);
        }
        if (e instanceof HttpTimeoutException) {
            return " (request timeout)";
        }
        return " (" + e.getClass().getSimpleName() + ")";
    }

    /**
     * The JDK client wraps every connect failure in a {@link ConnectException}; the OS reason
     * survives only in the cause chain. Measured on JDK 21 (Linux and macOS): an unreachable
     * network carries "Network is unreachable", but a refused connect carries no text at all —
     * only a {@code ClosedChannelException} cause. A name that does not resolve (NXDOMAIN, or no
     * network to reach a resolver) is never an {@link UnknownHostException} here: it is a
     * message-less {@code ConnectException} caused by {@code UnresolvedAddressException}. Never
     * includes an address: the text reaches customer run logs.
     */
    private static String connectFailureDetail(Throwable e) {
        StringBuilder chain = new StringBuilder();
        Throwable t = e;
        for (int depth = 0; t != null && depth < 8; depth++, t = t.getCause()) {
            chain.append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append(' ');
        }
        String why = chain.toString().toLowerCase(Locale.ROOT);
        if (why.contains("unresolvedaddressexception")) {
            return " (DNS lookup failed: the host name did not resolve from this engine)";
        }
        if (why.contains("network is unreachable")) {
            return " (network unreachable: the host may be IPv6-only — engines reach the internet over IPv4"
                    + " — or this engine's internet access is off)";
        }
        if (why.contains("noroutetohost") || why.contains("no route to host") || why.contains("host is unreachable")) {
            return " (no route to host)";
        }
        if (why.contains("refused") || why.contains("closedchannelexception")) {
            return " (connection refused: the server, or a firewall in front of it, rejected this engine;"
                    + " if it allowlists caller IPs, add this engine's outbound IP)";
        }
        if (why.contains("timed out")) {
            return " (connection timed out)";
        }
        return " (connection refused or unreachable)";
    }

    /**
     * GET raw bytes for a multipart file part. Never logs the body. Caps at
     * {@link #MAX_FETCH_BYTES}; oversize and non-2xx become an error (no truncated file).
     */
    public static BytesResp fetchBytes(String url, int timeoutSeconds) {
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : 25;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body() == null ? new byte[0] : resp.body();
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return new BytesResp(new byte[0], "HTTP " + resp.statusCode());
            }
            if (body.length > MAX_FETCH_BYTES) {
                return new BytesResp(new byte[0], "file too large");
            }
            return new BytesResp(body, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BytesResp(new byte[0], "request interrupted");
        } catch (IllegalArgumentException e) {
            return new BytesResp(new byte[0], "invalid URL");
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return new BytesResp(new byte[0], "HTTP transport failed" + transportFailureDetail(e));
        }
    }

    public static HttpResp get(String url, Map<String, String> headers, int timeoutSeconds) {
        return exchange(null, "GET", url, headers, null, timeoutSeconds);
    }

    public static HttpResp post(String url, Map<String, String> headers, String body, int timeoutSeconds) {
        return exchange(null, "POST", url, headers, body, timeoutSeconds);
    }

    public static HttpResp put(String url, Map<String, String> headers, String body, int timeoutSeconds) {
        return exchange(null, "PUT", url, headers, body, timeoutSeconds);
    }

    /**
     * Parse a JSON object body. Strips Google-style XSSI prefix {@code )]}'} if present.
     * Returns {@code null} when the root is not an object.
     */
    public static Map<String, Object> parseObject(String body) {
        Object v = parseJson(body);
        return v instanceof Map<?, ?> ? Json.asMap(v) : null;
    }

    public static Object parseJson(String body) {
        if (body == null) {
            return null;
        }
        String s = body.trim();
        int brace = indexOfJsonStart(s);
        if (brace > 0) {
            s = s.substring(brace);
        }
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Json.parse(s);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    static int indexOfJsonStart(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') {
                return i;
            }
        }
        return 0;
    }

    /** Replace known secret literals in an error string. Never include the secret in logs. */
    public static String redact(String message, String... secrets) {
        String out = message == null ? "request failed" : message;
        if (secrets != null) {
            for (String secret : secrets) {
                if (secret != null && secret.length() >= 4) {
                    out = out.replace(secret, "***");
                }
            }
        }
        return out;
    }

    public static boolean httpOk(int status) {
        return status >= 200 && status < 300;
    }

    /** HTTP result. {@code error} is set only on transport failure (status then -1). */
    public record HttpResp(int status, String body, String error) {
        public boolean ok() {
            return error == null && httpOk(status);
        }

        public Map<String, Object> jsonObject() {
            return parseObject(body);
        }
    }

    /** Raw-byte GET. {@code error} is set on transport / non-2xx / oversize; body is never logged. */
    public record BytesResp(byte[] body, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    public static Map<String, String> headers(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        if (kv == null) {
            return m;
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i] != null && kv[i + 1] != null) {
                m.put(kv[i], kv[i + 1]);
            }
        }
        return m;
    }

    public static String templateId(String envKey, String fallback) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return Objects.requireNonNullElse(fallback, "");
    }
}
