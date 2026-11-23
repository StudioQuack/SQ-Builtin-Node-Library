package com.studioquack.nodelibrary.nodes.aiagent;

import com.studioquack.nodelibrary.core.OfficialApiNode;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DuckTape tool catalog + {@code delegate_to_agent} + pre-RAG (Python
 * {@code build_tool_definitions} / {@code build_delegation_tool} /
 * resolvers). Pre-RAG lives in {@link AgentRag}.
 */
final class ToolCatalog {

    private static final Map<String, String> TYPE_MAP = Map.ofEntries(
            Map.entry("list", "array"),
            Map.entry("dict", "object"),
            Map.entry("int", "integer"),
            Map.entry("float", "number"),
            Map.entry("bool", "boolean"),
            Map.entry("str", "string"),
            Map.entry("text", "string"),
            Map.entry("long", "integer"),
            Map.entry("double", "number"),
            Map.entry("map", "object"),
            Map.entry("hashmap", "object"),
            Map.entry("datetime", "string"),
            Map.entry("date", "string"),
            Map.entry("timestamp", "string"));

    /**
     * JSON Schema's own type names. A strict provider (SGLang, vLLM) validates every tool's
     * parameters against the JSON Schema meta-schema and rejects the WHOLE request with 400 when
     * one property says e.g. {@code "file"} or {@code "password"} — so the agent gets no answer.
     */
    private static final java.util.Set<String> JSON_SCHEMA_TYPES =
            java.util.Set.of("string", "number", "integer", "boolean", "object", "array", "null");

    /** A standard JSON Schema type for a node field type; file refs, secrets and the rest travel as strings. */
    static String jsonSchemaType(String raw) {
        String lower = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (JSON_SCHEMA_TYPES.contains(lower)) {
            return lower;
        }
        return TYPE_MAP.getOrDefault(lower, "string");
    }

    /** LLM-facing defs including optional {@code delegate_to_agent}. */
    final List<Map<String, Object>> definitions;

    private ToolCatalog(List<Map<String, Object>> definitions) {
        this.definitions = definitions == null ? List.of() : List.copyOf(definitions);
    }

    /** Discover DuckTape tools + data + delegation for one invoke. */
    static ToolCatalog build(StudioQuackHelper helper) {
        List<Map<String, Object>> defs = new ArrayList<>(buildToolDefinitions(helper));
        Map<String, Object> delegation = buildDelegationTool(helper);
        if (delegation != null && !delegation.isEmpty()) {
            defs.add(delegation);
        }
        return new ToolCatalog(defs);
    }

    /** Slim schemas for the model FUNCTION payload ({@code name}/{@code description}/{@code request}). */
    List<Object> llmTools() {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> d : definitions) {
            Map<String, Object> slim = new LinkedHashMap<>();
            slim.put("name", d.get("name"));
            slim.put("description", d.get("description"));
            slim.put("request", d.get("request"));
            out.add(slim);
        }
        return out;
    }

    /** Resolve a tool name (exact, then aliases). {@code null} when unknown. */
    Map<String, Object> resolve(String name) {
        return resolveToolDefinition(name, definitions).def;
    }

    /**
     * Pick a connected sub-agent by exact {@code nodeId}, then unique compact-name match.
     */
    static Map<String, Object> resolveSubAgent(String agentId, List<Object> details) {
        if (agentId == null || agentId.isBlank() || details == null) {
            return null;
        }
        String want = agentId.strip();
        for (Object d : details) {
            Map<String, Object> m = AgentJson.asMap(d);
            if (want.equals(AgentJson.str(m.get("nodeId")))) {
                return m;
            }
        }
        String key = AgentJson.compactLookupText(want);
        Map<String, Object> hit = null;
        int hits = 0;
        for (Object d : details) {
            Map<String, Object> m = AgentJson.asMap(d);
            if (key.equals(AgentJson.compactLookupText(m.get("nodeId")))
                    || key.equals(AgentJson.compactLookupText(m.get("nodeName")))) {
                hit = m;
                hits++;
            }
        }
        return hits == 1 ? hit : null;
    }

    /** Designer params on a connection-detail row ({@code role}/{@code instructions}/{@code prompt}). */
    static Map<String, Object> configMap(StudioQuackHelper helper, Map<String, Object> target) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (helper == null || target == null) {
            return out;
        }
        List<Object> cfgList = helper.getConnectionDetailParameters(target);
        if (cfgList == null) {
            return out;
        }
        for (Object p : cfgList) {
            Map<String, Object> pm = AgentJson.asMap(p);
            String sc = AgentJson.str(pm.get("shortCode"));
            if (!sc.isBlank()) {
                Object v = pm.get("value") != null ? pm.get("value") : pm.get("defaultValue");
                out.put(sc, v);
            }
        }
        return out;
    }

    static Object normalizeSchemaTypes(Object schema) {
        if (schema instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String k = AgentJson.str(e.getKey());
                Object v = e.getValue();
                if ("type".equals(k) && v instanceof String vs) {
                    out.put(k, jsonSchemaType(vs));
                } else if ("type".equals(k) && v instanceof List<?> types) {
                    List<Object> nt = new ArrayList<>();
                    for (Object t : types) {
                        String st = jsonSchemaType(AgentJson.str(t));
                        if (!nt.contains(st)) {
                            nt.add(st);
                        }
                    }
                    out.put(k, nt);
                } else if (("properties".equals(k) || "patternProperties".equals(k)) && v instanceof Map<?, ?> props) {
                    Map<String, Object> np = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> pe : props.entrySet()) {
                        np.put(AgentJson.str(pe.getKey()), normalizeSchemaTypes(pe.getValue()));
                    }
                    out.put(k, np);
                } else if ("items".equals(k)) {
                    out.put(k, normalizeSchemaTypes(v));
                } else if (("allOf".equals(k) || "anyOf".equals(k) || "oneOf".equals(k)) && v instanceof List<?> lst) {
                    List<Object> nl = new ArrayList<>();
                    for (Object s : lst) {
                        nl.add(normalizeSchemaTypes(s));
                    }
                    out.put(k, nl);
                } else if ("additionalProperties".equals(k) && v instanceof Map) {
                    out.put(k, normalizeSchemaTypes(v));
                } else {
                    out.put(k, v);
                }
            }
            if ("array".equals(out.get("type")) && !out.containsKey("items")) {
                out.put("items", Map.of("type", "string"));
            }
            return out;
        }
        if (schema instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object i : list) {
                out.add(normalizeSchemaTypes(i));
            }
            return out;
        }
        return schema;
    }

    /**
     * Cohere Chat v2 {@code tools} must be JSON objects; default list fields map to string items.
     */
    /**
     * Bracketed INPUT shortCodes ({@code filter[name]}) get a plain alias on tool schemas ({@code filter_name}).
     */
    static void patchBracketToolSchemaAliases(Map<String, Object> schema,
                                              Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null || targetFields.isEmpty()) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        boolean changed = false;
        for (Map.Entry<String, Map<String, Object>> e : targetFields.entrySet()) {
            String fn = e.getKey();
            if (fn == null || !fn.contains("[") || !fn.contains("]")) {
                continue;
            }
            String alias = OfficialApiNode.bracketFieldAlias(fn);
            if (alias.isBlank() || alias.equals(fn) || props.containsKey(alias)) {
                continue;
            }
            Map<String, Object> info = e.getValue();
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonSchemaType(AgentJson.str(info.get("type"))));
            Object desc = info.get("description");
            if (desc instanceof String ds && !ds.isBlank()) {
                prop.put("description", ds + " (alias for wire key " + fn + ")");
            } else {
                prop.put("description", "Alias for wire key " + fn);
            }
            props.put(alias, prop);
            changed = true;
        }
        if (changed) {
            schema.put("properties", props);
        }
    }

    /**
     * Stable Image tools must expose {@code operation} as an enum so agents can call {@code get_result}
     * without a {@code prompt}.
     */
    static void patchTursoToolSchemaItems(Map<String, Object> schema,
                                          Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null || !targetFields.containsKey("requests")) {
            return;
        }
        if (!targetFields.containsKey("sql") && !targetFields.containsKey("operation")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        if (!props.containsKey("sql") && targetFields.containsKey("sql")) {
            props.put("sql", Map.of("type", "string",
                    "description", "SQL for execute.sql (pipeline built with trailing close)"));
        }
        Map<String, Object> requests = new LinkedHashMap<>(AgentJson.asMap(props.get("requests")));
        if (requests.isEmpty()) {
            requests.put("type", "array");
        } else if (!"array".equals(AgentJson.str(requests.get("type")))) {
            requests.put("type", "array");
        }
        requests.putIfAbsent("items", Map.of("type", "object"));
        props.put("requests", requests);
        schema.put("properties", props);
    }

    static void patchStabilityAiToolSchemaItems(Map<String, Object> schema,
                                                Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("operation")
                || !targetFields.containsKey("id")
                || !targetFields.containsKey("prompt")) {
            return;
        }
        Object idDesc = targetFields.get("id").get("description");
        if (!(idDesc instanceof String ds) || !ds.contains("get_result")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        Map<String, Object> op = new LinkedHashMap<>(AgentJson.asMap(props.get("operation")));
        if (op.isEmpty()) {
            op.put("type", "string");
        }
        op.put("enum", List.of("generate", "upscale", "edit", "control", "get_result"));
        op.put("description", "generate | upscale | edit | control | get_result — get_result needs id only");
        props.put("operation", op);
        schema.put("properties", props);
    }

    static void patchNewRelicToolSchemaItems(Map<String, Object> schema,
                                             Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || (!targetFields.containsKey("incident_id") && !targetFields.containsKey("events"))) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        if (targetFields.containsKey("incident_id") && !props.containsKey("incident_id")) {
            props.put("incident_id", Map.of("type", "string",
                    "description", "incident_get REST v2 alert incident id"));
        }
        if (targetFields.containsKey("query") && !props.containsKey("query")) {
            props.put("query", Map.of("type", "string",
                    "description", "nrql_query NerdGraph GraphQL document"));
        }
        if (targetFields.containsKey("events")) {
            Map<String, Object> events = new LinkedHashMap<>(AgentJson.asMap(props.get("events")));
            if (events.isEmpty()) {
                events.put("type", "array");
            }
            events.putIfAbsent("items", Map.of("type", "object"));
            props.put("events", events);
            props.putIfAbsent("events_json", Map.of("type", "string",
                    "description", "event_ingest JSON array as string when array param is unavailable"));
        }
        schema.put("properties", props);
    }

    private static final List<String> GUMROAD_TOOL_INPUT_KEYS = List.of(
            "product_id", "page", "after", "before", "email");

    private static final List<String> IDEOGRAM_TOOL_INPUT_KEYS = List.of(
            "operation", "generation_id", "image_request", "image_file", "image", "json_prompt", "webhook_url");

    static void patchIdeogramToolSchemaItems(Map<String, Object> schema,
                                             Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null || !targetFields.containsKey("text_prompt")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        for (String key : IDEOGRAM_TOOL_INPUT_KEYS) {
            if (targetFields.containsKey(key)) {
                putToolSchemaField(props, key, targetFields.get(key));
            }
        }
        Map<String, Object> op = new LinkedHashMap<>(AgentJson.asMap(props.get("operation")));
        if (op.isEmpty()) {
            op.put("type", "string");
            Object desc = targetFields.get("operation") == null ? null
                    : targetFields.get("operation").get("description");
            if (desc instanceof String ds && !ds.isBlank()) {
                op.put("description", ds);
            }
        }
        op.put("enum", List.of("generate", "generate_async", "get_task", "remix", "upscale"));
        props.put("operation", op);
        schema.put("properties", props);
    }

    private static final List<String> CLICKUP_TOOL_OPERATIONS = List.of(
            "task_create", "task_get", "task_list", "task_update", "task_delete", "task_members",
            "task_custom_field", "list_create", "list_get", "list_list", "list_update", "list_delete",
            "folder_create", "folder_get", "folder_update", "folder_delete", "comment_create",
            "comment_get", "comment_update", "comment_delete", "checklist_create", "checklist_delete",
            "checklist_item_create", "checklist_item_update", "goal_create", "goal_get", "goal_update",
            "goal_delete", "key_result_create", "key_result_delete", "tag_add", "tag_remove",
            "dependency_create", "dependency_delete", "time_entry_create", "time_entry_get",
            "time_entry_update", "time_entry_delete", "time_start", "time_stop", "listen");

    private static final String CLICKUP_OPERATION_DESC =
            "ClickUp v2 op. listen is BPMN-only (errors at node — use a message event). "
                    + "team_id required: goal_create, time_entry_create/get/update/delete, time_start, time_stop "
                    + "(INPUT team_id or config team_id). space_id required: folder_create. "
                    + "Other ops: path ids per api_spec (task_id, list_id, folder_id, …).";

    /** ClickUp 41-op facade: operation enum plus team_id/space_id hints agents miss on slim DuckTape. */
    static void patchClickupToolSchemaItems(Map<String, Object> schema,
                                          Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("list_id")
                || !targetFields.containsKey("team_id")
                || !targetFields.containsKey("timer_id")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        for (String key : List.of("team_id", "space_id", "task_id", "list_id", "folder_id", "goal_id",
                "timer_id", "comment_text", "name")) {
            putToolSchemaField(props, key, targetFields.get(key));
        }
        Map<String, Object> op = new LinkedHashMap<>(AgentJson.asMap(props.get("operation")));
        if (op.isEmpty()) {
            op.put("type", "string");
        }
        op.put("enum", CLICKUP_TOOL_OPERATIONS);
        op.put("description", CLICKUP_OPERATION_DESC);
        props.put("operation", op);
        if (props.containsKey("team_id")) {
            Map<String, Object> team = new LinkedHashMap<>(AgentJson.asMap(props.get("team_id")));
            team.put("description",
                    "Workspace id. Required for goal_create and all time_entry_* / time_start / time_stop "
                            + "(INPUT or configurationParameters team_id).");
            props.put("team_id", team);
        }
        if (props.containsKey("space_id")) {
            Map<String, Object> space = new LinkedHashMap<>(AgentJson.asMap(props.get("space_id")));
            space.put("description", "Space id. Required for folder_create.");
            props.put("space_id", space);
        }
        schema.put("properties", props);
        @SuppressWarnings("unchecked")
        List<Object> required = schema.get("required") instanceof List<?> r
                ? new ArrayList<>(r) : new ArrayList<>();
        if (!required.contains("operation")) {
            required.add("operation");
        }
        schema.put("required", required);
    }

    private static final List<String> CAL_COM_TOOL_OPERATIONS = List.of(
            "create-booking", "get-booking", "list-bookings", "reschedule-booking", "cancel-booking",
            "list-event-types", "get-event-type");

    private static final String CAL_COM_OPERATION_DESC =
            "Cal.com API v2 HTTP operation. Booking webhooks are BPMN message events only — not listen. "
                    + "get-event-type uses id (event type id). create-booking uses eventTypeId or "
                    + "eventTypeSlug+username/teamSlug. Bearer api_key lives in configurationParameters.";

    /** Cal.com: object attendee, operation enum without ingress listen, id vs eventTypeId hints. */
    static void patchCalComToolSchemaItems(Map<String, Object> schema,
                                           Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("eventTypeId")
                || !targetFields.containsKey("uid")
                || !targetFields.containsKey("operation")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        Map<String, Object> op = new LinkedHashMap<>(AgentJson.asMap(props.get("operation")));
        if (op.isEmpty()) {
            op.put("type", "string");
        }
        op.put("enum", CAL_COM_TOOL_OPERATIONS);
        op.put("description", CAL_COM_OPERATION_DESC);
        props.put("operation", op);
        Map<String, Object> attendee = new LinkedHashMap<>(AgentJson.asMap(props.get("attendee")));
        attendee.put("type", "object");
        attendee.put("description",
                "create-booking attendee object: name, timeZone (required), email, phoneNumber, language. "
                        + "Use a nested object, not a plain string (JSON-encoded object strings are parsed).");
        props.put("attendee", attendee);
        if (props.containsKey("id")) {
            Map<String, Object> id = new LinkedHashMap<>(AgentJson.asMap(props.get("id")));
            id.put("description",
                    "Event type id for get-event-type (numeric string). Not the booking uid.");
            props.put("id", id);
        }
        if (props.containsKey("eventTypeId")) {
            Map<String, Object> et = new LinkedHashMap<>(AgentJson.asMap(props.get("eventTypeId")));
            et.put("description",
                    "Event type id for create-booking when not using eventTypeSlug+username/teamSlug.");
            props.put("eventTypeId", et);
        }
        schema.put("properties", props);
        @SuppressWarnings("unchecked")
        List<Object> required = schema.get("required") instanceof List<?> r
                ? new ArrayList<>(r) : new ArrayList<>();
        if (!required.contains("operation")) {
            required.add("operation");
        }
        schema.put("required", required);
    }

    /** Compression agent tools: format enum and sandbox path contract on {@code file}. */
    static void patchCompressionToolSchemaItems(Map<String, Object> schema,
                                              Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("file") || !targetFields.containsKey("format")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        Map<String, Object> file = new LinkedHashMap<>(AgentJson.asMap(props.get("file")));
        if (file.isEmpty()) {
            file.put("type", "string");
        }
        file.put("description",
                "Source file: absolute path under /sandbox/files, or ContentBlock with base64 data "
                        + "(not a bare ref-id string).");
        props.put("file", file);
        Map<String, Object> format = new LinkedHashMap<>(AgentJson.asMap(props.get("format")));
        if (format.isEmpty()) {
            format.put("type", "string");
        }
        format.put("enum", List.of("zip", "gzip", "tar", "tgz"));
        format.put("description", "Archive format for compress/decompress.");
        props.put("format", format);
        if (targetFields.containsKey("file_name")) {
            putToolSchemaField(props, "file_name", targetFields.get("file_name"));
            Map<String, Object> fn = new LinkedHashMap<>(AgentJson.asMap(props.get("file_name")));
            fn.put("description",
                    "Output filename (single segment). Required non-empty when provided; no / or ..");
            props.put("file_name", fn);
        }
        schema.put("properties", props);
        @SuppressWarnings("unchecked")
        List<Object> required = schema.get("required") instanceof List<?> r
                ? new ArrayList<>(r) : new ArrayList<>();
        if (!required.contains("file")) {
            required.add("file");
        }
        schema.put("required", required);
    }

    /** JWT tools infer sign vs verify, but agents still need an explicit {@code operation} enum. */
    static void patchJwtToolSchemaItems(Map<String, Object> schema,
                                        Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("claims") || !targetFields.containsKey("token")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        Map<String, Object> op = new LinkedHashMap<>(AgentJson.asMap(props.get("operation")));
        if (op.isEmpty()) {
            op.put("type", "string");
            Object desc = targetFields.get("operation") == null ? null
                    : targetFields.get("operation").get("description");
            if (desc instanceof String ds && !ds.isBlank()) {
                op.put("description", ds);
            } else {
                op.put("description", "sign | verify | decode — omit to infer from claims vs token");
            }
        }
        op.put("enum", List.of("sign", "verify", "decode"));
        props.put("operation", op);
        schema.put("properties", props);
    }

    static void patchGumroadToolSchemaItems(Map<String, Object> schema,
                                            Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("operation")
                || !targetFields.containsKey("product_id")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        for (String key : GUMROAD_TOOL_INPUT_KEYS) {
            if (targetFields.containsKey(key)) {
                putToolSchemaField(props, key, targetFields.get(key));
            }
        }
        Map<String, Object> op = new LinkedHashMap<>(AgentJson.asMap(props.get("operation")));
        if (op.isEmpty()) {
            op.put("type", "string");
        }
        op.put("enum", List.of("sale_list", "product_list", "subscriber_list"));
        props.put("operation", op);
        schema.put("properties", props);
    }

    private static final List<String> SUPABASE_TOOL_INPUT_KEYS = List.of(
            "operation", "table", "record", "fn", "id", "select", "limit", "offset", "order", "prefer");

    private static final List<String> GITLAB_TOOL_INPUT_KEYS = List.of(
            "branch", "commit_message", "tag_name", "title", "source_branch", "target_branch", "body", "job_id",
            "pipeline_id", "description", "state_event", "labels", "name", "message", "membership", "search", "page",
            "per_page", "encoding", "author_email", "author_name", "assignee_id", "milestone_id", "confidential",
            "internal", "created_at");

    /**
     * GitLab multi-op tools need every INPUT shortCode on the agent schema; slim DuckTape mappings
     * often expose only {@code operation}/{@code id}/{@code file_path}/… and runners drop the rest.
     */
    /**
     * Supabase PostgREST tools need {@code record} and query params on the agent schema; slim mappings
     * often drop them so insert/update/rpc always fail required checks.
     */
    static void patchSupabaseToolSchemaItems(Map<String, Object> schema,
                                             Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("table")
                || !targetFields.containsKey("record")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        for (String key : SUPABASE_TOOL_INPUT_KEYS) {
            putToolSchemaField(props, key, targetFields.get(key));
        }
        Map<String, Object> record = new LinkedHashMap<>(AgentJson.asMap(props.get("record")));
        if (!record.isEmpty()) {
            record.put("type", "object");
            Object desc = targetFields.get("record") == null ? null
                    : targetFields.get("record").get("description");
            if (desc instanceof String ds && !ds.isBlank()) {
                record.put("description", ds);
            } else if (!record.containsKey("description")) {
                record.put("description",
                        "Row, row array, or RPC args object for table.insert / table.update / rpc");
            }
            props.put("record", record);
        }
        schema.put("properties", props);
    }

    static void patchGitLabToolSchemaItems(Map<String, Object> schema,
                                           Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("file_path")
                || !targetFields.containsKey("iid")
                || !targetFields.containsKey("tag_name")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        for (Map.Entry<String, Map<String, Object>> e : targetFields.entrySet()) {
            putToolSchemaField(props, e.getKey(), e.getValue());
        }
        for (String key : GITLAB_TOOL_INPUT_KEYS) {
            putToolSchemaField(props, key, targetFields.get(key));
        }
        schema.put("properties", props);
    }

    private static void putToolSchemaField(Map<String, Object> props, String key,
                                           Map<String, Object> fieldInfo) {
        if (key == null || key.isBlank() || props.containsKey(key)) {
            return;
        }
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", jsonSchemaType(fieldInfo == null ? "string" : AgentJson.str(fieldInfo.get("type"))));
        if (fieldInfo != null) {
            Object desc = fieldInfo.get("description");
            if (desc instanceof String ds && !ds.isBlank()) {
                prop.put("description", ds);
            }
        }
        if (!prop.containsKey("description")) {
            prop.put("description", "GitLab API parameter " + key);
        }
        props.put(key, prop);
    }

    static void patchJinaAiToolSchemaItems(Map<String, Object> schema,
                                           Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("embed_input")
                || !targetFields.containsKey("messages")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        Map<String, Object> messages = new LinkedHashMap<>(AgentJson.asMap(props.get("messages")));
        if (messages.isEmpty() || !"array".equals(AgentJson.str(messages.get("type")))) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("role", Map.of("type", "string", "description", "user | assistant | system"));
        itemProps.put("content", Map.of("type", "string", "description", "Message text"));
        item.put("properties", itemProps);
        messages.put("items", item);
        props.put("messages", messages);
        schema.put("properties", props);
    }

    /**
     * OAuth token INPUT exposes {@code grant_type}; agents often send camelCase {@code grantType}.
     */
    /**
     * Groq multi-mode LLM tools need {@code operation} on the agent schema (chat | stt | tts | list_models).
     */
    /**
     * Convert File: agent tool defs often map only {@code file}/{@code sheet}; expose {@code operation} enum.
     */
    static void patchConvertFileToolSchemaOperation(Map<String, Object> schema,
                                                    Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("file")
                || !targetFields.containsKey("sheet")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        Map<String, Object> op = new LinkedHashMap<>(AgentJson.asMap(props.get("operation")));
        if (op.isEmpty()) {
            op.put("type", "string");
            op.put("description",
                    "Target format. Omitted uses instance config (default to_csv).");
        }
        op.put("enum", List.of(
                "to_csv", "to_xlsx", "to_ods", "to_json", "to_html", "to_rtf", "to_text", "to_ics"));
        props.put("operation", op);
        schema.put("properties", props);
    }

    static void patchGroqOperationToolSchemaEnum(Map<String, Object> schema,
                                               Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null
                || !targetFields.containsKey("messages")
                || !targetFields.containsKey("input")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        Map<String, Object> op = new LinkedHashMap<>(AgentJson.asMap(props.get("operation")));
        if (op.isEmpty()) {
            op.put("type", "string");
            op.put("description",
                    "chat | stt | tts | list_models — overrides instance default chat");
        }
        op.put("enum", List.of("chat", "stt", "tts", "list_models"));
        props.put("operation", op);
        schema.put("properties", props);
    }

    static void patchOAuthGrantTypeToolSchemaAlias(Map<String, Object> schema,
                                                   Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null || !targetFields.containsKey("grant_type")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        if (props.containsKey("grantType")) {
            return;
        }
        Map<String, Object> wire = AgentJson.asMap(props.get("grant_type"));
        Map<String, Object> alias = new LinkedHashMap<>();
        alias.put("type", jsonSchemaType(AgentJson.str(wire.get("type"))));
        Object desc = wire.get("description");
        if (desc instanceof String ds && !ds.isBlank()) {
            alias.put("description", ds + " (alias for grant_type)");
        } else {
            alias.put("description", "Alias for grant_type on OAuth token requests");
        }
        props.put("grantType", alias);
        schema.put("properties", props);
    }

    static void patchCohereChatToolSchemaItems(Map<String, Object> schema,
                                                       Map<String, Map<String, Object>> targetFields) {
        if (schema == null || targetFields == null || !targetFields.containsKey("tools")) {
            return;
        }
        Map<String, Object> props = AgentJson.asMap(schema.get("properties"));
        Map<String, Object> tools = new LinkedHashMap<>(AgentJson.asMap(props.get("tools")));
        if (tools.isEmpty() || !"array".equals(AgentJson.str(tools.get("type")))) {
            return;
        }
        tools.put("items", Map.of("type", "object"));
        props.put("tools", tools);
    }

    static Set<String> buildToolAliases(String toolName, Map<String, Object> connectionDetail) {
        Set<String> aliases = new LinkedHashSet<>();
        if (toolName != null && !toolName.isBlank()) {
            aliases.add(toolName);
            String snake = AgentJson.snakeAlias(toolName);
            if (!snake.isEmpty()) {
                aliases.add(snake);
            }
        }
        for (String v : AgentJson.collectConnectionText(connectionDetail, 0)) {
            if (v != null && !v.isBlank()) {
                aliases.add(v);
                String s = AgentJson.snakeAlias(v);
                if (!s.isEmpty()) {
                    aliases.add(s);
                }
            }
        }
        aliases.removeIf(a -> a == null || a.isBlank());
        return aliases;
    }

    /**
     * Build LLM-facing tool defs from DuckTape on {@code tools} + {@code data} ports.
     * Requires name AND description AND at least one mapped field (Python {@code has_config}).
     */
    static List<Map<String, Object>> buildToolDefinitions(StudioQuackHelper helper) {
        List<Map<String, Object>> defs = new ArrayList<>();
        if (helper == null) {
            return defs;
        }
        for (String portValue : List.of("tools", "data")) {
            List<Object> connectionDetails = helper.getConnectionDetailListByPortValue(portValue);
            if (connectionDetails == null) {
                continue;
            }
            for (Object cdObj : connectionDetails) {
                Map<String, Object> cd = AgentJson.asMap(cdObj);
                Object nodeId = cd.get("nodeId");
                Map<String, Object> portDto = AgentJson.asMap(cd.get("portDto"));
                String targetPortValue = AgentJson.str(portDto.get("value"));
                List<Object> duckTape = helper.getDucktapeConfigurations(
                        AgentJson.str(nodeId), portValue, targetPortValue);

                String toolName = null;
                String toolDescription = "";
                Map<String, Object> inputSchema = new LinkedHashMap<>();
                inputSchema.put("type", "object");
                inputSchema.put("properties", new LinkedHashMap<String, Object>());
                inputSchema.put("required", new ArrayList<Object>());
                boolean hasConfig = false;

                Map<String, Map<String, Object>> targetFields = new LinkedHashMap<>();
                for (Object fieldObj : AgentJson.asList(portDto.get("schema"))) {
                    if (!(fieldObj instanceof Map<?, ?>)) {
                        continue;
                    }
                    Map<String, Object> field = AgentJson.asMap(fieldObj);
                    String fn = AgentJson.str(field.get("shortCode"));
                    if (fn.isBlank()) {
                        continue;
                    }
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("type", field.get("type") == null ? "string" : field.get("type"));
                    info.put("required", Boolean.TRUE.equals(field.get("required")) || "true".equalsIgnoreCase(AgentJson.str(field.get("required"))));
                    info.put("description", field.get("description") == null ? "" : AgentJson.str(field.get("description")));
                    targetFields.put(fn, info);
                }

                for (Object dtObj : duckTape) {
                    Map<String, Object> dt = AgentJson.asMap(dtObj);
                    String srcPath = AgentJson.str(dt.get("sourcePath"));
                    Object srcVal = dt.get("sourcePathValue");
                    String tgtPath = AgentJson.str(dt.get("targetPath"));
                    if ("tools".equals(portValue)) {
                        if ("request".equals(srcPath) && !tgtPath.isBlank()) {
                            Map<String, Object> info = targetFields.getOrDefault(tgtPath, Map.of());
                            Map<String, Object> prop = new LinkedHashMap<>();
                            prop.put("type", info.get("type") == null ? "string" : info.get("type"));
                            Object desc = info.get("description");
                            if (desc instanceof String ds && !ds.isBlank()) {
                                prop.put("description", ds);
                            }
                            AgentJson.asMap(inputSchema.get("properties")).put(tgtPath, prop);
                            if (Boolean.TRUE.equals(info.get("required"))) {
                                AgentJson.asList(inputSchema.get("required")).add(tgtPath);
                            }
                            hasConfig = true;
                        } else if ("name".equals(srcPath)) {
                            toolName = srcVal == null ? null : AgentJson.str(srcVal);
                            hasConfig = true;
                        } else if ("description".equals(srcPath)) {
                            toolDescription = srcVal == null ? "" : AgentJson.str(srcVal);
                        }
                    } else if ("data".equals(portValue)) {
                        if ("query".equals(srcPath)) {
                            Map<String, Object> q = new LinkedHashMap<>();
                            q.put("type", "string");
                            AgentJson.asMap(inputSchema.get("properties")).put("query", q);
                            AgentJson.asList(inputSchema.get("required")).add("query");
                            hasConfig = true;
                        } else if ("name".equals(srcPath)) {
                            toolName = srcVal == null ? null : AgentJson.str(srcVal);
                            hasConfig = true;
                        } else if ("description".equals(srcPath)) {
                            toolDescription = srcVal == null ? "" : AgentJson.str(srcVal);
                        }
                    }
                }

                if ("tools".equals(portValue) && !targetFields.isEmpty()) {
                    Map<String, Object> props = AgentJson.asMap(inputSchema.get("properties"));
                    List<Object> required = AgentJson.asList(inputSchema.get("required"));
                    for (Map.Entry<String, Map<String, Object>> tf : targetFields.entrySet()) {
                        String fn = tf.getKey();
                        if (props.containsKey(fn)) {
                            continue;
                        }
                        Map<String, Object> info = tf.getValue();
                        Map<String, Object> prop = new LinkedHashMap<>();
                        prop.put("type", info.get("type") == null ? "string" : info.get("type"));
                        Object desc = info.get("description");
                        if (desc instanceof String ds && !ds.isBlank()) {
                            prop.put("description", ds);
                        }
                        props.put(fn, prop);
                        if (Boolean.TRUE.equals(info.get("required"))) {
                            required.add(fn);
                        }
                    }
                    if (!props.isEmpty()) {
                        hasConfig = true;
                    }
                }

                if (hasConfig && toolName != null && !toolName.isBlank() && toolDescription != null && !toolDescription.isBlank()) {
                    try {
                        Object norm = normalizeSchemaTypes(inputSchema);
                        Map<String, Object> normMap = AgentJson.asMap(norm);
                        patchBracketToolSchemaAliases(normMap, targetFields);
                        patchOAuthGrantTypeToolSchemaAlias(normMap, targetFields);
                        patchGroqOperationToolSchemaEnum(normMap, targetFields);
                        patchConvertFileToolSchemaOperation(normMap, targetFields);
                        patchCohereChatToolSchemaItems(normMap, targetFields);
                        patchJinaAiToolSchemaItems(normMap, targetFields);
                        patchNewRelicToolSchemaItems(normMap, targetFields);
                        patchStabilityAiToolSchemaItems(normMap, targetFields);
                        patchTursoToolSchemaItems(normMap, targetFields);
                        patchGitLabToolSchemaItems(normMap, targetFields);
                        patchSupabaseToolSchemaItems(normMap, targetFields);
                        patchGumroadToolSchemaItems(normMap, targetFields);
                        patchIdeogramToolSchemaItems(normMap, targetFields);
                        patchJwtToolSchemaItems(normMap, targetFields);
                        patchClickupToolSchemaItems(normMap, targetFields);
                        patchCalComToolSchemaItems(normMap, targetFields);
                        patchCompressionToolSchemaItems(normMap, targetFields);
                        Map<String, Object> def = new LinkedHashMap<>();
                        def.put("name", toolName);
                        def.put("description", toolDescription);
                        def.put("request", norm);
                        def.put("node_id", nodeId);
                        def.put("port_value", portValue);
                        List<String> aliases = new ArrayList<>(buildToolAliases(toolName, cd));
                        aliases.sort(String::compareTo);
                        def.put("aliases", aliases);
                        defs.add(def);
                    } catch (Exception e) {
                        AgentJson.log(helper, "ai-agent tool_def_error nodeId=" + nodeId
                                + " type=" + e.getClass().getSimpleName());
                    }
                }
            }
        }
        return defs;
    }

    static Map<String, Object> buildDelegationTool(StudioQuackHelper helper) {
        if (helper == null) {
            return null;
        }
        List<Object> details = helper.getConnectionDetailListByPortValue("sub_agent");
        if (details == null || details.isEmpty()) {
            return null;
        }
        List<String> summaries = new ArrayList<>();
        List<String> agentIds = new ArrayList<>();
        for (Object cdObj : details) {
            Map<String, Object> cd = AgentJson.asMap(cdObj);
            Object nodeId = cd.get("nodeId");
            if (nodeId != null && !AgentJson.str(nodeId).isBlank()) {
                agentIds.add(AgentJson.str(nodeId));
            }
            List<Object> cfgList = helper.getConnectionDetailParameters(cd);
            Map<String, Object> cfgMap = new LinkedHashMap<>();
            for (Object p : cfgList) {
                Map<String, Object> pm = AgentJson.asMap(p);
                String sc = AgentJson.str(pm.get("shortCode"));
                if (!sc.isBlank()) {
                    cfgMap.put(sc, pm.get("value"));
                }
            }
            String roleA = AgentJson.str(cfgMap.get("role")).strip();
            String instrA = AgentJson.str(cfgMap.get("instructions")).strip();
            if (roleA.length() > 250) {
                roleA = roleA.substring(0, 250) + "...";
            }
            if (instrA.length() > 350) {
                instrA = instrA.substring(0, 350) + "...";
            }
            summaries.add("agent_id=" + nodeId + ": role='" + roleA + "', instructions='" + instrA + "'");
        }
        if (summaries.isEmpty()) {
            return null;
        }
        StringBuilder agentsText = new StringBuilder();
        for (String s : summaries) {
            agentsText.append("- ").append(s).append('\n');
        }
        if (agentsText.length() > 0) {
            agentsText.setLength(agentsText.length() - 1);
        }
        Map<String, Object> agentIdProp = new LinkedHashMap<>();
        agentIdProp.put("type", "string");
        agentIdProp.put("description",
                "Exact target sub-agent node ID. Must be copied verbatim from the Available agents list; do not use names, roles, file names, or tool names.");
        agentIdProp.put("enum", agentIds);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("agent_id", agentIdProp);
        properties.put("role", Map.of("type", "string",
                "description", "Optional role override for the sub-agent. If sub-agent's role is not specified, use this field."));
        properties.put("instructions", Map.of("type", "string",
                "description", "Optional instructions override for the sub-agent."));
        properties.put("prompt", Map.of("type", "string",
                "description", "Complete task content to pass to the sub-agent. Include all context the sub-agent needs."));
        properties.put("files", Map.of("type", "array",
                "description", "Optional multimodal attachments to forward to the sub-agent.",
                "items", Map.of("type", "object")));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("agent_id", "prompt"));
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "delegate_to_agent");
        tool.put("description", AgentConfig.DELEGATION_DESCRIPTION_TEMPLATE.replace("{agents}", agentsText.toString()));
        tool.put("request", schema);
        return tool;
    }

    /** Exact > alias_exact > alias_contains > alias_tokens. */
    static Resolved resolveToolDefinition(String toolName, List<Map<String, Object>> toolDefinitions) {
        String requested = toolName == null ? "" : toolName.strip();
        if (requested.isEmpty()) {
            return Resolved.miss("missing");
        }
        if (toolDefinitions != null) {
            for (Map<String, Object> td : toolDefinitions) {
                if (AgentJson.str(td.get("name")).strip().equals(requested)) {
                    return new Resolved(td, AgentJson.str(td.get("name")), "exact");
                }
            }
        }
        String reqKey = AgentJson.compactLookupText(requested);
        if (reqKey.isEmpty()) {
            return Resolved.miss("missing");
        }
        List<Resolved> matches = new ArrayList<>();
        if (toolDefinitions != null) {
            for (Map<String, Object> td : toolDefinitions) {
                List<Object> aliasVals = new ArrayList<>();
                aliasVals.add(td.get("name"));
                aliasVals.addAll(AgentJson.asList(td.get("aliases")));
                Set<String> aliasKeys = new LinkedHashSet<>();
                for (Object v : aliasVals) {
                    if (v != null) {
                        String ck = AgentJson.compactLookupText(v);
                        if (!ck.isEmpty()) {
                            aliasKeys.add(ck);
                        }
                    }
                }
                if (aliasKeys.contains(reqKey)) {
                    matches.add(new Resolved(td, AgentJson.str(td.get("name")), "alias_exact"));
                    continue;
                }
                if (reqKey.length() >= 8) {
                    boolean contains = false;
                    for (String ak : aliasKeys) {
                        if (ak.contains(reqKey)) {
                            contains = true;
                            break;
                        }
                    }
                    if (contains) {
                        matches.add(new Resolved(td, AgentJson.str(td.get("name")), "alias_contains"));
                        continue;
                    }
                }
                Set<String> reqTokens = new LinkedHashSet<>(AgentJson.lookupTokens(requested));
                if (reqTokens.size() >= 2) {
                    for (Object alias : aliasVals) {
                        if (new LinkedHashSet<>(AgentJson.lookupTokens(alias)).containsAll(reqTokens)) {
                            matches.add(new Resolved(td, AgentJson.str(td.get("name")), "alias_tokens"));
                            break;
                        }
                    }
                }
            }
        }
        Map<String, Resolved> unique = new LinkedHashMap<>();
        for (Resolved r : matches) {
            String key = r.name + ":" + r.def.get("node_id") + ":" + r.def.get("port_value");
            unique.put(key, r);
        }
        if (unique.size() == 1) {
            return unique.values().iterator().next();
        }
        if (unique.size() > 1) {
            return Resolved.miss("ambiguous");
        }
        return Resolved.miss("not_found");
    }

    /** Package-visible fields so {@code AgentTools} can read them without accessors. */
    static final class Resolved {
        final Map<String, Object> def;
        final String name;
        final String reason;

        Resolved(Map<String, Object> def, String name, String reason) {
            this.def = def;
            this.name = name == null ? "" : name;
            this.reason = reason == null ? "" : reason;
        }

        static Resolved miss(String reason) {
            return new Resolved(null, "", reason);
        }
    }
}
