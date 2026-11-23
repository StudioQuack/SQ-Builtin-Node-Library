package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import studioquack.Json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** bodyFormat encoding without HTTP (except multipart url, covered in OfficialApiNodeTest). */
class OfficialApiBodyTest {

    private static OfficialApiSpec.Operation op(Map<String, Object> fields) {
        return OfficialApiSpec.fromMap("t", Map.of(
                "baseUrl", "https://x.test",
                "operations", Map.of("do", fields))).operation("do");
    }

    @Test
    void jsonDefaultUsesBodyFromSubset() {
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST", "bodyFrom", List.of("name")));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op,
                Map.of("name", "widget", "ignored", "x"), 5);
        assertEquals("application/json", p.contentType());
        assertEquals("{\"name\":\"widget\"}", new String(p.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void jsonParsesJsonTextArrayFieldsIntoNativeJson() {
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFrom", List.of("messages", "model")));
        String raw = "[{\"role\":\"user\",\"content\":\"hi\"}]";
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op,
                Map.of("messages", raw, "model", "grok-test"), 5);
        Map<String, Object> body = Json.asMap(Json.parse(new String(p.bytes(), StandardCharsets.UTF_8)));
        assertEquals(List.of(Map.of("role", "user", "content", "hi")), body.get("messages"));
        assertEquals("grok-test", body.get("model"));
    }

    @Test
    void formEncodesBodyFromAsUrlencoded() {
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "form",
                "bodyFrom", List.of("q", "limit")));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op,
                Map.of("q", "hello world", "limit", 5, "ignored", "x"), 5);
        assertEquals("application/x-www-form-urlencoded", p.contentType());
        assertEquals("q=hello%20world&limit=5", new String(p.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void multipartEmitsTextAndBase64FileParts() {
        byte[] file = "hi".getBytes(StandardCharsets.UTF_8);
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "multipart",
                "bodyFrom", List.of("note", "file")));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of(
                "note", "hello",
                "file", Map.of(
                        "dataBase64", Base64.getEncoder().encodeToString(file),
                        "fileName", "hi.txt",
                        "contentType", "text/plain")), 5);
        assertFalse(p.failed());
        assertTrue(p.contentType().startsWith("multipart/form-data; boundary="));
        String body = new String(p.bytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("name=\"note\""), body);
        assertTrue(body.contains("hello"), body);
        assertTrue(body.contains("filename=\"hi.txt\""), body);
        assertTrue(body.contains("Content-Type: text/plain"), body);
        assertTrue(body.contains("hi"), body);
        assertTrue(body.contains("--" + p.contentType().substring("multipart/form-data; boundary=".length()) + "--"));
    }

    @Test
    void multipartReadsThePlatformFileContentBlockNatively() {
        // A file-typed port carries {kind, mime_type, source, data, name}. That block alone must
        // become the part — no workflow should have to duplicate data as dataBase64 to attach it.
        byte[] file = "hi".getBytes(StandardCharsets.UTF_8);
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "multipart",
                "bodyFrom", List.of("file")));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of(
                "file", Map.of(
                        "kind", "document",
                        "mime_type", "text/plain",
                        "source", "base64",
                        "data", Base64.getEncoder().encodeToString(file),
                        "name", "hi.txt")), 5);
        assertFalse(p.failed());
        String body = new String(p.bytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("filename=\"hi.txt\""), body);
        assertTrue(body.contains("Content-Type: text/plain"), body);
        assertTrue(body.contains("\r\n\r\nhi\r\n"), body);
    }

    @Test
    void multipartBlockCarryingBothShapesIsStillOnePart() {
        // Legacy keys beside the block: one part, the block's data is the body, never two copies.
        byte[] file = "hi".getBytes(StandardCharsets.UTF_8);
        String b64 = Base64.getEncoder().encodeToString(file);
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "multipart",
                "bodyFrom", List.of("file")));
        Map<String, Object> both = new java.util.LinkedHashMap<>();
        both.put("mime_type", "text/plain");
        both.put("source", "base64");
        both.put("data", b64);
        both.put("name", "hi.txt");
        both.put("dataBase64", b64);
        both.put("fileName", "hi.txt");
        both.put("contentType", "text/plain");
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of("file", both), 5);
        assertFalse(p.failed());
        String body = new String(p.bytes(), StandardCharsets.UTF_8);
        int first = body.indexOf("filename=\"hi.txt\"");
        assertTrue(first >= 0, body);
        assertEquals(-1, body.indexOf("filename=\"hi.txt\"", first + 1), "exactly one file part");
    }

    @Test
    void rawSendsNamedInputVerbatim() {
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "raw",
                "bodyParam", "payload",
                "contentType", "text/plain"));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op,
                Map.of("payload", "not-json {ok}"), 5);
        assertEquals("text/plain", p.contentType());
        assertEquals("not-json {ok}", new String(p.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void rawRequiresBodyParam() {
        OfficialApiSpec.Operation op = op(Map.of("method", "POST", "bodyFormat", "raw"));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of("payload", "x"), 5);
        assertTrue(p.failed());
        assertTrue(p.error().contains("bodyParam"));
    }

    @Test
    void invalidBase64IsError() {
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "multipart",
                "bodyFrom", List.of("file")));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op,
                Map.of("file", Map.of("dataBase64", "!!!!")), 5);
        assertTrue(p.failed());
        assertTrue(p.error().contains("base64"));
    }

    @Test
    void multipartOneContentBlockListIsOneFilePartNotJsonText() {
        byte[] file = "hi".getBytes(StandardCharsets.UTF_8);
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "multipart",
                "bodyFrom", List.of("file")));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of(
                "file", List.of(Map.of(
                        "kind", "document",
                        "mime_type", "text/plain",
                        "source", "base64",
                        "data", Base64.getEncoder().encodeToString(file),
                        "name", "hi.txt"))), 5);
        assertFalse(p.failed());
        String body = new String(p.bytes(), StandardCharsets.UTF_8);
        assertEquals(1, countDispName(body, "file"), body);
        assertTrue(body.contains("filename=\"hi.txt\""), body);
        assertTrue(body.contains("Content-Type: text/plain"), body);
        assertTrue(body.contains("\r\n\r\nhi\r\n"), body);
        assertFalse(body.contains("\"kind\""), body);
        assertFalse(body.contains("[{"), body);
    }

    @Test
    void multipartTwoContentBlockListEmitsTwoFileParts() {
        byte[] a = "aaa".getBytes(StandardCharsets.UTF_8);
        byte[] b = "bbb".getBytes(StandardCharsets.UTF_8);
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "multipart",
                "bodyFrom", List.of("file")));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of(
                "file", List.of(
                        Map.of("kind", "document", "mime_type", "text/plain", "source", "base64",
                                "data", Base64.getEncoder().encodeToString(a), "name", "a.txt"),
                        Map.of("kind", "document", "mime_type", "text/csv", "source", "base64",
                                "data", Base64.getEncoder().encodeToString(b), "name", "b.csv"))), 5);
        assertFalse(p.failed());
        String body = new String(p.bytes(), StandardCharsets.UTF_8);
        assertEquals(2, countDispName(body, "file"), body);
        assertTrue(body.contains("filename=\"a.txt\""), body);
        assertTrue(body.contains("filename=\"b.csv\""), body);
        assertTrue(body.contains("\r\n\r\naaa\r\n"), body);
        assertTrue(body.contains("\r\n\r\nbbb\r\n"), body);
        assertTrue(body.contains("Content-Type: text/plain"), body);
        assertTrue(body.contains("Content-Type: text/csv"), body);
    }

    @Test
    void multipartEmptyFileListEmitsNoFilePart() {
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "multipart",
                "bodyFrom", List.of("file")));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of("file", List.of()), 5);
        assertFalse(p.failed());
        String body = new String(p.bytes(), StandardCharsets.UTF_8);
        assertEquals(0, countDispName(body, "file"), body);
        assertFalse(body.contains("[]"), body);
    }

    @Test
    void multipartInvalidBase64InListIsSameErrorAsSingleMap() {
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "multipart",
                "bodyFrom", List.of("file")));
        OfficialApiBody.Prepared single = OfficialApiBody.encode(op,
                Map.of("file", Map.of("dataBase64", "!!!!")), 5);
        OfficialApiBody.Prepared listed = OfficialApiBody.encode(op,
                Map.of("file", List.of(Map.of("dataBase64", "!!!!"))), 5);
        assertTrue(single.failed());
        assertTrue(listed.failed());
        assertEquals(single.error(), listed.error());
        assertTrue(listed.error().contains("base64"));
    }

    @Test
    void rawDecodesFirstContentBlockInFileList() {
        byte[] file = "s3-bytes".getBytes(StandardCharsets.UTF_8);
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "PUT",
                "bodyFormat", "raw",
                "bodyParam", "file",
                "contentType", "application/octet-stream"));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of(
                "file", List.of(Map.of(
                        "kind", "document",
                        "mime_type", "application/octet-stream",
                        "source", "base64",
                        "name", "obj.bin",
                        "data", Base64.getEncoder().encodeToString(file)))), 5);
        assertFalse(p.failed());
        assertEquals("application/octet-stream", p.contentType());
        assertArrayEquals(file, p.bytes());
    }

    @Test
    void rawFileBytesRejectsInlineRef() {
        OfficialApiSpec.Operation op = op(Map.of(
                "method", "POST",
                "bodyFormat", "raw",
                "bodyParam", "file",
                "fileBytes", true,
                "contentType", "application/octet-stream"));
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op,
                Map.of("file", "nonexistent-ref-12345"), 5);
        assertTrue(p.failed());
        assertTrue(p.error().contains("/sandbox/files"), p.error());
    }

    private static int countDispName(String body, String partName) {
        String needle = "name=\"" + partName + "\"";
        int n = 0;
        int i = 0;
        while ((i = body.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}
