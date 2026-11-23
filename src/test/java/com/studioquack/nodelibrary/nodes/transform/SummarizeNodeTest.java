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

/** Summarize node: per-field aggregations. */
class SummarizeNodeTest {

    private final SummarizeNode node = new SummarizeNode();

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
    void numericAggregationsOverAField() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize",
                        "fields", "[\"amount\"]",
                        "aggregations", "[\"sum\",\"count\",\"avg\",\"min\",\"max\"]"),
                Map.of("items", List.of(
                        Map.of("amount", 1), Map.of("amount", 2), Map.of("amount", 3))));
        Map<String, Object> data = dataOf(node.handle(request, null));
        @SuppressWarnings("unchecked")
        Map<String, Object> amount = (Map<String, Object>) data.get("amount");
        assertEquals(6L, amount.get("sum"));
        assertEquals(3L, amount.get("count"));
        assertEquals(2L, amount.get("avg"));
        assertEquals(1L, amount.get("min"));
        assertEquals(3L, amount.get("max"));
    }

    @Test
    void nonIntegralAvgStaysADouble() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize",
                        "fields", "[\"v\"]", "aggregations", "[\"avg\"]"),
                Map.of("items", List.of(Map.of("v", 1), Map.of("v", 2))));
        @SuppressWarnings("unchecked")
        Map<String, Object> v = (Map<String, Object>) dataOf(node.handle(request, null)).get("v");
        assertEquals(1.5, v.get("avg"));
    }

    @Test
    void concatJoinsStringForms() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize",
                        "fields", "[\"n\"]", "aggregations", "[\"concat\"]"),
                Map.of("items", List.of(Map.of("n", "a"), Map.of("n", "b"))));
        @SuppressWarnings("unchecked")
        Map<String, Object> n = (Map<String, Object>) dataOf(node.handle(request, null)).get("n");
        assertEquals("a,b", n.get("concat"));
    }

    @Test
    void noFieldsDefaultsToWholeBatchCount() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize"),
                Map.of("items", List.of(1, 2, 3)));
        assertEquals(Map.of("count", 3L), dataOf(node.handle(request, null)));
    }

    @Test
    void noFieldsWithSumIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize", "aggregations", "[\"sum\"]"),
                Map.of("items", List.of(1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("fields"));
    }

    @Test
    void unknownAggregationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize",
                        "fields", "[\"v\"]", "aggregations", "[\"median\"]"),
                Map.of("items", List.of(Map.of("v", 1))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown aggregation"));
    }

    @Test
    void missingItemsIsAnError() {
        Map<String, Object> request = withInput(requestWithConfig("operation", "summarize"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("items"));
    }

    @Test
    void sumPlainNumericStringsWithSingleField() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize",
                        "fields", "[\"a\"]", "aggregations", "[\"sum\"]"),
                Map.of("items", List.of("2", "2")));
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) dataOf(node.handle(request, null)).get("a");
        assertEquals(4L, a.get("sum"));
    }

    @Test
    void csvRowsWithHeader() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize",
                        "fields", "[\"a\",\"b\"]", "aggregations", "[\"sum\"]"),
                Map.of("items", List.of("a,b", "2,2", "3,4")));
        Map<String, Object> data = dataOf(node.handle(request, null));
        assertEquals(5L, ((Map<String, Object>) data.get("a")).get("sum"));
        assertEquals(6L, ((Map<String, Object>) data.get("b")).get("sum"));
    }

    @Test
    void fieldEqualsSyntax() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize",
                        "fields", "[\"a\"]", "aggregations", "[\"sum\"]"),
                Map.of("items", List.of("a=2", "a=2")));
        assertEquals(4L, ((Map<String, Object>) dataOf(node.handle(request, null)).get("a")).get("sum"));
    }

    @Test
    void concatOnPlainStringsWithSingleField() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize",
                        "fields", "[\"a\"]", "aggregations", "[\"concat\"]"),
                Map.of("items", List.of("alpha", "beta", "gamma")));
        assertEquals("alpha,beta,gamma",
                ((Map<String, Object>) dataOf(node.handle(request, null)).get("a")).get("concat"));
    }

    @Test
    void minMaxAvgOnPlainNumbers() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "summarize",
                        "fields", "[\"a\"]", "aggregations", "[\"min\",\"max\",\"avg\"]"),
                Map.of("items", List.of("5", "1", "9")));
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) dataOf(node.handle(request, null)).get("a");
        assertEquals(1L, a.get("min"));
        assertEquals(9L, a.get("max"));
        assertEquals(5L, a.get("avg"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rollup"),
                Map.of("items", List.of(1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
