package com.studioquack.nodelibrary.nodes.officialapi;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The accept_google_terms gate and the pre-HTTP input validation. The gate is
 * closed by default, so these paths raise INVALID_INPUT without any network
 * call; the opt-in scraper path is not exercised (it needs HTTP).
 */
class GoogleTrendsNodeTest {

    private final GoogleTrendsNode node = new GoogleTrendsNode();

    /** Invoke body carrying configurationParameters[] in the executionContext shape. */
    private static Map<String, Object> requestWithConfig(Object... shortCodeThenValue) {
        List<Object> params = new ArrayList<>();
        for (int i = 0; i + 1 < shortCodeThenValue.length; i += 2) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", shortCodeThenValue[i]);
            row.put("value", shortCodeThenValue[i + 1]);
            params.add(row);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        return request;
    }

    private static Map<String, Object> withInput(Map<String, Object> request, Object inputValue) {
        request.put("inputVariables", Map.of("input", inputValue));
        return request;
    }

    private static StudioQuackError thrown(java.util.function.Supplier<Map<String, Object>> call) {
        return assertThrows(StudioQuackError.class, call::get);
    }

    // ---------- template id ----------

    @Test
    void templateIdDefaultsToDocumentedConstant() {
        String env = System.getenv("SQ_TEMPLATE_ID_GOOGLE_TRENDS");
        assertEquals(env == null || env.isBlank() ? GoogleTrendsNode.DEFAULT_TEMPLATE_ID : env.trim(),
                node.templateId());
    }

    // ---------- gate: closed by default ----------

    @Test
    void missingAcceptGoogleTermsReturnsTermsError() {
        StudioQuackError e = thrown(() -> node.handle(requestWithConfig(), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Terms of Service"), e.getMessage());
        assertTrue(e.getMessage().contains("accept_google_terms"), e.getMessage());
        assertTrue(e.getMessage().contains("No request was sent"), e.getMessage());
    }

    @Test
    void nullRequestIsGatedNotCrashing() {
        StudioQuackError e = thrown(() -> node.handle(null, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Terms of Service"), e.getMessage());
    }

    @Test
    void emptyConfigurationParametersAreGated() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", List.of()))));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Terms of Service"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "no", "0", "off", ""})
    void falsyStringAcceptValuesAreGated(String value) {
        StudioQuackError e = thrown(() -> node.handle(requestWithConfig("accept_google_terms", value), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Terms of Service"));
    }

    @Test
    void booleanFalseAcceptIsGated() {
        StudioQuackError e = thrown(() -> node.handle(requestWithConfig("accept_google_terms", false), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Terms of Service"));
    }

    @Test
    void unrelatedConfigDoesNotOpenGate() {
        StudioQuackError e = thrown(() -> node.handle(
                requestWithConfig("timeout_seconds", 5, "accept_google_terms", "FALSE"), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Terms of Service"));
    }

    // ---------- gate open: input validation happens before any HTTP ----------

    @Test
    void acceptedTermsWithoutKeywordsFailsBeforeHttp() {
        Map<String, Object> request = withInput(
                requestWithConfig("accept_google_terms", true), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("keywords is required and must be a list of strings (max 5).", e.getMessage());
    }

    @Test
    void acceptedTermsWithNonListKeywordsFailsBeforeHttp() {
        Map<String, Object> request = withInput(
                requestWithConfig("accept_google_terms", "true"),
                Map.of("keywords", "not-a-list"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("keywords is required and must be a list of strings (max 5).", e.getMessage());
    }

    @Test
    void acceptedTermsWithEmptyKeywordListFailsBeforeHttp() {
        Map<String, Object> request = withInput(
                requestWithConfig("accept_google_terms", true),
                Map.of("keywords", List.of()));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("keywords is required and must be a list of strings (max 5).", e.getMessage());
    }

    @Test
    void acceptedTermsWithBlankOnlyKeywordsFailsBeforeHttp() {
        Map<String, Object> request = withInput(
                requestWithConfig("accept_google_terms", "yes"),
                Map.of("keywords", List.of("   ", "")));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("keywords is required and must be a list of strings (max 5).", e.getMessage());
    }

    @Test
    void acceptedTermsWithMissingInputVariablesFailsBeforeHttp() {
        StudioQuackError e = thrown(() -> node.handle(requestWithConfig("accept_google_terms", true), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("keywords is required and must be a list of strings (max 5).", e.getMessage());
    }

    @Test
    void gatedRequestReturnsTermsError() {
        StudioQuackError e = thrown(() -> node.handle(null, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Terms of Service"), e.getMessage());
    }
}
