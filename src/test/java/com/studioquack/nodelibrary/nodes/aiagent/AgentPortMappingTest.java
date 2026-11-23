package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPortMappingTest {

    @Test
    void memoryKeyPrefixesConversationId() {
        assertEquals("sq:memory:abc", AgentPortMapping.memoryKey("abc"));
        assertEquals("sq:memory:", AgentPortMapping.memoryKey(""));
    }

    @Test
    void memoryAppendIsRpushWithoutPeerSchema() {
        AgentSession session = new AgentSession(null, "", "", "", "mem-1", true, "c1");
        Map<String, Object> req = AgentPortMapping.memoryAppend(session, Map.of("role", "user", "content", "hi"));
        assertEquals("RPUSH", req.get("operation"));
        assertEquals("sq:memory:c1", req.get("key"));
        assertEquals("user", ((Map<?, ?>) req.get("value")).get("role"));
        assertFalse(req.containsKey("command"));
        assertFalse(req.containsKey("conversation_id"));
        assertFalse(req.containsKey("message"));
    }

    @Test
    void memoryReadIsLrangeWithoutPeerSchema() {
        AgentSession session = new AgentSession(null, "", "", "", "mem-1", true, "c1");
        Map<String, Object> req = AgentPortMapping.memoryRead(session);
        assertEquals("LRANGE", req.get("operation"));
        assertEquals("sq:memory:c1", req.get("key"));
        assertEquals(0, req.get("start"));
        assertEquals(-1, req.get("stop"));
        assertFalse(req.containsKey("command"));
    }

    @Test
    void projectOntoRedisSchemaKeepsGenericFields() {
        List<Map<String, Object>> fields = List.of(
                Map.of("shortCode", "operation", "description", "EXACT: SET | GET | DEL | RPUSH | LRANGE"),
                Map.of("shortCode", "key"),
                Map.of("shortCode", "value"),
                Map.of("shortCode", "start"),
                Map.of("shortCode", "stop"));
        Map<String, Object> out = AgentPortMapping.projectOnto(
                Map.of("operation", "RPUSH", "key", "k", "value", "v"), fields);
        assertEquals("RPUSH", out.get("operation"));
        assertEquals("k", out.get("key"));
        assertEquals("v", out.get("value"));
    }

    @Test
    void projectOntoPineconeRemapsQueryFields() {
        List<Map<String, Object>> fields = List.of(
                Map.of("shortCode", "operation",
                        "description", "EXACT: index.list | vector.query | vector.upsert"),
                Map.of("shortCode", "vector"),
                Map.of("shortCode", "topK"),
                Map.of("shortCode", "filter"));
        Map<String, Object> canonical = new java.util.LinkedHashMap<>();
        canonical.put("operation", "QUERY");
        canonical.put("query", "order 42");
        canonical.put("embedding", List.of(0.1, 0.2));
        canonical.put("topK", 5);
        canonical.put("filter", Map.of("source", "orders"));
        Map<String, Object> out = AgentPortMapping.projectOnto(canonical, fields);
        assertEquals("vector.query", out.get("operation"));
        assertEquals(List.of(0.1, 0.2), out.get("vector"));
        assertEquals(5, out.get("topK"));
        assertEquals(Map.of("source", "orders"), out.get("filter"));
        assertFalse(out.containsKey("query"));
    }

    @Test
    void projectOntoMapsJobIdAndLinkOntoCloudConvertFields() {
        List<Map<String, Object>> fields = List.of(
                Map.of("shortCode", "operation"),
                Map.of("shortCode", "id"),
                Map.of("shortCode", "url"));
        Map<String, Object> out = AgentPortMapping.projectOnto(
                Map.of("operation", "job_get", "job_id", "abc123", "link", "https://x.example"), fields);
        assertEquals("job_get", out.get("operation"));
        assertEquals("abc123", out.get("id"));
        assertEquals("https://x.example", out.get("url"));
    }

    @Test
    void projectOntoGitLabForwardsWriteParamsWhenPeerSchemaIsSlim() {
        List<Map<String, Object>> fields = List.of(
                Map.of("shortCode", "operation"),
                Map.of("shortCode", "id"),
                Map.of("shortCode", "file_path"),
                Map.of("shortCode", "ref"),
                Map.of("shortCode", "content"),
                Map.of("shortCode", "iid"));
        Map<String, Object> canonical = new java.util.LinkedHashMap<>();
        canonical.put("operation", "file_create");
        canonical.put("id", "1");
        canonical.put("file_path", "a.md");
        canonical.put("branch", "main");
        canonical.put("commit_message", "test");
        canonical.put("content", "hello");
        Map<String, Object> out = AgentPortMapping.projectOnto(canonical, fields);
        assertEquals("main", out.get("branch"));
        assertEquals("test", out.get("commit_message"));
    }

    @Test
    void projectOntoShippoKeepsPeerInputFieldsBeyondMappedAliases() {
        List<Map<String, Object>> fields = List.of(
                Map.of("shortCode", "operation"),
                Map.of("shortCode", "name"),
                Map.of("shortCode", "street1"),
                Map.of("shortCode", "city"),
                Map.of("shortCode", "zip"),
                Map.of("shortCode", "country"));
        Map<String, Object> canonical = new java.util.LinkedHashMap<>();
        canonical.put("operation", "address_validate");
        canonical.put("name", "John Smith");
        canonical.put("street1", "1600 Pennsylvania Avenue NW");
        canonical.put("city", "Washington");
        canonical.put("zip", "20500");
        canonical.put("country", "US");
        Map<String, Object> out = AgentPortMapping.projectOnto(canonical, fields);
        assertEquals("address_validate", out.get("operation"));
        assertEquals("John Smith", out.get("name"));
        assertEquals("20500", out.get("zip"));
    }

    @Test
    void projectOntoChromaWrapsQueryEmbeddings() {
        List<Map<String, Object>> fields = List.of(
                Map.of("shortCode", "operation", "description", "collection.query | collection.add"),
                Map.of("shortCode", "query_embeddings"),
                Map.of("shortCode", "n_results"),
                Map.of("shortCode", "where"));
        Map<String, Object> canonical = new java.util.LinkedHashMap<>();
        canonical.put("operation", "QUERY");
        canonical.put("embedding", List.of(1, 2));
        canonical.put("topK", 3);
        canonical.put("filter", Map.of("k", "v"));
        Map<String, Object> out = AgentPortMapping.projectOnto(canonical, fields);
        assertEquals("collection.query", out.get("operation"));
        assertEquals(List.of(List.of(1, 2)), out.get("query_embeddings"));
        assertEquals(3, out.get("n_results"));
        assertEquals(Map.of("k", "v"), out.get("where"));
    }

    @Test
    void dataQueryDefaultsTopKAndOmitsEmptyEmbedding() {
        Map<String, Object> req = AgentPortMapping.dataQuery(null, "n1", "bin A", Map.of());
        assertEquals("QUERY", req.get("operation"));
        assertEquals("bin A", req.get("query"));
        assertEquals(5, req.get("topK"));
        assertFalse(req.containsKey("embedding"));
        assertEquals(Map.of(), req.get("filter"));
    }

    @Test
    void unwrapListPayloadReadsGenericEnvelopes() {
        List<Object> fromData = AgentPortMapping.unwrapListPayload(
                Map.of("data", Map.of("value", List.of(Map.of("role", "user", "content", "hi")))));
        assertEquals("hi", ((Map<?, ?>) fromData.get(0)).get("content"));
        List<Object> fromMatches = AgentPortMapping.unwrapListPayload(
                Map.of("matches", List.of(Map.of("document", "x"))));
        assertEquals("x", ((Map<?, ?>) fromMatches.get(0)).get("document"));
        List<Object> fromHistory = AgentPortMapping.unwrapListPayload(
                Map.of("history", List.of("{\"role\":\"user\",\"content\":\"q\"}")));
        assertEquals("user", ((Map<?, ?>) fromHistory.get(0)).get("role"));
    }

    @Test
    void asNumberListFlattensNestedVector() {
        assertEquals(List.of(1, 2), AgentPortMapping.asNumberList(List.of(List.of(1, 2))));
        assertTrue(AgentPortMapping.asNumberList(null).isEmpty());
    }
}
