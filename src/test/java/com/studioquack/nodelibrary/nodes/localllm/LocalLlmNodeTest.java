package com.studioquack.nodelibrary.nodes.localllm;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-stream normalization: a raw provider envelope (OpenAI-native tool_calls,
 * string content, usage) must come out of {@link LocalLlmStream#ingest} +
 * {@link LocalLlmStream#toAssistant} in the v2 assistant shape the AI agent's
 * tool loop parses — {@code tool_calls:[{id,name,args,type:tool_call}]}.
 */
class LocalLlmNodeTest {

    @Test
    void nonStreamEnvelope_normalizesNativeToolCalls() {
        Map<String, Object> envelope = Map.of(
                "choices", List.of(Map.of(
                        "finish_reason", "tool_calls",
                        "message", Map.of(
                                "role", "assistant",
                                "content", "Let me check.",
                                "tool_calls", List.of(Map.of(
                                        "id", "call_1",
                                        "type", "function",
                                        "function", Map.of(
                                                "name", "get_weather",
                                                "arguments", "{\"city\":\"Paris\"}")))))),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 4, "total_tokens", 14));

        LocalLlmStream acc = new LocalLlmStream();
        acc.ingest(envelope, null);
        Map<String, Object> assistant = acc.toAssistant();

        assertEquals("assistant", assistant.get("role"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls = (List<Map<String, Object>>) assistant.get("tool_calls");
        assertNotNull(calls, "tool_calls must be present");
        assertEquals(1, calls.size());
        assertEquals("get_weather", calls.get(0).get("name"));
        assertEquals("tool_call", calls.get(0).get("type"));
        assertEquals(Map.of("city", "Paris"), calls.get(0).get("args"));
        assertEquals("tool_calls", assistant.get("finish_reason"));
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) assistant.get("usage");
        assertNotNull(usage);
        assertEquals(14, ((Number) usage.get("total_tokens")).intValue());
    }

    @Test
    void nonStreamEnvelope_stringContentBecomesBlocks() {
        Map<String, Object> envelope = Map.of(
                "choices", List.of(Map.of(
                        "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", "Hello there"))));

        LocalLlmStream acc = new LocalLlmStream();
        acc.ingest(envelope, null);
        Map<String, Object> assistant = acc.toAssistant();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) assistant.get("content");
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).get("type"));
        assertEquals("Hello there", content.get(0).get("text"));
        assertEquals("stop", assistant.get("finish_reason"));
    }

    @Test
    void errorEnvelope_passesThroughUnNormalized() {
        // Provider errors must stay visible to the caller (agent treats them as
        // terminal failures) — never squeezed into the assistant shape.
        Map<String, Object> envelope = Map.of("error", Map.of("message", "model not found"));
        assertTrue(envelope.get("error") != null);
    }

    @Test
    void blankAssistant_isDetectedSoTheNodeCanFailTheTurn() {
        // The exact payload a zero-token provider turn produced in the field:
        // {"role":"assistant","content":[{"type":"text","text":""}]} — must classify
        // as blank so the node returns EmptyResponseError instead of a silent "".
        Map<String, Object> blank = Map.of(
                "role", "assistant",
                "content", List.of(Map.of("type", "text", "text", "")));
        assertTrue(LocalLlmNode.isBlankAssistant(blank));

        Map<String, Object> withText = Map.of(
                "role", "assistant",
                "content", List.of(Map.of("type", "text", "text", "hi")));
        assertTrue(!LocalLlmNode.isBlankAssistant(withText));

        Map<String, Object> withTools = Map.of(
                "role", "assistant",
                "content", List.of(Map.of("type", "text", "text", "")),
                "tool_calls", List.of(Map.of("id", "call_1", "name", "t", "args", Map.of())));
        assertTrue(!LocalLlmNode.isBlankAssistant(withTools));

        Map<String, Object> withThoughts = Map.of(
                "role", "assistant",
                "content", List.of(Map.of("type", "text", "text", "")),
                "thoughts", List.of(Map.of("type", "thinking", "text", "…")));
        assertTrue(!LocalLlmNode.isBlankAssistant(withThoughts));
    }

    @Test
    void blankMessage_isDetectedOnTheRawFallbackPath() {
        assertTrue(LocalLlmNode.isBlankMessage(Map.of("role", "assistant", "content", "")));
        assertTrue(LocalLlmNode.isBlankMessage(Map.of("role", "assistant",
                "content", List.of(Map.of("type", "text", "text", " ")))));
        assertTrue(LocalLlmNode.isBlankMessage(Map.of()));
        assertTrue(!LocalLlmNode.isBlankMessage(Map.of("role", "assistant", "content", "hi")));
        assertTrue(!LocalLlmNode.isBlankMessage(Map.of("role", "assistant", "content", "",
                "tool_calls", List.of(Map.of("id", "call_1")))));
        assertTrue(!LocalLlmNode.isBlankMessage(Map.of("role", "assistant", "content", "",
                "reasoning_content", "thinking…")));
    }

    @Test
    void portsCopiesResultOutputFilesToEnvelope() {
        Map<String, Object> file = Map.of(
                "kind", "image", "mime_type", "image/png",
                "source", "base64", "data", "aGVsbG8=", "name", "out.png");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "assistant");
        result.put("content", List.of(Map.of("type", "text", "text", "ok")));
        result.put("output_files", List.of(file));
        Map<String, Object> ports = LocalLlmNode.ports(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) ports.get("llm_output");
        @SuppressWarnings("unchecked")
        Map<String, Object> published = (Map<String, Object>) inner.get("result");
        assertFalse(published.containsKey("output_files"));
        assertEquals(List.of(file), inner.get("outputFiles"));
    }

    @Test
    void portsCopiesCamelCaseOutputFilesAndOmitsEmpty() {
        Map<String, Object> file = Map.of("kind", "audio", "name", "out.wav");
        Map<String, Object> result = Map.of("outputFiles", List.of(file));
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) LocalLlmNode.ports(result).get("llm_output");
        assertEquals(List.of(file), inner.get("outputFiles"));

        Map<String, Object> empty = LocalLlmNode.ports(Map.of("role", "assistant", "content", "x"));
        @SuppressWarnings("unchecked")
        Map<String, Object> emptyInner = (Map<String, Object>) empty.get("llm_output");
        assertFalse(emptyInner.containsKey("outputFiles"));
        assertEquals("x", emptyInner.get("result") instanceof Map<?, ?> m ? m.get("content") : null);
    }

    @Test
    void streamIngestPreservesOutputFilesOnAssistant() {
        Map<String, Object> file = Map.of(
                "kind", "image", "mime_type", "image/png",
                "source", "base64", "data", "aGVsbG8=", "name", "gen.png");
        Map<String, Object> envelope = Map.of(
                "choices", List.of(Map.of(
                        "finish_reason", "stop",
                        "message", Map.of(
                                "role", "assistant",
                                "content", "here",
                                "output_files", List.of(file)))));
        LocalLlmStream acc = new LocalLlmStream();
        acc.ingest(envelope, null);
        Map<String, Object> assistant = acc.toAssistant();
        assertEquals(List.of(file), assistant.get("output_files"));
        assertTrue(!LocalLlmNode.isBlankAssistant(assistant));
    }

    @Test
    void mergeOutputFilesCopiesFromRawEnvelope() {
        Map<String, Object> dest = new LinkedHashMap<>();
        dest.put("role", "assistant");
        dest.put("content", List.of(Map.of("type", "text", "text", "ok")));
        Map<String, Object> raw = Map.of("output_files", List.of(Map.of("name", "a.png")));
        LocalLlmNode.mergeOutputFiles(dest, raw);
        assertEquals(List.of(Map.of("name", "a.png")), dest.get("output_files"));
        LocalLlmNode.mergeOutputFiles(dest, Map.of("output_files", List.of(Map.of("name", "other.png"))));
        assertEquals(List.of(Map.of("name", "a.png")), dest.get("output_files"));
    }

    /** SGLang/vLLM reject with {object:"error", …}: the node must fail with that reason, not answer empty. */
    @Test
    void openAiCompatibleRejectionFailsTheNodeWithTheServersReason() {
        Map<String, Object> sglang400 = Map.of(
                "object", "error",
                "message", "Tool 3 function has invalid 'parameters' schema: 'file' is not valid under any of the given schemas",
                "type", "BadRequestError",
                "code", 400);
        studioquack.StudioQuackError err = org.junit.jupiter.api.Assertions.assertThrows(
                studioquack.StudioQuackError.class, () -> LocalLlmNode.providerRejection(sglang400));
        assertTrue(err.getMessage().contains("rejected the request (HTTP 400)"), err.getMessage());
        assertTrue(err.getMessage().contains("invalid 'parameters' schema"), err.getMessage());

        assertEquals(null, LocalLlmNode.providerRejection(Map.of("choices", List.of())));
    }
}
