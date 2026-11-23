package com.studioquack.nodelibrary.nodes.aiagent;

import com.studioquack.nodelibrary.core.OfficialApiNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adapts AI Agent memory/data intents onto a generic cache or vector-DB
 * port schema. History windowing and pre-RAG stay in {@link AgentMemory} /
 * {@link AgentRag}; this class only shapes the helper {@code execute} payload
 * so a Redis cache, PGVector, or any compatible node can consume it through
 * its normal INPUT fields — never {@code add_message} / {@code get_history}.
 */
final class AgentPortMapping {

    static final int DEFAULT_TOP_K = 5;
    static final String MEMORY_KEY_PREFIX = "sq:memory:";

    private static final List<String> QUERY_OPS = List.of(
            "vector.query", "collection.query", "collection.search", "entities.search",
            "QUERY", "query", "search");
    private static final List<String> APPEND_OPS = List.of("RPUSH", "LPUSH", "rpush", "lpush");
    private static final List<String> RANGE_OPS = List.of("LRANGE", "lrange");

    private AgentPortMapping() {
        // namespace — never instantiated
    }

    static String memoryKey(String conversationId) {
        String id = conversationId == null ? "" : conversationId.strip();
        return MEMORY_KEY_PREFIX + id;
    }

    /** One chat turn → generic list-append ({@code RPUSH} + serialized message). */
    static Map<String, Object> memoryAppend(AgentSession session, Map<String, Object> message) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("operation", "RPUSH");
        canonical.put("key", memoryKey(session == null ? "" : session.conversationId));
        canonical.put("value", message == null ? Map.of() : message);
        String nodeId = session == null ? "" : session.memoryId;
        return project(session, "memory", nodeId, canonical);
    }

    /** Load the conversation list ({@code LRANGE 0 -1}). */
    static Map<String, Object> memoryRead(AgentSession session) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("operation", "LRANGE");
        canonical.put("key", memoryKey(session == null ? "" : session.conversationId));
        canonical.put("start", 0);
        canonical.put("stop", -1);
        String nodeId = session == null ? "" : session.memoryId;
        return project(session, "memory", nodeId, canonical);
    }

    /**
     * Semantic search → generic vector query. {@code extra} may carry
     * {@code embedding}/{@code topK}/{@code filter} from a model tool call;
     * pre-RAG typically has only {@code query}.
     */
    static Map<String, Object> dataQuery(AgentSession session, String nodeId, String query,
                                         Map<String, Object> extra) {
        Map<String, Object> src = extra == null ? Map.of() : extra;
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("operation", "QUERY");
        canonical.put("query", query == null ? "" : query);
        List<Object> embedding = asNumberList(first(src, "embedding", "vector", "query_embeddings", "embeddings"));
        if (!embedding.isEmpty()) {
            canonical.put("embedding", embedding);
        }
        canonical.put("topK", intFrom(src, DEFAULT_TOP_K, "topK", "top_k", "n_results", "limit", "k"));
        Object filter = first(src, "filter", "where", "metadata_filter");
        canonical.put("filter", filter instanceof Map<?, ?> ? AgentJson.asMap(filter) : Map.of());
        return project(session, "data", nodeId, canonical);
    }

    /**
     * Keep only fields the peer INPUT schema declares. Unknown peers (empty
     * schema) receive the canonical payload unchanged.
     */
    static Map<String, Object> project(AgentSession session, String portValue, String nodeId,
                                       Map<String, Object> canonical) {
        List<Map<String, Object>> fields = peerFields(session, portValue, nodeId);
        return projectOnto(canonical, fields);
    }

    static Map<String, Object> projectOnto(Map<String, Object> canonical, List<Map<String, Object>> fields) {
        if (canonical == null) {
            return Map.of();
        }
        if (fields == null || fields.isEmpty()) {
            return new LinkedHashMap<>(canonical);
        }
        Set<String> codes = new LinkedHashSet<>();
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        for (Map<String, Object> f : fields) {
            String code = AgentJson.str(f.get("shortCode")).strip();
            if (!code.isEmpty()) {
                codes.add(code);
                byCode.put(code, f);
            }
        }
        if (codes.isEmpty()) {
            return new LinkedHashMap<>(canonical);
        }
        Map<String, Object> c = withAgentToolAliases(new LinkedHashMap<>(canonical));
        Map<String, Object> out = new LinkedHashMap<>();
        putMapped(out, codes, byCode, c, "operation",
                List.of("operation", "command", "op"));
        putMapped(out, codes, byCode, c, "key", List.of("key"));
        putMapped(out, codes, byCode, c, "value", List.of("value", "message"));
        putMapped(out, codes, byCode, c, "start", List.of("start", "begin"));
        putMapped(out, codes, byCode, c, "stop", List.of("stop", "end"));
        putMapped(out, codes, byCode, c, "ttl_seconds", List.of("ttl_seconds", "ttl"));
        putMapped(out, codes, byCode, c, "id",
                List.of("id", "job_id", "task_id", "file_id", "uuid", "mailbox_id", "parser_id",
                        "document_id"));
        putMapped(out, codes, byCode, c, "name",
                List.of("name", "mailbox_name", "parser_name", "title"));
        putMapped(out, codes, byCode, c, "url",
                List.of("url", "link", "website", "page_url", "source_url"));
        putMapped(out, codes, byCode, c, "query", List.of("query", "text", "q"));
        putMapped(out, codes, byCode, c, "embedding",
                List.of("embedding", "vector", "query_embeddings", "embeddings", "values"));
        putMapped(out, codes, byCode, c, "topK",
                List.of("topK", "top_k", "n_results", "limit", "k"));
        putMapped(out, codes, byCode, c, "filter",
                List.of("filter", "where", "metadata_filter"));
        Map<String, Object> flat = OfficialApiNode.flattenNestedToolInput(c);
        for (String code : codes) {
            if (!out.containsKey(code) && flat.containsKey(code)) {
                Object v = flat.get(code);
                if (v != null) {
                    out.put(code, v);
                }
            }
        }
        if (codes.contains("file_path") && codes.contains("iid")) {
            for (String key : List.of(
                    "branch", "commit_message", "tag_name", "title", "source_branch", "target_branch", "body",
                    "job_id", "pipeline_id", "description", "state_event", "labels", "name", "message", "content")) {
                if (!out.containsKey(key) && flat.containsKey(key) && flat.get(key) != null) {
                    out.put(key, flat.get(key));
                }
            }
        }
        if (codes.contains("table") && codes.contains("record")) {
            for (String key : List.of("record", "fn", "id", "select", "limit", "offset", "order", "prefer")) {
                if (!out.containsKey(key) && flat.containsKey(key) && flat.get(key) != null) {
                    out.put(key, flat.get(key));
                }
            }
        }
        return out.isEmpty() ? flat : out;
    }

    /**
     * Copies common LLM tool argument names into the INPUT shortCodes the peer node expects
     * ({@code job_id} → {@code id}, {@code link} → {@code url}) when the canonical key is absent.
     */
    static Map<String, Object> withAgentToolAliases(Map<String, Object> canonical) {
        if (canonical == null) {
            return Map.of();
        }
        copyIfAbsent(canonical, "id", "job_id", "task_id", "file_id", "uuid", "mailbox_id", "parser_id",
                "document_id");
        copyIfAbsent(canonical, "name", "mailbox_name", "parser_name", "title");
        copyIfAbsent(canonical, "url", "link", "website", "page_url", "source_url");
        return canonical;
    }

    private static void copyIfAbsent(Map<String, Object> m, String canonical, String... aliases) {
        if (m == null || m.containsKey(canonical)) {
            return;
        }
        for (String a : aliases) {
            if (a != null && !a.isBlank() && m.containsKey(a) && m.get(a) != null) {
                m.put(canonical, m.get(a));
                return;
            }
        }
    }

    static List<Map<String, Object>> peerFields(AgentSession session, String portValue, String nodeId) {
        if (session == null || session.helper == null || nodeId == null || nodeId.isBlank()) {
            return List.of();
        }
        try {
            List<Object> details = session.helper.getConnectionDetailListByPortValue(portValue);
            if (details == null) {
                return List.of();
            }
            String want = nodeId.strip();
            for (Object d : details) {
                Map<String, Object> cd = AgentJson.asMap(d);
                if (!want.equals(AgentJson.str(cd.get("nodeId")).strip())) {
                    continue;
                }
                Map<String, Object> portDto = AgentJson.asMap(cd.get("portDto"));
                List<Map<String, Object>> fields = new ArrayList<>();
                for (String key : List.of("schema", "inputSchema")) {
                    for (Object f : AgentJson.asList(portDto.get(key))) {
                        if (f instanceof Map<?, ?>) {
                            fields.add(AgentJson.asMap(f));
                        }
                    }
                }
                return fields;
            }
        } catch (Exception e) {
            AgentJson.log(session.helper, "ai-agent port_schema_read type=" + e.getClass().getSimpleName());
        }
        return List.of();
    }

    /**
     * Unwrap a generic cache/vector response into a list (history rows or
     * retrieved documents). Accepts {@code history}, {@code value}, {@code matches},
     * {@code documents}, and {@code data.*} envelopes.
     */
    static List<Object> unwrapListPayload(Object resp) {
        Object unwrapped = AgentJson.sanitizeForJson(AgentJson.unwrapListEnvelope(resp));
        Object payload = pickListish(unwrapped);
        if (payload instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(parseIfJson(item));
            }
            return out;
        }
        if (payload == null) {
            return List.of();
        }
        return List.of(parseIfJson(payload));
    }

    private static void putMapped(Map<String, Object> out, Set<String> codes,
                                  Map<String, Map<String, Object>> byCode,
                                  Map<String, Object> canonical, String srcKey,
                                  List<String> destAliases) {
        if (!canonical.containsKey(srcKey)) {
            return;
        }
        Object value = canonical.get(srcKey);
        if (value == null) {
            return;
        }
        if ("embedding".equals(srcKey) && value instanceof List<?> el && el.isEmpty()) {
            return;
        }
        if ("filter".equals(srcKey) && value instanceof Map<?, ?> fm && fm.isEmpty()) {
            // still emit empty filter when the peer declares the field
        }
        String dest = null;
        for (String alias : destAliases) {
            if (codes.contains(alias)) {
                dest = alias;
                break;
            }
        }
        if (dest == null) {
            return;
        }
        if ("operation".equals(srcKey)) {
            value = remapOperation(AgentJson.str(value), byCode.get(dest));
        }
        if ("embedding".equals(srcKey) && "query_embeddings".equals(dest) && value instanceof List<?> el
                && !el.isEmpty() && !(el.get(0) instanceof List<?>)) {
            value = List.of(el);
        }
        out.put(dest, value);
    }

    static String remapOperation(String canonical, Map<String, Object> field) {
        String want = canonical == null ? "" : canonical.strip();
        if (field == null) {
            return want;
        }
        String desc = AgentJson.str(field.get("description")) + " " + AgentJson.str(field.get("value"));
        List<String> candidates = switch (want.toUpperCase(Locale.ROOT)) {
            case "QUERY", "SEARCH" -> QUERY_OPS;
            case "RPUSH", "LPUSH" -> APPEND_OPS;
            case "LRANGE" -> RANGE_OPS;
            default -> List.of(want);
        };
        for (String c : candidates) {
            if (containsToken(desc, c) || c.equals(want)) {
                if (containsToken(desc, c)) {
                    return c;
                }
            }
        }
        for (String c : candidates) {
            if (containsToken(desc, c)) {
                return c;
            }
        }
        return want;
    }

    private static boolean containsToken(String hay, String needle) {
        if (hay == null || needle == null || needle.isEmpty()) {
            return false;
        }
        return hay.contains(needle);
    }

    private static Object pickListish(Object unwrapped) {
        if (unwrapped instanceof List<?>) {
            return unwrapped;
        }
        if (!(unwrapped instanceof Map<?, ?>)) {
            return unwrapped;
        }
        Map<String, Object> map = AgentJson.asMap(unwrapped);
        for (String key : List.of("history", "matches", "documents", "value", "items")) {
            if (map.get(key) != null) {
                return map.get(key);
            }
        }
        Object result = map.get("result");
        if (result instanceof Map<?, ?>) {
            return pickListish(result);
        }
        if (result instanceof List<?>) {
            return result;
        }
        Object data = map.get("data");
        if (data instanceof Map<?, ?> || data instanceof List<?>) {
            return pickListish(data);
        }
        Object response = map.get("response");
        if (response != null) {
            return pickListish(response);
        }
        return unwrapped;
    }

    private static Object parseIfJson(Object item) {
        if (item instanceof String s) {
            String t = s.strip();
            if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
                try {
                    return studioquack.Json.parse(t);
                } catch (Exception ignored) {
                    return item;
                }
            }
        }
        return item;
    }

    private static Object first(Map<String, Object> src, String... keys) {
        if (src == null || keys == null) {
            return null;
        }
        for (String k : keys) {
            if (src.get(k) != null) {
                return src.get(k);
            }
        }
        return null;
    }

    static List<Object> asNumberList(Object raw) {
        if (raw instanceof List<?> list) {
            if (!list.isEmpty() && list.get(0) instanceof List<?>) {
                return asNumberList(list.get(0));
            }
            List<Object> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Number || o instanceof String) {
                    out.add(o);
                }
            }
            return out;
        }
        return List.of();
    }

    private static int intFrom(Map<String, Object> src, int fallback, String... keys) {
        Object v = first(src, keys);
        if (v instanceof Number n) {
            int i = n.intValue();
            return i > 0 ? i : fallback;
        }
        if (v != null) {
            try {
                int i = Integer.parseInt(AgentJson.str(v).strip());
                return i > 0 ? i : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
