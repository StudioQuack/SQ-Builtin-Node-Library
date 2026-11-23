package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTML node: mustache-like templating (escaped + raw), CSS extract, to_table,
 * and the file source path.
 */
class HtmlNodeTest {

    private final HtmlNode node = new HtmlNode();

    @Test
    void templateEscapesByDefault() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "template"),
                CodecTestSupport.input("template", "<p>Hello {{name}}</p>",
                        "item", Map.of("name", "<script>alert(1)</script>"))));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        String html = String.valueOf(CodecTestSupport.data(result).get("html"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(!html.contains("<script>"));
    }

    @Test
    void templateRawTripleMustache() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "template"),
                CodecTestSupport.input("template", "<div>{{{body}}}</div>",
                        "item", Map.of("body", "<b>bold</b>"))));
        assertEquals("<div><b>bold</b></div>", CodecTestSupport.data(result).get("html"));
    }

    @Test
    void templateDottedPath() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "template"),
                CodecTestSupport.input("template", "{{user.name}}",
                        "item", Map.of("user", Map.of("name", "Ada")))));
        assertEquals("Ada", CodecTestSupport.data(result).get("html"));
    }

    @Test
    void templateMissingKeyIsEmpty() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "template"),
                CodecTestSupport.input("template", "[{{absent}}]", "item", Map.of())));
        assertEquals("[]", CodecTestSupport.data(result).get("html"));
    }

    @Test
    void extractByCssSelector() {
        String doc = "<html><body><ul><li class=\"a\">one</li><li>two</li></ul>"
                + "<p id=\"x\">para</p></body></html>";
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "extract", "css_selector", "ul li.a"),
                CodecTestSupport.input("html", doc)));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals(1, CodecTestSupport.data(result).get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches =
                (List<Map<String, Object>>) CodecTestSupport.data(result).get("matches");
        assertEquals("one", matches.get(0).get("text"));
    }

    @Test
    void extractById() {
        String doc = "<div><p id=\"target\">hit</p><p>miss</p></div>";
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "extract"),
                CodecTestSupport.input("html", doc, "css_selector", "#target")));
        assertEquals(1, CodecTestSupport.data(result).get("count"));
    }

    @Test
    void extractWithoutSelectorReturnsDocumentText() {
        String doc = "<html><head><style>.x{}</style><script>var a=1;</script></head>"
                + "<body><h1>Title</h1><p>Body &amp; soul</p></body></html>";
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "extract"),
                CodecTestSupport.input("html", doc)));
        String text = String.valueOf(CodecTestSupport.data(result).get("text"));
        assertTrue(text.contains("Title"));
        assertTrue(text.contains("Body & soul"));
        assertTrue(!text.contains("var a=1"), "script content must be excluded");
    }

    @Test
    void toTableExtractsHeadersAndRows() {
        String doc = "<table><thead><tr><th>Name</th><th>Age</th></tr></thead>"
                + "<tbody><tr><td>Ada</td><td>36</td></tr>"
                + "<tr><td>Grace</td><td>85</td></tr></tbody></table>";
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "to_table"),
                CodecTestSupport.input("html", doc)));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals(1, CodecTestSupport.data(result).get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables =
                (List<Map<String, Object>>) CodecTestSupport.data(result).get("tables");
        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) tables.get(0).get("headers");
        assertEquals(List.of("Name", "Age"), headers);
        @SuppressWarnings("unchecked")
        List<List<String>> rows = (List<List<String>>) tables.get(0).get("rows");
        assertEquals(2, rows.size());
        assertEquals(List.of("Grace", "85"), rows.get(1));
    }

    @Test
    void htmlFromFileSource() {
        String doc = "<p>from file</p>";
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "extract", "source", "file"),
                CodecTestSupport.input("file", CodecTestSupport.fileValue("doc.html",
                        doc.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertTrue(String.valueOf(CodecTestSupport.data(result).get("text")).contains("from file"));
    }

    @Test
    void missingHtmlFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "extract"),
                CodecTestSupport.input()));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void unknownOperationFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "crawl"),
                CodecTestSupport.input("html", "<p/>")));
        assertNotNull(CodecTestSupport.error(result));
    }
}
