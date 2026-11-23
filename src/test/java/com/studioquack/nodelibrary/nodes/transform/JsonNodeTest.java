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

/** JSON node: parse / stringify / jq subset / merge / RFC 6902 patch / flatten / unflatten. */
class JsonNodeTest {

    private final JsonNode node = new JsonNode();

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

    // ---------- parse / stringify ----------

    @Test
    void parseReadsJsonText() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "parse"),
                Map.of("value", "{\"a\":1,\"b\":[true,null]}"));
        // Arrays.asList because List.of rejects null elements
        assertEquals(Map.of("a", 1, "b", java.util.Arrays.asList(true, null)),
                dataOf(node.handle(request, null)));
    }

    @Test
    void parsePassesParsedValuesThrough() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "parse"),
                Map.of("value", Map.of("a", 1)));
        assertEquals(Map.of("a", 1), dataOf(node.handle(request, null)));
    }

    @Test
    void parseRejectsMalformedJson() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "parse"),
                Map.of("value", "{broken"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("invalid JSON"));
    }

    @Test
    void stringifyCompact() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "stringify"),
                Map.of("value", Map.of("a", 1)));
        assertEquals("{\"a\":1}", dataOf(node.handle(request, null)));
    }

    @Test
    void stringifyPrettyIndents() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "stringify", "pretty", "true"),
                Map.of("value", Map.of("a", 1)));
        assertEquals("{\n  \"a\": 1\n}", dataOf(node.handle(request, null)));
    }

    // ---------- jq subset ----------

    @Test
    void jqFieldPath() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "jq"),
                Map.of("value", Map.of("a", Map.of("b", 7)), "expression", ".a.b"));
        assertEquals(7, dataOf(node.handle(request, null)));
    }

    @Test
    void jqIterateAndPipe() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "jq"),
                Map.of("value", Map.of("items", List.of(Map.of("id", 1), Map.of("id", 2))),
                        "expression", ".items[] | .id"));
        assertEquals(List.of(1, 2), dataOf(node.handle(request, null)));
    }

    @Test
    void jqArrayIndex() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "jq"),
                Map.of("value", Map.of("items", List.of("a", "b")), "expression", ".items[1]"));
        assertEquals("b", dataOf(node.handle(request, null)));
    }

    @Test
    void jqIdentity() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "jq"),
                Map.of("value", Map.of("a", 1), "expression", "."));
        assertEquals(Map.of("a", 1), dataOf(node.handle(request, null)));
    }

    @Test
    void jqRejectsUnsupportedSyntax() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "jq"),
                Map.of("value", Map.of("a", 1), "expression", "map(.a)"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unsupported jq"));
    }

    @Test
    void jqMissingExpressionIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "jq"),
                Map.of("value", Map.of("a", 1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("expression"));
    }

    // ---------- merge ----------

    @Test
    void mergeFoldsAnArrayOfObjects() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "merge"),
                Map.of("value", List.of(Map.of("a", 1), Map.of("b", 2))));
        assertEquals(Map.of("a", 1, "b", 2), dataOf(node.handle(request, null)));
    }

    @Test
    void mergeIsDeepForNestedMaps() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "merge"),
                Map.of("value", List.of(
                        Map.of("a", Map.of("x", 1)),
                        Map.of("a", Map.of("y", 2)))));
        assertEquals(Map.of("a", Map.of("x", 1, "y", 2)), dataOf(node.handle(request, null)));
    }

    @Test
    void mergeRejectsNonObjectElements() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "merge"),
                Map.of("value", List.of(Map.of("a", 1), 42)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("objects"));
    }

    // ---------- patch (RFC 6902) ----------

    @Test
    void patchAddReplaceRemove() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "patch"),
                Map.of("value", Map.of("a", 1, "b", 2),
                        "patch", List.of(
                                Map.of("op", "add", "path", "/c", "value", 3),
                                Map.of("op", "replace", "path", "/a", "value", 9),
                                Map.of("op", "remove", "path", "/b"))));
        assertEquals(Map.of("a", 9, "c", 3), dataOf(node.handle(request, null)));
    }

    @Test
    void patchArrayAppendWithDash() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "patch"),
                Map.of("value", Map.of("list", List.of(1)),
                        "patch", List.of(Map.of("op", "add", "path", "/list/-", "value", 2))));
        assertEquals(Map.of("list", List.of(1, 2)), dataOf(node.handle(request, null)));
    }

    @Test
    void patchTestFailureIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "patch"),
                Map.of("value", Map.of("a", 1),
                        "patch", List.of(Map.of("op", "test", "path", "/a", "value", 2))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertTrue(e.getMessage().contains("test operation failed"));
    }

    @Test
    void patchMoveRelocatesAValue() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "patch"),
                Map.of("value", Map.of("a", 1),
                        "patch", List.of(Map.of("op", "move", "from", "/a", "path", "/b"))));
        assertEquals(Map.of("b", 1), dataOf(node.handle(request, null)));
    }

    @Test
    void patchMissingTargetIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "patch"),
                Map.of("value", Map.of("a", 1),
                        "patch", List.of(Map.of("op", "remove", "path", "/missing"))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertTrue(e.getMessage().contains("does not exist"));
    }

    // ---------- flatten / unflatten ----------

    @Test
    void flattenDotsNestedKeys() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "flatten"),
                Map.of("value", Map.of("a", Map.of("b", 1), "c", List.of("x", "y"))));
        assertEquals(Map.of("a.b", 1, "c.0", "x", "c.1", "y"), dataOf(node.handle(request, null)));
    }

    @Test
    void flattenHonorsPrefixFromConfigPath() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "flatten", "path", "root"),
                Map.of("value", Map.of("a", 1)));
        assertEquals(Map.of("root.a", 1), dataOf(node.handle(request, null)));
    }

    @Test
    void unflattenRebuildsNestedObjects() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "unflatten"),
                Map.of("value", Map.of("a.b", 1, "a.c", 2)));
        assertEquals(Map.of("a", Map.of("b", 1, "c", 2)), dataOf(node.handle(request, null)));
    }

    @Test
    void flattenRejectsScalars() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "flatten"),
                Map.of("value", 42));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("object or array"));
    }

    // ---------- generic errors ----------

    @Test
    void missingValueIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "parse"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("value"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "eval"),
                Map.of("value", Map.of()));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
