package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** anthropic-sse: event/data pairs → thinking/answer/tool_calls and v2 usage. */
class AnthropicSseCollectorTest {

    @Test
    void thinkingTextToolAndUsageSequence() {
        AnthropicSseCollector c = new AnthropicSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest("message_start", json("""
                {"type":"message_start","message":{"usage":{"input_tokens":10}}}
                """)));
        seq.addAll(c.ingest("content_block_delta", json("""
                {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"plan"}}
                """)));
        seq.addAll(c.ingest("content_block_delta", json("""
                {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hi"}}
                """)));
        seq.addAll(c.ingest("content_block_start", json("""
                {"type":"content_block_start","content_block":{"type":"tool_use","id":"toolu_1","name":"search"}}
                """)));
        seq.addAll(c.ingest("content_block_delta", json("""
                {"type":"content_block_delta","delta":{"type":"input_json_delta","partial_json":"{\\"q\\":"}}
                """)));
        seq.addAll(c.ingest("content_block_delta", json("""
                {"type":"content_block_delta","delta":{"type":"input_json_delta","partial_json":"\\"x\\"}"}}
                """)));
        seq.addAll(c.ingest("message_delta", json("""
                {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":4}}
                """)));
        seq.addAll(c.ingest("message_stop", json("""
                {"type":"message_stop"}
                """)));

        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "plan"),
                new StreamCollector.Piece("answer", "Hi")), seq);

        Map<String, Object> out = c.toAssistant();
        assertEquals("Hi", text(out));
        assertEquals("tool_use", out.get("finish_reason"));
        assertEquals(List.of(Map.of("type", "thinking", "text", "plan")), out.get("thoughts"));
        Map<?, ?> usage = (Map<?, ?>) out.get("usage");
        assertEquals(10, usage.get("prompt_tokens"));
        assertEquals(4, usage.get("completion_tokens"));
        assertEquals(14L, ((Number) usage.get("total_tokens")).longValue());
        List<?> calls = (List<?>) out.get("tool_calls");
        Map<?, ?> tc = (Map<?, ?>) calls.get(0);
        assertEquals("toolu_1", tc.get("id"));
        assertEquals("search", tc.get("name"));
        assertEquals(Map.of("q", "x"), tc.get("args"));
    }

    @Test
    void unknownEventsIgnored() {
        AnthropicSseCollector c = new AnthropicSseCollector("llm_output", null);
        assertTrue(c.ingest("ping", Map.of("type", "ping")).isEmpty());
        assertEquals("assistant", c.toAssistant().get("role"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void signatureDeltaIsIgnored() {
        AnthropicSseCollector c = new AnthropicSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest("content_block_delta", json("""
                {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"plan"}}
                """)));
        seq.addAll(c.ingest("content_block_delta", json("""
                {"type":"content_block_delta","delta":{"type":"signature_delta","signature":"EqQBCg"}}
                """)));
        seq.addAll(c.ingest("content_block_stop", json("""
                {"type":"content_block_stop","index":0}
                """)));
        assertEquals(List.of(new StreamCollector.Piece("thinking", "plan")), seq);
        assertEquals("plan", ((List<Map<String, Object>>) c.toAssistant().get("thoughts")).get(0).get("text"));
    }

    @Test
    void nonStreamMessageEnvelope() {
        AnthropicSseCollector c = new AnthropicSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = c.ingest(null, json("""
                {"type":"message","content":[
                  {"type":"thinking","thinking":"why"},
                  {"type":"text","text":"ok"}],
                 "stop_reason":"end_turn",
                 "usage":{"input_tokens":2,"output_tokens":3}}
                """));
        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "why"),
                new StreamCollector.Piece("answer", "ok")), seq);
        Map<String, Object> out = c.toAssistant();
        assertEquals("ok", text(out));
        assertEquals("end_turn", out.get("finish_reason"));
        assertEquals(5L, ((Number) ((Map<?, ?>) out.get("usage")).get("total_tokens")).longValue());
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
