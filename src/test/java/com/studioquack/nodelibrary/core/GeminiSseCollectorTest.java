package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** gemini-sse: thought parts vs answer parts, usageMetadata, finishReason. */
class GeminiSseCollectorTest {

    @Test
    void thoughtAndTextAndUsage() {
        GeminiSseCollector c = new GeminiSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest(null, json("""
                {"candidates":[{"content":{"parts":[
                  {"text":"plan","thought":true},
                  {"text":"Hel"}]}}]}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"candidates":[{"content":{"parts":[{"text":"lo"}]},
                  "finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":2,"totalTokenCount":7}}
                """)));

        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "plan"),
                new StreamCollector.Piece("answer", "Hel"),
                new StreamCollector.Piece("answer", "lo")), seq);

        Map<String, Object> out = c.toAssistant();
        assertEquals("Hello", text(out));
        assertEquals("STOP", out.get("finish_reason"));
        assertEquals(List.of(Map.of("type", "thinking", "text", "plan")), out.get("thoughts"));
        Map<?, ?> usage = (Map<?, ?>) out.get("usage");
        assertEquals(5, usage.get("prompt_tokens"));
        assertEquals(2, usage.get("completion_tokens"));
        assertEquals(7, usage.get("total_tokens"));
    }

    @Test
    void functionCallPartAssemblesToolCall() {
        GeminiSseCollector c = new GeminiSseCollector("llm_output", null);
        c.ingest(null, json("""
                {"candidates":[{"content":{"parts":[
                  {"functionCall":{"name":"lookup","args":{"q":"x"},"id":"call_g1"}}]},
                  "finishReason":"STOP"}]}
                """));
        Map<String, Object> out = c.toAssistant();
        assertEquals("I am calling a tool to answer your request.", text(out));
        List<?> calls = (List<?>) out.get("tool_calls");
        Map<?, ?> tc = (Map<?, ?>) calls.get(0);
        assertEquals("call_g1", tc.get("id"));
        assertEquals("lookup", tc.get("name"));
        assertEquals("tool_call", tc.get("type"));
        assertEquals(Map.of("q", "x"), tc.get("args"));
        assertEquals("STOP", out.get("finish_reason"));
    }

    @Test
    void envelopeSameShapeAsOneChunk() {
        GeminiSseCollector c = new GeminiSseCollector("llm_output", null);
        c.ingest(null, json("""
                {"candidates":[{"content":{"parts":[{"text":"ok"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}
                """));
        assertEquals("ok", text(c.toAssistant()));
        assertEquals("STOP", c.toAssistant().get("finish_reason"));
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
