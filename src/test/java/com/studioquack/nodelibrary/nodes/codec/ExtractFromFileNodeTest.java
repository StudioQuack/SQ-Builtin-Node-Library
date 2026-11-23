package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extract From File: CSV (header on/off), JSON, text, HTML, RTF, ICS, XLSX,
 * the from_xls unsupported error, and missing-input failures.
 */
class ExtractFromFileNodeTest {

    private final ExtractFromFileNode node = new ExtractFromFileNode();

    private Map<String, Object> run(String op, String name, byte[] bytes, boolean headerRow) {
        return CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", op, "header_row", String.valueOf(headerRow)),
                CodecTestSupport.input("file", CodecTestSupport.fileValue(name, bytes))));
    }

    @Test
    void csvWithHeaderRow() {
        Map<String, Object> result = run("from_csv", "people.csv",
                "name,age\nAda,36\nGrace,85\n".getBytes(StandardCharsets.UTF_8), true);
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals(2, CodecTestSupport.data(result).get("row_count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) CodecTestSupport.data(result).get("rows");
        assertEquals("Ada", rows.get(0).get("name"));
        assertEquals("85", rows.get(1).get("age"));
    }

    @Test
    void csvWithoutHeaderRow() {
        Map<String, Object> result = run("from_csv", "raw.csv",
                "1,2\n3,4\n".getBytes(StandardCharsets.UTF_8), false);
        @SuppressWarnings("unchecked")
        List<List<String>> rows = (List<List<String>>) CodecTestSupport.data(result).get("rows");
        assertEquals(2, rows.size());
        assertEquals(List.of("3", "4"), rows.get(1));
    }

    @Test
    void csvQuotedFieldsWithCommasAndNewlines() {
        Map<String, Object> result = run("from_csv", "q.csv",
                "a,b\n\"x, y\",\"line1\nline2\"\n".getBytes(StandardCharsets.UTF_8), true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) CodecTestSupport.data(result).get("rows");
        assertEquals("x, y", rows.get(0).get("a"));
        assertEquals("line1\nline2", rows.get(0).get("b"));
    }

    @Test
    void jsonParsesToValue() {
        Map<String, Object> result = run("from_json", "doc.json",
                "{\"a\": [1, 2]}".getBytes(StandardCharsets.UTF_8), true);
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) CodecTestSupport.data(result).get("value");
        assertEquals(List.of(1, 2), value.get("a"));
    }

    @Test
    void invalidJsonFails() {
        Map<String, Object> result = run("from_json", "bad.json",
                "{nope".getBytes(StandardCharsets.UTF_8), true);
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void textReturnsContentAndCounts() {
        Map<String, Object> result = run("from_text", "notes.txt",
                "one\ntwo\n".getBytes(StandardCharsets.UTF_8), true);
        String text = String.valueOf(CodecTestSupport.data(result).get("text"));
        assertTrue(text.contains("one"));
        assertEquals(3, CodecTestSupport.data(result).get("line_count"));
    }

    @Test
    void htmlStripsToText() {
        Map<String, Object> result = run("from_html", "page.html",
                "<h1>Hi</h1><p>there &amp; you</p>".getBytes(StandardCharsets.UTF_8), true);
        String text = String.valueOf(CodecTestSupport.data(result).get("text"));
        assertTrue(text.contains("Hi"));
        assertTrue(text.contains("there & you"));
    }

    @Test
    void rtfStripsControlWords() {
        Map<String, Object> result = run("from_rtf", "doc.rtf",
                "{\\rtf1\\ansi {\\fonttbl {\\f0 Arial;}} \\f0\\fs24 Hello \\par World}"
                        .getBytes(StandardCharsets.UTF_8), true);
        String text = String.valueOf(CodecTestSupport.data(result).get("text"));
        assertTrue(text.contains("Hello"));
        assertTrue(text.contains("World"));
    }

    @Test
    void icsParsesEvents() {
        String ics = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:1@sq\nSUMMARY:Standup\n"
                + "DTSTART:20260821T130000Z\nEND:VEVENT\nBEGIN:VEVENT\nUID:2@sq\n"
                + "SUMMARY:Review\\, weekly\nEND:VEVENT\nEND:VCALENDAR\n";
        Map<String, Object> result = run("from_ics", "cal.ics",
                ics.getBytes(StandardCharsets.UTF_8), true);
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals(2, CodecTestSupport.data(result).get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events =
                (List<Map<String, Object>>) CodecTestSupport.data(result).get("events");
        assertEquals("Standup", events.get(0).get("summary"));
        assertEquals("Review, weekly", events.get(1).get("summary"));
    }

    @Test
    void xlsxReadsSharedStringsAndCells() throws Exception {
        Map<String, Object> result = run("from_xlsx", "book.xlsx", minimalXlsx(), true);
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals(1, CodecTestSupport.data(result).get("sheet_count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sheets =
                (List<Map<String, Object>>) CodecTestSupport.data(result).get("sheets");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) sheets.get(0).get("rows");
        assertEquals("Ada", rows.get(0).get("name"));
        assertEquals("36", rows.get(0).get("age"));
    }

    @Test
    void fromXlsIsUnsupported() {
        Map<String, Object> result = run("from_xls", "old.xls",
                "ole2".getBytes(StandardCharsets.UTF_8), true);
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(error.contains("not supported"));
    }

    @Test
    void numericFileFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "from_csv"),
                CodecTestSupport.input("file", 123)));
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(error.contains("must be a file-typed value"));
    }

    @Test
    void stringHeaderRowFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "from_csv"),
                CodecTestSupport.input(
                        "file", CodecTestSupport.fileValue("a.csv", "a,b\n1,2".getBytes(StandardCharsets.UTF_8)),
                        "header_row", "no")));
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(error.contains("header_row must be a boolean"));
    }

    @Test
    void missingFileFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "from_csv"),
                CodecTestSupport.input()));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void unknownOperationFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "from_docx"),
                CodecTestSupport.input("file",
                        CodecTestSupport.fileValue("a.bin", new byte[]{1}))));
        assertNotNull(CodecTestSupport.error(result));
    }

    /** Smallest valid xlsx: sharedStrings + one sheet, name/age header + one row. */
    private static byte[] minimalXlsx() throws Exception {
        String shared = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><t>name</t></si><si><t>age</t></si><si><t>Ada</t></si></sst>";
        String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>"
                + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"B2\"><v>36</v></c></row>"
                + "</sheetData></worksheet>";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zos.write(shared.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zos.write(sheet.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}
