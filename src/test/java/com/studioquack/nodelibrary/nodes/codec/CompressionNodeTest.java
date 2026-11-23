package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compression: zip/gzip/tar/tgz round-trips, zip-slip rejection, bad input.
 */
class CompressionNodeTest {

    private final CompressionNode node = new CompressionNode();

    private Map<String, Object> compress(String format, String name, byte[] bytes) {
        return CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "compress", "format", format),
                CodecTestSupport.input("file", CodecTestSupport.fileValue(name, bytes))));
    }

    private Map<String, Object> decompress(String format, String name, byte[] bytes) {
        return CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "decompress", "format", format),
                CodecTestSupport.input("file", CodecTestSupport.fileValue(name, bytes))));
    }

    @Test
    void zipRoundTrip() {
        byte[] source = "hello zip".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> compressed = compress("zip", "a.txt", source);
        assertTrue(CodecTestSupport.isOk(compressed), () -> CodecTestSupport.error(compressed));
        assertEquals("a.txt.zip", CodecTestSupport.outputFileName(compressed));

        Map<String, Object> back = decompress("zip", "a.txt.zip",
                CodecTestSupport.outputFileBytes(compressed));
        assertTrue(CodecTestSupport.isOk(back), () -> CodecTestSupport.error(back));
        assertArrayEquals(source, CodecTestSupport.outputFileBytes(back));
        assertEquals("a.txt", CodecTestSupport.data(back).get("extracted"));
    }

    @Test
    void gzipRoundTrip() {
        byte[] source = "hello gzip".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> compressed = compress("gzip", "b.txt", source);
        assertTrue(CodecTestSupport.isOk(compressed), () -> CodecTestSupport.error(compressed));
        Map<String, Object> back = decompress("gzip", "b.txt.gz",
                CodecTestSupport.outputFileBytes(compressed));
        assertArrayEquals(source, CodecTestSupport.outputFileBytes(back));
    }

    @Test
    void tarRoundTrip() {
        byte[] source = "hello tar".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> compressed = compress("tar", "c.txt", source);
        assertTrue(CodecTestSupport.isOk(compressed), () -> CodecTestSupport.error(compressed));
        Map<String, Object> back = decompress("tar", "c.txt.tar",
                CodecTestSupport.outputFileBytes(compressed));
        assertTrue(CodecTestSupport.isOk(back), () -> CodecTestSupport.error(back));
        assertArrayEquals(source, CodecTestSupport.outputFileBytes(back));
        assertEquals("c.txt", CodecTestSupport.data(back).get("extracted"));
    }

    @Test
    void tgzRoundTrip() {
        byte[] source = "hello tgz".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> compressed = compress("tgz", "d.txt", source);
        assertTrue(CodecTestSupport.isOk(compressed), () -> CodecTestSupport.error(compressed));
        Map<String, Object> back = decompress("tgz", "d.txt.tar.gz",
                CodecTestSupport.outputFileBytes(compressed));
        assertArrayEquals(source, CodecTestSupport.outputFileBytes(back));
    }

    @Test
    void fileNameOverrideWins() {
        Map<String, Object> compressed = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "compress", "format", "zip",
                        "file_name", "bundle.zip"),
                CodecTestSupport.input("file",
                        CodecTestSupport.fileValue("a.txt", "x".getBytes()))));
        assertEquals("bundle.zip", CodecTestSupport.outputFileName(compressed));
    }

    @Test
    void zipSlipIsRejected() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write("pwned".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Map<String, Object> result = decompress("zip", "evil.zip", bos.toByteArray());
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(error.contains("unsafe entry name"));
    }

    @Test
    void absoluteEntryNameIsRejected() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("/etc/passwd"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertNotNull(CodecTestSupport.error(decompress("zip", "abs.zip", bos.toByteArray())));
    }

    @Test
    void unknownFormatFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "compress", "format", "rar"),
                CodecTestSupport.input("file",
                        CodecTestSupport.fileValue("a.txt", "x".getBytes()))));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void missingSandboxPathReturnsCleanError() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "compress", "format", "zip"),
                CodecTestSupport.input("file", "/sandbox/files/does-not-exist-compression-test.txt")));
        String err = CodecTestSupport.error(result);
        assertNotNull(err);
        assertTrue(err.contains("sandbox file not found:")
                || err.contains("must be under /sandbox/files"), err);
    }

    @Test
    void missingFileFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "compress"),
                CodecTestSupport.input()));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void emptyFileNameOnInputFailsBeforeFileRead() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "compress", "format", "zip"),
                CodecTestSupport.input("file_name", "",
                        "file", CodecTestSupport.fileValue("a.txt", "x".getBytes()))));
        assertEquals("file_name must be non-empty when provided on INPUT", CodecTestSupport.error(result));
    }

    @Test
    void fileNameWithPathSeparatorFailsBeforeFileRead() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "compress", "format", "zip"),
                CodecTestSupport.input("file_name", "../evil.zip",
                        "file", CodecTestSupport.fileValue("a.txt", "x".getBytes()))));
        String err = CodecTestSupport.error(result);
        assertNotNull(err);
        assertTrue(err.contains("file_name"));
    }

    @Test
    void corruptArchiveFailsCleanly() {
        Map<String, Object> result = decompress("gzip", "bad.gz", "not gzip at all".getBytes());
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void multiEntryZipListsAllEntries() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("one.txt"));
            zos.write("1".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("two.txt"));
            zos.write("22".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Map<String, Object> result = decompress("zip", "multi.zip", bos.toByteArray());
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals(2, CodecTestSupport.data(result).get("entry_count"));
    }
}
