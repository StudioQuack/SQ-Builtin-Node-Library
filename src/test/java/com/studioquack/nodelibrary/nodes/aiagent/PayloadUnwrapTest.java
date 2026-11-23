package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PayloadUnwrap: assistant-payload detection, envelope recursion, empty-transport detection, error helpers. */
class PayloadUnwrapTest {

    private static Object nestResult(int levels) {
        Object v = Map.of("role", "assistant", "content", "x");
        for (int i = 0; i < levels; i++) {
            v = Map.of("result", v);
        }
        return v;
    }

    // ---------- payloadError / payloadErrorType ----------

    @Test
    void modelNodeErrorEnvelopeExposesItsMessageAndType() {
        // The shape LocalLlmNode returns for a turn that generated nothing: the text lives on
        // `error`, NOT on `content`, which is why finalText alone reported an empty answer.
        Map<String, Object> payload = Map.of(
                "error", "LLM returned an empty response — no text, tool calls, or thoughts",
                "type", "EmptyResponseError");
        assertEquals("LLM returned an empty response — no text, tool calls, or thoughts",
                PayloadUnwrap.payloadError(payload));
        assertEquals("EmptyResponseError", PayloadUnwrap.payloadErrorType(payload));
        assertTrue(PayloadUnwrap.finalText(payload).isEmpty());
    }

    @Test
    void payloadWithoutAnErrorReportsNone() {
        assertEquals("", PayloadUnwrap.payloadError(Map.of("role", "assistant")));
        assertEquals("", PayloadUnwrap.payloadError(null));
        assertEquals("Error", PayloadUnwrap.payloadErrorType(Map.of("error", "boom")));
    }

    // ---------- looksLikeAssistantPayload ----------

    @Test
    void assistantRoleIsAlwaysClassified() {
        assertTrue(PayloadUnwrap.looksLikeAssistantPayload(Map.of("role", "assistant")));
        assertTrue(PayloadUnwrap.looksLikeAssistantPayload(
                Map.of("role", "assistant", "result", Map.of())));
    }

    @Test
    void assistantKeysClassifyWithoutRole() {
        assertTrue(PayloadUnwrap.looksLikeAssistantPayload(Map.of("tool_calls", List.of())));
        assertTrue(PayloadUnwrap.looksLikeAssistantPayload(Map.of("error", "e")));
        assertTrue(PayloadUnwrap.looksLikeAssistantPayload(Map.of("finish_reason", "stop")));
        assertTrue(PayloadUnwrap.looksLikeAssistantPayload(Map.of("thinking", "t")));
        assertTrue(PayloadUnwrap.looksLikeAssistantPayload(Map.of("outputFiles", List.of())));
    }

    @Test
    void bareContentClassifiesButEnvelopeKeysVeto() {
        assertTrue(PayloadUnwrap.looksLikeAssistantPayload(Map.of("role", "user", "content", "x")));
        assertFalse(PayloadUnwrap.looksLikeAssistantPayload(
                Map.of("content", "x", "result", Map.of())));
        assertFalse(PayloadUnwrap.looksLikeAssistantPayload(
                Map.of("content", "x", "output", "y")));
        assertFalse(PayloadUnwrap.looksLikeAssistantPayload(Map.of()));
        assertFalse(PayloadUnwrap.looksLikeAssistantPayload("str"));
        assertFalse(PayloadUnwrap.looksLikeAssistantPayload(null));
    }

    // ---------- findAssistantPayload ----------

    @Test
    void findReturnsDirectAssistantMap() {
        Map<String, Object> m = Map.of("role", "assistant", "content", "x");
        assertSame(m, PayloadUnwrap.findAssistantPayload(m));
    }

    @Test
    void findUnwrapsEnvelopesRecursively() {
        assertEquals(Map.of("role", "assistant"),
                PayloadUnwrap.findAssistantPayload(Map.of("result", Map.of("role", "assistant"))));
        Object deep = Map.of("response", Map.of("output", Map.of("tool_calls", List.of(1))));
        assertEquals(Map.of("tool_calls", List.of(1)), PayloadUnwrap.findAssistantPayload(deep));
    }

    @Test
    void findPrefersEnvelopeDescentOverBareContent() {
        Object raw = Map.of("content", "outer", "result", Map.of("role", "assistant", "content", "inner"));
        assertEquals(Map.of("role", "assistant", "content", "inner"),
                PayloadUnwrap.findAssistantPayload(raw));
    }

    @Test
    void findUnwrapsListsAndParsesJsonStrings() {
        Map<String, Object> assistant = Map.of("role", "assistant", "content", "x");
        assertEquals(assistant, PayloadUnwrap.findAssistantPayload(List.of(assistant)));
        assertEquals(assistant, PayloadUnwrap.findAssistantPayload(List.of(Map.of(), assistant)));
        assertEquals(assistant,
                PayloadUnwrap.findAssistantPayload("{\"role\":\"assistant\",\"content\":\"x\"}"));
        assertEquals(assistant,
                PayloadUnwrap.findAssistantPayload(Map.of("result", "{\"role\":\"assistant\",\"content\":\"x\"}")));
    }

    @Test
    void findReturnsNullWhenNothingMatches() {
        assertNull(PayloadUnwrap.findAssistantPayload(null));
        assertNull(PayloadUnwrap.findAssistantPayload(List.of()));
        assertNull(PayloadUnwrap.findAssistantPayload(Map.of("foo", "bar")));
        assertNull(PayloadUnwrap.findAssistantPayload("plain"));
    }

    @Test
    void findHonorsDepthCap() {
        assertEquals(Map.of("role", "assistant", "content", "x"),
                PayloadUnwrap.findAssistantPayload(nestResult(10)));
        assertNull(PayloadUnwrap.findAssistantPayload(nestResult(11)));
    }

    // ---------- isEmptyExecuteResponse ----------

    @Test
    void emptyExecuteResponseDetectsRecursiveEmptiness() {
        assertTrue(PayloadUnwrap.isEmptyExecuteResponse(null));
        assertTrue(PayloadUnwrap.isEmptyExecuteResponse(""));
        assertTrue(PayloadUnwrap.isEmptyExecuteResponse("  "));
        assertTrue(PayloadUnwrap.isEmptyExecuteResponse(Map.of()));
        assertTrue(PayloadUnwrap.isEmptyExecuteResponse(List.of()));
        assertTrue(PayloadUnwrap.isEmptyExecuteResponse(
                java.util.Arrays.asList(null, Map.of(), List.of())));
    }

    @Test
    void emptyExecuteResponseRejectsRealPayloads() {
        assertFalse(PayloadUnwrap.isEmptyExecuteResponse("x"));
        assertFalse(PayloadUnwrap.isEmptyExecuteResponse(Map.of("a", 1)));
        assertFalse(PayloadUnwrap.isEmptyExecuteResponse(List.of(0)));
        assertFalse(PayloadUnwrap.isEmptyExecuteResponse(0));
        assertFalse(PayloadUnwrap.isEmptyExecuteResponse(false));
    }

    // ---------- describeRawShape ----------

    @Test
    void describeRawShapeRendersCompactShapes() {
        assertEquals("{'a': 1}", PayloadUnwrap.describeRawShape(Map.of("a", 1)));
        assertEquals("list[2, first=1]", PayloadUnwrap.describeRawShape(List.of(1, 2)));
        assertEquals("[]", PayloadUnwrap.describeRawShape(List.of()));
        assertEquals("'x'", PayloadUnwrap.describeRawShape("x"));
        assertEquals("null", PayloadUnwrap.describeRawShape(null));
        assertEquals("42", PayloadUnwrap.describeRawShape(42));
        assertEquals("true", PayloadUnwrap.describeRawShape(true));
    }

    @Test
    void describeRawShapeEscapesQuotesAndTruncatesLongStrings() {
        assertEquals("'it\\'s'", PayloadUnwrap.describeRawShape("it's"));
        String long121 = "x".repeat(121);
        String shape = PayloadUnwrap.describeRawShape(long121);
        assertTrue(shape.endsWith("...<121B>"));
        assertTrue(shape.startsWith("'xxxx"));
    }

    @Test
    void describeRawShapeCapsDepthAndKeyCount() {
        Map<String, Object> cur = new LinkedHashMap<>(Map.of("k", "v"));
        for (int i = 0; i < 4; i++) {
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("k", cur);
            cur = next;
        }
        assertTrue(PayloadUnwrap.describeRawShape(cur).contains("<String>"));

        Map<String, Object> wide = new LinkedHashMap<>();
        for (int i = 0; i < 15; i++) {
            wide.put("k" + i, i);
        }
        String shape = PayloadUnwrap.describeRawShape(wide);
        assertTrue(shape.contains("k11"));
        assertFalse(shape.contains("k12"));
    }

    // ---------- payloadIndicatesContextOverflow ----------

    @Test
    void contextOverflowMatchesKnownHints() {
        assertTrue(PayloadUnwrap.payloadIndicatesContextOverflow(Map.of("error", "context_length_exceeded")));
        assertTrue(PayloadUnwrap.payloadIndicatesContextOverflow(
                Map.of("error", "Maximum context length is 128k tokens")));
        assertTrue(PayloadUnwrap.payloadIndicatesContextOverflow(
                Map.of("finish_reason", "length", "content", "prompt is too long")));
        assertTrue(PayloadUnwrap.payloadIndicatesContextOverflow(
                Map.of("finish_reason", "error", "error", "Request too large")));
    }

    @Test
    void contextOverflowRejectsUnrelatedPayloads() {
        assertFalse(PayloadUnwrap.payloadIndicatesContextOverflow("not a map"));
        assertFalse(PayloadUnwrap.payloadIndicatesContextOverflow(Map.of("finish_reason", "stop", "error", "")));
        assertFalse(PayloadUnwrap.payloadIndicatesContextOverflow(Map.of("error", "rate limit hit")));
        assertFalse(PayloadUnwrap.payloadIndicatesContextOverflow(Map.of("finish_reason", "error")));
        assertFalse(PayloadUnwrap.payloadIndicatesContextOverflow(
                Map.of("finish_reason", "length", "content", "")));
    }

    // ---------- finalText / finalFiles ----------

    @Test
    void finalTextExtracts() {
        assertEquals("x", PayloadUnwrap.finalText(Map.of("content", " x ")));
        assertEquals("a\nb", PayloadUnwrap.finalText(
                Map.of("content", List.of(Map.of("text", "a"), Map.of("text", "b")))));
        assertEquals("raw", PayloadUnwrap.finalText("raw"));
        assertEquals("", PayloadUnwrap.finalText(null));
    }

    @Test
    void finalFilesExtractsOutputFilesList() {
        assertEquals(List.of("a", "b"), PayloadUnwrap.finalFiles(Map.of("output_files", List.of("a", "b"))));
        assertEquals(List.of("c"), PayloadUnwrap.finalFiles(Map.of("outputFiles", List.of("c"))));
        assertEquals(List.of("a", "b"), PayloadUnwrap.finalFiles(
                Map.of("output_files", List.of("a"), "outputFiles", List.of("a", "b"))));
        assertTrue(PayloadUnwrap.finalFiles(Map.of()).isEmpty());
        assertTrue(PayloadUnwrap.finalFiles(Map.of("output_files", "x")).isEmpty());
        assertTrue(PayloadUnwrap.finalFiles(null).isEmpty());
    }

    // ---------- findOutputFiles ----------

    @Test
    void findOutputFilesSearchesNestedEnvelopes() {
        assertEquals(List.of("f"), PayloadUnwrap.findOutputFiles(
                Map.of("result", Map.of("output_files", List.of("f")))));
        // JSON-string payloads are parsed before the search.
        assertEquals(List.of("f"), PayloadUnwrap.findOutputFiles(
                "{\"wrap\": {\"output_files\": [\"f\"]}}"));
        assertTrue(PayloadUnwrap.findOutputFiles(Map.of("output_files", List.of())).isEmpty());
        assertTrue(PayloadUnwrap.findOutputFiles(Map.of("a", Map.of("b", "c"))).isEmpty());
        assertTrue(PayloadUnwrap.findOutputFiles(null).isEmpty());
        assertEquals(List.of("g"), PayloadUnwrap.findOutputFiles(
                Map.of("outputFiles", List.of("g"))));
        assertEquals(List.of("a", "b"), PayloadUnwrap.findOutputFiles(
                Map.of("output_files", List.of("a"), "outputFiles", List.of("a", "b"))));
    }

    // ---------- errorAssistant / textAssistant ----------

    @Test
    void errorAssistantShape() {
        Map<String, Object> err = PayloadUnwrap.errorAssistant("msg", "error");
        assertEquals("assistant", err.get("role"));
        assertEquals(List.of(Map.of("type", "text", "text", "msg")), err.get("content"));
        assertEquals("error", err.get("finish_reason"));
        assertEquals("msg", err.get("error"));

        Map<String, Object> noReason = PayloadUnwrap.errorAssistant("m", null);
        assertFalse(noReason.containsKey("finish_reason"));

        Map<String, Object> nullMessage = PayloadUnwrap.errorAssistant(null, "error");
        assertEquals(List.of(Map.of("type", "text", "text", "")), nullMessage.get("content"));
        assertFalse(nullMessage.containsKey("error"));
    }

    @Test
    void textAssistantShape() {
        Map<String, Object> out = PayloadUnwrap.textAssistant("hi");
        assertEquals("assistant", out.get("role"));
        assertEquals(List.of(Map.of("type", "text", "text", "hi")), out.get("content"));
        assertFalse(out.containsKey("finish_reason"));
        assertFalse(out.containsKey("error"));
        assertEquals(List.of(Map.of("type", "text", "text", "")),
                PayloadUnwrap.textAssistant(null).get("content"));
    }

    // ---------- looks404or503 / statusCode ----------

    @Test
    void looks404or503ByStatusCode() {
        assertTrue(PayloadUnwrap.looks404or503(Map.of("status", 404), ""));
        assertTrue(PayloadUnwrap.looks404or503(Map.of("status", "503"), null));
        assertTrue(PayloadUnwrap.looks404or503(Map.of("statusCode", 404), ""));
        assertFalse(PayloadUnwrap.looks404or503(Map.of("status", 500), "server error"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP 404 Not Found", "Service Unavailable", "endpoint missing",
            "non-SSE response", "got a 503 back"})
    void looks404or503ByRawText(String raw) {
        assertTrue(PayloadUnwrap.looks404or503(null, raw));
    }

    @Test
    void looks404or503RejectsUnrelatedText() {
        assertFalse(PayloadUnwrap.looks404or503(null, null));
        assertFalse(PayloadUnwrap.looks404or503(Map.of(), "all good"));
    }

    @Test
    void statusCodeScansKnownKeys() {
        assertEquals(404, PayloadUnwrap.statusCode(Map.of("status", 404)));
        assertEquals(503, PayloadUnwrap.statusCode(Map.of("status_code", "503")));
        assertEquals(200, PayloadUnwrap.statusCode(Map.of("code", 200)));
        assertEquals(302, PayloadUnwrap.statusCode(Map.of("httpStatus", 302)));
        assertEquals(0, PayloadUnwrap.statusCode(Map.of("status", "abc")));
        assertEquals(0, PayloadUnwrap.statusCode(Map.of()));
        assertEquals(0, PayloadUnwrap.statusCode("x"));
        assertEquals(0, PayloadUnwrap.statusCode(null));
    }

    // ---------- truthy ----------

    @Test
    void truthySemantics() {
        assertFalse(PayloadUnwrap.truthy(null));
        assertFalse(PayloadUnwrap.truthy(false));
        assertTrue(PayloadUnwrap.truthy(true));
        assertFalse(PayloadUnwrap.truthy(""));
        assertFalse(PayloadUnwrap.truthy("  "));
        assertTrue(PayloadUnwrap.truthy("x"));
        assertFalse(PayloadUnwrap.truthy(Map.of()));
        assertTrue(PayloadUnwrap.truthy(Map.of("a", 1)));
        assertFalse(PayloadUnwrap.truthy(List.of()));
        assertTrue(PayloadUnwrap.truthy(List.of(1)));
        assertTrue(PayloadUnwrap.truthy(0));
    }

    // ---------- bridge methods ----------

    @Test
    void bridgeMethodsDelegate() {
        assertTrue(PayloadUnwrap.isEmpty(null));
        assertTrue(PayloadUnwrap.overflow(Map.of("error", "context_length_exceeded")));
        assertEquals("x", PayloadUnwrap.extractText(Map.of("content", "x")));
        assertEquals("plain", PayloadUnwrap.extractText("plain"));
        assertEquals(Map.of("role", "assistant", "content", "x"),
                PayloadUnwrap.findAssistant(Map.of("result", Map.of("role", "assistant", "content", "x"))));
        assertNull(PayloadUnwrap.findAssistant(Map.of("foo", 1)));
    }
}
