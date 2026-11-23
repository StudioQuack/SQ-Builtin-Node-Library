package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Markdown node: block + inline rendering to HTML, GFM tables, HTML → MD, and
 * script-injection safety (source is escaped before formatting).
 */
class MarkdownNodeTest {

    private final MarkdownNode node = new MarkdownNode();

    private String toHtml(String markdown) {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "md_to_html"),
                CodecTestSupport.input("markdown", markdown)));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        return String.valueOf(CodecTestSupport.data(result).get("html"));
    }

    private String toMd(String html) {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "html_to_md"),
                CodecTestSupport.input("html", html)));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        return String.valueOf(CodecTestSupport.data(result).get("markdown"));
    }

    @Test
    void headingsAndParagraphs() {
        String html = toHtml("# Title\n\nSome text here.");
        assertTrue(html.contains("<h1>Title</h1>"));
        assertTrue(html.contains("<p>Some text here.</p>"));
    }

    @Test
    void boldItalicCodeLink() {
        String html = toHtml("A **bold** and *italic* and `code` and [sq](https://example.com).");
        assertTrue(html.contains("<strong>bold</strong>"));
        assertTrue(html.contains("<em>italic</em>"));
        assertTrue(html.contains("<code>code</code>"));
        assertTrue(html.contains("<a href=\"https://example.com\">sq</a>"));
    }

    @Test
    void fencedCodeBlockIsEscapedNotFormatted() {
        String html = toHtml("```java\nint x = **not bold**;\n```");
        assertTrue(html.contains("<pre><code class=\"language-java\">"));
        assertTrue(html.contains("**not bold**"), "markers inside fences stay literal");
        assertTrue(!html.contains("<strong>not bold</strong>"));
    }

    @Test
    void unorderedAndOrderedLists() {
        String html = toHtml("- one\n- two\n\n1. first\n2. second");
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>one</li>"));
        assertTrue(html.contains("<ol>"));
        assertTrue(html.contains("<li>second</li>"));
    }

    @Test
    void gfmTable() {
        String html = toHtml("| Name | Age |\n| --- | --- |\n| Ada | 36 |");
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>Name</th>"));
        assertTrue(html.contains("<td>Ada</td>"));
    }

    @Test
    void tablesCanBeDisabled() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "md_to_html", "tables", "false"),
                CodecTestSupport.input("markdown", "| a |\n| --- |\n| b |")));
        String html = String.valueOf(CodecTestSupport.data(result).get("html"));
        assertTrue(!html.contains("<table>"));
    }

    @Test
    void rawHtmlInSourceIsEscaped() {
        String html = toHtml("Hello <script>alert(1)</script>");
        assertTrue(!html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void htmlToMdRoundTripShapes() {
        String md = toMd("<h2>Head</h2><p>Some <strong>bold</strong> and <em>em</em> "
                + "and <code>x=1</code>.</p><ul><li>a</li><li>b</li></ul>");
        assertTrue(md.contains("## Head"));
        assertTrue(md.contains("**bold**"));
        assertTrue(md.contains("*em*"));
        assertTrue(md.contains("`x=1`"));
        assertTrue(md.contains("- a"));
        assertTrue(md.contains("- b"));
    }

    @Test
    void htmlToMdLinksAndTable() {
        String md = toMd("<p><a href=\"https://example.com\">ex</a></p>"
                + "<table><tr><th>N</th></tr><tr><td>v</td></tr></table>");
        assertTrue(md.contains("[ex](https://example.com)"));
        assertTrue(md.contains("| N |"));
        assertTrue(md.contains("| v |"));
    }

    @Test
    void htmlToMdSkipsScript() {
        String md = toMd("<p>keep</p><script>drop()</script>");
        assertTrue(md.contains("keep"));
        assertTrue(!md.contains("drop()"));
    }

    @Test
    void missingInputFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "md_to_html"),
                CodecTestSupport.input()));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void unknownOperationFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "lint"),
                CodecTestSupport.input("markdown", "x")));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void blockquoteRenders() {
        String html = toHtml("> quoted words");
        assertTrue(html.contains("<blockquote>"));
        assertTrue(html.contains("quoted words"));
    }
}
