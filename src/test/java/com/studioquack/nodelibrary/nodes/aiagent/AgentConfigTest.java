package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AgentConfig: constants, numeric parsing, node-id coercion, snapshot defaults, null-helper load path. */
class AgentConfigTest {

    @Test
    void constantsMatchDesignerContract() {
        assertEquals("You are a helpful AI assistant.", AgentConfig.DEFAULT_ROLE);
        assertEquals(10, AgentConfig.DEFAULT_MAX_ITERATIONS);
        assertEquals(200_000, AgentConfig.DEFAULT_CONTEXT_WINDOW);
        assertTrue(AgentConfig.STRICT_TOOL_CALL_RULES.contains("STRICT TOOL CALL PARAMETER RULES"));
        assertTrue(AgentConfig.DELEGATION_DESCRIPTION_TEMPLATE.contains("{agents}"));
        assertTrue(AgentConfig.SUMMARIZER_SYSTEM.contains("summarizer"));
    }

    @Test
    void parsePositiveIntCoercions() {
        assertEquals(7, AgentConfig.parsePositiveInt(null, 7));
        assertEquals(7, AgentConfig.parsePositiveInt("", 7));
        assertEquals(7, AgentConfig.parsePositiveInt("   ", 7));
        assertEquals(5, AgentConfig.parsePositiveInt("5", 7));
        assertEquals(5, AgentConfig.parsePositiveInt(5, 7));
        assertEquals(7, AgentConfig.parsePositiveInt(" 7 ", 0));
        assertEquals(7, AgentConfig.parsePositiveInt(0, 7));
        assertEquals(7, AgentConfig.parsePositiveInt(-3, 7));
        assertEquals(7, AgentConfig.parsePositiveInt("abc", 7));
        assertEquals(7, AgentConfig.parsePositiveInt(true, 7));
        assertEquals(5, AgentConfig.parsePositiveInt(5.7d, 7));
        assertEquals(7, AgentConfig.parsePositiveInt("5.7", 7));
    }

    @Test
    void parseContextWindowCoercions() {
        assertEquals(200_000, AgentConfig.parseContextWindow(null));
        assertEquals(200_000, AgentConfig.parseContextWindow(""));
        assertEquals(200_000, AgentConfig.parseContextWindow("abc"));
        assertEquals(123, AgentConfig.parseContextWindow("123"));
        assertEquals(123, AgentConfig.parseContextWindow(123.9d));
        assertEquals(0, AgentConfig.parseContextWindow(0));
        assertEquals(0, AgentConfig.parseContextWindow("0"));
        assertEquals(200_000, AgentConfig.parseContextWindow(-5)); // negatives invalid → default
    }

    @Test
    void nodeIdCoercion() {
        assertEquals("", AgentConfig.nodeId(null));
        assertEquals("", AgentConfig.nodeId("null"));
        assertEquals("", AgentConfig.nodeId(" NULL "));
        assertEquals("x", AgentConfig.nodeId("  x  "));
        assertEquals("42", AgentConfig.nodeId(42));
    }

    @Test
    void snapshotAppliesDefaultsForNulls() {
        AgentConfig.Snapshot snap = new AgentConfig.Snapshot(null, null, null, null, 3, 100);
        assertEquals(AgentConfig.DEFAULT_ROLE, snap.role());
        assertEquals("", snap.instructions());
        assertEquals("", snap.mergedStatic());
        assertTrue(snap.files().isEmpty());
        assertTrue(snap.voices().isEmpty());
        assertEquals(3, snap.maxIterations());
        assertEquals(100, snap.contextWindow());
    }

    @Test
    void snapshotCopiesFilesDefensively() {
        List<Map<String, Object>> src = new ArrayList<>();
        src.add(Map.of("k", "v"));
        AgentConfig.Snapshot snap = new AgentConfig.Snapshot("r", "i", "s", src, 1, 1);
        src.add(Map.of("later", true));
        assertEquals(1, snap.files().size());
        assertThrows(UnsupportedOperationException.class, () -> snap.files().add(Map.of()));
    }

    @Test
    void readWithNullHelperReturnsDefaults() {
        AgentConfig.Snapshot snap = AgentConfig.read(null);
        assertEquals(AgentConfig.DEFAULT_ROLE, snap.role());
        assertEquals("", snap.instructions());
        assertEquals("", snap.mergedStatic());
        assertTrue(snap.files().isEmpty());
        assertTrue(snap.voices().isEmpty());
        assertEquals(AgentConfig.DEFAULT_MAX_ITERATIONS, snap.maxIterations());
        assertEquals(AgentConfig.DEFAULT_CONTEXT_WINDOW, snap.contextWindow());
    }

    @Test
    void setupWithNullHelperThrows() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AgentConfig.setup(null));
        assertTrue(e.getMessage().contains("helper is required"));
    }

    @Test
    void loadWithNullHelperFallsBackToUnwiredConfig() {
        AgentConfig cfg = AgentConfig.load(null, null);
        assertEquals("", cfg.llmId);
        assertEquals("", cfg.thoughtsId);
        assertEquals("", cfg.memoryId);
        assertFalse(cfg.useRemoteMemory);
        assertNotNull(cfg.conversationId);
        assertFalse(cfg.conversationId.isBlank());
        assertEquals(AgentConfig.DEFAULT_ROLE, cfg.role);
        assertEquals("", cfg.instructions);
        assertEquals("", cfg.mergedStatic);
        assertTrue(cfg.files.isEmpty());
        assertTrue(cfg.voices.isEmpty());
        assertEquals(AgentConfig.DEFAULT_MAX_ITERATIONS, cfg.maxIterations);
        assertEquals(AgentConfig.DEFAULT_CONTEXT_WINDOW, cfg.contextWindow);
    }

    @Test
    void filesToBlocksDelegates() {
        assertTrue(AgentConfig.filesToBlocks(null).isEmpty());
        List<Map<String, Object>> blocks = AgentConfig.filesToBlocks(List.of(
                Map.of("kind", "image", "mime_type", "image/png", "source", "base64", "data", "x", "name", "")));
        assertEquals("image", blocks.get(0).get("type"));
        assertEquals("x", blocks.get(0).get("data"));
    }

    @Test
    void readCollectsVoicesAlongsideFiles() {
        StudioQuackHelper helper = new StudioQuackHelper(Map.of(
                "currentNode", Map.of(
                        "inputList", List.of(
                                Map.of("targetPortValue", "main_input",
                                        "value", Map.of(
                                                "files", List.of(Map.of(
                                                        "kind", "image", "data", "abc", "name", "a.png")),
                                                "voices", List.of(Map.of(
                                                        "kind", "audio", "data", "def", "name", "v.wav"))))))));
        AgentConfig.Snapshot snap = AgentConfig.read(helper);
        assertEquals(1, snap.files().size());
        assertEquals("image", snap.files().get(0).get("kind"));
        assertEquals(1, snap.voices().size());
        assertEquals("audio", snap.voices().get(0).get("kind"));
        assertEquals("v.wav", snap.voices().get(0).get("name"));
    }

    @Test
    void snapshotCopiesVoicesDefensively() {
        List<Map<String, Object>> src = new ArrayList<>();
        src.add(Map.of("k", "v"));
        AgentConfig.Snapshot snap = new AgentConfig.Snapshot("r", "i", "s", List.of(), src, List.of(), 1, 1);
        src.add(Map.of("later", true));
        assertEquals(1, snap.voices().size());
        assertThrows(UnsupportedOperationException.class, () -> snap.voices().add(Map.of()));
    }

    @Test
    void readParsesConversationThreadFromPromptString() {
        // Conversation mode: the Duckbot start msg port ships the thread as a JSON
        // array string; the edge JOLT renames it onto our prompt input.
        StudioQuackHelper helper = new StudioQuackHelper(Map.of(
                "currentNode", Map.of(
                        "inputList", List.of(
                                Map.of("targetPortValue", "main_input",
                                        "value", Map.of(
                                                "prompt", "[{\"role\":\"user\",\"content\":\"q1\"},"
                                                        + "{\"role\":\"assistant\",\"content\":\"a1\"},"
                                                        + "{\"role\":\"user\",\"content\":\"q2\"}]"))))));
        AgentConfig.Snapshot snap = AgentConfig.read(helper);
        assertEquals(3, snap.threadMessages().size());
        assertEquals("assistant", snap.threadMessages().get(1).get("role"));
        assertEquals("a1", snap.threadMessages().get(1).get("content"));
        // the thread is NOT also duplicated into the plain prompt text
        assertEquals("", snap.mergedStatic());
    }

    @Test
    void readTreatsNonMessageJsonArrayAsPlainPrompt() {
        StudioQuackHelper helper = new StudioQuackHelper(Map.of(
                "currentNode", Map.of(
                        "inputList", List.of(
                                Map.of("targetPortValue", "main_input",
                                        "value", Map.of("prompt", "[1, 2, 3]"))))));
        AgentConfig.Snapshot snap = AgentConfig.read(helper);
        assertTrue(snap.threadMessages().isEmpty());
        assertEquals("[1, 2, 3]", snap.mergedStatic());
    }

    @Test
    void readParsesBareStringThread() {
        StudioQuackHelper helper = new StudioQuackHelper(Map.of(
                "currentNode", Map.of(
                        "inputList", List.of(
                                Map.of("targetPortValue", "main_input",
                                        "value", "[{\"role\":\"user\",\"content\":\"hi\"}]")))));
        AgentConfig.Snapshot snap = AgentConfig.read(helper);
        assertEquals(1, snap.threadMessages().size());
        assertEquals("hi", snap.threadMessages().get(0).get("content"));
    }

    @Test
    void resolveConversationIdIsDeterministicAndScopedAcrossRuns() {
        Map<String, Object> ctx = Map.of(
                "workflowId", "wf-1",
                "agenticInfo", Map.of("conversationId", "conv-42"));
        StudioQuackHelper h1 = new StudioQuackHelper(ctx);
        StudioQuackHelper h2 = new StudioQuackHelper(ctx);

        String k1 = AgentConfig.resolveConversationId(h1, "agent-node-1");
        String k2 = AgentConfig.resolveConversationId(h2, "agent-node-1");
        assertEquals("wf-1:agent-node-1:conv-42", k1);
        assertEquals(k1, k2);
    }

    @Test
    void resolveConversationIdIsolatesSiblingAgentsAndWorkflowsInSameRoom() {
        StudioQuackHelper wfA = new StudioQuackHelper(Map.of(
                "workflowId", "wf-a",
                "agenticInfo", Map.of("conversationId", "room-9")));
        StudioQuackHelper wfB = new StudioQuackHelper(Map.of(
                "workflowId", "wf-b",
                "agenticInfo", Map.of("conversationId", "room-9")));

        String agent1 = AgentConfig.resolveConversationId(wfA, "node-1");
        String agent2 = AgentConfig.resolveConversationId(wfA, "node-2");
        String otherWf = AgentConfig.resolveConversationId(wfB, "node-1");

        assertFalse(agent1.equals(agent2));
        assertFalse(agent1.equals(otherWf));
    }

    @Test
    void resolveConversationIdHashesUnsafeCharactersAndFallsBackToUuidWhenAbsent() {
        StudioQuackHelper unsafe = new StudioQuackHelper(Map.of(
                "workflowId", "wf-1",
                "agenticInfo", Map.of("conversationId", "bad id\r\nFLUSHALL")));
        String key = AgentConfig.resolveConversationId(unsafe, "node-1");
        assertTrue(key.startsWith("wf-1:node-1:h_"));
        assertFalse(key.contains("\r"));
        assertFalse(key.contains("\n"));
        assertFalse(key.contains(" "));

        StudioQuackHelper empty = new StudioQuackHelper(Map.of("workflowId", "wf-1"));
        String u1 = AgentConfig.resolveConversationId(empty, "node-1");
        String u2 = AgentConfig.resolveConversationId(empty, "node-1");
        assertFalse(u1.equals(u2));
    }

    @Test
    void preRagPromptRemovedAndOnDemandRagRulesPresent() {
        assertFalse(AgentConfig.STRICT_TOOL_CALL_RULES.contains("Retrieved Context"));
        assertTrue(AgentConfig.DATA_SOURCE_RAG_RULES.contains("{data_tools}"));
        assertTrue(AgentConfig.DATA_SOURCE_RAG_RULES.contains("NOT pre-fetched"));
    }
}
