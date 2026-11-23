package com.studioquack.nodelibrary.nodes.openai;

import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat Completions HTTP: SSE stream and non-stream fast path.
 *
 * <p>SSE {@code data:} lines are JSON chunks. Reasoning/thinking deltas fan out
 * via port-tagged {@code helper.emitPartial("llm_output", "thinking", delta)}; visible
 * text via {@code emitPartial("llm_output", "answer", delta)} — the daemon graph-routes
 * each frame along this node's own output-port edges. Best-effort — never throws into
 * the node. Request-scoped buffers only (no mutable statics).
 */
public final class OpenAiSse {

    private OpenAiSse() {
        // Utility — never instantiated.
    }

    /**
     * POST {@code /v1/chat/completions} with {@code stream:true}. On HTTP 400
     * mentioning {@code stream_options}, retry without that field (Python SDK TypeError path).
     * {@link HttpRequest#timeout} waits for headers only; body {@code readLine}
     * is bounded by {@code stream_idle_timeout_seconds} (watchdog closes the stream).
     */
    static Map<String, Object> stream(Map<String, Object> cfg, String apiKey, StudioQuackHelper helper)
            throws Exception {
        return stream(cfg, apiKey, helper, OpenAiConfig.REQUEST_TIMEOUT, OpenAiConfig.STREAM_IDLE_TIMEOUT);
    }

    static Map<String, Object> stream(Map<String, Object> cfg, String apiKey, StudioQuackHelper helper,
                                      Duration readTimeout, Duration idleTimeout)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(cfg);
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        String model = NodeParams.str(cfg.get("model"));
        long t0 = System.nanoTime();
        HttpResponse<InputStream> resp = postStream(body, apiKey, readTimeout, model, t0);
        if (resp.statusCode() == 400) {
            String err = utf8(resp.body());
            if (err.toLowerCase(Locale.ROOT).contains("stream_options")) {
                body.remove("stream_options");
                resp = postStream(body, apiKey, readTimeout, model, t0);
            } else {
                throw new OpenAiConfig.ApiException(400, OpenAiChat.openaiErrorMessage(400, err), err);
            }
        }
        if (resp.statusCode() >= 400) {
            String err = utf8(resp.body());
            throw new OpenAiConfig.ApiException(resp.statusCode(),
                    OpenAiChat.openaiErrorMessage(resp.statusCode(), err), err);
        }
        String ct = resp.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        Collector acc = new Collector();
        if (!ct.contains("event-stream")) {
            // Provider ignored stream=true and returned a JSON envelope.
            Object parsed = NodeParams.parseJson(utf8(resp.body()));
            acc.ingest(parsed, null);
            OpenAiConfig.timing("openai.nonstream", t0, "model=" + cfg.get("model"));
            return acc.toAssistant();
        }
        AtomicBoolean stalled = new AtomicBoolean();
        try (InputStream watched = OpenAiIdleStream.watch(resp.body(), idleTimeout, stalled);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(watched, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder data = new StringBuilder();
            int chunks = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (dispatchData(data.toString(), acc, helper)) {
                        chunks++;
                    }
                    data.setLength(0);
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("data:")) {
                    String rest = line.length() > 5 && line.charAt(5) == ' ' ? line.substring(6) : line.substring(5);
                    if ("[DONE]".equals(rest.trim())) {
                        break;
                    }
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(rest);
                }
            }
            if (data.length() > 0) {
                dispatchData(data.toString(), acc, helper);
            }
            OpenAiConfig.timing("openai.stream.consume", t0, "model=" + cfg.get("model") + " chunks=" + chunks);
        } catch (java.io.IOException e) {
            if (stalled.get() || e instanceof OpenAiIdleStream.StreamStalledException) {
                throw new OpenAiConfig.TimeoutException(OpenAiConfig.timeoutMessage(
                        model, "/chat/completions", OpenAiConfig.elapsedSeconds(t0), "stream stalled"), e);
            }
            throw e;
        }
        if (stalled.get()) {
            throw new OpenAiConfig.TimeoutException(OpenAiConfig.timeoutMessage(
                    model, "/chat/completions", OpenAiConfig.elapsedSeconds(t0), "stream stalled"));
        }
        return acc.toAssistant();
    }

    /** Non-stream Chat Completions (python {@code _nonstream_chat_completion}). */
    static Map<String, Object> nonStream(Map<String, Object> cfg, String apiKey) throws Exception {
        return nonStream(cfg, apiKey, OpenAiConfig.REQUEST_TIMEOUT);
    }

    static Map<String, Object> nonStream(Map<String, Object> cfg, String apiKey, Duration readTimeout)
            throws Exception {
        long t0 = System.nanoTime();
        String model = NodeParams.str(cfg.get("model"));
        Map<String, Object> resp = OpenAiConfig.postJson(
                "/chat/completions", apiKey, cfg, readTimeout, model);
        Collector acc = new Collector();
        acc.ingest(resp, null);
        OpenAiConfig.timing("openai.nonstream", t0, "model=" + cfg.get("model"));
        return acc.toAssistant();
    }

    /**
     * Parse one SSE {@code data:} payload. {@code [DONE]} ends the stream (returns false).
     * Malformed JSON is skipped so a truncated last line cannot fail the node.
     */
    static boolean dispatchData(String raw, Collector acc, StudioQuackHelper helper) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.isEmpty() || "[DONE]".equalsIgnoreCase(t)) {
            return false;
        }
        if (t.startsWith("data:")) {
            t = t.substring(5).trim();
        }
        if ("[DONE]".equalsIgnoreCase(t)) {
            return false;
        }
        Object parsed = NodeParams.parseJson(t);
        if (parsed == null) {
            return false;
        }
        acc.ingest(parsed, helper);
        return true;
    }

    private static HttpResponse<InputStream> postStream(Map<String, Object> body, String apiKey,
                                                         Duration readTimeout, String model, long startedNanos)
            throws Exception {
        String json = Json.write(body);
        HttpRequest req = OpenAiConfig.authed("/chat/completions", apiKey, readTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        try {
            return OpenAiConfig.HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.net.http.HttpTimeoutException e) {
            throw OpenAiConfig.wrapHttpTimeout(e, model, "/chat/completions", startedNanos);
        }
    }

    private static String utf8(InputStream in) throws java.io.IOException {
        if (in == null) {
            return "";
        }
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    static void emit(StudioQuackHelper helper, String channel, String delta) {
        if (helper == null || delta == null || delta.isEmpty()) {
            return;
        }
        try {
            // Tag with this node's own output port so the daemon routes the frame along that
            // port's edges (Duckbot end included); untagged frames never leave the daemon.
            helper.emitPartial("llm_output", channel, delta);
        } catch (Exception ignored) {
            // SDK: best-effort, never throws past the node
        }
    }

    /** Request-scoped SSE/JSON accumulator (python text_buf / tool_call_buf / audio_buf). */
    static final class Collector {
        final StringBuilder text = new StringBuilder();
        final Map<Integer, ToolAcc> tools = new TreeMap<>();
        final StringBuilder audioData = new StringBuilder();
        final StringBuilder audioTranscript = new StringBuilder();
        final List<Map<String, Object>> liveThoughts = new ArrayList<>();
        /** Lifts inline {@code <think>…</think>} markup (and template-stripped orphan closers) out of the answer. */
        final com.studioquack.nodelibrary.core.ThinkTagSplitter tagSplitter =
                new com.studioquack.nodelibrary.core.ThinkTagSplitter();
        String audioFormat = "wav";
        String finishReason;
        Map<String, Object> usage;

        void ingest(Object chunk, StudioQuackHelper helper) {
            Map<String, Object> map = NodeParams.asMap(chunk);
            if (map.isEmpty()) {
                return;
            }
            takeUsage(map.get("usage"));
            List<Object> choices = NodeParams.asList(map.get("choices"));
            if (choices.isEmpty()) {
                ingestMessage(map, helper);
                return;
            }
            Map<String, Object> choice = NodeParams.asMap(choices.get(0));
            String fr = NodeParams.trim(choice.get("finish_reason"));
            if (!fr.isEmpty() && !"null".equals(fr)) {
                finishReason = fr;
            }
            if (choice.get("delta") instanceof Map<?, ?>) {
                ingestDelta(NodeParams.asMap(choice.get("delta")), helper);
            }
            if (choice.get("message") instanceof Map<?, ?>) {
                ingestMessage(NodeParams.asMap(choice.get("message")), helper);
            }
            if (choice.get("text") != null) {
                appendAnswer(NodeParams.str(choice.get("text")), helper);
            }
        }

        private void ingestDelta(Map<String, Object> delta, StudioQuackHelper helper) {
            fanThinking(delta, helper);
            splitContent(delta.get("content"), helper);
            for (Object tc : NodeParams.asList(delta.get("tool_calls"))) {
                absorbTool(NodeParams.asMap(tc), false);
            }
            Map<String, Object> audio = NodeParams.asMap(delta.get("audio"));
            if (!audio.isEmpty()) {
                if (audio.get("data") != null) {
                    audioData.append(NodeParams.str(audio.get("data")));
                }
                if (audio.get("transcript") != null) {
                    audioTranscript.append(NodeParams.str(audio.get("transcript")));
                }
                if (audio.get("format") != null) {
                    audioFormat = NodeParams.str(audio.get("format"));
                }
            }
        }

        private void ingestMessage(Map<String, Object> msg, StudioQuackHelper helper) {
            fanThinking(msg, helper);
            splitContent(msg.get("content"), helper);
            int idx = 0;
            for (Object tc : NodeParams.asList(msg.get("tool_calls"))) {
                Map<String, Object> m = new LinkedHashMap<>(NodeParams.asMap(tc));
                m.putIfAbsent("index", idx);
                absorbTool(m, true);
                idx++;
            }
        }

        /**
         * thinking channel: {@code reasoning}, {@code reasoning_content}, {@code thinking},
         * nested {@code reasoning.summary[].text}, or content parts typed thinking/reasoning.
         */
        private void fanThinking(Map<String, Object> src, StudioQuackHelper helper) {
            String think = thinkingText(src.get("reasoning"), src.get("reasoning_content"),
                    src.get("thinking"), src.get("thoughts"));
            if (!think.isEmpty()) {
                liveThoughts.add(Map.of("type", "thinking", "text", think));
                emit(helper, "thinking", think);
            }
        }

        private void splitContent(Object content, StudioQuackHelper helper) {
            if (content == null) {
                return;
            }
            if (content instanceof String s) {
                appendAnswer(s, helper);
                return;
            }
            if (content instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s) {
                        appendAnswer(s, helper);
                        continue;
                    }
                    Map<String, Object> m = NodeParams.asMap(item);
                    String type = NodeParams.trim(m.get("type")).toLowerCase(Locale.ROOT);
                    // Recursive unwrap: Mistral-style ThinkChunk carries thinking as a
                    // nested LIST of text chunks, which a plain str() would drop.
                    String piece = thinkingText(m.get("text"), m.get("thinking"), m.get("content"));
                    if (type.contains("reason") || type.contains("think")) {
                        if (!piece.isEmpty()) {
                            liveThoughts.add(Map.of("type", "thinking", "text", piece));
                            emit(helper, "thinking", piece);
                        }
                    } else {
                        appendAnswer(piece, helper);
                    }
                }
                return;
            }
            appendAnswer(NodeParams.str(content), helper);
        }

        private void appendAnswer(String piece, StudioQuackHelper helper) {
            if (piece == null || piece.isEmpty()) {
                return;
            }
            for (com.studioquack.nodelibrary.core.ThinkTagSplitter.Segment seg : tagSplitter.feed(piece)) {
                applySegment(seg, helper);
            }
        }

        /** Stream end: resolve any held tag tail so no text is stranded in the splitter. */
        void flush(StudioQuackHelper helper) {
            for (com.studioquack.nodelibrary.core.ThinkTagSplitter.Segment seg : tagSplitter.flush()) {
                applySegment(seg, helper);
            }
        }

        private void applySegment(com.studioquack.nodelibrary.core.ThinkTagSplitter.Segment seg,
                                  StudioQuackHelper helper) {
            if (com.studioquack.nodelibrary.core.ThinkTagSplitter.THINKING.equals(seg.channel())) {
                liveThoughts.add(Map.of("type", "thinking", "text", seg.text()));
                emit(helper, "thinking", seg.text());
            } else {
                text.append(seg.text());
                emit(helper, "answer", seg.text());
            }
        }

        private void absorbTool(Map<String, Object> tc, boolean replaceArgs) {
            if (tc.isEmpty()) {
                return;
            }
            int idx = 0;
            Object i = tc.get("index");
            if (i instanceof Number n) {
                idx = n.intValue();
            }
            ToolAcc entry = tools.computeIfAbsent(idx, k -> new ToolAcc());
            if (tc.get("id") != null) {
                entry.id = NodeParams.str(tc.get("id"));
            }
            Map<String, Object> fn = NodeParams.asMap(tc.get("function"));
            if (!fn.isEmpty()) {
                if (fn.get("name") != null) {
                    entry.name = NodeParams.str(fn.get("name"));
                }
                if (fn.get("arguments") != null) {
                    String a = NodeParams.str(fn.get("arguments"));
                    entry.args = replaceArgs ? a : entry.args + a;
                }
            } else {
                if (tc.get("name") != null) {
                    entry.name = NodeParams.str(tc.get("name"));
                }
                Object a = tc.get("args") != null ? tc.get("args") : tc.get("arguments");
                if (a != null) {
                    String s = a instanceof String str ? str : Json.write(a);
                    entry.args = replaceArgs ? s : entry.args + s;
                }
            }
        }

        private void takeUsage(Object u) {
            Map<String, Object> map = NodeParams.asMap(u);
            if (map.isEmpty()) {
                return;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            for (String k : List.of("prompt_tokens", "completion_tokens", "total_tokens")) {
                if (map.get(k) != null) {
                    out.put(k, map.get(k));
                }
            }
            Map<String, Object> details = NodeParams.asMap(map.get("completion_tokens_details"));
            Object reason = details.get("reasoning_tokens");
            if (reason instanceof Number n && n.longValue() > 0) {
                out.put("reasoning_tokens", n);
            } else if (reason != null && !(reason instanceof Number)
                    && !NodeParams.trim(reason).isEmpty() && !"0".equals(NodeParams.trim(reason))) {
                out.put("reasoning_tokens", reason);
            }
            usage = out;
        }

        Map<String, Object> toAssistant() {
            flush(null); // envelope/one-shot path: resolve any held tag tail
            String body = text.toString();
            if (liveThoughts.isEmpty()) {
                // Streamed missing-opener repair (SGLang/vLLM pre-fill the opening tag
                // into the prompt): reasoning rode the answer channel — split at the
                // orphan closer now.
                String[] orphan = com.studioquack.nodelibrary.core.ThinkTagSplitter.splitOrphanedCloser(body);
                if (orphan != null) {
                    if (!orphan[0].isBlank()) {
                        liveThoughts.add(Map.of("type", "thinking", "text", orphan[0]));
                    }
                    body = orphan[1];
                }
            }
            List<String> textParts = new ArrayList<>();
            if (!body.isEmpty()) {
                textParts.add(body);
            }
            List<Map<String, Object>> toolCalls = materialize();
            // Token-usage metadata stays in the usage map for programmatic consumers; it is
            // never synthesized into the human-readable thinking stream.
            List<Map<String, Object>> thoughts = new ArrayList<>(liveThoughts);
            List<Map<String, Object>> files = new ArrayList<>();
            if (audioData.length() > 0) {
                String fmt = audioFormat == null || audioFormat.isBlank() ? "wav" : audioFormat;
                Map<String, Object> file = new LinkedHashMap<>();
                file.put("type", "audio");
                file.put("mime_type", "audio/" + fmt);
                file.put("source", "base64");
                file.put("data", audioData.toString());
                file.put("name", "out_" + NodeParams.uuid8() + "." + fmt);
                files.add(file);
                if (audioTranscript.length() > 0 && textParts.isEmpty()) {
                    textParts.add(audioTranscript.toString());
                }
            }
            Map<String, Object> response = OpenAiChat.makeResponse(
                    textParts, toolCalls.isEmpty() ? null : toolCalls,
                    thoughts.isEmpty() ? null : thoughts,
                    files.isEmpty() ? null : files, finishReason, usage);
            boolean empty = String.join("", textParts).isBlank() && toolCalls.isEmpty() && files.isEmpty();
            if (empty && finishReason != null && !"stop".equals(finishReason)) {
                if ("length".equals(finishReason)) {
                    response.put("content", List.of(Map.of("type", "text",
                            "text", "[System: Response stopped due to length limit. Consider increasing max_completion_tokens.]")));
                } else if ("content_filter".equals(finishReason)) {
                    return OpenAiChat.errorResponse("Response blocked by content filter.", finishReason);
                } else {
                    response.put("content", List.of(Map.of("type", "text",
                            "text", "[System: Response stopped due to " + finishReason + "]")));
                }
            }
            return response;
        }

        private List<Map<String, Object>> materialize() {
            List<Map<String, Object>> out = new ArrayList<>();
            for (ToolAcc entry : tools.values()) {
                String args = entry.args == null || entry.args.isEmpty() ? "{}" : entry.args;
                Object parsed;
                try {
                    parsed = Json.parse(args);
                } catch (RuntimeException e) {
                    parsed = Map.of("_raw_args", args, "_parse_error", "Invalid JSON from model");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", entry.id == null || entry.id.isBlank() ? "call_" + NodeParams.uuid24() : entry.id);
                row.put("name", entry.name == null || entry.name.isBlank() ? "unknown_tool" : entry.name);
                row.put("args", parsed);
                row.put("type", "tool_call");
                out.add(row);
            }
            return out;
        }
    }

    static final class ToolAcc {
        String id;
        String name = "";
        String args = "";
    }

    /** Pull nested summary text out of reasoning objects/lists. */
    static String thinkingText(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (Object v : values) {
            appendThinking(sb, v, 0);
        }
        return sb.toString();
    }

    private static void appendThinking(StringBuilder sb, Object v, int depth) {
        if (v == null || depth > 4) {
            return;
        }
        if (v instanceof String s) {
            sb.append(s);
            return;
        }
        if (v instanceof List<?> list) {
            for (Object item : list) {
                appendThinking(sb, item, depth + 1);
            }
            return;
        }
        Map<String, Object> m = NodeParams.asMap(v);
        if (m.isEmpty()) {
            return;
        }
        String t = NodeParams.str(m.get("text"));
        if (t.isEmpty()) {
            t = NodeParams.str(m.get("thinking"));
        }
        if (t.isEmpty()) {
            t = NodeParams.str(m.get("content"));
        }
        if (t.isEmpty()) {
            t = NodeParams.str(m.get("summary_text"));
        }
        if (!t.isEmpty()) {
            sb.append(t);
        }
        if (m.get("summary") != null) {
            appendThinking(sb, m.get("summary"), depth + 1);
        }
    }
}
