package com.studioquack.nodelibrary.nodes.aiagent;

import studioquack.StudioQuackHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Agent instance config + FUNCTION-port discovery (Python {@code get_config_and_input}
 * / {@code setup_nodes_and_memory}).
 *
 * <p>Prompt always merges config + every {@code main_input} connection (newline-joined).
 * Role and instructions: config wins; connection values apply only when config is blank.
 * Files and voices merge across connections. Missing model connection fails the invoke.
 */
final class AgentConfig {

    /** Verbatim from {@code ai agent.py} {@code STRICT_TOOL_CALL_RULES} (pre-RAG prompt removed; RAG is on-demand via data tools). */
    static final String STRICT_TOOL_CALL_RULES =
            "IMPORTANT: If your instructions define a multi-phase workflow, you MUST complete ALL phases before producing a final text answer. "
                    + "Every response you give MUST include tool calls or thinking content (if enabled) until the very last phase is complete. "
                    + "A text-only response with no tool calls is treated as your FINAL answer and terminates your execution.\n"
                    + "\n"
                    + "=== STRICT TOOL CALL PARAMETER RULES (READ CAREFULLY) ===\n"
                    + "Each tool you can call is provided with an EXACT input schema (parameter names, types and structure). "
                    + "Treat every tool call as a schema-fill operation: first read the MAIN input schema, then build the "
                    + "args object from that schema, then write each value under the matching parameter shown by the schema. "
                    + "The schema is authoritative; your wording, source labels, examples, and user phrasing are not.\n"
                    + "You MUST follow these rules WITHOUT EXCEPTION when invoking any tool:\n"
                    + "1) PRESERVE THE MAIN SCHEMA VERBATIM. Use the EXACT top-level parameter names defined in the tool's schema. "
                    + "   Do NOT rename, translate, pluralize, shorten, prefix, suffix, merge or split them. "
                    + "   Do NOT invent new top-level keys that are not present in the schema. "
                    + "   Do NOT replace a defined parameter with a 'similar' one you think fits better.\n"
                    + "2) FILL VALUES UNDER THE RELATED PARAMETER. Every value you want to send MUST live INSIDE the "
                    + "   parameter that the schema defines for it (i.e. as the value of that parameter, or nested within it). "
                    + "   Never promote inner fields to the top level and never bypass the defined parameter.\n"
                    + "3) RESPECT THE EXPECTED TYPE OF EACH PARAMETER. If a parameter expects a JSON object/array (or a "
                    + "   JSON-encoded string), put your JSON payload as the VALUE of THAT parameter — not as a sibling key, "
                    + "   not as a new top-level field, and not spread across multiple new keys. "
                    + "   If a parameter expects a string, send a string; if it expects an object, send an object with the "
                    + "   exact sub-fields described in the schema.\n"
                    + "4) THE ARGS ROOT MUST BE ONLY THE SCHEMA'S PARAMETER OBJECT. Do not wrap arguments in 'request', "
                    + "   'parameters', 'payload', 'body', 'data', or 'input' unless that exact key is listed as a parameter "
                    + "   in the tool schema. If the schema lists {\"query\": string}, call with {\"query\": \"...\"}, not "
                    + "   {\"text\": \"...\"}, {\"q\": \"...\"}, or {\"payload\": {\"query\": \"...\"}}.\n"
                    + "5) EXTRA / AUXILIARY DATA goes UNDER the parameter the schema designates for it (for example a "
                    + "   'payload', 'body', 'data', 'input' or similarly typed parameter). Never attach extra data as new "
                    + "   ad-hoc top-level keys and never rename an existing parameter to carry it.\n"
                    + "6) REQUIRED PARAMETERS ARE MANDATORY. Always include every required parameter using its exact name, "
                    + "   even if you also need to add optional ones. Optional parameters must also use their exact schema names.\n"
                    + "7) WHY THIS MATTERS: The receiving system validates tool calls against the ORIGINAL schema. If you "
                    + "   rename a parameter, drop the root parameter, or move its contents to the top level, the receiver "
                    + "   cannot find the expected fields and the tool call WILL FAIL. Schema-faithful calls are non-negotiable.\n"
                    + "8) SELF-CHECK BEFORE EMITTING A TOOL CALL: (a) Did I start from the MAIN schema? "
                    + "   (b) Do my argument keys exactly match the schema's parameter names? "
                    + "   (c) Is every piece of data nested UNDER the correct parameter rather than beside it? "
                    + "   (d) Did I avoid aliases/synonyms such as link->url, text->query, content->prompt, id->agent_id? "
                    + "   (e) Do the types match (object vs. string vs. array)? "
                    + "   Only emit the tool call when all checks pass.\n"
                    + "\n"
                    + "=== TOOL ERROR RETRY POLICY ===\n"
                    + "After every tool call, inspect returned status/error metadata before deciding whether to call again. "
                    + "Do not retry blindly.\n"
                    + "1) 401/auth_error means credentials or access context are invalid. Do NOT retry the same call. "
                    + "   Record the error and continue with other available work.\n"
                    + "2) 403/restricted means access is blocked. Do NOT retry the same URL/source in a loop. "
                    + "   Record it as access restricted and continue.\n"
                    + "3) 404/not_found means the exact URL is gone. Do NOT retry the same URL. If appropriate, make one "
                    + "   search/canonical-URL attempt using the title or slug, then stop.\n"
                    + "4) 429/rate_limited may be retried once only after waiting. If a postpone/wait tool is available, "
                    + "   call it with retry_after_seconds from the tool result, or 60 seconds if missing, then retry once.\n"
                    + "5) timeout/5xx/transient_error may be retried once only. If a postpone/wait tool is available, "
                    + "   wait retry_after_seconds from the tool result, or 30 seconds if missing, then retry once.\n"
                    + "6) Never retry the same failed call more than once unless the user explicitly asks. Preserve partial "
                    + "   findings and summarize accessible results instead of aborting the whole workflow.";

    /**
     * Verbatim from {@code ai agent.py} {@code DELEGATION_DESCRIPTION_TEMPLATE}.
     * Python {@code str.format} turns {@code {{}/{}}} into single braces; Java stores
     * that runtime form and substitutes {@code {agents}} only.
     */
    static final String DELEGATION_DESCRIPTION_TEMPLATE =
            "Evaluate the query and delegate sub-tasks to connected sub-agents according to their expertise outlined in their roles and instructions, if possible. Otherwise proceed with your task.\n"
                    + "CRITICAL: agent_id MUST be copied exactly from one of the listed agent_id values below. "
                    + "Do NOT use an agent name, role title, markdown file name, tool name, label, or description as agent_id. "
                    + "If you are unsure which connected agent to use, choose the best matching listed agent_id by role/instructions, but the value sent in args.agent_id must still be the exact listed ID.\n"
                    + "Correct delegate_to_agent args shape: {\"agent_id\":\"<exact listed agent_id>\",\"prompt\":\"<complete task for that sub-agent>\"}. "
                    + "Optional keys are role and instructions only.\n"
                    + "Available agents:\n{agents}";

    /** Verbatim from {@code ai agent.py} {@code SUMMARIZER_SYSTEM}. */
    static final String SUMMARIZER_SYSTEM =
            "You are a precise conversation summarizer. Compress the supplied messages into a "
                    + "single concise paragraph that preserves all factual details: user questions, "
                    + "assistant decisions and answers, tool names, tool arguments, tool results, file "
                    + "attachments, and conclusions. Some inputs may already be summaries from earlier "
                    + "rounds — merge them seamlessly into the new summary. Make the most recent user "
                    + "question or task prominent at the END of the summary so the next assistant "
                    + "response can address it. Do not invent information. Output ONLY the summary text "
                    + "— no preamble, no markdown, no role labels, no enclosing quotes.";

    /**
     * System-prompt guidance injected whenever one or more {@code data} FUNCTION tools are wired.
     * Pre-RAG on turn start is deliberately removed; the agent decides what keywords to search
     * and invokes its connected data tools on demand before answering.
     */
    static final String DATA_SOURCE_RAG_RULES =
            "=== CONNECTED DATA SOURCES (ON-DEMAND RAG) ===\n"
                    + "You have connected knowledge/data search tool(s): {data_tools}.\n"
                    + "Documents are NOT pre-fetched automatically at the start of a turn. "
                    + "Before answering any question that may depend on stored documents, domain knowledge, or facts in these sources, "
                    + "extract the key terms/concepts from the user's request and query the relevant data tool(s) for RAG context first. "
                    + "If the initial search is incomplete or misses details, refine your keywords and query again before giving a final answer. "
                    + "For purely conversational messages that clearly do not require lookup (such as greetings or acknowledgements), you may answer directly.";

    private static final Pattern SAFE_KEY_SEGMENT = Pattern.compile("^[A-Za-z0-9_.-]{1,128}$");

    static final String DEFAULT_ROLE = "You are a helpful AI assistant.";
    static final int DEFAULT_MAX_ITERATIONS = 10;
    static final int DEFAULT_CONTEXT_WINDOW = 200_000;

    /** Merged designer + INPUT values used by {@link AiAgentNode} / {@link AgentLoop}. */
    final String role;
    final String instructions;
    final String mergedStatic;
    final List<Map<String, Object>> files;
    final List<Map<String, Object>> voices;
    final int maxIterations;
    final int contextWindow;
    final String llmId;
    final String thoughtsId;
    final String memoryId;
    final boolean useRemoteMemory;
    final String conversationId;

    private AgentConfig(Snapshot snap, AgentSession session) {
        this.role = snap.role();
        this.instructions = snap.instructions();
        this.mergedStatic = snap.mergedStatic();
        this.files = snap.files();
        this.voices = snap.voices();
        this.maxIterations = snap.maxIterations();
        this.contextWindow = snap.contextWindow();
        this.llmId = session.llmNodeId;
        this.thoughtsId = session.thoughtsNodeId;
        this.memoryId = session.memoryId;
        this.useRemoteMemory = session.useRemoteMemory;
        this.conversationId = session.conversationId;
    }

    /**
     * Instance config + FUNCTION-port discovery. Missing {@code model} leaves
     * {@link #llmId} blank so the handler can return a port-keyed error.
     */
    static AgentConfig load(Map<String, Object> request, StudioQuackHelper helper) {
        Snapshot snap = read(helper);
        try {
            return new AgentConfig(snap, setup(helper));
        } catch (IllegalStateException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("Missing required LLM") || msg.contains("helper is required")) {
                return new AgentConfig(snap, new AgentSession(helper, "", "", "", "", false,
                        UUID.randomUUID().toString()));
            }
            throw e;
        }
    }

    /** Multimodal file maps → content blocks (Python {@code _files_to_content_blocks}). */
    static List<Map<String, Object>> filesToBlocks(List<Map<String, Object>> files) {
        return AgentJson.filesToContentBlocks(files);
    }

    /**
     * Read config parameters + merged {@code main_input} (Python {@code get_config_and_input}).
     */
    static Snapshot read(StudioQuackHelper helper) {
        List<Object> incoming = helper == null ? List.of() : helper.getInputValuesByPortValue("main_input");
        Object roleCfg = helper == null ? null : helper.getParameterValueByShortCode("role");
        Object instructionsCfg = helper == null ? null : helper.getParameterValueByShortCode("instructions");
        Object staticCfg = helper == null ? null : helper.getParameterValueByShortCode("prompt");

        List<String> allPrompts = new ArrayList<>();
        List<String> allRoles = new ArrayList<>();
        List<String> allInstructions = new ArrayList<>();
        List<Map<String, Object>> allFiles = new ArrayList<>();
        List<Map<String, Object>> allVoices = new ArrayList<>();
        // Conversation-mode thread(s) arriving on OUR OWN prompt input (the Duckbot
        // start msg port's JSON array string). Interpreted here — never upstream.
        List<Map<String, Object>> allThread = new ArrayList<>();
        int droppedFiles = 0;
        int droppedVoices = 0;

        if (incoming != null) {
            for (Object raw : incoming) {
                if (raw instanceof String s && !s.isBlank()) {
                    List<Map<String, Object>> thread = AgentJson.parseThreadMessages(s);
                    if (thread != null) {
                        allThread.addAll(thread);
                    } else {
                        allPrompts.add(s.strip());
                    }
                } else if (raw instanceof Map<?, ?> m) {
                    Map<String, Object> row = AgentJson.asMap(m);
                    List<Map<String, Object>> thread = AgentJson.parseThreadMessages(row.get("prompt"));
                    if (thread != null) {
                        allThread.addAll(thread);
                    } else {
                        String prompt = AgentJson.nonBlankString(row.get("prompt"));
                        if (prompt != null) {
                            allPrompts.add(prompt);
                        }
                    }
                    String role = AgentJson.nonBlankString(row.get("role"));
                    if (role != null) {
                        allRoles.add(role);
                    }
                    String instr = AgentJson.nonBlankString(row.get("instructions"));
                    if (instr != null) {
                        allInstructions.add(instr);
                    }
                    if (row.containsKey("files")) {
                        AgentJson.CoerceResult res = AgentJson.coerceFilesListDetailed(row.get("files"));
                        allFiles.addAll(res.rows());
                        droppedFiles += res.dropped();
                    }
                    if (row.containsKey("voices")) {
                        AgentJson.CoerceResult res = AgentJson.coerceFilesListDetailed(row.get("voices"));
                        allVoices.addAll(res.rows());
                        droppedVoices += res.dropped();
                    }
                }
            }
        }

        if (droppedFiles + droppedVoices > 0) {
            // Shape-only (counts, never content): the duckbot create-node probe's files:[{}] /
            // voices:[""] lands here — naming the drop is what makes "the model never got a file"
            // explainable from the node log instead of looking like a provider hang.
            AgentJson.log(helper, "agent input sanitized: dropped " + droppedFiles
                    + " empty files entr(ies), " + droppedVoices
                    + " empty voices entr(ies) (no payload and no file handle)");
        }

        String mergedPrompt = String.join("\n", allPrompts);
        String mergedRole = String.join("\n", allRoles);
        String mergedInstructions = String.join("\n", allInstructions);

        String roleCfgStr = AgentJson.nonBlankString(roleCfg);
        String role = roleCfgStr != null ? roleCfgStr
                : (!mergedRole.isEmpty() ? mergedRole : DEFAULT_ROLE);

        String instrCfgStr = AgentJson.nonBlankString(instructionsCfg);
        String instructions = instrCfgStr != null ? instrCfgStr
                : (!mergedInstructions.isEmpty() ? mergedInstructions : "");

        List<String> staticParts = new ArrayList<>();
        String staticStr = AgentJson.nonBlankString(staticCfg);
        if (staticStr != null) {
            staticParts.add(staticStr);
        }
        if (!mergedPrompt.isEmpty()) {
            staticParts.add(mergedPrompt);
        }
        String mergedStatic = String.join("\n", staticParts);

        int maxIterations = parsePositiveInt(
                helper == null ? null : helper.getParameterValueByShortCode("maxIterations", DEFAULT_MAX_ITERATIONS),
                DEFAULT_MAX_ITERATIONS);
        int contextWindow = parseContextWindow(
                helper == null ? null : helper.getParameterValueByShortCode("contextWindow", DEFAULT_CONTEXT_WINDOW));

        return new Snapshot(role, instructions, mergedStatic, allFiles, allVoices, allThread,
                maxIterations, contextWindow);
    }

    /**
     * Discover model/memory/thoughts connections and resolve the conversation key.
     *
     * <p>When the execution context carries a conversation id (e.g. DuckBot), the memory key is
     * deterministically scoped to {@code workflowId:nodeId:conversationId} so follow-up runs of
     * the same conversation resume the same remote memory bucket while staying isolated from
     * other workflows and sibling/sub-agents. Non-conversational runs allocate a fresh UUID.
     *
     * @throws IllegalStateException when the {@code model} FUNCTION port is unwired
     */
    static AgentSession setup(StudioQuackHelper helper) {
        if (helper == null) {
            throw new IllegalStateException("helper is required");
        }
        String myNodeId = AgentJson.str(helper.getCurrentNodeId());
        String memoryId = nodeId(helper.findFirstConnectionNodeIdByPortValue("memory"));
        boolean useRemote = !memoryId.isEmpty();
        String llmId = nodeId(helper.findFirstConnectionNodeIdByPortValue("model"));
        if (llmId.isEmpty()) {
            throw new IllegalStateException("Missing required LLM connection on `model` FUNCTION port.");
        }
        String thoughtsId = nodeId(helper.findFirstConnectionNodeIdByPortValue("thoughts"));
        if (!thoughtsId.isEmpty()) {
            AgentJson.log(helper, "ai-agent thoughts_connected=true thoughtsNodeId=" + thoughtsId);
        } else {
            AgentJson.log(helper, "ai-agent thoughts_connected=false");
        }
        return new AgentSession(
                helper,
                myNodeId,
                llmId,
                thoughtsId,
                memoryId,
                useRemote,
                resolveConversationId(helper, myNodeId));
    }

    /**
     * Deterministic memory key for conversational runs ({@code workflowId:nodeId:conversationId}
     * with non-blank segments), else a per-invoke random UUID. Unsafe/oversized segments are
     * hashed so Redis keys stay clean and free of untrusted whitespace/control characters.
     */
    static String resolveConversationId(StudioQuackHelper helper, String myNodeId) {
        if (helper == null) {
            return UUID.randomUUID().toString();
        }
        String externalConvId = AgentJson.nonBlankString(helper.getConversationId());
        if (externalConvId == null) {
            return UUID.randomUUID().toString();
        }
        String wfId = AgentJson.nonBlankString(helper.getWorkflowId());
        String nodeId = AgentJson.nonBlankString(myNodeId);
        List<String> parts = new ArrayList<>(3);
        if (wfId != null) {
            parts.add(safeKeySegment(wfId));
        }
        if (nodeId != null) {
            parts.add(safeKeySegment(nodeId));
        }
        parts.add(safeKeySegment(externalConvId));
        return String.join(":", parts);
    }

    private static String safeKeySegment(String segment) {
        if (SAFE_KEY_SEGMENT.matcher(segment).matches()) {
            return segment;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(segment.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(34);
            hex.append("h_");
            for (int i = 0; i < 16; i++) {
                hex.append(Character.forDigit((digest[i] >> 4) & 0xf, 16));
                hex.append(Character.forDigit(digest[i] & 0xf, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(segment.getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    static int parsePositiveInt(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = String.valueOf(raw).strip();
        if (s.isEmpty()) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(s);
            return n > 0 ? n : fallback;
        } catch (NumberFormatException e) {
            if (raw instanceof Number num) {
                int n = num.intValue();
                return n > 0 ? n : fallback;
            }
            return fallback;
        }
    }

    /**
     * Context window in tokens. Blank/non-numeric → default. {@code 0} disables summarization.
     */
    static int parseContextWindow(Object raw) {
        if (raw == null) {
            return DEFAULT_CONTEXT_WINDOW;
        }
        String s = String.valueOf(raw).strip();
        if (s.isEmpty()) {
            return DEFAULT_CONTEXT_WINDOW;
        }
        try {
            int n = Integer.parseInt(s);
            // 0 disables summarization (documented); negatives are invalid → default
            return n >= 0 ? n : DEFAULT_CONTEXT_WINDOW;
        } catch (NumberFormatException e) {
            if (raw instanceof Number num) {
                return num.intValue() >= 0 ? num.intValue() : DEFAULT_CONTEXT_WINDOW;
            }
            return DEFAULT_CONTEXT_WINDOW;
        }
    }

    static String nodeId(Object raw) {
        if (raw == null) {
            return "";
        }
        String s = String.valueOf(raw).strip();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    /** Immutable snapshot of merged input + instance parameters. */
    record Snapshot(String role, String instructions, String mergedStatic,
                    List<Map<String, Object>> files, List<Map<String, Object>> voices,
                    List<Map<String, Object>> threadMessages,
                    int maxIterations, int contextWindow) {
        Snapshot {
            role = role == null ? DEFAULT_ROLE : role;
            instructions = instructions == null ? "" : instructions;
            mergedStatic = mergedStatic == null ? "" : mergedStatic;
            files = files == null ? List.of() : List.copyOf(files);
            voices = voices == null ? List.of() : List.copyOf(voices);
            threadMessages = threadMessages == null ? List.of() : List.copyOf(threadMessages);
        }

        Snapshot(String role, String instructions, String mergedStatic,
                 List<Map<String, Object>> files, int maxIterations, int contextWindow) {
            this(role, instructions, mergedStatic, files, List.of(), List.of(),
                    maxIterations, contextWindow);
        }
    }
}
