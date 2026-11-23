package com.studioquack.nodelibrary.nodes.web;

import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure helpers of WebNodeSupport — send() (HTTP) is not exercised. */
class WebNodeSupportTest {

    // ---------- str / trim / toInt ----------

    @Test
    void strAndTrim() {
        assertEquals("", WebNodeSupport.str(null));
        assertEquals("5", WebNodeSupport.str(5));
        assertEquals("x", WebNodeSupport.trim(" x "));
        assertEquals("", WebNodeSupport.trim(null));
    }

    @ParameterizedTest
    @CsvSource({
            "'3', 0, 3",
            "'3.9', 0, 3",
            "' 7 ', 0, 7",
            "'abc', 9, 9",
            "'', 9, 9"
    })
    void toIntVariants(String raw, int dflt, int expected) {
        assertEquals(expected, WebNodeSupport.toInt(raw, dflt));
    }

    @Test
    void toIntFromNumberAndNull() {
        assertEquals(4, WebNodeSupport.toInt(4, 0));
        assertEquals(4, WebNodeSupport.toInt(4.8d, 0));
        assertEquals(2, WebNodeSupport.toInt(null, 2));
    }

    // ---------- asBool ----------

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "1", "yes", " Yes "})
    void asBoolTrueValues(String raw) {
        assertTrue(WebNodeSupport.asBool(raw, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE", "0", "no", " No "})
    void asBoolFalseValues(String raw) {
        assertFalse(WebNodeSupport.asBool(raw, true));
    }

    @Test
    void asBoolUnknownOrBlankFallsBack() {
        assertTrue(WebNodeSupport.asBool("maybe", true));
        assertFalse(WebNodeSupport.asBool("maybe", false));
        assertTrue(WebNodeSupport.asBool("  ", true));
        assertTrue(WebNodeSupport.asBool(null, true));
        assertFalse(WebNodeSupport.asBool(null, false));
    }

    @Test
    void asBoolBooleanInstance() {
        assertTrue(WebNodeSupport.asBool(Boolean.TRUE, false));
        assertFalse(WebNodeSupport.asBool(Boolean.FALSE, true));
    }

    // ---------- verifyTls ----------

    @Test
    void verifyTlsOnlyStringFalseDisables() {
        assertFalse(WebNodeSupport.verifyTls("false", true));
        assertFalse(WebNodeSupport.verifyTls("FALSE", true));
        assertFalse(WebNodeSupport.verifyTls(" false ", true));
        assertTrue(WebNodeSupport.verifyTls("0", true), "only the string false disables");
        assertFalse(WebNodeSupport.verifyTls(Boolean.FALSE, true),
                "Boolean.FALSE stringifies to \"false\" and also disables (doc says string only)");
        assertTrue(WebNodeSupport.verifyTls("true", false));
    }

    @Test
    void verifyTlsNullReturnsDefault() {
        assertTrue(WebNodeSupport.verifyTls(null, true));
        assertFalse(WebNodeSupport.verifyTls(null, false));
    }

    // ---------- maps ----------

    @Test
    void asMapAndMutableMap() {
        assertTrue(WebNodeSupport.asMap(null).isEmpty());
        assertTrue(WebNodeSupport.asMap("x").isEmpty());
        assertEquals(Map.of("a", 1), WebNodeSupport.asMap(Map.of("a", 1)));
        Map<String, Object> copy = WebNodeSupport.mutableMap(Map.of("a", 1));
        copy.put("b", 2);
        assertEquals(2, copy.size());
    }

    @Test
    void jsonObjectFromMapStringAndGarbage() {
        assertEquals(Map.of("a", 1), WebNodeSupport.jsonObject(Map.of("a", 1)));
        assertEquals(Map.of("a", 1), WebNodeSupport.jsonObject("{\"a\":1}"));
        assertTrue(WebNodeSupport.jsonObject("{broken").isEmpty());
        assertTrue(WebNodeSupport.jsonObject("[1,2]").isEmpty(), "non-object JSON → empty");
        assertTrue(WebNodeSupport.jsonObject(null).isEmpty());
        assertTrue(WebNodeSupport.jsonObject("   ").isEmpty());
    }

    @Test
    void jsonObjectReturnsMutableCopy() {
        Map<String, Object> m = WebNodeSupport.jsonObject(Map.of("a", 1));
        m.put("b", 2);
        assertEquals(2, m.size());
    }

    // ---------- headers ----------

    @Test
    void hasHeaderIsCaseInsensitive() {
        Map<String, String> h = Map.of("Content-Type", "application/json");
        assertTrue(WebNodeSupport.hasHeader(h, "content-type"));
        assertTrue(WebNodeSupport.hasHeader(h, "CONTENT-TYPE"));
        assertFalse(WebNodeSupport.hasHeader(h, "accept"));
        assertFalse(WebNodeSupport.hasHeader(null, "accept"));
        assertFalse(WebNodeSupport.hasHeader(h, null));
    }

    @Test
    void stringHeadersFiltersAndStringifies() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("A", "1");
        raw.put("B", 2);
        raw.put("  ", "blank-key");
        raw.put("C", null);
        Map<String, String> out = WebNodeSupport.stringHeaders(raw);
        assertEquals(Map.of("A", "1", "B", "2"), out);
        assertTrue(WebNodeSupport.stringHeaders(null).isEmpty());
    }

    // ---------- utf8 ----------

    @Test
    void utf8EncodesAndHandlesNull() {
        assertArrayEquals(new byte[0], WebNodeSupport.utf8(null));
        assertArrayEquals("héllo".getBytes(StandardCharsets.UTF_8), WebNodeSupport.utf8("héllo"));
    }

    // ---------- readInput ----------

    @Test
    void readInputPrefersInputVariables() {
        Map<String, Object> req = Map.of("inputVariables", Map.of("input", Map.of("a", 1)));
        assertEquals(Map.of("a", 1), WebNodeSupport.readInput(req));
    }

    @Test
    void readInputUnwrapsOneElementList() {
        Map<String, Object> req = Map.of("inputVariables", Map.of("input", List.of(Map.of("a", 1))));
        assertEquals(Map.of("a", 1), WebNodeSupport.readInput(req));
    }

    @Test
    void readInputParsesJsonObjectString() {
        Map<String, Object> req = Map.of("inputVariables", Map.of("input", " {\"a\":1} "));
        assertEquals(Map.of("a", 1), WebNodeSupport.readInput(req));
    }

    @Test
    void readInputInvalidJsonStringBecomesEmpty() {
        Map<String, Object> req = Map.of("inputVariables", Map.of("input", "{broken}"));
        assertTrue(WebNodeSupport.readInput(req).isEmpty());
    }

    @Test
    void readInputFallsBackToExecutionContextInputList() {
        Map<String, Object> req = Map.of("executionContext", Map.of("currentNode",
                Map.of("inputList", List.of(
                        Map.of("targetPortValue", "other", "value", Map.of("x", 9)),
                        Map.of("targetPortValue", "input", "value", Map.of("b", 2))))));
        assertEquals(Map.of("b", 2), WebNodeSupport.readInput(req));
    }

    @Test
    void readInputEmptyWhenNothingPresent() {
        assertTrue(WebNodeSupport.readInput(null).isEmpty());
        assertTrue(WebNodeSupport.readInput(Map.of()).isEmpty());
        assertTrue(WebNodeSupport.readInput(Map.of("inputVariables", Map.of("input", 42))).isEmpty(),
                "non-map input coerces to empty");
    }

    @Test
    void readInputJsonArrayStringIsNotParsed() {
        Map<String, Object> req = Map.of("inputVariables", Map.of("input", "[1,2]"));
        assertTrue(WebNodeSupport.readInput(req).isEmpty(),
                "looksLikeJsonObject requires {…}; arrays stay raw and coerce to empty");
    }

    // ---------- readConfig ----------

    @Test
    void readConfigReadsConfigurationParameters() {
        Map<String, Object> req = Map.of("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", List.of(
                        Map.of("shortCode", "timeout_seconds", "value", 15))))));
        assertEquals(Map.of("timeout_seconds", 15), WebNodeSupport.readConfig(req));
        assertTrue(WebNodeSupport.readConfig(null).isEmpty());
    }

    // ---------- helperClient / log ----------

    @Test
    void helperClientNeverThrowsOnOddInput() {
        assertNotNull(WebNodeSupport.helperClient(null));
        assertNotNull(WebNodeSupport.helperClient(Map.of()));
        assertNotNull(WebNodeSupport.helperClient(Map.of("helperToken", "  ")));
        assertNotNull(WebNodeSupport.helperClient(Map.of("helperToken", "tok-123")));
    }

    @Test
    void logWithNullOrDisabledHelperIsNoOp() {
        HelperClient disabled = WebNodeSupport.helperClient(Map.of());
        WebNodeSupport.log(null, "message");
        WebNodeSupport.log(disabled, "message");
        WebNodeSupport.log(disabled, null);
        // no exception = pass
    }

    // ---------- HttpResult ----------

    @Test
    void httpResultNullsBecomeDefaults() {
        WebNodeSupport.HttpResult r = new WebNodeSupport.HttpResult(200, null, null, null, null, false);
        assertEquals("", r.url);
        assertEquals("", r.body);
        assertEquals("", r.contentType);
        assertTrue(r.headers.isEmpty());
        assertFalse(r.truncated);
        assertEquals(200, r.statusCode);
    }

    @Test
    void httpResultHeaderLookupIsCaseInsensitive() {
        WebNodeSupport.HttpResult r = new WebNodeSupport.HttpResult(
                200, "u", "b", "text/plain", Map.of("Content-Type", "text/plain"), false);
        assertTrue(r.header("content-type").isPresent());
        assertEquals("text/plain", r.header("CONTENT-TYPE").orElseThrow());
        assertTrue(r.header("missing").isEmpty());
        assertTrue(r.header(null).isEmpty());
    }
}
