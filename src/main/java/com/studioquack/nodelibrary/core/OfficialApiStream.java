package com.studioquack.nodelibrary.core;

import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Spec-driven streaming execution for {@link OfficialApiNode}.
 *
 * <p>Decision / URL / body planning is pure ({@link #enabled}, {@link #applyRequestFlag},
 * {@link #boolish}) so tests do not stub {@link DataSupport#exchange}. Request bodies for
 * stream-capable operations are translated by {@link ProviderRequestMapper} in
 * {@link OfficialApiNode} before the flag is forced. HTTP uses a
 * dedicated HTTP/1.1 client (SSE line-reads stay well-defined).
 */
public final class OfficialApiStream {

    /** HTTP/1.1 so {@code readLine} on SSE is not HTTP/2 DATA-frame sliced. */
    private static final HttpClient STREAM_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private OfficialApiStream() {
        // Utility — never instantiated.
    }

    /**
     * Stream when the op declares a known stream block and the effective pref is on.
     * Input {@code stream}/{@code streaming} overrides config; default ON.
     * {@code llmInput} is consulted after {@code input} (v2 LLM payload lives there).
     */
    static boolean enabled(OfficialApiSpec.StreamBlock block, Map<String, Object> input,
                           Map<String, Object> llmInput, Map<String, Object> cfg) {
        if (block == null) {
            return false;
        }
        Boolean v = firstBoolish(input, "stream", "streaming");
        if (v != null) {
            return v;
        }
        v = firstBoolish(llmInput, "stream", "streaming");
        if (v != null) {
            return v;
        }
        v = firstBoolish(cfg, "stream", "streaming");
        if (v != null) {
            return v;
        }
        return true;
    }

    /** Force {@code requestFlag=requestValue} into a JSON object body, independent of bodyFrom. */
    static Map<String, Object> applyRequestFlag(Map<String, Object> body, OfficialApiSpec.StreamBlock block) {
        Map<String, Object> out = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        if (block == null) {
            return out;
        }
        String flag = block.requestFlag == null || block.requestFlag.isBlank() ? "stream" : block.requestFlag;
        Object value = block.requestValue == null ? Boolean.TRUE : block.requestValue;
        out.put(flag, value);
        return out;
    }

    /**
     * Python/{@code NodeParams.boolish}: true/false/unknown. {@code stream}/{@code streaming}
     * count as true so a stringly designer value still enables SSE.
     */
    static Boolean boolish(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        if (s.equals("1") || s.equals("true") || s.equals("yes") || s.equals("on")
                || s.equals("stream") || s.equals("streaming")) {
            return true;
        }
        if (s.equals("0") || s.equals("false") || s.equals("no") || s.equals("off")
                || s.equals("nonstream") || s.equals("non-stream") || s.equals("sync")) {
            return false;
        }
        return null;
    }

    static Boolean firstBoolish(Map<String, Object> m, String... keys) {
        if (m == null || keys == null) {
            return null;
        }
        for (String k : keys) {
            if (m.containsKey(k)) {
                Boolean parsed = boolish(m.get(k));
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    /** Overlay {@code llm_input} onto the generic {@code input} map. llm_input keys win. */
    static Map<String, Object> mergeInput(Map<String, Object> input, Map<String, Object> llmInput) {
        if (llmInput == null || llmInput.isEmpty()) {
            return input == null ? new LinkedHashMap<>() : input;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (input != null) {
            out.putAll(input);
        }
        out.putAll(llmInput);
        return out;
    }

    static Map<String, Object> execute(OfficialApiSpec.StreamBlock stream, String method, String url,
                                       Map<String, String> headers, String body, int timeoutSeconds,
                                       StudioQuackHelper helper) {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        return execute(stream, method, url, headers, bytes, timeoutSeconds, helper);
    }

    static Map<String, Object> execute(OfficialApiSpec.StreamBlock stream, String method, String url,
                                       Map<String, String> headers, byte[] body, int timeoutSeconds,
                                       StudioQuackHelper helper) {
        String port = stream == null || stream.port == null || stream.port.isBlank()
                ? "llm_output" : stream.port;
        String format = stream == null ? "" : stream.format;
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : 25;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", DataSupport.USER_AGENT)
                    .header("Accept", "text/event-stream");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null
                            || e.getKey().isBlank() || e.getValue().isBlank()) {
                        continue;
                    }
                    b.header(e.getKey(), e.getValue());
                }
            }
            String m = method == null ? "POST" : method.toUpperCase(Locale.ROOT);
            byte[] payload = body == null ? new byte[0] : body;
            b.method(m, HttpRequest.BodyPublishers.ofByteArray(payload));
            HttpResponse<InputStream> resp = STREAM_CLIENT.send(b.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                String err = utf8(resp.body());
                throw new StudioQuackError(DataSupport.httpErrorCode(resp.statusCode()),
                        httpError(resp.statusCode(), err));
            }
            String ct = resp.headers().firstValue("content-type").orElse("");
            StreamCollector collector = ProviderStream.collector(format, port, helper);
            if (collector == null) {
                throw new StudioQuackError("REQUEST_FAILED", "unknown stream format");
            }
            try (InputStream in = resp.body()) {
                ProviderStream.consume(collector, format, in, ct, DataSupport.MAX_BODY_CHARS);
            }
            return LlmAssistant.ports(port, collector.toAssistant());
        } catch (StudioQuackError e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StudioQuackError("REQUEST_FAILED", "request interrupted");
        } catch (IllegalArgumentException e) {
            throw new StudioQuackError("REQUEST_FAILED", "invalid URL");
        } catch (Exception e) {
            throw new StudioQuackError("REQUEST_FAILED", "HTTP transport failed" + DataSupport.transportFailureDetail(e));
        }
    }

    static String httpError(int status, String body) {
        return LlmAssistant.redact(DataSupport.httpStatusMessage(status, body));
    }

    static String withQuery(String url, Map<String, String> extra) {
        if (url == null) {
            return "";
        }
        String qs = DataSupport.query(extra);
        if (qs.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + qs;
    }

    static Map<String, Object> parseBodyObject(String json) {
        Object parsed = DataSupport.parseJson(json);
        if (parsed instanceof Map<?, ?>) {
            return new LinkedHashMap<>(ProviderStream.asMap(parsed));
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    static String writeBody(Map<String, Object> body) {
        return Json.write(body == null ? Map.of() : body);
    }

    private static String utf8(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return "";
        }
    }
}
