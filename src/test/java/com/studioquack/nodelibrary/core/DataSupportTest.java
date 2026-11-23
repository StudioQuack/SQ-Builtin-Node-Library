package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.UnknownHostException;

/** Pure helpers of DataSupport — no HTTP path is exercised here. */
class DataSupportTest {

    @Test
    void httpErrorDetailSimplifiesTomcatHtml404() {
        String body = "<!doctype html><html><body>HTTP Status 404 – Not Found Apache Tomcat</body></html>";
        String detail = DataSupport.httpErrorDetail(body);
        assertTrue(detail.contains("HTML error page"), detail);
        assertFalse(detail.contains("Tomcat"), detail);
    }

    @Test
    void clarifyInvoiceNinjaTierDetailMentionsUpgrade() {
        String in = "Feature not available with free / unpaid account";
        String out = DataSupport.clarifyInvoiceNinjaTierDetail(in);
        assertTrue(out.contains("paid"));
        assertTrue(out.contains("trial"));
    }

    @Test
    void clarifyBitbucketAuthDetailMentionsEmailConfig() {
        String in = "API token must be used with an atlassian registered email";
        String out = DataSupport.clarifyBitbucketAuthDetail(in);
        assertTrue(out.contains("email"));
        assertTrue(out.contains("Atlassian"));
    }

    @Test
    void clarifyCalComBookingFieldDetailExpandsSlug() {
        String in = "responses - {notes}error_required_field,";
        String out = DataSupport.clarifyCalComBookingFieldDetail(in);
        assertTrue(out.contains("bookingFieldsResponses.notes"));
        assertTrue(out.contains("get-event-type"));
    }

    // ---------- urlEncode ----------

    @Test
    void urlEncodeMapsSpaceToPercent20NotPlus() {
        assertEquals("a%20b", DataSupport.urlEncode("a b"));
    }

    @Test
    void urlEncodeHandlesNullAndSpecials() {
        assertEquals("", DataSupport.urlEncode(null));
        assertEquals("a%26b%3Dc", DataSupport.urlEncode("a&b=c"));
        assertEquals("%C3%A9", DataSupport.urlEncode("é"), "UTF-8 percent encoding");
    }

    // ---------- query ----------

    @Test
    void queryNullOrEmptyIsEmptyString() {
        assertEquals("", DataSupport.query(null));
        assertEquals("", DataSupport.query(Map.of()));
    }

    @Test
    void querySkipsNullKeysAndValues() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("a", "1");
        p.put(null, "x");
        p.put("b", null);
        p.put("c", "3");
        assertEquals("a=1&c=3", DataSupport.query(p));
    }

    @Test
    void queryEncodesKeysAndValues() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("q", "hello world");
        p.put("a&b", "x=y");
        assertEquals("q=hello%20world&a%26b=x%3Dy", DataSupport.query(p));
    }

    // ---------- parseJson / parseObject ----------

    @Test
    void parseJsonNullAndBlankReturnNull() {
        assertNull(DataSupport.parseJson(null));
        assertNull(DataSupport.parseJson(""));
        assertNull(DataSupport.parseJson("   "));
    }

    @Test
    void parseJsonStripsGoogleXssiPrefix() {
        Object v = DataSupport.parseJson(")]}'\n{\"a\":1}");
        assertEquals(Map.of("a", 1), v);
    }

    @Test
    void parseJsonSeeksFirstBraceForArraysToo() {
        Object v = DataSupport.parseJson("garbage prefix [1,2]");
        assertEquals(List.of(1, 2), v);
    }

    @Test
    void parseJsonNonJsonReturnsNull() {
        assertNull(DataSupport.parseJson("hello world"));
        assertNull(DataSupport.parseJson("{broken"));
    }

    @Test
    void parseJsonScalarRootIsReturned() {
        assertEquals(7, DataSupport.parseJson("7"));
        assertEquals("x", DataSupport.parseJson("\"x\""));
    }

    @Test
    void parseObjectReturnsNullForNonObjectRoots() {
        assertNull(DataSupport.parseObject("[1,2]"));
        assertNull(DataSupport.parseObject("7"));
        assertNull(DataSupport.parseObject("not json"));
        assertNull(DataSupport.parseObject(null));
    }

    @Test
    void parseObjectParsesObject() {
        assertEquals(Map.of("k", "v"), DataSupport.parseObject("{\"k\":\"v\"}"));
        assertEquals(Map.of("k", "v"), DataSupport.parseObject(")]}'{\"k\":\"v\"}"));
    }

    @Test
    void indexOfJsonStartFindsFirstBraceOrBracket() {
        assertEquals(0, DataSupport.indexOfJsonStart("{\"a\":1}"));
        assertEquals(4, DataSupport.indexOfJsonStart(")]}'{\"a\":1}"));
        assertEquals(0, DataSupport.indexOfJsonStart("no braces at all"), "no brace → 0 (parse will fail)");
        assertEquals(2, DataSupport.indexOfJsonStart("x [1]"));
    }

    // ---------- input accessors ----------

    @Test
    void inStrFallbacksAndTrims() {
        assertEquals("fb", DataSupport.inStr(null, "k", "fb"));
        assertEquals("fb", DataSupport.inStr(Map.of(), "k", "fb"));
        assertEquals("fb", DataSupport.inStr(Map.of("k", "   "), "k", "fb"));
        assertEquals("v", DataSupport.inStr(Map.of("k", " v "), "k", "fb"));
        assertEquals("5", DataSupport.inStr(Map.of("k", 5), "k", "fb"), "numbers stringify");
    }

    @Test
    void integralDoubleIsWrittenWithoutFraction() {
        assertEquals("1", DataSupport.integralText(1.0d));
        assertEquals("0", DataSupport.integralText(0.0d));
        assertEquals("-2", DataSupport.integralText(-2.0f));
        assertEquals("1.5", DataSupport.integralText(1.5d));
    }

    @Test
    void httpStatusMessageKeepsProviderDetail() {
        assertEquals("HTTP 400", DataSupport.httpStatusMessage(400, "  "));
        assertEquals("HTTP 400: nope", DataSupport.httpStatusMessage(400, "{\"message\":\"nope\"}"));
        assertEquals("HTTP 400: bad sparql more",
                DataSupport.httpStatusMessage(400, "bad sparql\nmore"));
        String info = DataSupport.httpStatusMessage(400,
                "{\"error\":{\"code\":\"badinteger\",\"info\":\"Invalid value\"}}");
        assertEquals("HTTP 400: Invalid value", info);
        String crossref = "{\"status\":\"failed\",\"message\":[{\"type\":\"integer-not-valid\","
                + "\"message\":\"Integer specified as five but must be a positive integer.\"}]}";
        assertEquals("HTTP 400: Integer specified as five but must be a positive integer.",
                DataSupport.httpStatusMessage(400, crossref));
        assertEquals("HTTP 404: Resource not found.",
                DataSupport.httpStatusMessage(404, "Resource not found.\n"));
    }

    @Test
    void clarifyNewRelicAuthDetailOn401() {
        String msg = DataSupport.clarifyNewRelicAuthDetail("authentication required", 401);
        assertTrue(msg.contains("user_api_key"), msg);
    }

    @Test
    void clarifyAtlassianCloudHttpDetailOnBare404() {
        String msg = DataSupport.clarifyAtlassianCloudHttpDetail("Not Found", 404);
        assertTrue(msg.contains("cloud_id"), msg);
    }

    @Test
    void inStrAnyPicksFirstNonBlankAlias() {
        Map<String, Object> in = Map.of("q", "  ", "query", "cats");
        assertEquals("cats", DataSupport.inStrAny(in, "fb", "q", "query"));
        assertEquals("fb", DataSupport.inStrAny(Map.of(), "fb", "q", "query"));
        assertEquals("fb", DataSupport.inStrAny(Map.of(), "fb"));
        assertEquals("fb", DataSupport.inStrAny(Map.of(), "fb", (String[]) null));
    }

    @Test
    void inIntVariants() {
        assertEquals(9, DataSupport.inInt(null, "k", 9));
        assertEquals(9, DataSupport.inInt(Map.of(), "k", 9));
        assertEquals(9, DataSupport.inInt(Map.of("k", "  "), "k", 9));
        assertEquals(3, DataSupport.inInt(Map.of("k", "3"), "k", 9));
        assertEquals(3, DataSupport.inInt(Map.of("k", 3), "k", 9));
        assertEquals(9, DataSupport.inInt(Map.of("k", "abc"), "k", 9));
    }

    @Test
    void inBoolVariants() {
        assertTrue(DataSupport.inBool(null, "k", true));
        assertFalse(DataSupport.inBool(Map.of(), "k", false));
        assertTrue(DataSupport.inBool(Map.of("k", true), "k", false));
        assertTrue(DataSupport.inBool(Map.of("k", "yes"), "k", false));
        assertFalse(DataSupport.inBool(Map.of("k", "no"), "k", true));
        assertFalse(DataSupport.inBool(Map.of("k", 0), "k", true));
    }

    @Test
    void inStringListFromListFiltersBlanks() {
        Map<String, Object> in = Map.of("k", List.of(" a ", "", "b", "  "));
        assertEquals(List.of("a", "b"), DataSupport.inStringList(in, "k"));
    }

    @Test
    void inStringListFromCommaString() {
        assertEquals(List.of("a", "b", "c"), DataSupport.inStringList(Map.of("k", "a, b ,,c"), "k"));
        assertEquals(List.of(), DataSupport.inStringList(Map.of("k", "  "), "k"));
        assertEquals(List.of(), DataSupport.inStringList(Map.of(), "k"));
        assertEquals(List.of(), DataSupport.inStringList(null, "k"));
    }

    @Test
    void inStringListStringifiesNonStringItems() {
        assertEquals(List.of("1", "2"), DataSupport.inStringList(Map.of("k", List.of(1, 2)), "k"));
    }

    // ---------- config accessors ----------

    @Test
    void cfgStrVariants() {
        assertEquals("fb", DataSupport.cfgStr(null, "k", "fb"));
        assertEquals("fb", DataSupport.cfgStr(Map.of(), "k", "fb"));
        assertEquals("fb", DataSupport.cfgStr(Map.of("k", "  "), "k", "fb"));
        assertEquals("v", DataSupport.cfgStr(Map.of("k", " v "), "k", "fb"));
    }

    @Test
    void cfgIntVariants() {
        assertEquals(7, DataSupport.cfgInt(null, "k", 7));
        assertEquals(7, DataSupport.cfgInt(Map.of(), "k", 7));
        assertEquals(7, DataSupport.cfgInt(Map.of("k", ""), "k", 7));
        assertEquals(7, DataSupport.cfgInt(Map.of("k", 0), "k", 7), "non-positive falls back");
        assertEquals(7, DataSupport.cfgInt(Map.of("k", -3), "k", 7), "non-positive falls back");
        assertEquals(7, DataSupport.cfgInt(Map.of("k", "abc"), "k", 7));
        assertEquals(30, DataSupport.cfgInt(Map.of("k", "30"), "k", 7));
        assertEquals(30, DataSupport.cfgInt(Map.of("k", 30), "k", 7));
    }

    // ---------- trimToNull ----------

    @Test
    void trimToNullVariants() {
        assertNull(DataSupport.trimToNull(null));
        assertNull(DataSupport.trimToNull(""));
        assertNull(DataSupport.trimToNull("   "));
        assertEquals("x", DataSupport.trimToNull(" x "));
    }

    // ---------- ports / fail / invalidInput / failError ----------

    @Test
    void portsWrapsPayloadOnOutputKey() {
        Map<String, Object> p = DataSupport.ports(Map.of("a", 1));
        assertEquals(1, p.size());
        assertEquals(Map.of("a", 1), p.get("output"));
    }

    @Test
    void portsAllowsNullPayload() {
        Map<String, Object> p = DataSupport.ports(null);
        assertTrue(p.containsKey("output"));
        assertNull(p.get("output"));
    }

    @Test
    void failRaisesRequestFailed() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> DataSupport.fail("boom"));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertEquals("boom", e.getMessage());
        assertTrue(e.data().isEmpty());
    }

    @Test
    void failBlankMessageBecomesRequestFailed() {
        StudioQuackError a = assertThrows(StudioQuackError.class, () -> DataSupport.fail(null));
        StudioQuackError b = assertThrows(StudioQuackError.class, () -> DataSupport.fail("   "));
        assertEquals("REQUEST_FAILED", a.errorCode());
        assertEquals("request failed", a.getMessage());
        assertEquals("REQUEST_FAILED", b.errorCode());
        assertEquals("request failed", b.getMessage());
    }

    @Test
    void invalidInputRaisesInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> DataSupport.invalidInput("bad field"));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("bad field", e.getMessage());
        StudioQuackError blank = assertThrows(StudioQuackError.class, () -> DataSupport.invalidInput("  "));
        assertEquals("INVALID_INPUT", blank.errorCode());
        assertEquals("invalid input", blank.getMessage());
    }

    @Test
    void failErrorMapsHttp401And403ToAuthFailed() {
        StudioQuackError e401 = DataSupport.failError("HTTP 401: denied");
        assertEquals("AUTH_FAILED", e401.errorCode());
        assertEquals("HTTP 401: denied", e401.getMessage());
        assertEquals(401, e401.data().get("http_status"));

        StudioQuackError e403 = DataSupport.failError("provider HTTP 403 forbidden");
        assertEquals("AUTH_FAILED", e403.errorCode());
        assertEquals(403, e403.data().get("http_status"));
    }

    @Test
    void failErrorMapsHttp429ToRateLimited() {
        StudioQuackError e = DataSupport.failError("HTTP 429: slow down");
        assertEquals("RATE_LIMITED", e.errorCode());
        assertEquals(429, e.data().get("http_status"));
    }

    @Test
    void failErrorMapsHttp404WithStatusData() {
        StudioQuackError e = DataSupport.failError("HTTP 404: nope");
        assertEquals("HTTP_404", e.errorCode());
        assertEquals("HTTP 404: nope", e.getMessage());
        assertEquals(404, e.data().get("http_status"));
    }

    @Test
    void failErrorWithoutStatusIsRequestFailed() {
        StudioQuackError e = DataSupport.failError("transport timeout");
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertEquals("transport timeout", e.getMessage());
        assertTrue(e.data().isEmpty());
    }

    // ---------- redact ----------

    @Test
    void redactReplacesSecretsOfFourCharsOrMore() {
        assertEquals("token *** failed", DataSupport.redact("token abcd failed", "abcd"));
        assertEquals("*** ***", DataSupport.redact("sk-1234 sk-1234", "sk-1234"), "all occurrences");
    }

    @Test
    void redactLeavesShortSecretsAlone() {
        assertEquals("key abc here", DataSupport.redact("key abc here", "abc"));
    }

    @Test
    void redactHandlesNulls() {
        assertEquals("request failed", DataSupport.redact(null, "secret"));
        assertEquals("msg", DataSupport.redact("msg", (String[]) null));
        assertEquals("msg", DataSupport.redact("msg", (String) null));
    }

    @Test
    void redactMultipleSecrets() {
        assertEquals("a=*** b=***", DataSupport.redact("a=AAAA b=BBBB", "AAAA", "BBBB"));
    }

    // ---------- httpOk / HttpResp ----------

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 299})
    void httpOkTrueFor2xx(int status) {
        assertTrue(DataSupport.httpOk(status));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 199, 300, 301, 404, 500})
    void httpOkFalseOutside2xx(int status) {
        assertFalse(DataSupport.httpOk(status));
    }

    @Test
    void httpRespOkRequires2xxAndNoError() {
        assertTrue(new DataSupport.HttpResp(200, "{}", null).ok());
        assertFalse(new DataSupport.HttpResp(200, "", "transport").ok(),
                "error set → not ok even with 2xx status");
        assertFalse(new DataSupport.HttpResp(500, "{}", null).ok());
        assertFalse(new DataSupport.HttpResp(-1, "", "invalid URL").ok());
    }

    @Test
    void httpRespJsonObjectParsesBody() {
        assertEquals(Map.of("a", 1), new DataSupport.HttpResp(200, "{\"a\":1}", null).jsonObject());
        assertNull(new DataSupport.HttpResp(200, "not json", null).jsonObject());
    }

    // ---------- headers ----------

    @Test
    void headersPairsKv() {
        assertEquals(Map.of("A", "1", "B", "2"), DataSupport.headers("A", "1", "B", "2"));
    }

    @Test
    void headersSkipsNullsAndDanglingTail() {
        Map<String, String> m = DataSupport.headers("A", null, null, "2", "B", "2", "dangling");
        assertEquals(Map.of("B", "2"), m);
    }

    @Test
    void headersNullArrayIsEmpty() {
        assertTrue(DataSupport.headers((String[]) null).isEmpty());
        assertTrue(DataSupport.headers().isEmpty());
    }

    // ---------- templateId ----------

    @Test
    void templateIdFallsBackWhenEnvAbsent() {
        assertEquals("fb-id", DataSupport.templateId("SQ_TEST_DEFINITELY_UNSET_9f3b2c", "fb-id"));
        assertEquals("", DataSupport.templateId("SQ_TEST_DEFINITELY_UNSET_9f3b2c", null),
                "null fallback becomes empty string");
    }

    // ---------- readConfig ----------

    @Test
    void readConfigReadsConfigurationParameters() {
        Map<String, Object> request = Map.of("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", List.of(
                        Map.of("shortCode", "timeout_seconds", "value", 10))))));
        assertEquals(Map.of("timeout_seconds", 10), DataSupport.readConfig(request));
    }

    @Test
    void readConfigMissingShapeIsEmpty() {
        assertTrue(DataSupport.readConfig(null).isEmpty());
        assertTrue(DataSupport.readConfig(Map.of()).isEmpty());
    }

    // ---------- readInput ----------

    @Test
    void readInputPrefersInputVariables() {
        Map<String, Object> request = Map.of("inputVariables", Map.of("input", Map.of("a", 1)));
        assertEquals(Map.of("a", 1), DataSupport.readInput(request, null));
    }

    @Test
    void readInputUnwrapsOneElementList() {
        Map<String, Object> request = Map.of("inputVariables",
                Map.of("input", List.of(Map.of("a", 1))));
        assertEquals(Map.of("a", 1), DataSupport.readInput(request, null));
    }

    @Test
    void readInputParsesJsonObjectString() {
        Map<String, Object> request = Map.of("inputVariables", Map.of("input", "{\"a\":1}"));
        assertEquals(Map.of("a", 1), DataSupport.readInput(request, null));
    }

    @Test
    void readInputKeepsRawStringWhenJsonInvalid() {
        Map<String, Object> request = Map.of("inputVariables", Map.of("input", "{broken"));
        assertTrue(DataSupport.readInput(request, null).isEmpty(),
                "non-map raw value coerces to empty map");
    }

    @Test
    void readInputFallsBackToRequestInputList() {
        Map<String, Object> request = Map.of("executionContext", Map.of("currentNode",
                Map.of("inputList", List.of(
                        Map.of("targetPortValue", "other", "value", Map.of("x", 9)),
                        Map.of("targetPortValue", "input", "value", Map.of("b", 2))))));
        assertEquals(Map.of("b", 2), DataSupport.readInput(request, null));
    }

    @Test
    void readInputFallsBackToHelperInputList() {
        Map<String, Object> ctx = Map.of("currentNode",
                Map.of("inputList", List.of(
                        Map.of("targetPortValue", "input", "value", Map.of("c", 3)))));
        StudioQuackHelper helper = new StudioQuackHelper(ctx);
        assertEquals(Map.of("c", 3), DataSupport.readInput(Map.of(), helper));
    }

    @Test
    void readInputEmptyWhenNothingPresent() {
        assertTrue(DataSupport.readInput(null, null).isEmpty());
        assertTrue(DataSupport.readInput(Map.of(), null).isEmpty());
        assertTrue(DataSupport.readInput(Map.of("inputVariables", Map.of()), null).isEmpty());
    }

    @Test
    void readInputNonMapValueBecomesEmpty() {
        Map<String, Object> request = Map.of("inputVariables", Map.of("input", 42));
        assertTrue(DataSupport.readInput(request, null).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "'a b', 'a%20b'",
            "'a+b', 'a%2Bb'",
            "'100%25', '100%2525'"
    })
    void urlEncodeParameterized(String raw, String expected) {
        assertEquals(expected, DataSupport.urlEncode(raw));
    }

    @Test
    void transportFailureDetailClassifiesCommonCauses() {
        assertTrue(DataSupport.transportFailureDetail(new UnknownHostException("x")).contains("DNS"));
        assertTrue(DataSupport.transportFailureDetail(new ConnectException()).contains("connection"));
    }

    /**
     * 2026-11-10: Pro customers saw one message for a refusing server and an IPv6-only host alike.
     * The chains are the real JDK 21 shapes (Linux container without IPv6, as a Pro sandbox).
     */
    @Test
    void transportFailureDetailNamesTheConnectFailureKind() {
        ConnectException refused = new ConnectException();
        refused.initCause(new java.nio.channels.ClosedChannelException());
        assertTrue(DataSupport.transportFailureDetail(refused).contains("connection refused: the server"));

        ConnectException ipv6Only = new ConnectException("Network is unreachable");
        ipv6Only.initCause(new java.net.SocketException("Network is unreachable"));
        assertTrue(DataSupport.transportFailureDetail(ipv6Only).contains("IPv6-only"));

        ConnectException noRoute = new ConnectException("No route to host");
        noRoute.initCause(new java.net.NoRouteToHostException("No route to host"));
        assertTrue(DataSupport.transportFailureDetail(noRoute).contains("no route to host"));

        // What the JDK client throws for a name that does not resolve (also: no network at all).
        ConnectException unresolved = new ConnectException();
        unresolved.initCause(new java.nio.channels.UnresolvedAddressException());
        assertTrue(DataSupport.transportFailureDetail(unresolved).contains("DNS lookup failed"));

        assertTrue(DataSupport.transportFailureDetail(new ConnectException()).contains("refused or unreachable"));
    }

    /** The real JDK message shape: a closed local port reaches the "refused" branch end to end. */
    @Test
    void closedPortReportsConnectionRefused() throws Exception {
        int port;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        DataSupport.HttpResp resp = DataSupport.get("http://127.0.0.1:" + port + "/", Map.of(), 5);
        assertTrue(resp.error() != null && resp.error().contains("connection refused: the server"),
                String.valueOf(resp.error()));
    }
}
