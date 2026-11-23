package com.studioquack.nodelibrary.nodes.aiagent;

import com.studioquack.nodelibrary.core.ThinkTagSplitter;
import studioquack.SseFrame;
import studioquack.SseStream;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Model FUNCTION call: {@code executeStream} first, atomic {@code execute} fallback
 * when the stream never started (missing endpoint / 404 / 503 / {@code transportFailed}).
 *
 * <p>Mid-stream errors are not retried (provider side effects may already have run).
 * Thoughts fan-out: {@code execute("thoughts", ...)} plus {@code emitPartial("thinking"|"answer", delta)}.
 */
final class AgentLlm {

    private AgentLlm() {
        // namespace — never instantiated
    }

    /**
     * Publish one thoughts-port event (Python {@code emit_thought}) and, for thinking
     * text, also {@code emitPartial("thinking", text)}. The port publishes a single
     * {@code thoughts} string parameter — the human-readable reasoning text, no event
     * envelope. Never throws.
     */
    static void emitThought(AgentSession session, String eventType, int iteration, String text,
                            String signature, Object toolCalls) {
        if (session == null) {
            return;
        }
        if ("thinking".equals(eventType) && text != null && !text.isBlank() && !session.thinkingStreamedLive) {
            try {
                // Tag with the agent's own thoughts port so the daemon routes the frame
                // along that port's edges (Duckbot end included) instead of bypassing the graph.
                session.helper.emitPartial("thoughts", "thinking", text);
            } catch (Exception ignored) {
                // emitPartial must never break the loop
            }
        }
        if (!session.thoughtsConnected()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("thoughts", text == null ? "" : text);
        try {
            session.helper.execute("thoughts", AgentJson.sanitizeForJson(payload), session.thoughtsNodeId, null);
        } catch (Exception e) {
            AgentJson.log(session.helper, "ai-agent emit_thought_failed type=" + e.getClass().getSimpleName());
        }
    }

    static Map<String, Object> safeCallLlm(AgentSession session, List<Map<String, Object>> history) {
        try {
            return callLlm(session, history);
        } catch (Exception e) {
            String msg = "LLM call raised: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            AgentJson.log(session.helper, "ai-agent llm_raised type=" + e.getClass().getSimpleName());
            Map<String, Object> err = PayloadUnwrap.errorAssistant("[Provider error] " + msg, "error");
            err.put("error", msg);
            return err;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> callLlm(AgentSession session, List<Map<String, Object>> history) {
        Map<String, Object> atomicPayload = buildAtomicPayload(session, history);
        // The agent never decides streaming: the payload carries no `stream` flag, so the
        // LLM node's own stream/streaming config (default ON) governs the provider call.
        // With streaming off the sub-PI simply completes without partials and the result
        // arrives on the stream's complete frame — same parse path either way.
        AgentJson.log(session.helper, "ai-agent llm.payload_built messages="
                + (history == null ? 0 : history.size())
                + " tools=" + session.toolDefinitions.size()
                + " inputFiles=" + session.inputFiles.size());

        StreamOutcome outcome = consumeStream(session, atomicPayload);
        // Dedup marker for publishThinkingBlocks: live thinking deltas already
        // reached the UI this round — don't re-emit the assembled block.
        session.thinkingStreamedLive = !outcome.fallbackAtomic && outcome.thinkingAcc.length() > 0;
        Object raw;
        if (outcome.fallbackAtomic) {
            AgentJson.log(session.helper, "ai-agent llm.stream_fallback reason=" + outcome.fallbackReason);
            raw = session.helper.execute("model", atomicPayload, session.llmNodeId, null);
        } else if (outcome.complete != null) {
            raw = outcome.complete;
            // The complete frame is the provider's terminal envelope; partial frames may already
            // have carried an output_files snapshot (image/TTS blocks published mid-stream).
            // Merge so a complete frame that omits them does not drop files the stream produced.
            if (!outcome.filesFromStream.isEmpty()) {
                Map<String, Object> assistant = parseModelRaw(session, raw);
                PayloadUnwrap.mergeOutputFilesInto(assistant, Map.of("output_files", outcome.filesFromStream));
                return assistant;
            }
        } else if (!outcome.thinkingAcc.isEmpty() || !outcome.answerAcc.isEmpty()
                || !outcome.filesFromStream.isEmpty()) {
            String text = outcome.answerAcc.isEmpty()
                    ? outcome.thinkingAcc.toString() : outcome.answerAcc.toString();
            Map<String, Object> assistant = PayloadUnwrap.textAssistant(text);
            // The complete frame never arrived (stream ended early) — keep whatever files the
            // partial frames already carried (image/TTS blocks) instead of dropping them.
            if (!outcome.filesFromStream.isEmpty()) {
                assistant.put("output_files", outcome.filesFromStream);
            }
            return assistant;
        } else if (outcome.sawError) {
            // The provider sub-process failed and said why — report that, not "empty transport".
            String hint = outcome.errorHint.trim();
            String msg = "LLM model FUNCTION call failed: "
                    + (hint.length() > 600 ? hint.substring(0, 600) + "…" : hint);
            AgentJson.log(session.helper, "ai-agent llm.stream_error hint_chars=" + hint.length());
            Map<String, Object> err = PayloadUnwrap.errorAssistant("[Provider error] " + msg, "error");
            err.put("error", msg);
            return err;
        } else {
            raw = List.of();
        }
        AgentJson.log(session.helper, "ai-agent llm.execute_returned raw_shape="
                + PayloadUnwrap.describeRawShape(raw));
        return parseModelRaw(session, raw);
    }

    /**
     * PRIMARY wire guarantee: files ride {@code inputFiles} once. Handle-bearing
     * blocks keep metadata only (no {@code data}); message copies become
     * {@code [file: name]} placeholders so the same bytes are not inlined again.
     */
    static Map<String, Object> buildAtomicPayload(AgentSession session, List<Map<String, Object>> history) {
        List<Map<String, Object>> wireFiles = copyInputFilesForWire(
                session == null ? null : session.inputFiles);
        List<Map<String, Object>> wireMessages = stripCarriedFilesFromMessages(history, wireFiles);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", wireMessages);
        body.put("tools", session.toolDefinitions);
        if (!wireFiles.isEmpty()) {
            body.put("inputFiles", wireFiles);
        }
        return AgentJson.asMap(AgentJson.sanitizeForJson(body));
    }

    /** Copy {@code inputFiles}; strip {@code data} from handle-bearing blocks. Does not mutate the caller. */
    private static List<Map<String, Object>> copyInputFilesForWire(List<Map<String, Object>> inputFiles) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (inputFiles == null) {
            return out;
        }
        for (Map<String, Object> f : inputFiles) {
            if (f == null) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(f);
            if (AgentJson.hasFileHandle(copy)) {
                copy.remove("data");
            }
            out.add(copy);
        }
        return out;
    }

    /**
     * Replace file-shaped content blocks that ride {@code inputFiles} (or themselves
     * carry a handle) with a text placeholder. Handle-less blocks not on
     * {@code inputFiles} stay inline (legacy).
     */
    private static List<Map<String, Object>> stripCarriedFilesFromMessages(
            List<Map<String, Object>> history, List<Map<String, Object>> wireFiles) {
        if (history == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> msg : history) {
            if (msg == null) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(msg);
            Object content = copy.get("content");
            if (content instanceof List<?> list) {
                List<Object> rewritten = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> im) {
                        Map<String, Object> block = AgentJson.asMap(im);
                        if (AgentJson.isFileShapedBlock(block)
                                && (AgentJson.hasFileHandle(block)
                                || AgentJson.carriedOnInputFiles(block, wireFiles))) {
                            rewritten.add(AgentJson.filePlaceholder(block.get("name")));
                            continue;
                        }
                    }
                    rewritten.add(item);
                }
                copy.put("content", rewritten);
            }
            out.add(copy);
        }
        return out;
    }

    static Map<String, Object> parseModelRaw(AgentSession session, Object raw) {
        if (PayloadUnwrap.isEmptyExecuteResponse(raw)) {
            String msg = "LLM model FUNCTION call returned an empty transport response. "
                    + "The provider node may have produced output (visible as "
                    + "`portValue:llm_output:::result:...` in its stdout), but the "
                    + "platform did not route that output back into the agent's `model` "
                    + "FUNCTION response. Verify the workflow edge from the provider's "
                    + "`llm_output` output port to the agent's `model` FUNCTION port. "
                    + "Refusing to retry — the model already executed (and for image "
                    + "generation, a billable image was already produced).";
            AgentJson.log(session.helper, "ai-agent empty_model_transport raw_shape="
                    + PayloadUnwrap.describeRawShape(raw));
            Map<String, Object> err = PayloadUnwrap.errorAssistant("[Provider return-path error] " + msg, "error");
            err.put("error", msg);
            return err;
        }
        Object found = PayloadUnwrap.findAssistantPayload(raw);
        if (found instanceof Map<?, ?> fm) {
            Map<String, Object> assistant = AgentJson.mutableMap(fm);
            normaliseResponseToolCalls(assistant);
            PayloadUnwrap.mergeOutputFilesInto(assistant, raw);
            AgentJson.log(session.helper, "ai-agent model_parsed role=" + assistant.get("role")
                    + " text_len=" + AgentJson.extractTextFromContent(assistant.get("content")).length()
                    + " tool_calls=" + AgentJson.asList(assistant.get("tool_calls")).size()
                    + " output_files=" + AgentJson.asList(assistant.get("output_files")).size()
                    + " finish_reason=" + assistant.get("finish_reason"));
            return assistant;
        }
        String topKeys;
        if (raw instanceof Map<?, ?> rm) {
            topKeys = rm.keySet().toString();
        } else if (raw instanceof List<?> rl) {
            topKeys = "list[" + rl.size() + "]";
        } else {
            topKeys = raw == null ? "null" : raw.getClass().getSimpleName();
        }
        AgentJson.log(session.helper, "ai-agent unparseable_llm top_keys=" + topKeys
                + " shape=" + PayloadUnwrap.describeRawShape(raw));
        String text = AgentJson.extractTextFromContent(raw);
        // No recognizable assistant payload, but the transport may still carry generated files
        // (e.g. an image result whose envelope shape changed) — salvage them rather than
        // publishing an empty answer with empty files.
        List<Object> salvagedFiles = PayloadUnwrap.findOutputFiles(raw);
        if ((text != null && !text.isEmpty()) || !salvagedFiles.isEmpty()) {
            Map<String, Object> assistant = PayloadUnwrap.textAssistant(text == null ? "" : text);
            PayloadUnwrap.mergeOutputFilesInto(assistant, raw);
            return assistant;
        }
        return PayloadUnwrap.textAssistant("");
    }

    /**
     * Normalises provider {@code tool_calls} to the agent's internal {@code {id, name, args}}
     * shape. An OpenAI-compatible provider returns
     * {@code {id, type:"function", function:{name, arguments:<json-string>}}}; the agent
     * resolves tools by flat {@code name} and parses {@code args} via
     * {@link AgentTools#normalizeToolArgs} (which accepts a JSON string), so the
     * {@code arguments} string rides through verbatim. Entries already flat pass through.
     * Wire-only translation — the model port schema is unchanged.
     */
    static void normaliseResponseToolCalls(Map<String, Object> assistant) {
        Object tcs = assistant.get("tool_calls");
        if (!(tcs instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        boolean changed = false;
        List<Object> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> tc = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    tc.put(AgentJson.str(e.getKey()), e.getValue());
                }
                Object fn = tc.get("function");
                boolean flat = !AgentJson.str(tc.get("name")).isBlank();
                if (fn instanceof Map<?, ?> fmap && !flat) {
                    tc.put("name", AgentJson.str(fmap.get("name")));
                    Object arguments = fmap.get("arguments");
                    tc.put("args", arguments == null ? Map.of() : arguments);
                    tc.remove("function");
                    tc.remove("type");
                    changed = true;
                }
                out.add(tc);
            } else {
                out.add(o);
            }
        }
        if (changed) {
            assistant.put("tool_calls", out);
        }
    }

    /**
     * Iterate SSE frames. Fallback to atomic only when the stream never delivered
     * a partial/complete frame and transport looks down (404/503/transportFailed).
     */
    static StreamOutcome consumeStream(AgentSession session, Map<String, Object> streamPayload) {
        StreamOutcome out = new StreamOutcome();
        SseStream stream;
        try {
            stream = session.helper.executeStream("model", streamPayload, session.llmNodeId, null);
        } catch (Exception e) {
            out.fallbackAtomic = true;
            out.fallbackReason = e.getClass().getSimpleName();
            return out;
        }
        if (stream == null) {
            out.fallbackAtomic = true;
            out.fallbackReason = "null_stream";
            return out;
        }
        int frameCount = 0;
        try (SseStream s = stream) {
            if (s.transportFailed()) {
                out.fallbackAtomic = true;
                out.fallbackReason = "transportFailed:" + s.failureHint();
                return out;
            }
            for (SseFrame frame : s) {
                if (frame == null) {
                    continue;
                }
                frameCount++;
                String event = eventOf(frame).toLowerCase(Locale.ROOT);
                Object data = dataOf(frame);
                String raw = rawOf(frame);
                // Frame trace: proves what crossed the helper pipe (a lost/truncated complete
                // frame is the "agent returned empty" smoking gun). Sizes only, never content.
                AgentJson.log(session.helper, "ai-agent llm.frame n=" + frameCount
                        + " event=" + (event.isEmpty() ? "<none>" : event)
                        + " raw_chars=" + (raw == null ? 0 : raw.length())
                        + " data_type=" + (data == null ? "null" : data.getClass().getSimpleName()));
                if ("done".equals(event)) {
                    break;
                }
                if ("partial".equals(event) || event.isEmpty() || "delta".equals(event) || "thinking".equals(event)) {
                    out.started = true;
                    applyPartial(session, data, raw, event, out);
                    continue;
                }
                if ("complete".equals(event) || "result".equals(event)) {
                    out.started = true;
                    out.complete = data != null ? data : AgentJson.maybeParseString(raw);
                    continue;
                }
                if ("error".equals(event)) {
                    out.sawError = true;
                    out.errorHint = AgentJson.str(data) + " " + raw;
                    if (!out.started && (s.transportFailed() || PayloadUnwrap.looks404or503(data, raw + " " + s.failureHint()))) {
                        out.fallbackAtomic = true;
                        out.fallbackReason = "error_" + PayloadUnwrap.statusCode(data);
                    }
                    continue;
                }
                // Unknown event: treat payload as a partial if it carries deltas.
                if (data != null) {
                    out.started = true;
                    applyPartial(session, data, raw, event, out);
                }
            }
            flushAnswer(session.helper, out);
            if (!out.started && (s.transportFailed() || out.sawError
                    && PayloadUnwrap.looks404or503(out.errorHint, out.errorHint))) {
                out.fallbackAtomic = true;
                if (out.fallbackReason == null) {
                    out.fallbackReason = s.transportFailed() ? "transportFailed_after" : "error_before_start";
                }
            }
        } catch (Exception e) {
            if (!out.started) {
                out.fallbackAtomic = true;
                out.fallbackReason = e.getClass().getSimpleName();
            }
        }
        // Stream-end summary: complete==false with frames>0 means the result frame was lost
        // in transit — the agent must not silently publish an empty answer in that case.
        AgentJson.log(session.helper, "ai-agent llm.stream_end frames=" + frameCount
                + " started=" + out.started
                + " complete=" + (out.complete != null)
                + " answer_chars=" + out.answerAcc.length()
                + " thinking_chars=" + out.thinkingAcc.length()
                + " files_from_stream=" + out.filesFromStream.size()
                + " fallback=" + (out.fallbackAtomic ? out.fallbackReason : "no"));
        return out;
    }

    static void applyPartial(AgentSession session, Object data, String raw, String event, StreamOutcome out) {
        StudioQuackHelper helper = session.helper;
        if (data instanceof String s) {
            String t = s;
            if (looksLikeThinking(event, t)) {
                emitDelta(helper, "thinking", t);
                out.thinkingAcc.append(t);
            } else if (!t.isBlank()) {
                emitAnswer(helper, out, t);
            }
            return;
        }
        walkPartial(helper, data, event, out, 0);
        if (data == null && raw != null && !raw.isBlank() && looksLikeThinking(event, raw)) {
            emitDelta(helper, "thinking", raw);
            out.thinkingAcc.append(raw);
        }
    }

    private static void walkPartial(StudioQuackHelper helper, Object data, String event,
                                    StreamOutcome out, int depth) {
        if (data == null || depth > 6) {
            return;
        }
        if (data instanceof Map<?, ?> map) {
            // Partial frames sometimes carry the whole in-flight assistant message; when one does,
            // its output_files are the latest snapshot (image/TTS blocks) — keep the newest.
            List<Object> snapshot = new ArrayList<>();
            PayloadUnwrap.addUniqueFiles(snapshot, map.get("output_files"));
            PayloadUnwrap.addUniqueFiles(snapshot, map.get("outputFiles"));
            if (!snapshot.isEmpty()) {
                out.filesFromStream = snapshot;
            }
            Object delta = map.get("delta");
            if (delta != null && !(delta instanceof Map) && !(delta instanceof List)) {
                String t = AgentJson.str(delta);
                if (!t.isBlank()) {
                    // execute/stream wraps emit-partial as {channel, delta}; event name is always "partial".
                    String ch = channelOf(map, event, t);
                    if ("thinking".equals(ch)) {
                        emitDelta(helper, "thinking", t);
                        out.thinkingAcc.append(t);
                    } else {
                        emitAnswer(helper, out, t);
                    }
                }
            }
            for (String k : List.of("thinking", "reasoning", "reasoning_content", "thought", "thoughts")) {
                Object v = map.get(k);
                if (v instanceof String ts && !ts.isBlank()) {
                    emitDelta(helper, "thinking", ts);
                    out.thinkingAcc.append(ts);
                } else if (v != null) {
                    walkPartial(helper, v, "thinking", out, depth + 1);
                }
            }
            for (String k : List.of("content", "text", "answer")) {
                Object v = map.get(k);
                if (v instanceof String ts && !ts.isBlank()) {
                    String ch = channelOf(map, event, ts);
                    if ("thinking".equals(ch)) {
                        emitDelta(helper, "thinking", ts);
                        out.thinkingAcc.append(ts);
                    } else {
                        emitAnswer(helper, out, ts);
                    }
                } else if (v instanceof List || v instanceof Map) {
                    walkPartial(helper, v, inheritedEvent(map, event), out, depth + 1);
                }
            }
            if (delta instanceof Map || delta instanceof List) {
                walkPartial(helper, delta, inheritedEvent(map, event), out, depth + 1);
            }
            Object choices = map.get("choices");
            if (choices instanceof List<?>) {
                walkPartial(helper, choices, inheritedEvent(map, event), out, depth + 1);
            }
            return;
        }
        if (data instanceof List<?> list) {
            for (Object item : list) {
                walkPartial(helper, item, event, out, depth + 1);
            }
        }
    }

    /** A parent-declared thinking channel/type flows into nested containers. */
    private static String inheritedEvent(Map<?, ?> map, String event) {
        String declared = AgentJson.str(map.get("channel"));
        if (declared.isBlank()) {
            declared = AgentJson.str(map.get("type"));
        }
        return looksLikeThinking(declared, null) ? "thinking" : event;
    }

    /** Honor execute/stream {@code channel} (and legacy {@code type}) before text heuristics. */
    private static String channelOf(Map<?, ?> map, String event, String text) {
        String declared = AgentJson.str(map.get("channel"));
        if (declared.isBlank()) {
            declared = AgentJson.str(map.get("type"));
        }
        return looksLikeThinking(event, text) || looksLikeThinking(declared, text) ? "thinking" : "answer";
    }

    static boolean looksLikeThinking(String eventOrType, String text) {
        String e = eventOrType == null ? "" : eventOrType.toLowerCase(Locale.ROOT);
        if (e.contains("think") || e.contains("reason") || e.contains("thought")) {
            return true;
        }
        if (text == null) {
            return false;
        }
        String t = text.strip();
        return t.startsWith("<thinking>") || t.startsWith("Thinking:") || t.startsWith("Reasoning:");
    }

    /**
     * Answer-channel text passes through the think-tag splitter: a model node that
     * forwards provider-verbatim content (inline {@code <think>} markup, or a
     * template-stripped orphan closer à la SGLang/vLLM) is reclassified here instead
     * of leaking reasoning and tags into the answer channel.
     */
    private static void emitAnswer(StudioQuackHelper helper, StreamOutcome out, String t) {
        for (ThinkTagSplitter.Segment seg : out.answerSplitter.feed(t)) {
            emitDelta(helper, seg.channel(), seg.text());
            (ThinkTagSplitter.THINKING.equals(seg.channel()) ? out.thinkingAcc : out.answerAcc).append(seg.text());
        }
    }

    private static void flushAnswer(StudioQuackHelper helper, StreamOutcome out) {
        for (ThinkTagSplitter.Segment seg : out.answerSplitter.flush()) {
            emitDelta(helper, seg.channel(), seg.text());
            (ThinkTagSplitter.THINKING.equals(seg.channel()) ? out.thinkingAcc : out.answerAcc).append(seg.text());
        }
    }

    static void emitDelta(StudioQuackHelper helper, String channel, String delta) {
        if (helper == null || delta == null || delta.isEmpty()) {
            return;
        }
        try {
            // Tag with the agent's own source port: thinking rides the thoughts FUNCTION port,
            // answer rides the main_output OUTPUT port — the daemon routes each frame along
            // that port's edges so wired targets (e.g. Duckbot end) render the live stream.
            String port = "thinking".equals(channel) ? "thoughts" : "main_output";
            helper.emitPartial(port, channel, delta);
        } catch (Exception ignored) {
            // never throws past the loop
        }
    }

    static String eventOf(SseFrame frame) {
        try {
            return AgentJson.str(frame.event);
        } catch (Exception e) {
            return "";
        }
    }

    static Object dataOf(SseFrame frame) {
        try {
            return frame.data;
        } catch (Exception e) {
            return null;
        }
    }

    static String rawOf(SseFrame frame) {
        try {
            return AgentJson.str(frame.rawData);
        } catch (Exception e) {
            return "";
        }
    }

    static final class StreamOutcome {
        Object complete;
        boolean started;
        boolean sawError;
        boolean fallbackAtomic;
        String fallbackReason;
        String errorHint = "";
        final StringBuilder thinkingAcc = new StringBuilder();
        final StringBuilder answerAcc = new StringBuilder();
        /** Reclassifies answer deltas that carry inline think markup (raw-passthrough model nodes). */
        final ThinkTagSplitter answerSplitter = new ThinkTagSplitter();
        /** Latest non-empty {@code output_files} snapshot seen on any partial frame. */
        List<Object> filesFromStream = List.of();
    }
}
