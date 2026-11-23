package com.studioquack.nodelibrary.core;

import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code anthropic-sse}: {@code event:}/{@code data:} pairs from Messages streaming.
 *
 * <p>{@code text_delta} → answer; {@code thinking_delta} → thinking;
 * {@code tool_use} + {@code input_json_delta} → tool_calls; usage mapped to the
 * v2 {@code prompt_tokens}/{@code completion_tokens}/{@code total_tokens} shape.
 */
final class AnthropicSseCollector implements StreamCollector {

    private final String port;
    private final StudioQuackHelper helper;
    private final StringBuilder text = new StringBuilder();
    private final List<Map<String, Object>> liveThoughts = new ArrayList<>();
    private final Map<Integer, ToolAcc> tools = new TreeMap<>();
    private int currentTool = -1;
    private String finishReason;
    private Object promptTokens;
    private Object completionTokens;

    AnthropicSseCollector(String port, StudioQuackHelper helper) {
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
        String type = event != null && !event.isBlank() ? event.trim() : ProviderStream.trim(map.get("type"));
        switch (type) {
            case "content_block_start" -> startBlock(map);
            case "content_block_delta" -> deltaBlock(map, out);
            case "content_block_stop" -> {
                // block closed; signature_delta (if any) already ignored
            }
            case "message_start" -> takeMessageStart(map, out);
            case "message_delta" -> takeMessageDelta(map);
            case "message_stop" -> {
                // stream end — collector already has assembled state
            }
            case "message" -> takeMessageStart(Map.of("message", map), out);
            default -> {
                // unknown events ignored
            }
        }
        return out;
    }

    private void startBlock(Map<String, Object> map) {
        Map<String, Object> block = ProviderStream.asMap(map.get("content_block"));
        if (block.isEmpty()) {
            block = ProviderStream.asMap(map.get("contentBlock"));
        }
        String btype = ProviderStream.trim(block.get("type"));
        if ("tool_use".equals(btype)) {
            currentTool++;
            ToolAcc acc = tools.computeIfAbsent(currentTool, k -> new ToolAcc());
            if (block.get("id") != null) {
                acc.id = ProviderStream.str(block.get("id"));
            }
            if (block.get("name") != null) {
                acc.name = ProviderStream.str(block.get("name"));
            }
        }
    }

    private void deltaBlock(Map<String, Object> map, List<Piece> out) {
        Map<String, Object> delta = ProviderStream.asMap(map.get("delta"));
        String dtype = ProviderStream.trim(delta.get("type"));
        if ("text_delta".equals(dtype)) {
            appendAnswer(ProviderStream.str(delta.get("text")), out);
        } else if ("thinking_delta".equals(dtype)) {
            String think = ProviderStream.str(delta.get("thinking"));
            if (!think.isEmpty()) {
                liveThoughts.add(Map.of("type", "thinking", "text", think));
                ProviderStream.emitAndRecord(helper, port, "thinking", think, out);
            }
        } else if ("input_json_delta".equals(dtype)) {
            String fragment = ProviderStream.str(delta.get("partial_json"));
            if (!fragment.isEmpty()) {
                int idx = currentTool >= 0 ? currentTool : 0;
                ToolAcc acc = tools.computeIfAbsent(idx, k -> new ToolAcc());
                acc.args = acc.args + fragment;
            }
        } else if ("signature_delta".equals(dtype) || "citations_delta".equals(dtype)) {
            // integrity / citation metadata — not thinking or answer text
        }
    }

    private void takeMessageStart(Map<String, Object> map, List<Piece> out) {
        Map<String, Object> message = ProviderStream.asMap(map.get("message"));
        if (message.isEmpty()) {
            message = map;
        }
        Map<String, Object> usage = ProviderStream.asMap(message.get("usage"));
        if (usage.isEmpty()) {
            usage = ProviderStream.asMap(map.get("usage"));
        }
        if (usage.get("input_tokens") != null) {
            promptTokens = usage.get("input_tokens");
        }
        if (usage.get("output_tokens") != null) {
            completionTokens = usage.get("output_tokens");
        }
        // Non-stream envelope: message.content[] already assembled.
        for (Object block : ProviderStream.asList(message.get("content"))) {
            Map<String, Object> b = ProviderStream.asMap(block);
            String btype = ProviderStream.trim(b.get("type"));
            if ("text".equals(btype)) {
                appendAnswer(ProviderStream.str(b.get("text")), out);
            } else if ("thinking".equals(btype)) {
                String think = ProviderStream.str(b.get("thinking"));
                if (think.isEmpty()) {
                    think = ProviderStream.str(b.get("text"));
                }
                if (!think.isEmpty()) {
                    liveThoughts.add(Map.of("type", "thinking", "text", think));
                    ProviderStream.emitAndRecord(helper, port, "thinking", think, out);
                }
            } else if ("tool_use".equals(btype)) {
                currentTool++;
                ToolAcc acc = tools.computeIfAbsent(currentTool, k -> new ToolAcc());
                acc.id = ProviderStream.str(b.get("id"));
                acc.name = ProviderStream.str(b.get("name"));
                Object input = b.get("input");
                if (input != null) {
                    acc.args = input instanceof String s ? s : studioquack.Json.write(input);
                }
            }
        }
        if (message.get("stop_reason") != null && finishReason == null) {
            String sr = ProviderStream.trim(message.get("stop_reason"));
            if (!sr.isEmpty() && !"null".equals(sr)) {
                finishReason = sr;
            }
        }
    }

    private void takeMessageDelta(Map<String, Object> map) {
        Map<String, Object> delta = ProviderStream.asMap(map.get("delta"));
        String sr = ProviderStream.trim(delta.get("stop_reason"));
        if (!sr.isEmpty() && !"null".equals(sr)) {
            finishReason = sr;
        }
        Map<String, Object> usage = ProviderStream.asMap(map.get("usage"));
        if (usage.get("output_tokens") != null) {
            completionTokens = usage.get("output_tokens");
        }
        if (usage.get("input_tokens") != null) {
            promptTokens = usage.get("input_tokens");
        }
    }

    private void appendAnswer(String piece, List<Piece> out) {
        if (piece == null || piece.isEmpty()) {
            return;
        }
        text.append(piece);
        ProviderStream.emitAndRecord(helper, port, "answer", piece, out);
    }

    @Override
    public Map<String, Object> toAssistant() {
        List<String> textParts = text.length() > 0 ? List.of(text.toString()) : List.of();
        List<Map<String, Object>> toolCalls = materialize();
        Map<String, Object> usage = v2Usage();
        return LlmAssistant.makeResponse(
                textParts.isEmpty() ? List.of() : new ArrayList<>(textParts),
                toolCalls.isEmpty() ? null : toolCalls,
                liveThoughts.isEmpty() ? null : liveThoughts,
                null, finishReason, usage.isEmpty() ? null : usage);
    }

    private Map<String, Object> v2Usage() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (promptTokens != null) {
            out.put("prompt_tokens", promptTokens);
        }
        if (completionTokens != null) {
            out.put("completion_tokens", completionTokens);
        }
        if (promptTokens instanceof Number p && completionTokens instanceof Number c) {
            out.put("total_tokens", p.longValue() + c.longValue());
        } else if (promptTokens != null && completionTokens != null) {
            try {
                out.put("total_tokens", Long.parseLong(String.valueOf(promptTokens).trim())
                        + Long.parseLong(String.valueOf(completionTokens).trim()));
            } catch (NumberFormatException ignored) {
                // leave total absent
            }
        }
        return out;
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

    private static final class ToolAcc {
        String id;
        String name = "";
        String args = "";
    }
}
