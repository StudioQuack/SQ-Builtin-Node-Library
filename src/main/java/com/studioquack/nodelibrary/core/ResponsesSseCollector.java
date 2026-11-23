package com.studioquack.nodelibrary.core;

import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code responses-sse}: OpenAI Responses API SSE ({@code POST /v1/responses},
 * {@code stream:true}). Perplexity Agent API ({@code POST /v1/agent}) uses the
 * same event names.
 *
 * <p>{@code response.output_text.delta} → answer; {@code response.reasoning_text.delta}
 * / {@code response.reasoning_summary_text.delta} → thinking;
 * {@code response.function_call_arguments.delta} + {@code response.output_item.done}
 * ({@code type:function_call}) assemble {@code tool_calls}
 * {@code {id, name, args, type:tool_call}} (args = parsed JSON object);
 * {@code response.completed} / {@code response.incomplete} → usage (+ finish_reason from {@code status});
 * {@code response.failed} / {@code error} → v2 error payload. Unknown events ignored.
 *
 * <p>Event names follow the current Responses streaming reference. A unary JSON
 * Response object (provider ignored {@code stream:true}) is ingested as an envelope.
 */
final class ResponsesSseCollector implements StreamCollector {

    private final String port;
    private final StudioQuackHelper helper;
    private final StringBuilder text = new StringBuilder();
    private final List<Map<String, Object>> liveThoughts = new ArrayList<>();
    private final LinkedHashMap<String, ToolAcc> tools = new LinkedHashMap<>();
    private String finishReason;
    private Map<String, Object> usage;
    private String errorMessage;
    private String errorReason;

    ResponsesSseCollector(String port, StudioQuackHelper helper) {
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
        if ("response.output_text.delta".equals(type)) {
            appendAnswer(deltaText(map), out);
            return out;
        }
        if ("response.reasoning_text.delta".equals(type)
                || "response.reasoning_summary_text.delta".equals(type)) {
            appendThinking(deltaText(map), out);
            return out;
        }
        if ("response.function_call_arguments.delta".equals(type)) {
            deltaTool(map);
            return out;
        }
        if ("response.output_item.added".equals(type)) {
            startItem(map);
            return out;
        }
        if ("response.output_item.done".equals(type)) {
            finishItem(map);
            return out;
        }
        if ("response.completed".equals(type) || "response.incomplete".equals(type)) {
            takeCompleted(map);
            return out;
        }
        if ("response.failed".equals(type) || "error".equals(type)) {
            takeError(map);
            return out;
        }
        if (looksLikeEnvelope(type, map)) {
            takeEnvelope(map, out);
        }
        return out;
    }

    private static boolean looksLikeEnvelope(String type, Map<String, Object> map) {
        if (type != null && type.startsWith("response.") && !"response".equals(type)) {
            return false;
        }
        if ("error".equals(type)) {
            return false;
        }
        return "response".equals(map.get("object"))
                || map.get("output") instanceof List<?>
                || map.get("output_text") != null;
    }

    private void startItem(Map<String, Object> map) {
        Map<String, Object> item = itemOf(map);
        if (!"function_call".equals(ProviderStream.trim(item.get("type")))) {
            return;
        }
        ToolAcc acc = tools.computeIfAbsent(toolKey(map, item), k -> new ToolAcc());
        absorbFunctionCall(acc, item, false);
    }

    private void finishItem(Map<String, Object> map) {
        Map<String, Object> item = itemOf(map);
        if (!"function_call".equals(ProviderStream.trim(item.get("type")))) {
            return;
        }
        ToolAcc acc = tools.computeIfAbsent(toolKey(map, item), k -> new ToolAcc());
        absorbFunctionCall(acc, item, true);
    }

    private void deltaTool(Map<String, Object> map) {
        ToolAcc acc = tools.computeIfAbsent(toolKey(map, Map.of()), k -> new ToolAcc());
        if (map.get("item_id") != null && (acc.itemId == null || acc.itemId.isBlank())) {
            acc.itemId = ProviderStream.str(map.get("item_id"));
        }
        String piece = deltaText(map);
        if (!piece.isEmpty()) {
            acc.args = acc.args + piece;
        }
    }

    private void absorbFunctionCall(ToolAcc acc, Map<String, Object> item, boolean replaceArgs) {
        if (item.get("id") != null) {
            acc.itemId = ProviderStream.str(item.get("id"));
        }
        String callId = firstStr(item, "call_id", "callId", "id");
        if (!callId.isEmpty()) {
            acc.id = callId;
        }
        if (item.get("name") != null) {
            acc.name = ProviderStream.str(item.get("name"));
        }
        Object args = item.get("arguments") != null ? item.get("arguments") : item.get("args");
        if (args != null) {
            String s = args instanceof String str ? str : Json.write(args);
            acc.args = replaceArgs || acc.args.isEmpty() ? s : acc.args + s;
        }
    }

    private void takeCompleted(Map<String, Object> map) {
        Map<String, Object> response = ProviderStream.asMap(map.get("response"));
        if (response.isEmpty()) {
            response = map;
        }
        takeStatus(response);
        takeUsage(response.get("usage") != null ? response.get("usage") : map.get("usage"));
        if (text.length() == 0 && tools.isEmpty() && liveThoughts.isEmpty()) {
            harvestOutput(response, new ArrayList<>());
        }
    }

    private void takeEnvelope(Map<String, Object> map, List<Piece> out) {
        Map<String, Object> response = ProviderStream.asMap(map.get("response"));
        if (response.isEmpty() || map.get("output") instanceof List<?>) {
            response = map;
        }
        takeStatus(response);
        takeUsage(response.get("usage") != null ? response.get("usage") : map.get("usage"));
        if (response.get("error") instanceof Map<?, ?> || map.get("error") instanceof Map<?, ?>) {
            takeError(response.isEmpty() ? map : response);
        }
        harvestOutput(response, out);
    }

    private void harvestOutput(Map<String, Object> response, List<Piece> out) {
        boolean hadText = text.length() > 0;
        boolean hadThink = !liveThoughts.isEmpty();
        boolean hadTools = !tools.isEmpty();
        if (!hadText) {
            String ready = ProviderStream.str(response.get("output_text"));
            if (!ready.isEmpty()) {
                appendAnswer(ready, out);
                hadText = true;
            }
        }
        for (Object raw : ProviderStream.asList(response.get("output"))) {
            Map<String, Object> item = ProviderStream.asMap(raw);
            String itype = ProviderStream.trim(item.get("type")).toLowerCase(Locale.ROOT);
            if ("function_call".equals(itype)) {
                if (!hadTools || !hasMatchingTool(item)) {
                    ToolAcc acc = tools.computeIfAbsent(toolKey(Map.of(), item), k -> new ToolAcc());
                    absorbFunctionCall(acc, item, true);
                }
                continue;
            }
            if ("reasoning".equals(itype) && !hadThink) {
                String think = OpenAiSseCollector.thinkingText(item.get("summary"), item.get("content"),
                        item.get("text"));
                appendThinking(think, out);
                continue;
            }
            if (!hadText && ("message".equals(itype) || itype.isEmpty())) {
                harvestMessage(item, out);
            }
        }
    }

    private void harvestMessage(Map<String, Object> item, List<Piece> out) {
        Object content = item.get("content");
        if (content == null) {
            content = item.get("text");
        }
        if (content instanceof String s) {
            appendAnswer(s, out);
            return;
        }
        for (Object part : ProviderStream.asList(content)) {
            if (part instanceof String s) {
                appendAnswer(s, out);
                continue;
            }
            Map<String, Object> p = ProviderStream.asMap(part);
            String ptype = ProviderStream.trim(p.get("type")).toLowerCase(Locale.ROOT);
            String piece = ProviderStream.str(p.getOrDefault("text", p.get("thinking")));
            if (ptype.contains("reason") || ptype.contains("think") || ptype.contains("summary")) {
                appendThinking(piece, out);
            } else if ("output_text".equals(ptype) || "text".equals(ptype) || ptype.isEmpty()) {
                appendAnswer(piece, out);
            }
        }
    }

    private boolean hasMatchingTool(Map<String, Object> item) {
        String id = firstStr(item, "call_id", "callId", "id");
        if (id.isEmpty()) {
            return false;
        }
        for (ToolAcc acc : tools.values()) {
            if (id.equals(acc.id) || id.equals(acc.itemId)) {
                return true;
            }
        }
        return false;
    }

    private void takeStatus(Map<String, Object> response) {
        String status = ProviderStream.trim(response.get("status"));
        if (status.isEmpty()) {
            return;
        }
        finishReason = status;
        if ("failed".equals(status)) {
            takeError(response);
        }
        if ("incomplete".equals(status)) {
            Map<String, Object> details = ProviderStream.asMap(response.get("incomplete_details"));
            String reason = ProviderStream.trim(details.get("reason"));
            if (!reason.isEmpty()) {
                finishReason = reason;
            }
        }
    }

    private void takeError(Map<String, Object> map) {
        Map<String, Object> response = ProviderStream.asMap(map.get("response"));
        Map<String, Object> err = ProviderStream.asMap(map.get("error"));
        if (err.isEmpty()) {
            err = ProviderStream.asMap(response.get("error"));
        }
        String msg = firstStr(err, "message", "msg");
        if (msg.isEmpty()) {
            msg = firstStr(map, "message", "msg");
        }
        if (msg.isEmpty()) {
            msg = "response failed";
        }
        errorMessage = msg;
        String code = firstStr(err, "code");
        if (code.isEmpty()) {
            code = firstStr(map, "code");
        }
        if (code.isEmpty()) {
            code = ProviderStream.trim(response.get("status"));
        }
        errorReason = code.isEmpty() ? "error" : code;
        if (finishReason == null || finishReason.isBlank()) {
            finishReason = errorReason;
        }
    }

    private void takeUsage(Object raw) {
        Map<String, Object> map = ProviderStream.asMap(raw);
        if (map.isEmpty()) {
            return;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        Object prompt = first(map.get("prompt_tokens"), map.get("input_tokens"));
        Object completion = first(map.get("completion_tokens"), map.get("output_tokens"));
        Object total = map.get("total_tokens");
        if (prompt != null) {
            out.put("prompt_tokens", prompt);
        }
        if (completion != null) {
            out.put("completion_tokens", completion);
        }
        if (total != null) {
            out.put("total_tokens", total);
        } else if (prompt instanceof Number p && completion instanceof Number c) {
            out.put("total_tokens", p.longValue() + c.longValue());
        }
        Map<String, Object> details = ProviderStream.asMap(map.get("output_tokens_details"));
        if (details.isEmpty()) {
            details = ProviderStream.asMap(map.get("completion_tokens_details"));
        }
        Object reason = details.get("reasoning_tokens");
        if (reason instanceof Number n && n.longValue() > 0) {
            out.put("reasoning_tokens", n);
        } else if (reason != null && !(reason instanceof Number)
                && !ProviderStream.trim(reason).isEmpty()
                && !"0".equals(ProviderStream.trim(reason))) {
            out.put("reasoning_tokens", reason);
        }
        if (!out.isEmpty()) {
            usage = out;
        }
    }

    private void appendAnswer(String piece, List<Piece> out) {
        if (piece == null || piece.isEmpty()) {
            return;
        }
        text.append(piece);
        ProviderStream.emitAndRecord(helper, port, "answer", piece, out);
    }

    private void appendThinking(String piece, List<Piece> out) {
        if (piece == null || piece.isEmpty()) {
            return;
        }
        liveThoughts.add(Map.of("type", "thinking", "text", piece));
        ProviderStream.emitAndRecord(helper, port, "thinking", piece, out);
    }

    private static String deltaText(Map<String, Object> map) {
        Object delta = map.get("delta");
        if (delta instanceof String s) {
            return s;
        }
        Map<String, Object> nested = ProviderStream.asMap(delta);
        if (!nested.isEmpty()) {
            String t = ProviderStream.str(nested.get("text"));
            if (t.isEmpty()) {
                t = ProviderStream.str(nested.get("delta"));
            }
            return t;
        }
        return ProviderStream.str(map.get("text"));
    }

    private static Map<String, Object> itemOf(Map<String, Object> map) {
        Map<String, Object> item = ProviderStream.asMap(map.get("item"));
        return item.isEmpty() ? map : item;
    }

    private static String toolKey(Map<String, Object> map, Map<String, Object> item) {
        String id = firstStr(map, "item_id", "itemId");
        if (id.isEmpty()) {
            id = firstStr(item, "id", "call_id", "callId");
        }
        if (!id.isEmpty()) {
            return id;
        }
        Object idx = map.get("output_index") != null ? map.get("output_index") : item.get("output_index");
        return "idx:" + (idx == null ? "0" : idx);
    }

    private static String firstStr(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            String s = ProviderStream.trim(m.get(k));
            if (!s.isEmpty()) {
                return s;
            }
        }
        return "";
    }

    private static Object first(Object a, Object b) {
        return a != null ? a : b;
    }

    @Override
    public Map<String, Object> toAssistant() {
        if (errorMessage != null && !errorMessage.isBlank()) {
            return LlmAssistant.errorResponse(errorMessage, errorReason == null ? "error" : errorReason);
        }
        List<String> textParts = text.length() > 0 ? List.of(text.toString()) : List.of();
        List<Map<String, Object>> toolCalls = materialize();
        return LlmAssistant.makeResponse(
                textParts.isEmpty() ? List.of() : new ArrayList<>(textParts),
                toolCalls.isEmpty() ? null : toolCalls,
                liveThoughts.isEmpty() ? null : liveThoughts,
                null, finishReason, usage);
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
        String itemId;
        String id;
        String name = "";
        String args = "";
    }
}
