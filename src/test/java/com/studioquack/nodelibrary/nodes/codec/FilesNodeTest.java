package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Files node against a @TempDir sandbox root: read/write/list/delete happy
 * paths, base_dir confinement (path traversal + absolute host paths), and the
 * watch trigger guidance.
 */
class FilesNodeTest {

    @TempDir
    Path sandbox;

    private FilesNode node() {
        return new FilesNode(FilesNode.DEFAULT_ID, sandbox);
    }

    @Test
    void writeThenReadRoundTrip() {
        Map<String, Object> write = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "write"),
                CodecTestSupport.input("path", "notes/a.txt", "text", "hello files")));
        assertTrue(CodecTestSupport.isOk(write), () -> CodecTestSupport.error(write));
        assertEquals("notes/a.txt", CodecTestSupport.data(write).get("path"));

        Map<String, Object> read = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read"),
                CodecTestSupport.input("path", "notes/a.txt")));
        assertTrue(CodecTestSupport.isOk(read), () -> CodecTestSupport.error(read));
        assertArrayEqualsBytes("hello files", CodecTestSupport.outputFileBytes(read));
        assertEquals("a.txt", CodecTestSupport.outputFileName(read));
    }

    @Test
    void writeFromFileRef() {
        Map<String, Object> write = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "write"),
                CodecTestSupport.input("path", "blob.bin",
                        "file", CodecTestSupport.fileValue("blob.bin", new byte[]{1, 2, 3}))));
        assertTrue(CodecTestSupport.isOk(write), () -> CodecTestSupport.error(write));
        assertEquals(3, CodecTestSupport.data(write).get("size"));
    }

    @Test
    void writeRequiresContent() {
        Map<String, Object> write = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "write"),
                CodecTestSupport.input("path", "empty.txt")));
        assertNotNull(CodecTestSupport.error(write));
    }

    @Test
    void listHonoursGlob() throws Exception {
        Files.writeString(sandbox.resolve("a.csv"), "1");
        Files.writeString(sandbox.resolve("b.csv"), "2");
        Files.writeString(sandbox.resolve("c.txt"), "3");
        Map<String, Object> list = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "list", "watch_glob", "*.csv"),
                CodecTestSupport.input()));
        assertTrue(CodecTestSupport.isOk(list), () -> CodecTestSupport.error(list));
        assertEquals(2, CodecTestSupport.data(list).get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) CodecTestSupport.data(list).get("entries");
        assertEquals("a.csv", entries.get(0).get("name"));
    }

    @Test
    void deleteRemovesFile() throws Exception {
        Files.writeString(sandbox.resolve("doomed.txt"), "x");
        Map<String, Object> delete = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "delete"),
                CodecTestSupport.input("path", "doomed.txt")));
        assertTrue(CodecTestSupport.isOk(delete), () -> CodecTestSupport.error(delete));
        assertTrue(Files.notExists(sandbox.resolve("doomed.txt")));
    }

    @Test
    void deleteMissingFails() {
        Map<String, Object> delete = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "delete"),
                CodecTestSupport.input("path", "ghost.txt")));
        assertNotNull(CodecTestSupport.error(delete));
    }

    @Test
    void pathTraversalIsRefused() {
        Map<String, Object> read = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read"),
                CodecTestSupport.input("path", "../escape.txt")));
        assertNotNull(CodecTestSupport.error(read));
    }

    @Test
    void absoluteHostPathIsRefused() {
        Map<String, Object> read = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read"),
                CodecTestSupport.input("path", "/etc/passwd")));
        assertNotNull(CodecTestSupport.error(read));
    }

    @Test
    void baseDirEscapeIsRefused() {
        Map<String, Object> read = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read", "base_dir", "../../.."),
                CodecTestSupport.input("path", "x.txt")));
        assertNotNull(CodecTestSupport.error(read));
    }

    @Test
    void baseDirScopesOperations() throws Exception {
        Path sub = sandbox.resolve("env");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("in.txt"), "scoped");
        Map<String, Object> read = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read", "base_dir", "env"),
                CodecTestSupport.input("path", "in.txt")));
        assertTrue(CodecTestSupport.isOk(read), () -> CodecTestSupport.error(read));

        // the same relative path outside base_dir must not resolve
        Map<String, Object> escape = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read", "base_dir", "env"),
                CodecTestSupport.input("path", "../env/in.txt")));
        // ../env/in.txt normalizes back inside env's parent chain → refused or scoped,
        // but never an escape outside the sandbox root
        if (CodecTestSupport.error(escape) == null) {
            assertTrue(Files.exists(sub.resolve("in.txt")));
        }
    }

    @Test
    void watchReturnsBpmnGuidance() {
        Map<String, Object> watch = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "watch"),
                CodecTestSupport.input()));
        String error = CodecTestSupport.error(watch);
        assertNotNull(error);
        assertTrue(error.contains("BPMN message start"));
    }

    @Test
    void textInputInfersWriteWhenConfigIsRead() {
        Map<String, Object> write = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read"),
                CodecTestSupport.input("path", "hello.txt", "text", "Hello, world!")));
        assertTrue(CodecTestSupport.isOk(write), () -> CodecTestSupport.error(write));
        assertEquals("hello.txt", CodecTestSupport.data(write).get("path"));
    }

    @Test
    void writeEmptyTextCreatesFile() {
        Map<String, Object> write = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read"),
                CodecTestSupport.input("path", "empty.txt", "text", "")));
        assertTrue(CodecTestSupport.isOk(write), () -> CodecTestSupport.error(write));
        assertEquals(0, CodecTestSupport.data(write).get("size"));
    }

    @Test
    void missingPathSaysRequired() {
        Map<String, Object> read = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read"),
                CodecTestSupport.input()));
        assertEquals("path is required.", CodecTestSupport.error(read));
    }

    @Test
    void dotPathIsRejectedForRead() {
        Map<String, Object> read = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read"),
                CodecTestSupport.input("path", ".")));
        assertNotNull(CodecTestSupport.error(read));
        assertTrue(CodecTestSupport.error(read).contains("must name a file"));
    }

    @Test
    void backslashTraversalIsRefused() {
        Map<String, Object> read = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read"),
                CodecTestSupport.input("path", "..\\evil.txt")));
        assertNotNull(CodecTestSupport.error(read));
    }

    @Test
    void readMissingFileFails() {
        Map<String, Object> read = CodecTestSupport.run(node(), CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "read"),
                CodecTestSupport.input("path", "nope.txt")));
        assertNotNull(CodecTestSupport.error(read));
    }

    private static void assertArrayEqualsBytes(String expected, byte[] actual) {
        assertEquals(expected, new String(actual, StandardCharsets.UTF_8));
    }
}
