package com.studioquack.nodelibrary.core;

import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ollama-ndjson}: newline-delimited JSON from {@code POST /api/chat}.
 *
 * <p>{@code message.content} → answer; {@code message.thinking} → thinking;
 * {@code done_reason} → finish_reason; eval counts → v2 usage.
 */
final class OllamaNdjsonCollector implements StreamCollector {

    private final String port;
    private final StudioQuackHelper helper;
    private final StringBuilder text = new StringBuilder();
    private final List<Map<String, Object>> liveThoughts = new ArrayList<>();
    private String finishReason;
    private Object promptTokens;
    private Object completionTokens;

    OllamaNdjsonCollector(String port, StudioQuackHelper helper) {
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
        Map<String, Object> message = ProviderStream.asMap(map.get("message"));
        if (message.isEmpty() && map.get("response") != null) {
            // /api/generate envelope — treat response as content
            appendAnswer(ProviderStream.str(map.get("response")), out);
        } else {
            String think = ProviderStream.str(message.get("thinking"));
            if (!think.isEmpty()) {
                liveThoughts.add(Map.of("type", "thinking", "text", think));
                ProviderStream.emitAndRecord(helper, port, "thinking", think, out);
            }
            appendAnswer(ProviderStream.str(message.get("content")), out);
        }
        if (truthy(map.get("done"))) {
            String reason = ProviderStream.trim(map.get("done_reason"));
            if (!reason.isEmpty()) {
                finishReason = reason;
            } else if (finishReason == null) {
                finishReason = "stop";
            }
        }
        if (map.get("prompt_eval_count") != null) {
            promptTokens = map.get("prompt_eval_count");
        }
        if (map.get("eval_count") != null) {
            completionTokens = map.get("eval_count");
        }
        return out;
    }

    private void appendAnswer(String piece, List<Piece> out) {
        if (piece == null || piece.isEmpty()) {
            return;
        }
        text.append(piece);
        ProviderStream.emitAndRecord(helper, port, "answer", piece, out);
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return false;
        }
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    @Override
    public Map<String, Object> toAssistant() {
        List<String> textParts = text.length() > 0 ? List.of(text.toString()) : List.of();
        Map<String, Object> usage = new LinkedHashMap<>();
        if (promptTokens != null) {
            usage.put("prompt_tokens", promptTokens);
        }
        if (completionTokens != null) {
            usage.put("completion_tokens", completionTokens);
        }
        if (promptTokens instanceof Number p && completionTokens instanceof Number c) {
            usage.put("total_tokens", p.longValue() + c.longValue());
        }
        return LlmAssistant.makeResponse(
                textParts.isEmpty() ? List.of() : new ArrayList<>(textParts),
                null,
                liveThoughts.isEmpty() ? null : liveThoughts,
                null, finishReason, usage.isEmpty() ? null : usage);
    }
}
