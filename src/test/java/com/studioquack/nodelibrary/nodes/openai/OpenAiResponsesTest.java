package com.studioquack.nodelibrary.nodes.openai;

import com.studioquack.nodelibrary.core.ProviderStream;
import com.studioquack.nodelibrary.core.StreamCollector;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OpenAiResponses}: the Responses-API gate, the Chat→Responses request translation,
 * and the stream wiring that lands reasoning summaries on the {@code thinking} channel.
 */
class OpenAiResponsesTest {

    private static Map<String, Object> requestWithParams(Map<String, Object> params) {
        List<Object> list = new java.util.ArrayList<>();
        params.forEach((k, v) -> list.add(Map.of("shortCode", k, "value", v)));
        return Map.of("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", list))));
    }

    private static OpenAiConfig configOf(Map<String, Object> params) {
        return OpenAiConfig.from(requestWithParams(params), null);
    }

    // ---------- supports gate ----------

    @Test
    void reasoningModelsTakeTheResponsesPath() {
        assertTrue(OpenAiResponses.supports(configOf(Map.of("model", "gpt-5.6-sol")), "gpt-5.6-sol", Map.of()));
        assertTrue(OpenAiResponses.supports(configOf(Map.of("model", "o3")), "o3", Map.of()));
        assertTrue(OpenAiResponses.supports(configOf(Map.of()), "gpt-5.6-sol", Map.of()),
                "the default model is a reasoning model");
    }

    @Test
    void nonReasoningModelsStayOnChatCompletions() {
        assertFalse(OpenAiResponses.supports(configOf(Map.of("model", "gpt-4o")), "gpt-4o", Map.of()));
        assertFalse(OpenAiResponses.supports(configOf(Map.of("model", "gpt-4.1")), "gpt-4.1", Map.of()));
    }

    @Test
    void audioModalitiesStayOnChatCompletionsEvenForReasoningModels() {
        OpenAiConfig cfg = configOf(Map.of("model", "gpt-5.6-sol", "modalities", "text,audio"));
        assertFalse(OpenAiResponses.supports(cfg, "gpt-5.6-sol", Map.of()));
        OpenAiConfig noMod = configOf(Map.of("model", "gpt-5.6-sol"));
        assertFalse(OpenAiResponses.supports(noMod, "gpt-5.6-sol", Map.of("modalities", List.of("audio"))),
                "input-carried modalities count too");
    }

    // ---------- rejection classifier ----------

    @Test
    void responsesRejectionIsDistinguishedFromParameterErrors() {
        assertTrue(OpenAiResponses.isResponsesRejection(404, "Not found"));
        assertTrue(OpenAiResponses.isResponsesRejection(400,
                "The model `o1-mini` is not supported on v1/responses."));
        assertFalse(OpenAiResponses.isResponsesRejection(400,
                "Unsupported parameter: 'temperature'"), "param-level 400s are for the strip-retry");
        assertFalse(OpenAiResponses.isResponsesRejection(401, "invalid api key"));
        assertFalse(OpenAiResponses.isResponsesRejection(500, "boom"));
    }

    // ---------- buildKwargs ----------

    @Test
    void reasoningSummaryAndEncryptedIncludeAreAlwaysSent() {
        OpenAiConfig cfg = configOf(Map.of("model", "gpt-5.6-sol"));
        Map<String, Object> req = OpenAiResponses.buildKwargs(cfg,
                List.of(Map.of("role", "user", "content", "hi")), List.of());

        assertEquals("gpt-5.6-sol", req.get("model"));
        @SuppressWarnings("unchecked")
        Map<String, Object> reasoning = (Map<String, Object>) req.get("reasoning");
        assertEquals("medium", reasoning.get("effort"), "default effort");
        assertEquals("auto", reasoning.get("summary"),
                "without summary:auto the API streams no reasoning_summary_text deltas");
        assertEquals(List.of("reasoning.encrypted_content"), req.get("include"),
                "without the encrypted-content include the summary streams empty");
        assertFalse(req.containsKey("stream"), "the caller owns the stream flag");
        assertFalse(req.containsKey("temperature"), "reasoning models reject sampling keys");
        assertFalse(req.containsKey("messages"), "Responses speaks input, not messages");
    }

    @Test
    void tokenCapsAndVerbosityAndFormatLandInResponsesShape() {
        OpenAiConfig cfg = configOf(Map.of(
                "model", "gpt-5.6-sol",
                "max_completion_tokens", "512",
                "reasoning_effort", "high",
                "verbosity", "low",
                "response_format", "json"));
        Map<String, Object> req = OpenAiResponses.buildKwargs(cfg,
                List.of(Map.of("role", "user", "content", "hi")), List.of());

        assertEquals(512, req.get("max_output_tokens"));
        assertFalse(req.containsKey("max_completion_tokens"));
        @SuppressWarnings("unchecked")
        Map<String, Object> reasoning = (Map<String, Object>) req.get("reasoning");
        assertEquals("high", reasoning.get("effort"));
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) req.get("text");
        assertEquals("low", text.get("verbosity"));
        assertEquals(Map.of("type", "json_object"), text.get("format"));
    }

    @Test
    void toolsFlattenToTheResponsesShape() {
        OpenAiConfig cfg = configOf(Map.of("model", "gpt-5.6-sol"));
        List<Object> tools = List.of(Map.of(
                "name", "lookup",
                "description", "find a thing",
                "request", Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string")))));
        Map<String, Object> req = OpenAiResponses.buildKwargs(cfg,
                List.of(Map.of("role", "user", "content", "hi")), tools);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> out = (List<Map<String, Object>>) req.get("tools");
        assertEquals(1, out.size());
        Map<String, Object> tool = out.get(0);
        assertEquals("function", tool.get("type"));
        assertEquals("lookup", tool.get("name"));
        assertEquals("find a thing", tool.get("description"));
        assertTrue(tool.get("parameters") instanceof Map<?, ?>);
        assertNull(tool.get("function"), "no nested function object on the Responses wire");
        assertEquals("auto", req.get("tool_choice"));
    }

    // ---------- toResponsesInput ----------

    @Test
    void systemBecomesDeveloperAndToolExchangeBecomesFunctionCallItems() {
        List<Map<String, Object>> chat = List.of(
                Map.of("role", "system", "content", "You are careful."),
                Map.of("role", "user", "content", "hi"),
                Map.of("role", "assistant", "content", "Let me check.",
                        "tool_calls", List.of(Map.of("id", "call_1", "type", "function",
                                "function", Map.of("name", "lookup", "arguments", "{\"q\":\"x\"}")))),
                Map.of("role", "tool", "tool_call_id", "call_1", "content", "found it"));
        List<Object> input = OpenAiResponses.toResponsesInput(chat);

        assertEquals(5, input.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> sys = (Map<String, Object>) input.get(0);
        assertEquals("developer", sys.get("role"));
        assertEquals("You are careful.", sys.get("content"));

        @SuppressWarnings("unchecked")
        Map<String, Object> assistant = (Map<String, Object>) input.get(2);
        assertEquals("assistant", assistant.get("role"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) assistant.get("content");
        assertEquals("output_text", parts.get(0).get("type"),
                "assistant history must use output_text parts on the Responses wire");

        @SuppressWarnings("unchecked")
        Map<String, Object> call = (Map<String, Object>) input.get(3);
        assertEquals("function_call", call.get("type"));
        assertEquals("call_1", call.get("call_id"));
        assertEquals("lookup", call.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> toolOut = (Map<String, Object>) input.get(4);
        assertEquals("function_call_output", toolOut.get("type"));
        assertEquals("call_1", toolOut.get("call_id"));
        assertEquals("found it", toolOut.get("output"));
    }

    @Test
    void userMediaPartsMapToInputTypes() {
        List<Map<String, Object>> chat = List.of(
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", "what is this?"),
                        Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,AA", "detail", "auto")),
                        Map.of("type", "file", "file", Map.of("file_data", "data:application/pdf;base64,BB", "filename", "spec.pdf")))));
        List<Object> input = OpenAiResponses.toResponsesInput(chat);

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) input.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) user.get("content");
        assertEquals("input_text", parts.get(0).get("type"));
        assertEquals("input_image", parts.get(1).get("type"));
        assertEquals("data:image/png;base64,AA", parts.get(1).get("image_url"));
        assertEquals("input_file", parts.get(2).get("type"));
        assertEquals("spec.pdf", parts.get(2).get("filename"));
    }

    // ---------- stream wiring ----------

    @Test
    void theResponsesStreamFeedsThinkingAndAnswerChannels() throws Exception {
        String sse = """
                event: response.reasoning_summary_text.delta
                data: {"type":"response.reasoning_summary_text.delta","item_id":"rs_1","delta":"I should"}

                event: response.reasoning_summary_text.delta
                data: {"type":"response.reasoning_summary_text.delta","item_id":"rs_1","delta":" look."}

                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"Done."}

                event: response.completed
                data: {"type":"response.completed","response":{"id":"resp_1","object":"response","status":"completed","usage":{"input_tokens":3,"output_tokens":2,"total_tokens":5}}}

                data: [DONE]
                """;
        StreamCollector collector = ProviderStream.collector("responses-sse", "llm_output", null);
        ProviderStream.consume(collector, "responses-sse",
                new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)), "text/event-stream", 0);

        Map<String, Object> out = collector.toAssistant();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) out.get("content");
        assertEquals("Done.", content.get(0).get("text"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> thoughts = (List<Map<String, Object>>) out.get("thoughts");
        assertEquals(2, thoughts.size(), "each reasoning summary delta is one thought entry");
        assertEquals("I should", thoughts.get(0).get("text"));
        assertEquals(" look.", thoughts.get(1).get("text"));
        assertEquals("completed", out.get("finish_reason"));
    }
}
