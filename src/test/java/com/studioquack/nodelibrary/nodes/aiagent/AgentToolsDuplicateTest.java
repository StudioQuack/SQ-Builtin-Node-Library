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

    @Test
    void mutatingCallDetectionAndSameToolCacheEviction() {
        assertTrue(AgentTools.isMutatingCall("activecampaign", Map.of("operation", "contact.delete")));
        assertTrue(AgentTools.isMutatingCall("activecampaign", Map.of("operation", "contact.update")));
        assertFalse(AgentTools.isMutatingCall("activecampaign", Map.of("operation", "contact.get")));
        assertFalse(AgentTools.isMutatingCall("activecampaign", Map.of("operation", "contact.list")));

        var cache = new java.util.concurrent.ConcurrentHashMap<String, String>();
        String getKey = "activecampaign\0{\"contactId\":\"2\",\"operation\":\"contact.get\"}";
        String delKey = "activecampaign\0{\"contactId\":\"2\",\"operation\":\"contact.delete\"}";
        String otherKey = "wikipedia\0{\"query\":\"ada\"}";
        cache.put(getKey, "{\"status\":\"ok\"}");
        cache.put(delKey, "{\"status\":\"ok\"}");
        cache.put(otherKey, "{\"status\":\"ok\"}");

        AgentTools.evictOtherCallsForTool(cache, "activecampaign", delKey);

        assertFalse(cache.containsKey(getKey));
        assertTrue(cache.containsKey(delKey));
        assertTrue(cache.containsKey(otherKey));
    }
}
