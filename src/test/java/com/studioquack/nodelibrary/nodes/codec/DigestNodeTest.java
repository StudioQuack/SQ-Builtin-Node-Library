package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Digest buffer: buffer/flush lifecycle against a @TempDir store, the
 * max_items hard cap, window parsing, and durability across node instances
 * (file-backed, not JVM memory).
 */
class DigestNodeTest {

    @TempDir
    Path store;

    private DigestNode node() {
        return new DigestNode(DigestNode.DEFAULT_ID, store);
    }

    private Map<String, Object> buffer(DigestNode node, Object item) {
        return CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "buffer", "window", "PT15M"),
                CodecTestSupport.input("item", item)));
    }

    @Test
    void bufferThenFlushEmitsAndClears() {
        DigestNode node = node();
        Map<String, Object> b1 = buffer(node, Map.of("event", "a"));
        assertTrue(CodecTestSupport.isOk(b1), () -> CodecTestSupport.error(b1));
        assertEquals(1, CodecTestSupport.data(b1).get("size"));
        Map<String, Object> b2 = buffer(node, Map.of("event", "b"));
        assertEquals(2, CodecTestSupport.data(b2).get("size"));

        Map<String, Object> flush = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "flush", "window", "PT15M"),
                CodecTestSupport.input()));
        assertTrue(CodecTestSupport.isOk(flush), () -> CodecTestSupport.error(flush));
        assertEquals(2, CodecTestSupport.data(flush).get("count"));
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) CodecTestSupport.data(flush).get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) items.get(0);
        assertEquals("a", first.get("event"));
        assertNotNull(CodecTestSupport.data(flush).get("window_start"));

        // second flush is empty — the store was cleared
        Map<String, Object> again = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "flush", "window", "PT15M"),
                CodecTestSupport.input()));
        assertEquals(0, CodecTestSupport.data(again).get("count"));
    }

    @Test
    void bufferSurvivesANewNodeInstance() {
        buffer(node(), Map.of("v", "persisted"));
        // a fresh handler over the same store dir sees the item (not JVM memory)
        Map<String, Object> flush = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "flush", "window", "PT15M"),
                CodecTestSupport.input()));
        assertEquals(1, CodecTestSupport.data(flush).get("count"));
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) ((List<?>) CodecTestSupport.data(flush).get("items")).get(0);
        assertEquals("persisted", item.get("v"));
    }

    @Test
    void maxItemsHardCap() {
        DigestNode node = node();
        Map<String, Object> cfg = CodecTestSupport.cfg("operation", "buffer", "max_items", "2");
        assertTrue(CodecTestSupport.isOk(CodecTestSupport.run(node, CodecTestSupport.request(cfg,
                CodecTestSupport.input("item", Map.of("n", 1))))));
        assertTrue(CodecTestSupport.isOk(CodecTestSupport.run(node, CodecTestSupport.request(cfg,
                CodecTestSupport.input("item", Map.of("n", 2))))));
        Map<String, Object> third = CodecTestSupport.run(node, CodecTestSupport.request(cfg,
                CodecTestSupport.input("item", Map.of("n", 3))));
        String error = CodecTestSupport.error(third);
        assertNotNull(error);
        assertTrue(error.contains("max_items"));
    }

    @Test
    void windowAcceptsMinutesInteger() {
        Map<String, Object> result = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "buffer", "window", "30"),
                CodecTestSupport.input("item", Map.of("k", "x"))));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals(1800L, CodecTestSupport.data(result).get("window_seconds"));
    }

    @Test
    void invalidWindowFails() {
        Map<String, Object> result = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "buffer", "window", "whenever"),
                CodecTestSupport.input("item", Map.of("k", "x"))));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void bufferRejectsPlainStringItem() {
        Map<String, Object> result = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "buffer", "window", "PT5M"),
                CodecTestSupport.input("item", "just a plain string, not an object")));
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(error.contains("item must be a JSON object"));
    }

    @Test
    void windowMaxThirtyDays() {
        Map<String, Object> result = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "buffer", "window", "999999"),
                CodecTestSupport.input("item", Map.of("id", "test-big"))));
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(error.contains("30 days"));
    }

    @Test
    void invalidTimezoneFails() {
        Map<String, Object> result = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "flush", "timezone", "Not/AZone"),
                CodecTestSupport.input()));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void bufferRequiresItem() {
        Map<String, Object> result = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "buffer"),
                CodecTestSupport.input()));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void unknownOperationFails() {
        Map<String, Object> result = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "purge"),
                CodecTestSupport.input()));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void inputWindowOverridesConfig() {
        DigestNode node = node();
        buffer(node, Map.of("k", "config-window"));
        Map<String, Object> flush = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "flush", "window", "PT15M"),
                CodecTestSupport.input("window", "PT1H")));
        // different window → different buffer → empty
        assertEquals(0, CodecTestSupport.data(flush).get("count"));
    }
}
