package com.studioquack.nodelibrary.nodes.aiagent;

import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chat history: optional remote {@code memory} FUNCTION port, else a
 * {@link ConcurrentHashMap} keyed by per-invoke {@code conversation_id} (UUID).
 *
 * <p>Python used a process-global dict. Concurrent dispatches here cannot clobber
 * each other because each {@link AiAgentNode#handle} allocates a fresh UUID and
 * {@link #release}s it when the invoke ends.
 */
final class AgentMemory {

    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>> CACHE =
            new ConcurrentHashMap<>();

    private AgentMemory() {
        // namespace — never instantiated
    }

    static void release(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            CACHE.remove(conversationId);
        }
    }

    static CopyOnWriteArrayList<Map<String, Object>> bucket(String conversationId) {
        String id = conversationId == null ? "" : conversationId;
        return CACHE.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalizeMessageForStorage(Object msg, String myNodeId) {
        Map<String, Object> m;
        if (msg instanceof String s) {
            m = new LinkedHashMap<>();
            m.put("role", "system");
            m.put("content", s);
        } else if (msg instanceof Map<?, ?> map) {
            Map<String, Object> src = AgentJson.asMap(map);
            if (src.containsKey("type") && !src.containsKey("role")) {
                Map<String, Object> data = src.get("data") instanceof Map<?, ?>
                        ? AgentJson.asMap(src.get("data")) : src;
                m = new LinkedHashMap<>();
                m.put("role", typeToRole(AgentJson.str(src.get("type"))));
                m.put("content", data.get("content"));
                for (String extra : List.of("tool_calls", "thinking", "tool_call_id", "name", "files",
                        "additional_kwargs")) {
                    if (data.get(extra) != null) {
                        m.put(extra, data.get(extra));
                    }
                }
            } else {
                m = new LinkedHashMap<>(src);
            }
        } else {
            m = new LinkedHashMap<>();
            m.put("role", "user");
            m.put("content", AgentJson.str(msg));
        }
        if (myNodeId != null && !myNodeId.isBlank()) {
            m.put("_agent_id", myNodeId);
        }
        Object sanitized = AgentJson.sanitizeForJson(m);
        return sanitized instanceof Map<?, ?> sm ? (Map<String, Object>) sm : m;
    }

    private static String typeToRole(String type) {
        return switch (type) {
            case "ai", "AIMessage", "assistant" -> "assistant";
            case "human", "HumanMessage" -> "user";
            case "system", "SystemMessage" -> "system";
            case "tool", "ToolMessage" -> "tool";
            default -> "user";
        };
    }

    static void addHistoryMessage(AgentSession session, Object messageOrList) {
        if (session == null) {
            return;
        }
        List<Object> msgs;
        if (messageOrList instanceof List<?> list) {
            msgs = new ArrayList<>();
            for (Object o : list) {
                msgs.add(o);
            }
        } else {
            msgs = List.of(messageOrList);
        }
        for (Object m : msgs) {
            if (m instanceof List<?>) {
                addHistoryMessage(session, m);
                continue;
            }
            Map<String, Object> msgDict = normalizeMessageForStorage(m, session.myNodeId);
            if (session.useRemoteMemory && !session.memoryId.isBlank()) {
                try {
                    Map<String, Object> req = AgentPortMapping.memoryAppend(session, msgDict);
                    session.helper.execute("memory", req, session.memoryId, null);
                } catch (Exception e) {
                    AgentJson.log(session.helper, "ai-agent memory_add_fallback type="
                            + e.getClass().getSimpleName());
                    bucket(session.conversationId).add(msgDict);
                }
            } else {
                bucket(session.conversationId).add(msgDict);
            }
        }
    }

    static List<Map<String, Object>> getHistory(AgentSession session) {
        if (session != null && session.useRemoteMemory && !session.memoryId.isBlank()) {
            try {
                Map<String, Object> req = AgentPortMapping.memoryRead(session);
                Object resp = session.helper.execute("memory", req, session.memoryId, null);
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object m : AgentPortMapping.unwrapListPayload(resp)) {
                    if (m != null) {
                        out.add(normalizeMessageForStorage(m, null));
                    }
                }
                return out;
            } catch (Exception e) {
                AgentJson.log(session.helper, "ai-agent memory_get_fallback type="
                        + e.getClass().getSimpleName());
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        String id = session == null ? "" : session.conversationId;
        for (Map<String, Object> m : bucket(id)) {
            out.add(normalizeMessageForStorage(m, null));
        }
        return out;
    }

    static void persistInMemoryCache(List<Map<String, Object>> history, AgentSession session) {
        if (session == null || session.conversationId.isBlank() || session.useRemoteMemory) {
            return;
        }
        try {
            CopyOnWriteArrayList<Map<String, Object>> copy = new CopyOnWriteArrayList<>();
            if (history != null) {
                for (Map<String, Object> m : history) {
                    Object s = AgentJson.sanitizeForJson(m);
                    if (s instanceof Map<?, ?>) {
                        copy.add(AgentJson.asMap(s));
                    }
                }
            }
            CACHE.put(session.conversationId, copy);
        } catch (Exception e) {
            AgentJson.log(session.helper, "ai-agent cache_persist_failed type="
                    + e.getClass().getSimpleName());
        }
    }

    /**
     * Build LLM-facing history. Handle-bearing files hoist as {@code [file: name]}
     * text (bytes ride {@code session.inputFiles}); handle-less files stay content
     * blocks. Old stored system messages are demoted to user.
     *
     * <p>When {@code threadMessages} is non-empty (conversation mode: the Duckbot
     * start {@code msg} port's JSON thread, parsed from OUR OWN prompt input), the
     * thread IS the turn's history and its last entry is the current user message —
     * a designer static prompt prepends to that last text and files hoist onto it.
     */
    static Prepared prepareInitialHistory(AgentSession session, String role, String instructions,
                                          String userText, List<Map<String, Object>> userFiles,
                                          String relevantDocuments,
                                          List<Map<String, Object>> threadMessages) {
        List<String> systemParts = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            systemParts.add(role);
        }
        if (instructions != null && !instructions.isBlank() && !instructions.equals(userText)) {
            systemParts.add(instructions);
            systemParts.add(AgentConfig.STRICT_TOOL_CALL_RULES);
        }
        Map<String, Object> systemMessage = null;
        if (!systemParts.isEmpty()) {
            systemMessage = new LinkedHashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", String.join("\n", systemParts));
        }

        List<Map<String, Object>> history = getHistory(session);
        List<Map<String, Object>> cleaned = new ArrayList<>();
        for (Map<String, Object> m : history) {
            if (m != null && "system".equals(m.get("role"))) {
                Map<String, Object> demoted = new LinkedHashMap<>();
                demoted.put("role", "user");
                demoted.put("content", m.get("content") == null ? "" : m.get("content"));
                cleaned.add(demoted);
            } else if (m != null) {
                cleaned.add(m);
            }
        }
        history = cleaned;

        Map<String, Object> relevantDocsMsg = null;
        if (relevantDocuments != null && !relevantDocuments.isBlank()) {
            relevantDocsMsg = new LinkedHashMap<>();
            relevantDocsMsg.put("role", "system");
            relevantDocsMsg.put("content", relevantDocuments);
            history.add(relevantDocsMsg);
        }

        Map<String, Object> userMessage;
        if (threadMessages != null && !threadMessages.isEmpty()) {
            List<Map<String, Object>> thread = new ArrayList<>();
            for (Map<String, Object> m : threadMessages) {
                if (m != null) {
                    thread.add(normalizeMessageForStorage(m, null));
                }
            }
            Map<String, Object> last = new LinkedHashMap<>(thread.remove(thread.size() - 1));
            String lastText = AgentJson.extractTextFromContent(last.get("content"));
            String merged = (userText != null && !userText.isBlank())
                    ? userText + "\n" + lastText : lastText;
            List<Object> lastBlocks = new ArrayList<>();
            if (!merged.isBlank()) {
                lastBlocks.add(Map.of("type", "text", "text", merged));
            }
            appendFileBlocks(lastBlocks, userFiles);
            if (lastBlocks.isEmpty()) {
                lastBlocks.add(Map.of("type", "text", "text", "Proceed with your task."));
            }
            last.put("content", collapseBlocks(lastBlocks));
            thread.add(last);
            history.addAll(thread);
            userMessage = last;
        } else {
            List<Object> userBlocks = new ArrayList<>();
            if (userText != null && !userText.isBlank()) {
                userBlocks.add(Map.of("type", "text", "text", userText));
            }
            appendFileBlocks(userBlocks, userFiles);
            if (userBlocks.isEmpty()) {
                userBlocks.add(Map.of("type", "text", "text", "Proceed with your task."));
            }
            userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", collapseBlocks(userBlocks));
            history.add(userMessage);
        }
        if (systemMessage != null) {
            history.add(0, systemMessage);
        }
        return new Prepared(history, systemMessage, userMessage, relevantDocsMsg);
    }

    /** Files → content blocks on a message block list (handle-bearing hoist as placeholders). */
    private static void appendFileBlocks(List<Object> blocks, List<Map<String, Object>> userFiles) {
        if (userFiles == null) {
            return;
        }
        for (Map<String, Object> f : userFiles) {
            if (f == null) {
                continue;
            }
            if (AgentJson.hasFileHandle(f)) {
                blocks.add(AgentJson.filePlaceholder(f.get("name")));
            } else {
                blocks.addAll(AgentJson.filesToContentBlocks(List.of(f)));
            }
        }
    }

    /** Single text block collapses to a bare string; anything else stays a block list. */
    private static Object collapseBlocks(List<Object> blocks) {
        if (blocks.size() == 1 && blocks.get(0) instanceof Map<?, ?> only
                && "text".equals(only.get("type"))) {
            return only.get("text");
        }
        return blocks;
    }

    record Prepared(List<Map<String, Object>> history, Map<String, Object> systemMessage,
                    Map<String, Object> userMessage, Map<String, Object> relevantDocsMsg) {
    }

    /** Bridge for {@link AgentLoop} / {@link AiAgentNode} (field-bearing {@link AgentConfig}). */
    static void add(StudioQuackHelper helper, AgentConfig cfg, Object messageOrList) {
        addHistoryMessage(sessionOf(helper, cfg), messageOrList);
    }

    static List<Map<String, Object>> get(StudioQuackHelper helper, AgentConfig cfg) {
        return getHistory(sessionOf(helper, cfg));
    }

    static void persistLocal(List<Map<String, Object>> history, AgentConfig cfg) {
        persistInMemoryCache(history, sessionOf(null, cfg));
    }

    static Object unwrapList(Object value) {
        return AgentJson.unwrapListEnvelope(value);
    }

    static Map<String, Object> toolResult(String id, String name, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("content", content);
        m.put("tool_call_id", id);
        m.put("name", name);
        return m;
    }

    static Map<String, Object> normalize(Object msg) {
        return normalizeMessageForStorage(msg, null);
    }

    private static AgentSession sessionOf(StudioQuackHelper helper, AgentConfig cfg) {
        if (cfg == null) {
            return new AgentSession(helper, "", "", "", "", false, "");
        }
        return new AgentSession(helper, "", cfg.llmId, cfg.thoughtsId, cfg.memoryId,
                cfg.useRemoteMemory, cfg.conversationId);
    }
}
