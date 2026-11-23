package com.studioquack.nodelibrary.nodes.transform;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compare Datasets node: pairing, buckets, modes, fuzzy, skip_fields. */
class CompareDatasetsNodeTest {

    private final CompareDatasetsNode node = new CompareDatasetsNode();

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(Map<String, Object> ports) {
        assertEquals("ok", output(ports).get("status"), "expected ok: " + ports);
        return (Map<String, Object>) output(ports).get("data");
    }

    @Test
    void pairsOnMatchFieldsAndBucketsTheRest() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "compare", "match_fields", "[\"id\"]"),
                Map.of("a", List.of(Map.of("id", 1, "x", "a"), Map.of("id", 2)),
                        "b", List.of(Map.of("id", 1, "x", "a"), Map.of("id", 3))));
        Map<String, Object> data = dataOf(node.handle(request, null));
        assertEquals(List.of(Map.of("id", 1, "x", "a")), data.get("both"));
        assertEquals(List.of(Map.of("id", 2)), data.get("only_a"));
        assertEquals(List.of(Map.of("id", 3)), data.get("only_b"));
    }

    @Test
    void changedRowsReportBothSides() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "compare", "match_fields", "[\"id\"]"),
                Map.of("a", List.of(Map.of("id", 1, "x", "a")),
                        "b", List.of(Map.of("id", 1, "x", "b"))));
        Map<String, Object> data = dataOf(node.handle(request, null));
        assertEquals(List.of(), data.get("both"));
        assertEquals(List.of(Map.of("id", 1, "x", "a")), data.get("only_a"));
        assertEquals(List.of(Map.of("id", 1, "x", "b")), data.get("only_b"));
    }

    @Test
    void skipFieldsMakeChangedRowsEqual() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "compare", "match_fields", "[\"id\"]",
                        "skip_fields", "[\"x\"]"),
                Map.of("a", List.of(Map.of("id", 1, "x", "a")),
                        "b", List.of(Map.of("id", 1, "x", "b"))));
        assertEquals(1, ((List<?>) dataOf(node.handle(request, null)).get("both")).size());
    }

    @Test
    void fuzzyMatchesTrimmedCaseInsensitiveStrings() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "compare", "match_fields", "[\"id\"]", "fuzzy", "true"),
                Map.of("a", List.of(Map.of("id", 1, "x", "Hello")),
                        "b", List.of(Map.of("id", 1, "x", " hello "))));
        assertEquals(1, ((List<?>) dataOf(node.handle(request, null)).get("both")).size());
    }

    @Test
    void differenceModeSelectsBuckets() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "compare", "match_fields", "[\"id\"]",
                        "difference_mode", "only_a"),
                Map.of("a", List.of(Map.of("id", 1)), "b", List.of(Map.of("id", 2))));
        Map<String, Object> data = dataOf(node.handle(request, null));
        assertTrue(data.containsKey("only_a"));
        assertFalse(data.containsKey("only_b"));
        assertFalse(data.containsKey("both"));
    }

    @Test
    void noMatchFieldsUsesWholeItemIdentity() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "compare"),
                Map.of("a", List.of(Map.of("id", 1), Map.of("id", 2)),
                        "b", List.of(Map.of("id", 2))));
        Map<String, Object> data = dataOf(node.handle(request, null));
        assertEquals(List.of(Map.of("id", 2)), data.get("both"));
        assertEquals(List.of(Map.of("id", 1)), data.get("only_a"));
        assertEquals(List.of(), data.get("only_b"));
    }

    @Test
    void inputMatchFieldsOverrideConfig() {
        // config would pair on id (no pair: 1 vs 9); the input override pairs on x,
        // and skip_fields ignores the differing id so the pair counts as equal
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "compare", "match_fields", "[\"id\"]",
                        "skip_fields", "[\"id\"]"),
                Map.of("match_fields", List.of("x"),
                        "a", List.of(Map.of("id", 1, "x", "a")),
                        "b", List.of(Map.of("id", 9, "x", "a"))));
        Map<String, Object> data = dataOf(node.handle(request, null));
        assertEquals(1, ((List<?>) data.get("both")).size());
        assertEquals(List.of(), data.get("only_a"));
        assertEquals(List.of(), data.get("only_b"));
    }

    @Test
    void missingArraysIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "compare"), Map.of("a", List.of(1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("a and b"));
    }

    @Test
    void unknownDifferenceModeIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "compare", "difference_mode", "sideways"),
                Map.of("a", List.of(), "b", List.of()));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("difference_mode"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "diff"),
                Map.of("a", List.of(), "b", List.of()));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
