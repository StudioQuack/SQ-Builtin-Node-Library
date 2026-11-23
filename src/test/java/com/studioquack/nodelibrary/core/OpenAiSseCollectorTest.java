package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studioquack.StudioQuackError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** openai-sse: (channel, delta) sequence and assembled v2 payload including usage/thoughts/tool_calls. */
class OpenAiSseCollectorTest {

    @Test
    void contentAndReasoningAndUsage() {
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"reasoning_content":"hmm"}}]}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"content":"Hel"}}]}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"content":"lo"}}]}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5,
                          "completion_tokens_details":{"reasoning_tokens":1}}}
                """)));

        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "hmm"),
                new StreamCollector.Piece("answer", "Hel"),
                new StreamCollector.Piece("answer", "lo")), seq);

        Map<String, Object> out = c.toAssistant();
        assertEquals("assistant", out.get("role"));
        assertEquals("Hello", text(out));
        assertEquals("stop", out.get("finish_reason"));
        assertEquals(List.of(Map.of("type", "thinking", "text", "hmm")), out.get("thoughts"));
        assertEquals(List.of(Map.of("type", "thinking", "thinking", "hmm")), out.get("thinking"));
        Map<?, ?> usage = (Map<?, ?>) out.get("usage");
        assertEquals(3, usage.get("prompt_tokens"));
        assertEquals(2, usage.get("completion_tokens"));
        assertEquals(5, usage.get("total_tokens"));
        assertEquals(1, usage.get("reasoning_tokens"));
    }

    @Test
    void toolCallsAssembledByIndex() {
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        c.ingest(null, json("""
                {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1",
                  "function":{"name":"lookup","arguments":"{\\"q\\":"}}]}}]}
                """));
        c.ingest(null, json("""
                {"choices":[{"delta":{"tool_calls":[{"index":0,
                  "function":{"arguments":"\\"x\\"}"}}]}}]}
                """));
        c.ingest(null, json("""
                {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
                """));
        Map<String, Object> out = c.toAssistant();
        assertEquals("I am calling a tool to answer your request.", text(out));
        List<?> calls = (List<?>) out.get("tool_calls");
        assertEquals(1, calls.size());
        Map<?, ?> tc = (Map<?, ?>) calls.get(0);
        assertEquals("call_1", tc.get("id"));
        assertEquals("lookup", tc.get("name"));
        assertEquals("tool_call", tc.get("type"));
        assertEquals(Map.of("q", "x"), tc.get("args"));
        assertEquals("tool_calls", out.get("finish_reason"));
    }

    @Test
    void typedThinkingContentBlocks() {
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = c.ingest(null, json("""
                {"choices":[{"delta":{"content":[
                  {"type":"thinking","text":"plan"},
                  {"type":"text","text":"ok"}]}}]}
                """));
        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "plan"),
                new StreamCollector.Piece("answer", "ok")), seq);
        Map<String, Object> out = c.toAssistant();
        assertEquals("ok", text(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "plan")), out.get("thoughts"));
    }

    @Test
    void mistralNestedThinkChunksUnwrapped() {
        // Mistral reasoning_effort=high: delta.content is a LIST whose ThinkChunk carries
        // thinking as a nested list of text chunks; the answer phase is a plain string.
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"content":[
                  {"type":"thinking","thinking":[{"type":"text","text":"Let me "}]}]}}]}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"content":[
                  {"type":"thinking","thinking":[{"type":"text","text":"reason."}]},
                  {"type":"text","text":"The answer"}]}}]}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"content":" is 42."}}]}
                """)));
        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "Let me "),
                new StreamCollector.Piece("thinking", "reason."),
                new StreamCollector.Piece("answer", "The answer"),
                new StreamCollector.Piece("answer", " is 42.")), seq);
        Map<String, Object> out = c.toAssistant();
        assertEquals("The answer is 42.", text(out));
        assertEquals(List.of(
                Map.of("type", "thinking", "text", "Let me "),
                Map.of("type", "thinking", "text", "reason.")), out.get("thoughts"));
    }

    @Test
    void inlineThinkTagsSplitLive() {
        // vLLM/SGLang without a reasoning parser: tags ride the content channel.
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"content":"<th"}}]}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"content":"ink>plan "}}]}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"content":"first</think>"}}]}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"choices":[{"delta":{"content":"The answer."}}]}
                """)));
        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "plan "),
                new StreamCollector.Piece("thinking", "first"),
                new StreamCollector.Piece("answer", "The answer.")), seq);
        Map<String, Object> out = c.toAssistant();
        assertEquals("The answer.", text(out));
        assertEquals(List.of(
                Map.of("type", "thinking", "text", "plan "),
                Map.of("type", "thinking", "text", "first")), out.get("thoughts"));
    }

    @Test
    void orphanCloserRepairedInFinalPayload() {
        // SGLang/vLLM pre-fill the opening tag into the prompt: the stream carries
        // reasoning…</think>answer with no opener. Live pieces ship as answer (no
        // retraction primitive); the assembled payload is repaired at the closer.
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        c.ingest(null, json("""
                {"choices":[{"delta":{"content":"reasoning steps "}}]}
                """));
        c.ingest(null, json("""
                {"choices":[{"delta":{"content":"here</think>\\n\\nFinal answer."}}]}
                """));
        Map<String, Object> out = c.toAssistant();
        assertEquals("Final answer.", text(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "reasoning steps here")),
                out.get("thoughts"));
    }

    @Test
    void orphanCloserNonStreamEnvelope() {
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        c.ingest(null, json("""
                {"choices":[{"message":{"role":"assistant",
                  "content":"deep thought</think>\\n\\nHi there"},"finish_reason":"stop"}]}
                """));
        Map<String, Object> out = c.toAssistant();
        assertEquals("Hi there", text(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "deep thought")), out.get("thoughts"));
    }

    @Test
    void doneAndMalformedSkipped() {
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        assertTrue(c.ingest(null, "[DONE]").isEmpty());
        assertTrue(c.ingest(null, 42).isEmpty());
        assertEquals("", text(c.toAssistant()));
        assertFalse(c.toAssistant().containsKey("usage"));
    }

    @Test
    void nonStreamEnvelopeIngest() {
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        c.ingest(null, json("""
                {"choices":[{"message":{"role":"assistant","content":"hi",
                  "reasoning":"why"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """));
        Map<String, Object> out = c.toAssistant();
        assertEquals("hi", text(out));
        assertEquals("why", ((List<Map<String, Object>>) out.get("thoughts")).get(0).get("text"));
        assertEquals("stop", out.get("finish_reason"));
    }

    @Test
    void contentFilterYieldsErrorPayload() {
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        c.ingest(null, json("""
                {"choices":[{"delta":{},"finish_reason":"content_filter"}]}
                """));
        StudioQuackError e = assertThrows(StudioQuackError.class, c::toAssistant);
        assertEquals("PROVIDER_ERROR", e.errorCode());
        assertTrue(e.getMessage().contains("content filter"), e.getMessage());
        assertEquals("content_filter", e.data().get("finish_reason"));
    }

    @SuppressWarnings("unchecked")
    private static String text(Map<String, Object> assistant) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) assistant.get("content");
        return String.valueOf(content.get(0).get("text"));
    }

    private static Object json(String raw) {
        return Json.parse(raw);
    }
}
