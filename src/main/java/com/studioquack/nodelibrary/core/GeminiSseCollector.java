package com.studioquack.nodelibrary.core;

import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code gemini-sse}: {@code data:} JSON {@code candidates[].content.parts}.
 *
 * <p>Parts with {@code thought:true} → thinking; other text parts → answer.
 * {@code functionCall}/{@code function_call} parts assemble {@code tool_calls}
 * ({@code args} is the structured object Gemini already parsed).
 * {@code usageMetadata} maps onto v2 token counts; {@code finishReason} → finish_reason.
 */
final class GeminiSseCollector implements StreamCollector {

    private final String port;
    private final StudioQuackHelper helper;
    private final StringBuilder text = new StringBuilder();
    private final List<Map<String, Object>> liveThoughts = new ArrayList<>();
    private final List<Map<String, Object>> toolCalls = new ArrayList<>();
    private String finishReason;
    private Map<String, Object> usage;

    GeminiSseCollector(String port, StudioQuackHelper helper) {
        this.port = port;
        this.helper = helper;
    }

    @Override
    public List<Piece> ingest(String event, Object data) {
        List<Piece> out = new ArrayList<>();
        Map<String, Object> map = ProviderStream.asMap(data);
        if (map.isEmpty()) {
            return out;
        }
        takeUsage(map.get("usageMetadata"));
        List<Object> candidates = ProviderStream.asList(map.get("candidates"));
        if (candidates.isEmpty()) {
            return out;
        }
        Map<String, Object> cand = ProviderStream.asMap(candidates.get(0));
        String fr = ProviderStream.trim(cand.get("finishReason"));
        if (fr.isEmpty()) {
            fr = ProviderStream.trim(cand.get("finish_reason"));
        }
        if (!fr.isEmpty() && !"null".equals(fr)) {
            finishReason = fr;
        }
        Map<String, Object> content = ProviderStream.asMap(cand.get("content"));
        for (Object part : ProviderStream.asList(content.get("parts"))) {
            Map<String, Object> p = ProviderStream.asMap(part);
            if (absorbFunctionCall(p)) {
                continue;
            }
            String piece = ProviderStream.str(p.get("text"));
            if (piece.isEmpty()) {
                continue;
            }
            if (isThought(p.get("thought"))) {
                liveThoughts.add(Map.of("type", "thinking", "text", piece));
                ProviderStream.emitAndRecord(helper, port, "thinking", piece, out);
            } else {
                text.append(piece);
                ProviderStream.emitAndRecord(helper, port, "answer", piece, out);
            }
        }
        return out;
    }

    /**
     * Streaming function calls arrive as a complete {@code functionCall} part
     * (name + args object), not token-wise JSON like Anthropic {@code input_json_delta}.
     */
    private boolean absorbFunctionCall(Map<String, Object> part) {
        Object raw = part.get("functionCall") != null ? part.get("functionCall") : part.get("function_call");
        Map<String, Object> fc = ProviderStream.asMap(raw);
        if (fc.isEmpty()) {
            return false;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        String id = ProviderStream.trim(fc.get("id"));
        if (id.isEmpty()) {
            id = ProviderStream.trim(part.get("id"));
        }
        row.put("id", id.isEmpty() ? "call_" + ProviderStream.uuid24() : id);
        String name = ProviderStream.trim(fc.get("name"));
        row.put("name", name.isEmpty() ? "unknown_tool" : name);
        Object args = fc.get("args") != null ? fc.get("args") : fc.get("arguments");
        if (args instanceof String s) {
            row.put("args", ProviderStream.parseJsonArgs(s));
        } else if (args instanceof Map<?, ?> || args instanceof List<?>) {
            row.put("args", args);
        } else {
            row.put("args", Map.of());
        }
        row.put("type", "tool_call");
        toolCalls.add(row);
        return true;
    }

    private static boolean isThought(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return false;
        }
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private void takeUsage(Object raw) {
        Map<String, Object> meta = ProviderStream.asMap(raw);
        if (meta.isEmpty()) {
            return;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (meta.get("promptTokenCount") != null) {
            out.put("prompt_tokens", meta.get("promptTokenCount"));
        }
        if (meta.get("candidatesTokenCount") != null) {
            out.put("completion_tokens", meta.get("candidatesTokenCount"));
        }
        if (meta.get("totalTokenCount") != null) {
            out.put("total_tokens", meta.get("totalTokenCount"));
        }
        if (!out.isEmpty()) {
            usage = out;
        }
    }

    @Override
    public Map<String, Object> toAssistant() {
        List<String> textParts = text.length() > 0 ? List.of(text.toString()) : List.of();
        return LlmAssistant.makeResponse(
                textParts.isEmpty() ? List.of() : new ArrayList<>(textParts),
                toolCalls.isEmpty() ? null : new ArrayList<>(toolCalls),
                liveThoughts.isEmpty() ? null : liveThoughts,
                null, finishReason, usage);
    }
}
