package com.studioquack.nodelibrary.nodes.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** openai.NodeParams: configurationParameters digging, map/list coercion, tri-state boolish, number parsing. */
class NodeParamsTest {

    private static Map<String, Object> requestWithParams(List<Object> params) {
        return Map.of("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
    }

    @Test
    void readNodeParamsReadsShortCodeValuesInOrder() {
        Map<String, Object> out = NodeParams.readNodeParams(requestWithParams(List.of(
                Map.of("shortCode", "b_key", "value", 1),
                Map.of("shortCode", "a_key", "value", 2))));
        assertEquals(List.of("b_key", "a_key"), new ArrayList<>(out.keySet()));
    }

    @Test
    void readNodeParamsSkipsBlankNullAndNonMapEntries() {
        Map<String, Object> out = NodeParams.readNodeParams(requestWithParams(List.of(
                Map.of("shortCode", " ", "value", "x"),
                Map.of("shortCode", "novalue"),
                "junk",
                42,
                Map.of("shortCode", "ok", "value", "v"))));
        assertEquals(Map.of("ok", "v"), out);
    }

    @Test
    void readNodeParamsDuplicateShortCodeLastWins() {
        Map<String, Object> out = NodeParams.readNodeParams(requestWithParams(List.of(
                Map.of("shortCode", "k", "value", 1),
                Map.of("shortCode", "k", "value", 2))));
        assertEquals(Map.of("k", 2), out);
    }

    @Test
    void readNodeParamsNullOrMissingPathReturnsEmpty() {
        assertTrue(NodeParams.readNodeParams(null).isEmpty());
        assertTrue(NodeParams.readNodeParams(Map.of()).isEmpty());
        assertTrue(NodeParams.readNodeParams(Map.of("executionContext", Map.of())).isEmpty());
        assertTrue(NodeParams.readNodeParams(requestWithParams(List.of())).isEmpty());
        assertTrue(NodeParams.readNodeParams(
                Map.of("executionContext", Map.of("currentNode",
                        Map.of("data", Map.of("configurationParameters", "not-a-list"))))).isEmpty());
    }

    @Test
    void asMapCastsOrReturnsEmpty() {
        Map<String, Object> m = Map.of("a", 1);
        assertSame(m, NodeParams.asMap(m));
        assertTrue(NodeParams.asMap("x").isEmpty());
        assertTrue(NodeParams.asMap(null).isEmpty());
    }

    @Test
    void mutableMapIsAMutableCopy() {
        Map<String, Object> src = Map.of("a", 1);
        Map<String, Object> copy = NodeParams.mutableMap(src);
        copy.put("b", 2);
        assertEquals(Map.of("a", 1, "b", 2), copy);
        assertEquals(Map.of("a", 1), src);
    }

    @Test
    void asListCopiesOrReturnsEmpty() {
        List<Object> out = NodeParams.asList(List.of(1, 2));
        assertEquals(List.of(1, 2), out);
        out.add(3);
        assertEquals(3, out.size());
        assertTrue(NodeParams.asList("x").isEmpty());
        assertTrue(NodeParams.asList(null).isEmpty());
    }

    @Test
    void strTrimIsBlank() {
        assertEquals("", NodeParams.str(null));
        assertEquals("42", NodeParams.str(42));
        assertEquals("x", NodeParams.trim("  x  "));
        assertEquals("", NodeParams.trim(null));
        assertTrue(NodeParams.isBlank(null));
        assertTrue(NodeParams.isBlank("  "));
        assertFalse(NodeParams.isBlank("x"));
        assertFalse(NodeParams.isBlank(0));
    }

    @Test
    void digWalksNestedMaps() {
        Map<String, Object> root = Map.of("a", Map.of("b", Map.of("c", 42)));
        assertEquals(42, NodeParams.dig(root, "a", "b", "c"));
        assertSame(root, NodeParams.dig(root));
        assertNull(NodeParams.dig(root, "a", "missing", "c"));
        assertNull(NodeParams.dig(Map.of("a", "string"), "a", "b"));
        assertNull(NodeParams.dig(null, "a"));
    }

    @Test
    void toIntCoercions() {
        assertEquals(5, NodeParams.toInt(null, 5));
        assertEquals(5, NodeParams.toInt("abc", 5));
        assertEquals(42, NodeParams.toInt("42", 5));
        assertNull(NodeParams.toIntOrNull(null));
        assertNull(NodeParams.toIntOrNull(Boolean.TRUE));
        assertNull(NodeParams.toIntOrNull(""));
        assertNull(NodeParams.toIntOrNull("abc"));
        assertEquals(42, NodeParams.toIntOrNull(42));
        assertEquals(42, NodeParams.toIntOrNull("42"));
        assertEquals(42, NodeParams.toIntOrNull("42.9"));
        assertEquals(7, NodeParams.toIntOrNull(7L));
    }

    @Test
    void toDoubleOrNullCoercions() {
        assertNull(NodeParams.toDoubleOrNull(null));
        assertNull(NodeParams.toDoubleOrNull(Boolean.FALSE));
        assertNull(NodeParams.toDoubleOrNull(" "));
        assertNull(NodeParams.toDoubleOrNull("abc"));
        assertEquals(1.5d, NodeParams.toDoubleOrNull("1.5"), 1e-9);
        assertEquals(2.0d, NodeParams.toDoubleOrNull(2), 1e-9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "TRUE", " yes ", "on", "stream", "streaming"})
    void boolishTrue(String s) {
        assertEquals(Boolean.TRUE, NodeParams.boolish(s));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "false", "no", "off", "nonstream", "non-stream", "sync"})
    void boolishFalse(String s) {
        assertEquals(Boolean.FALSE, NodeParams.boolish(s));
    }

    @Test
    void boolishUnknownIsNull() {
        assertNull(NodeParams.boolish(null));
        assertNull(NodeParams.boolish(""));
        assertNull(NodeParams.boolish("   "));
        assertNull(NodeParams.boolish("garbage"));
        assertEquals(Boolean.TRUE, NodeParams.boolish(Boolean.TRUE));
        assertEquals(Boolean.FALSE, NodeParams.boolish(Boolean.FALSE));
    }

    @Test
    void parseJsonNeverThrows() {
        assertEquals(Map.of("a", 1), NodeParams.parseJson("{\"a\":1}"));
        assertEquals(List.of(1, 2), NodeParams.parseJson("[1,2]"));
        assertEquals(42, NodeParams.parseJson("42"));
        assertNull(NodeParams.parseJson("bad"));
        assertNull(NodeParams.parseJson(null));
        assertNull(NodeParams.parseJson("  "));
    }

    @Test
    void uuidHelpersProduceHexOfFixedLength() {
        String u24 = NodeParams.uuid24();
        assertEquals(24, u24.length());
        assertTrue(u24.matches("[0-9a-f]{24}"));
        String u8 = NodeParams.uuid8();
        assertEquals(8, u8.length());
        assertTrue(u8.matches("[0-9a-f]{8}"));
    }

    @Test
    void firstNonNullPicksFirst() {
        assertEquals("a", NodeParams.firstNonNull(null, "a", "b"));
        assertNull(NodeParams.firstNonNull(null, null));
        assertNull(NodeParams.firstNonNull((Object[]) null));
        assertEquals(1, NodeParams.firstNonNull(1, "a"));
    }
}
