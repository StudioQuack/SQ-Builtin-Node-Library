package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentMemory: message normalization, local bucket lifecycle, cross-run diff sync against
 * full incoming chat threads (preserving tool calls, handling prompt edits and summaries),
 * and on-demand RAG system prompt injection.
 */
class AgentMemoryTest {

    private static AgentSession localSession(String conversationId) {
        return new AgentSession(null, "", "", "", "", false, conversationId);
    }

    private static String freshId() {
        return "test-" + UUID.randomUUID();
    }

    // ---------- normalizeMessageForStorage ----------

    @Test
    void normalizeStringBecomesSystemMessage() {
        Map<String, Object> m = AgentMemory.normalizeMessageForStorage("hello", null);
        assertEquals("system", m.get("role"));
        assertEquals("hello", m.get("content"));
        assertFalse(m.containsKey("_agent_id"));
    }

    @Test
    void normalizeRoleMapIsPreserved() {
        Map<String, Object> m = AgentMemory.normalizeMessageForStorage(
                Map.of("role", "user", "content", "hi"), null);
        assertEquals("user", m.get("role"));
        assertEquals("hi", m.get("content"));
    }

    @Test
    void normalizeLangChainStyleTypeMapsToRoles() {
        assertEquals("assistant", AgentMemory.normalizeMessageForStorage(
                Map.of("type", "ai", "data", Map.of("content", "x")), null).get("role"));
        assertEquals("assistant", AgentMemory.normalizeMessageForStorage(
                Map.of("type", "AIMessage", "data", Map.of("content", "x")), null).get("role"));
        assertEquals("user", AgentMemory.normalizeMessageForStorage(
                Map.of("type", "human", "data", Map.of("content", "x")), null).get("role"));
        assertEquals("user", AgentMemory.normalizeMessageForStorage(
                Map.of("type", "HumanMessage", "data", Map.of("content", "x")), null).get("role"));
        assertEquals("system", AgentMemory.normalizeMessageForStorage(
                Map.of("type", "SystemMessage", "data", Map.of("content", "x")), null).get("role"));
        assertEquals("tool", AgentMemory.normalizeMessageForStorage(
                Map.of("type", "ToolMessage", "data", Map.of("content", "x")), null).get("role"));
        assertEquals("user", AgentMemory.normalizeMessageForStorage(
                Map.of("type", "mystery", "data", Map.of("content", "x")), null).get("role"));
    }

    @Test
    void normalizeTypeMapWithoutDataReadsTopLevel() {
        Map<String, Object> m = AgentMemory.normalizeMessageForStorage(
                Map.of("type", "ai", "content", "hello"), null);
        assertEquals("assistant", m.get("role"));
        assertEquals("hello", m.get("content"));
    }

    @Test
    void normalizeCopiesToolExtras() {
        Map<String, Object> m = AgentMemory.normalizeMessageForStorage(
                Map.of("type", "tool", "data", Map.of(
                        "content", "out", "tool_call_id", "c1", "name", "get_weather")), null);
        assertEquals("tool", m.get("role"));
        assertEquals("out", m.get("content"));
        assertEquals("c1", m.get("tool_call_id"));
        assertEquals("get_weather", m.get("name"));
    }

    @Test
    void normalizeNonStringNonMapBecomesUserMessage() {
        Map<String, Object> m = AgentMemory.normalizeMessageForStorage(42, null);
        assertEquals("user", m.get("role"));
        assertEquals("42", m.get("content"));
    }

    @Test
    void normalizeTagsAgentIdWhenProvided() {
        assertEquals("node-1", AgentMemory.normalizeMessageForStorage("x", "node-1").get("_agent_id"));
        assertFalse(AgentMemory.normalizeMessageForStorage("x", "  ").containsKey("_agent_id"));
    }

    // ---------- bucket lifecycle ----------

    @Test
    void bucketNullAndBlankShareTheSameList() {
        assertSame(AgentMemory.bucket(null), AgentMemory.bucket(""));
    }

    @Test
    void addAndGetRoundTripPreservesOrder() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, Map.of("role", "user", "content", "q1"));
            AgentMemory.addHistoryMessage(session, Map.of("role", "assistant", "content", "a1"));
            List<Map<String, Object>> history = AgentMemory.getHistory(session);
            assertEquals(2, history.size());
            assertEquals("user", history.get(0).get("role"));
            assertEquals("assistant", history.get(1).get("role"));
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void addFlattensNestedListsOneLevelAtATime() {
        String id = freshId();
        try {
            AgentMemory.addHistoryMessage(localSession(id), List.of(List.of("a"), "b"));
            List<Map<String, Object>> history = AgentMemory.getHistory(localSession(id));
            assertEquals(2, history.size());
            assertEquals("a", history.get(0).get("content"));
            assertEquals("b", history.get(1).get("content"));
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void releaseDropsTheBucket() {
        String id = freshId();
        AgentMemory.addHistoryMessage(localSession(id), "x");
        AgentMemory.release(id);
        assertTrue(AgentMemory.getHistory(localSession(id)).isEmpty());
        AgentMemory.release(null);
        AgentMemory.release("  ");
    }

    @Test
    void replaceHistoryUpdatesLocalCache() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, "one");
            AgentMemory.addHistoryMessage(session, "two");
            AgentMemory.replaceHistory(session, List.of(Map.of("role", "user", "content", "kept")));
            List<Map<String, Object>> history = AgentMemory.getHistory(session);
            assertEquals(1, history.size());
            assertEquals("kept", history.get(0).get("content"));
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void replaceHistoryIgnoresNullSessionAndBlankId() {
        AgentMemory.replaceHistory(null, List.of(Map.of("role", "user", "content", "x")));
        AgentMemory.replaceHistory(localSession("  "), List.of(Map.of("role", "user", "content", "x")));
        assertTrue(AgentMemory.getHistory(localSession("  ")).isEmpty());
    }

    @Test
    void trimHistoryKeepsRequestedPrefix() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, Map.of("role", "user", "content", "m1"));
            AgentMemory.addHistoryMessage(session, Map.of("role", "assistant", "content", "m2"));
            AgentMemory.addHistoryMessage(session, Map.of("role", "user", "content", "m3"));
            AgentMemory.trimHistory(session, 2);
            List<Map<String, Object>> history = AgentMemory.getHistory(session);
            assertEquals(2, history.size());
            assertEquals("m1", history.get(0).get("content"));
            assertEquals("m2", history.get(1).get("content"));
        } finally {
            AgentMemory.release(id);
        }
    }

    // ---------- prepareInitialHistory & on-demand RAG prompt ----------

    @Test
    void initialHistoryBuildsSystemThenUser() {
        AgentSession session = localSession(freshId());
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, "Role.", "Do things.", "Hello", null, null);
        assertEquals(2, p.history().size());
        assertEquals("system", p.history().get(0).get("role"));
        assertEquals("user", p.history().get(1).get("role"));
        String systemText = String.valueOf(p.systemMessage().get("content"));
        assertTrue(systemText.startsWith("Role.\nDo things.\n"));
        assertTrue(systemText.contains("STRICT TOOL CALL PARAMETER RULES"));
        assertFalse(systemText.contains("Retrieved Context"));
        assertEquals("Hello", p.userMessage().get("content"));
        assertEquals(1, p.messagesToPersist().size());
    }

    @Test
    void initialHistorySkipsInstructionsEqualToUserText() {
        AgentSession session = localSession(freshId());
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, "Role.", "Hello", "Hello", null, null);
        assertEquals("Role.", p.systemMessage().get("content"));
    }

    @Test
    void initialHistoryWithoutRoleOrInstructionsHasNoSystemMessage() {
        AgentSession session = localSession(freshId());
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, "", "  ", "Hi", null, null);
        assertNull(p.systemMessage());
        assertEquals(1, p.history().size());
        assertEquals("user", p.history().get(0).get("role"));
    }

    @Test
    void initialHistoryInjectsDefaultPromptWhenInputIsEmpty() {
        AgentSession session = localSession(freshId());
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, null, null, "  ", null, null);
        assertEquals("Proceed with your task.", p.userMessage().get("content"));
    }

    @Test
    void initialHistoryWithFilesUsesContentBlocks() {
        AgentSession session = localSession(freshId());
        List<Map<String, Object>> files = List.of(Map.of(
                "kind", "image", "mime_type", "image/png", "source", "base64", "data", "QUJD", "name", "p.png"));
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, null, null, "Look at this", files, null);
        Object content = p.userMessage().get("content");
        assertTrue(content instanceof List<?>);
        List<?> blocks = (List<?>) content;
        assertEquals(2, blocks.size());
        assertEquals(Map.of("type", "text", "text", "Look at this"), blocks.get(0));
        assertEquals(Map.of("type", "image", "mime_type", "image/png", "source", "base64",
                "data", "QUJD", "name", "p.png"), blocks.get(1));
    }

    @Test
    void initialHistoryHandleBearingFileHoistsPlaceholder() {
        AgentSession session = localSession(freshId());
        List<Map<String, Object>> files = List.of(Map.of(
                "kind", "image", "mime_type", "image/png", "source", "sq-file",
                "fileId", "sq-file:abc", "name", "p.png"));
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, null, null, "Look at this", files, null);
        Object content = p.userMessage().get("content");
        assertTrue(content instanceof List<?>);
        List<?> blocks = (List<?>) content;
        assertEquals(2, blocks.size());
        assertEquals(Map.of("type", "text", "text", "Look at this"), blocks.get(0));
        assertEquals(Map.of("type", "text", "text", "[file: p.png]"), blocks.get(1));
    }

    @Test
    void connectedDataToolsInjectOnDemandRagRulesWithoutPrefetch() {
        AgentSession session = localSession(freshId());
        session.toolDefinitions.add(Map.of("name", "orders_kb", "port_value", "data", "node_id", "pg-1"));
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, "Warehouse assistant.", "", "Where is order 42?", null, null);
        assertEquals(2, p.history().size());
        assertEquals("system", p.history().get(0).get("role"));
        String sys = String.valueOf(p.systemMessage().get("content"));
        assertTrue(sys.contains("CONNECTED DATA SOURCES (ON-DEMAND RAG)"));
        assertTrue(sys.contains("orders_kb"));
        assertTrue(sys.contains("NOT pre-fetched"));
    }

    @Test
    void storedSystemMessagesAreDemotedToUser() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, "stored system text");
            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, null, null, "Q", null, null);
            assertEquals(2, p.history().size());
            assertEquals("user", p.history().get(0).get("role"));
            assertEquals("stored system text", p.history().get(0).get("content"));
            assertEquals("Q", p.history().get(1).get("content"));
            assertNull(p.systemMessage());
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void conversationThreadSeedsHistoryWhenMemoryIsEmpty() {
        AgentSession session = localSession(freshId());
        List<Map<String, Object>> thread = List.of(
                Map.of("role", "user", "content", "first question"),
                Map.of("role", "assistant", "content", "first answer"),
                Map.of("role", "user", "content", "follow up"));
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, "Role.", null, "", null, thread);
        assertEquals(4, p.history().size());
        assertEquals("system", p.history().get(0).get("role"));
        assertEquals("first question", p.history().get(1).get("content"));
        assertEquals("first answer", p.history().get(2).get("content"));
        assertEquals("follow up", p.history().get(3).get("content"));
        assertEquals("follow up", p.userMessage().get("content"));
        assertEquals(3, p.messagesToPersist().size());
    }

    @Test
    void conversationThreadPrependsStaticPromptToLastTurn() {
        AgentSession session = localSession(freshId());
        List<Map<String, Object>> thread = List.of(
                Map.of("role", "user", "content", "follow up"));
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, null, null, "Always answer in Turkish.", null, thread);
        assertEquals(1, p.history().size());
        assertEquals("Always answer in Turkish.\nfollow up", p.userMessage().get("content"));
    }

    @Test
    void conversationThreadHoistsFilesOntoLastTurn() {
        AgentSession session = localSession(freshId());
        List<Map<String, Object>> thread = List.of(
                Map.of("role", "user", "content", "what is in this file?"));
        List<Map<String, Object>> files = List.of(Map.of(
                "kind", "image", "mime_type", "image/png", "source", "base64", "data", "QUJD", "name", "p.png"));
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, null, null, "", files, thread);
        Object content = p.userMessage().get("content");
        assertTrue(content instanceof List<?>);
        List<?> blocks = (List<?>) content;
        assertEquals(2, blocks.size());
        assertEquals(Map.of("type", "text", "text", "what is in this file?"), blocks.get(0));
        assertEquals(Map.of("type", "image", "mime_type", "image/png", "source", "base64",
                "data", "QUJD", "name", "p.png"), blocks.get(1));
    }

    // ---------- cross-run diff sync against full incoming chat ----------

    @Test
    void diffSyncAppendsOnlyNewUserTurnAndPreservesPriorToolCalls() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            // Run 1 recorded user turn, tool-calling assistant turn, tool output, and final answer.
            AgentMemory.addHistoryMessage(session, List.of(
                    Map.of("role", "user", "content", "Where is order 42?"),
                    Map.of("role", "assistant", "content", "Calling lookup_order.",
                            "tool_calls", List.of(Map.of("id", "c1", "name", "lookup_order", "args", Map.of("id", "42")))),
                    Map.of("role", "tool", "tool_call_id", "c1", "name", "lookup_order", "content", "{\"bin\":\"A-12\"}"),
                    Map.of("role", "assistant", "content", List.of(Map.of("type", "text", "text", "Order 42 is in bin A-12.")))));

            // Run 2 receives the full chat transcript (humans + final assistant answers only) + new prompt.
            List<Map<String, Object>> fullChat = List.of(
                    Map.of("role", "user", "content", "Where is order 42?"),
                    Map.of("role", "assistant", "content", "Order 42 is in bin A-12."),
                    Map.of("role", "user", "content", "What is its weight?"));

            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, "Warehouse agent.", "", "", null, fullChat);

            // Only the 1 new user turn must be persisted — zero duplicate writes!
            assertEquals(1, p.messagesToPersist().size());
            assertEquals("user", p.messagesToPersist().get(0).get("role"));
            assertEquals("What is its weight?", p.messagesToPersist().get(0).get("content"));

            // LLM history has system + all 4 preserved memory items (including tool call/result) + 1 new user turn.
            assertEquals(6, p.history().size());
            assertEquals("system", p.history().get(0).get("role"));
            assertEquals("user", p.history().get(1).get("role"));
            assertEquals("assistant", p.history().get(2).get("role"));
            assertFalse(AgentJson.asList(p.history().get(2).get("tool_calls")).isEmpty());
            assertEquals("tool", p.history().get(3).get("role"));
            assertEquals("assistant", p.history().get(4).get("role"));
            assertEquals("What is its weight?", p.history().get(5).get("content"));
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void diffSyncAppendsMultipleIntermediateRoomMessagesWithoutDuplicatingPrefix() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, List.of(
                    Map.of("role", "user", "content", "q1"),
                    Map.of("role", "assistant", "content", "a1")));

            List<Map<String, Object>> fullChat = List.of(
                    Map.of("role", "user", "content", "q1"),
                    Map.of("role", "assistant", "content", "a1"),
                    Map.of("role", "user", "content", "colleague comment"),
                    Map.of("role", "assistant", "content", "other agent reply"),
                    Map.of("role", "user", "content", "q2"));

            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, null, null, "", null, fullChat);

            assertEquals(3, p.messagesToPersist().size());
            assertEquals("colleague comment", p.messagesToPersist().get(0).get("content"));
            assertEquals("other agent reply", p.messagesToPersist().get(1).get("content"));
            assertEquals("q2", p.messagesToPersist().get(2).get("content"));
            assertEquals(5, p.history().size());
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void diffSyncMatchesStaticPromptPrefixedUserAndFilePlaceholders() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, List.of(
                    Map.of("role", "user", "content", List.of(
                            Map.of("type", "text", "text", "Be concise.\nInspect this invoice."),
                            Map.of("type", "text", "text", "[file: inv.pdf]"))),
                    Map.of("role", "assistant", "content", List.of(
                            Map.of("type", "text", "text", "Total is $42.")))));

            List<Map<String, Object>> fullChat = List.of(
                    Map.of("role", "user", "content", "Inspect this invoice."),
                    Map.of("role", "assistant", "content", "Total is $42."),
                    Map.of("role", "user", "content", "Break down tax."));

            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, null, null, "Be concise.", null, fullChat);

            assertEquals(1, p.messagesToPersist().size());
            assertEquals("Be concise.\nBreak down tax.", p.messagesToPersist().get(0).get("content"));
            assertEquals(3, p.history().size());
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void diffSyncExactReplayProducesEmptyPersistDiff() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, List.of(
                    Map.of("role", "user", "content", "q1")));

            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, null, null, "", null, List.of(Map.of("role", "user", "content", "q1")));

            assertTrue(p.messagesToPersist().isEmpty());
            assertEquals(1, p.history().size());
            assertEquals("q1", p.userMessage().get("content"));
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void diffSyncTruncatesStaleTailOnPromptEditWhileKeepingEarlierToolCalls() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, List.of(
                    Map.of("role", "user", "content", "q1"),
                    Map.of("role", "assistant", "content", "Calling tool.",
                            "tool_calls", List.of(Map.of("id", "c1", "name", "t1", "args", Map.of()))),
                    Map.of("role", "tool", "tool_call_id", "c1", "name", "t1", "content", "ok"),
                    Map.of("role", "assistant", "content", "a1"),
                    Map.of("role", "user", "content", "q2 old"),
                    Map.of("role", "assistant", "content", "a2 old")));

            // User edited q2 old -> q2 edited; DuckBot superseded q2 old & a2 old.
            List<Map<String, Object>> editedChat = List.of(
                    Map.of("role", "user", "content", "q1"),
                    Map.of("role", "assistant", "content", "a1"),
                    Map.of("role", "user", "content", "q2 edited"));

            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, null, null, "", null, editedChat);

            // Local memory bucket was trimmed to 4 (q1..a1), dropping q2 old & a2 old.
            assertEquals(4, AgentMemory.getHistory(session).size());
            assertEquals(1, p.messagesToPersist().size());
            assertEquals("q2 edited", p.messagesToPersist().get(0).get("content"));
            assertEquals(5, p.history().size());
            assertEquals("tool", p.history().get(2).get("role"));
            assertEquals("q2 edited", p.history().get(4).get("content"));
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void diffSyncClearsMemoryWhenFirstTurnEdited() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, List.of(
                    Map.of("role", "user", "content", "q1 old"),
                    Map.of("role", "assistant", "content", "a1 old")));

            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, null, null, "", null, List.of(Map.of("role", "user", "content", "q1 edited")));

            assertTrue(AgentMemory.getHistory(session).isEmpty());
            assertEquals(1, p.messagesToPersist().size());
            assertEquals("q1 edited", p.messagesToPersist().get(0).get("content"));
            assertEquals(1, p.history().size());
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void diffSyncAfterSummarizationMatchesPostSummaryTurnsAndNeverReinjectsOldHistory() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            Map<String, Object> summary = AgentSummarizer.makeSummaryUserMessage(
                    "Discussed order 42 in bin A-12.", "What next?");
            AgentMemory.addHistoryMessage(session, List.of(
                    summary,
                    Map.of("role", "assistant", "content", "Calling tool.",
                            "tool_calls", List.of(Map.of("id", "c3", "name", "t3", "args", Map.of()))),
                    Map.of("role", "tool", "tool_call_id", "c3", "name", "t3", "content", "shipped"),
                    Map.of("role", "assistant", "content", "Order 42 has shipped.")));

            List<Map<String, Object>> fullChat = List.of(
                    Map.of("role", "user", "content", "Where is order 42?"),
                    Map.of("role", "assistant", "content", "Order 42 is in bin A-12."),
                    Map.of("role", "user", "content", "What next?"),
                    Map.of("role", "assistant", "content", "Order 42 has shipped."),
                    Map.of("role", "user", "content", "Share tracking number."));

            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, null, null, "", null, fullChat);

            assertEquals(1, p.messagesToPersist().size());
            assertEquals("Share tracking number.", p.messagesToPersist().get(0).get("content"));
            assertEquals(5, p.history().size());
            assertTrue(AgentSummarizer.isSummaryBlock(p.history().get(0)));
            assertEquals("Share tracking number.", p.history().get(4).get("content"));
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void diffSyncAfterSummarizationResetsWhenPreSummaryPromptEdited() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            Map<String, Object> summary = AgentSummarizer.makeSummaryUserMessage("Old summary.", "q2");
            AgentMemory.addHistoryMessage(session, List.of(
                    summary,
                    Map.of("role", "assistant", "content", "old post-summary answer")));

            List<Map<String, Object>> editedChat = List.of(
                    Map.of("role", "user", "content", "brand new first turn"));

            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, null, null, "", null, editedChat);

            assertTrue(AgentMemory.getHistory(session).isEmpty());
            assertEquals(1, p.messagesToPersist().size());
            assertEquals("brand new first turn", p.messagesToPersist().get(0).get("content"));
            assertEquals(1, p.history().size());
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void toolResultShape() {
        assertEquals(Map.of("role", "tool", "content", "out", "tool_call_id", "c1", "name", "f"),
                AgentMemory.toolResult("c1", "f", "out"));
    }

    @Test
    void normalizeBridgeUsesNoAgentId() {
        Map<String, Object> m = AgentMemory.normalize("x");
        assertEquals("system", m.get("role"));
        assertFalse(m.containsKey("_agent_id"));
    }
}
