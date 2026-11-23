package com.studioquack.nodelibrary.nodes.testemulator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client of {@code POST /v1/helper/test/build-run}.
 *
 * <p>The SQS daemon stages the build context, builds the image, runs a hardened job
 * sandbox, and returns the report fields. This class only forwards the request over
 * the helper channel and compares {@code expected_output} — it never talks to the
 * host engine.
 *
 * <p>Requires {@code SQ_HELPER_SOCKET} and the invoke body's {@code helperToken}.
 * {@link HelperClient} authenticates with the Authorization header; the token is never
 * placed in the JSON body or logs.
 */
public final class BuildTestRunner {

    /** Daemon-side cap for the image build itself (independent of the invoke timeout). */
    private static final int BUILD_WALL_CLOCK_SECONDS = 600;

    private static final String MISSING_CHANNEL =
            "build-mode requires the daemon helper channel (SQ_HELPER_SOCKET + helperToken)";

    private static final String EMPTY_OUTPUT =
            "app produced no output (HTTP body empty and no stdout markers)";

    private static final String FORBIDDEN_HINT =
            " daemon sqs.testbuild.allowed-images must include this instance's image"
                    + " (default sq-local/sq-builtin-node-library)";

    public BuildTestRunner() {
        // Stateless per-invoke runner.
    }

    public TestReport run(EmulatorInput input, EmulatorConfig config, Map<String, Object> invokeRequest) {
        TestReport report = new TestReport().language("java").mode("build").imageBuilt(false);
        long t0 = System.nanoTime();
        try {
            String socket = System.getenv("SQ_HELPER_SOCKET");
            String helperToken = invokeRequest == null
                    ? "" : EmulatorUtil.str(invokeRequest.get("helperToken"));
            if (socket == null || socket.isBlank() || helperToken.isBlank()) {
                report.scriptSuccess(false).error(MISSING_CHANNEL);
                return finish(report, input, t0);
            }

            int timeoutSeconds = input.resolveTimeout(config);
            if (config.verbose()) {
                System.err.println("build-test delegated timeout=" + timeoutSeconds + "s");
            }

            Map<String, Object> body = buildRequestBody(input, invokeRequest, timeoutSeconds);
            HelperClient helper = new HelperClient(EmulatorUtil.str(invokeRequest.get("helperToken")));
            Map<String, Object> resp = helper.buildRun(body, timeoutSeconds);
            if (isChannelError(resp)) {
                applyChannelError(report, resp);
                return finish(report, input, t0);
            }

            applySuccessBody(report, resp);
            report.compareExpected(input.expectedOutput());
        } catch (Exception e) {
            report.scriptSuccess(false).error(e.getClass().getSimpleName() + ": " + e.getMessage());
            report.compareExpected(input.expectedOutput());
        } finally {
            report.executionTimeMs((System.nanoTime() - t0) / 1_000_000L);
        }
        return report.finish();
    }

    /** Early-return path: stamp duration, compare expected, freeze the report. */
    private static TestReport finish(TestReport report, EmulatorInput input, long t0) {
        report.executionTimeMs((System.nanoTime() - t0) / 1_000_000L);
        report.compareExpected(input.expectedOutput());
        return report.finish();
    }

    /**
     * Stable-key-order body for {@code /v1/helper/test/build-run}.
     * {@code helperToken} stays out of this map — the daemon authenticates the header.
     */
    private static Map<String, Object> buildRequestBody(EmulatorInput input,
                                                        Map<String, Object> invokeRequest,
                                                        int timeoutSeconds) {
        List<Object> files = new ArrayList<>();
        List<EmulatorInput.SourceFile> sourceFiles = input.files();
        if (sourceFiles != null) {
            for (EmulatorInput.SourceFile f : sourceFiles) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("path", f.path());
                row.put("content", f.content());
                files.add(row);
            }
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("requestId", field(invokeRequest, "requestId"));
        request.put("workflowExecutionId", field(invokeRequest, "workflowExecutionId"));
        request.put("currentNodeId", field(invokeRequest, "currentNodeId"));
        request.put("envId", field(invokeRequest, "envId"));
        request.put("templateId", field(invokeRequest, "templateId"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("files", files);
        body.put("mock_function_responses",
                orEmptyMap(input.mockFunctionResponses()));
        body.put("input_data", orEmptyMap(input.inputData()));
        body.put("configuration", orEmptyMap(input.configuration()));
        body.put("execution_context", invokeRequest == null ? null : invokeRequest.get("executionContext"));
        body.put("request", request);
        body.put("timeout_seconds", timeoutSeconds);
        body.put("build_wall_clock_seconds", BUILD_WALL_CLOCK_SECONDS);
        return body;
    }

    /** Transport / HTTP 401/403/400/503 — no successful build-run payload. */
    private static boolean isChannelError(Map<String, Object> resp) {
        if (resp == null || resp.isEmpty()) {
            return true;
        }
        String err = EmulatorUtil.str(resp.get("error")).trim();
        if ("unauthorized".equals(err)
                || "test_build_forbidden".equals(err)
                || "test_build_unavailable".equals(err)) {
            return true;
        }
        if (!resp.containsKey("image_built")) {
            return true;
        }
        return onlyErrorField(resp);
    }

    /** True when every key other than {@code error} is null or a blank string. */
    private static boolean onlyErrorField(Map<String, Object> resp) {
        String err = EmulatorUtil.str(resp.get("error")).trim();
        if (err.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> e : resp.entrySet()) {
            if ("error".equals(e.getKey())) {
                continue;
            }
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof String s && s.isBlank()) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static void applyChannelError(TestReport report, Map<String, Object> resp) {
        String err = resp == null ? "" : EmulatorUtil.str(resp.get("error")).trim();
        if (err.isEmpty()) {
            err = "helper test/build-run failed";
        }
        if (isForbidden(err)) {
            err = err + FORBIDDEN_HINT;
        }
        report.scriptSuccess(false).error(err).imageBuilt(false);
    }

    private static boolean isForbidden(String err) {
        String t = err.trim();
        return "test_build_forbidden".equals(t)
                || t.contains("test_build_forbidden")
                || t.startsWith("403");
    }

    /** HTTP 200-shaped body: map daemon fields onto {@link TestReport}. */
    private static void applySuccessBody(TestReport report, Map<String, Object> resp) {
        report.imageBuilt(EmulatorUtil.isTruthy(resp.get("image_built")));
        report.buildLog(EmulatorUtil.str(resp.get("build_log")));
        report.stdout(EmulatorUtil.str(resp.get("stdout")));
        report.stderr(mergeHelperLogs(EmulatorUtil.str(resp.get("stderr")), resp.get("helper_logs")));

        Object log = resp.get("invocation_log");
        if (log instanceof List<?>) {
            report.invocationLog(EmulatorUtil.asList(log));
        } else {
            report.invocationLog(List.of());
        }

        Object output = resp.get("output");
        report.output(output);

        Map<String, Object> failure = failureMap(resp.get("failure"));
        String error;
        if (failure != null) {
            error = formatFailure(failure);
        } else {
            error = EmulatorUtil.str(resp.get("error"));
        }
        report.error(error);
        // failure always yields a non-blank error, so scriptSuccess stays false.
        boolean ok = (error == null || error.isBlank()) && output != null;
        report.scriptSuccess(ok);
        if (output == null && (error == null || error.isBlank())) {
            report.scriptSuccess(false).error(EMPTY_OUTPUT);
        }
    }

    /**
     * Append compact helper-channel diagnostics onto stderr so they survive without
     * a new {@link TestReport} field.
     */
    private static String mergeHelperLogs(String stderr, Object helperLogs) {
        List<Object> logs = EmulatorUtil.asList(helperLogs);
        if (logs.isEmpty()) {
            return stderr == null ? "" : stderr;
        }
        String extra = Json.writeValue(logs);
        if (stderr == null || stderr.isBlank()) {
            return extra;
        }
        return stderr + (stderr.endsWith("\n") ? "" : "\n") + extra;
    }

    /** Non-null Map means a test-level failure object from the daemon. */
    private static Map<String, Object> failureMap(Object raw) {
        return raw instanceof Map<?, ?> ? EmulatorUtil.asMap(raw) : null;
    }

    /** {@code errorCode: message}, skipping whichever part is blank. */
    private static String formatFailure(Map<String, Object> failure) {
        String code = EmulatorUtil.str(failure.get("errorCode")).trim();
        String message = EmulatorUtil.str(failure.get("message")).trim();
        if (!code.isEmpty() && !message.isEmpty()) {
            return code + ": " + message;
        }
        if (!code.isEmpty()) {
            return code;
        }
        if (!message.isEmpty()) {
            return message;
        }
        return "test failed";
    }

    private static String field(Map<String, Object> invokeRequest, String key) {
        return invokeRequest == null ? "" : EmulatorUtil.str(invokeRequest.get(key));
    }

    private static Map<String, Object> orEmptyMap(Map<String, Object> m) {
        return m == null ? Map.of() : m;
    }
}
