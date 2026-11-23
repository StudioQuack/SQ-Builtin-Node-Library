package com.studioquack.nodelibrary.nodes.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OpenAiConfig: param precedence, model classification, chat-kwargs shaping, stream preference. */
class OpenAiConfigTest {

    private static Map<String, Object> requestWithParams(Map<String, Object> params) {
        List<Object> list = new java.util.ArrayList<>();
        params.forEach((k, v) -> list.add(Map.of("shortCode", k, "value", v)));
        return Map.of("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", list))));
    }

    private static OpenAiConfig configOf(Map<String, Object> params) {
        return OpenAiConfig.from(requestWithParams(params), null);
    }

    private static List<Map<String, Object>> oneUserMessage() {
        return List.of(Map.of("role", "user", "content", "hi"));
    }

    // ---------- from / pick / present ----------

    @Test
    void fromReadsInstanceParams() {
        OpenAiConfig cfg = configOf(Map.of(
                "model", "gpt-4o", "api_key", "sk-test", "temperature", "0.7"));
        assertEquals("gpt-4o", cfg.model());
        assertEquals("sk-test", cfg.apiKey());
        assertEquals("0.7", cfg.get("temperature"));
        assertEquals("gpt-4o", cfg.modelLower());
    }

    @Test
    void fromAppliesDefaultsOnEmptyRequest() {
        OpenAiConfig cfg = OpenAiConfig.from(Map.of(), null);
        assertEquals("gpt-5.6-sol", cfg.model());
        assertEquals("", cfg.apiKey());
        assertNull(cfg.get("temperature"));
    }

    @Test
    void blankModelFallsBackToDefault() {
        assertEquals("gpt-5.6-sol", configOf(Map.of("model", "   ")).model());
    }

    @Test
    void modelLowerLowercases() {
        assertEquals("gpt-4o", configOf(Map.of("model", "GPT-4o")).modelLower());
    }

    @Test
    void pickTreatsBlankAndEmptyAsUnset() {
        assertEquals("v", OpenAiConfig.pick(Map.of("k", "v"), null, "k"));
        assertNull(OpenAiConfig.pick(Map.of("k", "  "), null, "k"));
        assertNull(OpenAiConfig.pick(Map.of("k", Map.of()), null, "k"));
        assertNull(OpenAiConfig.pick(Map.of("k", List.of()), null, "k"));
        assertNull(OpenAiConfig.pick(Map.of(), null, "k"));
        assertNull(OpenAiConfig.pick(null, null, "k"));
    }

    @Test
    void presentSemantics() {
        assertFalse(OpenAiConfig.present(null));
        assertFalse(OpenAiConfig.present(""));
        assertFalse(OpenAiConfig.present("  "));
        assertFalse(OpenAiConfig.present(Map.of()));
        assertFalse(OpenAiConfig.present(List.of()));
        assertTrue(OpenAiConfig.present("x"));
        assertTrue(OpenAiConfig.present(0));
        assertTrue(OpenAiConfig.present(false));
        assertTrue(OpenAiConfig.present(List.of(1)));
    }

    // ---------- model classification ----------

    @ParameterizedTest
    @ValueSource(strings = {"o1", "o1-preview", "o3-mini", "o4-x", "o-foo", "gpt-5", "gpt-5.5",
            "deepseek-reasoning-v3", "O1"})
    void reasoningModels(String model) {
        assertTrue(OpenAiConfig.isReasoning(model));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gpt-4o", "gpt-4.1", "whisper-1", "tts-1"})
    void nonReasoningModels(String model) {
        assertFalse(OpenAiConfig.isReasoning(model));
    }

    @Test
    void reasoningNullIsFalse() {
        assertFalse(OpenAiConfig.isReasoning(null));
    }

    @Test
    void stopUnsupportedModels() {
        assertTrue(OpenAiConfig.stopUnsupported("gpt-5.5"));
        assertTrue(OpenAiConfig.stopUnsupported("o3-mini"));
        assertTrue(OpenAiConfig.stopUnsupported("computer-use-preview"));
        assertFalse(OpenAiConfig.stopUnsupported("gpt-4o"));
        assertFalse(OpenAiConfig.stopUnsupported(null));
    }

    @Test
    void imageTtsSttClassification() {
        assertTrue(OpenAiConfig.isImageModel("gpt-image-1"));
        assertTrue(OpenAiConfig.isImageModel("dall-e-3"));
        assertTrue(OpenAiConfig.isImageModel("dall-e-4"));
        assertFalse(OpenAiConfig.isImageModel("gpt-4o"));
        assertFalse(OpenAiConfig.isImageModel(null));

        assertTrue(OpenAiConfig.isTtsModel("tts-1"));
        assertTrue(OpenAiConfig.isTtsModel("gpt-4o-mini-tts"));
        assertTrue(OpenAiConfig.isTtsModel("custom-tts"));
        assertFalse(OpenAiConfig.isTtsModel("whisper-1"));
        assertFalse(OpenAiConfig.isTtsModel(null));

        assertTrue(OpenAiConfig.isSttModel("whisper-1"));
        assertTrue(OpenAiConfig.isSttModel("gpt-4o-transcribe"));
        assertTrue(OpenAiConfig.isSttModel("x-transcribe-y"));
        assertFalse(OpenAiConfig.isSttModel("tts-1"));
        assertFalse(OpenAiConfig.isSttModel(null));
    }

    // ---------- buildChatKwargs ----------

    @Test
    void chatKwargsNonReasoningKeepsSamplingParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", "gpt-4o");
        params.put("temperature", "0.7");
        params.put("top_p", "0.9");
        params.put("frequency_penalty", "0.1");
        params.put("presence_penalty", "-0.2");
        params.put("max_tokens", "100");
        params.put("max_completion_tokens", "200");
        params.put("stop", "a, b ,c");
        params.put("n", "2");
        params.put("seed", "42");
        params.put("prompt_cache_key", "pk");
        params.put("service_tier", "flex");
        params.put("safety_identifier", "sid");
        params.put("logprobs", "true");
        params.put("top_logprobs", "3");

        Map<String, Object> cfg = configOf(params).buildChatKwargs(oneUserMessage(), null, null);
        assertEquals("gpt-4o", cfg.get("model"));
        assertEquals(oneUserMessage(), cfg.get("messages"));
        assertEquals(0.7d, (Double) cfg.get("temperature"), 1e-9);
        assertEquals(0.9d, (Double) cfg.get("top_p"), 1e-9);
        assertEquals(0.1d, (Double) cfg.get("frequency_penalty"), 1e-9);
        assertEquals(-0.2d, (Double) cfg.get("presence_penalty"), 1e-9);
        assertEquals(200, cfg.get("max_completion_tokens"));
        assertFalse(cfg.containsKey("max_tokens"));
        assertEquals(List.of("a", "b", "c"), cfg.get("stop"));
        assertEquals(2, cfg.get("n"));
        assertEquals(42, cfg.get("seed"));
        assertEquals("pk", cfg.get("prompt_cache_key"));
        assertEquals("flex", cfg.get("service_tier"));
        assertEquals("sid", cfg.get("safety_identifier"));
        assertEquals(Boolean.TRUE, cfg.get("logprobs"));
        assertEquals(3, cfg.get("top_logprobs"));
        assertFalse(cfg.containsKey("reasoning_effort"));
        assertFalse(cfg.containsKey("verbosity"));
        assertFalse(cfg.containsKey("tools"));
    }

    @Test
    void chatKwargsTopLogprobsDroppedUnlessLogprobsTrue() {
        // The API 400s with "'top_logprobs' requires 'logprobs' to be true" — a stray
        // top_logprobs (e.g. a saved instance default) must never ride along alone.
        Map<String, Object> off = configOf(Map.of(
                "model", "gpt-4o", "logprobs", "false", "top_logprobs", "3"))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertEquals(Boolean.FALSE, off.get("logprobs"));
        assertFalse(off.containsKey("top_logprobs"));

        Map<String, Object> unset = configOf(Map.of("model", "gpt-4o", "top_logprobs", "3"))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertFalse(unset.containsKey("logprobs"));
        assertFalse(unset.containsKey("top_logprobs"));
    }

    @Test
    void chatKwargsNeverSendsDeprecatedMaxTokens() {
        Map<String, Object> cfg = configOf(Map.of("model", "gpt-4o", "max_tokens", "100"))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertFalse(cfg.containsKey("max_tokens"));
        assertFalse(cfg.containsKey("max_completion_tokens"));
    }

    @Test
    void chatKwargsZeroTokenCapsAreOmitted() {
        // Saved instances may carry the old '0' definition defaults — a zero cap makes
        // the API generate zero tokens (empty assistant). Non-positive caps never wire.
        Map<String, Object> zeroed = configOf(Map.of(
                "model", "gpt-4o", "max_completion_tokens", "0"))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertFalse(zeroed.containsKey("max_completion_tokens"));
        assertFalse(zeroed.containsKey("max_tokens"));
    }

    @Test
    void chatKwargsReasoningDropsSamplingAndInjectsEffort() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", "o3-mini");
        params.put("temperature", "0.7");
        params.put("top_p", "0.9");
        params.put("reasoning_effort", "high");
        params.put("verbosity", "low");
        params.put("stop", "x");
        params.put("max_completion_tokens", "50");

        Map<String, Object> cfg = configOf(params).buildChatKwargs(oneUserMessage(), null, null);
        assertFalse(cfg.containsKey("temperature"));
        assertFalse(cfg.containsKey("top_p"));
        assertFalse(cfg.containsKey("frequency_penalty"));
        assertFalse(cfg.containsKey("presence_penalty"));
        assertFalse(cfg.containsKey("logprobs"));
        assertFalse(cfg.containsKey("top_logprobs"));
        assertFalse(cfg.containsKey("stop"));
        assertEquals("high", cfg.get("reasoning_effort"));
        assertEquals("low", cfg.get("verbosity"));
        assertEquals(50, cfg.get("max_completion_tokens"));
    }

    @Test
    void chatKwargsReasoningEffortDefaultsToMedium() {
        Map<String, Object> cfg = configOf(Map.of("model", "gpt-5.5"))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertEquals("medium", cfg.get("reasoning_effort"));
        assertFalse(cfg.containsKey("verbosity"));
    }

    @Test
    void chatKwargsStopListTruncatesToFour() {
        Map<String, Object> cfg = configOf(Map.of(
                "model", "gpt-4o", "stop", List.of("a", "b", "c", "d", "e", "f")))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertEquals(List.of("a", "b", "c", "d"), cfg.get("stop"));
    }

    @Test
    void chatKwargsBlankStopIsOmitted() {
        Map<String, Object> cfg = configOf(Map.of("model", "gpt-4o", "stop", "  "))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertFalse(cfg.containsKey("stop"));
    }

    @Test
    void chatKwargsResponseFormatCoercion() {
        assertEquals(Map.of("type", "json_object"), configOf(Map.of("model", "gpt-4o", "response_format", "json"))
                .buildChatKwargs(oneUserMessage(), null, null).get("response_format"));
        assertEquals(Map.of("type", "text"), configOf(Map.of("model", "gpt-4o", "response_format", "text"))
                .buildChatKwargs(oneUserMessage(), null, null).get("response_format"));
        assertEquals(Map.of("type", "json_schema"), configOf(Map.of(
                "model", "gpt-4o", "response_format", "{\"type\":\"json_schema\"}"))
                .buildChatKwargs(oneUserMessage(), null, null).get("response_format"));
        Map<String, Object> asMap = Map.of("type", "json_object");
        assertEquals(asMap, configOf(Map.of("model", "gpt-4o", "response_format", asMap))
                .buildChatKwargs(oneUserMessage(), null, null).get("response_format"));
        assertFalse(configOf(Map.of("model", "gpt-4o", "response_format", "bogus"))
                .buildChatKwargs(oneUserMessage(), null, null).containsKey("response_format"));
    }

    @Test
    void chatKwargsModalitiesFilteredToTextAndAudio() {
        Map<String, Object> cfg = configOf(Map.of("model", "gpt-4o", "modalities", "text, audio ,video"))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertEquals(List.of("text", "audio"), cfg.get("modalities"));

        Map<String, Object> jsonList = configOf(Map.of("model", "gpt-4o", "modalities", "[\"audio\"]"))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertEquals(List.of("audio"), jsonList.get("modalities"));

        Map<String, Object> invalidOnly = configOf(Map.of("model", "gpt-4o", "modalities", "video"))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertFalse(invalidOnly.containsKey("modalities"));
    }

    @Test
    void chatKwargsModalitiesFallBackToOptions() {
        Map<String, Object> cfg = configOf(Map.of("model", "gpt-4o"))
                .buildChatKwargs(oneUserMessage(), null, Map.of("modalities", "audio"));
        assertEquals(List.of("audio"), cfg.get("modalities"));
    }

    @Test
    void chatKwargsAudioCoercion() {
        Map<String, Object> asMap = Map.of("voice", "alloy");
        assertEquals(asMap, configOf(Map.of("model", "gpt-4o", "audio", asMap))
                .buildChatKwargs(oneUserMessage(), null, null).get("audio"));
        assertEquals(Map.of("voice", "alloy"), configOf(Map.of(
                "model", "gpt-4o", "audio", "{\"voice\":\"alloy\"}"))
                .buildChatKwargs(oneUserMessage(), null, null).get("audio"));
        assertFalse(configOf(Map.of("model", "gpt-4o", "audio", "not json"))
                .buildChatKwargs(oneUserMessage(), null, null).containsKey("audio"));
    }

    @Test
    void chatKwargsToolsAndParallelToolCalls() {
        List<Object> tools = List.of(Map.of(
                "name", "get_weather", "description", "weather", "parameters", Map.of("type", "object")));
        Map<String, Object> cfg = configOf(Map.of(
                "model", "gpt-4o", "parallel_tool_calls", "true"))
                .buildChatKwargs(oneUserMessage(), tools, null);
        assertEquals("auto", cfg.get("tool_choice"));
        assertEquals(Boolean.TRUE, cfg.get("parallel_tool_calls"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> built = (List<Map<String, Object>>) cfg.get("tools");
        assertEquals(1, built.size());
        assertEquals("function", built.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) built.get(0).get("function");
        assertEquals("get_weather", fn.get("name"));
    }

    @Test
    void chatKwargsNoToolsMeansNoToolKeys() {
        Map<String, Object> cfg = configOf(Map.of("model", "gpt-4o", "parallel_tool_calls", "true"))
                .buildChatKwargs(oneUserMessage(), null, null);
        assertFalse(cfg.containsKey("tools"));
        assertFalse(cfg.containsKey("tool_choice"));
        assertFalse(cfg.containsKey("parallel_tool_calls"));

        Map<String, Object> emptyTools = configOf(Map.of("model", "gpt-4o"))
                .buildChatKwargs(oneUserMessage(), List.of(), null);
        assertFalse(emptyTools.containsKey("tools"));
    }

    // ---------- shouldStream / streamPref ----------

    @Test
    void streamDefaultsToTrueWhenUnset() {
        OpenAiConfig cfg = OpenAiConfig.from(Map.of(), null);
        assertTrue(cfg.shouldStream(null));
        assertTrue(cfg.shouldStream(Map.of()));
        assertNull(cfg.streamPref(null));
    }

    @Test
    void streamInputOptionWins() {
        OpenAiConfig cfg = OpenAiConfig.from(Map.of(), null);
        assertFalse(cfg.shouldStream(Map.of("stream", false)));
        assertFalse(cfg.shouldStream(Map.of("stream", "no")));
        assertFalse(cfg.shouldStream(Map.of("streaming", "off")));
        assertTrue(cfg.shouldStream(Map.of("stream", "true")));
    }

    @Test
    void streamFallsBackToConfigThenDefault() {
        OpenAiConfig cfgFalse = configOf(Map.of("stream", "false"));
        assertFalse(cfgFalse.shouldStream(null));
        assertTrue(cfgFalse.shouldStream(Map.of("stream", "true")));

        OpenAiConfig cfgRemovedAlias = configOf(Map.of("streaming", "0"));
        assertTrue(cfgRemovedAlias.shouldStream(null));

        OpenAiConfig cfgNone = OpenAiConfig.from(Map.of(), null);
        assertTrue(cfgNone.shouldStream(Map.of("stream", "garbage")));
    }

    // ---------- stripUnsupported ----------

    @Test
    void stripUnsupportedRemovesNamedParam() {
        Map<String, Object> cfg = new LinkedHashMap<>(Map.of("temperature", 0.7, "model", "x"));
        assertTrue(OpenAiConfig.stripUnsupported(cfg, "Error: Unsupported parameter: 'temperature'."));
        assertFalse(cfg.containsKey("temperature"));
        assertEquals("x", cfg.get("model"));
    }

    @Test
    void stripUnsupportedIgnoresUnrelatedErrors() {
        Map<String, Object> cfg = new LinkedHashMap<>(Map.of("temperature", 0.7));
        assertFalse(OpenAiConfig.stripUnsupported(cfg, "rate limit exceeded"));
        assertFalse(OpenAiConfig.stripUnsupported(cfg, "Unsupported parameter: 'baz'"));
        assertFalse(OpenAiConfig.stripUnsupported(cfg, null));
        assertFalse(OpenAiConfig.stripUnsupported(null, "Unsupported parameter: 'temperature'"));
        assertEquals(0.7, cfg.get("temperature"));
    }

    // ---------- ApiException ----------

    @Test
    void apiExceptionCarriesStatusAndBody() {
        OpenAiConfig.ApiException e = new OpenAiConfig.ApiException(429, "rate limited", "{\"error\":1}");
        assertEquals(429, e.status);
        assertEquals("{\"error\":1}", e.body);
        assertEquals("rate limited", e.getMessage());
    }

    // ---------- timeouts ----------

    @Test
    void connectTimeoutIsTenSecondsOnSharedClient() {
        assertEquals(10, OpenAiConfig.CONNECT_TIMEOUT.toSeconds());
        assertEquals(300, OpenAiConfig.REQUEST_TIMEOUT.toSeconds());
        assertEquals(600, OpenAiConfig.STREAM_IDLE_TIMEOUT.toSeconds());
    }

    @Test
    void fromDefaultsReadAndIdleTimeouts() {
        OpenAiConfig cfg = OpenAiConfig.from(Map.of(), null);
        assertEquals(OpenAiConfig.REQUEST_TIMEOUT, cfg.readTimeout());
        assertEquals(OpenAiConfig.STREAM_IDLE_TIMEOUT, cfg.streamIdleTimeout());
    }

    @Test
    void fromClampsReadAndIdleOverrides() {
        OpenAiConfig low = configOf(Map.of(
                "read_timeout_seconds", "5", "stream_idle_timeout_seconds", "1"));
        assertEquals(30, low.readTimeout().toSeconds());
        assertEquals(5, low.streamIdleTimeout().toSeconds());

        OpenAiConfig high = configOf(Map.of(
                "read_timeout_seconds", "999", "stream_idle_timeout_seconds", "999"));
        assertEquals(600, high.readTimeout().toSeconds());
        assertEquals(600, high.streamIdleTimeout().toSeconds());

        OpenAiConfig mid = configOf(Map.of(
                "read_timeout_seconds", 120, "stream_idle_timeout_seconds", 15));
        assertEquals(120, mid.readTimeout().toSeconds());
        assertEquals(15, mid.streamIdleTimeout().toSeconds());
    }

    @Test
    void fromIgnoresInvalidTimeoutOverrides() {
        OpenAiConfig cfg = configOf(Map.of(
                "read_timeout_seconds", "nope", "stream_idle_timeout_seconds", ""));
        assertEquals(300, cfg.readTimeout().toSeconds());
        assertEquals(600, cfg.streamIdleTimeout().toSeconds());
    }

    @Test
    void timeoutMessageContainsModelEndpointElapsed() {
        String msg = OpenAiConfig.timeoutMessage("gpt-5", "/chat/completions", 12, "request timed out");
        assertTrue(msg.contains("gpt-5"), msg);
        assertTrue(msg.contains("/chat/completions"), msg);
        assertTrue(msg.contains("12"), msg);
        assertTrue(msg.contains("request timed out"), msg);
    }

    @Test
    void wrapHttpTimeoutMapsRequestTimeout() {
        long started = System.nanoTime() - 4_000_000_000L;
        RuntimeException wrapped = OpenAiConfig.wrapHttpTimeout(
                new java.net.http.HttpTimeoutException("timed out"),
                "gpt-5.5", "/responses", started);
        assertTrue(wrapped instanceof OpenAiConfig.TimeoutException);
        String msg = wrapped.getMessage();
        assertTrue(msg.contains("gpt-5.5"), msg);
        assertTrue(msg.contains("/responses"), msg);
        assertTrue(msg.contains("elapsed="), msg);
        assertTrue(OpenAiConfig.isTimeoutFailure(wrapped));
        assertTrue(OpenAiConfig.isTimeoutFailure(new java.net.http.HttpConnectTimeoutException("c")));
        assertTrue(OpenAiConfig.isTimeoutFailure(new OpenAiIdleStream.StreamStalledException(
                java.time.Duration.ofSeconds(1))));
        assertFalse(OpenAiConfig.isTimeoutFailure(new RuntimeException("nope")));
    }

    @Test
    void openaiErrorMessageParsesProviderJsonAndFallsBack() {
        assertEquals("invalid content part type",
                OpenAiChat.openaiErrorMessage(400, "{\"error\":{\"message\":\"invalid content part type\"}}"));
        assertEquals("HTTP 400", OpenAiChat.openaiErrorMessage(400, ""));
        assertEquals("HTTP 400", OpenAiChat.openaiErrorMessage(400, "not-json"));
        assertEquals("model does not support images",
                OpenAiChat.openaiErrorMessage(400, "{\"error\":{\"message\":\"model does not support images\"}}"));
        assertTrue(OpenAiChat.openaiErrorMessage(401, "{\"error\":{\"message\":\"Bearer sk-abc123xyz\"}}")
                .contains("[redacted]"));
    }
}
