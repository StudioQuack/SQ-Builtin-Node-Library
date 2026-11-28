package com.studioquack.nodelibrary.nodes.aiagent;

import studioquack.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context-window compaction (Python {@code summarize_for_context_window}).
 * Token estimate is {@code chars / 4}. {@code contextWindow <= 0} disables this path.
 */
final class AgentSummarizer {

    static final String SUMMARIZED_PREFIX = "Summarized((";
    static final String SUMMARIZED_SUFFIX = "))";
    static final String SUMMARIZED_FLAG_KEY = "_summarized";
    static final String SUMMARY_KIND_KEY = "_summary_kind";
    static final String SUMMARY_KIND_VALUE = "summarized_chat_history";
    /** Cap summarizer input (~6k tokens) so compacting a giant tool output never overflows the summarizer call itself. */
    static final int MAX_SUMMARIZER_INPUT_CHARS = 24_000;
    static final int MAX_FALLBACK_SUMMARY_CHARS = 2_000;
    static final int MAX_PRESERVED_USER_CHARS = 1_000;

    private AgentSummarizer() {
        // namespace — never instantiated
    }

    static int estimateTokens(Object text) {
        if (text == null) {
            return 0;
        }
        String s = text instanceof String ? (String) text : String.valueOf(text);
        if (s.isEmpty()) {
            return 0;
        }
        return Math.max(1, s.length() / 4);
    }

    static int messageTokenEstimate(Object msg) {
        if (!(msg instanceof Map<?, ?> map)) {
            return estimateTokens(String.valueOf(msg));
        }
        int total = 8;
        Object content = map.get("content");
        if (content instanceof String s) {
            total += estimateTokens(s);
        } else if (content instanceof List<?> list) {
            for (Object b : list) {
                if (b instanceof Map<?, ?> bm) {
                    total += estimateTokens(bm.get("text"));
                    total += estimateTokens(bm.get("data"));
                } else {
                    total += estimateTokens(String.valueOf(b));
                }
            }
        }
        for (Object tc : AgentJson.asList(map.get("tool_calls"))) {
            try {
                total += estimateTokens(Json.write(tc));
            } catch (Exception e) {
                total += estimateTokens(String.valueOf(tc));
            }
        }
        for (Object t : AgentJson.asList(map.get("thinking"))) {
            if (t instanceof Map<?, ?> tm) {
                Object txt = tm.get("text");
                if (txt == null) {
                    txt = tm.get("thinking");
                }
                total += estimateTokens(txt == null ? "" : txt);
            }
        }
        return total;
    }

    static int historyTokenEstimate(List<Map<String, Object>> history) {
        int n = 0;
        if (history == null) {
            return 0;
        }
        for (Map<String, Object> m : history) {
            n += messageTokenEstimate(m);
        }
        return n;
    }

    static boolean isSummarized(Object msg) {
        if (!(msg instanceof Map<?, ?> map)) {
            return false;
        }
        if (Boolean.TRUE.equals(map.get(SUMMARIZED_FLAG_KEY))) {
            return true;
        }
        String text = AgentJson.extractTextFromContent(map.get("content"));
        return text.startsWith(SUMMARIZED_PREFIX) && text.contains(SUMMARIZED_SUFFIX);
    }

    static String wrapSummarized(String text) {
        String s = text == null ? "" : text.strip();
        if (s.startsWith(SUMMARIZED_PREFIX) && s.endsWith(SUMMARIZED_SUFFIX)) {
            return s;
        }
        return SUMMARIZED_PREFIX + s + SUMMARIZED_SUFFIX;
    }

    static String serializeForSummarization(Object msg) {
        if (!(msg instanceof Map<?, ?> map)) {
            return String.valueOf(msg);
        }
        String role = AgentJson.str(map.get("role"));
        if (role.isBlank()) {
            role = "unknown";
        }
        List<String> parts = new ArrayList<>();
        parts.add("=== " + role.toUpperCase() + " ===");
        Object content = map.get("content");
        if (content instanceof String cs && !cs.isBlank()) {
            parts.add(cs);
        } else if (content instanceof List<?> list) {
            for (Object b : list) {
                if (b instanceof Map<?, ?> bm) {
                    String t = AgentJson.str(bm.get("text"));
                    if (!t.isBlank()) {
                        parts.add(t);
                    } else if (AgentJson.MEDIA_BLOCK_KINDS.contains(AgentJson.str(bm.get("type")))) {
                        Object label = bm.get("name");
                        if (label == null || AgentJson.str(label).isBlank()) {
                            label = bm.get("mime_type");
                        }
                        if (label == null || AgentJson.str(label).isBlank()) {
                            label = bm.get("type");
                        }
                        parts.add("[" + bm.get("type") + " attachment '" + label + "']");
                    }
                } else if (b instanceof String bs) {
                    parts.add(bs);
                }
            }
        }
        for (Object tcObj : AgentJson.asList(map.get("tool_calls"))) {
            Map<String, Object> tc = AgentJson.asMap(tcObj);
            String name = AgentJson.str(tc.get("name"));
            if (name.isBlank()) {
                name = "unknown_tool";
            }
            Object args = tc.get("args");
            if (args == null) {
                args = Map.of();
            }
            String argsStr;
            try {
                argsStr = Json.write(args);
            } catch (Exception e) {
                argsStr = String.valueOf(args);
            }
            parts.add("[tool_call] " + name + "(" + argsStr + ")");
        }
        if ("tool".equals(role) && map.get("name") != null) {
            parts.add("[tool_name: " + map.get("name") + "]");
        }
        return String.join("\n", parts);
    }

    /**
     * Serialize a batch of messages within {@code maxTotalChars}, budgeting per message so a
     * single multi-megabyte tool output is truncated cleanly instead of crowding out every
     * other turn or overflowing the summarizer LLM call.
     */
    static String serializeBatchForSummarization(List<Map<String, Object>> messages, int maxTotalChars) {
        if (messages == null || messages.isEmpty() || maxTotalChars <= 0) {
            return "";
        }
        int perMessageCap = Math.max(300, maxTotalChars / messages.size());
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> m : messages) {
            String block = serializeForSummarization(m);
            if (block.length() > perMessageCap) {
                int omitted = block.length() - perMessageCap;
                block = block.substring(0, perMessageCap) + "\n...[truncated " + omitted + " chars]";
            }
            if (out.length() > 0) {
                out.append("\n\n");
            }
            if (out.length() + block.length() > maxTotalChars) {
                int remaining = Math.max(0, maxTotalChars - out.length());
                if (remaining > 32) {
                    out.append(block, 0, remaining).append(" ...[truncated]");
                }
                break;
            }
            out.append(block);
        }
        return out.toString();
    }

    static String fallbackSummary(List<Map<String, Object>> messages) {
        String serialized = serializeBatchForSummarization(messages, MAX_FALLBACK_SUMMARY_CHARS)
                .replace('\n', ' ')
                .strip();
        return serialized.isEmpty() ? "(empty summary)" : serialized;
    }

    static String callSummarizer(AgentSession session, List<Map<String, Object>> messages) {
        return callSummarizer(session, messages, 0);
    }

    static String callSummarizer(AgentSession session, List<Map<String, Object>> messages, int contextWindow) {
        if (messages == null || messages.isEmpty() || session == null || session.llmNodeId.isBlank()) {
            return fallbackSummary(messages);
        }
        int charBudget = contextWindow > 0
                ? Math.min(MAX_SUMMARIZER_INPUT_CHARS, Math.max(2_000, contextWindow * 2))
                : MAX_SUMMARIZER_INPUT_CHARS;
        String joined = serializeBatchForSummarization(messages, charBudget);
        List<Object> sumMessages = new ArrayList<>();
        sumMessages.add(Map.of("role", "system", "content", AgentConfig.SUMMARIZER_SYSTEM));
        sumMessages.add(Map.of("role", "user",
                "content", "Summarize the following conversation messages into one paragraph:\n\n" + joined));
        Map<String, Object> payload = AgentJson.asMap(AgentJson.sanitizeForJson(
                Map.of("messages", sumMessages, "tools", List.of())));
        try {
            Object raw = session.helper.execute("model", payload, session.llmNodeId, null);
            Object found = PayloadUnwrap.findAssistantPayload(raw);
            if (found instanceof Map<?, ?> fm) {
                String text = AgentJson.extractTextFromContent(fm.get("content"));
                if (text != null && !text.isBlank()) {
                    return text.strip();
                }
            }
        } catch (Exception e) {
            AgentJson.log(session.helper, "ai-agent summarizer_failed type=" + e.getClass().getSimpleName());
        }
        return fallbackSummary(messages);
    }

    /**
     * Build the compacted history turn as {@code role: "user"} with string content.
     * Strict chat templates (Qwen3, Llama-3, DeepSeek, SGLang, vLLM, llama.cpp) reject
     * system-only message arrays with HTTP 400 ("No user query found in messages."); emitting
     * the summary as a single user message keeps every compacted history {@code [system?, user]}.
     */
    static Map<String, Object> makeSummaryUserMessage(String summaryText, String lastUserPrompt) {
        StringBuilder content = new StringBuilder(wrapSummarized(summaryText));
        if (lastUserPrompt != null && !lastUserPrompt.isBlank()) {
            content.append("\n\nContinue the task: ").append(lastUserPrompt.strip());
        } else {
            content.append("\n\nContinue your task based on the summarized conversation history above.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "user");
        m.put("content", content.toString());
        m.put(SUMMARIZED_FLAG_KEY, Boolean.TRUE);
        m.put(SUMMARY_KIND_KEY, SUMMARY_KIND_VALUE);
        return m;
    }

    static boolean isSummaryBlock(Object msg) {
        return isSummarized(msg);
    }

    private static String extractLastUserPrompt(List<Map<String, Object>> messages) {
        if (messages == null) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> m = messages.get(i);
            if (m == null || isSummaryBlock(m) || !"user".equals(m.get("role"))) {
                continue;
            }
            String text = AgentJson.extractTextFromContent(m.get("content")).strip();
            if (!text.isEmpty()) {
                return text.length() > MAX_PRESERVED_USER_CHARS
                        ? text.substring(0, MAX_PRESERVED_USER_CHARS) + " ...[truncated]"
                        : text;
            }
        }
        return null;
    }

    private static String extractSummaryBody(Map<String, Object> summaryMsg) {
        String text = AgentJson.extractTextFromContent(summaryMsg.get("content")).strip();
        if (text.startsWith(SUMMARIZED_PREFIX)) {
            int end = text.indexOf(SUMMARIZED_SUFFIX, SUMMARIZED_PREFIX.length());
            if (end >= SUMMARIZED_PREFIX.length()) {
                return text.substring(SUMMARIZED_PREFIX.length(), end).strip();
            }
        }
        return text;
    }

    /**
     * Compact history under {@code contextWindow} tokens. {@code force} skips the
     * local estimate short-circuit (provider overflow retry). Always preserves at least one
     * {@code role: "user"} message so strict chat templates never fail with
     * {@code No user query found in messages}.
     */
    static List<Map<String, Object>> summarizeForContextWindow(AgentSession session,
                                                               List<Map<String, Object>> history,
                                                               int contextWindow, boolean force) {
        if (contextWindow <= 0 || session == null || session.llmNodeId.isBlank()
                || history == null || history.isEmpty()) {
            return history;
        }
        if (!force && historyTokenEstimate(history) <= contextWindow) {
            return history;
        }

        Map<String, Object> coreSystem = null;
        List<Map<String, Object>> rest = new ArrayList<>(history);
        Map<String, Object> first = history.get(0);
        if (first != null && "system".equals(first.get("role")) && !isSummaryBlock(first)) {
            coreSystem = first;
            rest = new ArrayList<>(history.subList(1, history.size()));
        }

        List<Map<String, Object>> existingSummaryBlocks = new ArrayList<>();
        List<Map<String, Object>> freshMessages = new ArrayList<>();
        for (Map<String, Object> m : rest) {
            if (isSummaryBlock(m)) {
                existingSummaryBlocks.add(m);
            } else {
                freshMessages.add(m);
            }
        }

        String lastUserPrompt = extractLastUserPrompt(freshMessages);
        List<String> summaryParts = new ArrayList<>();
        for (Map<String, Object> existing : existingSummaryBlocks) {
            String body = extractSummaryBody(existing);
            if (!body.isBlank()) {
                summaryParts.add(body);
            }
        }
        if (!freshMessages.isEmpty()) {
            summaryParts.add(callSummarizer(session, freshMessages, contextWindow));
        }

        String combinedSummary = String.join(" ", summaryParts).strip();
        Map<String, Object> single = makeSummaryUserMessage(combinedSummary, lastUserPrompt);
        List<Map<String, Object>> newHistory = new ArrayList<>();
        if (coreSystem != null) {
            newHistory.add(coreSystem);
        }
        newHistory.add(single);

        if ((summaryParts.size() > 1 || (freshMessages.isEmpty() && force))
                && (force || historyTokenEstimate(newHistory) > contextWindow)) {
            String meta = callSummarizer(session, List.of(single), contextWindow);
            single = makeSummaryUserMessage(meta, lastUserPrompt);
            newHistory = new ArrayList<>();
            if (coreSystem != null) {
                newHistory.add(coreSystem);
            }
            newHistory.add(single);
        }

        AgentMemory.replaceHistory(session, newHistory);
        return newHistory;
    }
}
