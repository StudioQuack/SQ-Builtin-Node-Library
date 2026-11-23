package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SSE / NDJSON framing around collectors (no HTTP). */
class ProviderStreamFramingTest {

    @Test
    void sseSplitsEventsAndStopsOnDone() throws Exception {
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        String raw = """
                data: {"choices":[{"delta":{"content":"A"}}]}

                : keep-alive

                data: {"choices":[{"delta":{"content":"B"}}]}

                data: [DONE]

                data: {"choices":[{"delta":{"content":"nope"}}]}
                """;
        ProviderStream.readSse(c, new BufferedReader(new StringReader(raw)));
        assertEquals("AB", text(c.toAssistant()));
    }

    @Test
    void ndjsonReadsOneObjectPerLine() throws Exception {
        OllamaNdjsonCollector c = new OllamaNdjsonCollector("llm_output", null);
        String raw = """
                {"message":{"content":"x"},"done":false}
                {"message":{"content":"y"},"done":true,"done_reason":"stop"}
                """;
        ProviderStream.readNdjson(c, new BufferedReader(new StringReader(raw)));
        assertEquals("xy", text(c.toAssistant()));
        assertEquals("stop", c.toAssistant().get("finish_reason"));
    }

    @Test
    void envelopeFallbackWhenNotEventStream() throws Exception {
        OpenAiSseCollector c = new OpenAiSseCollector("llm_output", null);
        byte[] body = "{\"choices\":[{\"message\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}".getBytes();
        ProviderStream.consume(c, "openai-sse", new java.io.ByteArrayInputStream(body),
                "application/json", 1_000_000);
        assertEquals("hi", text(c.toAssistant()));
    }

    @Test
    void responsesEnvelopeFallbackWhenNotEventStream() throws Exception {
        ResponsesSseCollector c = new ResponsesSseCollector("llm_output", null);
        byte[] body = "{\"object\":\"response\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"hi\"}]}]}".getBytes();
        ProviderStream.consume(c, "responses-sse", new java.io.ByteArrayInputStream(body),
                "application/json", 1_000_000);
        assertEquals("hi", text(c.toAssistant()));
        assertEquals("completed", c.toAssistant().get("finish_reason"));
    }

    @Test
    void knownFormatsMatchSpecContract() {
        assertTrue(ProviderStream.knownFormat("openai-sse"));
        assertTrue(ProviderStream.knownFormat("ANTHROPIC-SSE"));
        assertTrue(ProviderStream.knownFormat("responses-sse"));
        assertEquals(6, ProviderStream.FORMATS.size());
        assertTrue(ProviderStream.collector("nope", "llm_output", null) == null);
        assertTrue(ProviderStream.collector("responses-sse", "llm_output", null) instanceof ResponsesSseCollector);
    }

    @SuppressWarnings("unchecked")
    private static String text(Map<String, Object> assistant) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) assistant.get("content");
        return String.valueOf(content.get(0).get("text"));
    }
}
