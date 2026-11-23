package com.studioquack.nodelibrary.nodes.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ai.NodeParams: instance-param precedence, input reads, scalar coercions. Helper is always null (pure logic). */
class NodeParamsTest {

    private static Map<String, Object> requestWithParams(List<Object> params) {
        return Map.of("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
    }

    @Test
    void instanceParamsReadsShortCodeValues() {
        Map<String, Object> out = NodeParams.instanceParams(requestWithParams(List.of(
                Map.of("shortCode", "model", "value", "gpt-4o"),
                Map.of("shortCode", "temperature", "value", "0.7"))));
        assertEquals("gpt-4o", out.get("model"));
        assertEquals("0.7", out.get("temperature"));
        assertEquals(2, out.size());
    }

    @Test
    void instanceParamsSkipsBlankShortCodeNullValueAndNonMapItems() {
        Map<String, Object> out = NodeParams.instanceParams(requestWithParams(List.of(
                Map.of("shortCode", " ", "value", "x"),
                Map.of("shortCode", "novalue"),
                "junk",
                Map.of("shortCode", "ok", "value", 1))));
        assertEquals(Map.of("ok", 1), out);
    }

    @Test
    void instanceParamsNullOrMissingPathReturnsEmpty() {
        assertTrue(NodeParams.instanceParams(null).isEmpty());
        assertTrue(NodeParams.instanceParams(Map.of()).isEmpty());
        assertTrue(NodeParams.instanceParams(Map.of("executionContext", "not-a-map")).isEmpty());
    }

    @Test
    void paramPrefersInstanceValue() {
        assertEquals("v", NodeParams.param(Map.of("k", "v"), null, "k", "fb"));
        assertEquals(0, NodeParams.param(Map.of("k", 0), null, "k", "fb"));
    }

    @Test
    void paramFallsBackOnBlankStringNullOrMissingKey() {
        assertEquals("fb", NodeParams.param(Map.of("k", "  "), null, "k", "fb"));
        assertEquals("fb", NodeParams.param(Map.of("k", "ignored"), null, "other", "fb"));
        assertEquals("fb", NodeParams.param(null, null, "k", "fb"));
    }

    @Test
    void paramStrStringifiesAndFallsBack() {
        assertEquals("42", NodeParams.paramStr(Map.of("k", 42), null, "k", "fb"));
        assertEquals("fb", NodeParams.paramStr(Map.of(), null, "k", "fb"));
        assertNull(NodeParams.paramStr(Map.of(), null, "k", null));
    }

    @Test
    void readInputReadsInputVariablesPort() {
        Map<String, Object> request = Map.of("inputVariables", Map.of("main_input", "hello"));
        assertEquals("hello", NodeParams.readInput(request, null, "main_input"));
    }

    @Test
    void readInputUnwrapsSingleElementListAndParsesJson() {
        Map<String, Object> listWrap = Map.of("inputVariables", Map.of("main_input", List.of("a", "b")));
        assertEquals("a", NodeParams.readInput(listWrap, null, "main_input"));

        Map<String, Object> json = Map.of("inputVariables", Map.of("main_input", "{\"k\":1}"));
        assertEquals(Map.of("k", 1), NodeParams.readInput(json, null, "main_input"));
    }

    @Test
    void readInputMissingReturnsNull() {
        assertNull(NodeParams.readInput(null, null, "main_input"));
        assertNull(NodeParams.readInput(Map.of(), null, "main_input"));
        assertNull(NodeParams.readInput(Map.of("inputVariables", "not-a-map"), null, "main_input"));
        assertNull(NodeParams.readInput(Map.of("inputVariables", Map.of("other", 1)), null, "main_input"));
    }

    @Test
    void unwrapReturnsFirstListElement() {
        assertEquals("a", NodeParams.unwrap(List.of("a", "b")));
        assertEquals(List.of(), NodeParams.unwrap(List.of()));
    }

    @Test
    void unwrapParsesWrappedJsonKeepsMalformed() {
        assertEquals(Map.of("k", 1), NodeParams.unwrap("  {\"k\":1}  "));
        assertEquals(List.of(1, 2), NodeParams.unwrap("[1,2]"));
        assertEquals("{bad", NodeParams.unwrap("{bad"));
        assertEquals("plain", NodeParams.unwrap("plain"));
        assertEquals(42, NodeParams.unwrap(42));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "TRUE", " yes ", "on", "stream", "streaming"})
    void boolishTrueValues(String s) {
        assertTrue(NodeParams.boolish(s));
    }

    @Test
    void boolishBooleanAndNumberAndFalseValues() {
        assertTrue(NodeParams.boolish(Boolean.TRUE));
        assertTrue(NodeParams.boolish(1));
        assertFalse(NodeParams.boolish(Boolean.FALSE));
        assertFalse(NodeParams.boolish(null));
        assertFalse(NodeParams.boolish("false"));
        assertFalse(NodeParams.boolish("0"));
        assertFalse(NodeParams.boolish("garbage"));
        assertFalse(NodeParams.boolish(""));
    }

    @Test
    void boolOrNullTriState() {
        assertNull(NodeParams.boolOrNull(null));
        assertNull(NodeParams.boolOrNull("  "));
        assertNull(NodeParams.boolOrNull("maybe"));
        assertTrue(NodeParams.boolOrNull("true"));
        assertTrue(NodeParams.boolOrNull(Boolean.TRUE));
        assertFalse(NodeParams.boolOrNull("0"));
        assertFalse(NodeParams.boolOrNull("no"));
        assertFalse(NodeParams.boolOrNull("off"));
        assertFalse(NodeParams.boolOrNull("nonstream"));
        assertFalse(NodeParams.boolOrNull("non-stream"));
        assertFalse(NodeParams.boolOrNull("sync"));
        assertFalse(NodeParams.boolOrNull(Boolean.FALSE));
    }

    @Test
    void intOrNullCoercions() {
        assertNull(NodeParams.intOrNull(null));
        assertNull(NodeParams.intOrNull(Boolean.TRUE));
        assertNull(NodeParams.intOrNull(" "));
        assertNull(NodeParams.intOrNull("abc"));
        assertNull(NodeParams.intOrNull("4.5"));
        assertEquals(42, NodeParams.intOrNull(42));
        assertEquals(42, NodeParams.intOrNull("42"));
        assertEquals(42, NodeParams.intOrNull("42.0"));
        assertEquals(7, NodeParams.intOrNull(7L));
        assertEquals(3, NodeParams.intOrNull(3.9d));
    }

    @Test
    void floatOrNullCoercions() {
        assertNull(NodeParams.floatOrNull(null));
        assertNull(NodeParams.floatOrNull(Boolean.FALSE));
        assertNull(NodeParams.floatOrNull(" "));
        assertNull(NodeParams.floatOrNull("abc"));
        assertEquals(1.5d, NodeParams.floatOrNull("1.5"), 1e-9);
        assertEquals(2.0d, NodeParams.floatOrNull(2), 1e-9);
        assertEquals(-0.25d, NodeParams.floatOrNull(-0.25f), 1e-9);
    }

    @Test
    void jsonOrRawSemantics() {
        Map<String, Object> map = Map.of("a", 1);
        assertSame(map, NodeParams.jsonOrRaw(map, false));
        List<Object> list = List.of(1);
        assertSame(list, NodeParams.jsonOrRaw(list, true));
        assertNull(NodeParams.jsonOrRaw(null, true));
        assertEquals(Map.of("a", 1), NodeParams.jsonOrRaw("{\"a\":1}", false));
        assertEquals("raw", NodeParams.jsonOrRaw("raw", true));
        assertNull(NodeParams.jsonOrRaw("raw", false));
        assertEquals("", NodeParams.jsonOrRaw("", true));
        assertNull(NodeParams.jsonOrRaw("", false));
        assertEquals(5, NodeParams.jsonOrRaw(5, false));
    }
}
