package com.studioquack.nodelibrary.nodes.localllm;

import com.studioquack.nodelibrary.core.ThinkTagSplitter;
import studioquack.Json;
import studioquack.SseFrame;
import studioquack.SseStream;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects provider-verbatim chat SSE (same shape as OpenAI Models
 * {@code _stream_chat_completion} / {@code _make_response}).
 *
 * <p>Live tokens: port-tagged {@code emitPartial("llm_output", "thinking", …)} for
 * reasoning/thinking deltas, {@code emitPartial("llm_output", "answer", …)} for content —
 * the daemon graph-routes each frame along this node's own output-port edges.
 * Request-scoped — no static buffers. Never logs deltas (PHI).
 */
final class LocalLlmStream {

    private final StringBuilder text = new StringBuilder();
    private final Map<Integer, Map<String, String>> toolCalls = new LinkedHashMap<>();
    private final StringBuilder audioData = new StringBuilder();
    private final StringBuilder audioTranscript = new StringBuilder();
    private String audioFormat = "wav";
    private String finishReason;
    private Map<String, Object> usage;
    private final List<Map<String, Object>> thoughts = new ArrayList<>();
    /** Lifts inline {@code <think>…</think>} markup (and template-stripped orphan closers) out of the answer. */
    private final ThinkTagSplitter tagSplitter = new ThinkTagSplitter();
    /** Files from the raw envelope / message; never logged (bytes / PHI). */
    private final List<Map<String, Object>> outputFiles = new ArrayList<>();

    /**
     * Drain {@code chatStream}, fan out partials, assemble the assistant payload.
     * Returns {@code null} when the transport failed so the caller can
     * {@code completion()} instead.
     */
    static Object collect(SseStream stream, StudioQuackHelper helper) {
        if (stream == null) {
            return null;
        }
        LocalLlmStream acc = new LocalLlmStream();
        try (SseStream s = stream) {
            for (SseFrame frame : s) {
                if (frame == null) {
                    continue;
                }
                acc.ingest(payload(frame), helper);
            }
            if (s.transportFailed()) {
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }
        acc.flush(helper);
        return acc.toAssistant();
    }

    /** Prefer parsed {@code data}; fall back to JSON in {@code rawData}. Skip {@code [DONE]}. */
    static Object payload(SseFrame frame) {
        Object data = frame.data;
        if (data instanceof Map<?, ?> || data instanceof List<?>) {
            return data;
        }
        String raw = data instanceof String s && !s.isBlank() ? s : frame.rawData;
        return parseDataLine(raw);
    }

    static Object parseDataLine(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.startsWith("data:")) {
            t = t.substring(5).trim();
        }
        if ("[DONE]".equalsIgnoreCase(t) || "[DONE]".equalsIgnoreCase(t)) {
            return null;
        }
        try {
            return Json.parse(t);
        } catch (Exception ignored) {
            return null;
        }
    }

    void ingest(Object chunk, StudioQuackHelper helper) {
        if (chunk == null || (chunk instanceof String s && (s.isBlank()
                || "[DONE]".equalsIgnoreCase(s.trim()) || "[DONE]".equalsIgnoreCase(s.trim())))) {
            return;
        }
        Map<String, Object> map = Json.asMap(chunk);
        if (map.isEmpty()) {
            return;
        }
        takeUsage(map.get("usage"));
        takeFiles(map);
        List<Object> choices = Json.asList(map.get("choices"));
        if (choices.isEmpty()) {
            ingestMessage(map, helper);
            return;
        }
        Map<String, Object> choice = Json.asMap(choices.get(0));
        Object fr = choice.get("finish_reason");
        if (fr != null && !Json.str(fr).isBlank()) {
            finishReason = Json.str(fr);
        }
        if (choice.get("delta") instanceof Map<?, ?>) {
            ingestDelta(Json.asMap(choice.get("delta")), helper);
        }
        if (choice.get("message") instanceof Map<?, ?>) {
            Map<String, Object> msg = Json.asMap(choice.get("message"));
            ingestMessage(msg, helper);
            takeFiles(msg);
        }
        if (choice.get("text") != null) {
            appendAnswer(Json.str(choice.get("text")), helper);
        }
    }

    private void ingestDelta(Map<String, Object> delta, StudioQuackHelper helper) {
        fanThinking(delta, helper);
        if (delta.get("content") != null) {
            appendAnswer(contentToText(delta.get("content")), helper);
        }
        for (Object tc : Json.asList(delta.get("tool_calls"))) {
            absorbToolCall(Json.asMap(tc));
        }
        Map<String, Object> audio = Json.asMap(delta.get("audio"));
        if (!audio.isEmpty()) {
            if (audio.get("data") != null) {
                audioData.append(Json.str(audio.get("data")));
            }
            if (audio.get("transcript") != null) {
                audioTranscript.append(Json.str(audio.get("transcript")));
            }
            if (audio.get("format") != null) {
                audioFormat = Json.str(audio.get("format"));
            }
        }
    }

    private void ingestMessage(Map<String, Object> msg, StudioQuackHelper helper) {
        fanThinking(msg, helper);
        if (msg.get("content") != null) {
            appendAnswer(contentToText(msg.get("content")), helper);
        }
        int idx = 0;
        for (Object tc : Json.asList(msg.get("tool_calls"))) {
            Map<String, Object> m = Json.asMap(tc);
            if (!m.containsKey("index")) {
                m = new LinkedHashMap<>(m);
                m.put("index", idx);
            }
            absorbToolCall(m);
            idx++;
        }
    }

    private void fanThinking(Map<String, Object> src, StudioQuackHelper helper) {
        String think = firstText(src.get("reasoning"), src.get("reasoning_content"),
                src.get("thinking"), src.get("thoughts"));
        if (!think.isEmpty()) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "thinking");
            block.put("text", think);
            thoughts.add(block);
            emit(helper, "thinking", think);
        }
    }

    private void appendAnswer(String piece, StudioQuackHelper helper) {
        if (piece == null || piece.isEmpty()) {
            return;
        }
        for (ThinkTagSplitter.Segment seg : tagSplitter.feed(piece)) {
            applySegment(seg, helper);
        }
    }

    /** Stream end: resolve any held tag tail so no text is stranded in the splitter. */
    void flush(StudioQuackHelper helper) {
        for (ThinkTagSplitter.Segment seg : tagSplitter.flush()) {
            applySegment(seg, helper);
        }
    }

    private void applySegment(ThinkTagSplitter.Segment seg, StudioQuackHelper helper) {
        if (ThinkTagSplitter.THINKING.equals(seg.channel())) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "thinking");
            block.put("text", seg.text());
            thoughts.add(block);
            emit(helper, "thinking", seg.text());
        } else {
            text.append(seg.text());
            emit(helper, "answer", seg.text());
        }
    }

    private static void emit(StudioQuackHelper helper, String channel, String delta) {
        if (helper == null || delta == null || delta.isEmpty()) {
            return;
        }
        try {
            // Tag with this node's own output port so the daemon routes the frame along that
            // port's edges (Duckbot end included); untagged frames never leave the daemon.
            helper.emitPartial("llm_output", channel, delta);
        } catch (Exception ignored) {
            // SDK contract: never throw past the node
        }
    }

    private void absorbToolCall(Map<String, Object> tc) {
        if (tc.isEmpty()) {
            return;
        }
        int idx = 0;
        Object i = tc.get("index");
        if (i instanceof Number n) {
            idx = n.intValue();
        }
        Map<String, String> entry = toolCalls.computeIfAbsent(idx, k -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", "");
            m.put("name", "");
            m.put("args", "");
            return m;
        });
        if (tc.get("id") != null) {
            entry.put("id", Json.str(tc.get("id")));
        }
        Map<String, Object> fn = Json.asMap(tc.get("function"));
        if (!fn.isEmpty()) {
            if (fn.get("name") != null) {
                entry.put("name", Json.str(fn.get("name")));
            }
            if (fn.get("arguments") != null) {
                entry.put("args", entry.get("args") + Json.str(fn.get("arguments")));
            }
        } else {
            if (tc.get("name") != null) {
                entry.put("name", Json.str(tc.get("name")));
            }
            Object a = tc.get("args") != null ? tc.get("args") : tc.get("arguments");
            if (a != null) {
                entry.put("args", entry.get("args") + (a instanceof String ? (String) a : Json.write(a)));
            }
        }
    }

    /** First non-empty {@code output_files}/{@code outputFiles} snapshot wins. */
    private void takeFiles(Map<String, Object> src) {
        if (src == null || !outputFiles.isEmpty()) {
            return;
        }
        Object raw = src.get("output_files");
        if (raw == null) {
            raw = src.get("outputFiles");
        }
        for (Object item : Json.asList(raw)) {
            Map<String, Object> f = Json.asMap(item);
            if (!f.isEmpty()) {
                outputFiles.add(new LinkedHashMap<>(f));
            }
        }
    }

    private void takeUsage(Object u) {
        Map<String, Object> map = Json.asMap(u);
        if (map.isEmpty()) {
            return;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        copyNum(map, out, "prompt_tokens");
        copyNum(map, out, "completion_tokens");
        copyNum(map, out, "total_tokens");
        Map<String, Object> details = Json.asMap(map.get("completion_tokens_details"));
        Object reason = details.get("reasoning_tokens");
        if (reason instanceof Number n && n.longValue() > 0) {
            // Token-usage metadata stays in the usage map for programmatic consumers; it is
            // never synthesized into the human-readable thinking stream.
            out.put("reasoning_tokens", n);
        }
        usage = out;
    }

    private static void copyNum(Map<String, Object> src, Map<String, Object> dest, String key) {
        if (src.get(key) != null) {
            dest.put(key, src.get(key));
        }
    }

    /** Canonical assistant payload matching Python {@code _make_response}. */
    Map<String, Object> toAssistant() {
        flush(null); // one-shot envelope path: resolve any held tag tail
        String body = text.toString();
        if (thoughts.isEmpty()) {
            // Streamed missing-opener repair (SGLang/vLLM pre-fill the opening tag into
            // the prompt): reasoning rode the answer channel token-by-token, so the live
            // split never fired — split the assembled text at the orphan closer now.
            String[] orphan = ThinkTagSplitter.splitOrphanedCloser(body);
            if (orphan != null) {
                if (!orphan[0].isBlank()) {
                    Map<String, Object> block = new LinkedHashMap<>();
                    block.put("type", "thinking");
                    block.put("text", orphan[0]);
                    thoughts.add(block);
                }
                body = orphan[1];
            }
        }
        List<Map<String, Object>> calls = materialiseTools();
        if (body.isBlank() && !calls.isEmpty()) {
            body = "I am calling a tool to answer your request.";
        }
        if (body.isBlank() && !audioTranscript.isEmpty()) {
            body = audioTranscript.toString();
        }
        List<Object> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", body));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", "assistant");
        out.put("content", content);
        if (!calls.isEmpty()) {
            out.put("tool_calls", calls);
        }
        if (!thoughts.isEmpty()) {
            out.put("thoughts", thoughts);
            List<Map<String, Object>> thinking = new ArrayList<>();
            for (Map<String, Object> t : thoughts) {
                thinking.add(Map.of("type", "thinking",
                        "thinking", t.get("text") == null ? "" : t.get("text")));
            }
            out.put("thinking", thinking);
        }
        List<Map<String, Object>> files = new ArrayList<>(outputFiles);
        if (audioData.length() > 0) {
            files.add(Map.of(
                    "type", "audio",
                    "mime_type", "audio/" + audioFormat,
                    "source", "base64",
                    "data", audioData.toString(),
                    "name", "out." + audioFormat));
        }
        if (!files.isEmpty()) {
            out.put("output_files", files);
        }
        if (finishReason != null && !finishReason.isBlank()) {
            out.put("finish_reason", finishReason);
        }
        if (usage != null) {
            out.put("usage", usage);
        }
        return out;
    }

    private List<Map<String, Object>> materialiseTools() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Integer> keys = new ArrayList<>(toolCalls.keySet());
        keys.sort(Integer::compareTo);
        for (Integer k : keys) {
            Map<String, String> e = toolCalls.get(k);
            String args = e.get("args") == null || e.get("args").isBlank() ? "{}" : e.get("args");
            Object parsed;
            try {
                parsed = Json.parse(args);
            } catch (Exception ex) {
                parsed = Map.of("_raw_args", args, "_parse_error", "Invalid JSON from model");
            }
            Map<String, Object> tc = new LinkedHashMap<>();
            String id = e.get("id");
            tc.put("id", id == null || id.isBlank() ? "call_" + k : id);
            tc.put("name", e.get("name") == null || e.get("name").isBlank() ? "unknown_tool" : e.get("name"));
            tc.put("args", parsed);
            tc.put("type", "tool_call");
            out.add(tc);
        }
        return out;
    }

    private static String contentToText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                Map<String, Object> m = Json.asMap(item);
                if (m.get("text") != null) {
                    sb.append(Json.str(m.get("text")));
                }
            }
            return sb.toString();
        }
        Map<String, Object> m = Json.asMap(content);
        return m.get("text") == null ? "" : Json.str(m.get("text"));
    }

    private static String firstText(Object... values) {
        for (Object v : values) {
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
            if (v instanceof List<?> list) {
                StringBuilder sb = new StringBuilder();
                for (Object item : list) {
                    Map<String, Object> m = Json.asMap(item);
                    String t = Json.str(m.get("text"));
                    if (t.isEmpty()) {
                        t = Json.str(m.get("thinking"));
                    }
                    sb.append(t);
                }
                if (sb.length() > 0) {
                    return sb.toString();
                }
            }
            Map<String, Object> m = Json.asMap(v);
            if (!m.isEmpty()) {
                String t = Json.str(m.get("text"));
                if (t.isEmpty()) {
                    t = Json.str(m.get("summary"));
                }
                if (t.isEmpty()) {
                    t = Json.str(m.get("content"));
                }
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }
}
