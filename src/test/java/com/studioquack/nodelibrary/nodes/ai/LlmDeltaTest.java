package com.studioquack.nodelibrary.nodes.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LlmDelta: SSE chunk accumulation, thinking/answer fan-out state, toAssistant payload shape. */
class LlmDeltaTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> contentOf(Map<String, Object> assistant) {
        return (List<Map<String, Object>>) assistant.get("content");
    }

    private static String bodyOf(Map<String, Object> assistant) {
        return String.valueOf(contentOf(assistant).get(0).get("text"));
    }

    @Test
    void freshDeltaProducesEmptyAssistant() {
        LlmDelta d = new LlmDelta();
        assertFalse(d.hasText());
        assertFalse(d.hasToolCalls());
        assertFalse(d.hasFiles());
        assertNull(d.finishReason());

        Map<String, Object> out = d.toAssistant();
        assertEquals("assistant", out.get("role"));
        assertEquals("", bodyOf(out));
        assertFalse(out.containsKey("tool_calls"));
        assertFalse(out.containsKey("thoughts"));
        assertFalse(out.containsKey("thinking"));
        assertFalse(out.containsKey("output_files"));
        assertFalse(out.containsKey("finish_reason"));
        assertFalse(out.containsKey("usage"));
    }

    @Test
    void ignoresNullDoneBlankAndNonMapChunks() {
        LlmDelta d = new LlmDelta();
        d.ingest(null, null);
        d.ingest("[DONE]", null);
        d.ingest("   ", null);
        d.ingest("plain text", null);
        d.ingest(42, null);
        assertFalse(d.hasText());
        assertFalse(d.hasToolCalls());
    }

    @Test
    void deltaContentAccumulatesAcrossChunks() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "Hel")))), null);
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "lo")))), null);
        assertTrue(d.hasText());
        assertEquals("Hello", bodyOf(d.toAssistant()));
    }

    @Test
    void inlineThinkTagsSplitIntoThoughts() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "<think>plan</think>Hi")))), null);
        Map<String, Object> out = d.toAssistant();
        assertEquals("Hi", bodyOf(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "plan")), out.get("thoughts"));
    }

    @Test
    void orphanCloserRepairedInFinalPayload() {
        // SGLang/vLLM pre-fill the opening tag into the prompt: reasoning…</think>answer.
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "reasoning ")))), null);
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "here</think>\n\nAnswer.")))), null);
        Map<String, Object> out = d.toAssistant();
        assertEquals("Answer.", bodyOf(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "reasoning here")), out.get("thoughts"));
    }

    @Test
    void contentBlockListIsFlattened() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta",
                Map.of("content", List.of(Map.of("type", "text", "text", "a"), Map.of("text", "b")))))), null);
        assertEquals("ab", bodyOf(d.toAssistant()));
    }

    @Test
    void legacyChoiceTextIsAppended() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("text", "legacy"))), null);
        assertEquals("legacy", bodyOf(d.toAssistant()));
    }

    @Test
    void finishReasonIsCaptured() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("content", "x"), "finish_reason", "stop"))), null);
        assertEquals("stop", d.finishReason());
        assertEquals("stop", d.toAssistant().get("finish_reason"));
    }

    @Test
    void reasoningContentBecomesThoughtsAndThinking() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("reasoning_content", "thinking...")))), null);
        assertFalse(d.hasText());
        Map<String, Object> out = d.toAssistant();
        assertEquals(List.of(Map.of("type", "thinking", "text", "thinking...")), out.get("thoughts"));
        assertEquals(List.of(Map.of("type", "thinking", "thinking", "thinking...")), out.get("thinking"));
    }

    @Test
    void reasoningBlockListIsConcatenated() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta",
                Map.of("reasoning", List.of(Map.of("text", "a"), Map.of("thinking", "b")))))), null);
        assertEquals(List.of(Map.of("type", "thinking", "text", "ab")), d.toAssistant().get("thoughts"));
    }

    @Test
    void topLevelMessageWithoutChoicesIsIngested() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("role", "assistant", "content", "top"), null);
        assertEquals("top", bodyOf(d.toAssistant()));
    }

    @Test
    void messageEnvelopeToolCallsGetSequentialIndex() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("message", Map.of(
                "content", "",
                "tool_calls", List.of(Map.of("id", "c1", "function",
                        Map.of("name", "f", "arguments", "{}"))))))), null);
        assertTrue(d.hasToolCalls());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls = (List<Map<String, Object>>) d.toAssistant().get("tool_calls");
        assertEquals(1, calls.size());
        assertEquals("c1", calls.get(0).get("id"));
        assertEquals("f", calls.get(0).get("name"));
        assertEquals(Map.of(), calls.get(0).get("args"));
        assertEquals("tool_call", calls.get(0).get("type"));
    }

    @Test
    void streamedToolCallArgumentsAreConcatenatedAndParsed() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
                Map.of("index", 0, "id", "call_1", "function",
                        Map.of("name", "get_weather", "arguments", "{\"city\":"))))))), null);
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
                Map.of("index", 0, "function", Map.of("arguments", "\"Paris\"}"))))))), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls = (List<Map<String, Object>>) d.toAssistant().get("tool_calls");
        assertEquals(1, calls.size());
        assertEquals("call_1", calls.get(0).get("id"));
        assertEquals("get_weather", calls.get(0).get("name"));
        assertEquals(Map.of("city", "Paris"), calls.get(0).get("args"));
    }

    @Test
    void invalidToolArgsJsonFallsBackToRawArgs() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
                Map.of("index", 0, "id", "c", "function", Map.of("name", "f", "arguments", "not json"))))))), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls = (List<Map<String, Object>>) d.toAssistant().get("tool_calls");
        assertEquals(Map.of("_raw_args", "not json", "_parse_error", "Invalid JSON from model"),
                calls.get(0).get("args"));
    }

    @Test
    void toolCallDefaultsAndOrdering() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
                Map.of("index", 1, "function", Map.of("name", "b", "arguments", "{}")),
                Map.of("index", 0, "function", Map.of("name", "a", "arguments", "{}"))))))), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls = (List<Map<String, Object>>) d.toAssistant().get("tool_calls");
        assertEquals(2, calls.size());
        assertEquals("a", calls.get(0).get("name"));
        assertEquals("b", calls.get(1).get("name"));
        assertEquals("call_0", calls.get(0).get("id"));
    }

    @Test
    void blankBodyWithToolCallsGetsPlaceholder() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
                Map.of("index", 0, "id", "c", "function", Map.of("name", "f", "arguments", "{}"))))))), null);
        assertEquals("I am calling a tool to answer your request.", bodyOf(d.toAssistant()));
    }

    @Test
    void usageIsCapturedWithKnownKeysOnly() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("usage", Map.of(
                "prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15, "ignored", 1)), null);
        assertEquals(Map.of("prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15),
                d.toAssistant().get("usage"));
    }

    @Test
    void reasoningTokensAddUsageEntryWithoutSummaryThought() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("usage", Map.of(
                "completion_tokens", 5,
                "completion_tokens_details", Map.of("reasoning_tokens", 3))), null);
        Map<String, Object> out = d.toAssistant();
        assertEquals(Map.of("completion_tokens", 5, "reasoning_tokens", 3), out.get("usage"));
        // Token usage is metadata for programmatic consumers — never a human-readable thought.
        assertFalse(out.containsKey("thoughts"));
    }

    @Test
    void zeroReasoningTokensAreIgnored() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("usage", Map.of(
                "completion_tokens", 5,
                "completion_tokens_details", Map.of("reasoning_tokens", 0))), null);
        Map<String, Object> out = d.toAssistant();
        assertEquals(Map.of("completion_tokens", 5), out.get("usage"));
        assertFalse(out.containsKey("thoughts"));
    }

    @Test
    void audioDataProducesOutputFileAndTranscriptBody() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("audio",
                Map.of("data", "QUJD", "transcript", "hello audio", "format", "mp3"))))), null);
        assertTrue(d.hasFiles());
        assertFalse(d.hasText());
        Map<String, Object> out = d.toAssistant();
        assertEquals("hello audio", bodyOf(out));
        assertEquals(List.of(Map.of(
                "type", "audio",
                "mime_type", "audio/mp3",
                "source", "base64",
                "data", "QUJD",
                "name", "out.mp3")), out.get("output_files"));
    }

    @Test
    void audioFormatDefaultsToWav() {
        LlmDelta d = new LlmDelta();
        d.ingest(Map.of("choices", List.of(Map.of("delta", Map.of("audio", Map.of("data", "eA"))))), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) d.toAssistant().get("output_files");
        assertEquals("audio/wav", files.get(0).get("mime_type"));
        assertEquals("out.wav", files.get(0).get("name"));
    }
}
