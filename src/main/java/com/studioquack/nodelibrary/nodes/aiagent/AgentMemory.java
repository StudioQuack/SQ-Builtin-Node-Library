package com.studioquack.nodelibrary.nodes.aiagent;

import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Chat history: optional remote {@code memory} FUNCTION port (persisted across runs under a
 * scoped conversation key), else a per-invoke {@link ConcurrentHashMap} bucket released in
 * {@code finally}.
 *
 * <p>When a full conversation thread arrives (e.g. DuckBot conversation mode), existing memory
 * history is read first and diffed against the incoming chat so prior turns (and any tool calls /
 * tool results recorded during earlier runs) are preserved while only new messages are appended.
 * Divergent prefixes (prompt edits) trim or reset memory cleanly before appending the edited tail.
 */
final class AgentMemory {

    private static final String DEFAULT_FALLBACK_PROMPT = "Proceed with your task.";
    private static final Pattern FILE_PLACEHOLDER_PATTERN = Pattern.compile("^\\[file: .+\\]$");

    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>> CACHE =
            new ConcurrentHashMap<>();

    private AgentMemory() {
        // namespace — never instantiated
    }

    /** Drops only the in-process JVM fallback bucket; never deletes remote memory. */
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
        if (session == null || messageOrList == null) {
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
            if (session.useRemoteMemory && !session.memoryId.isBlank() && session.helper != null) {
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
        if (session != null && session.useRemoteMemory && !session.memoryId.isBlank() && session.helper != null) {
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

    /** Trim stored memory to the first {@code keepCount} entries ({@code LTRIM 0 keepCount-1}). */
    static void trimHistory(AgentSession session, int keepCount) {
        if (session == null || session.conversationId.isBlank()) {
            return;
        }
        if (keepCount <= 0) {
            clearHistory(session);
            return;
        }
        if (session.useRemoteMemory && !session.memoryId.isBlank() && session.helper != null) {
            try {
                Map<String, Object> req = AgentPortMapping.memoryTrim(session, 0, keepCount - 1);
                session.helper.execute("memory", req, session.memoryId, null);
            } catch (Exception e) {
                AgentJson.log(session.helper, "ai-agent memory_trim_failed type="
                        + e.getClass().getSimpleName());
            }
        }
        CopyOnWriteArrayList<Map<String, Object>> local = CACHE.get(session.conversationId);
        if (local != null && local.size() > keepCount) {
            CACHE.put(session.conversationId, new CopyOnWriteArrayList<>(local.subList(0, keepCount)));
        }
    }

    /** Delete stored memory for this conversation key ({@code DEL key}). */
    static void clearHistory(AgentSession session) {
        if (session == null || session.conversationId.isBlank()) {
            return;
        }
        if (session.useRemoteMemory && !session.memoryId.isBlank() && session.helper != null) {
            try {
                Map<String, Object> req = AgentPortMapping.memoryClear(session);
                session.helper.execute("memory", req, session.memoryId, null);
            } catch (Exception e) {
                AgentJson.log(session.helper, "ai-agent memory_clear_failed type="
                        + e.getClass().getSimpleName());
            }
        }
        CACHE.remove(session.conversationId);
    }

    /**
     * Replace stored memory with compacted history (e.g. after context-window summarization).
     * Core {@code system} messages are excluded from remote memory because
     * {@link #prepareInitialHistory} always prepends a fresh system message on each run.
     */
    static void replaceHistory(AgentSession session, List<Map<String, Object>> history) {
        if (session == null || session.conversationId.isBlank()) {
            return;
        }
        if (session.useRemoteMemory && !session.memoryId.isBlank() && session.helper != null) {
            clearHistory(session);
            if (history != null) {
                List<Object> toPersist = new ArrayList<>();
                for (Map<String, Object> m : history) {
                    if (m != null && !"system".equals(m.get("role"))) {
                        toPersist.add(m);
                    }
                }
                if (!toPersist.isEmpty()) {
                    addHistoryMessage(session, toPersist);
                }
            }
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
     * Build LLM-facing history and compute the exact diff of new messages to persist to memory.
     *
     * <p>Handle-bearing files hoist as {@code [file: name]} text (bytes ride
     * {@code session.inputFiles}); handle-less files stay content blocks. Old stored system
     * messages are demoted to user. Connected {@code data} sources inject on-demand RAG rules
     * into the system message (pre-RAG on turn start is removed).
     *
     * <p>When {@code threadMessages} is non-empty (conversation mode: full chat history from the
     * trigger), stored memory is read first and compared against {@code threadMessages}:
     * <ul>
     * <li>Matching prefix turns (including any intermediate {@code tool_calls} / {@code tool}
     * messages stored in memory between them) are kept intact without re-writing.</li>
     * <li>Only the new trailing diff messages are appended to history and returned in
     * {@link Prepared#messagesToPersist()}.</li>
     * <li>If the incoming thread diverged from memory (e.g. prompt edit), stale memory entries
     * are trimmed or cleared before appending the new tail.</li>
     * </ul>
     */
    static Prepared prepareInitialHistory(AgentSession session, String role, String instructions,
                                          String userText, List<Map<String, Object>> userFiles,
                                          List<Map<String, Object>> threadMessages) {
        List<String> systemParts = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            systemParts.add(role);
        }
        if (instructions != null && !instructions.isBlank() && !instructions.equals(userText)) {
            systemParts.add(instructions);
            systemParts.add(AgentConfig.STRICT_TOOL_CALL_RULES);
        }
        List<String> dataToolNames = dataToolNames(session);
        if (!dataToolNames.isEmpty()) {
            systemParts.add(AgentConfig.DATA_SOURCE_RAG_RULES.replace(
                    "{data_tools}", String.join(", ", dataToolNames)));
        }
        Map<String, Object> systemMessage = null;
        if (!systemParts.isEmpty()) {
            systemMessage = new LinkedHashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", String.join("\n", systemParts));
        }

        List<Map<String, Object>> stored = demoteStoredSystemMessages(getHistory(session));
        List<Map<String, Object>> history;
        List<Map<String, Object>> messagesToPersist;
        Map<String, Object> userMessage;

        if (threadMessages != null && !threadMessages.isEmpty()) {
            List<Map<String, Object>> incoming = new ArrayList<>(threadMessages.size());
            for (Map<String, Object> m : threadMessages) {
                if (m != null) {
                    incoming.add(normalizeMessageForStorage(m, null));
                }
            }
            ThreadSync sync = computeThreadSync(stored, incoming, userText);
            if (sync.cleared()) {
                clearHistory(session);
            } else if (sync.truncated()) {
                trimHistory(session, sync.keepCount());
            }
            if (session != null && session.helper != null) {
                AgentJson.log(session.helper, "ai-agent memory_sync stored=" + stored.size()
                        + " incoming=" + incoming.size()
                        + " diff=" + sync.diff().size()
                        + " cleared=" + sync.cleared()
                        + " truncated=" + sync.truncated());
            }
            history = new ArrayList<>(sync.baseHistory());
            if (!sync.diff().isEmpty()) {
                List<Map<String, Object>> diff = new ArrayList<>(sync.diff().size());
                for (Map<String, Object> m : sync.diff()) {
                    diff.add(new LinkedHashMap<>(m));
                }
                Map<String, Object> last = enrichFinalTurn(diff.get(diff.size() - 1), userText, userFiles);
                diff.set(diff.size() - 1, last);
                history.addAll(diff);
                messagesToPersist = List.copyOf(diff);
                userMessage = last;
            } else {
                messagesToPersist = List.of();
                if (!history.isEmpty()) {
                    Map<String, Object> tail = history.get(history.size() - 1);
                    if (userFiles != null && !userFiles.isEmpty()) {
                        Map<String, Object> withFiles = enrichFinalTurn(new LinkedHashMap<>(tail), null, userFiles);
                        history.set(history.size() - 1, withFiles);
                        userMessage = withFiles;
                    } else {
                        userMessage = tail;
                    }
                } else {
                    userMessage = buildUserMessage(userText, userFiles);
                    history.add(userMessage);
                }
            }
        } else {
            history = new ArrayList<>(stored);
            userMessage = buildUserMessage(userText, userFiles);
            history.add(userMessage);
            messagesToPersist = List.of(userMessage);
        }

        if (systemMessage != null) {
            history.add(0, systemMessage);
        }
        return new Prepared(history, systemMessage, userMessage, messagesToPersist);
    }

    /**
     * Compare existing memory ({@code stored}, which may include tool calls, tool results, and
     * compacted summaries) against the full incoming chat thread ({@code incoming}). Returns the
     * preserved base slice plus only the new diff messages not yet present in memory.
     */
    static ThreadSync computeThreadSync(List<Map<String, Object>> stored,
                                        List<Map<String, Object>> incoming,
                                        String staticPrefix) {
        if (stored == null || stored.isEmpty()) {
            return new ThreadSync(List.of(), incoming == null ? List.of() : incoming, false, false, 0);
        }
        if (incoming == null || incoming.isEmpty()) {
            return new ThreadSync(stored, List.of(), false, false, 0);
        }

        int lastSummaryIdx = -1;
        for (int i = stored.size() - 1; i >= 0; i--) {
            if (AgentSummarizer.isSummaryBlock(stored.get(i))) {
                lastSummaryIdx = i;
                break;
            }
        }

        if (lastSummaryIdx >= 0) {
            List<VisibleEntry> postVisible = visibleEntries(stored, lastSummaryIdx + 1);
            if (postVisible.isEmpty()) {
                // Memory ends right on a summary block — append only the newest incoming turn so
                // already-summarized earlier chat messages are never re-injected.
                return new ThreadSync(stored, incoming.subList(incoming.size() - 1, incoming.size()),
                        false, false, 0);
            }
            // Match the longest prefix of post-summary visible turns against the latest matching
            // contiguous window in the incoming thread.
            for (int prefixLen = postVisible.size(); prefixLen >= 1; prefixLen--) {
                int matchStart = findLatestWindowMatch(incoming, postVisible, prefixLen, staticPrefix);
                if (matchStart >= 0) {
                    int nextIncoming = matchStart + prefixLen;
                    List<Map<String, Object>> diff = nextIncoming < incoming.size()
                            ? incoming.subList(nextIncoming, incoming.size()) : List.of();
                    if (prefixLen == postVisible.size()) {
                        return new ThreadSync(stored, diff, false, false, 0);
                    }
                    int cutIdx = postVisible.get(prefixLen).storedIndex();
                    return new ThreadSync(stored.subList(0, cutIdx), diff, false, true, cutIdx);
                }
            }
            // None of the post-summary turns appear in incoming (e.g. user edited a prompt from
            // before the summary) — reset memory and rebuild from incoming thread.
            return new ThreadSync(List.of(), incoming, true, false, 0);
        }

        List<VisibleEntry> memVisible = visibleEntries(stored, 0);
        if (memVisible.isEmpty()) {
            return new ThreadSync(stored, incoming, false, false, 0);
        }

        int matched = 0;
        while (matched < memVisible.size() && matched < incoming.size()
                && messagesMatch(memVisible.get(matched).msg(), incoming.get(matched), staticPrefix)) {
            matched++;
        }

        if (matched == memVisible.size()) {
            List<Map<String, Object>> diff = matched < incoming.size()
                    ? incoming.subList(matched, incoming.size()) : List.of();
            return new ThreadSync(stored, diff, false, false, 0);
        }

        // Prefix diverged (prompt edit / supersede) — drop stale entries from cutIdx onward.
        int cutIdx = memVisible.get(matched).storedIndex();
        List<Map<String, Object>> diff = matched < incoming.size()
                ? incoming.subList(matched, incoming.size()) : List.of();
        if (cutIdx == 0) {
            return new ThreadSync(List.of(), diff, true, false, 0);
        }
        return new ThreadSync(stored.subList(0, cutIdx), diff, false, true, cutIdx);
    }

    private static int findLatestWindowMatch(List<Map<String, Object>> incoming,
                                             List<VisibleEntry> postVisible,
                                             int prefixLen,
                                             String staticPrefix) {
        for (int start = incoming.size() - prefixLen; start >= 0; start--) {
            boolean ok = true;
            for (int j = 0; j < prefixLen; j++) {
                if (!messagesMatch(postVisible.get(j).msg(), incoming.get(start + j), staticPrefix)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return start;
            }
        }
        return -1;
    }

    private static List<VisibleEntry> visibleEntries(List<Map<String, Object>> stored, int fromIndex) {
        List<VisibleEntry> out = new ArrayList<>();
        for (int i = fromIndex; i < stored.size(); i++) {
            Map<String, Object> m = stored.get(i);
            if (isChatVisible(m)) {
                out.add(new VisibleEntry(i, m));
            }
        }
        return out;
    }

    /**
     * Chat-visible turns are human {@code user} messages (not compacted summaries) and final
     * {@code assistant} messages (no pending {@code tool_calls}). Intermediate tool-call
     * assistant turns and {@code tool} results live only in memory and are skipped during
     * prefix alignment against the chat transcript.
     */
    static boolean isChatVisible(Map<String, Object> msg) {
        if (msg == null) {
            return false;
        }
        String role = AgentJson.str(msg.get("role")).strip().toLowerCase(Locale.ROOT);
        if ("user".equals(role)) {
            return !AgentSummarizer.isSummaryBlock(msg);
        }
        if ("assistant".equals(role)) {
            return AgentJson.asList(msg.get("tool_calls")).isEmpty();
        }
        return false;
    }

    static boolean messagesMatch(Map<String, Object> storedMsg,
                                 Map<String, Object> incomingMsg,
                                 String staticPrefix) {
        if (storedMsg == null || incomingMsg == null) {
            return false;
        }
        String r1 = AgentJson.str(storedMsg.get("role")).strip().toLowerCase(Locale.ROOT);
        String r2 = AgentJson.str(incomingMsg.get("role")).strip().toLowerCase(Locale.ROOT);
        if (!r1.equals(r2)) {
            return false;
        }
        String t1 = canonicalText(storedMsg.get("content"), r1, staticPrefix);
        String t2 = canonicalText(incomingMsg.get("content"), r2, staticPrefix);
        if (t1.equals(t2)) {
            return true;
        }
        return "user".equals(r1) && t2.isEmpty() && DEFAULT_FALLBACK_PROMPT.equals(t1);
    }

    /**
     * Normalize message content to plain text for cross-store comparison: unwraps content-block
     * lists, ignores {@code [file: ...]} placeholders and media blocks, and strips an optional
     * static designer prompt prefix on user turns.
     */
    static String canonicalText(Object content, String role, String staticPrefix) {
        String text;
        if (content instanceof String s) {
            text = s.strip();
        } else if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                String piece = null;
                if (item instanceof Map<?, ?> bm) {
                    String type = AgentJson.str(bm.get("type")).strip().toLowerCase(Locale.ROOT);
                    if (type.isEmpty() || "text".equals(type) || "output_text".equals(type) || "input_text".equals(type)) {
                        Object t = bm.get("text");
                        if (t != null) {
                            String candidate = AgentJson.str(t).strip();
                            if (!candidate.isEmpty() && !FILE_PLACEHOLDER_PATTERN.matcher(candidate).matches()) {
                                piece = candidate;
                            }
                        }
                    }
                } else if (item instanceof String bs) {
                    String candidate = bs.strip();
                    if (!candidate.isEmpty() && !FILE_PLACEHOLDER_PATTERN.matcher(candidate).matches()) {
                        piece = candidate;
                    }
                }
                if (piece != null) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(piece);
                }
            }
            text = sb.toString().strip();
        } else {
            text = content == null ? "" : AgentJson.str(content).strip();
        }
        if ("user".equalsIgnoreCase(role) && staticPrefix != null && !staticPrefix.isBlank()) {
            String prefix = staticPrefix.strip();
            if (text.equals(prefix)) {
                return "";
            }
            if (text.startsWith(prefix + "\n")) {
                return text.substring(prefix.length() + 1).strip();
            }
        }
        return text;
    }

    private static List<String> dataToolNames(AgentSession session) {
        if (session == null || session.toolDefinitions.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Map<String, Object> td : session.toolDefinitions) {
            if ("data".equals(td.get("port_value"))) {
                String n = AgentJson.nonBlankString(td.get("name"));
                if (n != null) {
                    names.add(n);
                }
            }
        }
        return names;
    }

    private static List<Map<String, Object>> demoteStoredSystemMessages(List<Map<String, Object>> raw) {
        List<Map<String, Object>> cleaned = new ArrayList<>();
        if (raw == null) {
            return cleaned;
        }
        for (Map<String, Object> m : raw) {
            if (m != null && "system".equals(m.get("role"))) {
                Map<String, Object> demoted = new LinkedHashMap<>();
                demoted.put("role", "user");
                demoted.put("content", m.get("content") == null ? "" : m.get("content"));
                cleaned.add(demoted);
            } else if (m != null) {
                cleaned.add(m);
            }
        }
        return cleaned;
    }

    private static Map<String, Object> enrichFinalTurn(Map<String, Object> msg,
                                                       String userText,
                                                       List<Map<String, Object>> userFiles) {
        String lastText = AgentJson.extractTextFromContent(msg.get("content"));
        String merged = (userText != null && !userText.isBlank())
                ? (lastText.isBlank() ? userText : userText + "\n" + lastText)
                : lastText;
        List<Object> lastBlocks = new ArrayList<>();
        if (!merged.isBlank()) {
            lastBlocks.add(Map.of("type", "text", "text", merged));
        }
        appendFileBlocks(lastBlocks, userFiles);
        if (lastBlocks.isEmpty()) {
            lastBlocks.add(Map.of("type", "text", "text", DEFAULT_FALLBACK_PROMPT));
        }
        msg.put("content", collapseBlocks(lastBlocks));
        return msg;
    }

    private static Map<String, Object> buildUserMessage(String userText, List<Map<String, Object>> userFiles) {
        List<Object> userBlocks = new ArrayList<>();
        if (userText != null && !userText.isBlank()) {
            userBlocks.add(Map.of("type", "text", "text", userText));
        }
        appendFileBlocks(userBlocks, userFiles);
        if (userBlocks.isEmpty()) {
            userBlocks.add(Map.of("type", "text", "text", DEFAULT_FALLBACK_PROMPT));
        }
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", collapseBlocks(userBlocks));
        return userMessage;
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

    private record VisibleEntry(int storedIndex, Map<String, Object> msg) {
    }

    record ThreadSync(List<Map<String, Object>> baseHistory,
                      List<Map<String, Object>> diff,
                      boolean cleared,
                      boolean truncated,
                      int keepCount) {
    }

    record Prepared(List<Map<String, Object>> history,
                    Map<String, Object> systemMessage,
                    Map<String, Object> userMessage,
                    List<Map<String, Object>> messagesToPersist) {
    }

    /** Bridge for {@link AgentLoop} / {@link AiAgentNode} (field-bearing {@link AgentConfig}). */
    static void add(StudioQuackHelper helper, AgentConfig cfg, Object messageOrList) {
        addHistoryMessage(sessionOf(helper, cfg), messageOrList);
    }

    static List<Map<String, Object>> get(StudioQuackHelper helper, AgentConfig cfg) {
        return getHistory(sessionOf(helper, cfg));
    }

    static void persistLocal(List<Map<String, Object>> history, AgentConfig cfg) {
        replaceHistory(sessionOf(null, cfg), history);
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
