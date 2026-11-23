package com.studioquack.nodelibrary.nodes.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Capability table + leftover-image reject. No network. */
class OpenAiCapabilitiesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "gpt-4o", "gpt-4o-mini", "gpt-4o-audio-preview", "gpt-4.1", "gpt-4.1-mini",
            "gpt-4-turbo", "gpt-4-vision-preview",
            "gpt-5", "gpt-5.1", "gpt-5.5", "gpt-5.6-sol", "gpt-5.6-terra",
            "o1", "o1-pro", "o3", "o3-pro", "o4-mini", "o4-mini-high"
    })
    void visionCapableFamilies(String model) {
        assertTrue(OpenAiCapabilities.acceptsVision(model), model);
        assertTrue(OpenAiCapabilities.acceptsNativeDocuments(model), model);
        assertTrue(OpenAiCapabilities.shouldGate(model), model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"o3-mini", "o3-mini-high", "o1-mini", "o1-preview", "gpt-3.5-turbo", "gpt-3.5"})
    void noVisionFamilies(String model) {
        assertFalse(OpenAiCapabilities.acceptsVision(model), model);
        assertFalse(OpenAiCapabilities.acceptsNativeDocuments(model), model);
        assertTrue(OpenAiCapabilities.shouldGate(model), model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gpt-image-1", "gpt-image-1.5", "dall-e-3", "tts-1", "gpt-4o-mini-tts",
            "whisper-1", "gpt-4o-transcribe"})
    void imageTtsSttNeverGated(String model) {
        assertFalse(OpenAiCapabilities.shouldGate(model), model);
        assertFalse(OpenAiCapabilities.acceptsVision(model), model);
    }

    @Test
    void rejectImagesMessageNamesGapAndExamples() {
        String msg = OpenAiCapabilities.rejectImagesMessage("o3-mini");
        assertTrue(msg.contains("model 'o3-mini'"), msg);
        assertTrue(msg.contains("does not accept image inputs"), msg);
        assertTrue(msg.contains("gpt-5.5"), msg);
        assertTrue(msg.contains("gpt-5"), msg);
        assertTrue(msg.contains("gpt-4o"), msg);
    }

    @Test
    void inspectCountsLeftoverImagesAndExtractedOffice() {
        List<Object> messages = List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "text", "text", "[File 'a.xlsx' (sheet), extracted text]\nAda"),
                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,xx")),
                Map.of("type", "file", "file", Map.of("filename", "a.pdf")))));
        OpenAiCapabilities.GateShape shape = OpenAiCapabilities.inspect(messages);
        assertEquals(1, shape.images);
        assertEquals(1, shape.documents);
        assertEquals(1, shape.officeExtracted);
    }
}
