package com.studioquack.nodelibrary.nodes.protocol;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Convert File: per-format write, XLSX/ODS zip XML checks, ICS/CSV round-trip,
 * and INVALID_INPUT for missing file / unknown / removed {@code to_xls}.
 */
class ConvertFileNodeTest {

    private final ConvertFileNode node = new ConvertFileNode(
            "a40a264c-c9ae-45d2-b591-665576cbd588");

    @Test
    void templateIdUsesConstructor() {
        assertEquals("a40a264c-c9ae-45d2-b591-665576cbd588", node.templateId());
        assertEquals(ConvertFileNode.DEFAULT_ID, new ConvertFileNode(null).templateId());
    }

    @Test
    void toCsvRoundTripQuotedFields() {
        String csv = "name,note\r\n\"Ada Lovelace\",\"said \"\"hello\"\", then left\"\r\n";
        Map<String, Object> result = run("to_csv", "people.csv",
                csv.getBytes(StandardCharsets.UTF_8), true);
        String out = new String(fileBytes(result), StandardCharsets.UTF_8);
        assertTrue(out.contains("Ada Lovelace"));
        assertTrue(out.contains("\"\"hello\"\""));
        assertEquals("ok", output(result).get("status"));
        assertEquals("converted.csv", ((Map<?, ?>) output(result).get("file")).get("name"));
    }

    @Test
    void toXlsxUnzipsToSheetXml() throws Exception {
        Map<String, Object> result = run("to_xlsx", "people.csv",
                "name,age\nAda,36\n".getBytes(StandardCharsets.UTF_8), true);
        byte[] xlsx = fileBytes(result);
        Map<String, String> entries = unzipText(xlsx);
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"));
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("inlineStr"));
        assertTrue(sheet.contains(">Ada<"));
        assertTrue(sheet.contains(">36<"));
        assertTrue(sheet.contains("r=\"A1\""));
        assertTrue(sheet.contains("r=\"B2\""));
        String workbook = entries.get("xl/workbook.xml");
        assertTrue(workbook.contains("sheet name="));
    }

    @Test
    void toOdsUnzipsToContentXml() throws Exception {
        Map<String, Object> result = run("to_ods", "people.csv",
                "name,age\nGrace,85\n".getBytes(StandardCharsets.UTF_8), true);
        Map<String, String> entries = unzipText(fileBytes(result));
        assertEquals("application/vnd.oasis.opendocument.spreadsheet", entries.get("mimetype"));
        String content = entries.get("content.xml");
        assertTrue(content.contains("table:table"));
        assertTrue(content.contains("Grace"));
        assertTrue(content.contains("85"));
    }

    @Test
    void toJsonUsesHeaderRow() {
        Map<String, Object> result = run("to_json", "people.csv",
                "name,age\nAda,36\n".getBytes(StandardCharsets.UTF_8), true);
        String json = new String(fileBytes(result), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("Ada"));
        assertTrue(json.contains("36"));
    }

    @Test
    void toHtmlWritesTable() {
        Map<String, Object> result = run("to_html", "people.csv",
                "name,age\nAda,36\n".getBytes(StandardCharsets.UTF_8), true);
        String html = new String(fileBytes(result), StandardCharsets.UTF_8);
        assertTrue(html.contains("<th>name</th>"));
        assertTrue(html.contains("<td>Ada</td>"));
    }

    @Test
    void toRtfWritesRows() {
        Map<String, Object> result = run("to_rtf", "people.csv",
                "name,age\nAda,36\n".getBytes(StandardCharsets.UTF_8), true);
        String rtf = new String(fileBytes(result), StandardCharsets.UTF_8);
        assertTrue(rtf.startsWith("{\\rtf1"));
        assertTrue(rtf.contains("Ada"));
        assertTrue(rtf.contains("36"));
    }

    @Test
    void toTextWritesTabRows() {
        Map<String, Object> result = run("to_text", "people.csv",
                "name,age\nAda,36\n".getBytes(StandardCharsets.UTF_8), true);
        String text = new String(fileBytes(result), StandardCharsets.UTF_8);
        assertTrue(text.contains("Ada\t36"));
    }

    @Test
    void toIcsRoundTripEvents() {
        String ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:1@sq\r\nSUMMARY:Standup\r\n"
                + "DTSTART:20260821T130000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        Map<String, Object> result = run("to_ics", "cal.ics",
                ics.getBytes(StandardCharsets.UTF_8), true);
        String out = new String(fileBytes(result), StandardCharsets.UTF_8);
        assertTrue(out.contains("BEGIN:VCALENDAR"));
        assertTrue(out.contains("SUMMARY:Standup"));
        assertTrue(out.contains("DTSTART:20260821T130000Z"));
        assertTrue(out.contains("UID:1@sq"));
    }

    @Test
    void xlsxToCsvRoundTrip() {
        Map<String, Object> xlsx = run("to_xlsx", "src.csv",
                "city,pop\nOslo,1\n".getBytes(StandardCharsets.UTF_8), true);
        Map<String, Object> csv = node.handle(request(
                cfg("operation", "to_csv", "header_row", "true"),
                input("file", fileValue("book.xlsx", fileBytes(xlsx)))), null);
        String out = new String(fileBytes(csv), StandardCharsets.UTF_8);
        assertTrue(out.contains("Oslo"));
        assertTrue(out.contains("city"));
    }

    @Test
    void jsonArrayToCsv() {
        Map<String, Object> result = run("to_csv", "rows.json",
                "[{\"a\":1,\"b\":\"x\"}]".getBytes(StandardCharsets.UTF_8), true);
        String csv = new String(fileBytes(result), StandardCharsets.UTF_8);
        assertTrue(csv.contains("a,b") || csv.contains("b,a"));
        assertTrue(csv.contains("1"));
        assertTrue(csv.contains("x"));
    }

    @Test
    void fileNameFromConfig() {
        Map<String, Object> result = node.handle(request(
                cfg("operation", "to_text", "file_name", "notes.txt", "header_row", "false"),
                input("file", fileValue("src.csv", "a,b\n".getBytes(StandardCharsets.UTF_8)))), null);
        assertEquals("notes.txt", ((Map<?, ?>) output(result).get("file")).get("name"));
    }

    @Test
    void toXlsIsRejected() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> run("to_xls", "old.csv",
                "a,b\n".getBytes(StandardCharsets.UTF_8), true));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("not supported"));
    }

    @Test
    void unknownOperationFails() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> run("to_docx", "a.csv",
                "a\n".getBytes(StandardCharsets.UTF_8), true));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Unknown operation"));
    }

    @Test
    void emptyFileStringUsesRequiredMessage() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () ->
                node.handle(request(
                        cfg("operation", "to_csv"),
                        input("file", "")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("file is required"));
    }

    @Test
    void sheetNumericIndexCoercedToString() {
        assertEquals("2", ConvertFileNode.sheetSelection(Map.of("sheet", 2), Map.of()));
    }

    @Test
    void missingFileFails() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () ->
                node.handle(request(cfg("operation", "to_csv"), input()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("file"));
    }

    @Test
    void unknownEncodingFails() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () ->
                node.handle(request(
                        cfg("operation", "to_csv", "encoding", "not-a-charset"),
                        input("file", fileValue("a.csv", "a\n".getBytes(StandardCharsets.UTF_8)))),
                        null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("encoding"));
    }

    @Test
    void outputHasNoErrorFieldOnSuccess() {
        Map<String, Object> result = run("to_csv", "a.csv",
                "h\nv\n".getBytes(StandardCharsets.UTF_8), true);
        assertFalse(output(result).containsKey("error"));
        assertTrue(output(result).get("outputFiles") instanceof List<?>);
    }

    @Test
    void rowCapIsEnforced() {
        StringBuilder sb = new StringBuilder("n\n");
        for (int i = 0; i < ConvertFileNode.MAX_ROWS + 2; i++) {
            sb.append(i).append('\n');
        }
        StudioQuackError e = assertThrows(StudioQuackError.class, () ->
                run("to_csv", "big.csv", sb.toString().getBytes(StandardCharsets.UTF_8), false));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("row"));
    }

    @Test
    void colRefMatchesExcel() {
        assertEquals("A", ConvertFormats.colRef(0));
        assertEquals("Z", ConvertFormats.colRef(25));
        assertEquals("AA", ConvertFormats.colRef(26));
    }

    private Map<String, Object> run(String op, String name, byte[] bytes, boolean headerRow) {
        return node.handle(request(
                cfg("operation", op, "header_row", String.valueOf(headerRow),
                        "encoding", "UTF-8", "timeout_seconds", "25"),
                input("file", fileValue(name, bytes))), null);
    }

    private static Map<String, Object> request(Map<String, Object> cfg, Map<String, Object> input) {
        List<Map<String, Object>> params = new ArrayList<>();
        cfg.forEach((k, v) -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("shortCode", k);
            p.put("value", v);
            params.add(p);
        });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configurationParameters", params);
        Map<String, Object> currentNode = new LinkedHashMap<>();
        currentNode.put("data", data);
        Map<String, Object> executionContext = new LinkedHashMap<>();
        executionContext.put("currentNode", currentNode);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", executionContext);
        request.put("inputVariables", Map.of("input", input));
        return request;
    }

    private static Map<String, Object> cfg(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> input(Object... kv) {
        return cfg(kv);
    }

    private static Map<String, Object> fileValue(String name, byte[] bytes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "file");
        m.put("name", name);
        m.put("mime_type", "application/octet-stream");
        m.put("source", "base64");
        m.put("data", Base64.getEncoder().encodeToString(bytes));
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> result) {
        return (Map<String, Object>) result.get("output");
    }

    @SuppressWarnings("unchecked")
    private static byte[] fileBytes(Map<String, Object> result) {
        Map<String, Object> file = (Map<String, Object>) output(result).get("file");
        return Base64.getDecoder().decode(String.valueOf(file.get("data")));
    }

    private static Map<String, String> unzipText(byte[] zip) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                out.put(e.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }
}
