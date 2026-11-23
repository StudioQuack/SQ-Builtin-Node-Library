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
        return text.startsWith(SUMMARIZED_PREFIX) && text.endsWith(SUMMARIZED_SUFFIX);
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

    static String fallbackSummary(List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        if (messages != null) {
            for (Map<String, Object> m : messages) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(serializeForSummarization(m));
            }
        }
        String serialized = sb.toString().replace('\n', ' ').strip();
        if (serialized.length() > 1000) {
            return serialized.substring(0, 1000) + " ...[truncated]";
        }
        return serialized.isEmpty() ? "(empty summary)" : serialized;
    }

    static String callSummarizer(AgentSession session, List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty() || session == null || session.llmNodeId.isBlank()) {
            return fallbackSummary(messages);
        }
        List<String> blocks = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            blocks.add(serializeForSummarization(m));
        }
        String joined = String.join("\n\n", blocks);
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

    static Map<String, Object> makeSummarySystemMessage(String summaryText) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "system");
        m.put("content", wrapSummarized(summaryText));
        m.put(SUMMARIZED_FLAG_KEY, Boolean.TRUE);
        m.put(SUMMARY_KIND_KEY, SUMMARY_KIND_VALUE);
        return m;
    }

    static boolean isSummarySystemBlock(Object msg) {
        if (!(msg instanceof Map<?, ?> map)) {
            return false;
        }
        if (!"system".equals(map.get("role"))) {
            return false;
        }
        return isSummarized(msg);
    }

    /**
     * Compact history under {@code contextWindow} tokens. {@code force} skips the
     * local estimate short-circuit (provider overflow retry).
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
        if (first != null && "system".equals(first.get("role")) && !isSummarySystemBlock(first)) {
            coreSystem = first;
            rest = new ArrayList<>(history.subList(1, history.size()));
        }

        List<Map<String, Object>> existingSummaryBlocks = new ArrayList<>();
        List<Map<String, Object>> freshMessages = new ArrayList<>();
        for (Map<String, Object> m : rest) {
            if (isSummarySystemBlock(m)) {
                existingSummaryBlocks.add(m);
            } else {
                freshMessages.add(m);
            }
        }

        List<Map<String, Object>> newHistory = new ArrayList<>(history);
        List<Map<String, Object>> allBlocks;
        if (!freshMessages.isEmpty()) {
            String freshSummary = callSummarizer(session, freshMessages);
            Map<String, Object> newBlock = makeSummarySystemMessage(freshSummary);
            allBlocks = new ArrayList<>(existingSummaryBlocks);
            allBlocks.add(newBlock);
            newHistory = new ArrayList<>();
            if (coreSystem != null) {
                newHistory.add(coreSystem);
            }
            newHistory.addAll(allBlocks);
            AgentMemory.persistInMemoryCache(newHistory, session);
            if (historyTokenEstimate(newHistory) <= contextWindow) {
                return newHistory;
            }
        } else {
            allBlocks = existingSummaryBlocks;
        }

        if (allBlocks.size() > 1) {
            String meta = callSummarizer(session, allBlocks);
            Map<String, Object> single = makeSummarySystemMessage(meta);
            newHistory = new ArrayList<>();
            if (coreSystem != null) {
                newHistory.add(coreSystem);
            }
            newHistory.add(single);
            AgentMemory.persistInMemoryCache(newHistory, session);
        }
        return newHistory;
    }
}
