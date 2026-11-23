package com.studioquack.nodelibrary.nodes.testemulator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs python / javascript / typescript scripts against the generated mock helper.
 *
 * <p>Command: {@code python3 script.py} / {@code node script.js} / {@code tsx script.ts}
 * with a wall-clock timeout. Captures stdout/stderr, reads {@code output.json} (port →
 * value) and {@code invocation_log.json}, then deep-compares against {@code expected_output}.
 */
public final class ScriptTestRunner {

    public ScriptTestRunner() {
        // Stateless runner — constructed per invoke so it can grow request-scoped state later.
    }

    public TestReport run(EmulatorInput input, EmulatorConfig config) {
        TestReport report = new TestReport()
                .language(input.language())
                .mode("script");
        long t0 = System.nanoTime();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("sq-emu-script-");
            MockHelperGenerator.writeScriptWorkspace(
                    dir, input.language(), input.inputData(),
                    input.mockFunctionResponses(), input.configuration());
            String filename = switch (input.language()) {
                case "python" -> "script.py";
                case "javascript" -> "script.js";
                case "typescript" -> "script.ts";
                default -> throw new IllegalArgumentException("unsupported script language: " + input.language());
            };
            String wrapped = switch (input.language()) {
                case "python" -> MockHelperGenerator.wrapPython(input.script());
                case "javascript" -> MockHelperGenerator.wrapJavaScript(input.script());
                default -> MockHelperGenerator.wrapTypeScript(input.script());
            };
            Files.writeString(dir.resolve(filename), wrapped, StandardCharsets.UTF_8);

            if (config.verbose()) {
                System.err.println("script-test language=" + input.language()
                        + " timeout=" + input.resolveTimeout(config) + "s dir=" + dir);
            }

            List<String> argv = commandFor(input.language(), filename);
            ProcessRunner.ProcessResult proc = ProcessRunner.run(argv, dir, Map.of(), input.resolveTimeout(config));
            report.stdout(proc.stdout()).stderr(proc.stderr());

            Object captured = readJsonIfPresent(dir.resolve("output.json"));
            List<Object> log = readLog(dir.resolve("invocation_log.json"));
            report.output(captured).invocationLog(log);

            if (proc.error() != null) {
                report.scriptSuccess(false).error(proc.error());
            } else if (!proc.success() && captured == null) {
                report.scriptSuccess(false).error("script exited " + proc.exitCode()
                        + (proc.stderr().isBlank() ? "" : ": " + tail(proc.stderr(), 500)));
            } else {
                // returnResult() exits 0 after writing output.json — that is success even
                // when the process used sys.exit(0) via the helper.
                report.scriptSuccess(captured != null || proc.success());
                if (captured == null && proc.success()) {
                    report.error("Script did not produce output.json (returnResult was not called)");
                    report.scriptSuccess(false);
                }
            }
            report.compareExpected(input.expectedOutput());
        } catch (Exception e) {
            report.scriptSuccess(false).error(e.getClass().getSimpleName() + ": " + e.getMessage());
            report.compareExpected(input.expectedOutput());
        } finally {
            report.executionTimeMs((System.nanoTime() - t0) / 1_000_000L);
            deleteQuietly(dir);
        }
        return report.finish();
    }

    private static List<String> commandFor(String language, String filename) {
        return switch (language) {
            case "python" -> List.of("python3", filename);
            case "javascript" -> List.of("node", filename);
            case "typescript" -> List.of("tsx", filename);
            default -> throw new IllegalArgumentException(language);
        };
    }

    private static Object readJsonIfPresent(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return Map.of("msg", Map.of("error", "Output JSON is empty"));
            }
            return Json.parse(raw);
        } catch (Exception e) {
            return Map.of("msg", Map.of("error", "Output JSON is invalid"));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> readLog(Path path) {
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        try {
            Object parsed = Json.parse(Files.readString(path, StandardCharsets.UTF_8));
            if (parsed instanceof List<?> list) {
                return new ArrayList<>((List<Object>) list);
            }
        } catch (Exception ignored) {
            // invocation_log is diagnostic; a parse miss must not fail the test
        }
        return new ArrayList<>();
    }

    private static String tail(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(s.length() - n);
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // temp-dir cleanup is best-effort
                        }
                    });
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
