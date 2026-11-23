package com.studioquack.nodelibrary.core;

import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spec-driven LLM stream formats. Factory + SSE/NDJSON framing; each format is a
 * {@link StreamCollector} that turns one payload chunk into {@code (channel, delta)}
 * pieces and an assembled v2 assistant result.
 *
 * <p>{@code emitPartial} is best-effort and never throws into the node.
 */
public final class ProviderStream {

    public static final Set<String> FORMATS = Set.of(
            "openai-sse", "anthropic-sse", "gemini-sse", "ollama-ndjson", "cohere-sse",
            "responses-sse");

    private ProviderStream() {
        // Utility — never instantiated.
    }

    public static boolean knownFormat(String format) {
        return format != null && FORMATS.contains(format.toLowerCase(Locale.ROOT));
    }

    public static StreamCollector collector(String format, String port, StudioQuackHelper helper) {
        String f = format == null ? "" : format.toLowerCase(Locale.ROOT);
        String p = port == null || port.isBlank() ? "llm_output" : port;
        return switch (f) {
            case "openai-sse" -> new OpenAiSseCollector(p, helper);
            case "anthropic-sse" -> new AnthropicSseCollector(p, helper);
            case "gemini-sse" -> new GeminiSseCollector(p, helper);
            case "ollama-ndjson" -> new OllamaNdjsonCollector(p, helper);
            case "cohere-sse" -> new CohereSseCollector(p, helper);
            case "responses-sse" -> new ResponsesSseCollector(p, helper);
            default -> null;
        };
    }

    /**
     * Read a provider body into {@code collector}. When {@code contentType} is not
     * an event-stream (SSE formats) — or a non-ndjson JSON envelope for Ollama —
     * the whole body is ingested as one envelope (provider ignored {@code stream=true}).
     */
    public static void consume(StreamCollector collector, String format, InputStream in,
                               String contentType, int maxBodyChars) throws IOException {
        if (collector == null || in == null) {
            return;
        }
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        boolean ndjson = format != null && format.toLowerCase(Locale.ROOT).endsWith("-ndjson");
        boolean envelope = ndjson
                ? ct.contains("json") && !ct.contains("ndjson") && !ct.contains("stream")
                : !ct.contains("event-stream");
        if (envelope) {
            String raw = utf8(in, maxBodyChars);
            Object parsed = DataSupport.parseJson(raw);
            collector.ingest(null, parsed);
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            if (ndjson) {
                readNdjson(collector, reader);
            } else {
                readSse(collector, reader);
            }
        }
    }

    static void readSse(StreamCollector collector, BufferedReader reader) throws IOException {
        String event = "";
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                dispatchData(collector, event, data.toString());
                event = "";
                data.setLength(0);
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }
            if (line.regionMatches(true, 0, "event:", 0, 6)) {
                event = line.substring(6).trim();
                continue;
            }
            if (line.regionMatches(true, 0, "data:", 0, 5)) {
                String rest = line.length() > 5 && line.charAt(5) == ' '
                        ? line.substring(6) : line.substring(5);
                if ("[DONE]".equals(rest.trim())) {
                    return;
                }
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(rest);
            }
        }
        if (data.length() > 0) {
            dispatchData(collector, event, data.toString());
        }
    }

    static void readNdjson(StreamCollector collector, BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            Object parsed = parseChunk(line);
            if (parsed != null) {
                collector.ingest(null, parsed);
            }
        }
    }

    static void dispatchData(StreamCollector collector, String event, String raw) {
        if (raw == null) {
            return;
        }
        String t = raw.trim();
        if (t.isEmpty() || "[DONE]".equalsIgnoreCase(t)) {
            return;
        }
        if (t.regionMatches(true, 0, "data:", 0, 5)) {
            t = t.substring(5).trim();
        }
        if ("[DONE]".equalsIgnoreCase(t)) {
            return;
        }
        Object parsed = parseChunk(t);
        if (parsed == null) {
            return;
        }
        collector.ingest(event == null ? "" : event, parsed);
    }

    static Object parseChunk(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Json.parse(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Best-effort port-tagged emit. Never throws. Empty deltas are skipped
     * (openai-models {@code OpenAiSse.emit} contract).
     */
    static void emit(StudioQuackHelper helper, String port, String channel, String delta) {
        if (helper == null || delta == null || delta.isEmpty()) {
            return;
        }
        try {
            helper.emitPartial(port, channel, delta);
        } catch (Exception ignored) {
            // SDK: best-effort, never throws past the node
        }
    }

    static List<StreamCollector.Piece> emitAndRecord(StudioQuackHelper helper, String port,
                                                     String channel, String delta,
                                                     List<StreamCollector.Piece> sink) {
        if (delta == null || delta.isEmpty()) {
            return sink;
        }
        sink.add(new StreamCollector.Piece(channel, delta));
        emit(helper, port, channel, delta);
        return sink;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) {
        if (o instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static String trim(Object o) {
        return str(o).trim();
    }

    static Object dig(Map<String, Object> root, String... keys) {
        Object cur = root;
        for (String k : keys) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(k);
        }
        return cur;
    }

    static String uuid24() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    static String uuid8() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    static Object parseJsonArgs(String args) {
        if (args == null || args.isBlank()) {
            return Map.of();
        }
        try {
            return Json.parse(args);
        } catch (RuntimeException e) {
            Map<String, Object> bad = new LinkedHashMap<>();
            bad.put("_raw_args", args);
            bad.put("_parse_error", "Invalid JSON from model");
            return bad;
        }
    }

    private static String utf8(InputStream in, int maxChars) throws IOException {
        byte[] bytes = in.readAllBytes();
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (maxChars > 0 && s.length() > maxChars) {
            return s.substring(0, maxChars);
        }
        return s;
    }
}
