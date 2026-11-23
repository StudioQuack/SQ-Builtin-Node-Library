package com.studioquack.nodelibrary.nodes.localllm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * LocalLlmStream: inline think-tag lifting and the SGLang/vLLM missing-opener repair
 * on the assembled assistant payload.
 */
class LocalLlmStreamTest {

    @SuppressWarnings("unchecked")
    private static String bodyOf(Map<String, Object> assistant) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) assistant.get("content");
        return String.valueOf(content.get(0).get("text"));
    }

    @Test
    void inlineThinkTagsSplitIntoThoughts() {
        LocalLlmStream acc = new LocalLlmStream();
        acc.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "<th")))), null);
        acc.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "ink>plan first")))), null);
        acc.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "</think>The answer.")))), null);
        Map<String, Object> out = acc.toAssistant();
        assertEquals("The answer.", bodyOf(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "plan first")), out.get("thoughts"));
    }

    @Test
    void orphanCloserStreamedThenRepaired() {
        // SGLang/vLLM pre-fill the opening tag into the prompt: the stream carries
        // reasoning…</think>answer with no opener; the final payload splits at the closer.
        LocalLlmStream acc = new LocalLlmStream();
        acc.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "reasoning steps ")))), null);
        acc.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "here</think>\n\nFinal answer.")))), null);
        Map<String, Object> out = acc.toAssistant();
        assertEquals("Final answer.", bodyOf(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "reasoning steps here")),
                out.get("thoughts"));
    }

    @Test
    void orphanCloserNonStreamEnvelope() {
        LocalLlmStream acc = new LocalLlmStream();
        acc.ingest(Map.of("choices", List.of(Map.of("message", Map.of(
                "role", "assistant", "content", "deep thought</think>\n\nHi there")))), null);
        Map<String, Object> out = acc.toAssistant();
        assertEquals("Hi there", bodyOf(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "deep thought")), out.get("thoughts"));
    }

    @Test
    void plainAnswerUntouched() {
        LocalLlmStream acc = new LocalLlmStream();
        acc.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "Just an answer.")))), null);
        Map<String, Object> out = acc.toAssistant();
        assertEquals("Just an answer.", bodyOf(out));
        assertFalse(out.containsKey("thoughts"));
    }
}
