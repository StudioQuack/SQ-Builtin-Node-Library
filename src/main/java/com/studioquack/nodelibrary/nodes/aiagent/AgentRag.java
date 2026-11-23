package com.studioquack.nodelibrary.nodes.aiagent;

import studioquack.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Pre-RAG over {@code data} port tools (Python {@code prefetch_relevant_documents}).
 * Parallel workers capped at 8; executor is always shut down.
 */
final class AgentRag {

    static final int PREFETCH_MAX_WORKERS = 8;

    private AgentRag() {
        // namespace — never instantiated
    }

    static String prefetchRelevantDocuments(AgentSession session, String mergedPrompt) {
        String query = mergedPrompt == null ? "" : mergedPrompt.strip();
        if (query.isEmpty() || session == null) {
            return null;
        }
        List<Map<String, Object>> dataDefs = new ArrayList<>();
        for (Map<String, Object> td : session.toolDefinitions) {
            if ("data".equals(td.get("port_value")) && td.get("node_id") != null
                    && !AgentJson.str(td.get("node_id")).isBlank()) {
                dataDefs.add(td);
            }
        }
        if (dataDefs.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int workers = Math.min(PREFETCH_MAX_WORKERS, dataDefs.size());
        try (ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, workers))) {
            List<Future<Map<String, Object>>> futs = new ArrayList<>();
            for (Map<String, Object> td : dataDefs) {
                futs.add(ex.submit(() -> callDataTool(session, td, query)));
            }
            for (Future<Map<String, Object>> fut : futs) {
                try {
                    Map<String, Object> r = fut.get();
                    if (r != null && PayloadUnwrap.truthy(r.get("documents"))) {
                        results.add(r);
                    }
                } catch (Exception e) {
                    AgentJson.log(session.helper, "ai-agent pre_rag_future type=" + e.getClass().getSimpleName());
                }
            }
        }
        return formatRelevantDocuments(results);
    }

    private static Map<String, Object> callDataTool(AgentSession session, Map<String, Object> td, String query) {
        String tn = AgentJson.str(td.get("name"));
        if (tn.isBlank()) {
            tn = "data_tool";
        }
        Map<String, Object> payload = AgentPortMapping.dataQuery(session, AgentJson.str(td.get("node_id")),
                query, Map.of());
        payload = AgentJson.asMap(AgentJson.sanitizeForJson(payload));
        try {
            Object resp = session.helper.execute(AgentJson.str(td.get("port_value")), payload,
                    AgentJson.str(td.get("node_id")), null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tool_name", tn);
            row.put("documents", extractDataToolDocuments(resp));
            return row;
        } catch (Exception e) {
            AgentJson.log(session.helper, "ai-agent pre_rag_failed name_len=" + tn.length()
                    + " type=" + e.getClass().getSimpleName());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tool_name", tn);
            row.put("documents", List.of());
            return row;
        }
    }

    static List<Object> extractDataToolDocuments(Object toolResponse) {
        return new ArrayList<>(AgentPortMapping.unwrapListPayload(toolResponse));
    }

    static String formatRelevantDocuments(List<Map<String, Object>> retrievalResults) {
        if (retrievalResults == null || retrievalResults.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add("## Retrieved Context");
        lines.add("The following documents were retrieved from connected data sources before the model answered.");
        lines.add("Use this context first. If it is insufficient or the user asks for information not covered here, use the available data tool with a more specific query.");
        int docCounter = 1;
        for (Map<String, Object> r : retrievalResults) {
            String toolName = AgentJson.str(r.get("tool_name"));
            if (toolName.isBlank()) {
                toolName = "data_tool";
            }
            List<Object> documents = AgentJson.asList(r.get("documents"));
            if (documents.isEmpty()) {
                continue;
            }
            lines.add("\n### Source: " + toolName);
            for (Object d : documents) {
                String txt = (d instanceof Map || d instanceof List)
                        ? Json.write(AgentJson.safeJson(d)) : AgentJson.str(d);
                txt = txt.strip();
                if (txt.isEmpty()) {
                    continue;
                }
                lines.add("\n[Document " + docCounter + "]");
                lines.add(txt);
                docCounter++;
            }
        }
        return docCounter > 1 ? String.join("\n", lines) : null;
    }
}
