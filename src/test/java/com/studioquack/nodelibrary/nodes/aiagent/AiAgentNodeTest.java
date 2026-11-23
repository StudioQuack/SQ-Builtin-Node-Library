package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AiAgentNode#mainOutput} always publishes {@code voices} (audio subset of
 * {@code output_files}; originals stay in {@code output_files}).
 */
class AiAgentNodeTest {

    @Test
    void audioKindGoesToVoices() {
        Map<String, Object> audio = Map.of(
                "kind", "audio", "mime_type", "audio/mpeg", "source", "base64",
                "data", "xx", "name", "a.mp3");
        Map<String, Object> body = bodyOf(AiAgentNode.mainOutput("ok", List.of(audio)));
        assertEquals(List.of(audio), body.get("output_files"));
        assertEquals(List.of(audio), body.get("voices"));
        assertSame(audio, ((List<?>) body.get("voices")).get(0));
    }

    @Test
    void typeAudioGoesToVoices() {
        Map<String, Object> audio = Map.of(
                "type", "audio", "mime_type", "audio/mpeg", "source", "base64",
                "data", "xx", "name", "a.mp3");
        Map<String, Object> body = bodyOf(AiAgentNode.mainOutput("ok", List.of(audio)));
        assertEquals(List.of(audio), body.get("output_files"));
        assertEquals(List.of(audio), body.get("voices"));
    }

    @Test
    void mimeTypeAudioWithoutKindGoesToVoices() {
        Map<String, Object> audio = Map.of(
                "mime_type", "audio/wav", "source", "base64", "data", "xx", "name", "a.wav");
        Map<String, Object> body = bodyOf(AiAgentNode.mainOutput("ok", List.of(audio)));
        assertEquals(List.of(audio), body.get("output_files"));
        assertEquals(List.of(audio), body.get("voices"));
    }

    @Test
    void imageOnlyLeavesVoicesEmpty() {
        Map<String, Object> image = Map.of(
                "kind", "image", "mime_type", "image/png", "source", "base64",
                "data", "xx", "name", "a.png");
        Map<String, Object> body = bodyOf(AiAgentNode.mainOutput("ok", List.of(image)));
        assertEquals(List.of(image), body.get("output_files"));
        assertEquals(List.of(), body.get("voices"));
    }

    @Test
    void mixedImageAndAudioSplitsVoices() {
        Map<String, Object> image = Map.of(
                "kind", "image", "mime_type", "image/png", "source", "base64",
                "data", "xx", "name", "a.png");
        Map<String, Object> audio = Map.of(
                "kind", "audio", "mime_type", "audio/wav", "source", "base64",
                "data", "yy", "name", "a.wav");
        Map<String, Object> body = bodyOf(AiAgentNode.mainOutput("ok", List.of(image, audio)));
        assertEquals(List.of(image, audio), body.get("output_files"));
        assertEquals(List.of(audio), body.get("voices"));
        assertSame(audio, ((List<?>) body.get("voices")).get(0));
    }

    @Test
    void voicesOnlyBlankTextStillPublishesVoices() {
        Map<String, Object> audio = Map.of(
                "kind", "audio", "mime_type", "audio/wav", "source", "base64",
                "data", "xx", "name", "a.wav");
        Map<String, Object> blank = bodyOf(AiAgentNode.mainOutput("  ", List.of(audio)));
        assertEquals("  ", blank.get("final_answer"));
        assertEquals(List.of(audio), blank.get("voices"));
        assertEquals(List.of(audio), blank.get("output_files"));
        Map<String, Object> empty = bodyOf(AiAgentNode.mainOutput("", List.of(audio)));
        assertEquals("", empty.get("final_answer"));
        assertEquals(List.of(audio), empty.get("voices"));
    }

    @Test
    void errorPathEmitsEmptyVoices() {
        Map<String, Object> ports = new AiAgentNode().handle(Map.of(), null);
        Map<String, Object> body = bodyOf(ports);
        assertTrue(String.valueOf(body.get("final_answer")).contains("helper is required"));
        assertEquals(List.of(), body.get("output_files"));
        assertEquals(List.of(), body.get("voices"));
    }

    @Test
    void nullOutputFilesYieldsEmptyLists() {
        Map<String, Object> body = bodyOf(AiAgentNode.mainOutput("hi", null));
        assertEquals("hi", body.get("final_answer"));
        assertEquals(List.of(), body.get("output_files"));
        assertEquals(List.of(), body.get("voices"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bodyOf(Map<String, Object> ports) {
        return (Map<String, Object>) ports.get("main_output");
    }
}
