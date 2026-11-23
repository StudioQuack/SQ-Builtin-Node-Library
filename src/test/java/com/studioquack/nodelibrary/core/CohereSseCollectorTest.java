package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** cohere-sse: content-delta → answer; tool-plan/tool-call events; message-end → finish_reason. */
class CohereSseCollectorTest {

    @Test
    void contentDeltasThenMessageEnd() {
        CohereSseCollector c = new CohereSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest("content-delta", json("""
                {"type":"content-delta","delta":{"message":{"content":{"text":"Hel"}}}}
                """)));
        seq.addAll(c.ingest("content-delta", json("""
                {"type":"content-delta","delta":{"message":{"content":{"text":"lo"}}}}
                """)));
        seq.addAll(c.ingest("stream-start", json("""
                {"type":"stream-start"}
                """)));
        seq.addAll(c.ingest("message-end", json("""
                {"type":"message-end","delta":{"finish_reason":"COMPLETE",
                  "usage":{"tokens":{"input_tokens":3,"output_tokens":2}}}}
                """)));

        assertEquals(List.of(
                new StreamCollector.Piece("answer", "Hel"),
                new StreamCollector.Piece("answer", "lo")), seq);

        Map<String, Object> out = c.toAssistant();
        assertEquals("Hello", text(out));
        assertEquals("COMPLETE", out.get("finish_reason"));
        Map<?, ?> usage = (Map<?, ?>) out.get("usage");
        assertEquals(3, usage.get("prompt_tokens"));
        assertEquals(2, usage.get("completion_tokens"));
        assertEquals(5L, ((Number) usage.get("total_tokens")).longValue());
    }

    @Test
    void toolPlanAndToolCallEventsAssembleToolCalls() {
        CohereSseCollector c = new CohereSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest("tool-plan-delta", json("""
                {"type":"tool-plan-delta","delta":{"message":{"tool_plan":"I will"}}}
                """)));
        seq.addAll(c.ingest("tool-plan-delta", json("""
                {"type":"tool-plan-delta","delta":{"message":{"tool_plan":" search"}}}
                """)));
        seq.addAll(c.ingest("tool-call-start", json("""
                {"type":"tool-call-start","index":0,
                 "delta":{"message":{"tool_calls":{"id":"get_weather_abc","type":"function",
                   "function":{"name":"get_weather","arguments":""}}}}}
                """)));
        seq.addAll(c.ingest("tool-call-delta", json("""
                {"type":"tool-call-delta","index":0,
                 "delta":{"message":{"tool_calls":{"function":{"arguments":"{\\"city\\":"}}}}}
                """)));
        seq.addAll(c.ingest("tool-call-delta", json("""
                {"type":"tool-call-delta","index":0,
                 "delta":{"message":{"tool_calls":{"function":{"arguments":"\\"x\\"}"}}}}}
                """)));
        seq.addAll(c.ingest("tool-call-end", json("""
                {"type":"tool-call-end","index":0}
                """)));
        seq.addAll(c.ingest("message-end", json("""
                {"type":"message-end","delta":{"finish_reason":"TOOL_CALL"}}
                """)));

        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "I will"),
                new StreamCollector.Piece("thinking", " search")), seq);

        Map<String, Object> out = c.toAssistant();
        assertEquals("I am calling a tool to answer your request.", text(out));
        assertEquals("TOOL_CALL", out.get("finish_reason"));
        assertEquals(List.of(Map.of("type", "thinking", "text", "I will"),
                Map.of("type", "thinking", "text", " search")), out.get("thoughts"));
        List<?> calls = (List<?>) out.get("tool_calls");
        Map<?, ?> tc = (Map<?, ?>) calls.get(0);
        assertEquals("get_weather_abc", tc.get("id"));
        assertEquals("get_weather", tc.get("name"));
        assertEquals("tool_call", tc.get("type"));
        assertEquals(Map.of("city", "x"), tc.get("args"));
    }

    @Test
    void thinkingContentChunksRouteToThinkingChannel() {
        // command-a-reasoning: reasoning streams on content-delta as chunks carrying a
        // `thinking` field, interleaved with answer text chunks on the same event type.
        CohereSseCollector c = new CohereSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest("content-delta", json("""
                {"type":"content-delta","delta":{"message":{"content":{"type":"thinking","thinking":"Let me"}}}}
                """)));
        seq.addAll(c.ingest("content-delta", json("""
                {"type":"content-delta","delta":{"message":{"content":{"type":"thinking","thinking":" think"}}}}
                """)));
        seq.addAll(c.ingest("content-delta", json("""
                {"type":"content-delta","delta":{"message":{"content":{"type":"text","text":"Hi"}}}}
                """)));

        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "Let me"),
                new StreamCollector.Piece("thinking", " think"),
                new StreamCollector.Piece("answer", "Hi")), seq);

        Map<String, Object> out = c.toAssistant();
        assertEquals("Hi", text(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "Let me"),
                Map.of("type", "thinking", "text", " think")), out.get("thoughts"));
    }

    @Test
    void nonStreamEnvelopeThinkingBlocksCollected() {
        CohereSseCollector c = new CohereSseCollector("llm_output", null);
        c.ingest(null, json("""
                {"message":{"content":[
                  {"type":"thinking","thinking":"because reasons"},
                  {"type":"text","text":"Answer"}]},
                 "finish_reason":"COMPLETE"}
                """));
        Map<String, Object> out = c.toAssistant();
        assertEquals("Answer", text(out));
        assertEquals(List.of(Map.of("type", "thinking", "text", "because reasons")),
                out.get("thoughts"));
    }

    @Test
    void unknownEventIgnored() {
        CohereSseCollector c = new CohereSseCollector("llm_output", null);
        assertTrue(c.ingest("citation-start", Map.of("type", "citation-start")).isEmpty());
        assertEquals("", text(c.toAssistant()));
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
