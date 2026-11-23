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
 * AgentMemory: message normalization, in-memory bucket lifecycle, initial-history assembly.
 * Every test uses a fresh conversation id so the static cache never leaks between tests.
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
    void persistReplacesLocalCache() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, "one");
            AgentMemory.addHistoryMessage(session, "two");
            AgentMemory.persistInMemoryCache(
                    List.of(Map.of("role", "user", "content", "kept")), session);
            List<Map<String, Object>> history = AgentMemory.getHistory(session);
            assertEquals(1, history.size());
            assertEquals("kept", history.get(0).get("content"));
        } finally {
            AgentMemory.release(id);
        }
    }

    @Test
    void persistIgnoresNullSessionBlankIdAndRemoteMemory() {
        AgentMemory.persistInMemoryCache(List.of(Map.of("role", "user", "content", "x")), null);
        AgentMemory.persistInMemoryCache(List.of(Map.of("role", "user", "content", "x")), localSession("  "));
        String id = freshId();
        AgentSession remote = new AgentSession(null, "", "", "", "mem-1", true, id);
        AgentMemory.persistInMemoryCache(List.of(Map.of("role", "user", "content", "x")), remote);
        assertTrue(AgentMemory.getHistory(localSession(id)).isEmpty());
    }

    // ---------- prepareInitialHistory ----------

    @Test
    void initialHistoryBuildsSystemThenUser() {
        AgentSession session = localSession(freshId());
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, "Role.", "Do things.", "Hello", null, null, null);
        assertEquals(2, p.history().size());
        assertEquals("system", p.history().get(0).get("role"));
        assertEquals("user", p.history().get(1).get("role"));
        String systemText = String.valueOf(p.systemMessage().get("content"));
        assertTrue(systemText.startsWith("Role.\nDo things.\n"));
        assertTrue(systemText.contains("STRICT TOOL CALL PARAMETER RULES"));
        assertEquals("Hello", p.userMessage().get("content"));
        assertNull(p.relevantDocsMsg());
    }

    @Test
    void initialHistorySkipsInstructionsEqualToUserText() {
        AgentSession session = localSession(freshId());
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, "Role.", "Hello", "Hello", null, null, null);
        assertEquals("Role.", p.systemMessage().get("content"));
    }

    @Test
    void initialHistoryWithoutRoleOrInstructionsHasNoSystemMessage() {
        AgentSession session = localSession(freshId());
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, "", "  ", "Hi", null, null, null);
        assertNull(p.systemMessage());
        assertEquals(1, p.history().size());
        assertEquals("user", p.history().get(0).get("role"));
    }

    @Test
    void initialHistoryInjectsDefaultPromptWhenInputIsEmpty() {
        AgentSession session = localSession(freshId());
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, null, null, "  ", null, null, null);
        assertEquals("Proceed with your task.", p.userMessage().get("content"));
    }

    @Test
    void initialHistoryWithFilesUsesContentBlocks() {
        AgentSession session = localSession(freshId());
        List<Map<String, Object>> files = List.of(Map.of(
                "kind", "image", "mime_type", "image/png", "source", "base64", "data", "QUJD", "name", "p.png"));
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, null, null, "Look at this", files, null, null);
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
                session, null, null, "Look at this", files, null, null);
        Object content = p.userMessage().get("content");
        assertTrue(content instanceof List<?>);
        List<?> blocks = (List<?>) content;
        assertEquals(2, blocks.size());
        assertEquals(Map.of("type", "text", "text", "Look at this"), blocks.get(0));
        assertEquals(Map.of("type", "text", "text", "[file: p.png]"), blocks.get(1));
    }

    @Test
    void relevantDocumentsSlotBeforeUserMessage() {
        AgentSession session = localSession(freshId());
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, null, null, "Q", null, "doc contents", null);
        assertEquals(2, p.history().size());
        assertEquals("system", p.history().get(0).get("role"));
        assertEquals("doc contents", p.history().get(0).get("content"));
        assertEquals("user", p.history().get(1).get("role"));
        assertEquals("doc contents", p.relevantDocsMsg().get("content"));
    }

    @Test
    void storedSystemMessagesAreDemotedToUser() {
        String id = freshId();
        AgentSession session = localSession(id);
        try {
            AgentMemory.addHistoryMessage(session, "stored system text");
            AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                    session, null, null, "Q", null, null, null);
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
    void conversationThreadSeedsHistoryAndLastMessageIsTheTurn() {
        AgentSession session = localSession(freshId());
        List<Map<String, Object>> thread = List.of(
                Map.of("role", "user", "content", "first question"),
                Map.of("role", "assistant", "content", "first answer"),
                Map.of("role", "user", "content", "follow up"));
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, "Role.", null, "", null, null, thread);
        // system + the three thread messages; the last one is the current turn
        assertEquals(4, p.history().size());
        assertEquals("system", p.history().get(0).get("role"));
        assertEquals("user", p.history().get(1).get("role"));
        assertEquals("first question", p.history().get(1).get("content"));
        assertEquals("assistant", p.history().get(2).get("role"));
        assertEquals("first answer", p.history().get(2).get("content"));
        assertEquals("user", p.history().get(3).get("role"));
        assertEquals("follow up", p.history().get(3).get("content"));
        assertEquals("follow up", p.userMessage().get("content"));
    }

    @Test
    void conversationThreadPrependsStaticPromptToLastTurn() {
        AgentSession session = localSession(freshId());
        List<Map<String, Object>> thread = List.of(
                Map.of("role", "user", "content", "follow up"));
        AgentMemory.Prepared p = AgentMemory.prepareInitialHistory(
                session, null, null, "Always answer in Turkish.", null, null, thread);
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
                session, null, null, "", files, null, thread);
        Object content = p.userMessage().get("content");
        assertTrue(content instanceof List<?>);
        List<?> blocks = (List<?>) content;
        assertEquals(2, blocks.size());
        assertEquals(Map.of("type", "text", "text", "what is in this file?"), blocks.get(0));
        assertEquals(Map.of("type", "image", "mime_type", "image/png", "source", "base64",
                "data", "QUJD", "name", "p.png"), blocks.get(1));
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
