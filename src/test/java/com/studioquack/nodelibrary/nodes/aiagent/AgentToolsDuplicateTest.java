package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolsDuplicateTest {

    @Test
    void duplicateOfErrorReplaysTheError() {
        String cached = "{\"error\":\"srsearch is required for operation search\"}";
        assertTrue(AgentTools.errorToolContent(cached));
        assertEquals(cached, AgentTools.duplicatePayload("wikipedia", cached));
    }

    @Test
    void duplicateOfSuccessStaysACacheNotice() {
        String cached = "{\"status\":\"ok\",\"data\":{\"title\":\"Ada\"}}";
        assertFalse(AgentTools.errorToolContent(cached));
        Object payload = AgentTools.duplicatePayload("wikipedia", cached);
        assertTrue(payload instanceof Map<?, ?>);
        assertTrue(String.valueOf(((Map<?, ?>) payload).get("info")).contains("already called"));
    }

    @Test
    void inFlightDuplicateStaysACacheNotice() {
        Object payload = AgentTools.duplicatePayload("wikipedia", "");
        assertTrue(String.valueOf(((Map<?, ?>) payload).get("info")).contains("already called"));
    }
}
