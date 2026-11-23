package com.studioquack.nodelibrary.nodes.testemulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Definition-seeded defaults arrive as JSON <em>strings</em> (the node-definition format
 * serialises object values), so {@link EmulatorInput#parse} must parse string payloads for
 * {@code input_data} / {@code configuration} / {@code mock_function_responses} instead of
 * degrading them to empty maps.
 */
class EmulatorInputTest {

    private static Map<String, Object> invoke(Object inputData) {
        return Map.of("inputVariables", Map.of("input", Map.of(
                "language", "python",
                "mode", "script",
                "script", "studioquack.returnResult([])",
                "input_data", inputData)));
    }

    @Test
    void inputDataStringIsJsonParsed() {
        EmulatorInput in = EmulatorInput.parse(invoke("{\"in\":{\"a\":3,\"b\":5}}"), EmulatorConfig.defaults());
        // testemulator Json parses integers as Long
        assertEquals(Map.of("in", Map.of("a", 3L, "b", 5L)), in.inputData());
    }

    @Test
    void inputDataMapPassesThrough() {
        EmulatorInput in = EmulatorInput.parse(invoke(Map.of("in", Map.of("a", 1))), EmulatorConfig.defaults());
        assertEquals(1, ((Map<?, ?>) in.inputData().get("in")).get("a"));
    }

    @Test
    void inputDataBlankStringMeansEmpty() {
        EmulatorInput in = EmulatorInput.parse(invoke("  "), EmulatorConfig.defaults());
        assertTrue(in.inputData().isEmpty());
    }

    @Test
    void inputDataInvalidStringMeansEmpty() {
        EmulatorInput in = EmulatorInput.parse(invoke("not json"), EmulatorConfig.defaults());
        assertTrue(in.inputData().isEmpty());
    }

    @Test
    void configurationAndMocksStringsAreJsonParsed() {
        Map<String, Object> req = Map.of("inputVariables", Map.of("input", Map.of(
                "language", "python",
                "mode", "script",
                "script", "studioquack.returnResult([])",
                "configuration", "{\"k\":\"v\"}",
                "mock_function_responses", "{\"f\":[1,2]}")));
        EmulatorInput in = EmulatorInput.parse(req, EmulatorConfig.defaults());
        assertEquals("v", in.configuration().get("k"));
        assertEquals(List.of(1L, 2L), in.mockFunctionResponses().get("f"));
    }
}
