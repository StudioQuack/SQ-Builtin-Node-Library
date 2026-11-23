package com.studioquack.nodelibrary.nodes.testemulator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes the mock helper module and sidecar JSON files into a temp test directory.
 *
 * <p>User data (input_data, mocks, configuration) is never interpolated into source —
 * it is written as JSON files the helper reads at runtime, so a payload cannot inject
 * Python/JS syntax.
 */
public final class MockHelperGenerator {

    private MockHelperGenerator() {
        // Utility — never instantiated.
    }

    /**
     * Lay down helper + sidecars for a script-mode run.
     *
     * @param dir              temp workspace (also the process cwd)
     * @param language         python | javascript | typescript
     * @param inputData        test INPUT-port values keyed by port value
     * @param mocks            FUNCTION port value → mocked execute() response
     * @param configuration    config shortCode → value (test overrides)
     */
    public static void writeScriptWorkspace(Path dir, String language,
                                            Map<String, Object> inputData,
                                            Map<String, Object> mocks,
                                            Map<String, Object> configuration) throws IOException {
        Files.createDirectories(dir);
        writeJson(dir.resolve("input.json"), inputData == null ? Map.of() : inputData);
        writeJson(dir.resolve("mock_responses.json"), mocks == null ? Map.of() : mocks);
        writeJson(dir.resolve("configuration.json"), configuration == null ? Map.of() : configuration);
        if ("python".equals(language)) {
            Files.writeString(dir.resolve("studioquack_helper.py"),
                    PythonMockHelperSource.source(), StandardCharsets.UTF_8);
        } else {
            // javascript and typescript both consume the CommonJS helper.
            Files.writeString(dir.resolve("studioquack_helper.js"),
                    JsMockHelperSource.source(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Wrap the user script so {@code studioquack} is in scope without the author
     * having to import it (legacy emulator behaviour).
     */
    public static String wrapPython(String script) {
        return """
import sys, os
sys.path.insert(0, os.getcwd())
from studioquack_helper import studioquack

""" + (script == null ? "" : script);
    }

    public static String wrapJavaScript(String script) {
        return """
const { studioquack } = require('./studioquack_helper.js');

""" + (script == null ? "" : script);
    }

    /** tsx can require() the .js helper; same wrap as JS plus a TS-friendly import fallback. */
    public static String wrapTypeScript(String script) {
        return """
// eslint-disable-next-line @typescript-eslint/no-var-requires
const { studioquack } = require('./studioquack_helper.js');

""" + (script == null ? "" : script);
    }

    private static void writeJson(Path path, Map<String, Object> obj) throws IOException {
        Files.writeString(path, Json.write(obj), StandardCharsets.UTF_8);
    }
}
