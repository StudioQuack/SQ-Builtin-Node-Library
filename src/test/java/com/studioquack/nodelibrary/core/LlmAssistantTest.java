package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Envelope helpers for Official-API LLM ports. */
class LlmAssistantTest {

    @Test
    void portsCopiesOutputFilesFromResultOutputFiles() {
        Map<String, Object> result = LlmAssistant.makeResponse(
                List.of("ok"), null, null,
                List.of(Map.of("type", "image", "mime_type", "image/png",
                        "source", "base64", "data", "abc", "name", "out.png")),
                "stop", null);
        Map<String, Object> ports = LlmAssistant.ports("llm_output", result);
        assertTrue(ports.containsKey("llm_output"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) ports.get("llm_output");
        @SuppressWarnings("unchecked")
        Map<String, Object> published = (Map<String, Object>) inner.get("result");
        assertFalse(published.containsKey("output_files"));
        assertEquals("ok", ((List<Map<String, Object>>) published.get("content")).get(0).get("text"));
        assertEquals(result.get("output_files"), inner.get("outputFiles"));
    }

    @Test
    void portsOmitsOutputFilesWhenEmpty() {
        Map<String, Object> result = LlmAssistant.makeResponse(List.of("ok"), null, null, null, "stop", null);
        Map<String, Object> ports = LlmAssistant.ports("llm_output", result);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) ports.get("llm_output");
        assertFalse(inner.containsKey("outputFiles"));
    }
}
