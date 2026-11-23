package com.studioquack.nodelibrary.core;

import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code openai-sse}: Chat Completions {@code data:} JSON chunks.
 *
 * <p>Mirrors {@code OpenAiSse.Collector} — content → {@code answer}; reasoning /
 * thinking fields and typed content blocks → {@code thinking}; tool_calls by index;
 * usage including {@code completion_tokens_details.reasoning_tokens}.
 */
final class OpenAiSseCollector implements StreamCollector {

    private final String port;
    private final StudioQuackHelper helper;
    private final StringBuilder text = new StringBuilder();
    private final Map<Integer, ToolAcc> tools = new TreeMap<>();
    private final StringBuilder audioData = new StringBuilder();
    private final StringBuilder audioTranscript = new StringBuilder();
    private final List<Map<String, Object>> liveThoughts = new ArrayList<>();
    /** Lifts inline {@code <think>…</think>} markup (and template-stripped orphan closers) out of the answer. */
    private final ThinkTagSplitter tagSplitter = new ThinkTagSplitter();
    private String audioFormat = "wav";
    private String finishReason;
    private Map<String, Object> usage;

    OpenAiSseCollector(String port, StudioQuackHelper helper) {
        this.port = port;
        this.helper = helper;
    }

    @Override
    public List<Piece> ingest(String event, Object chunk) {
        List<Piece> out = new ArrayList<>();
        Map<String, Object> map = ProviderStream.asMap(chunk);
        if (map.isEmpty()) {
            return out;
        }
        takeUsage(map.get("usage"));
        List<Object> choices = ProviderStream.asList(map.get("choices"));
        if (choices.isEmpty()) {
            ingestMessage(map, out);
            return out;
        }
        Map<String, Object> choice = ProviderStream.asMap(choices.get(0));
        String fr = ProviderStream.trim(choice.get("finish_reason"));
        if (!fr.isEmpty() && !"null".equals(fr)) {
            finishReason = fr;
        }
        if (choice.get("delta") instanceof Map<?, ?>) {
            ingestDelta(ProviderStream.asMap(choice.get("delta")), out);
        }
        if (choice.get("message") instanceof Map<?, ?>) {
            ingestMessage(ProviderStream.asMap(choice.get("message")), out);
        }
        if (choice.get("text") != null) {
            appendAnswer(ProviderStream.str(choice.get("text")), out);
        }
        return out;
    }

    private void ingestDelta(Map<String, Object> delta, List<Piece> out) {
        fanThinking(delta, out);
        splitContent(delta.get("content"), out);
        for (Object tc : ProviderStream.asList(delta.get("tool_calls"))) {
            absorbTool(ProviderStream.asMap(tc), false);
        }
        Map<String, Object> audio = ProviderStream.asMap(delta.get("audio"));
        if (!audio.isEmpty()) {
            if (audio.get("data") != null) {
                audioData.append(ProviderStream.str(audio.get("data")));
            }
            if (audio.get("transcript") != null) {
                audioTranscript.append(ProviderStream.str(audio.get("transcript")));
            }
            if (audio.get("format") != null) {
                audioFormat = ProviderStream.str(audio.get("format"));
            }
        }
    }

    private void ingestMessage(Map<String, Object> msg, List<Piece> out) {
        fanThinking(msg, out);
        splitContent(msg.get("content"), out);
        int idx = 0;
        for (Object tc : ProviderStream.asList(msg.get("tool_calls"))) {
            Map<String, Object> m = new LinkedHashMap<>(ProviderStream.asMap(tc));
            m.putIfAbsent("index", idx);
            absorbTool(m, true);
            idx++;
        }
    }

    private void fanThinking(Map<String, Object> src, List<Piece> out) {
        String think = thinkingText(src.get("reasoning"), src.get("reasoning_content"),
                src.get("thinking"), src.get("thoughts"));
        if (!think.isEmpty()) {
            liveThoughts.add(Map.of("type", "thinking", "text", think));
            ProviderStream.emitAndRecord(helper, port, "thinking", think, out);
        }
    }

    private void splitContent(Object content, List<Piece> out) {
        if (content == null) {
            return;
        }
        if (content instanceof String s) {
            appendAnswer(s, out);
            return;
        }
        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    appendAnswer(s, out);
                    continue;
                }
                Map<String, Object> m = ProviderStream.asMap(item);
                String type = ProviderStream.trim(m.get("type")).toLowerCase(Locale.ROOT);
                // Recursive unwrap: Mistral streams ThinkChunk {type:thinking,
                // thinking:[{type:text, text:…}]} — the thinking field is a LIST there.
                String piece = thinkingText(m.get("text"), m.get("thinking"), m.get("content"));
                if (type.contains("reason") || type.contains("think")) {
                    if (!piece.isEmpty()) {
                        liveThoughts.add(Map.of("type", "thinking", "text", piece));
                        ProviderStream.emitAndRecord(helper, port, "thinking", piece, out);
                    }
                } else {
                    appendAnswer(piece, out);
                }
            }
            return;
        }
        appendAnswer(ProviderStream.str(content), out);
    }

    private void appendAnswer(String piece, List<Piece> out) {
        if (piece == null || piece.isEmpty()) {
            return;
        }
        for (ThinkTagSplitter.Segment seg : tagSplitter.feed(piece)) {
            applySegment(seg, out);
        }
    }

    /** Stream end: resolve any held tag tail so no text is stranded in the splitter. */
    void flush(List<Piece> out) {
        for (ThinkTagSplitter.Segment seg : tagSplitter.flush()) {
            applySegment(seg, out);
        }
    }

    private void applySegment(ThinkTagSplitter.Segment seg, List<Piece> out) {
        if (ThinkTagSplitter.THINKING.equals(seg.channel())) {
            liveThoughts.add(Map.of("type", "thinking", "text", seg.text()));
            ProviderStream.emitAndRecord(helper, port, "thinking", seg.text(), out);
        } else {
            text.append(seg.text());
            ProviderStream.emitAndRecord(helper, port, "answer", seg.text(), out);
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
            entry.id = ProviderStream.str(tc.get("id"));
        }
        Map<String, Object> fn = ProviderStream.asMap(tc.get("function"));
        if (!fn.isEmpty()) {
            if (fn.get("name") != null) {
                entry.name = ProviderStream.str(fn.get("name"));
            }
            if (fn.get("arguments") != null) {
                String a = ProviderStream.str(fn.get("arguments"));
                entry.args = replaceArgs ? a : entry.args + a;
            }
        } else {
            if (tc.get("name") != null) {
                entry.name = ProviderStream.str(tc.get("name"));
            }
            Object a = tc.get("args") != null ? tc.get("args") : tc.get("arguments");
            if (a != null) {
                String s = a instanceof String str ? str : Json.write(a);
                entry.args = replaceArgs ? s : entry.args + s;
            }
        }
    }

    private void takeUsage(Object u) {
        Map<String, Object> map = ProviderStream.asMap(u);
        if (map.isEmpty()) {
            return;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : List.of("prompt_tokens", "completion_tokens", "total_tokens")) {
            if (map.get(k) != null) {
                out.put(k, map.get(k));
            }
        }
        Map<String, Object> details = ProviderStream.asMap(map.get("completion_tokens_details"));
        Object reason = details.get("reasoning_tokens");
        if (reason instanceof Number n && n.longValue() > 0) {
            out.put("reasoning_tokens", n);
        } else if (reason != null && !(reason instanceof Number)
                && !ProviderStream.trim(reason).isEmpty()
                && !"0".equals(ProviderStream.trim(reason))) {
            out.put("reasoning_tokens", reason);
        }
        usage = out;
    }

    @Override
    public Map<String, Object> toAssistant() {
        flush(new ArrayList<>()); // envelope/one-shot path: resolve any held tag tail
        String body = text.toString();
        if (liveThoughts.isEmpty()) {
            // Streamed missing-opener repair (SGLang/vLLM pre-fill the opening tag into
            // the prompt): reasoning rode the answer channel — split at the orphan closer.
            String[] orphan = ThinkTagSplitter.splitOrphanedCloser(body);
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
        List<Map<String, Object>> thoughts = new ArrayList<>(liveThoughts);
        List<Map<String, Object>> files = new ArrayList<>();
        if (audioData.length() > 0) {
            String fmt = audioFormat == null || audioFormat.isBlank() ? "wav" : audioFormat;
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("type", "audio");
            file.put("mime_type", "audio/" + fmt);
            file.put("source", "base64");
            file.put("data", audioData.toString());
            file.put("name", "out_" + ProviderStream.uuid8() + "." + fmt);
            files.add(file);
            if (audioTranscript.length() > 0 && textParts.isEmpty()) {
                textParts.add(audioTranscript.toString());
            }
        }
        Map<String, Object> response = LlmAssistant.makeResponse(
                textParts, toolCalls.isEmpty() ? null : toolCalls,
                thoughts.isEmpty() ? null : thoughts,
                files.isEmpty() ? null : files, finishReason, usage);
        boolean empty = String.join("", textParts).isBlank() && toolCalls.isEmpty() && files.isEmpty();
        if (empty && finishReason != null && !"stop".equals(finishReason)) {
            if ("length".equals(finishReason)) {
                response.put("content", List.of(Map.of("type", "text",
                        "text", "[System: Response stopped due to length limit. Consider increasing max_completion_tokens.]")));
            } else if ("content_filter".equals(finishReason)) {
                return LlmAssistant.errorResponse("Response blocked by content filter.", finishReason);
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
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.id == null || entry.id.isBlank() ? "call_" + ProviderStream.uuid24() : entry.id);
            row.put("name", entry.name == null || entry.name.isBlank() ? "unknown_tool" : entry.name);
            row.put("args", ProviderStream.parseJsonArgs(args));
            row.put("type", "tool_call");
            out.add(row);
        }
        return out;
    }

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
        Map<String, Object> m = ProviderStream.asMap(v);
        if (m.isEmpty()) {
            return;
        }
        String t = ProviderStream.str(m.get("text"));
        if (t.isEmpty()) {
            t = ProviderStream.str(m.get("thinking"));
        }
        if (t.isEmpty()) {
            t = ProviderStream.str(m.get("content"));
        }
        if (t.isEmpty()) {
            t = ProviderStream.str(m.get("summary_text"));
        }
        if (!t.isEmpty()) {
            sb.append(t);
        }
        if (m.get("summary") != null) {
            appendThinking(sb, m.get("summary"), depth + 1);
        }
    }

    private static final class ToolAcc {
        String id;
        String name = "";
        String args = "";
    }
}
