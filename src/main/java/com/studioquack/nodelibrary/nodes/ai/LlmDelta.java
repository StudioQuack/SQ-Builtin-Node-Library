package com.studioquack.nodelibrary.nodes.ai;

import com.studioquack.nodelibrary.core.ThinkTagSplitter;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates OpenAI-style chat SSE / envelope chunks and fans thinking/answer
 * deltas through {@link StudioQuackHelper#emitPartial(String, String)}.
 *
 * <p>Request-scoped (not static). Never logs deltas that may contain PHI.
 */
public final class LlmDelta {

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

    /** Apply one provider chunk (a parsed {@code data:} object or a full envelope). */
    public void ingest(Object chunk, StudioQuackHelper helper) {
        if (chunk == null || chunk instanceof String s && ("[DONE]".equals(s.trim()) || s.isBlank())) {
            return;
        }
        Map<String, Object> map = Json.asMap(chunk);
        if (map.isEmpty()) {
            return;
        }
        takeUsage(map.get("usage"));
        List<Object> choices = Json.asList(map.get("choices"));
        if (choices.isEmpty()) {
            // some relays send the message at the top level
            ingestMessage(map, helper);
            return;
        }
        Map<String, Object> choice = Json.asMap(choices.get(0));
        Object fr = choice.get("finish_reason");
        if (fr != null && !Json.str(fr).isBlank()) {
            finishReason = Json.str(fr);
        }
        Object delta = choice.get("delta");
        if (delta instanceof Map<?, ?>) {
            ingestDelta(Json.asMap(delta), helper);
        }
        Object message = choice.get("message");
        if (message instanceof Map<?, ?>) {
            ingestMessage(Json.asMap(message), helper);
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
            thoughts.add(Map.of("type", "thinking", "text", think));
            if (helper != null) {
                // Port-tagged so the daemon graph-routes the frame; untagged never leaves.
                helper.emitPartial("llm_output", "thinking", think);
            }
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
    public void flush(StudioQuackHelper helper) {
        for (ThinkTagSplitter.Segment seg : tagSplitter.flush()) {
            applySegment(seg, helper);
        }
    }

    private void applySegment(ThinkTagSplitter.Segment seg, StudioQuackHelper helper) {
        if (ThinkTagSplitter.THINKING.equals(seg.channel())) {
            thoughts.add(Map.of("type", "thinking", "text", seg.text()));
            if (helper != null) {
                helper.emitPartial("llm_output", "thinking", seg.text());
            }
        } else {
            text.append(seg.text());
            if (helper != null) {
                helper.emitPartial("llm_output", "answer", seg.text());
            }
        }
    }

    private void absorbToolCall(Map<String, Object> tc) {
        if (tc.isEmpty()) {
            return;
        }
        int idx;
        Object i = tc.get("index");
        if (i instanceof Number n) {
            idx = n.intValue();
        } else if (tc.get("id") != null || Json.asMap(tc.get("function")).get("name") != null) {
            // Providers that omit `index`: a delta carrying id/name starts a new call.
            idx = toolCalls.isEmpty() ? 0 : java.util.Collections.max(toolCalls.keySet()) + 1;
        } else {
            // Bare argument fragments continue the most recent call.
            idx = toolCalls.isEmpty() ? 0 : java.util.Collections.max(toolCalls.keySet());
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
            if (tc.get("args") != null || tc.get("arguments") != null) {
                Object a = tc.get("args") != null ? tc.get("args") : tc.get("arguments");
                entry.put("args", entry.get("args") + (a instanceof String ? (String) a : Json.write(a)));
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
            // Token-usage metadata stays in the usage map; never synthesized into thoughts.
            out.put("reasoning_tokens", n);
        }
        usage = out;
    }

    private static void copyNum(Map<String, Object> src, Map<String, Object> dest, String key) {
        if (src.get(key) != null) {
            dest.put(key, src.get(key));
        }
    }

    /** Canonical v2 assistant payload matching the Python {@code _make_response}. */
    public Map<String, Object> toAssistant() {
        flush(null); // one-shot envelope path: resolve any held tag tail
        String body = text.toString();
        if (thoughts.isEmpty()) {
            // Streamed missing-opener repair (SGLang/vLLM pre-fill the opening tag into
            // the prompt): reasoning rode the answer channel — split at the orphan closer.
            String[] orphan = ThinkTagSplitter.splitOrphanedCloser(body);
            if (orphan != null) {
                if (!orphan[0].isBlank()) {
                    thoughts.add(Map.of("type", "thinking", "text", orphan[0]));
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
        List<Map<String, Object>> files = new ArrayList<>();
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

    public String finishReason() {
        return finishReason;
    }

    public boolean hasText() {
        return text.length() > 0;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public boolean hasFiles() {
        return audioData.length() > 0;
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
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }
}
