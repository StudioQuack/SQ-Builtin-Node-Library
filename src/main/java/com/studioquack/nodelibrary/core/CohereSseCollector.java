package com.studioquack.nodelibrary.core;

import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code cohere-sse}: Cohere Chat v2 {@code POST /v2/chat} SSE events ({@code stream:true}).
 *
 * <p>{@code content-delta} → answer ({@code delta.message.content.text}); content chunks
 * carrying {@code thinking} (command-a-reasoning) → thinking;
 * {@code tool-plan-delta} → thinking ({@code delta.message.tool_plan});
 * {@code tool-call-start}/{@code tool-call-delta}/{@code tool-call-end} assemble
 * {@code tool_calls} {@code {id, name, args, type:tool_call}} (args = parsed JSON object);
 * {@code message-end} → finish_reason (+ usage). Unknown events ignored.
 *
 * <p>Event names follow the current v2 streaming guide (hyphenated). Underscore
 * aliases are accepted so a proxy that rewrites names still collects.
 */
final class CohereSseCollector implements StreamCollector {

    private final String port;
    private final StudioQuackHelper helper;
    private final StringBuilder text = new StringBuilder();
    private final List<Map<String, Object>> liveThoughts = new ArrayList<>();
    private final TreeMap<Integer, ToolAcc> tools = new TreeMap<>();
    private String finishReason;
    private Map<String, Object> usage;

    CohereSseCollector(String port, StudioQuackHelper helper) {
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
        if ("content-delta".equals(type) || "content_delta".equals(type)) {
            // command-a-reasoning streams reasoning as content chunks carrying a
            // `thinking` field ({type:thinking, thinking:…}) on the same content-delta
            // event as answer text — route each to its own channel.
            String think = thinkingText(deltaContent(map));
            if (!think.isEmpty()) {
                liveThoughts.add(Map.of("type", "thinking", "text", think));
                ProviderStream.emitAndRecord(helper, port, "thinking", think, out);
            }
            String piece = contentText(map);
            if (!piece.isEmpty()) {
                text.append(piece);
                ProviderStream.emitAndRecord(helper, port, "answer", piece, out);
            }
            return out;
        }
        if ("tool-plan-delta".equals(type) || "tool_plan_delta".equals(type)) {
            String plan = toolPlanText(map);
            if (!plan.isEmpty()) {
                liveThoughts.add(Map.of("type", "thinking", "text", plan));
                ProviderStream.emitAndRecord(helper, port, "thinking", plan, out);
            }
            return out;
        }
        if ("tool-call-start".equals(type) || "tool_call_start".equals(type)) {
            startTool(map);
            return out;
        }
        if ("tool-call-delta".equals(type) || "tool_call_delta".equals(type)) {
            deltaTool(map);
            return out;
        }
        if ("tool-call-end".equals(type) || "tool_call_end".equals(type)) {
            return out;
        }
        if ("message-end".equals(type) || "message_end".equals(type)) {
            takeEnd(map);
            return out;
        }
        // Non-stream envelope: message.content[] / message.tool_calls already assembled.
        if (map.get("message") instanceof Map<?, ?> && map.get("delta") == null) {
            Map<String, Object> message = ProviderStream.asMap(map.get("message"));
            String think = thinkingText(message.get("content"));
            if (!think.isEmpty()) {
                liveThoughts.add(Map.of("type", "thinking", "text", think));
                ProviderStream.emitAndRecord(helper, port, "thinking", think, out);
            }
            String piece = contentText(message);
            if (piece.isEmpty()) {
                piece = nestedContentText(message.get("content"));
            }
            if (!piece.isEmpty()) {
                text.append(piece);
                ProviderStream.emitAndRecord(helper, port, "answer", piece, out);
            }
            String plan = ProviderStream.str(message.get("tool_plan"));
            if (!plan.isEmpty()) {
                liveThoughts.add(Map.of("type", "thinking", "text", plan));
                ProviderStream.emitAndRecord(helper, port, "thinking", plan, out);
            }
            absorbEnvelopeTools(message.get("tool_calls"));
            String fr = ProviderStream.trim(map.get("finish_reason"));
            if (fr.isEmpty()) {
                fr = ProviderStream.trim(map.get("finishReason"));
            }
            if (!fr.isEmpty()) {
                finishReason = fr;
            }
            takeUsage(map.get("usage"));
        }
        return out;
    }

    private String contentText(Map<String, Object> map) {
        Object content = deltaContent(map);
        String t = nestedContentText(content);
        if (!t.isEmpty()) {
            return t;
        }
        Object direct = ProviderStream.dig(map, "delta", "message", "content", "text");
        return ProviderStream.str(direct);
    }

    private static Object deltaContent(Map<String, Object> map) {
        Object content = ProviderStream.dig(map, "delta", "message", "content");
        if (content == null) {
            content = ProviderStream.dig(map, "delta", "content");
        }
        return content;
    }

    /**
     * Collect {@code thinking} fields out of a content chunk (map or list of chunks).
     * Plain strings and {@code text} fields are answer text and deliberately excluded.
     * A list-valued {@code thinking} (nested text chunks) is unwrapped recursively.
     */
    private static String thinkingText(Object v) {
        StringBuilder sb = new StringBuilder();
        collectThinking(v, sb, 0);
        return sb.toString();
    }

    private static void collectThinking(Object v, StringBuilder sb, int depth) {
        if (v == null || depth > 4) {
            return;
        }
        if (v instanceof List<?> list) {
            for (Object item : list) {
                collectThinking(item, sb, depth + 1);
            }
            return;
        }
        Map<String, Object> m = ProviderStream.asMap(v);
        if (m.isEmpty()) {
            return;
        }
        Object thinking = m.get("thinking");
        if (thinking instanceof String s) {
            sb.append(s);
        } else {
            collectThinking(thinking, sb, depth + 1);
        }
    }

    private static String nestedContentText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        Map<String, Object> m = ProviderStream.asMap(content);
        if (!m.isEmpty()) {
            String t = ProviderStream.str(m.get("text"));
            if (!t.isEmpty()) {
                return t;
            }
        }
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                sb.append(nestedContentText(item));
            }
            return sb.toString();
        }
        return "";
    }

    private static String toolPlanText(Map<String, Object> map) {
        Object plan = ProviderStream.dig(map, "delta", "message", "tool_plan");
        if (plan == null) {
            plan = ProviderStream.dig(map, "delta", "tool_plan");
        }
        if (plan == null) {
            plan = map.get("tool_plan");
        }
        return ProviderStream.str(plan);
    }

    private void startTool(Map<String, Object> map) {
        int idx = toolIndex(map);
        ToolAcc acc = tools.computeIfAbsent(idx, k -> new ToolAcc());
        Map<String, Object> tc = toolCallPayload(map);
        if (tc.get("id") != null) {
            acc.id = ProviderStream.str(tc.get("id"));
        }
        Map<String, Object> fn = ProviderStream.asMap(tc.get("function"));
        if (fn.get("name") != null) {
            acc.name = ProviderStream.str(fn.get("name"));
        } else if (tc.get("name") != null) {
            acc.name = ProviderStream.str(tc.get("name"));
        }
        Object args = fn.get("arguments") != null ? fn.get("arguments") : tc.get("arguments");
        if (args != null) {
            acc.args = args instanceof String s ? s : studioquack.Json.write(args);
        }
    }

    private void deltaTool(Map<String, Object> map) {
        int idx = toolIndex(map);
        ToolAcc acc = tools.computeIfAbsent(idx, k -> new ToolAcc());
        Map<String, Object> tc = toolCallPayload(map);
        Map<String, Object> fn = ProviderStream.asMap(tc.get("function"));
        if (fn.get("name") != null && (acc.name == null || acc.name.isBlank())) {
            acc.name = ProviderStream.str(fn.get("name"));
        }
        Object args = fn.get("arguments") != null ? fn.get("arguments") : tc.get("arguments");
        if (args != null) {
            acc.args = acc.args + (args instanceof String s ? s : studioquack.Json.write(args));
        }
    }

    /**
     * v2 streams a single tool-call <em>object</em> at {@code delta.message.tool_calls}
     * (not an array). A list is still accepted for the non-stream envelope.
     */
    private static Map<String, Object> toolCallPayload(Map<String, Object> map) {
        Object raw = ProviderStream.dig(map, "delta", "message", "tool_calls");
        if (raw == null) {
            raw = ProviderStream.dig(map, "delta", "tool_calls");
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return ProviderStream.asMap(list.get(0));
        }
        return ProviderStream.asMap(raw);
    }

    private void absorbEnvelopeTools(Object raw) {
        List<Object> list = ProviderStream.asList(raw);
        if (list.isEmpty() && raw instanceof Map<?, ?>) {
            list = List.of(raw);
        }
        int i = 0;
        for (Object item : list) {
            Map<String, Object> tc = ProviderStream.asMap(item);
            if (tc.isEmpty()) {
                continue;
            }
            int idx = i++;
            Object ix = tc.get("index");
            if (ix instanceof Number n) {
                idx = n.intValue();
            }
            ToolAcc acc = tools.computeIfAbsent(idx, k -> new ToolAcc());
            if (tc.get("id") != null) {
                acc.id = ProviderStream.str(tc.get("id"));
            }
            Map<String, Object> fn = ProviderStream.asMap(tc.get("function"));
            if (fn.get("name") != null) {
                acc.name = ProviderStream.str(fn.get("name"));
            } else if (tc.get("name") != null) {
                acc.name = ProviderStream.str(tc.get("name"));
            }
            Object args = fn.get("arguments") != null ? fn.get("arguments")
                    : (tc.get("arguments") != null ? tc.get("arguments") : tc.get("args"));
            if (args != null) {
                acc.args = args instanceof String s ? s : studioquack.Json.write(args);
            }
        }
    }

    private int toolIndex(Map<String, Object> map) {
        Object i = map.get("index");
        if (i instanceof Number n) {
            return n.intValue();
        }
        if (i != null) {
            try {
                return Integer.parseInt(ProviderStream.trim(i));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return tools.isEmpty() ? 0 : tools.lastKey();
    }

    private void takeEnd(Map<String, Object> map) {
        Map<String, Object> delta = ProviderStream.asMap(map.get("delta"));
        String fr = ProviderStream.trim(delta.get("finish_reason"));
        if (fr.isEmpty()) {
            fr = ProviderStream.trim(map.get("finish_reason"));
        }
        if (fr.isEmpty()) {
            fr = ProviderStream.trim(delta.get("finishReason"));
        }
        if (!fr.isEmpty()) {
            finishReason = fr;
        }
        Object u = delta.get("usage") != null ? delta.get("usage") : map.get("usage");
        takeUsage(u);
    }

    private void takeUsage(Object raw) {
        Map<String, Object> map = ProviderStream.asMap(raw);
        if (map.isEmpty()) {
            return;
        }
        Map<String, Object> billed = ProviderStream.asMap(map.get("billed_units"));
        Map<String, Object> tokens = ProviderStream.asMap(map.get("tokens"));
        Map<String, Object> out = new LinkedHashMap<>();
        Object prompt = first(map.get("prompt_tokens"), tokens.get("input_tokens"), billed.get("input_tokens"));
        Object completion = first(map.get("completion_tokens"), tokens.get("output_tokens"), billed.get("output_tokens"));
        Object total = first(map.get("total_tokens"), null, null);
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
        if (!out.isEmpty()) {
            usage = out;
        }
    }

    private static Object first(Object a, Object b, Object c) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        return c;
    }

    @Override
    public Map<String, Object> toAssistant() {
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
        String id;
        String name = "";
        String args = "";
    }
}
