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

/** Rename Keys node: literal mappings, regex mode, arrays. */
class RenameKeysNodeTest {

    private final RenameKeysNode node = new RenameKeysNode();

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
    void arrowStringMappingsRenameKeys() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", Map.of("name", "John", "age", "30"),
                        "mappings", List.of("name->fullName")));
        assertEquals(Map.of("fullName", "John", "age", "30"), dataOf(node.handle(request, null)));
    }

    @Test
    void multipleArrowMappingsAndArrayOfObjects() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", List.of(Map.of("id", "1", "name", "A"), Map.of("id", "2", "name", "B")),
                        "mappings", List.of("name->fullName")));
        assertEquals(List.of(Map.of("id", "1", "fullName", "A"), Map.of("id", "2", "fullName", "B")),
                dataOf(node.handle(request, null)));
    }

    @Test
    void unicodeAndEmojiArrowMappings() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", Map.of("isim", "Ali", "🔑", "v"),
                        "mappings", List.of("isim->fullName", "🔑->key")));
        assertEquals(Map.of("fullName", "Ali", "key", "v"), dataOf(node.handle(request, null)));
    }

    @Test
    void mappingsAsPlainStringIsInvalid() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", Map.of("a", "1"), "mappings", "a->b"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("must be a JSON array"));
    }

    @Test
    void mappingsAsObjectIsInvalid() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", Map.of("a", "1"), "mappings", Map.of("a", "b")));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("must be a JSON array"));
    }

    @Test
    void emptyMappingStringIsInvalid() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", Map.of("a", "1"), "mappings", List.of("")));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("from->to"));
    }

    @Test
    void malformedMappingWithoutArrowIsInvalid() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", Map.of("a", "1"), "mappings", List.of("a")));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("from->to"));
    }

    @Test
    void nonStringMappingElementIsInvalid() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", Map.of("a", "1"), "mappings", List.of(Map.of("unexpected", "x"))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("requires non-empty from"));
    }

    @Test
    void literalRenameFromInputMappings() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", Map.of("n", "Ada", "keep", 1),
                        "mappings", List.of(Map.of("from", "n", "to", "name"))));
        assertEquals(Map.of("name", "Ada", "keep", 1), dataOf(node.handle(request, null)));
    }

    @Test
    void mappingsFromConfigJsonString() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename",
                        "mappings", "[{\"from\":\"n\",\"to\":\"name\"}]"),
                Map.of("item", Map.of("n", "Ada")));
        assertEquals(Map.of("name", "Ada"), dataOf(node.handle(request, null)));
    }

    @Test
    void arrayOfObjectsIsRenamedElementWise() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", List.of(Map.of("n", "a"), Map.of("n", "b")),
                        "mappings", List.of(Map.of("from", "n", "to", "name"))));
        assertEquals(List.of(Map.of("name", "a"), Map.of("name", "b")),
                dataOf(node.handle(request, null)));
    }

    @Test
    void regexModeRewritesMatchingKeys() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename", "regex", "true"),
                Map.of("item", Map.of("first_name", "Ada", "last_name", "L", "other", 1),
                        "mappings", List.of(Map.of("from", "(.*)_name", "to", "$1"))));
        assertEquals(Map.of("first", "Ada", "last", "L", "other", 1),
                dataOf(node.handle(request, null)));
    }

    @Test
    void invalidRegexMappingIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename", "regex", "true"),
                Map.of("item", Map.of("a", 1),
                        "mappings", List.of(Map.of("from", "(", "to", "x"))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("invalid regex"));
    }

    @Test
    void missingItemIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("mappings", List.of(Map.of("from", "a", "to", "b"))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("item"));
    }

    @Test
    void scalarItemIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "rename"),
                Map.of("item", 42, "mappings", List.of(Map.of("from", "a", "to", "b"))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("object"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "swap"),
                Map.of("item", Map.of("a", 1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
