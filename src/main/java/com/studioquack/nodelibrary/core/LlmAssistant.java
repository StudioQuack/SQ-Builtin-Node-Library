package com.studioquack.nodelibrary.core;

import studioquack.StudioQuackError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Canonical v2 assistant payload assembly for spec-driven LLM streaming.
 *
 * <p>Mirrors {@code OpenAiChat.makeResponse} / {@code errorResponse} key-for-key so
 * {@code PayloadUnwrap} treats Official-API providers identically to openai-models.
 * The error content prefix is {@code [provider error]} (not OpenAI-branded) because
 * this assembler is shared by every spec-driven provider.
 */
public final class LlmAssistant {

    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+\\S+");
    private static final Pattern SK = Pattern.compile("sk-[A-Za-z0-9_-]+");

    private LlmAssistant() {
        // Utility — never instantiated.
    }

    /**
     * Provider / LLM failure. Throws {@link StudioQuackError} so the dispatcher
     * posts {@code /v1/helper/fail} instead of returning an {@code error} OUTPUT.
     * Return type stays {@code Map} so existing {@code return ...errorResponse()} sites compile.
     */
    public static Map<String, Object> errorResponse(String message) {
        throw new StudioQuackError("PROVIDER_ERROR", redact(message));
    }

    public static Map<String, Object> errorResponse(String message, String finishReason) {
        String msg = redact(message);
        if (finishReason == null || finishReason.isBlank() || "error".equals(finishReason)) {
            throw new StudioQuackError("PROVIDER_ERROR", msg);
        }
        throw new StudioQuackError("PROVIDER_ERROR", msg, Map.of("finish_reason", finishReason));
    }

    /** Strip Bearer tokens and sk- keys. Never include {@code api_key}. */
    public static String redact(String message) {
        if (message == null) {
            return "";
        }
        String s = BEARER.matcher(message).replaceAll("Bearer [redacted]");
        return SK.matcher(s).replaceAll("sk-[redacted]");
    }

    /**
     * Success envelope matching openai-models {@code _make_response}:
     * {@code role, content[{type:text,text}], tool_calls?, thoughts?, thinking? (v1 mirror),
     * output_files?, finish_reason?, usage?}.
     */
    public static Map<String, Object> makeResponse(List<String> textParts, List<Map<String, Object>> toolCalls,
                                                   List<Map<String, Object>> thoughts,
                                                   List<Map<String, Object>> outputFiles,
                                                   String finishReason, Map<String, Object> usage) {
        String text = textParts == null ? "" : String.join("", textParts);
        if (toolCalls != null && !toolCalls.isEmpty() && text.isBlank()) {
            text = "I am calling a tool to answer your request.";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", "assistant");
        out.put("content", List.of(Map.of("type", "text", "text", text)));
        if (toolCalls != null && !toolCalls.isEmpty()) {
            out.put("tool_calls", toolCalls);
        }
        if (thoughts != null && !thoughts.isEmpty()) {
            out.put("thoughts", thoughts);
            List<Map<String, Object>> thinking = new ArrayList<>();
            for (Map<String, Object> t : thoughts) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", "thinking");
                Object piece = t.get("text") != null ? t.get("text") : t.get("thinking");
                row.put("thinking", piece == null ? "" : String.valueOf(piece));
                thinking.add(row);
            }
            out.put("thinking", thinking);
        }
        if (outputFiles != null && !outputFiles.isEmpty()) {
            out.put("output_files", outputFiles);
        }
        if (finishReason != null && !finishReason.isBlank()) {
            out.put("finish_reason", finishReason);
        }
        if (usage != null && !usage.isEmpty()) {
            out.put("usage", usage);
        }
        return out;
    }

    /**
     * {@code {<port>: {result: payload, outputFiles?}}} — openai-models / local-llm envelope.
     * {@code outputFiles} is copied from {@code result.output_files} when that list is non-empty
     * so stream and unary share the same port schema. The file list is published ONCE: the
     * {@code result} copy drops {@code output_files} — otherwise the same file renders twice
     * (inside {@code result} and on the file field) in the run-log panels.
     */
    public static Map<String, Object> ports(String port, Map<String, Object> result) {
        String key = port == null || port.isBlank() ? "llm_output" : port;
        Map<String, Object> inner = new LinkedHashMap<>();
        Object files = result == null ? null : result.get("output_files");
        if (files instanceof List<?> list && !list.isEmpty()) {
            Map<String, Object> stripped = new LinkedHashMap<>(result);
            stripped.remove("output_files");
            inner.put("result", stripped);
            inner.put("outputFiles", list);
        } else {
            inner.put("result", result == null ? Map.of() : result);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(key, inner);
        return out;
    }
}
