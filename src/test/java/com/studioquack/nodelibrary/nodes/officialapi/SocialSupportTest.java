package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import studioquack.StudioQuackError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic coverage for {@link SocialSupport}: invoke-body parsing, OUTPUT-port
 * shaping, coercion helpers, and pagination/timeout clamps. No HTTP is exercised
 * here — {@link SocialHttp} is covered only through the shapes this class feeds it.
 */
class SocialSupportTest {

    // --- nodeParams: configurationParameters keyed by shortCode -----------------

    @Test
    void nodeParamsNullRequestYieldsEmpty() {
        assertTrue(SocialSupport.nodeParams(null).isEmpty());
    }

    @Test
    void nodeParamsReadsShortCodesAndSkipsInvalidRows() {
        Map<String, Object> request = Map.of("executionContext", Map.of("currentNode", Map.of("data",
                Map.of("configurationParameters", List.of(
                        Map.of("shortCode", "api_key", "value", "k1"),
                        Map.of("shortCode", "limit", "value", 42),
                        Map.of("shortCode", "  ", "value", "skipped"),
                        Map.of("shortCode", "null_value") // no value -> skipped
                )))));
        Map<String, Object> params = SocialSupport.nodeParams(request);
        assertEquals("k1", params.get("api_key"));
        assertEquals(42, params.get("limit"));
        assertFalse(params.containsKey("  "));
        assertFalse(params.containsKey("null_value"));
        assertEquals(2, params.size());
    }

    @Test
    void nodeParamsMissingConfigurationParametersYieldsEmpty() {
        assertTrue(SocialSupport.nodeParams(Map.of("executionContext", Map.of())).isEmpty());
    }

    // --- inputPort: INPUT port unwrap -------------------------------------------

    @Test
    void inputPortNullRequestYieldsMutableEmpty() {
        Map<String, Object> in = SocialSupport.inputPort(null);
        assertTrue(in.isEmpty());
        in.put("mutable", true); // must not be an immutable Map.of()
        assertEquals(true, in.get("mutable"));
    }

    @Test
    void inputPortPrefersInputVariables() {
        Map<String, Object> request = Map.of(
                "inputVariables", Map.of("input", Map.of("q", "cats")),
                "executionContext", Map.of("currentNode", Map.of("inputList",
                        List.of(Map.of("targetPortValue", "input", "value", Map.of("q", "dogs"))))));
        assertEquals("cats", SocialSupport.inputPort(request).get("q"));
    }

    @Test
    void inputPortUnwrapsOneElementList() {
        Map<String, Object> request = Map.of(
                "inputVariables", Map.of("input", List.of(Map.of("video_id", "abc"))));
        assertEquals("abc", SocialSupport.inputPort(request).get("video_id"));
    }

    @Test
    void inputPortFallsBackToCurrentNodeInputList() {
        Map<String, Object> request = Map.of("executionContext", Map.of("currentNode",
                Map.of("inputList", List.of(
                        Map.of("targetPortValue", "other", "value", Map.of("x", 1)),
                        Map.of("targetPortValue", "input", "value", Map.of("subreddit", "java"))))));
        assertEquals("java", SocialSupport.inputPort(request).get("subreddit"));
    }

    @Test
    void inputPortNonMapValueYieldsEmpty() {
        Map<String, Object> request = Map.of("inputVariables", Map.of("input", "just-a-string"));
        assertTrue(SocialSupport.inputPort(request).isEmpty());
    }

    // --- ports / DataSupport.fail / invalidInput / failError ----------------------

    @Test
    void portsWrapsPayloadUnderOutput() {
        assertEquals("x", SocialSupport.ports("x").get("output"));
        assertEquals(Map.of(), SocialSupport.ports(null).get("output"));
    }

    @Test
    void failRaisesRequestFailed() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> DataSupport.fail("boom"));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertEquals("boom", e.getMessage());
    }

    @Test
    void invalidInputRaisesInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> DataSupport.invalidInput("bad"));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("bad", e.getMessage());
    }

    @Test
    void failErrorMapsHttpStatuses() {
        StudioQuackError e401 = DataSupport.failError("HTTP 401");
        assertEquals("AUTH_FAILED", e401.errorCode());
        assertEquals(401, e401.data().get("http_status"));
        StudioQuackError e429 = DataSupport.failError("HTTP 429");
        assertEquals("RATE_LIMITED", e429.errorCode());
        StudioQuackError none = DataSupport.failError(null);
        assertEquals("REQUEST_FAILED", none.errorCode());
        assertEquals("request failed", none.getMessage());
    }

    // --- string coercions ---------------------------------------------------------

    @Test
    void strTrimsAndIsNullSafe() {
        assertEquals("", SocialSupport.str(null));
        assertEquals("a", SocialSupport.str("  a  "));
        assertEquals("5", SocialSupport.str(5));
    }

    @Test
    void lowerAndIsBlank() {
        assertEquals("abc", SocialSupport.lower(" AbC "));
        assertEquals("", SocialSupport.lower(null));
        assertTrue(SocialSupport.isBlank(null));
        assertTrue(SocialSupport.isBlank("   "));
        assertFalse(SocialSupport.isBlank("x"));
    }

    @Test
    void firstNonBlankPicksFirstTrimmedValue() {
        assertEquals("a", SocialSupport.firstNonBlank(null, "  ", " a ", "b"));
        assertEquals("", SocialSupport.firstNonBlank(null, " ", ""));
        assertEquals("", SocialSupport.firstNonBlank((Object[]) null));
    }

    // --- numeric coercions ---------------------------------------------------------

    static Stream<Arguments> clampCases() {
        return Stream.of(
                Arguments.of("5", 3, 1, 10, 5),   // within range
                Arguments.of("-2", 3, 1, 10, 1),  // below min -> min
                Arguments.of("99", 3, 1, 10, 10), // above max -> max
                Arguments.of("junk", 3, 1, 10, 3),// non-numeric -> default
                Arguments.of(null, 3, 1, 10, 3),  // missing -> default
                Arguments.of(7, 3, 1, 10, 7),     // Number passthrough
                Arguments.of("0", 3, -5, -1, -1)  // clamped into negative range
        );
    }

    @ParameterizedTest
    @MethodSource("clampCases")
    void clampIntBoundsValues(Object raw, int dflt, int min, int max, int expected) {
        assertEquals(expected, SocialSupport.clampInt(raw, dflt, min, max));
    }

    static Stream<Arguments> longCases() {
        return Stream.of(
                Arguments.of(5L, 0L, 5L),
                Arguments.of(3.7, 0L, 3L),        // Number -> longValue
                Arguments.of("42", 0L, 42L),
                Arguments.of("4.9", 0L, 4L),      // double-string fallback
                Arguments.of("junk", 7L, 7L),
                Arguments.of("", 7L, 7L),
                Arguments.of(null, 7L, 7L),
                Arguments.of("3000000000", 0L, 3000000000L) // beyond int range
        );
    }

    @ParameterizedTest
    @MethodSource("longCases")
    void toLongHandlesCountersBeyondIntRange(Object raw, long dflt, long expected) {
        assertEquals(expected, SocialSupport.toLong(raw, dflt));
    }

    // --- stringList -----------------------------------------------------------------

    @Test
    void stringListAcceptsListCsvJsonArrayAndScalar() {
        assertEquals(List.of("a", "b"), SocialSupport.stringList(List.of("a", " ", "b")));
        assertEquals(List.of("a", "b", "c"), SocialSupport.stringList("a, b ,,c"));
        assertEquals(List.of("x", "y"), SocialSupport.stringList("[\"x\",\"y\"]"));
        assertEquals(List.of("solo"), SocialSupport.stringList("solo"));
        assertTrue(SocialSupport.stringList(null).isEmpty());
        assertTrue(SocialSupport.stringList("").isEmpty());
    }

    // --- childMap / cap --------------------------------------------------------------

    @Test
    void childMapNeverReturnsNull() {
        assertTrue(SocialSupport.childMap(null, "k").isEmpty());
        assertTrue(SocialSupport.childMap(Map.of(), "k").isEmpty());
        assertTrue(SocialSupport.childMap(Map.of("k", "not-a-map"), "k").isEmpty());
        assertEquals("v", SocialSupport.childMap(Map.of("k", Map.of("a", "v")), "k").get("a"));
    }

    @Test
    void capTruncatesLikePythonSlice() {
        assertEquals("hel", SocialSupport.cap("hello", 3));
        assertEquals("hi", SocialSupport.cap("hi", 5));
        assertEquals("", SocialSupport.cap(null, 5));
        assertEquals("x", SocialSupport.cap("x", 0)); // non-positive max -> unchanged
    }

    // --- timeoutSeconds / cfg / templateId -------------------------------------------

    @Test
    void timeoutSecondsFloorsAtOneAndFallsBack() {
        assertEquals(25, SocialSupport.timeoutSeconds(Map.of(), 25));
        assertEquals(25, SocialSupport.timeoutSeconds(Map.of("timeout_seconds", 0), 25));
        assertEquals(25, SocialSupport.timeoutSeconds(Map.of("timeout_seconds", -5), 25));
        assertEquals(7, SocialSupport.timeoutSeconds(Map.of("timeout_seconds", 7), 25));
        assertEquals(9, SocialSupport.timeoutSeconds(Map.of("timeout_seconds", "9"), 25));
    }

    @Test
    void cfgReturnsFallbackOnBlank() {
        assertEquals("v", SocialSupport.cfg(Map.of("k", "v"), "k", "fb"));
        assertEquals("fb", SocialSupport.cfg(Map.of(), "k", "fb"));
        assertEquals("fb", SocialSupport.cfg(Map.of("k", "   "), "k", "fb"));
        assertEquals("", SocialSupport.cfg(Map.of(), "k", null));
    }

    @Test
    void templateIdFallsBackWhenEnvUnset() {
        // SQ_TEMPLATE_ID_NO_SUCH_NODE_KEY_ZZ is not expected to be set in any environment.
        assertEquals("fallback-id", SocialSupport.templateId("no-such-node-key-zz", "fallback-id"));
    }
}
