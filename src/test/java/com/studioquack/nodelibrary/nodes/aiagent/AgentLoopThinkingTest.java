package com.studioquack.nodelibrary.nodes.aiagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Thinking-block merge (the per-token sub-PI flood fix): stream-capable producers record one
 * block per token, and {@code publishThinkingBlocks} must deliver ONE concatenated thoughts
 * string per round — not one function-port invocation per block.
 */
class AgentLoopThinkingTest {

    @Test
    void tokenLevelBlocksMergeIntoOneStringInOrder() {
        List<Map<String, Object>> blocks = List.of(
                Map.of("type", "thinking", "thinking", "Let"),
                Map.of("type", "thinking", "thinking", " me"),
                Map.of("type", "thinking", "thinking", " think"));
        assertEquals("Let me think", AgentLoop.mergeThinkingBlocks(blocks));
    }

    @Test
    void textKeyIsPreferredOverThinkingKey() {
        List<Map<String, Object>> blocks = List.of(
                Map.of("text", "a"),
                Map.of("thinking", "b"));
        assertEquals("ab", AgentLoop.mergeThinkingBlocks(blocks));
    }

    @Test
    void blankAndNonMapEntriesAreSkipped() {
        List<Object> blocks = List.of(
                Map.of("thinking", "x"),
                Map.of("thinking", "   "),
                "not-a-map",
                Map.of("thinking", "y"));
        assertEquals("xy", AgentLoop.mergeThinkingBlocks(blocks));
    }

    @Test
    void emptyOrAllBlankBlocksYieldEmptyString() {
        assertTrue(AgentLoop.mergeThinkingBlocks(List.of()).isEmpty());
        assertTrue(AgentLoop.mergeThinkingBlocks(List.of(Map.of("thinking", " "))).isEmpty());
        assertTrue(AgentLoop.mergeThinkingBlocks(List.of(Map.of("other", 1))).isEmpty());
    }
}
