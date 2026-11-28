package com.studioquack.nodelibrary.nodes.aiagent;

import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI Agent orchestrator. Reply is always {@code main_output} with
 * {@code final_answer} + {@code output_files} + {@code voices}. Never throws past the dispatcher.
 * Never logs {@code helperToken}.
 *
 * <p>Thread-safe: per-invoke {@link AgentSession} (UUID {@code conversation_id}).
 * The only instance field is the immutable template id.
 */
public final class AiAgentNode implements NodeHandler {

    /** Fallback when catalog/env omit the id; must match {@code nodes/ai-agent} {@code _id}. */
    public static final String DEFAULT_TEMPLATE_ID = "a72d6175-f0d5-48d7-94b4-cb50fa9acaf7";

    private final String templateId;

    public AiAgentNode() {
        this(DEFAULT_TEMPLATE_ID);
    }

    public AiAgentNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? DEFAULT_TEMPLATE_ID
                : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        AgentSession session = null;
        try {
            if (helper == null) {
                return mainOutput("Agent v2 error: IllegalArgumentException: helper is required", List.of());
            }
            AgentJson.log(helper, "ai-agent start requestId="
                    + (request == null ? "" : AgentJson.str(request.get("requestId"))));
            AgentConfig.Snapshot cfg = AgentConfig.read(helper);
            session = AgentConfig.setup(helper);
            ToolCatalog catalog = ToolCatalog.build(helper);
            session.toolDefinitions.addAll(catalog.definitions);
            List<Map<String, Object>> turnFiles = new ArrayList<>(cfg.files());
            turnFiles.addAll(cfg.voices());
            session.inputFiles.addAll(AgentJson.filesToContentBlocks(turnFiles));
            int dataToolsN = 0;
            for (Map<String, Object> td : session.toolDefinitions) {
                if ("data".equals(td.get("port_value"))) {
                    dataToolsN++;
                }
            }
            AgentJson.log(helper, "ai-agent tools_ready n=" + session.toolDefinitions.size()
                    + " data_tools_n=" + dataToolsN
                    + " has_memory=" + session.useRemoteMemory
                    + " has_thoughts=" + session.thoughtsConnected()
                    + " files_n=" + cfg.files().size()
                    + " voices_n=" + cfg.voices().size());

            // Pre-RAG on turn start is deliberately removed: connected `data` nodes are exposed
            // as on-demand RAG search tools so the agent searches relevant keywords when needed.
            AgentMemory.Prepared prepared = AgentMemory.prepareInitialHistory(
                    session, cfg.role(), cfg.instructions(), cfg.mergedStatic(), cfg.files(),
                    cfg.threadMessages());
            if (!prepared.messagesToPersist().isEmpty()) {
                AgentMemory.addHistoryMessage(session, prepared.messagesToPersist());
            }

            Map<String, Object> finalPayload = AgentLoop.run(
                    session, prepared.history(), cfg.maxIterations(), cfg.contextWindow());
            String finalText = PayloadUnwrap.finalText(finalPayload);
            List<Object> outFiles = PayloadUnwrap.finalFiles(finalPayload);
            // A round that ended on an error carrying nothing to show is a failed turn, not an
            // answer. Publishing it as main_output handed the chat a blank final_answer and threw
            // the cause away (the model node's own EmptyResponseError text lives on `error`, not
            // `content`), so the turn looked successful and empty instead of failed.
            String failure = PayloadUnwrap.payloadError(finalPayload);
            if (!failure.isBlank() && finalText.isBlank() && outFiles.isEmpty()) {
                String type = PayloadUnwrap.payloadErrorType(finalPayload);
                AgentJson.log(helper, "ai-agent failed type=" + type);
                return error(failure, type);
            }
            Map<String, Object> ports = mainOutput(finalText, outFiles);
            Object assembled = ports.get("main_output");
            int voicesN = 0;
            if (assembled instanceof Map<?, ?> body && body.get("voices") instanceof List<?> voices) {
                voicesN = voices.size();
            }
            AgentJson.log(helper, "ai-agent done text_len=" + (finalText == null ? 0 : finalText.length())
                    + " output_files=" + (outFiles == null ? 0 : outFiles.size())
                    + " voices_n=" + voicesN);
            return ports;
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            String msg = "Agent v2 error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            if (helper != null) {
                AgentJson.log(helper, "ai-agent error type=" + e.getClass().getSimpleName());
            }
            throw new studioquack.StudioQuackError("SANDBOX_FAILED", msg,
                    Map.of("exceptionType", e.getClass().getName()));
        } finally {
            if (session != null) {
                AgentMemory.release(session.conversationId);
            }
        }
    }

    /**
     * Failed-turn envelope, the same {@code error}/{@code type} shape the model nodes return, so a
     * failed agent turn reaches the dispatcher as a failure rather than a successful empty answer.
     */
    static Map<String, Object> error(String message, String type) {
        throw new studioquack.StudioQuackError("PROVIDER_ERROR",
                message == null || message.isBlank() ? "AI Agent produced no answer" : message,
                Map.of("exceptionType", type == null || type.isBlank() ? "AgentError" : type));
    }

    static Map<String, Object> mainOutput(String finalAnswer, List<?> outputFiles) {
        List<?> files = outputFiles == null ? new ArrayList<>() : outputFiles;
        // Voice blocks (kind/type == audio, or mime_type audio/*) are ALSO published on the
        // dedicated `voices` port; `output_files` keeps the full list so a consumer wired only
        // to `output_files` still receives every generated file (the Duckbot end node splits
        // audio out of `files` itself when its `voices` input is empty).
        List<Object> voices = new ArrayList<>();
        for (Object block : files) {
            if (isVoiceBlock(block)) {
                voices.add(block);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("final_answer", finalAnswer == null ? "" : finalAnswer);
        body.put("output_files", files);
        body.put("voices", voices);
        Map<String, Object> ports = new LinkedHashMap<>();
        ports.put("main_output", body);
        return ports;
    }

    /** {@code kind}/{@code type} == audio, or {@code mime_type} starts with {@code audio/} (case-insensitive). */
    private static boolean isVoiceBlock(Object block) {
        if (!(block instanceof Map<?, ?> m)) {
            return false;
        }
        if (isAudioLabel(m.get("kind")) || isAudioLabel(m.get("type"))) {
            return true;
        }
        Object mime = m.get("mime_type");
        return mime != null && AgentJson.str(mime).toLowerCase(Locale.ROOT).startsWith("audio/");
    }

    private static boolean isAudioLabel(Object value) {
        return value != null && "audio".equalsIgnoreCase(AgentJson.str(value));
    }
}
