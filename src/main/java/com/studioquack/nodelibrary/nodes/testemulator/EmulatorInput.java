package com.studioquack.nodelibrary.nodes.testemulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed INPUT-port {@code input} object for one emulator invocation.
 *
 * <p>Accepts the body either as {@code inputVariables.input} (stateful contract) or
 * as {@code executionContext.currentNode.inputList[]} whose {@code targetPortValue}
 * is {@code input}. A list-wrapped value (helper {@code getInputValuesByPortValue}
 * shape) is unwrapped to the first element.
 */
public final class EmulatorInput {

    public static final Set<String> SCRIPT_LANGUAGES = Set.of("python", "javascript", "typescript");
    public static final Set<String> ALL_LANGUAGES = Set.of("python", "javascript", "typescript", "java");

    private final String language;
    private final String mode;
    private final String script;
    private final List<SourceFile> files;
    private final Map<String, Object> inputData;
    private final Map<String, Object> configuration;
    private final Map<String, Object> mockFunctionResponses;
    private final Object expectedOutput;
    private final Integer timeoutSeconds;

    public EmulatorInput(String language, String mode, String script, List<SourceFile> files,
                         Map<String, Object> inputData, Map<String, Object> configuration,
                         Map<String, Object> mockFunctionResponses, Object expectedOutput,
                         Integer timeoutSeconds) {
        this.language = language;
        this.mode = mode;
        this.script = script;
        this.files = files;
        this.inputData = inputData;
        this.configuration = configuration;
        this.mockFunctionResponses = mockFunctionResponses;
        this.expectedOutput = expectedOutput;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Pull the {@code input} port payload out of a stateful invoke JSON body.
     *
     * @throws IllegalArgumentException when language/mode/script/files are illegal
     */
    public static EmulatorInput parse(Map<String, Object> invokeRequest, EmulatorConfig config) {
        Object raw = EmulatorUtil.asMap(invokeRequest.get("inputVariables")).get("input");
        if (raw == null) {
            raw = findInputListValue(invokeRequest);
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            raw = list.get(0);
        }
        Map<String, Object> in = EmulatorUtil.asMap(raw);
        if (in.isEmpty()) {
            throw new IllegalArgumentException(
                    "INPUT port 'input' is required: object with language, mode, and script or files");
        }

        String language = EmulatorUtil.lower(in.get("language"));
        if (language.isEmpty()) {
            throw new IllegalArgumentException(
                    "input.language is required: python | javascript | typescript | java");
        }
        if (!ALL_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException(
                    "input.language '" + language + "' is not supported; accepted: python, javascript, typescript, java");
        }
        if (!config.allowedLanguages().contains(language)) {
            throw new IllegalArgumentException(
                    "input.language '" + language + "' is not in allowed_languages " + config.allowedLanguages());
        }

        String mode = EmulatorUtil.lower(in.get("mode"));
        if (mode.isEmpty()) {
            // Infer from language when the caller omitted mode (still documented as required).
            mode = "java".equals(language) ? "build" : "script";
        }
        if (!"script".equals(mode) && !"build".equals(mode)) {
            throw new IllegalArgumentException("input.mode is required: script | build");
        }
        if (SCRIPT_LANGUAGES.contains(language) && !"script".equals(mode)) {
            throw new IllegalArgumentException(
                    "language " + language + " must use mode=script (build is Java-only)");
        }
        if ("java".equals(language) && !"build".equals(mode)) {
            throw new IllegalArgumentException("language java must use mode=build (script is python/js/ts only)");
        }

        String script = in.get("script") == null ? "" : EmulatorUtil.str(in.get("script"));
        List<SourceFile> files = parseFiles(in.get("files"));
        if ("script".equals(mode) && script.isBlank()) {
            throw new IllegalArgumentException("input.script is required in mode=script (the source to execute)");
        }
        if ("build".equals(mode) && files.isEmpty()) {
            throw new IllegalArgumentException(
                    "input.files is required in mode=build: array of {path, content} including a Containerfile or Dockerfile");
        }

        // Object-typed fields arrive as JSON strings when the invoke request was
        // seeded from node-definition defaults (the definition format stores object
        // values serialised, same convention as expected_output). Parse them here or
        // asMap() would silently degrade a documented default like
        // '{"in":{"a":3,"b":5}}' to an empty map and the script under test could not
        // resolve its inputs.
        Map<String, Object> inputData = EmulatorUtil.mutableMap(parseJsonString(in.get("input_data")));
        Map<String, Object> configuration = EmulatorUtil.mutableMap(parseJsonString(in.get("configuration")));
        Map<String, Object> mocks = EmulatorUtil.mutableMap(parseJsonString(in.get("mock_function_responses")));
        Object expected = normalizeExpected(in.get("expected_output"));
        Integer timeout = in.get("timeout_seconds") == null ? null
                : EmulatorUtil.toInt(in.get("timeout_seconds"), config.defaultTimeoutSeconds());

        return new EmulatorInput(language, mode, script, files, inputData, configuration, mocks,
                expected, timeout);
    }

    /**
     * Object-typed fields arrive as strings when the value came from schema-default seeding
     * (the node-definition format reserves raw objects): a blank string means "no value",
     * a non-blank string is JSON-parsed so a declared default still applies.
     */
    private static Object parseJsonString(Object raw) {
        if (raw instanceof String s) {
            if (s.isBlank()) {
                return null;
            }
            try {
                return Json.parse(s.trim());
            } catch (Exception e) {
                return raw;
            }
        }
        return raw;
    }

    /**
     * Object-typed fields arrive as strings when the value came from schema-default seeding
     * (the node-definition format reserves raw objects): a blank string means "no assertion",
     * a non-blank string is JSON-parsed so a declared default still asserts.
     */
    private static Object normalizeExpected(Object raw) {
        return parseJsonString(raw);
    }

    private static Object findInputListValue(Map<String, Object> invokeRequest) {
        Object list = EmulatorUtil.dig(invokeRequest, "executionContext", "currentNode", "inputList");
        for (Object item : EmulatorUtil.asList(list)) {
            Map<String, Object> row = EmulatorUtil.asMap(item);
            if ("input".equals(EmulatorUtil.str(row.get("targetPortValue")))) {
                return row.get("value");
            }
        }
        return null;
    }

    private static List<SourceFile> parseFiles(Object raw) {
        List<SourceFile> out = new ArrayList<>();
        for (Object item : EmulatorUtil.asList(raw)) {
            Map<String, Object> row = EmulatorUtil.asMap(item);
            String path = EmulatorUtil.str(row.get("path")).trim();
            if (path.isEmpty()) {
                throw new IllegalArgumentException("files[].path is required (relative path inside the workspace)");
            }
            // Refuse absolute paths and `..` so a test cannot write outside the temp workspace.
            if (path.startsWith("/") || path.contains("..") || path.contains("\\")) {
                throw new IllegalArgumentException(
                        "files[].path must be a relative POSIX path without '..': got '" + path + "'");
            }
            Object content = row.get("content");
            out.add(new SourceFile(path, content == null ? "" : EmulatorUtil.str(content)));
        }
        return out;
    }

    public int resolveTimeout(EmulatorConfig config) {
        return timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : config.defaultTimeoutSeconds();
    }

    public String language() {
        return language;
    }

    public String mode() {
        return mode;
    }

    public String script() {
        return script;
    }

    public List<SourceFile> files() {
        return files;
    }

    public Map<String, Object> inputData() {
        return inputData;
    }

    public Map<String, Object> configuration() {
        return configuration;
    }

    public Map<String, Object> mockFunctionResponses() {
        return mockFunctionResponses;
    }

    public Object expectedOutput() {
        return expectedOutput;
    }

    /** One workspace file for build-mode ({@code path} relative, {@code content} as text). */
    public record SourceFile(String path, String content) {
    }
}
