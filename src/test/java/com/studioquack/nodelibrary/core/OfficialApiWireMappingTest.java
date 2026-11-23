package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code bodyMap}, {@code queryMap} and {@code mime} operation blocks. */
class OfficialApiWireMappingTest {

    private static OfficialApiSpec spec(Map<String, Object> op) {
        return OfficialApiSpec.fromMap("t", Map.of(
                "baseUrl", "https://x.test",
                "operations", Map.of("do", op)));
    }

    private static String text(OfficialApiBody.Prepared p) {
        return new String(p.bytes(), StandardCharsets.UTF_8);
    }

    @Test
    void bodyMapNestsJsonFields() {
        OfficialApiSpec.Operation op = spec(Map.of("method", "POST",
                "bodyFrom", List.of("prompt", "temperature", "max_tokens"),
                "bodyMap", Map.of("temperature", "generationConfig.temperature",
                        "max_tokens", "generationConfig.maxOutputTokens"))).operation("do");
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op,
                Map.of("prompt", "hi", "temperature", 0.2, "max_tokens", 50), 5);
        Map<String, Object> body = Json.asMap(Json.parse(text(p)));
        assertEquals("hi", body.get("prompt"));
        Map<String, Object> generation = Json.asMap(body.get("generationConfig"));
        assertEquals("0.2", String.valueOf(generation.get("temperature")));
        assertEquals("50", String.valueOf(generation.get("maxOutputTokens")));
        assertFalse(body.containsKey("temperature"));
    }

    @Test
    void bodyMapBuildsArraysFromIndexedPaths() {
        OfficialApiSpec.Operation op = spec(Map.of("method", "POST",
                "bodyFrom", List.of("to", "subject", "text"),
                "bodyMap", Map.of("to", "personalizations[0].to[0].email",
                        "text", "content[0].value"))).operation("do");
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op,
                Map.of("to", "ada@example.com", "subject", "s", "text", "hi"), 5);
        Map<String, Object> body = Json.asMap(Json.parse(text(p)));
        assertEquals("s", body.get("subject"));
        assertEquals("[{to=[{email=ada@example.com}]}]", String.valueOf(body.get("personalizations")));
        assertEquals("[{value=hi}]", String.valueOf(body.get("content")));
        assertFalse(body.containsKey("to"));
    }

    @Test
    void bodyMapRootMergesAnObjectAndSendsAnArrayAsIs() {
        OfficialApiSpec.Operation op = spec(Map.of("method", "POST",
                "bodyFrom", List.of("record", "id"),
                "bodyMap", Map.of("record", "$"))).operation("do");
        Map<String, Object> merged = Json.asMap(Json.parse(text(OfficialApiBody.encode(op,
                Map.of("record", "{\"name\":\"n\",\"id\":\"ignored\"}", "id", "7"), 5))));
        assertEquals("n", merged.get("name"));
        assertEquals("7", merged.get("id"));
        assertFalse(merged.containsKey("record"));

        assertEquals("[{\"eventType\":\"a\"}]", text(OfficialApiBody.encode(op,
                Map.of("record", List.of(Map.of("eventType", "a"))), 5)));
    }

    @Test
    void bodyMapMergesIntoAnEnvelopeGivenAsJsonText() {
        OfficialApiSpec.Operation op = spec(Map.of("method", "POST",
                "bodyFrom", List.of("properties", "dealname"),
                "bodyMap", Map.of("dealname", "properties.dealname"))).operation("do");
        Map<String, Object> body = Json.asMap(Json.parse(text(OfficialApiBody.encode(op,
                Map.of("properties", "{\"custom_score\":\"9\"}", "dealname", "Q4"), 5))));
        assertEquals("{custom_score=9, dealname=Q4}", String.valueOf(body.get("properties")));
    }

    @Test
    void bodyMapRenamesFormFields() {
        OfficialApiSpec.Operation op = spec(Map.of("method", "POST", "bodyFormat", "form",
                "bodyFrom", List.of("order_id"),
                "bodyMap", Map.of("order_id", "metadata[order_id]"))).operation("do");
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of("order_id", "42"), 5);
        assertEquals("metadata%5Border_id%5D=42", text(p));
    }

    @Test
    void queryMapRenamesQueryParameters() {
        OfficialApiSpec s = spec(Map.of("method", "GET", "path", "/items",
                "queryFrom", List.of("page_size"),
                "queryMap", Map.of("page_size", "$top")));
        String url = new OfficialApiNode("tid", s).buildUrl(s.operation("do"),
                Map.of("page_size", "10"), Map.of(), null);
        assertTrue(url.endsWith("/items?%24top=10") || url.endsWith("/items?$top=10"), url);
    }

    @Test
    void mimeBuildsAnEncodedMessageFromFields() {
        OfficialApiSpec.Operation op = spec(Map.of("method", "POST",
                "bodyFrom", List.of("raw", "threadId"),
                "mime", Map.of("target", "raw", "encoding", "base64url"))).operation("do");
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of(
                "to", List.of("ada@example.com", "bob@example.com"),
                "cc", "carol@example.com",
                "subject", "Merhaba",
                "text", "plain body",
                "html", "<p>rich body</p>",
                "attachments", List.of(Map.of("kind", "file", "source", "base64",
                        "data", Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8)),
                        "name", "hi.txt", "mime_type", "text/plain"))), 5);
        assertFalse(p.failed(), p.error());
        Map<String, Object> body = Json.asMap(Json.parse(text(p)));
        String message = new String(Base64.getUrlDecoder().decode(String.valueOf(body.get("raw"))),
                StandardCharsets.UTF_8);
        assertTrue(message.contains("To: ada@example.com, bob@example.com"), message);
        assertTrue(message.contains("Cc: carol@example.com"), message);
        assertTrue(message.contains("Subject: Merhaba"), message);
        assertTrue(message.contains("multipart/alternative"), message);
        assertTrue(message.contains("filename=hi.txt"), message);
        assertFalse(body.containsKey("to"));
    }

    @Test
    void mimeKeepsACallerSuppliedTarget() {
        OfficialApiSpec.Operation op = spec(Map.of("method", "POST", "bodyFrom", List.of("raw"),
                "mime", Map.of("target", "raw"))).operation("do");
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of("raw", "prebuilt"), 5);
        assertEquals("{\"raw\":\"prebuilt\"}", text(p));
    }

    @Test
    void mimeWithoutRecipientFails() {
        OfficialApiSpec.Operation op = spec(Map.of("method", "POST", "bodyFrom", List.of("raw"),
                "mime", Map.of("target", "raw"))).operation("do");
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, Map.of("subject", "x"), 5);
        assertTrue(p.failed());
        assertEquals("to is required to build the message", p.error());
    }

    @Test
    void mimeFieldsFallBackToConfigAndNestUnderBodyMap() {
        OfficialApiSpec.Operation op = spec(Map.of("method", "POST", "bodyFrom", List.of("raw", "threadId"),
                "bodyMap", Map.of("raw", "message.raw", "threadId", "message.threadId"),
                "mime", Map.of("target", "raw"))).operation("do");
        Map<String, Object> in = OfficialApiNode.underlayBodyConfig(op,
                Map.of("to", "ada@example.com", "subject", "s", "text", "t", "threadId", "th1"),
                Map.of("from", "sender@example.com"), Map.of());
        OfficialApiBody.Prepared p = OfficialApiBody.encode(op, in, 5);
        assertFalse(p.failed(), p.error());
        Map<String, Object> message = Json.asMap(Json.asMap(Json.parse(text(p))).get("message"));
        assertEquals("th1", message.get("threadId"));
        String mail = new String(Base64.getUrlDecoder().decode(String.valueOf(message.get("raw"))),
                StandardCharsets.UTF_8);
        assertTrue(mail.contains("From: sender@example.com"), mail);
    }
}
