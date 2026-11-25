package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSummarizerTest {

    private static AgentSession sessionWithFallback() {
        // helper == null triggers summarizer fallback without requiring an HTTP stub
        return new AgentSession(null, "agent-1", "llm-1", "", "", false,
                "conv-" + UUID.randomUUID());
    }

    @Test
    void compactionPreservesUserRoleAndNeverProducesSystemOnlyMessages() {
        String giantToolBody = "x".repeat(20_000);
        List<Map<String, Object>> history = List.of(
                Map.of("role", "system", "content", "You are a tool testing agent."),
                Map.of("role", "user", "content", "Test coingecko tools."),
                Map.of("role", "assistant", "content", "Calling coin_list.",
                        "tool_calls", List.of(Map.of("id", "c1", "name", "coingecko", "args", Map.of("operation", "coin_list")))),
                Map.of("role", "tool", "name", "coingecko", "tool_call_id", "c1", "content", giantToolBody));

        List<Map<String, Object>> compacted = AgentSummarizer.summarizeForContextWindow(
                sessionWithFallback(), history, 500, false);

        assertEquals(2, compacted.size());
        assertEquals("system", compacted.get(0).get("role"));
        assertEquals("You are a tool testing agent.", compacted.get(0).get("content"));

        Map<String, Object> summaryTurn = compacted.get(1);
        assertEquals("user", summaryTurn.get("role"));
        assertInstanceOf(String.class, summaryTurn.get("content"));
        String content = (String) summaryTurn.get("content");
        assertTrue(content.startsWith(AgentSummarizer.SUMMARIZED_PREFIX));
        assertTrue(content.contains("Test coingecko tools."));
        assertTrue(AgentSummarizer.isSummaryBlock(summaryTurn));
    }

    @Test
    void compactionWithoutCoreSystemStillYieldsSingleUserTurn() {
        List<Map<String, Object>> history = List.of(
                Map.of("role", "user", "content", "Proceed with your task."),
                Map.of("role", "assistant", "content", "a".repeat(4_000)));

        List<Map<String, Object>> compacted = AgentSummarizer.summarizeForContextWindow(
                sessionWithFallback(), history, 100, false);

        assertEquals(1, compacted.size());
        assertEquals("user", compacted.get(0).get("role"));
        assertTrue(AgentSummarizer.isSummaryBlock(compacted.get(0)));
    }

    @Test
    void batchSerializationBoundsHugeToolOutputsWhileKeepingOtherTurns() {
        List<Map<String, Object>> batch = List.of(
                Map.of("role", "user", "content", "Find bitcoin price"),
                Map.of("role", "tool", "name", "coingecko", "content", "z".repeat(500_000)),
                Map.of("role", "assistant", "content", "Next step ready"));

        String serialized = AgentSummarizer.serializeBatchForSummarization(batch, 6_000);

        assertTrue(serialized.length() <= 6_100, "serialized len=" + serialized.length());
        assertTrue(serialized.contains("Find bitcoin price"));
        assertTrue(serialized.contains("...[truncated "));
        assertTrue(serialized.contains("Next step ready"));
    }

    @Test
    void repeatedCompactionAndLegacySystemSummaryCollapseIntoSingleUserTurn() {
        Map<String, Object> legacySystemSummary = Map.of(
                "role", "system",
                "content", "Summarized((Earlier calls succeeded.))",
                AgentSummarizer.SUMMARIZED_FLAG_KEY, Boolean.TRUE,
                AgentSummarizer.SUMMARY_KIND_KEY, AgentSummarizer.SUMMARY_KIND_VALUE);
        List<Map<String, Object>> history = List.of(
                Map.of("role", "system", "content", "Core instructions."),
                legacySystemSummary,
                Map.of("role", "assistant", "content", "Round 2 tool output: " + "y".repeat(4_000)));

        List<Map<String, Object>> compacted = AgentSummarizer.summarizeForContextWindow(
                sessionWithFallback(), history, 200, true);

        assertEquals(2, compacted.size());
        assertEquals("system", compacted.get(0).get("role"));
        assertEquals("user", compacted.get(1).get("role"));
        String content = (String) compacted.get(1).get("content");
        assertTrue(content.contains("Earlier calls succeeded."));
    }
}
