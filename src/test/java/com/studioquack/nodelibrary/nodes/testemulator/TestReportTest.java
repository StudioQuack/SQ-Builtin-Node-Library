package com.studioquack.nodelibrary.nodes.testemulator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Report aggregation: fluent setters, compare gate, finish rule, toMap shape. */
class TestReportTest {

    // ---------- fluent setters / null handling ----------

    @Test
    void settersAreFluentAndNullSafe() {
        TestReport r = new TestReport()
                .language(null)
                .mode(null)
                .stdout(null)
                .stderr(null)
                .error(null);
        Map<String, Object> m = r.toMap(100);
        assertEquals("", m.get("language"));
        assertEquals("", m.get("mode"));
        assertEquals("", m.get("stdout"));
        assertEquals("", m.get("stderr"));
        assertEquals("", m.get("error"));
    }

    @Test
    void defaultsBeforeFinish() {
        TestReport r = new TestReport();
        assertFalse(r.testPassed(), "test_passed stays false until finish()");
        assertEquals("", r.error());
        Map<String, Object> m = r.toMap(100);
        assertEquals(false, m.get("test_passed"));
        assertEquals(false, m.get("script_success"));
        assertEquals(true, m.get("output_matches"), "outputMatches defaults to true");
        assertEquals(List.of(), m.get("differences"));
        assertEquals(List.of(), m.get("invocation_log"));
        assertEquals(0L, m.get("execution_time_ms"));
    }

    // ---------- compareExpected ----------

    @Test
    void compareExpectedNullMeansNoAssertion() {
        TestReport r = new TestReport().output(Map.of("a", 1)).compareExpected(null);
        Map<String, Object> m = r.toMap(100);
        assertEquals(true, m.get("output_matches"));
        assertEquals(List.of(), m.get("differences"));
    }

    @Test
    void compareExpectedMatch() {
        TestReport r = new TestReport().output(Map.of("sum", 8L)).compareExpected(Map.of("sum", 8.0d));
        assertEquals(true, r.toMap(100).get("output_matches"));
    }

    @Test
    void compareExpectedMismatchRecordsDifferences() {
        TestReport r = new TestReport().output(Map.of("sum", 7L)).compareExpected(Map.of("sum", 8L));
        Map<String, Object> m = r.toMap(100);
        assertEquals(false, m.get("output_matches"));
        assertEquals(List.of("Value mismatch at root.sum: expected 8, got 7"), m.get("differences"));
    }

    // ---------- finish rule: scriptSuccess && outputMatches && error blank ----------

    @Test
    void finishPassesOnlyWhenSuccessMatchAndNoError() {
        assertTrue(new TestReport().scriptSuccess(true).output("x").compareExpected(null).finish().testPassed());
    }

    @Test
    void finishFailsWhenScriptFailed() {
        assertFalse(new TestReport().scriptSuccess(false).compareExpected(null).finish().testPassed());
    }

    @Test
    void finishFailsWhenOutputMismatched() {
        assertFalse(new TestReport().scriptSuccess(true).output(1L).compareExpected(2L).finish().testPassed());
    }

    @Test
    void finishFailsWhenErrorPresent() {
        assertFalse(new TestReport().scriptSuccess(true).compareExpected(null)
                .error("boom").finish().testPassed());
    }

    @Test
    void finishTreatsBlankErrorAsNoError() {
        assertTrue(new TestReport().scriptSuccess(true).compareExpected(null)
                .error("   ").finish().testPassed());
    }

    // ---------- toMap shape ----------

    @Test
    void toMapContainsAllScriptModeFields() {
        Map<String, Object> m = new TestReport()
                .language("python").mode("script").scriptSuccess(true)
                .output(Map.of("a", 1)).stdout("out").stderr("err")
                .executionTimeMs(123).finish()
                .toMap(1000);
        assertEquals(true, m.get("test_passed"));
        assertEquals(Map.of("a", 1), m.get("output"));
        assertEquals("out", m.get("stdout"));
        assertEquals("err", m.get("stderr"));
        assertEquals(123L, m.get("execution_time_ms"));
        assertEquals("python", m.get("language"));
        assertEquals("script", m.get("mode"));
        assertFalse(m.containsKey("build_log"), "script mode omits build fields");
        assertFalse(m.containsKey("image_built"), "script mode omits build fields");
    }

    @Test
    void buildModeAddsBuildFields() {
        Map<String, Object> m = new TestReport()
                .mode("build").buildLog("compiled").imageBuilt(true)
                .toMap(1000);
        assertEquals("compiled", m.get("build_log"));
        assertEquals(true, m.get("image_built"));
    }

    @Test
    void buildModeNullImageBuiltBecomesFalse() {
        Map<String, Object> m = new TestReport().mode("build").toMap(1000);
        assertEquals(false, m.get("image_built"));
        assertEquals("", m.get("build_log"));
    }

    @Test
    void stdoutAndStderrAreTruncatedToMaxLogChars() {
        Map<String, Object> m = new TestReport()
                .stdout("x".repeat(50)).stderr("y".repeat(50))
                .toMap(10);
        String stdout = (String) m.get("stdout");
        String stderr = (String) m.get("stderr");
        assertTrue(stdout.startsWith("x".repeat(10)) && stdout.contains("max_log_chars=10"), stdout);
        assertTrue(stderr.startsWith("y".repeat(10)) && stderr.contains("max_log_chars=10"), stderr);
    }

    @Test
    void invocationLogIsDefensivelyCopied() {
        List<Object> log = new ArrayList<>();
        log.add("first");
        TestReport r = new TestReport().invocationLog(log);
        log.add("second");
        assertEquals(List.of("first"), r.toMap(100).get("invocation_log"));
    }

    @Test
    void invocationLogNullBecomesEmpty() {
        assertEquals(List.of(), new TestReport().invocationLog(null).toMap(100).get("invocation_log"));
    }

    @Test
    void errorAccessorReflectsSetter() {
        assertEquals("e1", new TestReport().error("e1").error());
        assertEquals("", new TestReport().error(null).error());
    }

    @Test
    void finishReturnsSameInstanceForChaining() {
        TestReport r = new TestReport();
        assertSame(r, r.finish());
    }

    @Test
    void nullOutputSerialisesAsNull() {
        Map<String, Object> m = new TestReport().toMap(100);
        assertTrue(m.containsKey("output"));
        assertNull(m.get("output"));
    }
}
