package com.studioquack.nodelibrary.nodes.aiagent;

import studioquack.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/**
 * Tool + sub-agent dispatch (Python {@code handle_tool_call} / {@code handle_delegation}).
 * Every {@code tool_use} gets a {@code tool} result message (Anthropic-strict).
 */
final class AgentTools {

    private AgentTools() {
        // namespace — never instantiated
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalizeToolArgs(Object args) {
        Object parsed = args;
        if (parsed instanceof String s) {
            try {
                parsed = Json.parse(s);
            } catch (Exception e) {
                parsed = Map.of("value", s);
            }
        }
        if (!(parsed instanceof Map<?, ?>)) {
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("value", parsed);
            parsed = wrap;
        }
        Map<String, Object> map = AgentJson.mutableMap(parsed);
        Object value = map.get("value");
        if (value instanceof Map<?, ?> vm) {
            Map<String, Object> merged = AgentJson.mutableMap(vm);
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!"value".equals(e.getKey())) {
                    merged.put(e.getKey(), e.getValue());
                }
            }
            return merged;
        }
        return map;
    }

    static Map<String, Object> toolResultMessage(String toolCallId, String name, Object content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("content", content instanceof String s ? s : AgentJson.jsonString(content));
        m.put("tool_call_id", toolCallId);
        m.put("name", name);
        return m;
    }

    static Object mergeWrapperDict(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> e : source.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (!target.containsKey(k)) {
                target.put(k, v);
                continue;
            }
            Object existing = target.get(k);
            if (existing instanceof Map<?, ?> em && v instanceof Map<?, ?> vm) {
                mergeWrapperDict(AgentJson.asMap(em), AgentJson.asMap(vm));
            } else if (existing instanceof List<?> el) {
                List<Object> live = el instanceof ArrayList<?> ? (List<Object>) el : new ArrayList<>(el);
                live.add(v);
                target.put(k, live);
            } else {
                target.put(k, List.of(existing, v));
            }
        }
        return target;
    }

    static Object extractWrapperResponsePayload(Object toolResponse) {
        toolResponse = AgentJson.unwrapSingleItemEnvelope(toolResponse);
        if (toolResponse instanceof List<?> list) {
            Map<String, Object> merged = new LinkedHashMap<>();
            List<Object> passthrough = new ArrayList<>();
            for (Object item : list) {
                Object ext = extractWrapperResponsePayload(item);
                if (ext instanceof Map<?, ?> em) {
                    mergeWrapperDict(merged, AgentJson.asMap(em));
                } else {
                    passthrough.add(ext);
                }
            }
            if (!merged.isEmpty() && !passthrough.isEmpty()) {
                merged.put("items", passthrough);
            }
            return merged.isEmpty() ? passthrough : merged;
        }
        if (toolResponse instanceof Map<?, ?> map) {
            if (map.containsKey("response")) {
                return extractWrapperResponsePayload(map.get("response"));
            }
            if (map.size() == 1) {
                String only = map.keySet().iterator().next() == null ? ""
                        : String.valueOf(map.keySet().iterator().next());
                if (only.equals("result") || only.equals("output") || only.equals("data") || only.equals("content")) {
                    return extractWrapperResponsePayload(map.get(only));
                }
            }
            return toolResponse;
        }
        return toolResponse;
    }

    static Object extractToolResponseMessage(Object toolResponse) {
        toolResponse = AgentJson.unwrapSingleItemEnvelope(toolResponse);
        if (toolResponse instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object i : list) {
                out.add(extractToolResponseMessage(i));
            }
            return out;
        }
        if (toolResponse instanceof Map<?, ?> map) {
            for (String key : List.of("response", "output", "result", "data", "content", "final_answer")) {
                if (map.containsKey(key) && map.get(key) != null) {
                    Object value = AgentJson.unwrapSingleItemEnvelope(map.get(key));
                    if (value instanceof List<?> vl) {
                        List<Object> out = new ArrayList<>();
                        for (Object i : vl) {
                            out.add(extractToolResponseMessage(i));
                        }
                        return out;
                    }
                    if (value instanceof Map<?, ?> vm && vm.containsKey("result") && vm.size() == 1) {
                        return extractToolResponseMessage(vm.get("result"));
                    }
                    return value;
                }
            }
            return toolResponse;
        }
        return AgentJson.str(toolResponse);
    }

    static Object formatToolResponseForHistory(Object toolResponse, String portValue) {
        if ("tools".equals(portValue)) {
            return AgentJson.safeJson(extractWrapperResponsePayload(toolResponse));
        }
        return extractToolResponseMessage(toolResponse);
    }

    static Map<String, Object> connectionConfigMap(AgentSession session, Map<String, Object> connectionDetail) {
        List<Object> targetParams = AgentJson.asList(connectionDetail.get("configurationParameters"));
        if (targetParams.isEmpty() && session != null && session.helper != null) {
            try {
                targetParams = session.helper.getConnectionDetailParameters(connectionDetail);
            } catch (Exception e) {
                targetParams = List.of();
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object p : targetParams) {
            Map<String, Object> pm = AgentJson.asMap(p);
            String sc = AgentJson.str(pm.get("shortCode"));
            if (sc.equals("role") || sc.equals("instructions") || sc.equals("prompt")) {
                out.put(sc, pm.get("value"));
            }
        }
        return out;
    }

    static ToolCatalog.Resolved resolveSubAgentConnection(Object targetId, List<Object> subAgentDetails,
                                                          AgentSession session) {
        String targetRaw = targetId == null ? "" : AgentJson.str(targetId).strip();
        if (targetRaw.isEmpty()) {
            return ToolCatalog.Resolved.miss("missing");
        }
        if (subAgentDetails != null) {
            for (Object cdObj : subAgentDetails) {
                Map<String, Object> cd = AgentJson.asMap(cdObj);
                if (AgentJson.str(cd.get("nodeId")).strip().equals(targetRaw)) {
                    return new ToolCatalog.Resolved(cd, AgentJson.str(cd.get("nodeId")), "exact");
                }
            }
        }
        String targetKey = AgentJson.compactLookupText(targetRaw);
        if (targetKey.isEmpty()) {
            return ToolCatalog.Resolved.miss("missing");
        }
        List<ToolCatalog.Resolved> matches = new ArrayList<>();
        if (subAgentDetails != null) {
            for (Object cdObj : subAgentDetails) {
                Map<String, Object> cd = AgentJson.asMap(cdObj);
                String nid = AgentJson.str(cd.get("nodeId")).strip();
                List<String> texts = AgentJson.collectConnectionText(cd, 0);
                Map<String, Object> cfgMap = connectionConfigMap(session, cd);
                for (Object v : cfgMap.values()) {
                    if (v instanceof String vs) {
                        texts.add(vs);
                    }
                }
                List<String> keys = new ArrayList<>();
                for (String t : texts) {
                    String k = AgentJson.compactLookupText(t);
                    if (!k.isEmpty()) {
                        keys.add(k);
                    }
                }
                boolean exact = false;
                for (String k : keys) {
                    if (k.equals(targetKey)) {
                        exact = true;
                        break;
                    }
                }
                if (exact) {
                    matches.add(new ToolCatalog.Resolved(cd, nid, "alias_exact"));
                    continue;
                }
                if (targetKey.length() >= 8) {
                    boolean contains = false;
                    for (String k : keys) {
                        if (k.contains(targetKey)) {
                            contains = true;
                            break;
                        }
                    }
                    if (contains) {
                        matches.add(new ToolCatalog.Resolved(cd, nid, "alias_contains"));
                    }
                }
            }
        }
        Map<String, ToolCatalog.Resolved> unique = new LinkedHashMap<>();
        for (ToolCatalog.Resolved r : matches) {
            if (r.name != null && !r.name.isBlank()) {
                unique.put(r.name, r);
            }
        }
        if (unique.size() == 1) {
            return unique.values().iterator().next();
        }
        if (unique.size() > 1) {
            return ToolCatalog.Resolved.miss("ambiguous");
        }
        return ToolCatalog.Resolved.miss("not_found");
    }

    static Map<String, Object> handleDelegation(AgentSession session, String toolName, Object toolArgs,
                                                String toolId) {
        Map<String, Object> args = normalizeToolArgs(toolArgs);
        Object targetId = args.get("agent_id");
        Map<String, Object> conn = session.helper.findConnectionByPortValue("sub_agent");
        List<Object> subDetails = conn == null ? List.of() : AgentJson.asList(conn.get("connectionDetailList"));
        ToolCatalog.Resolved resolved = resolveSubAgentConnection(targetId, subDetails, session);
        if (resolved.def == null) {
            List<Object> availableIds = new ArrayList<>();
            List<Map<String, Object>> hints = new ArrayList<>();
            for (Object d : subDetails) {
                Map<String, Object> dm = AgentJson.asMap(d);
                availableIds.add(dm.get("nodeId"));
                Map<String, Object> cm = connectionConfigMap(session, dm);
                String r = AgentJson.takeNonemptyStr(cm.get("role"));
                if (r != null && r.length() > 160) {
                    r = r.substring(0, 160) + "...";
                }
                Map<String, Object> hint = new LinkedHashMap<>();
                hint.put("agent_id", dm.get("nodeId"));
                hint.put("role", r);
                hints.add(hint);
            }
            String text = "ambiguous".equals(resolved.reason)
                    ? "The provided agent_id matched multiple connected sub-agents by alias."
                    : "missing".equals(resolved.reason)
                    ? "The delegate_to_agent call did not include a usable agent_id."
                    : "Target agent configuration was not found.";
            String err = text + " Retry delegate_to_agent using agent_id copied exactly from one of the available node IDs. "
                    + "Do not use role names, markdown file names, labels, or tool names as agent_id. "
                    + "Invalid agent_id: " + args.get("agent_id") + ". Available agent IDs: " + availableIds;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", err);
            body.put("available_agents", hints);
            Map<String, Object> msg = toolResultMessage(toolId, toolName, AgentJson.jsonString(body));
            AgentMemory.addHistoryMessage(session, msg);
            return msg;
        }
        Map<String, Object> cfgMap = connectionConfigMap(session, resolved.def);
        Object staticOverride = args.get("prompt");
        if (staticOverride == null) {
            staticOverride = args.get("input");
        }
        if (staticOverride == null) {
            staticOverride = args.get("task");
        }
        if (staticOverride == null) {
            staticOverride = args.get("text");
        }
        if (staticOverride == null) {
            staticOverride = args.get("content");
        }
        String roleToSend = AgentJson.takeNonemptyStr(cfgMap.get("role"));
        if (roleToSend == null && args.get("role") instanceof String rs) {
            roleToSend = rs;
        }
        String instrToSend = AgentJson.takeNonemptyStr(cfgMap.get("instructions"));
        if (instrToSend == null && args.get("instructions") instanceof String is) {
            instrToSend = is;
        }
        List<String> parts = new ArrayList<>();
        if (cfgMap.get("prompt") instanceof String cp && !cp.isBlank()) {
            parts.add(cp.strip());
        }
        if (staticOverride instanceof String so && !so.isBlank()) {
            parts.add(so.strip());
        }
        String staticToSend = parts.isEmpty() ? null : String.join("\n\n", parts);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prompt", staticToSend);
        if (roleToSend != null && !roleToSend.isBlank()) {
            payload.put("role", roleToSend);
        }
        if (instrToSend != null && !instrToSend.isBlank()) {
            payload.put("instructions", instrToSend);
        }
        if (args.get("files") instanceof List<?> fl && !fl.isEmpty()) {
            payload.put("files", fl);
        }
        payload.entrySet().removeIf(e -> e.getValue() == null
                || (e.getValue() instanceof String s && s.isBlank()));
        Object subResp;
        try {
            subResp = session.helper.execute("sub_agent", payload, resolved.name, null);
        } catch (Exception e) {
            Map<String, Object> msg = toolResultMessage(toolId, toolName,
                    AgentJson.jsonString(Map.of("error", "Sub-agent invocation failed: " + e.getMessage())));
            AgentMemory.addHistoryMessage(session, msg);
            return msg;
        }
        subResp = AgentJson.unwrapListEnvelope(subResp);
        Object subMsg;
        if (subResp instanceof Map<?, ?> sm) {
            subMsg = sm.get("final_answer");
            if (subMsg == null && sm.get("response") instanceof Map<?, ?> rm) {
                Object fa = rm.get("final_answer");
                subMsg = fa != null ? fa : rm;
            }
            if (subMsg == null && sm.get("result") instanceof Map<?, ?> resm) {
                Object fa = resm.get("final_answer");
                subMsg = fa != null ? fa : resm;
            }
            if (subMsg == null) {
                subMsg = subResp;
            }
        } else {
            subMsg = subResp;
        }
        Map<String, Object> payloadOut = new LinkedHashMap<>();
        payloadOut.put("result", AgentJson.safeJson(subMsg));
        payloadOut.put("agent", resolved.name);
        Map<String, Object> msg = toolResultMessage(toolId, toolName, AgentJson.jsonString(payloadOut));
        AgentMemory.addHistoryMessage(session, msg);
        return msg;
    }

    static Map<String, Object> handleToolCall(AgentSession session, String toolName, Object toolArgs,
                                              String toolId) {
        Map<String, Object> args = normalizeToolArgs(toolArgs);
        ToolCatalog.Resolved resolved = ToolCatalog.resolveToolDefinition(toolName, session.toolDefinitions);
        if (resolved.def == null) {
            List<Object> available = new ArrayList<>();
            for (Map<String, Object> d : session.toolDefinitions) {
                available.add(d.get("name"));
            }
            String err = ("ambiguous".equals(resolved.reason)
                    ? "Tool '" + toolName + "' matched multiple connected tools."
                    : "Tool '" + toolName + "' not found.")
                    + " Retry with one exact valid tool name from: " + available;
            Map<String, Object> msg = toolResultMessage(toolId, toolName,
                    AgentJson.jsonString(Map.of("error", err)));
            AgentMemory.addHistoryMessage(session, msg);
            return msg;
        }
        Object nodeId = resolved.def.get("node_id");
        String portValue = AgentJson.str(resolved.def.get("port_value"));
        if (nodeId == null || AgentJson.str(nodeId).isBlank() || portValue.isBlank()) {
            Map<String, Object> msg = toolResultMessage(toolId, toolName,
                    AgentJson.jsonString(Map.of("error", "Tool '" + toolName + "' is missing necessary definition info.")));
            AgentMemory.addHistoryMessage(session, msg);
            return msg;
        }
        Map<String, Object> payload;
        if ("data".equals(portValue)) {
            payload = AgentPortMapping.dataQuery(session, AgentJson.str(nodeId),
                    AgentJson.str(args.get("query")), args);
        } else {
            payload = new LinkedHashMap<>(args);
            payload.put("name", resolved.name);
            payload.put("tool_id", toolId);
        }
        Object toolResp;
        try {
            toolResp = session.helper.execute(portValue, payload, AgentJson.str(nodeId), null);
        } catch (Exception e) {
            Map<String, Object> msg = toolResultMessage(toolId, toolName,
                    AgentJson.jsonString(Map.of("error", "Tool error in '" + resolved.name + "': " + e.getMessage())));
            AgentMemory.addHistoryMessage(session, msg);
            return msg;
        }
        toolResp = AgentJson.unwrapSingleItemEnvelope(toolResp);
        if (toolResp instanceof Map<?, ?> tm && tm.get("error") != null) {
            Map<String, Object> msg = toolResultMessage(toolId, toolName,
                    AgentJson.jsonString(Map.of("error",
                            "Tool error in '" + resolved.name + "': " + tm.get("error"))));
            AgentMemory.addHistoryMessage(session, msg);
            return msg;
        }
        Object body = formatToolResponseForHistory(toolResp, portValue);
        String bodyStr = body instanceof String s ? s : AgentJson.jsonString(body);
        Map<String, Object> msg = toolResultMessage(toolId, toolName, bodyStr);
        AgentMemory.addHistoryMessage(session, msg);
        return msg;
    }

    static Map<String, Object> processOne(AgentSession session, Map<String, Object> tc,
                                          ConcurrentMap<String, String> recentCalls) {
        String name = AgentJson.str(tc.get("name"));
        if (name.isBlank()) {
            name = "unknown_tool";
        }
        Map<String, Object> args = normalizeToolArgs(tc.get("args") == null ? Map.of() : tc.get("args"));
        String tid = AgentJson.str(tc.get("id"));
        if (tid.isBlank()) {
            tid = UUID.randomUUID().toString();
        }
        String key;
        try {
            key = name + "\0" + AgentJson.canonicalJson(args);
        } catch (Exception e) {
            key = name + "\0" + args;
        }
        // Empty string marks in-flight. A finished call stores its tool content.
        String existing = recentCalls.putIfAbsent(key, "");
        if (existing != null) {
            Map<String, Object> dup = toolResultMessage(tid, name, duplicatePayload(name, existing));
            AgentMemory.addHistoryMessage(session, dup);
            return dup;
        }
        try {
            Map<String, Object> msg = "delegate_to_agent".equals(name)
                    ? handleDelegation(session, name, args, tid)
                    : handleToolCall(session, name, args, tid);
            recentCalls.put(key, contentOf(msg));
            return msg;
        } catch (Exception e) {
            recentCalls.remove(key);
            Map<String, Object> err = toolResultMessage(tid, name,
                    AgentJson.jsonString(Map.of("error", "Tool exception '" + name + "': " + e.getMessage())));
            AgentMemory.addHistoryMessage(session, err);
            return err;
        }
    }

    /**
     * A repeat of a failed call returns that error. A repeat of a success stays the
     * short cache notice so the model does not re-read a large payload.
     */
    static Object duplicatePayload(String name, String cached) {
        if (cached != null && !cached.isEmpty() && errorToolContent(cached)) {
            return cached;
        }
        return Map.of("info",
                "This tool (" + name + ") was already called with the same arguments. Using cached result.");
    }

    static boolean errorToolContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        try {
            Object parsed = Json.parse(content);
            if (!(parsed instanceof Map<?, ?> m)) {
                return false;
            }
            if (presentError(m.get("error"))) {
                return true;
            }
            Object response = m.get("response");
            if (response instanceof Map<?, ?> rm && presentError(rm.get("error"))) {
                return true;
            }
            Object output = m.get("output");
            return output instanceof Map<?, ?> om && presentError(om.get("error"));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean presentError(Object err) {
        return err != null && !String.valueOf(err).isBlank();
    }

    private static String contentOf(Map<String, Object> msg) {
        Object content = msg == null ? null : msg.get("content");
        if (content instanceof String s) {
            return s;
        }
        return content == null ? "" : AgentJson.jsonString(content);
    }
}
