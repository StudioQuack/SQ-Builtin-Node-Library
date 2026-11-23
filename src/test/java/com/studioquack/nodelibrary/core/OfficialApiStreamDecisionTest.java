package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Streaming decision + URL/body/header planning with no HTTP.
 *
 * <p>Seam: {@link OfficialApiStream#enabled} / {@link OfficialApiStream#applyRequestFlag}
 * (pure) and {@link OfficialApiNode#planStream} (URL/body/headers, still no socket).
 * {@link DataSupport#exchange} is not stubbed.
 */
class OfficialApiStreamDecisionTest {

    private static OfficialApiSpec.StreamBlock block() {
        return OfficialApiSpec.fromMap("t", Map.of(
                "baseUrl", "https://api.example.test",
                "operations", Map.of("chat", Map.of(
                        "method", "POST",
                        "path", "/v1/chat",
                        "bodyFrom", List.of("model", "messages"),
                        "stream", Map.of("format", "openai-sse"))))).operation("chat").stream;
    }

    @Test
    void noBlockNeverStreams() {
        assertFalse(OfficialApiStream.enabled(null, Map.of(), Map.of(), Map.of()));
        assertFalse(OfficialApiStream.enabled(null, Map.of("stream", true), Map.of(), Map.of()));
    }

    @Test
    void defaultOnWhenBlockPresent() {
        assertTrue(OfficialApiStream.enabled(block(), Map.of(), Map.of(), Map.of()));
    }

    @Test
    void inputFalseOverridesDefaultOn() {
        assertFalse(OfficialApiStream.enabled(block(), Map.of("stream", false), Map.of(), Map.of()));
        assertFalse(OfficialApiStream.enabled(block(), Map.of("streaming", "off"), Map.of(), Map.of()));
    }

    @Test
    void inputTrueOverridesConfigFalse() {
        assertTrue(OfficialApiStream.enabled(block(), Map.of("stream", true), Map.of(),
                Map.of("stream", false)));
    }

    @Test
    void llmInputFalseOverridesDefault() {
        assertFalse(OfficialApiStream.enabled(block(), Map.of(), Map.of("streaming", "no"), Map.of()));
    }

    @Test
    void configFalseWhenInputUnset() {
        assertFalse(OfficialApiStream.enabled(block(), Map.of(), Map.of(), Map.of("stream", false)));
    }

    @Test
    void boolishStreamStringIsTrue() {
        assertEquals(Boolean.TRUE, OfficialApiStream.boolish("stream"));
        assertEquals(Boolean.TRUE, OfficialApiStream.boolish("streaming"));
        assertEquals(Boolean.FALSE, OfficialApiStream.boolish("non-stream"));
        assertEquals(Boolean.FALSE, OfficialApiStream.boolish("sync"));
    }

    @Test
    void applyRequestFlagIndependentOfBodyFrom() {
        OfficialApiSpec.StreamBlock b = block();
        Map<String, Object> body = OfficialApiStream.applyRequestFlag(Map.of("messages", List.of()), b);
        assertEquals(Boolean.TRUE, body.get("stream"));
        assertTrue(body.containsKey("messages"));
    }

    @Test
    void planStreamBuildsUrlHeadersAndFlaggedBody() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("t", Map.of(
                "baseUrl", "https://api.example.test",
                "auth", Map.of("type", "none"),
                "operations", Map.of("chat", Map.of(
                        "method", "POST",
                        "path", "/v1/chat",
                        "bodyFrom", List.of("model", "messages"),
                        "stream", Map.of(
                                "format", "openai-sse",
                                "extraHeaders", Map.of("X-Test", "1"),
                                "query", Map.of("alt", "sse"),
                                "path", "/v1/stream")))));
        OfficialApiNode node = new OfficialApiNode("tid", spec);
        OfficialApiNode.StreamPlan plan = node.planStream(spec.operation("chat"),
                Map.of("model", "m", "messages", List.of(Map.of("role", "user", "content", "hi"))),
                Map.of());
        assertTrue(plan.stream());
        assertTrue(plan.url().startsWith("https://api.example.test/v1/stream"));
        assertTrue(plan.url().contains("alt=sse"), plan.url());
        assertEquals("1", plan.headers().get("X-Test"));
        assertEquals("text/event-stream", plan.headers().get("Accept"));
        assertTrue(plan.body().contains("\"stream\":true"), plan.body());
        assertEquals("llm_output", plan.port());
    }

    @Test
    void planStreamOffWhenInputDisables() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("t", Map.of(
                "baseUrl", "https://api.example.test",
                "operations", Map.of("chat", Map.of(
                        "path", "/v1/chat",
                        "stream", Map.of("format", "openai-sse")))));
        OfficialApiNode node = new OfficialApiNode("tid", spec);
        OfficialApiNode.StreamPlan plan = node.planStream(spec.operation("chat"),
                Map.of("stream", false), Map.of());
        assertFalse(plan.stream());
    }

    @Test
    void geminiStreamPathReplacesAndMergesQuery() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("gemini", Map.of(
                "baseUrl", "https://generativelanguage.googleapis.com",
                "auth", Map.of("type", "none"),
                "operations", Map.of("chat", Map.of(
                        "method", "POST",
                        "path", "/v1beta/models/{model}:generateContent",
                        "stream", Map.of(
                                "format", "gemini-sse",
                                "path", "/v1beta/models/{model}:streamGenerateContent",
                                "query", Map.of("alt", "sse"))))));
        OfficialApiNode node = new OfficialApiNode("tid", spec);
        OfficialApiNode.StreamPlan plan = node.planStream(spec.operation("chat"),
                Map.of("model", "gemini-2.5-flash"), Map.of());
        assertTrue(plan.url().contains(":streamGenerateContent"), plan.url());
        assertFalse(plan.url().contains(":generateContent?"), plan.url());
        assertTrue(plan.url().contains("alt=sse"), plan.url());
    }

    @Test
    void planStreamGeminiMapsV2MessagesToContents() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("gemini", Map.of(
                "baseUrl", "https://generativelanguage.googleapis.com",
                "auth", Map.of("type", "none"),
                "operations", Map.of("chat", Map.of(
                        "method", "POST",
                        "path", "/v1beta/models/{model}:generateContent",
                        "bodyFrom", List.of("messages", "temperature", "tools"),
                        "stream", Map.of(
                                "format", "gemini-sse",
                                "path", "/v1beta/models/{model}:streamGenerateContent",
                                "query", Map.of("alt", "sse"))))));
        OfficialApiNode node = new OfficialApiNode("tid", spec);
        OfficialApiNode.StreamPlan plan = node.planStream(spec.operation("chat"),
                Map.of("model", "gemini-2.5-flash",
                        "messages", List.of(
                                Map.of("role", "system", "content", "sys"),
                                Map.of("role", "user", "content", "hi")),
                        "temperature", 0.2,
                        "tools", List.of(Map.of("name", "lookup", "request", Map.of("type", "object")))),
                Map.of());
        assertTrue(plan.stream());
        assertTrue(plan.body().contains("\"contents\""), plan.body());
        assertTrue(plan.body().contains("\"systemInstruction\""), plan.body());
        assertTrue(plan.body().contains("\"generationConfig\""), plan.body());
        assertTrue(plan.body().contains("\"functionDeclarations\""), plan.body());
        assertFalse(plan.body().contains("\"messages\""), plan.body());
        assertTrue(plan.body().contains("\"stream\":true"), plan.body());
    }

    @Test
    void planStreamResponsesMapsV2MessagesToInput() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("pplx", Map.of(
                "baseUrl", "https://api.perplexity.ai",
                "auth", Map.of("type", "none"),
                "operations", Map.of("chat", Map.of(
                        "method", "POST",
                        "path", "/v1/agent",
                        "bodyFrom", List.of("messages", "input", "temperature", "tools", "max_tokens"),
                        "stream", Map.of("format", "responses-sse")))));
        OfficialApiNode node = new OfficialApiNode("tid", spec);
        OfficialApiNode.StreamPlan plan = node.planStream(spec.operation("chat"),
                Map.of("messages", List.of(
                                Map.of("role", "system", "content", "sys"),
                                Map.of("role", "user", "content", "hi")),
                        "temperature", 0.2,
                        "max_tokens", 64,
                        "tools", List.of(Map.of("name", "lookup", "request", Map.of("type", "object")))),
                Map.of());
        assertTrue(plan.stream());
        assertTrue(plan.body().contains("\"input\""), plan.body());
        assertTrue(plan.body().contains("\"instructions\""), plan.body());
        assertTrue(plan.body().contains("\"input_text\""), plan.body());
        assertTrue(plan.body().contains("\"max_output_tokens\""), plan.body());
        assertTrue(plan.body().contains("\"type\":\"function\""), plan.body().replace(" ", ""));
        assertFalse(plan.body().contains("\"messages\""), plan.body());
        assertTrue(plan.body().contains("\"stream\":true"), plan.body());
    }
}
