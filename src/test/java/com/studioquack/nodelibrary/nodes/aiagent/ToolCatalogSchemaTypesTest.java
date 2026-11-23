package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tool parameter schemas reach strict providers (SGLang, vLLM) that validate them against the JSON
 * Schema meta-schema and reject the whole request with 400 on a single non-standard type — so every
 * type the agent sends must be one of JSON Schema's own seven.
 */
class ToolCatalogSchemaTypesTest {

    private static final Set<String> JSON_SCHEMA_TYPES =
            Set.of("string", "number", "integer", "boolean", "object", "array", "null");

    @Test
    void nodeFieldTypesBecomeStandardJsonSchemaTypes() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("attachment", Map.of("type", "file"));
        props.put("apiSecret", Map.of("type", "password"));
        props.put("title", Map.of("type", "String"));
        props.put("count", Map.of("type", "Integer"));
        props.put("ratio", Map.of("type", "float"));
        props.put("payload", Map.of("type", "json"));
        props.put("either", Map.of("type", List.of("file", "null")));
        Map<String, Object> schema = Map.of("type", "object", "properties", props);

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> out = (Map<String, Map<String, Object>>)
                ((Map<String, Object>) ToolCatalog.normalizeSchemaTypes(schema)).get("properties");

        assertEquals("string", out.get("attachment").get("type"));
        assertEquals("string", out.get("apiSecret").get("type"));
        assertEquals("string", out.get("title").get("type"));
        assertEquals("integer", out.get("count").get("type"));
        assertEquals("number", out.get("ratio").get("type"));
        assertEquals("string", out.get("payload").get("type"));
        assertEquals(List.of("string", "null"), out.get("either").get("type"));
        for (Map<String, Object> p : out.values()) {
            Object t = p.get("type");
            if (t instanceof String s) {
                assertTrue(JSON_SCHEMA_TYPES.contains(s), s);
            }
        }
    }

    @Test
    void bracketToolSchemaAddsFlatAliasForBracketShortCodes() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("operation", Map.of("type", "string"))));
        Map<String, Map<String, Object>> targetFields = Map.of(
                "filter[status]", Map.of("type", "string", "description", "Subscriber status"));
        ToolCatalog.patchBracketToolSchemaAliases(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("filter_status"));
    }

    @Test
    void newRelicToolSchemaExposesEventsAndIncidentId() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("operation", Map.of("type", "string"))));
        Map<String, Map<String, Object>> targetFields = Map.of(
                "incident_id", Map.of("type", "string"),
                "events", Map.of("type", "list"),
                "query", Map.of("type", "string"));
        ToolCatalog.patchNewRelicToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("incident_id"));
        assertTrue(props.containsKey("events_json"));
        assertTrue(props.containsKey("query"));
    }

    @Test
    void jinaMessagesArrayItemsAreObjects() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("messages", Map.of("type", "array", "items", Map.of("type", "string")));
        props.put("embed_input", Map.of("type", "string"));
        schema.put("properties", props);
        Map<String, Map<String, Object>> targetFields = Map.of(
                "messages", Map.of("type", "list"),
                "embed_input", Map.of("type", "string"));
        ToolCatalog.patchJinaAiToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> messages = (Map<String, Object>) ((Map<?, ?>) schema.get("properties")).get("messages");
        assertEquals("object", ((Map<?, ?>) messages.get("items")).get("type"));
    }

    @Test
    void tursoToolSchemaExposesSqlAndRequestsArray() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("operation", Map.of("type", "string"))));
        Map<String, Map<String, Object>> targetFields = Map.of(
                "operation", Map.of("type", "string"),
                "sql", Map.of("type", "string"),
                "requests", Map.of("type", "list"));
        ToolCatalog.patchTursoToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("sql"));
        assertEquals("array", ((Map<?, ?>) props.get("requests")).get("type"));
    }

    @Test
    void stabilityAiOperationEnumOnToolSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("operation", Map.of("type", "string"))));
        Map<String, Map<String, Object>> targetFields = Map.of(
                "operation", Map.of("type", "string"),
                "id", Map.of("type", "string", "description", "Generation id for get_result"),
                "prompt", Map.of("type", "string"));
        ToolCatalog.patchStabilityAiToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) ((Map<?, ?>) schema.get("properties")).get("operation");
        assertTrue(op.get("enum") instanceof List<?>);
        assertTrue(((List<?>) op.get("enum")).contains("get_result"));
    }

    @Test
    void ideogramToolSchemaExposesOperationAndUpscaleFields() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("text_prompt", Map.of("type", "string"))));
        Map<String, Map<String, Object>> targetFields = Map.of(
                "text_prompt", Map.of("type", "string"),
                "operation", Map.of("type", "string"),
                "generation_id", Map.of("type", "string"),
                "image_request", Map.of("type", "string"),
                "image_file", Map.of("type", "file"));
        ToolCatalog.patchIdeogramToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("operation"));
        assertTrue(props.containsKey("image_request"));
        assertTrue(((Map<?, ?>) props.get("operation")).containsKey("enum"));
    }

    @Test
    void compressionToolSchemaDocumentsSandboxFile() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("file", Map.of("type", "string"))));
        schema.put("required", new ArrayList<>());
        Map<String, Map<String, Object>> targetFields = Map.of(
                "file", Map.of("type", "file"),
                "format", Map.of("type", "string"),
                "file_name", Map.of("type", "string"));
        ToolCatalog.patchCompressionToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(((Map<?, ?>) props.get("format")).containsKey("enum"));
        assertTrue(((List<?>) schema.get("required")).contains("file"));
    }

    @Test
    void calComToolSchemaUsesObjectAttendeeAndHttpOperationEnum() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("uid", Map.of("type", "string"))));
        schema.put("required", new ArrayList<>());
        Map<String, Map<String, Object>> targetFields = Map.of(
                "operation", Map.of("type", "string"),
                "uid", Map.of("type", "string"),
                "eventTypeId", Map.of("type", "number"),
                "id", Map.of("type", "string"));
        ToolCatalog.patchCalComToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertEquals("object", ((Map<?, ?>) props.get("attendee")).get("type"));
        @SuppressWarnings("unchecked")
        List<String> enums = (List<String>) ((Map<?, ?>) props.get("operation")).get("enum");
        assertTrue(!enums.contains("listen"));
        assertTrue(enums.contains("reschedule-booking"));
    }

    @Test
    void clickupToolSchemaExposesOperationAndTeamHints() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("list_id", Map.of("type", "string"))));
        schema.put("required", new ArrayList<>());
        Map<String, Map<String, Object>> targetFields = new LinkedHashMap<>();
        targetFields.put("list_id", Map.of("type", "string"));
        targetFields.put("team_id", Map.of("type", "string"));
        targetFields.put("timer_id", Map.of("type", "string"));
        targetFields.put("operation", Map.of("type", "string"));
        ToolCatalog.patchClickupToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(((Map<?, ?>) props.get("operation")).containsKey("enum"));
        assertTrue(((List<?>) schema.get("required")).contains("operation"));
        assertTrue(String.valueOf(((Map<?, ?>) props.get("team_id")).get("description")).contains("goal_create"));
    }

    @Test
    void jwtToolSchemaExposesOperationEnum() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("claims", Map.of("type", "object"))));
        Map<String, Map<String, Object>> targetFields = Map.of(
                "claims", Map.of("type", "object"),
                "token", Map.of("type", "string"),
                "operation", Map.of("type", "string"));
        ToolCatalog.patchJwtToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(((Map<?, ?>) props.get("operation")).containsKey("enum"));
    }

    @Test
    void gumroadToolSchemaExposesProductId() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("operation", Map.of("type", "string"))));
        Map<String, Map<String, Object>> targetFields = Map.of(
                "operation", Map.of("type", "string"),
                "product_id", Map.of("type", "string", "description", "Gumroad product id for subscriber_list"),
                "page", Map.of("type", "number"));
        ToolCatalog.patchGumroadToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("product_id"));
        assertTrue(((Map<?, ?>) props.get("operation")).containsKey("enum"));
    }

    @Test
    void convertFileToolSchemaExposesOperationEnum() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of(
                "file", Map.of("type", "string"),
                "sheet", Map.of("type", "string"))));
        Map<String, Map<String, Object>> targetFields = Map.of(
                "file", Map.of("type", "file"),
                "sheet", Map.of("type", "string"),
                "operation", Map.of("type", "string"));
        ToolCatalog.patchConvertFileToolSchemaOperation(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) ((Map<?, ?>) schema.get("properties")).get("operation");
        assertTrue(op.get("enum") instanceof List<?>);
        assertTrue(((List<?>) op.get("enum")).contains("to_csv"));
    }

    @Test
    void groqToolSchemaExposesOperationEnum() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("messages", Map.of("type", "array"))));
        Map<String, Map<String, Object>> targetFields = Map.of(
                "messages", Map.of("type", "list"),
                "input", Map.of("type", "string"));
        ToolCatalog.patchGroqOperationToolSchemaEnum(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) ((Map<?, ?>) schema.get("properties")).get("operation");
        assertTrue(op.get("enum") instanceof List<?>);
        assertTrue(((List<?>) op.get("enum")).contains("list_models"));
    }

    @Test
    void oauthGrantTypeToolSchemaAddsCamelAlias() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("grant_type", Map.of("type", "string", "description", "OAuth grant"));
        schema.put("properties", props);
        Map<String, Map<String, Object>> targetFields = Map.of(
                "grant_type", Map.of("type", "string"));
        ToolCatalog.patchOAuthGrantTypeToolSchemaAlias(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) schema.get("properties");
        assertTrue(out.containsKey("grantType"));
    }

    @Test
    void gitLabToolSchemaIncludesWriteParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("operation", Map.of("type", "string"));
        props.put("id", Map.of("type", "string"));
        props.put("file_path", Map.of("type", "string"));
        props.put("ref", Map.of("type", "string"));
        props.put("content", Map.of("type", "string"));
        schema.put("properties", props);
        Map<String, Map<String, Object>> targetFields = new LinkedHashMap<>();
        targetFields.put("operation", Map.of("type", "string"));
        targetFields.put("id", Map.of("type", "string"));
        targetFields.put("iid", Map.of("type", "string"));
        targetFields.put("file_path", Map.of("type", "string"));
        targetFields.put("tag_name", Map.of("type", "string", "description", "tag"));
        targetFields.put("branch", Map.of("type", "string"));
        ToolCatalog.patchGitLabToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) schema.get("properties");
        assertTrue(out.containsKey("branch"));
        assertTrue(out.containsKey("job_id"));
        assertTrue(out.containsKey("pipeline_id"));
        assertTrue(out.containsKey("body"));
        assertTrue(out.containsKey("source_branch"));
    }

    @Test
    void cohereToolsArrayItemsAreObjects() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tools", Map.of("type", "array", "items", Map.of("type", "string")));
        schema.put("properties", props);
        Map<String, Map<String, Object>> targetFields = Map.of("tools", Map.of("type", "list"));
        ToolCatalog.patchCohereChatToolSchemaItems(schema, targetFields);
        @SuppressWarnings("unchecked")
        Map<String, Object> tools = (Map<String, Object>) ((Map<?, ?>) schema.get("properties")).get("tools");
        assertEquals("object", ((Map<?, ?>) tools.get("items")).get("type"));
    }
}
