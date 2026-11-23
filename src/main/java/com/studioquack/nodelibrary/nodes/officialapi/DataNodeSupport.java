package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeCatalog;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorConfig;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import com.studioquack.nodelibrary.nodes.testemulator.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared request/config/HTTP helpers for the data/API nodes.
 *
 * <p>Thread-safe: the {@link HttpClient} is shared; every other method is
 * stateless. Never log {@code helperToken}, PATs, or API keys.
 */
public final class DataNodeSupport {

    /** Same product UA the Python nodes send when they set User-Agent. */
    public static final String USER_AGENT = "StudioQuack/1.0";

    /** Response-body cap (DoS bound); matches the dispatcher invoke-body cap. */
    public static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private DataNodeSupport() {
        // utility — never instantiated
    }

    /**
     * Resolve {@code SQ_TEMPLATE_ID_<KEY>} (folder uppercased, dashes → underscores)
     * with a fallback to the bundled definition {@code _id}.
     */
    public static String templateId(String nodeKey, String defaultId) {
        String env = System.getenv(NodeCatalog.envName(nodeKey));
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return defaultId;
    }

    /** Instance configurationParameters (shortCode → value). Never log password values. */
    public static Map<String, Object> configParams(Map<String, Object> request) {
        return EmulatorConfig.readNodeParams(request);
    }

    /**
     * INPUT port {@code input}: {@code inputVariables.input}, else
     * {@code currentNode.inputList[]} with {@code targetPortValue=input}.
     * A list-wrapped value is unwrapped to the first element.
     */
    public static Map<String, Object> inputPort(Map<String, Object> request) {
        if (request == null) {
            return Map.of();
        }
        Object raw = EmulatorUtil.asMap(request.get("inputVariables")).get("input");
        if (raw == null) {
            Object list = EmulatorUtil.dig(request, "executionContext", "currentNode", "inputList");
            for (Object item : EmulatorUtil.asList(list)) {
                Map<String, Object> row = EmulatorUtil.asMap(item);
                if ("input".equals(EmulatorUtil.str(row.get("targetPortValue")))) {
                    raw = row.get("value");
                    break;
                }
            }
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            raw = list.get(0);
        }
        return EmulatorUtil.mutableMap(raw);
    }

    /** Per-request helper logs. Fire-and-forget. Never logs the bearer. */
    public static HelperClient logs(Map<String, Object> request) {
        String token = request == null ? "" : EmulatorUtil.str(request.get("helperToken"));
        return new HelperClient(token);
    }

    public static String str(Object o) {
        return EmulatorUtil.str(o);
    }

    /** First non-blank string among {@code keys} (in order). Empty when all missing. */
    public static String firstNonBlank(Map<String, Object> in, String... keys) {
        if (in == null || keys == null) {
            return "";
        }
        for (String k : keys) {
            String v = str(in.get(k)).trim();
            if (!v.isEmpty() && !"null".equals(v)) {
                return v;
            }
        }
        return "";
    }

    public static int toInt(Object o, int defaultValue) {
        return EmulatorUtil.toInt(o, defaultValue);
    }

    /** Positive int or {@code fallback} when missing / not positive. */
    public static int positiveInt(Object o, int fallback) {
        int n = toInt(o, fallback);
        return n > 0 ? n : fallback;
    }

    /** Python-style truthiness for optional query params (skip empty/0/false/null). */
    public static boolean present(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        if (o instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (o instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        String s = String.valueOf(o).trim();
        return !s.isEmpty() && !"null".equalsIgnoreCase(s) && !"false".equalsIgnoreCase(s);
    }

    /** OUTPUT-port map with a single {@code output} object (these nodes have no error port). */
    public static Map<String, Object> outputPorts(Map<String, Object> output) {
        Map<String, Object> ports = new LinkedHashMap<>();
        ports.put("output", output == null ? Map.of() : output);
        return ports;
    }



    /** Path segment (owner, repo, arXiv id). */
    public static String pathSegment(String raw) {
        if (raw == null) {
            return "";
        }
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * HTTP GET. {@code timeoutSeconds} is the request timeout (connect+read).
     * Does not throw for HTTP 4xx/5xx — caller inspects {@link HttpResult#status}.
     *
     * @throws DataHttpException on timeout, interrupt, or transport failure
     */
    public static HttpResult get(String url, Map<String, String> headers, Map<String, ?> query,
                                 int timeoutSeconds) {
        String full = withQuery(url, query);
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : 25;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(full))
                    .GET()
                    .timeout(Duration.ofSeconds(timeout));
            if (headers != null) {
                for (var e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null && !e.getValue().isBlank()) {
                        b.header(e.getKey(), e.getValue());
                    }
                }
            }
            HttpResponse<byte[]> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] raw = resp.body() == null ? new byte[0] : resp.body();
            if (raw.length > MAX_BODY_BYTES) {
                throw new DataHttpException("response body exceeds " + MAX_BODY_BYTES + " bytes",
                        resp.statusCode(), false);
            }
            String body = new String(raw, StandardCharsets.UTF_8);
            Map<String, String> hdrs = new LinkedHashMap<>();
            resp.headers().map().forEach((k, v) -> {
                if (k != null && v != null && !v.isEmpty()) {
                    hdrs.put(k.toLowerCase(Locale.ROOT), v.get(0));
                }
            });
            return new HttpResult(resp.statusCode(), body, full, hdrs);
        } catch (DataHttpException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new DataHttpException("timed out after " + timeout + "s", null, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataHttpException("interrupted", null, false);
        } catch (IOException | IllegalArgumentException e) {
            String msg = e.getMessage();
            throw new DataHttpException(msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg,
                    null, false);
        }
    }

    public static Object parseJson(String body) {
        return Json.parse(body);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object o) {
        return o instanceof List<?> list ? (List<Object>) list : List.of();
    }

    public static String truncate(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxChars ? s : s.substring(0, maxChars);
    }

    /** Collapse whitespace (arXiv title/abstract). */
    public static String collapseWs(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ");
    }

    /** Split a designer string or JSON list into trimmed non-empty tokens. */
    public static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String t = str(item).trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
            return out;
        }
        String s = str(raw).trim();
        if (s.isEmpty()) {
            return out;
        }
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String withQuery(String base, Map<String, ?> query) {
        if (base == null || base.isBlank()) {
            throw new DataHttpException("missing URL", null, false);
        }
        if (query == null || query.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        boolean hasQuery = base.contains("?");
        boolean firstPair = true;
        for (var e : query.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (firstPair && !hasQuery) {
                sb.append('?');
            } else {
                sb.append('&');
            }
            firstPair = false;
            String raw = DataSupport.integralText(e.getValue());
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(raw, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** GET result. {@link #header} is case-insensitive. */
    public static final class HttpResult {
        public final int status;
        public final String body;
        public final String url;
        private final Map<String, String> headers;

        HttpResult(int status, String body, String url, Map<String, String> headers) {
            this.status = status;
            this.body = body == null ? "" : body;
            this.url = url;
            this.headers = headers == null ? Map.of() : headers;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public String header(String name) {
            if (name == null) {
                return "";
            }
            String v = headers.get(name.toLowerCase(Locale.ROOT));
            return v == null ? "" : v;
        }
    }

    /** Transport or timeout failure. HTTP 4xx/5xx are not this — they stay on {@link HttpResult}. */
    public static final class DataHttpException extends RuntimeException {
        public final Integer status;
        public final boolean timeout;

        public DataHttpException(String message, Integer status, boolean timeout) {
            super(message);
            this.status = status;
            this.timeout = timeout;
        }
    }
}
