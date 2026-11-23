package com.studioquack.nodelibrary.nodes.aiagent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * LLM ↔ tools loop (Python {@code llm_loop}). Context-window summarization, overflow
 * retry, parallel tool dispatch (cap 8), thoughts + {@code emitPartial} fan-out.
 */
final class AgentLoop {

    static final int TOOL_MAX_WORKERS = 8;

    private AgentLoop() {
        // namespace — never instantiated
    }

    static Map<String, Object> run(AgentSession session, List<Map<String, Object>> history,
                                   int maxIterations, int contextWindow) {
        if (history == null || history.isEmpty()) {
            throw new IllegalStateException("History is empty before first LLM call.");
        }
        List<Map<String, Object>> current = new ArrayList<>(history);
        ConcurrentMap<String, String> recentCalls = new ConcurrentHashMap<>();
        int iteration = 0;
        Map<String, Object> payload = doRound(session, current, contextWindow, iteration);
        AgentMemory.addHistoryMessage(session, payload);
        current.add(payload);

        List<Object> toolCalls = AgentJson.asList(payload.get("tool_calls"));
        if (toolCalls.isEmpty()) {
            return payload;
        }

        Map<String, Object> finalPayload = null;
        int cap = maxIterations <= 0 ? AgentConfig.DEFAULT_MAX_ITERATIONS : maxIterations;
        for (int it = 0; it < cap; it++) {
            iteration = it + 1;
            emitToolCallThought(session, iteration, toolCalls);
            List<Map<String, Object>> toolOutputs = dispatchTools(session, toolCalls, recentCalls);
            current.addAll(toolOutputs);
            try {
                List<Map<String, Object>> remote = AgentMemory.getHistory(session);
                if (remote != null && remote.size() > current.size()) {
                    current = remote;
                }
            } catch (Exception ignored) {
                // keep local history
            }
            payload = doRound(session, current, contextWindow, iteration);
            AgentMemory.addHistoryMessage(session, payload);
            current.add(payload);
            toolCalls = AgentJson.asList(payload.get("tool_calls"));
            if (toolCalls.isEmpty()) {
                finalPayload = payload;
                break;
            }
        }
        return finalPayload != null ? finalPayload : payload;
    }

    static Map<String, Object> doRound(AgentSession session, List<Map<String, Object>> current,
                                       int contextWindow, int idx) {
        AgentJson.log(session.helper, "ai-agent llm_loop.round_start iteration=" + idx
                + " history_messages=" + current.size());
        if (contextWindow > 0) {
            int beforeN = current.size();
            List<Map<String, Object>> compacted = AgentSummarizer.summarizeForContextWindow(
                    session, current, contextWindow, false);
            replaceContents(current, compacted);
            if (current.size() < beforeN) {
                AgentLlm.emitThought(session, "summary", idx,
                        "History compacted: " + beforeN + " → " + current.size()
                                + " message(s) to fit the context window.",
                        null, null);
            }
        }

        Map<String, Object> payload = AgentLlm.safeCallLlm(session, current);

        if (contextWindow > 0 && PayloadUnwrap.payloadIndicatesContextOverflow(payload)) {
            int beforeTokens = AgentSummarizer.historyTokenEstimate(current);
            AgentJson.log(session.helper, "ai-agent context_overflow local_tokens=" + beforeTokens);
            List<Map<String, Object>> compacted = AgentSummarizer.summarizeForContextWindow(
                    session, current, contextWindow, true);
            replaceContents(current, compacted);
            int afterTokens = AgentSummarizer.historyTokenEstimate(current);
            if (afterTokens < beforeTokens) {
                AgentLlm.emitThought(session, "summary", idx,
                        "Provider reported context overflow. Force-summarized history "
                                + beforeTokens + " → " + afterTokens + " tokens, retrying.",
                        null, null);
                payload = AgentLlm.safeCallLlm(session, current);
            } else {
                AgentJson.log(session.helper, "ai-agent context_overflow_uncompressible");
            }
        }

        // Issue one LLM call per round and accept the returned payload without retrying empty replies.

        publishThinkingBlocks(session, payload, idx);
        return payload;
    }

    static void publishThinkingBlocks(AgentSession session, Map<String, Object> payload, int idx) {
        if (payload == null) {
            return;
        }
        for (String sourceKey : List.of("thoughts", "thinking")) {
            Object src = payload.get(sourceKey);
            if (!(src instanceof List<?> list)) {
                continue;
            }
            // Merge streamed thinking fragments into a single thoughts-port delivery per round.
            String merged = mergeThinkingBlocks(list);
            if (!merged.isEmpty()) {
                AgentLlm.emitThought(session, "thinking", idx, merged, null, null);
                break;
            }
        }
    }

    /**
     * Concatenate the text of every thinking block in stream-append order. Each block's text
     * is read from {@code text} (falling back to {@code thinking}); blank and non-map entries
     * are skipped. Returns the empty string when nothing usable is present.
     */
    static String mergeThinkingBlocks(List<?> blocks) {
        StringBuilder merged = new StringBuilder();
        for (Object t : blocks) {
            if (!(t instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> tm = AgentJson.asMap(t);
            Object text = tm.get("text");
            if (text == null) {
                text = tm.get("thinking");
            }
            if (text != null && !AgentJson.str(text).isBlank()) {
                merged.append(AgentJson.str(text));
            }
        }
        return merged.toString();
    }

    static void emitToolCallThought(AgentSession session, int iteration, List<Object> toolCalls) {
        try {
            List<Map<String, Object>> summary = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Object tcObj : toolCalls) {
                Map<String, Object> tc = AgentJson.asMap(tcObj);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", tc.get("id") == null ? "" : tc.get("id"));
                row.put("name", tc.get("name") == null ? "" : tc.get("name"));
                row.put("args", tc.get("args") == null ? Map.of() : tc.get("args"));
                summary.add(row);
                String n = AgentJson.str(row.get("name"));
                if (!n.isBlank()) {
                    names.add(n);
                }
            }
            AgentLlm.emitThought(session, "tool_call", iteration,
                    "Calling tools: " + String.join(", ", names), null, summary);
        } catch (Exception e) {
            AgentJson.log(session.helper, "ai-agent tool_call_emit_failed type="
                    + e.getClass().getSimpleName());
        }
    }

    static List<Map<String, Object>> dispatchTools(AgentSession session, List<Object> toolCalls,
                                                   ConcurrentMap<String, String> recentCalls) {
        List<Map<String, Object>> toolOutputs = new ArrayList<>();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return toolOutputs;
        }
        int workers = Math.min(TOOL_MAX_WORKERS, toolCalls.size());
        try (ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, workers))) {
            List<Future<Map<String, Object>>> futs = new ArrayList<>();
            List<Map<String, Object>> tcs = new ArrayList<>();
            for (Object tcObj : toolCalls) {
                Map<String, Object> tc = AgentJson.asMap(tcObj);
                tcs.add(tc);
                futs.add(ex.submit(() -> AgentTools.processOne(session, tc, recentCalls)));
            }
            for (int i = 0; i < futs.size(); i++) {
                Future<Map<String, Object>> fut = futs.get(i);
                try {
                    Map<String, Object> r = fut.get();
                    if (r != null) {
                        toolOutputs.add(r);
                    }
                } catch (Exception ex2) {
                    Map<String, Object> tc = tcs.get(i);
                    Map<String, Object> err = AgentTools.toolResultMessage(
                            AgentJson.str(tc.get("id")), AgentJson.str(tc.get("name")),
                            AgentJson.jsonString(Map.of("error", "Future failure: " + ex2.getMessage())));
                    AgentMemory.addHistoryMessage(session, err);
                    toolOutputs.add(err);
                }
            }
        }
        return toolOutputs;
    }

    static void replaceContents(List<Map<String, Object>> target, List<Map<String, Object>> src) {
        if (src == target) {
            return;
        }
        target.clear();
        if (src != null) {
            target.addAll(src);
        }
    }
}
