package com.studioquack.nodelibrary.nodes.aiagent;

import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-invoke agent state. Replaces Python module globals ({@code llm_node_id},
 * {@code thoughts_node_id}, {@code tool_definitions_global}) so concurrent
 * dispatches cannot clobber each other.
 */
final class AgentSession {

    final StudioQuackHelper helper;
    final String myNodeId;
    final String llmNodeId;
    final String thoughtsNodeId;
    final String memoryId;
    final boolean useRemoteMemory;
    /** Internal memory key — never placed on {@code main_input} / {@code main_output}. */
    final String conversationId;
    /** Filled once before the LLM loop; treated as read-only afterwards. */
    final List<Map<String, Object>> toolDefinitions = new ArrayList<>();
    /**
     * Current-turn file ContentBlocks for the model FUNCTION {@code inputFiles} key.
     * Empty by default so the 7-arg constructor stays valid.
     */
    final List<Map<String, Object>> inputFiles = new ArrayList<>();
    /**
     * Set by {@code AgentLlm.callLlm} each round: true when the round's thinking
     * was already fanned out live as {@code emitPartial("thinking", …)} deltas.
     * {@code publishThinkingBlocks} then skips the post-hoc full-text re-emit so
     * the Duckbot UI never shows the same thinking twice. The thoughts FUNCTION
     * port fan-out is unaffected (workflow consumers still get the full block).
     */
    boolean thinkingStreamedLive;

    AgentSession(StudioQuackHelper helper, String myNodeId, String llmNodeId, String thoughtsNodeId,
                 String memoryId, boolean useRemoteMemory, String conversationId) {
        this.helper = helper;
        this.myNodeId = myNodeId == null ? "" : myNodeId;
        this.llmNodeId = llmNodeId == null ? "" : llmNodeId;
        this.thoughtsNodeId = thoughtsNodeId == null ? "" : thoughtsNodeId;
        this.memoryId = memoryId == null ? "" : memoryId;
        this.useRemoteMemory = useRemoteMemory;
        this.conversationId = conversationId == null ? "" : conversationId;
    }

    boolean thoughtsConnected() {
        return !thoughtsNodeId.isBlank();
    }
}
