package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wikipedia pre-request validation raises {@link StudioQuackError} (INVALID_INPUT). */
class WikipediaOfficialApiNodeTest {

    private OfficialApiNode node;

    @BeforeEach
    void setUp() {
        OfficialApiSpec spec = OfficialApiSpec.load("wikipedia");
        node = new OfficialApiNode("604ef300-e831-4a10-bf4b-0149599e4826", spec);
    }

    private static Map<String, Object> request(Map<String, Object> input) {
        return Map.of("inputVariables", Map.of("input", input));
    }

    @Test
    void missingSrsearchReturnsErrorOnOutputPort() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(
                request(Map.of("operation", "search")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals(
                "srsearch is required for operation search (set on INPUT or in configurationParameters)",
                e.getMessage());
    }

    @Test
    void unknownOperationReturnsErrorOnOutputPort() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(
                request(Map.of("operation", "nope")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Unknown operation"));
    }
}
