package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ollama-ndjson: content/thinking deltas, done_reason, eval counts. */
class OllamaNdjsonCollectorTest {

    @Test
    void thinkingContentDoneAndUsage() {
        OllamaNdjsonCollector c = new OllamaNdjsonCollector("llm_output", null);
        List<StreamCollector.Piece> seq = new ArrayList<>();
        seq.addAll(c.ingest(null, json("""
                {"message":{"thinking":"hmm","content":""},"done":false}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"message":{"content":"Hi"},"done":false}
                """)));
        seq.addAll(c.ingest(null, json("""
                {"message":{"content":""},"done":true,"done_reason":"stop",
                 "prompt_eval_count":8,"eval_count":2}
                """)));

        assertEquals(List.of(
                new StreamCollector.Piece("thinking", "hmm"),
                new StreamCollector.Piece("answer", "Hi")), seq);

        Map<String, Object> out = c.toAssistant();
        assertEquals("Hi", text(out));
        assertEquals("stop", out.get("finish_reason"));
        assertEquals(List.of(Map.of("type", "thinking", "text", "hmm")), out.get("thoughts"));
        Map<?, ?> usage = (Map<?, ?>) out.get("usage");
        assertEquals(8, usage.get("prompt_tokens"));
        assertEquals(2, usage.get("completion_tokens"));
        assertEquals(10L, ((Number) usage.get("total_tokens")).longValue());
    }

    @Test
    void nonStreamEnvelopeWithDoneTrue() {
        OllamaNdjsonCollector c = new OllamaNdjsonCollector("llm_output", null);
        c.ingest(null, json("""
                {"message":{"content":"ok","thinking":"why"},"done":true,"done_reason":"stop",
                 "prompt_eval_count":1,"eval_count":1}
                """));
        Map<String, Object> out = c.toAssistant();
        assertEquals("ok", text(out));
        assertEquals("why", ((List<Map<String, Object>>) out.get("thoughts")).get(0).get("text"));
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
