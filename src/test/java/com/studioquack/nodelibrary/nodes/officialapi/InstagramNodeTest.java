package com.studioquack.nodelibrary.nodes.officialapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Graph version guard: invalid path segments fall back to current Graph v26.0
 * (https://developers.facebook.com/docs/graph-api/changelog/versions).
 */
class InstagramNodeTest {

    private final InstagramNode node = new InstagramNode();

    @Test
    void sanitizeVersionKeepsCurrentGraph() {
        assertEquals("v26.0", InstagramNode.sanitizeVersion("v26.0"));
    }

    @Test
    void sanitizeVersionKeepsExplicitOlderPin() {
        assertEquals("v23.0", InstagramNode.sanitizeVersion("v23.0"));
        assertEquals("v25.0", InstagramNode.sanitizeVersion("v25.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../", "v26.0/me", "v26.0/../", "latest", "26.0"})
    void sanitizeVersionRejectsPathInjection(String raw) {
        assertEquals("v26.0", InstagramNode.sanitizeVersion(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void sanitizeVersionFallsBackWhenBlank(String raw) {
        assertEquals("v26.0", InstagramNode.sanitizeVersion(raw));
    }

    @Test
    void liveStatsUsesSanitizedFallbackVersionWithoutHttp() {
        Map<String, Object> ports = node.handle(request(
                Map.of("mode", "live", "version", "../"),
                Map.of("action", "stats")), null);
        Map<String, Object> result = resultOf(ports);
        assertEquals("https://graph.facebook.com/v26.0", result.get("base_url"));
    }

    private static Map<String, Object> request(Map<String, Object> cfg, Map<String, Object> input) {
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : cfg.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", e.getKey());
            row.put("value", e.getValue());
            params.add(row);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        request.put("inputVariables", Map.of("input", input));
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultOf(Map<String, Object> ports) {
        Object output = ports.get("output");
        assertTrue(output instanceof Map, "output must be an object: " + ports);
        Object result = ((Map<String, Object>) output).get("result");
        assertTrue(result instanceof Map, "result must be an object: " + ports);
        return (Map<String, Object>) result;
    }
}
