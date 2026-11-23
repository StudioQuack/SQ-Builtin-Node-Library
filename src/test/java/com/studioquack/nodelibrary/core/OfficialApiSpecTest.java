package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec parsing: defaults, auth normalization, operation lookup. */
class OfficialApiSpecTest {

    @Test
    void parsesFullSpec() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("acme", Map.of(
                "baseUrl", "https://api.acme.test/v1",
                "defaultTimeout", 10,
                "timeoutConfigKey", "timeout_s",
                "auth", Map.of(
                        "type", "Bearer",
                        "header", "X-Auth",
                        "prefix", "Token ",
                        "configKey", "secret_key",
                        "userKey", "user"),
                "operations", Map.of(
                        "get-thing", Map.of(
                                "method", "get",
                                "path", "/things/{id}",
                                "required", List.of("id"),
                                "queryFrom", List.of("expand"),
                                "bodyFrom", List.of(),
                                "contentType", "application/json"),
                        "listen", Map.of("kind", "ingress"))));

        assertEquals("acme", spec.key);
        assertEquals("https://api.acme.test/v1", spec.baseUrl);
        assertEquals("bearer", spec.authType);
        assertEquals("X-Auth", spec.authHeader);
        assertEquals("Token ", spec.authPrefix);
        assertEquals("secret_key", spec.authConfigKey);
        assertEquals("user", spec.authUserKey);
        assertEquals("timeout_s", spec.timeoutConfigKey);
        assertEquals(10, spec.defaultTimeout);
        assertEquals(2, spec.operations.size());
        assertTrue(spec.placeholderDefaults.isEmpty());

        OfficialApiSpec.Operation op = spec.operation("get-thing");
        assertNotNull(op);
        assertEquals("GET", op.method);
        assertEquals("http", op.kind);
        assertEquals("/things/{id}", op.path);
        assertEquals(List.of("id"), op.required);
        assertEquals(List.of("expand"), op.queryFrom);
        assertTrue(op.bodyFrom.isEmpty());
        assertEquals("ingress", spec.operation("listen").kind);
        assertNull(op.stream);
    }

    @Test
    void parsesStreamBlockWithDefaults() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("llm", Map.of(
                "baseUrl", "https://api.llm.test",
                "operations", Map.of("chat", Map.of(
                        "method", "POST",
                        "path", "/v1/chat",
                        "stream", Map.of("format", "openai-sse")))));
        OfficialApiSpec.StreamBlock s = spec.operation("chat").stream;
        assertNotNull(s);
        assertEquals("openai-sse", s.format);
        assertEquals("stream", s.requestFlag);
        assertEquals(Boolean.TRUE, s.requestValue);
        assertEquals("llm_output", s.port);
        assertTrue(s.query.isEmpty());
        assertTrue(s.extraHeaders.isEmpty());
        assertEquals("", s.path);
    }

    @Test
    void parsesResponsesSseFormat() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("pplx", Map.of(
                "baseUrl", "https://api.perplexity.ai",
                "operations", Map.of("chat", Map.of(
                        "stream", Map.of("format", "responses-sse")))));
        OfficialApiSpec.StreamBlock s = spec.operation("chat").stream;
        assertNotNull(s);
        assertEquals("responses-sse", s.format);
        assertEquals("stream", s.requestFlag);
        assertEquals(Boolean.TRUE, s.requestValue);
    }

    @Test
    void unknownOrMissingFormatYieldsNullStream() {
        OfficialApiSpec missing = OfficialApiSpec.fromMap("x", Map.of(
                "baseUrl", "https://x.test",
                "operations", Map.of("chat", Map.of("stream", Map.of("requestFlag", "stream")))));
        assertNull(missing.operation("chat").stream);

        OfficialApiSpec unknown = OfficialApiSpec.fromMap("x", Map.of(
                "baseUrl", "https://x.test",
                "operations", Map.of("chat", Map.of("stream", Map.of("format", "nope-sse")))));
        assertNull(unknown.operation("chat").stream);
    }

    @Test
    void streamPathQueryAndHeadersAreCaptured() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("g", Map.of(
                "baseUrl", "https://g.test",
                "operations", Map.of("chat", Map.of(
                        "stream", Map.of(
                                "format", "gemini-sse",
                                "path", "/v1/{model}:streamGenerateContent",
                                "query", Map.of("alt", "sse"),
                                "extraHeaders", Map.of("anthropic-version", "2023-06-01"),
                                "port", "llm_output")))));
        OfficialApiSpec.StreamBlock s = spec.operation("chat").stream;
        assertEquals("/v1/{model}:streamGenerateContent", s.path);
        assertEquals("sse", s.query.get("alt"));
        assertEquals("2023-06-01", s.extraHeaders.get("anthropic-version"));
    }

    @Test
    void appliesDefaultsWhenFieldsMissing() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("bare", Map.of(
                "baseUrl", "https://api.bare.test",
                "operations", Map.of("ping", Map.of())));
        assertEquals("", spec.authType); // no auth block → empty, treated as none by the node
        assertEquals("Authorization", spec.authHeader);
        assertEquals("", spec.authPrefix);
        assertEquals("api_key", spec.authConfigKey);
        assertEquals("timeout_seconds", spec.timeoutConfigKey);
        assertEquals(25, spec.defaultTimeout);
        OfficialApiSpec.Operation op = spec.operation("ping");
        assertEquals("GET", op.method);
        assertEquals("http", op.kind);
        assertEquals("application/json", op.contentType);
        assertEquals("json", op.bodyFormat);
        assertEquals("", op.bodyParam);
        assertEquals("", op.baseUrl);
        assertTrue(op.extraHeaders.isEmpty());
        assertTrue(spec.extraHeaders.isEmpty());
        assertEquals("password", spec.basicKeyPosition);
        assertEquals("", spec.secretKeyConfigKey);
        assertNull(spec.sigv4);
    }

    @Test
    void parsesNewOptionalFields() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("aws", Map.of(
                "baseUrl", "https://api.example.test",
                "extraHeaders", Map.of("X-Top", "{token}", "X-Date", "{$now:rfc1123}"),
                "auth", Map.of(
                        "type", "aws-sigv4",
                        "configKey", "access_key_id",
                        "secretKeyConfigKey", "secret_access_key",
                        "basicKeyPosition", "username"),
                "sigv4", Map.of(
                        "service", "execute-api",
                        "regionConfigKey", "region",
                        "sessionTokenConfigKey", "session_token"),
                "operations", Map.of("export", Map.of(
                        "method", "POST",
                        "path", "/export",
                        "baseUrl", "https://export.example.test",
                        "bodyFormat", "form",
                        "bodyFrom", List.of("from_date"),
                        "extraHeaders", Map.of("X-Op", "1")))));
        assertEquals("aws-sigv4", spec.authType);
        assertEquals("access_key_id", spec.authConfigKey);
        assertEquals("secret_access_key", spec.secretKeyConfigKey);
        assertEquals("username", spec.basicKeyPosition);
        assertEquals("{token}", spec.extraHeaders.get("X-Top"));
        assertNotNull(spec.sigv4);
        assertEquals("execute-api", spec.sigv4.service);
        assertEquals("region", spec.sigv4.regionConfigKey);
        assertEquals("session_token", spec.sigv4.sessionTokenConfigKey);
        OfficialApiSpec.Operation op = spec.operation("export");
        assertEquals("https://export.example.test", op.baseUrl);
        assertEquals("form", op.bodyFormat);
        assertEquals("1", op.extraHeaders.get("X-Op"));
    }

    @Test
    void awsSigv4DefaultsSecretKeyConfigKey() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("aws", Map.of(
                "baseUrl", "https://x.test",
                "auth", Map.of("type", "aws-sigv4", "configKey", "access_key_id"),
                "sigv4", Map.of("serviceConfigKey", "service"),
                "operations", Map.of("ping", Map.of())));
        assertEquals("secret_access_key", spec.secretKeyConfigKey);
        assertEquals("service", spec.sigv4.serviceConfigKey);
        assertEquals("region", spec.sigv4.regionConfigKey);
        assertEquals("", spec.sigv4.sessionTokenConfigKey);
    }

    @Test
    void rejectsMalformedOptionalFieldsByOmittingThem() {
        Map<String, Object> op = new java.util.LinkedHashMap<>();
        op.put("bodyFormat", "xml");
        op.put("extraHeaders", List.of("not-a-map"));
        op.put("bodyParam", 12);
        OfficialApiSpec spec = OfficialApiSpec.fromMap("x", Map.of(
                "baseUrl", "https://x.test",
                "extraHeaders", "nope",
                "auth", Map.of("type", "basic", "basicKeyPosition", "both"),
                "sigv4", "nope",
                "operations", Map.of("ping", op)));
        assertTrue(spec.extraHeaders.isEmpty(), "non-object extraHeaders rejected");
        assertEquals("password", spec.basicKeyPosition, "unknown basicKeyPosition → password");
        assertNull(spec.sigv4, "non-object sigv4 rejected");
        assertEquals("json", spec.operation("ping").bodyFormat, "unknown bodyFormat → json");
        assertTrue(spec.operation("ping").extraHeaders.isEmpty());
        assertEquals("", spec.operation("ping").bodyParam, "non-string bodyParam rejected");
    }

    @Test
    void extraHeadersSkipNestedValues() {
        Map<String, Object> headers = new java.util.LinkedHashMap<>();
        headers.put("X-Ok", "1");
        headers.put("X-Bad", Map.of("nested", true));
        OfficialApiSpec spec = OfficialApiSpec.fromMap("x", Map.of(
                "baseUrl", "https://x.test",
                "extraHeaders", headers,
                "operations", Map.of("ping", Map.of())));
        assertEquals("1", spec.extraHeaders.get("X-Ok"));
        assertFalse(spec.extraHeaders.containsKey("X-Bad"));
    }

    @Test
    void operationLookupIsCaseInsensitive() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("case", Map.of(
                "baseUrl", "https://x.test",
                "operations", Map.of("ListItems", Map.of())));
        assertNotNull(spec.operation("listitems"));
        assertNotNull(spec.operation("LISTITEMS"));
        assertNull(spec.operation("other"));
        assertNull(spec.operation(null));
    }

    @Test
    void skipsMalformedOperationsAndBadTimeout() {
        Map<String, Object> ops = new java.util.LinkedHashMap<>();
        ops.put("good", Map.of("method", "POST"));
        ops.put("bad", "not-a-map");
        ops.put("  ", Map.of());
        OfficialApiSpec spec = OfficialApiSpec.fromMap("t", Map.of(
                "baseUrl", "https://x.test",
                "defaultTimeout", -5,
                "operations", ops));
        assertEquals(1, spec.operations.size());
        assertNotNull(spec.operation("good"));
        assertEquals(25, spec.defaultTimeout); // non-positive falls back
    }

    @Test
    void loadReturnsNullForUnknownKey() {
        assertNull(OfficialApiSpec.load(null));
        assertNull(OfficialApiSpec.load(""));
        assertNull(OfficialApiSpec.load("no-such-node-key-zzz"));
    }

    @Test
    void loadReadsPackagedSpec() {
        // nodes/google-books/api_spec.json is packaged at classpath /nodes by pom resources.
        OfficialApiSpec spec = OfficialApiSpec.load("google-books");
        assertNotNull(spec, "packaged api_spec.json must load");
        assertTrue(spec.baseUrl.startsWith("https://"));
        assertTrue(!spec.operations.isEmpty());
    }

    @Test
    void stabilityAiGenerateUsesMultipartBodyFormat() {
        OfficialApiSpec spec = OfficialApiSpec.load("stability-ai");
        assertNotNull(spec, "packaged stability-ai api_spec.json must load");
        for (String name : List.of("generate", "upscale", "edit", "control")) {
            OfficialApiSpec.Operation op = spec.operation(name);
            assertNotNull(op, name);
            assertEquals("multipart", op.bodyFormat, name);
        }
        OfficialApiSpec.Operation gen = spec.operation("generate");
        OfficialApiBody.Prepared prepared = OfficialApiBody.encode(gen, Map.of("prompt", "red circle"), 30);
        assertNotNull(prepared.contentType());
        assertTrue(prepared.contentType().startsWith("multipart/form-data; boundary="),
                prepared.contentType());
    }

    @Test
    void jiraAttachmentAddIsMultipartWithAtlassianToken() {
        OfficialApiSpec spec = OfficialApiSpec.load("jira");
        assertNotNull(spec, "packaged jira api_spec.json must load");
        OfficialApiSpec.Operation op = spec.operation("attachment_add");
        assertNotNull(op);
        assertEquals("multipart", op.bodyFormat);
        assertEquals(List.of("file"), op.bodyFrom);
        assertTrue(op.required.contains("file"));
        assertEquals("no-check", op.extraHeaders.get("X-Atlassian-Token"));
    }
}
