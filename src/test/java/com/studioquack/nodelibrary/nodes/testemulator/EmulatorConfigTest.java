package com.studioquack.nodelibrary.nodes.testemulator;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Config extraction/merge from the executionContext shape — pure logic. */
class EmulatorConfigTest {

    // ---------- defaults / constructor fallbacks ----------

    @Test
    void defaultsMatchDocumentedConstants() {
        EmulatorConfig c = EmulatorConfig.defaults();
        assertEquals(EmulatorConfig.DEFAULT_TIMEOUT_SECONDS, c.defaultTimeoutSeconds());
        assertEquals(EmulatorConfig.DEFAULT_MAX_LOG_CHARS, c.maxLogChars());
        assertFalse(c.verbose());
        assertEquals(Set.of("python", "javascript", "typescript", "java"), c.allowedLanguages());
    }

    @Test
    void constructorFallsBackOnNonPositiveNumbers() {
        EmulatorConfig c = new EmulatorConfig(0, -1, true, Set.of("python"));
        assertEquals(EmulatorConfig.DEFAULT_TIMEOUT_SECONDS, c.defaultTimeoutSeconds());
        assertEquals(EmulatorConfig.DEFAULT_MAX_LOG_CHARS, c.maxLogChars());
        assertTrue(c.verbose());
        assertEquals(Set.of("python"), c.allowedLanguages());
    }

    @Test
    void constructorFallsBackOnNullOrEmptyLanguages() {
        assertEquals(EmulatorConfig.parseAllowed(EmulatorConfig.DEFAULT_ALLOWED),
                new EmulatorConfig(10, 10, false, null).allowedLanguages());
        assertEquals(EmulatorConfig.parseAllowed(EmulatorConfig.DEFAULT_ALLOWED),
                new EmulatorConfig(10, 10, false, Set.of()).allowedLanguages());
    }

    // ---------- parseAllowed ----------

    @Test
    void parseAllowedNormalisesCaseAndWhitespace() {
        assertEquals(List.of("python", "java"),
                List.copyOf(EmulatorConfig.parseAllowed(" Python ,JAVA ")));
    }

    @Test
    void parseAllowedSkipsEmptyTokensAndDedupes() {
        assertEquals(Set.of("python", "java"), EmulatorConfig.parseAllowed("python,,java,python,"));
    }

    @Test
    void parseAllowedNullOrBlankFallsBackToDefault() {
        Set<String> dflt = EmulatorConfig.parseAllowed(EmulatorConfig.DEFAULT_ALLOWED);
        assertEquals(dflt, EmulatorConfig.parseAllowed(null));
        assertEquals(dflt, EmulatorConfig.parseAllowed("   "));
    }

    // ---------- from ----------

    @Test
    void fromNullsYieldsDefaults() {
        EmulatorConfig c = EmulatorConfig.from(null, null);
        assertEquals(EmulatorConfig.DEFAULT_TIMEOUT_SECONDS, c.defaultTimeoutSeconds());
        assertEquals(EmulatorConfig.DEFAULT_MAX_LOG_CHARS, c.maxLogChars());
        assertFalse(c.verbose());
    }

    @Test
    void fromReadsNodeParams() {
        Map<String, Object> params = Map.of(
                "default_timeout_seconds", "45",
                "max_log_chars", 5000,
                "verbose", "yes",
                "allowed_languages", "python, java");
        EmulatorConfig c = EmulatorConfig.from(params, null);
        assertEquals(45, c.defaultTimeoutSeconds());
        assertEquals(5000, c.maxLogChars());
        assertTrue(c.verbose());
        assertEquals(Set.of("python", "java"), c.allowedLanguages());
    }

    @Test
    void testOverridesWinOverNodeParams() {
        Map<String, Object> params = Map.of("default_timeout_seconds", 45, "verbose", false);
        Map<String, Object> overrides = Map.of("default_timeout_seconds", 5, "verbose", true);
        EmulatorConfig c = EmulatorConfig.from(params, overrides);
        assertEquals(5, c.defaultTimeoutSeconds());
        assertTrue(c.verbose());
    }

    @Test
    void fromDoesNotMutateInputs() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("verbose", false);
        EmulatorConfig.from(params, Map.of("verbose", true));
        assertEquals(false, params.get("verbose"));
    }

    @Test
    void fromInvalidNumbersFallBackToDefaults() {
        EmulatorConfig c = EmulatorConfig.from(
                Map.of("default_timeout_seconds", "abc", "max_log_chars", "x"), null);
        assertEquals(EmulatorConfig.DEFAULT_TIMEOUT_SECONDS, c.defaultTimeoutSeconds());
        assertEquals(EmulatorConfig.DEFAULT_MAX_LOG_CHARS, c.maxLogChars());
    }

    // ---------- readNodeParams ----------

    /** Build the daemon invoke-body shape: executionContext.currentNode.data.configurationParameters[]. */
    private static Map<String, Object> invokeBody(Object configurationParameters) {
        return Map.of("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", configurationParameters))));
    }

    @Test
    void readNodeParamsExtractsShortCodeValues() {
        Map<String, Object> body = invokeBody(List.of(
                Map.of("shortCode", "default_timeout_seconds", "value", 30),
                Map.of("shortCode", "verbose", "value", true)));
        Map<String, Object> out = EmulatorConfig.readNodeParams(body);
        assertEquals(30, out.get("default_timeout_seconds"));
        assertEquals(true, out.get("verbose"));
        assertEquals(2, out.size());
    }

    @Test
    void readNodeParamsSkipsNonMapsBlankCodesAndNullValues() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shortCode", "nullval");
        row.put("value", null);
        Map<String, Object> body = invokeBody(List.of(
                "not-a-map",
                Map.of("shortCode", "  ", "value", 1),
                row,
                Map.of("value", 1),
                Map.of("shortCode", "ok", "value", 2)));
        Map<String, Object> out = EmulatorConfig.readNodeParams(body);
        assertEquals(Map.of("ok", 2), out);
    }

    @Test
    void readNodeParamsMissingOrWrongShapeYieldsEmpty() {
        assertTrue(EmulatorConfig.readNodeParams(null).isEmpty());
        assertTrue(EmulatorConfig.readNodeParams(Map.of()).isEmpty());
        assertTrue(EmulatorConfig.readNodeParams(
                Map.of("executionContext", Map.of("currentNode", Map.of("data", Map.of())))).isEmpty());
        assertTrue(EmulatorConfig.readNodeParams(invokeBody("not-a-list")).isEmpty());
    }

    @Test
    void readNodeParamsKeepsInsertionOrder() {
        Map<String, Object> body = invokeBody(List.of(
                Map.of("shortCode", "b", "value", 2),
                Map.of("shortCode", "a", "value", 1)));
        assertEquals(List.of("b", "a"), List.copyOf(EmulatorConfig.readNodeParams(body).keySet()));
    }
}
