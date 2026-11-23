package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studioquack.StudioQuackError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * responses-sse: thinking + answer deltas, function_call items, completed usage,
 * and unary Response envelope fallback.
 */
class ResponsesSseCollectorTest {

    @Test
    void thinkingAnswerFunctionCallAndCompletedUsage() {
        ResponsesSseCollector c = new ResponsesSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest("response.created", json("""
                {"type":"response.created","response":{"id":"resp_1","object":"response","status":"in_progress"}}
                """)));
        seq.addAll(c.ingest("response.output_item.added", json("""
                {"type":"response.output_item.added","output_index":0,
                 "item":{"id":"rs_1","type":"reasoning"}}
                """)));
        seq.addAll(c.ingest("response.reasoning_text.delta", json("""
                {"type":"response.reasoning_text.delta","item_id":"rs_1","delta":"I will"}
                """)));
        seq.addAll(c.ingest("response.reasoning_summary_text.delta", json("""
                {"type":"response.reasoning_summary_text.delta","item_id":"rs_1","delta":" search"}
                """)));
        seq.addAll(c.ingest("response.output_item.added", json("""
                {"type":"response.output_item.added","output_index":1,
                 "item":{"id":"fc_1","call_id":"call_lookup","type":"function_call","name":"lookup","arguments":""}}
                """)));
        seq.addAll(c.ingest("response.function_call_arguments.delta", json("""
                {"type":"response.function_call_arguments.delta","item_id":"fc_1","delta":"{\\"q\\":"}
                """)));
        seq.addAll(c.ingest("response.function_call_arguments.delta", json("""
                {"type":"response.function_call_arguments.delta","item_id":"fc_1","delta":"\\"x\\"}"}
                """)));
        seq.addAll(c.ingest("response.output_item.done", json("""
                {"type":"response.output_item.done","output_index":1,
                 "item":{"id":"fc_1","call_id":"call_lookup","type":"function_call",
                   "name":"lookup","arguments":"{\\"q\\":\\"x\\"}"}}
                """)));
        seq.addAll(c.ingest("response.output_text.delta", json("""
                {"type":"response.output_text.delta","delta":"Hel"}
                """)));
        seq.addAll(c.ingest("response.output_text.delta", json("""
                {"type":"response.output_text.delta","delta":"lo"}
                """)));
        seq.addAll(c.ingest("response.completed", json("""
                {"type":"response.completed","response":{"id":"resp_1","object":"response","status":"completed",
                  "usage":{"input_tokens":3,"output_tokens":2,"total_tokens":5,
                           "output_tokens_details":{"reasoning_tokens":1}}}}
                """)));

        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "I will"),
                new StreamCollector.Piece("thinking", " search"),
                new StreamCollector.Piece("answer", "Hel"),
                new StreamCollector.Piece("answer", "lo")), seq);

        Map<String, Object> out = c.toAssistant();
        assertEquals("Hello", text(out));
        assertEquals("completed", out.get("finish_reason"));
        assertEquals(List.of(Map.of("type", "thinking", "text", "I will"),
                Map.of("type", "thinking", "text", " search")), out.get("thoughts"));
        Map<?, ?> usage = (Map<?, ?>) out.get("usage");
        assertEquals(3, usage.get("prompt_tokens"));
        assertEquals(2, usage.get("completion_tokens"));
        assertEquals(5, usage.get("total_tokens"));
        assertEquals(1, usage.get("reasoning_tokens"));
        List<?> calls = (List<?>) out.get("tool_calls");
        Map<?, ?> tc = (Map<?, ?>) calls.get(0);
        assertEquals("call_lookup", tc.get("id"));
        assertEquals("lookup", tc.get("name"));
        assertEquals("tool_call", tc.get("type"));
        assertEquals(Map.of("q", "x"), tc.get("args"));
    }

    @Test
    void functionCallOnlyUsesPlaceholderAnswer() {
        ResponsesSseCollector c = new ResponsesSseCollector("llm_output", null);
        c.ingest("response.output_item.done", json("""
                {"type":"response.output_item.done","output_index":0,
                 "item":{"id":"fc_1","call_id":"call_1","type":"function_call",
                   "name":"lookup","arguments":"{\\"q\\":\\"x\\"}"}}
                """));
        c.ingest("response.completed", json("""
                {"type":"response.completed","response":{"status":"completed"}}
                """));
        Map<String, Object> out = c.toAssistant();
        assertEquals("I am calling a tool to answer your request.", text(out));
        assertEquals("completed", out.get("finish_reason"));
    }

    @Test
    void unknownEventIgnored() {
        ResponsesSseCollector c = new ResponsesSseCollector("llm_output", null);
        assertTrue(c.ingest("response.reasoning.search_results",
                Map.of("type", "response.reasoning.search_results")).isEmpty());
        assertEquals("", text(c.toAssistant()));
    }

    @Test
    void failedEventYieldsErrorPayload() {
        ResponsesSseCollector c = new ResponsesSseCollector("llm_output", null);
        c.ingest("response.failed", json("""
                {"type":"response.failed","response":{"status":"failed",
                  "error":{"code":"server_error","message":"boom"}}}
                """));
        StudioQuackError e = assertThrows(StudioQuackError.class, c::toAssistant);
        assertEquals("PROVIDER_ERROR", e.errorCode());
        assertTrue(e.getMessage().contains("boom"), e.getMessage());
        assertEquals("server_error", e.data().get("finish_reason"));
    }

    @Test
    void errorEventYieldsErrorPayload() {
        ResponsesSseCollector c = new ResponsesSseCollector("llm_output", null);
        c.ingest("error", json("""
                {"type":"error","code":"invalid_request","message":"bad"}
                """));
        StudioQuackError e = assertThrows(StudioQuackError.class, c::toAssistant);
        assertEquals("PROVIDER_ERROR", e.errorCode());
        assertTrue(e.getMessage().contains("bad"), e.getMessage());
        assertEquals("invalid_request", e.data().get("finish_reason"));
    }

    @Test
    void nonStreamEnvelopeIngest() {
        ResponsesSseCollector c = new ResponsesSseCollector("llm_output", null);
        List<StreamCollector.Piece> seq = c.ingest(null, json("""
                {"id":"resp_1","object":"response","status":"completed",
                 "output":[
                   {"type":"reasoning","summary":[{"type":"summary_text","text":"why"}]},
                   {"type":"message","role":"assistant","content":[{"type":"output_text","text":"ok"}]},
                   {"type":"function_call","id":"fc_1","call_id":"call_1","name":"lookup",
                    "arguments":"{\\"q\\":\\"x\\"}"}],
                 "usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}
                """));
        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "why"),
                new StreamCollector.Piece("answer", "ok")), seq);
        Map<String, Object> out = c.toAssistant();
        assertEquals("ok", text(out));
        assertEquals("completed", out.get("finish_reason"));
        assertEquals(1, ((Map<?, ?>) out.get("usage")).get("prompt_tokens"));
        assertEquals(2, ((Map<?, ?>) out.get("usage")).get("completion_tokens"));
        List<?> calls = (List<?>) out.get("tool_calls");
        Map<?, ?> tc = (Map<?, ?>) calls.get(0);
        assertEquals("call_1", tc.get("id"));
        assertEquals("lookup", tc.get("name"));
        assertEquals(Map.of("q", "x"), tc.get("args"));
    }

    @Test
    void completedDoesNotDuplicateStreamedText() {
        ResponsesSseCollector c = new ResponsesSseCollector("llm_output", null);
        c.ingest("response.output_text.delta", json("""
                {"type":"response.output_text.delta","delta":"Hi"}
                """));
        c.ingest("response.completed", json("""
                {"type":"response.completed","response":{"object":"response","status":"completed",
                  "output":[{"type":"message","content":[{"type":"output_text","text":"Hi"}]}],
                  "usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2}}}
                """));
        assertEquals("Hi", text(c.toAssistant()));
        assertEquals(1, ((Map<?, ?>) c.toAssistant().get("usage")).get("prompt_tokens"));
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
