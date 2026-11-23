package com.studioquack.nodelibrary.nodes.officialapi;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAlex auth and error-hint coverage after the Feb 2026 polite-pool retirement.
 * No live calls to api.openalex.org.
 */
class OpenAlexNodeTest {

    private final OpenalexNode node = new OpenalexNode();

    @Test
    void authParamsSendsApiKeyWhenConfigured() {
        Map<String, String> params = OpenalexNode.authParams("oa-test-key");
        assertEquals("oa-test-key", params.get("api_key"));
        assertEquals(1, params.size());
        assertFalse(params.containsKey("mailto"));
    }

    @Test
    void authParamsOmitsMailtoWhenKeyMissing() {
        Map<String, String> params = OpenalexNode.authParams(null);
        assertTrue(params.isEmpty());
        assertFalse(params.containsKey("mailto"));
    }

    @Test
    void httpError429HasNoLegacyQuotaHint() {
        String msg = OpenalexNode.httpError(429, null);
        assertTrue(msg.startsWith("OpenAlex API Error: HTTP 429"), msg);
        assertFalse(msg.contains("10k"), msg);
        assertFalse(msg.contains("1k"), msg);
        assertTrue(msg.contains("100 requests/s"), msg);
    }

    @Test
    void httpError403KeepsKeyHint() {
        String msg = OpenalexNode.httpError(403, null);
        assertTrue(msg.contains("openalex.org/settings/api"), msg);
    }

    @Test
    void missingQueryFailsBeforeHttpEvenIfDeprecatedEmailIsSet() {
        StudioQuackError e = thrown(() -> node.handle(request(
                Map.of("email", "labs@example.edu"),
                Map.of("action", "search_works")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("query is required for search_works action.", e.getMessage());
    }

    @Test
    void unknownActionFailsWithoutHttp() {
        StudioQuackError e = thrown(() -> node.handle(request(Map.of(), Map.of("action", "search_topics")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Unknown action: search_topics"), e.getMessage());
    }

    private static Map<String, Object> request(Map<String, Object> cfg, Map<String, Object> input) {
        List<Object> params = cfg.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", e.getKey());
            row.put("value", e.getValue());
            return (Object) row;
        }).toList();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        request.put("inputVariables", Map.of("input", input));
        return request;
    }

    private static StudioQuackError thrown(java.util.function.Supplier<Map<String, Object>> call) {
        return assertThrows(StudioQuackError.class, call::get);
    }
}
