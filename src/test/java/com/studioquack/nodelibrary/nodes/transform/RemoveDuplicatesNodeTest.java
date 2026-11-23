package com.studioquack.nodelibrary.nodes.transform;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Remove Duplicates node: batch-unique and bounded history. */
class RemoveDuplicatesNodeTest {

    private final RemoveDuplicatesNode node = new RemoveDuplicatesNode();

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
    void uniqueWholeItemKeepsFirstOccurrence() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "unique"),
                Map.of("items", List.of(1, 2, 1, 3, 2)));
        assertEquals(List.of(1, 2, 3), dataOf(node.handle(request, null)));
    }

    @Test
    void uniqueByKeysIgnoresOtherFields() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "unique", "keys", "[\"id\"]"),
                Map.of("items", List.of(
                        Map.of("id", 1, "v", "x"),
                        Map.of("id", 1, "v", "y"),
                        Map.of("id", 2, "v", "z"))));
        assertEquals(List.of(Map.of("id", 1, "v", "x"), Map.of("id", 2, "v", "z")),
                dataOf(node.handle(request, null)));
    }

    @Test
    void inputKeysOverrideConfig() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "unique", "keys", "[\"id\"]"),
                Map.of("keys", List.of("v"),
                        "items", List.of(Map.of("id", 1, "v", "x"), Map.of("id", 2, "v", "x"))));
        assertEquals(List.of(Map.of("id", 1, "v", "x")), dataOf(node.handle(request, null)));
    }

    @Test
    void uniqueHistoryDropsItemsSeenInEarlierInvokes() {
        // unique scope per test run: history is process-wide by design
        String scope = "test-" + UUID.randomUUID();
        Map<String, Object> first = withInput(
                requestWithConfig("operation", "unique_history", "history_scope", scope),
                Map.of("items", List.of(1, 2)));
        assertEquals(List.of(1, 2), dataOf(node.handle(first, null)));

        Map<String, Object> second = withInput(
                requestWithConfig("operation", "unique_history", "history_scope", scope),
                Map.of("items", List.of(2, 3)));
        assertEquals(List.of(3), dataOf(node.handle(second, null)));
    }

    @Test
    void missingItemsIsAnError() {
        Map<String, Object> request = withInput(requestWithConfig("operation", "unique"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("items"));
    }

    @Test
    void keysOnStringItemsDedupesByWholeValue() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "unique"),
                Map.of("items", List.of("x", "y", "x", "z", "y"), "keys", List.of("id")));
        assertEquals(List.of("x", "y", "z"), dataOf(node.handle(request, null)));
    }

    @Test
    void itemsAsPlainStringIsInvalid() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "unique"),
                Map.of("items", "apple"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("must be a JSON array"));
    }

    @Test
    void keysAsPlainStringIsInvalid() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "unique"),
                Map.of("items", List.of("a", "b", "a"), "keys", "id"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("keys must be a JSON array"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "dedupe"),
                Map.of("items", List.of(1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
