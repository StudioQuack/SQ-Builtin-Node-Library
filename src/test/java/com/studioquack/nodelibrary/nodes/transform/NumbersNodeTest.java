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

/** Numbers node: format / round / math / random_int. */
class NumbersNodeTest {

    private final NumbersNode node = new NumbersNode();

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
    private static Object valueOf(Map<String, Object> ports) {
        assertEquals("ok", output(ports).get("status"), "expected ok: " + ports);
        return ((Map<String, Object>) output(ports).get("data")).get("value");
    }

    @Test
    void formatRendersPrecisionDecimals() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "format", "precision", "2"),
                Map.of("value", 3.14159));
        assertEquals("3.14", valueOf(node.handle(request, null)));
    }

    @Test
    void formatPadsZeros() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "format", "precision", "2"),
                Map.of("value", 2.5));
        assertEquals("2.50", valueOf(node.handle(request, null)));
    }

    @Test
    void roundHalfUp() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "round", "precision", "0"),
                Map.of("value", 2.5));
        assertEquals(3L, valueOf(node.handle(request, null)));
    }

    @Test
    void mathAddsAndSerializesIntegralResultsAsLongs() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "math", "op", "+"),
                Map.of("value", 1, "other", 2));
        assertEquals(3L, valueOf(node.handle(request, null)));
    }

    @Test
    void inputOperationAndOpOverrideConfig() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "format", "op", "+"),
                Map.of("operation", "math", "op", "*", "value", 6, "other", 7));
        assertEquals(42L, valueOf(node.handle(request, null)));
    }

    @Test
    void mathDividesToDouble() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "math", "op", "/"),
                Map.of("value", 7, "other", 2));
        assertEquals(3.5, valueOf(node.handle(request, null)));
    }

    @Test
    void mathModulo() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "math", "op", "%"),
                Map.of("value", 7, "other", 3));
        assertEquals(1L, valueOf(node.handle(request, null)));
    }

    @Test
    void divisionByZeroIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "math", "op", "/"),
                Map.of("value", 1, "other", 0));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("division by zero"));
    }

    @Test
    void randomIntHonorsInclusiveBounds() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "random_int"),
                Map.of("min", 5, "max", 5));
        assertEquals(5L, valueOf(node.handle(request, null)));
    }

    @Test
    void randomIntStaysInRange() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "random_int"),
                Map.of("min", 1, "max", 10));
        for (int i = 0; i < 50; i++) {
            long v = ((Number) valueOf(node.handle(request, null))).longValue();
            assertTrue(v >= 1 && v <= 10, "out of range: " + v);
        }
    }

    @Test
    void randomIntRejectsInvertedRange() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "random_int"),
                Map.of("min", 10, "max", 1));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("min"));
    }

    @Test
    void missingValueIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "format"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("value"));
    }

    @Test
    void nonNumericValueIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "round"),
                Map.of("value", "abc"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("value"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "sqrt"),
                Map.of("value", 4));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
