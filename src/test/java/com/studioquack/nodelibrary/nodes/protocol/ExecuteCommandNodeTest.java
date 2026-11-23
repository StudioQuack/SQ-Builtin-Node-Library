package com.studioquack.nodelibrary.nodes.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studioquack.StudioQuackError;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Execute-command handler: allowlist, no-shell argv, timeout, output caps,
 * workspace confinement. Uses {@code echo}/{@code true}/{@code sleep}/{@code false}
 * present on macOS and Linux.
 */
class ExecuteCommandNodeTest {

    @TempDir
    Path workspace;

    private ExecuteCommandNode node() {
        return new ExecuteCommandNode(ExecuteCommandNode.DEFAULT_ID, workspace);
    }

    @Test
    void disabledByDefault() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node().handle(request(
                        params("allowlist", "[\"echo\"]"),
                        Map.of("argv", List.of("echo", "hi"))), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("disabled"), e.getMessage());
    }

    @Test
    void emptyAllowlistIsRefused() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node().handle(request(
                        params("enabled", "true", "allowlist", "[]"),
                        Map.of("argv", List.of("echo", "hi"))), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("allowlist"), e.getMessage());
    }

    @Test
    void missingAllowlistIsRefused() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node().handle(request(
                        params("enabled", "true"),
                        Map.of("argv", List.of("echo"))), null));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void executableNotOnAllowlistIsRefused() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node().handle(request(
                        params("enabled", "true", "allowlist", "[\"echo\"]"),
                        Map.of("argv", List.of("sleep", "1"))), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("allowlist"), e.getMessage());
    }

    @Test
    void shDashCIsRefusedWhenShIsNotAllowlisted() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node().handle(request(
                        params("enabled", "true", "allowlist", "[\"echo\"]"),
                        Map.of("argv", List.of("sh", "-c", "echo pwned"))), null));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void echoRunsWithoutAShell() {
        Map<String, Object> out = output(node().handle(request(
                params("enabled", "true", "allowlist", "[\"echo\"]"),
                Map.of("argv", List.of("echo", "hello; rm -rf /", "$(whoami)"))), null));
        assertEquals("ok", out.get("status"));
        assertEquals(0, ((Number) out.get("exit_code")).intValue());
        assertFalse((Boolean) out.get("timed_out"));
        String stdout = String.valueOf(out.get("stdout"));
        assertTrue(stdout.contains("hello; rm -rf /"), stdout);
        assertTrue(stdout.contains("$(whoami)"), stdout);
        assertFalse(stdout.contains("pwned") && !stdout.contains("hello"), stdout);
    }

    @Test
    void trueIsZeroExit() {
        Map<String, Object> out = output(node().handle(request(
                params("enabled", "true", "allowlist", "[\"true\"]"),
                Map.of("argv", List.of("true"))), null));
        assertEquals("ok", out.get("status"));
        assertEquals(0, ((Number) out.get("exit_code")).intValue());
        assertFalse((Boolean) out.get("timed_out"));
    }

    @Test
    void falseIsNonZeroButNotAnError() {
        Map<String, Object> out = output(node().handle(request(
                params("enabled", "true", "allowlist", "[\"false\"]"),
                Map.of("argv", List.of("false"))), null));
        assertEquals("ok", out.get("status"));
        assertEquals(1, ((Number) out.get("exit_code")).intValue());
        assertFalse((Boolean) out.get("timed_out"));
    }

    @Test
    void timeoutKillsSleep() {
        Map<String, Object> out = output(node().handle(request(
                params("enabled", "true", "allowlist", "[\"sleep\"]", "timeout_ms", "200"),
                Map.of("argv", List.of("sleep", "5"))), null));
        assertEquals("ok", out.get("status"));
        assertEquals(Boolean.TRUE, out.get("timed_out"));
    }

    @Test
    void stdoutIsCapped() {
        String payload = "x".repeat(ExecuteCommandNode.MAX_OUTPUT_BYTES + 2048);
        Map<String, Object> out = output(node().handle(request(
                params("enabled", "true", "allowlist", "[\"echo\"]"),
                Map.of("argv", List.of("echo", "-n", payload))), null));
        assertEquals("ok", out.get("status"));
        String stdout = String.valueOf(out.get("stdout"));
        assertTrue(stdout.length() <= ExecuteCommandNode.MAX_OUTPUT_BYTES, "len=" + stdout.length());
        assertFalse(stdout.isEmpty());
    }

    @Test
    void workingDirTraversalIsRefused() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node().handle(request(
                        params("enabled", "true", "allowlist", "[\"echo\"]"),
                        Map.of("argv", List.of("echo", "hi"), "working_dir", "../")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("workspace") || e.getMessage().contains("working_dir"),
                e.getMessage());
    }

    @Test
    void absoluteWorkingDirOutsideWorkspaceIsRefused() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node().handle(request(
                        params("enabled", "true", "allowlist", "[\"echo\"]"),
                        Map.of("argv", List.of("echo", "hi"), "working_dir", "/etc")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void relativeWorkingDirInsideWorkspaceIsUsed() throws Exception {
        Path nested = workspace.resolve("sub");
        Files.createDirectories(nested);
        Map<String, Object> out = output(node().handle(request(
                params("enabled", "true", "allowlist", "[\"echo\"]"),
                Map.of("argv", List.of("echo", "ok"), "working_dir", "sub")), null));
        assertEquals("ok", out.get("status"));
        assertEquals(0, ((Number) out.get("exit_code")).intValue());
    }

    @Test
    void resolveExecutableRequiresAllowlistMatch() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> ExecuteCommandNode.resolveExecutable("sh", Set.of("echo")));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void resolveExecutableFindsEchoOnPath() {
        Path echo = ExecuteCommandNode.resolveExecutable("echo", Set.of("echo"));
        assertTrue(echo.isAbsolute());
        assertEquals("echo", echo.getFileName().toString());
        assertTrue(Files.isRegularFile(echo));
    }

    @Test
    void parseAllowlistJsonAndCsv() {
        assertEquals(Set.of("echo", "true"), ExecuteCommandNode.parseAllowlist("[\"echo\",\"true\"]"));
        assertEquals(Set.of("echo", "sleep"), ExecuteCommandNode.parseAllowlist("echo, sleep"));
        assertTrue(ExecuteCommandNode.parseAllowlist("[]").isEmpty());
        assertTrue(ExecuteCommandNode.parseAllowlist("").isEmpty());
        assertTrue(ExecuteCommandNode.parseAllowlist(null).isEmpty());
    }

    @Test
    void unknownOperationIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node().handle(request(
                        params("enabled", "true", "allowlist", "[\"echo\"]", "operation", "shell"),
                        Map.of("argv", List.of("echo"))), null));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void templateIdUsesConstructorArgument() {
        assertEquals("custom-id", new ExecuteCommandNode("custom-id", workspace).templateId());
        assertEquals(ExecuteCommandNode.DEFAULT_ID, new ExecuteCommandNode().templateId());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> ports) {
        return (Map<String, Object>) ports.get("output");
    }

    private static Map<String, Object> request(List<Object> params, Map<String, Object> input) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        req.put("inputVariables", Map.of("input", input));
        return req;
    }

    private static List<Object> params(String... kv) {
        List<Object> out = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", kv[i]);
            row.put("value", kv[i + 1]);
            out.add(row);
        }
        return out;
    }
}
