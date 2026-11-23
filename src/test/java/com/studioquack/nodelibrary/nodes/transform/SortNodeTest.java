package com.studioquack.nodelibrary.nodes.transform;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sort node: keyed sort, natural sort, CSPRNG shuffle, code refusal. */
class SortNodeTest {

    private final SortNode node = new SortNode();

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
    void sortsByKeyAscending() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "sort"),
                Map.of("items", List.of(
                        Map.of("name", "c"), Map.of("name", "a"), Map.of("name", "b")),
                        "keys", List.of(Map.of("path", "name", "direction", "asc"))));
        assertEquals(List.of(Map.of("name", "a"), Map.of("name", "b"), Map.of("name", "c")),
                dataOf(node.handle(request, null)));
    }

    @Test
    void sortsByNumericKeyDescendingFromConfig() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "sort",
                        "keys", "[{\"path\":\"age\",\"direction\":\"desc\"}]"),
                Map.of("items", List.of(
                        Map.of("age", 30), Map.of("age", 25), Map.of("age", 40))));
        assertEquals(List.of(Map.of("age", 40), Map.of("age", 30), Map.of("age", 25)),
                dataOf(node.handle(request, null)));
    }

    @Test
    void noKeysFallsBackToNaturalOrder() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "sort"),
                Map.of("items", List.of(3, 1, 2)));
        assertEquals(List.of(1, 2, 3), dataOf(node.handle(request, null)));
    }

    @Test
    void randomKeepsTheSameMultiset() {
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "sort", "type", "random"),
                Map.of("items", items));
        Object data = dataOf(node.handle(request, null));
        assertTrue(data instanceof List, "random sort returns a list: " + data);
        assertEquals(new TreeSet<>(items), new TreeSet<>((List<?>) data));
    }

    @Test
    void codeTypeIsRefusedWithoutEval() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "sort", "type", "code"),
                Map.of("items", List.of(1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("entitlement"));
    }

    @Test
    void unknownTypeIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "sort", "type", "bogus"),
                Map.of("items", List.of(1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown type"));
    }

    @Test
    void unwrapsSingleElementListOfJsonArrayString() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "sort"),
                Map.of("items", List.of("[3, 1, 2]")));
        assertEquals(List.of(1, 2, 3), dataOf(node.handle(request, null)));
    }

    @Test
    void nestedArgsItemsAreSorted() {
        Map<String, Object> request = requestWithConfig("operation", "sort");
        request.put("inputVariables", Map.of("input",
                Map.of("arguments", Map.of("items", List.of(10, 20, 3, 7, 1)))));
        assertEquals(List.of(1, 3, 7, 10, 20), dataOf(node.handle(request, null)));
    }

    @Test
    void plainStringItemsIsInvalidInput() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "sort"),
                Map.of("items", "banana apple cherry"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("must be a JSON array"));
    }

    @Test
    void missingItemsIsAnError() {
        Map<String, Object> request = withInput(requestWithConfig("operation", "sort"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("items"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "shuffle"),
                Map.of("items", List.of(1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
