package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import studioquack.Json;
import studioquack.SseFrame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentLlm partial-walk: the execute/stream {@code channel} field must win over text
 * heuristics — {@code channel:"thinking"} partials accumulate as thinking, everything
 * else as answer. Sessions carry a null helper so {@code emitPartial} is a no-op and
 * only the accumulators are asserted.
 */
class AgentLlmTest {

    private static AgentLlm.StreamOutcome apply(Object data, String event) {
        return apply(data, null, event);
    }

    private static AgentLlm.StreamOutcome apply(Object data, String raw, String event) {
        AgentSession session = new AgentSession(null, "", "", "", "", false,
                "conv-" + UUID.randomUUID());
        AgentLlm.StreamOutcome out = new AgentLlm.StreamOutcome();
        AgentLlm.applyPartial(session, data, raw, event, out);
        return out;
    }

    private static String thinking(AgentLlm.StreamOutcome out) {
        return out.thinkingAcc.toString();
    }

    private static String answer(AgentLlm.StreamOutcome out) {
        return out.answerAcc.toString();
    }

    private static Object nestContent(int levels) {
        Object v = "x";
        for (int i = 0; i < levels; i++) {
            v = Map.of("content", v);
        }
        return v;
    }

    // ---------- response tool_calls normalisation ----------

    @Test
    void openAiToolCallsAreFlattenedToInternalShape() {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", "search");
        fn.put("arguments", "{\"query\":\"q\"}");
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "c1");
        tc.put("type", "function");
        tc.put("function", fn);
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("tool_calls", new ArrayList<>(List.of(tc)));

        AgentLlm.normaliseResponseToolCalls(assistant);

        Map<?, ?> first = (Map<?, ?>) ((List<?>) assistant.get("tool_calls")).get(0);
        assertEquals("c1", first.get("id"));
        assertEquals("search", first.get("name"));
        assertEquals("{\"query\":\"q\"}", first.get("args")); // JSON string rides through verbatim
        assertNull(first.get("function"));
        assertNull(first.get("type"));
    }

    @Test
    void flatToolCallsPassThroughUnchanged() {
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "c1");
        tc.put("name", "search");
        tc.put("args", Map.of("query", "q"));
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("tool_calls", new ArrayList<>(List.of(tc)));

        AgentLlm.normaliseResponseToolCalls(assistant);

        Map<?, ?> first = (Map<?, ?>) ((List<?>) assistant.get("tool_calls")).get(0);
        assertEquals("search", first.get("name"));
        assertEquals(Map.of("query", "q"), first.get("args"));
    }

    // ---------- string partials ----------

    @Test
    void plainStringPartialGoesToAnswer() {
        AgentLlm.StreamOutcome out = apply("hello", "partial");
        assertEquals("hello", answer(out));
        assertEquals("", thinking(out));
    }

    @Test
    void stringPartialWithThinkingEventGoesToThinking() {
        AgentLlm.StreamOutcome out = apply("hmm", "thinking");
        assertEquals("hmm", thinking(out));
        assertEquals("", answer(out));
    }

    @Test
    void stringPartialWithThinkingPrefixGoesToThinking() {
        AgentLlm.StreamOutcome out = apply("<thinking>plan</thinking>", "partial");
        assertEquals("<thinking>plan</thinking>", thinking(out));
        assertEquals("", answer(out));
    }

    @Test
    void blankStringPartialIsIgnored() {
        AgentLlm.StreamOutcome out = apply("   ", "partial");
        assertEquals("", answer(out));
        assertEquals("", thinking(out));
    }

    // ---------- the channel fix: execute/stream wraps emit-partial as {channel, delta} ----------

    @Test
    void channelThinkingDeltaIsClassifiedAsThinking() {
        AgentLlm.StreamOutcome out = apply(Map.of("channel", "thinking", "delta", "reasoning step"), "partial");
        assertEquals("reasoning step", thinking(out));
        assertEquals("", answer(out));
    }

    @Test
    void channelAnswerDeltaIsClassifiedAsAnswer() {
        AgentLlm.StreamOutcome out = apply(Map.of("channel", "answer", "delta", "Hello"), "partial");
        assertEquals("Hello", answer(out));
        assertEquals("", thinking(out));
    }

    @Test
    void missingChannelFallsBackToEventHeuristic() {
        assertEquals("Hmm", thinking(apply(Map.of("delta", "Hmm"), "thinking")));
        assertEquals("Hello", answer(apply(Map.of("delta", "Hello"), "partial")));
    }

    @Test
    void legacyTypeFieldIsHonoredAsChannel() {
        AgentLlm.StreamOutcome out = apply(Map.of("type", "thinking", "delta", "legacy"), "partial");
        assertEquals("legacy", thinking(out));
        assertEquals("", answer(out));
    }

    @Test
    void channelAppliesToContentTextAndAnswerKeys() {
        assertEquals("via content", thinking(apply(Map.of("channel", "thinking", "content", "via content"), "partial")));
        assertEquals("via text", thinking(apply(Map.of("channel", "thinking", "text", "via text"), "partial")));
        assertEquals("via answer", answer(apply(Map.of("channel", "answer", "answer", "via answer"), "partial")));
    }

    @Test
    void channelThinkingWinsForPlainTextWithoutMarkers() {
        AgentLlm.StreamOutcome out = apply(Map.of("channel", "thinking", "delta", "plain words"), "partial");
        assertEquals("plain words", thinking(out));
    }

    @Test
    void thinkingPrefixedTextStaysThinkingEvenWithAnswerChannel() {
        AgentLlm.StreamOutcome out = apply(
                Map.of("channel", "answer", "delta", "<thinking>still thinking</thinking>"), "partial");
        assertEquals("<thinking>still thinking</thinking>", thinking(out));
        assertEquals("", answer(out));
    }

    // ---------- provider-style keys ----------

    @Test
    void explicitThinkingKeysAreClassifiedAsThinking() {
        assertEquals("t", thinking(apply(Map.of("thinking", "t"), "partial")));
        assertEquals("rc", thinking(apply(Map.of("reasoning_content", "rc"), "partial")));
        assertEquals("th", thinking(apply(Map.of("thoughts", "th"), "partial")));
    }

    @Test
    void nestedReasoningMapInheritsThinkingEvent() {
        AgentLlm.StreamOutcome out = apply(Map.of("reasoning", Map.of("text", "nested")), "partial");
        assertEquals("nested", thinking(out));
        assertEquals("", answer(out));
    }

    @Test
    void openAiStyleChoicesDeltaGoesToAnswer() {
        AgentLlm.StreamOutcome out = apply(
                Map.of("choices", List.of(Map.of("delta", Map.of("content", "tok")))), "partial");
        assertEquals("tok", answer(out));
        assertEquals("", thinking(out));
    }

    @Test
    void contentArrayAccumulatesInOrder() {
        AgentLlm.StreamOutcome out = apply(
                Map.of("content", List.of(Map.of("text", "a"), Map.of("text", "b"))), "partial");
        assertEquals("ab", answer(out));
    }

    @Test
    void contentArrayItemChannelIsHonored() {
        AgentLlm.StreamOutcome out = apply(
                Map.of("content", List.of(Map.of("text", "t", "channel", "thinking"))), "partial");
        assertEquals("t", thinking(out));
        assertEquals("", answer(out));
    }

    @Test
    void mapValuedDeltaIsRecursed() {
        AgentLlm.StreamOutcome out = apply(Map.of("delta", Map.of("content", "inner")), "partial");
        assertEquals("inner", answer(out));
    }

    @Test
    void nonStringDeltaIsCoerced() {
        AgentLlm.StreamOutcome out = apply(Map.of("delta", 42), "partial");
        assertEquals("42", answer(out));
    }

    // ---------- malformed / boundary partials ----------

    @Test
    void malformedPartialsAreIgnored() {
        for (Object bad : new Object[] {
                Map.of("delta", ""),
                Map.of("delta", "   "),
                Map.of("foo", "bar"),
                Map.of(),
                List.of()}) {
            AgentLlm.StreamOutcome out = apply(bad, "partial");
            assertEquals("", answer(out));
            assertEquals("", thinking(out));
        }
    }

    @Test
    void nullDataWithThinkingRawFallsBackToRaw() {
        AgentLlm.StreamOutcome out = apply(null, "<thinking>offline</thinking>", "partial");
        assertEquals("<thinking>offline</thinking>", thinking(out));
        assertEquals("", answer(out));
    }

    @Test
    void nullDataWithPlainRawIsIgnored() {
        AgentLlm.StreamOutcome out = apply(null, "plain", "partial");
        assertEquals("", answer(out));
        assertEquals("", thinking(out));
    }

    @Test
    void nestingBeyondDepthCapIsDropped() {
        assertEquals("x", answer(apply(nestContent(7), "partial")));
        AgentLlm.StreamOutcome deep = apply(nestContent(8), "partial");
        assertEquals("", answer(deep));
        assertEquals("", thinking(deep));
    }

    // ---------- looksLikeThinking ----------

    @ParameterizedTest
    @ValueSource(strings = {"thinking", "Thinking", "reasoning", "chain_of_thought", "reason"})
    void thinkingEventNames(String event) {
        assertTrue(AgentLlm.looksLikeThinking(event, "text"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<thinking>plan", "Thinking: plan", "Reasoning: plan"})
    void thinkingTextMarkers(String text) {
        assertTrue(AgentLlm.looksLikeThinking("partial", text));
    }

    @Test
    void nonThinkingSignals() {
        assertFalse(AgentLlm.looksLikeThinking("partial", "plain"));
        assertFalse(AgentLlm.looksLikeThinking(null, null));
        assertFalse(AgentLlm.looksLikeThinking("", null));
        assertFalse(AgentLlm.looksLikeThinking("answer", "text"));
    }

    // ---------- frame accessors ----------

    @Test
    void frameAccessorsReadFieldsDefensively() {
        SseFrame frame = new SseFrame("partial", Map.of("a", 1), "{\"a\":1}");
        assertEquals("partial", AgentLlm.eventOf(frame));
        assertEquals(Map.of("a", 1), AgentLlm.dataOf(frame));
        assertEquals("{\"a\":1}", AgentLlm.rawOf(frame));

        SseFrame empty = new SseFrame(null, null, null);
        assertEquals("", AgentLlm.eventOf(empty));
        assertNull(AgentLlm.dataOf(empty));
        assertEquals("", AgentLlm.rawOf(empty));
    }

    // ---------- emitDelta / StreamOutcome ----------

    @Test
    void emitDeltaWithNullHelperNeverThrows() {
        assertDoesNotThrow(() -> AgentLlm.emitDelta(null, "thinking", "x"));
        assertDoesNotThrow(() -> AgentLlm.emitDelta(null, "answer", ""));
        assertDoesNotThrow(() -> AgentLlm.emitDelta(null, "answer", null));
    }

    @Test
    void freshStreamOutcomeIsEmpty() {
        AgentLlm.StreamOutcome out = new AgentLlm.StreamOutcome();
        assertFalse(out.started);
        assertFalse(out.sawError);
        assertFalse(out.fallbackAtomic);
        assertNull(out.complete);
        assertNull(out.fallbackReason);
        assertEquals("", out.errorHint);
        assertEquals("", out.thinkingAcc.toString());
        assertEquals("", out.answerAcc.toString());
        assertTrue(out.filesFromStream.isEmpty());
    }

    // ---------- output_files survival ----------

    /** A partial carrying the in-flight assistant message snapshots its files for the fallback path. */
    @Test
    void partialSnapshotCapturesOutputFiles() {
        Map<String, Object> image = Map.of("type", "image", "mime_type", "image/png",
                "source", "base64", "data", "aW1hZ2U=", "name", "pic.png");
        AgentLlm.StreamOutcome out = apply(
                Map.of("content", "here is the image", "output_files", List.of(image)), "partial");
        assertEquals("here is the image", answer(out));
        assertEquals(1, out.filesFromStream.size());
        assertEquals("pic.png", ((Map<?, ?>) out.filesFromStream.get(0)).get("name"));
    }

    /** The newest non-empty snapshot wins; an empty list never erases captured files. */
    @Test
    void latestNonEmptySnapshotWins() {
        AgentSession session = new AgentSession(null, "", "", "", "", false,
                "conv-" + UUID.randomUUID());
        AgentLlm.StreamOutcome out = new AgentLlm.StreamOutcome();
        AgentLlm.applyPartial(session,
                Map.of("output_files", List.of(Map.of("name", "first.png"))), null, "partial", out);
        AgentLlm.applyPartial(session, Map.of("output_files", List.of()), null, "partial", out);
        assertEquals(1, out.filesFromStream.size());
        AgentLlm.applyPartial(session,
                Map.of("output_files", List.of(Map.of("name", "second.png"))), null, "partial", out);
        assertEquals("second.png", ((Map<?, ?>) out.filesFromStream.get(0)).get("name"));
    }

    /**
     * A role-less map carrying {@code output_files} is still assistant-like: the parsed model
     * result must keep the files instead of publishing an empty answer with empty files.
     */
    @Test
    void parseModelRawKeepsFilesFromRoleLessPayload() {
        AgentSession session = new AgentSession(null, "", "", "", "", false,
                "conv-" + UUID.randomUUID());
        Map<String, Object> image = Map.of("type", "image", "mime_type", "image/png",
                "source", "base64", "data", "aW1hZ2U=", "name", "pic.png");
        Map<String, Object> envelope = Map.of("weird", Map.of("nested",
                Map.of("summary", "done", "output_files", List.of(image))));

        Map<String, Object> parsed = AgentLlm.parseModelRaw(session, envelope);

        assertEquals(1, AgentJson.asList(parsed.get("output_files")).size());
    }

    /** Files alone (no extractable text) still produce a file-carrying assistant message. */
    @Test
    void parseModelRawKeepsFilesWhenTextIsAbsent() {
        AgentSession session = new AgentSession(null, "", "", "", "", false,
                "conv-" + UUID.randomUUID());
        Map<String, Object> filesOnly = Map.of("payload",
                Map.of("output_files", List.of(Map.of("name", "only.png", "data", "aW1hZ2U="))));

        Map<String, Object> parsed = AgentLlm.parseModelRaw(session, filesOnly);

        assertEquals(1, AgentJson.asList(parsed.get("output_files")).size());
        assertEquals("", AgentJson.extractTextFromContent(parsed.get("content")));
    }

    @Test
    void parseModelRawMergesTopLevelOutputFiles() {
        AgentSession session = new AgentSession(null, "", "", "", "", false,
                "conv-" + UUID.randomUUID());
        Map<String, Object> parsed = AgentLlm.parseModelRaw(session, Map.of(
                "role", "assistant",
                "content", "hi",
                "outputFiles", List.of(Map.of("name", "gen.png"))));
        assertEquals("gen.png", ((Map<?, ?>) AgentJson.asList(parsed.get("output_files")).get(0)).get("name"));
    }

    @Test
    void parseModelRawMergesResultOutputFiles() {
        AgentSession session = new AgentSession(null, "", "", "", "", false,
                "conv-" + UUID.randomUUID());
        Map<String, Object> parsed = AgentLlm.parseModelRaw(session, Map.of(
                "result", Map.of("role", "assistant", "content", "hi",
                        "output_files", List.of(Map.of("name", "from-result.png")))));
        assertEquals("from-result.png",
                ((Map<?, ?>) AgentJson.asList(parsed.get("output_files")).get(0)).get("name"));
    }

    @Test
    void parseModelRawMergesBothFileKeysUniquely() {
        AgentSession session = new AgentSession(null, "", "", "", "", false,
                "conv-" + UUID.randomUUID());
        Map<String, Object> shared = Map.of("name", "shared.png");
        Map<String, Object> extra = Map.of("name", "extra.png");
        Map<String, Object> parsed = AgentLlm.parseModelRaw(session, Map.of(
                "result", Map.of("role", "assistant", "content", "hi",
                        "output_files", List.of(shared)),
                "outputFiles", List.of(shared, extra)));
        List<Object> files = AgentJson.asList(parsed.get("output_files"));
        assertEquals(2, files.size());
        assertEquals("shared.png", ((Map<?, ?>) files.get(0)).get("name"));
        assertEquals("extra.png", ((Map<?, ?>) files.get(1)).get("name"));
    }

    @Test
    void parseModelRawEmptyTextWithOutputFilesStillYieldsFiles() {
        AgentSession session = new AgentSession(null, "", "", "", "", false,
                "conv-" + UUID.randomUUID());
        Map<String, Object> parsed = AgentLlm.parseModelRaw(session,
                Map.of("outputFiles", List.of(Map.of("name", "only.png"))));
        assertEquals(1, AgentJson.asList(parsed.get("output_files")).size());
        assertEquals("", AgentJson.extractTextFromContent(parsed.get("content")));
    }

    @Test
    void parseModelRawEmptyTransportIsProviderReturnPathError() {
        AgentSession session = new AgentSession(null, "", "", "", "", false,
                "conv-" + UUID.randomUUID());
        for (Object empty : new Object[] {null, "", "  ", Map.of(), List.of()}) {
            Map<String, Object> parsed = AgentLlm.parseModelRaw(session, empty);
            assertTrue(AgentJson.extractTextFromContent(parsed.get("content"))
                    .contains("[Provider return-path error]"));
        }
    }

    @Test
    void callLlmIncludesInputFilesWhenSessionHasFilesAndVoices() {
        AgentSession session = new AgentSession(null, "", "llm-1", "", "", false,
                "conv-" + UUID.randomUUID());
        session.inputFiles.addAll(AgentJson.filesToContentBlocks(List.of(
                Map.of("kind", "image", "mime_type", "image/png", "source", "base64",
                        "data", "abc", "name", "a.png"),
                Map.of("kind", "audio", "mime_type", "audio/wav", "source", "base64",
                        "data", "def", "name", "v.wav"))));
        Map<String, Object> payload = AgentLlm.buildAtomicPayload(session,
                List.of(Map.of("role", "user", "content", "hi")));
        assertTrue(payload.containsKey("inputFiles"));
        assertFalse(payload.containsKey("stream"));
        assertEquals(2, AgentJson.asList(payload.get("inputFiles")).size());
        assertEquals("image", ((Map<?, ?>) AgentJson.asList(payload.get("inputFiles")).get(0)).get("type"));
        assertEquals("audio", ((Map<?, ?>) AgentJson.asList(payload.get("inputFiles")).get(1)).get("type"));
    }

    @Test
    void handleBearingInputFileOmitsDataOnPayload() {
        AgentSession session = new AgentSession(null, "", "llm-1", "", "", false,
                "conv-" + UUID.randomUUID());
        Map<String, Object> handleFile = new LinkedHashMap<>();
        handleFile.put("type", "image");
        handleFile.put("kind", "image");
        handleFile.put("mime_type", "image/png");
        handleFile.put("source", "sq-file");
        handleFile.put("fileId", "sq-file:abc");
        handleFile.put("name", "p.png");
        handleFile.put("data", "SHOULD_NOT_SHIP");
        session.inputFiles.add(handleFile);
        Map<String, Object> payload = AgentLlm.buildAtomicPayload(session, List.of());
        Map<?, ?> wire = (Map<?, ?>) AgentJson.asList(payload.get("inputFiles")).get(0);
        assertEquals("sq-file:abc", wire.get("fileId"));
        assertEquals("p.png", wire.get("name"));
        assertEquals("image/png", wire.get("mime_type"));
        assertEquals("sq-file", wire.get("source"));
        assertFalse(wire.containsKey("data"));
        assertEquals("SHOULD_NOT_SHIP", session.inputFiles.get(0).get("data"));
    }

    @Test
    void handleFileInUserMessageBecomesPlaceholder() {
        AgentSession session = new AgentSession(null, "", "llm-1", "", "", false,
                "conv-" + UUID.randomUUID());
        session.inputFiles.add(Map.of("type", "image", "mime_type", "image/png",
                "source", "sq-file", "fileId", "sq-file:abc", "name", "p.png"));
        List<Map<String, Object>> history = List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", "see"),
                        Map.of("type", "image", "mime_type", "image/png", "source", "sq-file",
                                "fileId", "sq-file:abc", "name", "p.png"))));
        Map<String, Object> payload = AgentLlm.buildAtomicPayload(session, history);
        List<Object> content = AgentJson.asList(
                ((Map<?, ?>) AgentJson.asList(payload.get("messages")).get(0)).get("content"));
        assertEquals(Map.of("type", "text", "text", "see"), content.get(0));
        assertEquals(Map.of("type", "text", "text", "[file: p.png]"), content.get(1));
        assertEquals("image", ((Map<?, ?>) AgentJson.asList(history.get(0).get("content")).get(1)).get("type"));
    }

    @Test
    void handleLessFileOnInputFilesKeepsDataAndReplacesMessageCopy() {
        AgentSession session = new AgentSession(null, "", "llm-1", "", "", false,
                "conv-" + UUID.randomUUID());
        session.inputFiles.addAll(AgentJson.filesToContentBlocks(List.of(
                Map.of("kind", "image", "mime_type", "image/png", "source", "base64",
                        "data", "abc", "name", "a.png"))));
        List<Map<String, Object>> history = List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "image", "mime_type", "image/png",
                        "source", "base64", "data", "abc", "name", "a.png"))));
        Map<String, Object> payload = AgentLlm.buildAtomicPayload(session, history);
        Map<?, ?> wire = (Map<?, ?>) AgentJson.asList(payload.get("inputFiles")).get(0);
        assertEquals("abc", wire.get("data"));
        assertFalse(wire.containsKey("fileId"));
        List<Object> content = AgentJson.asList(
                ((Map<?, ?>) AgentJson.asList(payload.get("messages")).get(0)).get("content"));
        assertEquals(Map.of("type", "text", "text", "[file: a.png]"), content.get(0));
    }

    @Test
    void callLlmOmitsInputFilesWhenEmpty() {
        AgentSession session = new AgentSession(null, "", "llm-1", "", "", false,
                "conv-" + UUID.randomUUID());
        Map<String, Object> payload = AgentLlm.buildAtomicPayload(session, List.of());
        assertFalse(payload.containsKey("inputFiles"));
        assertTrue(payload.containsKey("messages"));
        assertTrue(payload.containsKey("tools"));
        assertFalse(payload.containsKey("stream"));
    }

    @Test
    void nodeDefinitionCarriesModelFileCarriage() throws Exception {
        Map<String, Object> def = Json.asMap(Json.parse(
                Files.readString(Path.of("nodes/ai-agent/node_definition.json"))));
        Map<String, Object> model = portByValue(def, "model");
        assertFileAttr(attrByCode(model.get("inputSchema"), "inputFiles"));
        assertFileAttr(attrByCode(model.get("outputSchema"), "outputFiles"));
        Map<String, Object> result = attrByCode(model.get("outputSchema"), "result");
        assertFileAttr(attrByCode(result.get("children"), "output_files"));
        Map<String, Object> mainIn = portByValue(def, "main_input");
        assertFileAttr(attrByCode(mainIn.get("schema"), "files"));
        assertFileAttr(attrByCode(mainIn.get("schema"), "voices"));
        Map<String, Object> mainOut = portByValue(def, "main_output");
        assertFileAttr(attrByCode(mainOut.get("schema"), "output_files"));
        assertFileAttr(attrByCode(mainOut.get("schema"), "voices"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> portByValue(Map<String, Object> def, String value) {
        for (Object p : (List<?>) def.get("ports")) {
            Map<String, Object> port = (Map<String, Object>) p;
            if (value.equals(port.get("value"))) {
                return port;
            }
        }
        throw new AssertionError("missing port " + value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attrByCode(Object schema, String shortCode) {
        for (Object a : (List<?>) schema) {
            Map<String, Object> attr = (Map<String, Object>) a;
            if (shortCode.equals(attr.get("shortCode"))) {
                return attr;
            }
        }
        throw new AssertionError("missing attr " + shortCode);
    }

    private static void assertFileAttr(Map<String, Object> attr) {
        assertEquals("file", attr.get("type"));
        List<String> codes = new ArrayList<>();
        for (Object child : (List<?>) attr.get("children")) {
            codes.add(String.valueOf(((Map<?, ?>) child).get("shortCode")));
        }
        assertEquals(List.of("kind", "mime_type", "source", "data", "name"), codes);
    }
}
