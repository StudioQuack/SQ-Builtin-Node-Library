package com.studioquack.nodelibrary.nodes.testemulator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable builder for the OUTPUT-port {@code output} test report object, including
 * {@code language}, {@code mode}, {@code build_log}, and {@code image_built} for
 * Java build-mode tests.
 */
public final class TestReport {

    private boolean testPassed;
    private boolean scriptSuccess;
    private Object output;
    private boolean outputMatches = true;
    private List<String> differences = new ArrayList<>();
    private List<Object> invocationLog = new ArrayList<>();
    private String stdout = "";
    private String stderr = "";
    private String error = "";
    private long executionTimeMs;
    private String language = "";
    private String mode = "";
    private String buildLog;
    private Boolean imageBuilt;

    public TestReport language(String language) {
        this.language = language == null ? "" : language;
        return this;
    }

    public TestReport mode(String mode) {
        this.mode = mode == null ? "" : mode;
        return this;
    }

    public TestReport scriptSuccess(boolean scriptSuccess) {
        this.scriptSuccess = scriptSuccess;
        return this;
    }

    public TestReport output(Object output) {
        this.output = output;
        return this;
    }

    public TestReport stdout(String stdout) {
        this.stdout = stdout == null ? "" : stdout;
        return this;
    }

    public TestReport stderr(String stderr) {
        this.stderr = stderr == null ? "" : stderr;
        return this;
    }

    public TestReport error(String error) {
        this.error = error == null ? "" : error;
        return this;
    }

    public TestReport executionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
        return this;
    }

    public TestReport invocationLog(List<Object> invocationLog) {
        this.invocationLog = invocationLog == null ? new ArrayList<>() : new ArrayList<>(invocationLog);
        return this;
    }

    public TestReport buildLog(String buildLog) {
        this.buildLog = buildLog;
        return this;
    }

    public TestReport imageBuilt(boolean imageBuilt) {
        this.imageBuilt = imageBuilt;
        return this;
    }

    /**
     * Apply {@link OutputComparer} against {@code expected}. A null expected means
     * "no assertion" and is treated as a match (legacy behaviour).
     */
    public TestReport compareExpected(Object expected) {
        if (expected == null) {
            this.outputMatches = true;
            this.differences = new ArrayList<>();
            return this;
        }
        OutputComparer.CompareResult r = OutputComparer.compare(this.output, expected);
        this.outputMatches = r.matches();
        this.differences = new ArrayList<>(r.differences());
        return this;
    }

    /** {@code test_passed} = script/build succeeded AND expected output matched. */
    public TestReport finish() {
        this.testPassed = this.scriptSuccess && this.outputMatches && (this.error == null || this.error.isBlank());
        return this;
    }

    public boolean testPassed() {
        return testPassed;
    }

    public String error() {
        return error;
    }

    /**
     * JSON object for OUTPUT port {@code output}. {@code build_log} / {@code image_built}
     * are omitted in script mode so agents do not confuse them with script results.
     */
    public Map<String, Object> toMap(int maxLogChars) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("test_passed", testPassed);
        m.put("script_success", scriptSuccess);
        m.put("output", output);
        m.put("output_matches", outputMatches);
        m.put("differences", differences);
        m.put("invocation_log", invocationLog);
        m.put("stdout", EmulatorUtil.truncate(stdout, maxLogChars));
        m.put("stderr", EmulatorUtil.truncate(stderr, maxLogChars));
        m.put("error", error == null ? "" : error);
        m.put("execution_time_ms", executionTimeMs);
        m.put("language", language);
        m.put("mode", mode);
        if ("build".equals(mode)) {
            m.put("build_log", EmulatorUtil.truncate(buildLog == null ? "" : buildLog, maxLogChars));
            m.put("image_built", Boolean.TRUE.equals(imageBuilt));
        }
        return m;
    }
}
