package com.studioquack.nodelibrary.nodes.localllm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** LocalLlmInput: llm_input port reads, JSON-vs-bare-text normalization, payload accessors. */
class LocalLlmInputTest {

    // ---------- read ----------

    @Test
    void readNullRequestReturnsNull() {
        assertNull(LocalLlmInput.read(null, null));
        assertNull(LocalLlmInput.read(Map.of(), null));
        assertNull(LocalLlmInput.read(Map.of("inputVariables", Map.of()), null));
        assertNull(LocalLlmInput.read(Map.of("inputVariables", "not-a-map"), null));
    }

    @Test
    void readWrapsBareTextAsUserMessage() {
        Object out = LocalLlmInput.read(
                Map.of("inputVariables", Map.of("llm_input", "hello")), null);
        assertEquals(Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))), out);
    }

    @Test
    void readParsesJsonStringPayload() {
        Object out = LocalLlmInput.read(
                Map.of("inputVariables", Map.of("llm_input", "{\"prompt\":\"hi\"}")), null);
        assertEquals(Map.of("prompt", "hi"), out);
    }

    @Test
    void readUnwrapsSingleMapList() {
        Map<String, Object> inner = Map.of("prompt", "x");
        Object out = LocalLlmInput.read(
                Map.of("inputVariables", Map.of("llm_input", List.of(inner))), null);
        assertEquals(inner, out);
    }

    // ---------- normalize ----------

    @Test
    void normalizeNullAndEmptyInputs() {
        assertNull(LocalLlmInput.normalize(null));
        assertNull(LocalLlmInput.normalize(List.of()));
        assertNull(LocalLlmInput.normalize("   "));
    }

    @Test
    void normalizeListShapes() {
        Map<String, Object> m = Map.of("prompt", "x");
        assertEquals(m, LocalLlmInput.normalize(List.of(m)));
        List<Object> two = List.of(Map.of("role", "user", "content", "a"), Map.of("role", "user", "content", "b"));
        assertSame(two, LocalLlmInput.normalize(two));
        List<Object> singleNonMap = List.of("x");
        assertSame(singleNonMap, LocalLlmInput.normalize(singleNonMap));
    }

    @Test
    void normalizeStringPayloads() {
        assertEquals(Map.of("prompt", "hi"), LocalLlmInput.normalize("{\"prompt\":\"hi\"}"));
        assertEquals(List.of(1, 2), LocalLlmInput.normalize("[1,2]"));
        assertEquals(42, LocalLlmInput.normalize("42"));
        assertEquals(Map.of("messages", List.of(Map.of("role", "user", "content", "plain text"))),
                LocalLlmInput.normalize("plain text"));
    }

    @Test
    void normalizePassesOtherTypesThrough() {
        assertEquals(5, LocalLlmInput.normalize(5));
        Map<String, Object> m = Map.of("k", "v");
        assertSame(m, LocalLlmInput.normalize(m));
    }

    // ---------- accessors ----------

    @Test
    void messagesOfReadsOnlyMaps() {
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "x"));
        assertEquals(messages, LocalLlmInput.messagesOf(Map.of("messages", messages)));
        assertNull(LocalLlmInput.messagesOf(Map.of()));
        assertNull(LocalLlmInput.messagesOf("x"));
        assertNull(LocalLlmInput.messagesOf(null));
    }

    @Test
    void promptOfFallsBackToWholePayload() {
        assertEquals("p", LocalLlmInput.promptOf(Map.of("prompt", "p")));
        assertNull(LocalLlmInput.promptOf(Map.of()));
        assertEquals("raw", LocalLlmInput.promptOf("raw"));
        assertEquals(42, LocalLlmInput.promptOf(42));
    }

    @Test
    void toolsOfReadsOnlyMaps() {
        List<Object> tools = List.of(Map.of("name", "t"));
        assertEquals(tools, LocalLlmInput.toolsOf(Map.of("tools", tools)));
        assertNull(LocalLlmInput.toolsOf(Map.of()));
        assertNull(LocalLlmInput.toolsOf("x"));
        assertNull(LocalLlmInput.toolsOf(null));
    }

    @Test
    void inputFilesOfReadsCamelAndSnake() {
        List<Object> files = List.of(Map.of("kind", "image", "name", "p.png"));
        assertEquals(files, LocalLlmInput.inputFilesOf(Map.of("inputFiles", files)));
        assertEquals(files, LocalLlmInput.inputFilesOf(Map.of("input_files", files)));
        assertEquals(files, LocalLlmInput.inputFilesOf(
                Map.of("inputFiles", files, "input_files", List.of())));
        assertNull(LocalLlmInput.inputFilesOf(Map.of()));
        assertNull(LocalLlmInput.inputFilesOf("x"));
        assertNull(LocalLlmInput.inputFilesOf(null));
    }
}
