package com.studioquack.nodelibrary.nodes.localllm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LocalLlmConfig: env::model split, typed coercion, stream resolution, wire body shape. */
class LocalLlmConfigTest {

    private static Map<String, Object> requestWithParams(Map<String, Object> params) {
        List<Object> list = new java.util.ArrayList<>();
        params.forEach((k, v) -> list.add(Map.of("shortCode", k, "value", v)));
        return Map.of("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", list))));
    }

    // ---------- PARAM_KEYS ----------

    @Test
    void paramKeysMatchDesignerSurface() {
        assertEquals(20, LocalLlmConfig.PARAM_KEYS.size());
        assertTrue(LocalLlmConfig.PARAM_KEYS.contains("model"));
        assertTrue(LocalLlmConfig.PARAM_KEYS.contains("stream"));
        assertFalse(LocalLlmConfig.PARAM_KEYS.contains("streaming"));
        assertFalse(LocalLlmConfig.PARAM_KEYS.contains("service_tier"));
        assertFalse(LocalLlmConfig.PARAM_KEYS.contains("safety_identifier"));
        assertTrue(LocalLlmConfig.PARAM_KEYS.contains("max_completion_tokens"));
    }

    // ---------- load ----------

    @Test
    void loadSplitsEnvPrefixedModel() {
        LocalLlmConfig cfg = LocalLlmConfig.load(
                requestWithParams(Map.of("model", "env1::llama3.1")), null, null);
        assertEquals("llama3.1", cfg.model);
        assertEquals("env1", cfg.envId);
        assertEquals("llama3.1", cfg.params.get("model"));
    }

    @Test
    void loadBareModelHasNoEnv() {
        LocalLlmConfig cfg = LocalLlmConfig.load(
                requestWithParams(Map.of("model", "llama3")), null, null);
        assertEquals("llama3", cfg.model);
        assertNull(cfg.envId);
    }

    @Test
    void loadDefaultsToStreamingWithNoParams() {
        LocalLlmConfig cfg = LocalLlmConfig.load(Map.of(), null, null);
        assertNull(cfg.model);
        assertNull(cfg.envId);
        assertTrue(cfg.stream);
        assertTrue(cfg.params.isEmpty());
    }

    @Test
    void loadCoercesTypes() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("temperature", "0.5");
        params.put("top_p", "0.9");
        params.put("max_tokens", "100");
        params.put("n", "2");
        params.put("logprobs", "true");
        params.put("seed", "42");
        params.put("verbosity", "high");
        LocalLlmConfig cfg = LocalLlmConfig.load(requestWithParams(params), null, null);
        assertEquals(0.5d, (Double) cfg.params.get("temperature"), 1e-9);
        assertEquals(0.9d, (Double) cfg.params.get("top_p"), 1e-9);
        assertEquals(100, cfg.params.get("max_tokens"));
        assertEquals(2, cfg.params.get("n"));
        assertEquals(Boolean.TRUE, cfg.params.get("logprobs"));
        assertEquals(42, cfg.params.get("seed"));
        assertEquals("high", cfg.params.get("verbosity"));
    }

    @Test
    void topLogprobsDroppedUnlessLogprobsTrue() {
        // The definition default ("0") must never reach the wire on its own:
        // OpenAI-compatible servers 400 with "'top_logprobs' requires 'logprobs' to be true".
        LocalLlmConfig off = LocalLlmConfig.load(requestWithParams(
                Map.of("logprobs", "false", "top_logprobs", "0")), null, null);
        assertFalse(off.params.containsKey("top_logprobs"));
        assertEquals(Boolean.FALSE, off.params.get("logprobs"));

        LocalLlmConfig unset = LocalLlmConfig.load(requestWithParams(
                Map.of("top_logprobs", "5")), null, null);
        assertFalse(unset.params.containsKey("top_logprobs"));

        LocalLlmConfig on = LocalLlmConfig.load(requestWithParams(
                Map.of("logprobs", "true", "top_logprobs", "5")), null, null);
        assertEquals(Boolean.TRUE, on.params.get("logprobs"));
        assertEquals(5, on.params.get("top_logprobs"));
    }

    @Test
    void nonPositiveTokenCapsAndNAreDropped() {
        // Instances created from the old definition carry '0' defaults — max_tokens=0 makes
        // the provider generate zero tokens (empty assistant). Non-positive caps never wire.
        LocalLlmConfig zeroed = LocalLlmConfig.load(requestWithParams(
                Map.of("max_completion_tokens", "0", "max_tokens", "0", "n", "0")), null, null);
        assertFalse(zeroed.params.containsKey("max_completion_tokens"));
        assertFalse(zeroed.params.containsKey("max_tokens"));
        assertFalse(zeroed.params.containsKey("n"));

        LocalLlmConfig positive = LocalLlmConfig.load(requestWithParams(
                Map.of("max_completion_tokens", "256", "n", "1")), null, null);
        assertEquals(256, positive.params.get("max_completion_tokens"));
        assertEquals(1, positive.params.get("n"));
    }

    @Test
    void maxCompletionTokensWinsOverMaxTokens() {
        LocalLlmConfig cfg = LocalLlmConfig.load(requestWithParams(
                Map.of("max_completion_tokens", "256", "max_tokens", "512")), null, null);
        assertEquals(256, cfg.params.get("max_completion_tokens"));
        assertFalse(cfg.params.containsKey("max_tokens"));
    }

    @Test
    void loadDropsStreamKeysFromWireParams() {
        LocalLlmConfig cfg = LocalLlmConfig.load(
                requestWithParams(Map.of("stream", "false", "streaming", "true")), null, null);
        assertFalse(cfg.stream);
        assertFalse(cfg.params.containsKey("stream"));
        assertFalse(cfg.params.containsKey("streaming"));
    }

    @Test
    void loadJsonKeysParseOrDrop() {
        LocalLlmConfig parsed = LocalLlmConfig.load(requestWithParams(
                Map.of("response_format", "{\"type\":\"json_object\"}")), null, null);
        assertEquals(Map.of("type", "json_object"), parsed.params.get("response_format"));

        LocalLlmConfig invalid = LocalLlmConfig.load(requestWithParams(
                Map.of("response_format", "not json")), null, null);
        assertFalse(invalid.params.containsKey("response_format"));
    }

    @Test
    void loadStopKeepsRawStringButParsesJsonArray() {
        LocalLlmConfig raw = LocalLlmConfig.load(requestWithParams(Map.of("stop", "a,b")), null, null);
        assertEquals("a,b", raw.params.get("stop"));

        LocalLlmConfig json = LocalLlmConfig.load(requestWithParams(Map.of("stop", "[\"a\",\"b\"]")), null, null);
        assertEquals(List.of("a", "b"), json.params.get("stop"));
    }

    // ---------- stream resolution ----------

    @Test
    void payloadStreamBeatsConfig() {
        LocalLlmConfig cfg = LocalLlmConfig.load(
                requestWithParams(Map.of("stream", "true")), null, Map.of("stream", "false"));
        assertFalse(cfg.stream);
    }

    @Test
    void payloadStreamingAliasAndNestedOptionsAreRead() {
        assertFalse(LocalLlmConfig.load(Map.of(), null, Map.of("streaming", "0")).stream);
        assertFalse(LocalLlmConfig.load(Map.of(), null,
                Map.of("options", Map.of("stream", "no"))).stream);
        assertFalse(LocalLlmConfig.load(Map.of(), null,
                Map.of("options", Map.of("streaming", "off"))).stream);
    }

    @Test
    void unparseablePayloadStreamFallsBackToConfig() {
        LocalLlmConfig cfg = LocalLlmConfig.load(
                requestWithParams(Map.of("stream", "false")), null, Map.of("stream", "garbage"));
        assertFalse(cfg.stream);
        LocalLlmConfig cfgDefault = LocalLlmConfig.load(Map.of(), null, Map.of("stream", "garbage"));
        assertTrue(cfgDefault.stream);
    }

    @Test
    void resolveStreamDirect() {
        assertTrue(LocalLlmConfig.resolveStream(Map.of(), null));
        assertFalse(LocalLlmConfig.resolveStream(Map.of("stream", false), null));
        assertTrue(LocalLlmConfig.resolveStream(Map.of("streaming", "no"), null));
        assertFalse(LocalLlmConfig.resolveStream(Map.of("stream", true), Map.of("stream", "false")));
        assertFalse(LocalLlmConfig.resolveStream(Map.of(), Map.of("streaming", 0)));
    }

    // ---------- splitEnvModel ----------

    static Stream<Arguments> envModelCases() {
        return Stream.of(
                Arguments.of(null, null, null),
                Arguments.of("", null, null),
                Arguments.of("env::mod", "env", "mod"),
                Arguments.of("mod", null, "mod"),
                Arguments.of("env::", "env", null),
                Arguments.of("::mod", null, "::mod"),
                Arguments.of("a::b::c", "a", "b::c"),
                Arguments.of("  e :: m  ", "e", "m"));
    }

    @ParameterizedTest
    @MethodSource("envModelCases")
    void splitEnvModelCases(String raw, String expectedEnv, String expectedModel) {
        String[] split = LocalLlmConfig.splitEnvModel(raw);
        assertEquals(expectedEnv, split[0]);
        assertEquals(expectedModel, split[1]);
    }

    // ---------- coerce ----------

    @Test
    void coerceRoutesByKeyType() {
        assertEquals(Boolean.TRUE, LocalLlmConfig.coerce("logprobs", "yes"));
        assertEquals(Boolean.FALSE, LocalLlmConfig.coerce("stream", "off"));
        assertEquals(3, LocalLlmConfig.coerce("n", "3"));
        assertEquals(0.9d, (Double) LocalLlmConfig.coerce("top_p", "0.9"), 1e-9);
        assertNull(LocalLlmConfig.coerce("response_format", "bad"));
        assertEquals("bad", LocalLlmConfig.coerce("stop", "bad"));
        assertEquals("high", LocalLlmConfig.coerce("verbosity", "high"));
        assertNull(LocalLlmConfig.coerce("temperature", "hot"));
    }

    // ---------- streamBody ----------

    @Test
    void streamBodyAlwaysSetsStreamTrue() {
        LocalLlmConfig cfg = LocalLlmConfig.load(
                requestWithParams(Map.of("model", "env1::llama3", "temperature", "0.5")), null, null);
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", "hi"));
        Map<String, Object> body = cfg.streamBody(messages, "prompt", null);
        assertEquals(Boolean.TRUE, body.get("stream"));
        assertEquals("llama3", body.get("model"));
        assertEquals("env1", body.get("envId"));
        assertEquals(messages, body.get("messages"));
        assertEquals("prompt", body.get("prompt"));
        assertEquals(0.5d, (Double) body.get("temperature"), 1e-9);
        assertFalse(body.containsKey("tools"));
        assertFalse(body.containsKey("streaming"));
    }

    @Test
    void streamBodyFoldsV2MediaIntoLocalServerSafeParts() {
        // Regression: v2 document/audio blocks reached the OpenAI-compatible server
        // verbatim and were rejected with "content objects must have a 'type' field
        // that is either 'text' or 'image_url'".
        LocalLlmConfig cfg = LocalLlmConfig.load(Map.of(), null, null);
        List<Map<String, Object>> messages = List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", "what is in these?"),
                        Map.of("type", "image", "mime_type", "image/png",
                                "source", "base64", "data", "aGVsbG8=", "name", "p.png"),
                        Map.of("type", "document", "mime_type", "application/pdf",
                                "source", "base64", "data", "JVBERi0x", "name", "spec.pdf"))));
        Map<String, Object> body = cfg.streamBody(messages, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outMessages = (List<Map<String, Object>>) body.get("messages");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) outMessages.get(0).get("content");
        for (Map<String, Object> part : parts) {
            String type = String.valueOf(part.get("type"));
            assertTrue("text".equals(type) || "image_url".equals(type),
                    "local server rejects part type: " + type);
        }
        assertEquals("image_url", parts.get(1).get("type"));
        String notice = String.valueOf(parts.get(2).get("text"));
        assertTrue(notice.contains("spec.pdf"), notice);
        assertTrue(notice.contains("not sent"), notice);
    }

    @Test
    void streamBodyFoldsTopLevelInputFilesIntoBasicImageUrl() {
        LocalLlmConfig cfg = LocalLlmConfig.load(Map.of(), null, null);
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", "look"));
        List<Map<String, Object>> inputFiles = List.of(Map.of(
                "kind", "image", "mime_type", "image/png",
                "source", "base64", "data", "aGVsbG8=", "name", "p.png"));
        Map<String, Object> body = cfg.streamBody(messages, null, null, inputFiles);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outMessages = (List<Map<String, Object>>) body.get("messages");
        assertEquals(1, outMessages.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) outMessages.get(0).get("content");
        assertEquals("text", parts.get(0).get("type"));
        assertEquals("image_url", parts.get(1).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> imageUrl = (Map<String, Object>) parts.get(1).get("image_url");
        assertEquals("data:image/png;base64,aGVsbG8=", imageUrl.get("url"));
        for (Map<String, Object> part : parts) {
            String type = String.valueOf(part.get("type"));
            assertTrue("text".equals(type) || "image_url".equals(type),
                    "BASIC must not emit local-server-rejected part: " + type);
        }
    }

    @Test
    void streamBodyInputFilesCreateUserMessageWhenNone() {
        LocalLlmConfig cfg = LocalLlmConfig.load(Map.of(), null, null);
        List<Map<String, Object>> inputFiles = List.of(Map.of(
                "kind", "image", "mime_type", "image/png",
                "source", "base64", "data", "aGVsbG8=", "name", "shot.png"));
        Map<String, Object> body = cfg.streamBody(null, null, null, inputFiles);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outMessages = (List<Map<String, Object>>) body.get("messages");
        assertEquals("user", outMessages.get(0).get("role"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) outMessages.get(0).get("content");
        assertEquals("image_url", parts.get(0).get("type"));
    }

    @Test
    void streamBodyInputFilesStayOnBasicProfile() {
        // Audio must degrade to a text notice — local servers reject input_audio.
        LocalLlmConfig cfg = LocalLlmConfig.load(Map.of(), null, null);
        Map<String, Object> body = cfg.streamBody(
                List.of(Map.of("role", "user", "content", "listen")),
                null, null,
                List.of(Map.of("kind", "audio", "mime_type", "audio/wav",
                        "source", "base64", "data", "UklGRg==", "name", "clip.wav")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outMessages = (List<Map<String, Object>>) body.get("messages");
        Object content = outMessages.get(0).get("content");
        String text = content instanceof String s ? s : String.valueOf(content);
        assertTrue(text.contains("clip.wav") || text.contains("not sent"), text);
        assertFalse(text.contains("input_audio"), text);
    }

    @Test
    void streamBodyOmitsMissingPieces() {
        LocalLlmConfig cfg = LocalLlmConfig.load(Map.of(), null, null);
        Map<String, Object> body = cfg.streamBody(null, null, List.of(Map.of("name", "t")));
        assertEquals(Boolean.TRUE, body.get("stream"));
        assertFalse(body.containsKey("model"));
        assertFalse(body.containsKey("envId"));
        assertFalse(body.containsKey("messages"));
        assertFalse(body.containsKey("prompt"));
        assertTrue(body.containsKey("tools"));
    }
}
