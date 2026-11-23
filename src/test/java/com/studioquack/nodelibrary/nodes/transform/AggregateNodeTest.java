package com.studioquack.nodelibrary.nodes.transform;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aggregate node: fields and all_items over a JSON array. */
class AggregateNodeTest {

    private final AggregateNode node = new AggregateNode();

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
    void fieldsCollectsValuesAcrossItems() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "fields", "field", "status"),
                Map.of("items", List.of(
                        Map.of("status", "a", "v", 1),
                        Map.of("status", "b", "v", 2),
                        Map.of("status", "a", "v", 3))));
        assertEquals(Map.of("status", List.of("a", "b", "a")), dataOf(node.handle(request, null)));
    }

    @Test
    void fieldsHonorsRenameAndInputFieldOverride() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "fields", "rename", "statuses"),
                Map.of("field", "status",
                        "items", List.of(Map.of("status", "x"))));
        assertEquals(Map.of("statuses", List.of("x")), dataOf(node.handle(request, null)));
    }

    @Test
    void fieldsMergeListsFlattensArrayValues() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "fields", "field", "tags", "merge_lists", "true"),
                Map.of("items", List.of(
                        Map.of("tags", List.of("a", "b")),
                        Map.of("tags", List.of("c")))));
        assertEquals(Map.of("tags", List.of("a", "b", "c")), dataOf(node.handle(request, null)));
    }

    @Test
    void fieldsDropsNullsUnlessKeepNull() {
        List<Map<String, Object>> items = List.of(Map.of("v", 1), Map.of("status", "a", "v", 2));
        Map<String, Object> dropped = withInput(
                requestWithConfig("operation", "fields", "field", "status"), Map.of("items", items));
        assertEquals(Map.of("status", List.of("a")), dataOf(node.handle(dropped, null)));

        Map<String, Object> kept = withInput(
                requestWithConfig("operation", "fields", "field", "status", "keep_null", "true"),
                Map.of("items", items));
        List<?> values = (List<?>) ((Map<?, ?>) dataOf(node.handle(kept, null))).get("status");
        assertEquals(2, values.size());
        assertNull(values.get(0));
    }

    @Test
    void allItemsCombinesUnderItemsOrRename() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "all_items"),
                Map.of("items", List.of(1, 2, 3)));
        assertEquals(Map.of("items", List.of(1, 2, 3)), dataOf(node.handle(request, null)));

        Map<String, Object> renamed = withInput(
                requestWithConfig("operation", "all_items", "rename", "everything"),
                Map.of("items", List.of(1)));
        assertEquals(Map.of("everything", List.of(1)), dataOf(node.handle(renamed, null)));
    }

    @Test
    void missingItemsIsAnError() {
        Map<String, Object> request = withInput(requestWithConfig("operation", "all_items"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("items"));
    }

    @Test
    void fieldsWithoutFieldIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "fields"),
                Map.of("items", List.of(Map.of("a", 1))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("field"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "nope"),
                Map.of("items", List.of(1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }

    @Test
    void itemsAsJsonStringIsAccepted() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "all_items"),
                Map.of("items", "[1,2,3]"));
        assertEquals(Map.of("items", List.of(1, 2, 3)), dataOf(node.handle(request, null)));
    }
}
