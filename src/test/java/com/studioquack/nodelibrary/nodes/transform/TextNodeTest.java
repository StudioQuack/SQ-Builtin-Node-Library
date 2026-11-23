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

/** Text node: replace / regex / split / join / case / trim / template / slug / strip_html. */
class TextNodeTest {

    private final TextNode node = new TextNode();

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

    @SuppressWarnings("unchecked")
    private static String textOf(Map<String, Object> ports) {
        return (String) ((Map<String, Object>) dataOf(ports)).get("text");
    }

    @Test
    void replacePassthroughWhenOnlyText() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace"),
                Map.of("text", "hello world"));
        assertEquals("hello world", textOf(node.handle(request, null)));
    }

    @Test
    void replaceAppliesCaseWithoutPattern() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace"),
                Map.of("text", "merhaba dünya", "case", "upper"));
        assertEquals("MERHABA DÜNYA", textOf(node.handle(request, null)));
    }

    @Test
    void replaceSplitJoinWithSeparatorOnly() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace"),
                Map.of("text", "a-b-c", "separator", "-"));
        assertEquals("abc", textOf(node.handle(request, null)));
    }

    @Test
    void replacePatternAndSeparatorJoins() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace"),
                Map.of("text", "a-b-c", "pattern", "-", "separator", "+"));
        assertEquals("a+b+c", textOf(node.handle(request, null)));
    }

    @Test
    void replaceAppliesCaseAfterSubstitution() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace"),
                Map.of("text", "Hello World", "pattern", "Hello", "replacement", "Hi", "case", "upper"));
        assertEquals("HI WORLD", textOf(node.handle(request, null)));
    }

    @Test
    void replaceUsesRegexWhenPatternHasMetacharacters() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace"),
                Map.of("text", "hello world", "pattern", "l+", "replacement", "L"));
        assertEquals("heLo worLd", textOf(node.handle(request, null)));
    }

    @Test
    void replaceRejectsInvalidCaseEnum() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace"),
                Map.of("text", "abc", "case", "Bogus"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown case"));
    }

    @Test
    void replaceIsLiteralAndGlobal() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace"),
                Map.of("text", "a.b.c", "pattern", ".", "replacement", "-"));
        assertEquals("a-b-c", textOf(node.handle(request, null)));
    }

    @Test
    void replaceHonorsLimit() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace", "limit", "1"),
                Map.of("text", "ooo", "pattern", "o", "replacement", "0"));
        assertEquals("0oo", textOf(node.handle(request, null)));
    }

    @Test
    void regexReplaceWithFlags() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "regex", "flags", "i"),
                Map.of("text", "Hello HELLO hello", "pattern", "hello", "replacement", "hi"));
        assertEquals("hi hi hi", textOf(node.handle(request, null)));
    }

    @Test
    void regexWithoutReplacementListsMatches() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "regex"),
                Map.of("text", "a1b22c333", "pattern", "\\d+"));
        assertEquals(List.of("1", "22", "333"), dataOf(node.handle(request, null)));
    }

    @Test
    void invalidRegexPatternIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "regex"),
                Map.of("text", "x", "pattern", "(unclosed"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("invalid regex"));
    }

    @Test
    void splitUsesLiteralSeparator() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "split"),
                Map.of("text", "a|b|c", "separator", "|"));
        assertEquals(List.of("a", "b", "c"), dataOf(node.handle(request, null)));
    }

    @Test
    void splitDefaultsToComma() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "split"),
                Map.of("text", "a,b"));
        assertEquals(List.of("a", "b"), dataOf(node.handle(request, null)));
    }

    @Test
    void joinConcatenatesWithSeparator() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "join"),
                Map.of("text", List.of("a", "b", "c"), "separator", "-"));
        assertEquals("a-b-c", textOf(node.handle(request, null)));
    }

    @Test
    void joinRejectsPlainStrings() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "join"),
                Map.of("text", "abc"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("array"));
    }

    @Test
    void caseModes() {
        assertEquals("HELLO", textOf(node.handle(withInput(
                requestWithConfig("operation", "case"), Map.of("text", "hello", "case", "upper")), null)));
        assertEquals("hello", textOf(node.handle(withInput(
                requestWithConfig("operation", "case"), Map.of("text", "HELLO", "case", "lower")), null)));
        assertEquals("Hello World", textOf(node.handle(withInput(
                requestWithConfig("operation", "case"), Map.of("text", "hello world", "case", "title")), null)));
        assertEquals("helloWorld", textOf(node.handle(withInput(
                requestWithConfig("operation", "case"), Map.of("text", "hello world", "case", "camel")), null)));
        assertEquals("hello_world", textOf(node.handle(withInput(
                requestWithConfig("operation", "case"), Map.of("text", "helloWorld", "case", "snake")), null)));
    }

    @Test
    void unknownCaseIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "case"),
                Map.of("text", "x", "case", "shouty"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown case"));
    }

    @Test
    void trimRemovesEdgeWhitespace() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "trim"),
                Map.of("text", "  padded\n"));
        assertEquals("padded", textOf(node.handle(request, null)));
    }

    @Test
    void templateResolvesPlaceholdersAgainstInput() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "template"),
                Map.of("text", "Hi {{user.name}}, you are {{user.name}}",
                        "user", Map.of("name", "Ada")));
        assertEquals("Hi Ada, you are Ada", textOf(node.handle(request, null)));
    }

    @Test
    void templateUnknownPlaceholdersBecomeEmpty() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "template"),
                Map.of("text", "[{{missing}}]"));
        assertEquals("[]", textOf(node.handle(request, null)));
    }

    @Test
    void slugNormalizes() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "slug"),
                Map.of("text", "Héllo,  World!"));
        assertEquals("hello-world", textOf(node.handle(request, null)));
    }

    @Test
    void stripHtmlRemovesTagsAndDecodesEntities() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "strip_html"),
                Map.of("text", "<p>Hello <b>World</b> &amp; friends</p>"));
        assertEquals("Hello World & friends", textOf(node.handle(request, null)));
    }

    @Test
    void stripHtmlDropsScriptBodies() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "strip_html"),
                Map.of("text", "<p>ok</p><script>alert(1)</script>"));
        assertEquals("ok", textOf(node.handle(request, null)));
    }

    @Test
    void missingTextIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "trim"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("text"));
    }

    @Test
    void replacementWithoutPatternIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "replace"),
                Map.of("text", "abc", "replacement", "x"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("pattern"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "encode"),
                Map.of("text", "x"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
