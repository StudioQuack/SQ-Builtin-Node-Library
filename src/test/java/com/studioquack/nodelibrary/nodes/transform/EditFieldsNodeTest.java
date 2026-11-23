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

/** Edit Fields node: set / keep_set / json_output, dot notation, binary passthrough. */
class EditFieldsNodeTest {

    private final EditFieldsNode node = new EditFieldsNode();

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
    void setAppliesDottedPaths() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set"),
                Map.of("item", Map.of("a", 1),
                        "fields", List.of(Map.of("path", "user.name", "value", "Ada"))));
        assertEquals(Map.of("a", 1, "user", Map.of("name", "Ada")), dataOf(node.handle(request, null)));
    }

    @Test
    void setWithDotNotationOffUsesLiteralKeys() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set", "dot_notation", "false"),
                Map.of("item", Map.of("a", 1),
                        "fields", List.of(Map.of("path", "user.name", "value", "Ada"))));
        assertEquals(Map.of("a", 1, "user.name", "Ada"), dataOf(node.handle(request, null)));
    }

    @Test
    void setDoesNotMutateTheInputItem() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("a", 1);
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set"),
                Map.of("item", item, "fields", List.of(Map.of("path", "b", "value", 2))));
        dataOf(node.handle(request, null));
        assertEquals(Map.of("a", 1), item);
    }

    @Test
    void keepSetKeepsOnlyListedPaths() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "keep_set"),
                Map.of("item", Map.of("a", 1, "b", 2, "c", Map.of("d", 3)),
                        "fields", List.of(Map.of("path", "a"), Map.of("path", "c.d"))));
        assertEquals(Map.of("a", 1, "c", Map.of("d", 3)), dataOf(node.handle(request, null)));
    }

    @Test
    void jsonOutputBuildsAFreshObjectWithoutItem() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_output",
                        "fields", "[{\"path\":\"x.y\",\"value\":42}]"),
                Map.of());
        assertEquals(Map.of("x", Map.of("y", 42)), dataOf(node.handle(request, null)));
    }

    @Test
    void includeBinaryPassesTheFileRefThrough() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set", "include_binary", "true"),
                Map.of("item", Map.of("a", 1), "file", "ref-abc-123",
                        "fields", List.of(Map.of("path", "b", "value", 2))));
        Map<String, Object> out = output(node.handle(request, null));
        assertEquals("ok", out.get("status"));
        assertEquals("ref-abc-123", out.get("file"));
    }

    @Test
    void fileRefIsDroppedWhenIncludeBinaryIsFalse() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set"),
                Map.of("item", Map.of("a", 1), "file", "ref-abc-123",
                        "fields", List.of(Map.of("path", "b", "value", 2))));
        assertFalse(output(node.handle(request, null)).containsKey("file"));
    }

    @Test
    void missingItemIsAnErrorForSet() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set"),
                Map.of("fields", List.of(Map.of("path", "b", "value", 2))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("item"));
    }

    @Test
    void malformedFieldsStringIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set", "fields", "{not json"),
                Map.of("item", Map.of("a", 1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("fields"));
    }

    @Test
    void setParsesJsonEncodedFieldStrings() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set"),
                Map.of("item", Map.of("user", Map.of("name", "John"), "age", 30),
                        "fields", List.of("{\"path\":\"user.name\",\"value\":\"Ada\"}")));
        assertEquals(Map.of("user", Map.of("name", "Ada"), "age", 30), dataOf(node.handle(request, null)));
    }

    @Test
    void setAcceptsPathEqualsValueSyntax() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set"),
                Map.of("item", Map.of("user", Map.of("name", "John")),
                        "fields", List.of("user.name=Ada")));
        assertEquals(Map.of("user", Map.of("name", "Ada")), dataOf(node.handle(request, null)));
    }

    @Test
    void setBarePathRemovesLeaf() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set"),
                Map.of("item", Map.of("user", Map.of("name", "John"), "age", 30),
                        "fields", List.of("user.name")));
        assertEquals(Map.of("user", Map.of(), "age", 30), dataOf(node.handle(request, null)));
    }

    @Test
    void setNullValueClearsField() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set"),
                Map.of("item", Map.of("a", 1),
                        "fields", List.of("{\"path\":\"a\",\"value\":null}")));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataOf(node.handle(request, null));
        assertTrue(data.containsKey("a"));
        assertEquals(null, data.get("a"));
    }

    @Test
    void malformedFieldEntryIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "set"),
                Map.of("item", Map.of("a", 1), "fields", List.of("{not json")));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("fields[0]"));
    }

    @Test
    void jsonOutputEchoesItemWhenFieldsEmpty() {
        Map<String, Object> item = Map.of("user", Map.of("name", "John"), "age", 30);
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_output"),
                Map.of("item", item, "fields", List.of()));
        assertEquals(item, dataOf(node.handle(request, null)));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "delete"),
                Map.of("item", Map.of("a", 1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
