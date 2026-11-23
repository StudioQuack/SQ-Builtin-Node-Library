package com.studioquack.nodelibrary.nodes.localllm;

import com.studioquack.nodelibrary.core.OpenAiContentParts;
import com.studioquack.nodelibrary.nodes.ai.NodeParams;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorConfig;
import studioquack.StudioQuackHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed Local LLM parameters (Python {@code PARAM_KEYS}).
 *
 * <p>Designer {@code model} values look like {@code envId::modelId}. The prefix is
 * routing metadata for the daemon's cross-env LLM channel; the provider only
 * ever sees the bare model id. No prefix → this environment.
 *
 * <p>Instance {@code configurationParameters} win over
 * {@code helper.getParameterValueByShortCode}.
 */
final class LocalLlmConfig {

    /**
     * Designer config keys. OpenAI-platform-only fields ({@code service_tier},
     * {@code safety_identifier}) are not sent: OpenAI-compatible local servers do not define them.
     */
    static final List<String> PARAM_KEYS = List.of(
            "model", "temperature", "top_p", "frequency_penalty", "presence_penalty",
            "max_completion_tokens", "max_tokens", "stop", "n", "logprobs",
            "top_logprobs", "reasoning_effort", "verbosity", "response_format",
            "modalities", "audio", "seed", "prompt_cache_key",
            "parallel_tool_calls", "stream");

    private static final List<String> BOOL_KEYS = List.of("logprobs", "parallel_tool_calls", "stream");
    private static final List<String> INT_KEYS = List.of(
            "max_completion_tokens", "max_tokens", "n", "top_logprobs", "seed");
    private static final List<String> FLOAT_KEYS = List.of(
            "temperature", "top_p", "frequency_penalty", "presence_penalty");
    private static final List<String> JSON_KEYS = List.of("response_format", "modalities", "audio");

    final String model;
    /** Target environment id; {@code null} means this engine. */
    final String envId;
    final boolean stream;
    /** Coerced wire fields ({@code stream} omitted — set by caller). */
    final Map<String, Object> params;

    private LocalLlmConfig(String model, String envId, boolean stream, Map<String, Object> params) {
        this.model = model;
        this.envId = envId;
        this.stream = stream;
        this.params = params;
    }

    static LocalLlmConfig load(Map<String, Object> request, StudioQuackHelper helper, Object payload) {
        Map<String, Object> instance = new LinkedHashMap<>(EmulatorConfig.readNodeParams(request));
        Map<String, Object> coerced = new LinkedHashMap<>();
        String model = null;
        String envId = null;
        for (String key : PARAM_KEYS) {
            Object v = NodeParams.param(instance, helper, key, null);
            if (v == null) {
                continue;
            }
            if ("model".equals(key)) {
                String[] split = splitEnvModel(v);
                envId = split[0];
                model = split[1];
                if (model != null && !model.isBlank()) {
                    coerced.put("model", model);
                }
                continue;
            }
            Object typed = coerce(key, v);
            if (typed != null) {
                coerced.put(key, typed);
            }
        }
        // OpenAI-compatible servers 400 on top_logprobs unless logprobs=true — and the node
        // definition ships top_logprobs=0 as a default, so it would ride along on every call.
        if (!Boolean.TRUE.equals(NodeParams.boolOrNull(coerced.get("logprobs")))) {
            coerced.remove("top_logprobs");
        }
        // Token caps and n accept POSITIVE integers only ("Empty/unset → omitted"). Instances
        // created from the old definition carry '0' defaults: max_tokens=0 makes the provider
        // generate zero tokens (empty assistant), so a non-positive cap is always dropped.
        dropNonPositive(coerced, "max_completion_tokens");
        dropNonPositive(coerced, "max_tokens");
        dropNonPositive(coerced, "n");
        // Contract: when both caps are set, max_completion_tokens wins.
        if (coerced.get("max_completion_tokens") != null) {
            coerced.remove("max_tokens");
        }
        boolean stream = resolveStream(coerced, payload);
        coerced.remove("stream");
        return new LocalLlmConfig(model, envId, stream, coerced);
    }

    /**
     * Input {@code stream}/{@code streaming} wins when parseable; else config {@code stream};
     * else {@code true} (Java streaming path is the default).
     */
    static boolean resolveStream(Map<String, Object> coerced, Object payload) {
        Map<String, Object> in = Map.of();
        if (payload instanceof Map<?, ?>) {
            in = new LinkedHashMap<>();
            Map<String, Object> p = asObjMap(payload);
            in.put("stream", p.get("stream"));
            in.put("streaming", p.get("streaming"));
            Object opts = p.get("options");
            if (opts instanceof Map<?, ?>) {
                Map<String, Object> o = asObjMap(opts);
                if (in.get("stream") == null) {
                    in.put("stream", o.get("stream"));
                }
                if (in.get("streaming") == null) {
                    in.put("streaming", o.get("streaming"));
                }
            }
        }
        Boolean fromInput = NodeParams.boolOrNull(in.get("stream"));
        if (fromInput == null) {
            fromInput = NodeParams.boolOrNull(in.get("streaming"));
        }
        if (fromInput != null) {
            return fromInput;
        }
        Boolean fromCfg = NodeParams.boolOrNull(coerced.get("stream"));
        return fromCfg == null || fromCfg;
    }

    /**
     * @return {@code [envId or null, bareModel]}
     */
    static String[] splitEnvModel(Object raw) {
        if (raw == null) {
            return new String[] {null, null};
        }
        String s = String.valueOf(raw).trim();
        int idx = s.indexOf("::");
        if (idx <= 0) {
            return new String[] {null, s.isEmpty() ? null : s};
        }
        String env = s.substring(0, idx).trim();
        String model = s.substring(idx + 2).trim();
        return new String[] {
                env.isEmpty() ? null : env,
                model.isEmpty() ? null : model
        };
    }

    /** Remove {@code key} when its coerced value is a number ≤ 0 (never a valid cap/count). */
    private static void dropNonPositive(Map<String, Object> coerced, String key) {
        Object v = coerced.get(key);
        if (v instanceof Number num && num.longValue() <= 0) {
            coerced.remove(key);
        }
    }

    static Object coerce(String key, Object v) {
        if (BOOL_KEYS.contains(key)) {
            return NodeParams.boolOrNull(v);
        }
        if (INT_KEYS.contains(key)) {
            return NodeParams.intOrNull(v);
        }
        if (FLOAT_KEYS.contains(key)) {
            return NodeParams.floatOrNull(v);
        }
        if (JSON_KEYS.contains(key)) {
            return NodeParams.jsonOrRaw(v, false);
        }
        if ("stop".equals(key)) {
            return NodeParams.jsonOrRaw(v, true);
        }
        return v;
    }

    /**
     * OpenAI-style body for {@code llm().chatStream}. Always sets {@code stream:true}.
     * Does not include the {@code streaming} alias.
     *
     * <p>v2 media blocks ride the message {@code content} / {@code files} from the agent;
     * local OpenAI-compatible servers (llama.cpp, LM Studio, vLLM) accept only
     * {@code text} / {@code image_url} parts, so messages are folded through
     * {@link OpenAiContentParts} under the BASIC profile — images become
     * {@code image_url} data URLs, text-like documents are inlined as text, and binary
     * attachments degrade to an explicit notice instead of a provider 400.
     */
    Map<String, Object> streamBody(Object messages, Object prompt, Object tools) {
        return streamBody(messages, prompt, tools, null);
    }

    /**
     * Same as {@link #streamBody(Object, Object, Object)} but folds top-level
     * {@code inputFiles} onto the last user message (or a new user message)
     * before the BASIC {@link OpenAiContentParts} conversion. Local servers
     * reject {@code input_audio}/{@code file} parts — BASIC never emits those.
     */
    Map<String, Object> streamBody(Object messages, Object prompt, Object tools, Object inputFiles) {
        Map<String, Object> body = new LinkedHashMap<>(params);
        if (model != null && !model.isBlank()) {
            body.put("model", model);
        }
        List<Object> fileList = asFileList(inputFiles);
        if (messages != null || !fileList.isEmpty()) {
            List<Object> src = messages != null ? asList(messages) : new java.util.ArrayList<>();
            if (!fileList.isEmpty()) {
                src = OpenAiContentParts.appendFilesToLastUserMessage(src, fileList);
            }
            body.put("messages", OpenAiContentParts.messagesToOpenAi(src,
                    OpenAiContentParts.Profile.BASIC));
        }
        if (prompt != null) {
            body.put("prompt", prompt);
        }
        if (tools != null) {
            body.put("tools", tools);
        }
        if (envId != null && !envId.isBlank()) {
            body.put("envId", envId);
        }
        body.put("stream", true);
        // Ask the provider for a usage frame on the final chunk (same as the
        // OpenAI node) so reasoning_tokens/usage survive the stream.
        body.putIfAbsent("stream_options", Map.of("include_usage", true));
        return body;
    }

    private static List<Object> asList(Object o) {
        return o instanceof List<?> list ? new java.util.ArrayList<>(list) : new java.util.ArrayList<>();
    }

    /** A file list, or a single file map wrapped as a one-element list. */
    private static List<Object> asFileList(Object o) {
        if (o instanceof List<?>) {
            return asList(o);
        }
        if (o instanceof Map<?, ?>) {
            return new java.util.ArrayList<>(List.of(o));
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObjMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }
}
