package com.studioquack.nodelibrary.nodes.openai;

import com.studioquack.nodelibrary.core.OpenAiContentParts;
import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studioquack.StudioQuackError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI Models handler: inputFiles fold, llm_output envelope, schema contract.
 * Never logs api_key or file bytes.
 */
class OpenAiModelsNodeTest {

    private static final Path DEF = Path.of("nodes/openai-models/node_definition.json");

    private static final List<String> FILE_CHILDREN =
            List.of("kind", "mime_type", "source", "data", "name");

    @Test
    void portsMirrorsOutputFilesWhenNonEmpty() {
        List<Map<String, Object>> files = List.of(Map.of(
                "type", "image", "mime_type", "image/png", "source", "base64",
                "data", "iVBORw0KGgo=", "name", "out.png"));
        Map<String, Object> assistant = new java.util.LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", List.of(Map.of("type", "text", "text", "done")));
        assistant.put("output_files", files);
        assistant.put("finish_reason", "stop");

        Map<String, Object> envelope = OpenAiModelsNode.ports(assistant);
        @SuppressWarnings("unchecked")
        Map<String, Object> llm = (Map<String, Object>) envelope.get("llm_output");
        assertEquals(assistant, llm.get("result"));
        assertEquals(files, llm.get("outputFiles"));
        assertTrue(assistant.containsKey("output_files"), "result object must stay unchanged");
    }

    @Test
    void portsOmitsOutputFilesWhenEmptyOrMissing() {
        Map<String, Object> noFiles = Map.of("role", "assistant",
                "content", List.of(Map.of("type", "text", "text", "hi")));
        assertFalse(asLlm(OpenAiModelsNode.ports(noFiles)).containsKey("outputFiles"));

        Map<String, Object> empty = new java.util.LinkedHashMap<>(noFiles);
        empty.put("output_files", List.of());
        assertFalse(asLlm(OpenAiModelsNode.ports(empty)).containsKey("outputFiles"));
        assertFalse(asLlm(OpenAiModelsNode.ports(null)).containsKey("outputFiles"));
    }

    @Test
    void foldedInputFilesSurviveBuildOpenaiMessagesAsImageUrl() {
        List<Object> folded = OpenAiContentParts.appendFilesToLastUserMessage(
                List.of(Map.of("role", "user", "content", "see this")),
                List.of(Map.of("kind", "image", "mime_type", "image/png",
                        "source", "base64", "data", "iVBORw0KGgo=", "name", "x.png")),
                OpenAiContentParts.Profile.FULL);
        List<Map<String, Object>> openai = OpenAiChat.buildOpenaiMessages(folded);
        Object content = openai.get(0).get("content");
        assertTrue(content instanceof List<?>, "folded image must stay a part list");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content;
        assertEquals("text", parts.get(0).get("type"));
        assertEquals("image_url", parts.get(1).get("type"));
        assertEquals("data:image/png;base64,iVBORw0KGgo=",
                Json.asMap(parts.get(1).get("image_url")).get("url"));
    }

    @Test
    void mergeOptionsExcludesInputFiles() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("messages", List.of(Map.of("role", "user", "content", "hi")));
        payload.put("inputFiles", List.of(Map.of("kind", "image")));
        payload.put("stream", true);
        Map<String, Object> options = OpenAiModelsNode.mergeOptions(payload);
        assertFalse(options.containsKey("inputFiles"));
        assertEquals(true, options.get("stream"));
    }

    @Test
    void usableWhenOnlyInputFilesPresent() {
        assertTrue(OpenAiModelsNode.usable(Map.of("inputFiles",
                List.of(Map.of("kind", "audio", "data", "UklGRg==")))));
    }

    @Test
    void capabilityGateRejectsO3MiniImageWithoutNetwork() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> new OpenAiModelsNode().handle(chatRequest("o3-mini", imageUserTurn()), null));
        assertEquals("PROVIDER_ERROR", e.errorCode());
        assertTrue(e.getMessage().contains("o3-mini"), e.getMessage());
        assertTrue(e.getMessage().contains("does not accept image inputs"), e.getMessage());
        assertTrue(e.getMessage().contains("gpt-5.5"), e.getMessage());
    }

    @Test
    void capabilityGateRejectsO1MiniImage() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> new OpenAiModelsNode().handle(chatRequest("o1-mini", imageUserTurn()), null));
        assertTrue(e.getMessage().contains("o1-mini"), e.getMessage());
        assertTrue(e.getMessage().contains("does not accept image inputs"), e.getMessage());
    }

    @Test
    void imageGenModelSkipsGateAndRoutesToImages() {
        assertFalse(OpenAiCapabilities.shouldGate("gpt-image-1"));
        // Blank prompt fails inside images() before HTTP — proves the chat gate was skipped.
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> new OpenAiModelsNode().handle(
                        chatRequest("gpt-image-1", Map.of("messages",
                                List.of(Map.of("role", "user", "content", "")))), null));
        assertTrue(e.getMessage().contains("Image generation requires a text prompt")
                || e.getMessage().contains("image"), e.getMessage());
    }

    @Test
    void visionModelsAreNotRejectedByGateAlone() {
        assertTrue(OpenAiCapabilities.acceptsVision("gpt-4o"));
        assertTrue(OpenAiCapabilities.acceptsVision("gpt-5"));
        assertTrue(OpenAiCapabilities.acceptsVision("gpt-5.5"));
        assertTrue(OpenAiCapabilities.acceptsVision("o3"));
        assertTrue(OpenAiCapabilities.acceptsVision("o4-mini"));
        @SuppressWarnings("unchecked")
        List<Object> msgs = (List<Object>) imageUserTurn().get("messages");
        OpenAiCapabilities.GateShape shape = OpenAiCapabilities.inspect(msgs);
        assertEquals(1, shape.images);
    }

    private static Map<String, Object> chatRequest(String model, Map<String, Object> llmInput) {
        List<Object> params = new java.util.ArrayList<>();
        params.add(Map.of("shortCode", "model", "value", model));
        params.add(Map.of("shortCode", "api_key", "value", "sk-test"));
        return Map.of(
                "executionContext", Map.of("currentNode",
                        Map.of("data", Map.of("configurationParameters", params))),
                "inputVariables", Map.of("llm_input", llmInput));
    }

    private static Map<String, Object> imageUserTurn() {
        return Map.of("messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "text", "text", "what is this"),
                Map.of("type", "image", "mime_type", "image/png", "source", "base64",
                        "data", "iVBORw0KGgo=", "name", "x.png")))));
    }

    @SuppressWarnings("unchecked")
    private static String assistantText(Map<String, Object> envelope) {
        Map<String, Object> llm = (Map<String, Object>) envelope.get("llm_output");
        Map<String, Object> result = (Map<String, Object>) llm.get("result");
        Object content = result.get("content");
        if (content instanceof List<?> parts && !parts.isEmpty()) {
            return Json.str(Json.asMap(parts.get(0)).get("text"))
                    + " " + Json.str(result.get("error"));
        }
        return Json.str(result.get("error"));
    }

    @Test
    void schemaDeclaresInputAndOutputFilesWithFiveChildren() throws Exception {
        Map<String, Object> def = Json.asMap(Json.parse(Files.readString(DEF)));
        Map<String, Object> llmIn = port(def, "llm_input");
        Map<String, Object> llmOut = port(def, "llm_output");

        Map<String, Object> inputFiles = field(Json.asList(llmIn.get("schema")), "inputFiles");
        assertEquals("file", inputFiles.get("type"));
        assertFalse((Boolean) inputFiles.get("required"));
        assertEquals(FILE_CHILDREN, childCodes(inputFiles));

        Map<String, Object> outputFiles = field(Json.asList(llmOut.get("schema")), "outputFiles");
        assertEquals("file", outputFiles.get("type"));
        assertFalse((Boolean) outputFiles.get("required"));
        assertEquals(FILE_CHILDREN, childCodes(outputFiles));

        Map<String, Object> result = field(Json.asList(llmOut.get("schema")), "result");
        Map<String, Object> outputFilesInner = field(Json.asList(result.get("children")), "output_files");
        assertEquals("file", outputFilesInner.get("type"));
        assertEquals(FILE_CHILDREN, childCodes(outputFilesInner));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asLlm(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("llm_output");
    }

    private static Map<String, Object> port(Map<String, Object> def, String name) {
        for (Object p : Json.asList(def.get("ports"))) {
            Map<String, Object> port = Json.asMap(p);
            if (name.equals(port.get("name"))) {
                return port;
            }
        }
        throw new AssertionError("missing port " + name);
    }

    private static Map<String, Object> field(List<Object> schema, String shortCode) {
        for (Object item : schema) {
            Map<String, Object> row = Json.asMap(item);
            if (shortCode.equals(row.get("shortCode"))) {
                return row;
            }
        }
        throw new AssertionError("missing field " + shortCode);
    }

    private static List<String> childCodes(Map<String, Object> field) {
        List<String> codes = new ArrayList<>();
        for (Object c : Json.asList(field.get("children"))) {
            codes.add(Json.str(Json.asMap(c).get("shortCode")));
        }
        return codes;
    }
}
