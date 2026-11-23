package com.studioquack.nodelibrary.nodes.transform;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Limit node: first/last slices. */
class LimitNodeTest {

    private final LimitNode node = new LimitNode();

    private static Map<String, Object> requestWithConfig(Object... shortCodeThenValue) {
        List<Object> params = new ArrayList<>();
        for (int i = 0; i + 1 < shortCodeThenValue.length; i += 2) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", shortCodeThenValue[i]);
            row.put("value", shortCodeThenValue[i + 1]);
            params.add(row);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        return request;
    }

    private static Map<String, Object> withInput(Map<String, Object> request, Object inputValue) {
        request.put("inputVariables", Map.of("input", inputValue));
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> ports) {
        return (Map<String, Object>) ports.get("output");
    }

    private static StudioQuackError thrown(java.util.function.Supplier<Map<String, Object>> call) {
        return assertThrows(StudioQuackError.class, call::get);
    }

    private static Object dataOf(Map<String, Object> ports) {
        assertEquals("ok", output(ports).get("status"), "expected ok: " + ports);
        return output(ports).get("data");
    }

    @Test
    void firstKeepsLeadingN() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "first", "max_items", "2"),
                Map.of("items", List.of(1, 2, 3, 4)));
        assertEquals(List.of(1, 2), dataOf(node.handle(request, null)));
    }

    @Test
    void lastKeepsTrailingN() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "last", "max_items", "2"),
                Map.of("items", List.of(1, 2, 3, 4)));
        assertEquals(List.of(3, 4), dataOf(node.handle(request, null)));
    }

    @Test
    void inputMaxItemsOverridesConfig() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "first", "max_items", "1"),
                Map.of("items", List.of(1, 2, 3), "max_items", 2));
        assertEquals(List.of(1, 2), dataOf(node.handle(request, null)));
    }

    @Test
    void defaultIsOneItem() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "first"),
                Map.of("items", List.of(9, 8, 7)));
        assertEquals(List.of(9), dataOf(node.handle(request, null)));
    }

    @Test
    void nLargerThanListReturnsWholeList() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "last", "max_items", "99999"),
                Map.of("items", List.of(1, 2)));
        assertEquals(List.of(1, 2), dataOf(node.handle(request, null)));
    }

    @Test
    void missingItemsIsAnError() {
        Map<String, Object> request = withInput(requestWithConfig("operation", "first"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("items"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "middle"),
                Map.of("items", List.of(1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }

    @Test
    void maxItemsZeroReturnsEmptySlice() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "first"),
                Map.of("items", List.of("a", "b"), "max_items", 0));
        assertEquals(List.of(), dataOf(node.handle(request, null)));
    }

    @Test
    void negativeMaxItemsIsInvalid() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "first"),
                Map.of("items", List.of("a"), "max_items", -1));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("max_items"));
    }

    @Test
    void itemsAsPlainStringIsInvalid() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "first"),
                Map.of("items", "abc", "max_items", 1));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("items"));
    }
}
