package com.studioquack.nodelibrary.nodes.files;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic coverage for {@link FilesSupport}: file-ref resolution, ContentBlock
 * shaping, error/redaction paths, multipart framing, MIME guessing, and RSA/JWT
 * helpers. Actual transfers ({@code exchange} and friends) perform HTTP and are not
 * exercised against the network; only the invalid-URL error shaping (which fails
 * before any socket is opened) is asserted.
 */
class FilesSupportTest {

    // --- constants -----------------------------------------------------------------------

    @Test
    void constantsMatchDocumentedBounds() {
        assertEquals(8 * 1024 * 1024, FilesSupport.MAX_FILE_BYTES);
        assertEquals(500, FilesSupport.ERR_BODY_CHARS);
        assertEquals("/sandbox/files", FilesSupport.SANDBOX_FILES);
        assertEquals("StudioQuack-NodeLibrary/1.0", FilesSupport.USER_AGENT);
    }

    // --- records -----------------------------------------------------------------------------

    @Test
    void fileBytesAccessors() {
        FilesSupport.FileBytes fb = new FilesSupport.FileBytes(new byte[]{1, 2}, "a.txt", "text/plain");
        assertArrayEquals(new byte[]{1, 2}, fb.bytes());
        assertEquals("a.txt", fb.name());
        assertEquals("text/plain", fb.mimeType());
    }

    @Test
    void httpResultOkTextAndHeaders() {
        FilesSupport.HttpResult ok = new FilesSupport.HttpResult(
                200, "{\"a\":1}".getBytes(StandardCharsets.UTF_8),
                Map.of("content-type", List.of("application/json")), null);
        assertTrue(ok.ok());
        assertEquals("{\"a\":1}", ok.text());
        assertEquals(1, ok.jsonObject().get("a"));
        // header lookup is case-insensitive, null-safe
        assertEquals("application/json", ok.header("Content-Type"));
        assertNull(ok.header("x-missing"));
        assertNull(ok.header(null));

        assertFalse(new FilesSupport.HttpResult(200, null, Map.of(), "boom").ok());
        assertFalse(new FilesSupport.HttpResult(500, null, Map.of(), null).ok());
        assertEquals("", new FilesSupport.HttpResult(204, null, Map.of(), null).text());
        assertNull(new FilesSupport.HttpResult(200, "plain".getBytes(StandardCharsets.UTF_8),
                Map.of(), null).jsonObject());
    }

    // --- fileBlock --------------------------------------------------------------------------------

    @Test
    void fileBlockShapesDownloadContentBlock() {
        Map<String, Object> block = FilesSupport.fileBlock("a.txt", "text/plain", "hi".getBytes(StandardCharsets.UTF_8));
        assertEquals("file", block.get("type"));
        assertEquals("a.txt", block.get("name"));
        assertEquals("text/plain", block.get("mime_type"));
        assertEquals("base64", block.get("source"));
        assertEquals("aGk=", block.get("data"));
        assertEquals(2, block.get("size"));
    }

    @Test
    void fileBlockDefaultsAndNullBytes() {
        Map<String, Object> block = FilesSupport.fileBlock(null, " ", null);
        assertEquals("download.bin", block.get("name"));
        assertEquals("application/octet-stream", block.get("mime_type"));
        assertEquals("", block.get("data"));
        assertEquals(0, block.get("size"));
    }

    @Test
    void fileBlockRejectsOversize() {
        FilesSupport.Fail e = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.fileBlock("big.bin", null, new byte[FilesSupport.MAX_FILE_BYTES + 1]));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("above the " + FilesSupport.MAX_FILE_BYTES + " byte node limit"));
    }

    // --- ok / failPorts / fail ------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void okMergesBodyUnderOutputPort() {
        Map<String, Object> output = (Map<String, Object>) FilesSupport.ok(Map.of("a", 1)).get("output");
        assertEquals("ok", output.get("status"));
        assertEquals(1, output.get("a"));

        Map<String, Object> nullBody = (Map<String, Object>) FilesSupport.ok(null).get("output");
        assertEquals(Map.of("status", "ok"), nullBody);
    }

    @Test
    void failPortsRedactsSecrets() {
        StudioQuackError redacted = assertThrows(StudioQuackError.class,
                () -> FilesSupport.failPorts("bad token abcd", List.of("abcd")));
        assertEquals("REQUEST_FAILED", redacted.errorCode());
        assertEquals("bad token ***", redacted.getMessage());
        StudioQuackError plain = assertThrows(StudioQuackError.class,
                () -> FilesSupport.failPorts("plain failure", null));
        assertEquals("REQUEST_FAILED", plain.errorCode());
        assertEquals("plain failure", plain.getMessage());
    }

    @Test
    void failMessageFallback() {
        FilesSupport.Fail e = FilesSupport.fail("x");
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertEquals("x", e.getMessage());
        assertEquals("request failed", FilesSupport.fail(null).getMessage());
        assertEquals("REQUEST_FAILED", FilesSupport.fail(null).errorCode());
    }

    // --- requireOk / requireOkNoJson -----------------------------------------------------------------------

    @Test
    void requireOkThrowsOnTransportError() {
        FilesSupport.HttpResult resp = new FilesSupport.HttpResult(-1, new byte[0], Map.of(), "boom");
        FilesSupport.Fail e = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.requireOk(resp, "Box", List.of()));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertEquals("Box API Error: boom", e.getMessage());
    }

    @Test
    void requireOkCapsErrorBodyExcerpt() {
        String big = "x".repeat(FilesSupport.ERR_BODY_CHARS + 100);
        FilesSupport.HttpResult resp = new FilesSupport.HttpResult(
                500, big.getBytes(StandardCharsets.UTF_8), Map.of(), null);
        FilesSupport.Fail e = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.requireOk(resp, "Box", null));
        assertEquals("HTTP_500", e.errorCode());
        assertEquals(500, e.data().get("http_status"));
        String prefix = "Box API Error: HTTP 500: ";
        assertTrue(e.getMessage().startsWith(prefix));
        assertEquals(prefix.length() + FilesSupport.ERR_BODY_CHARS, e.getMessage().length());
    }

    @Test
    void requireOkRedactsSecretsFromErrorBody() {
        FilesSupport.HttpResult resp = new FilesSupport.HttpResult(
                401, "denied for s3cr3t".getBytes(StandardCharsets.UTF_8), Map.of(), null);
        FilesSupport.Fail e = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.requireOk(resp, "Dropbox", List.of("s3cr3t")));
        assertEquals("AUTH_FAILED", e.errorCode());
        assertEquals(401, e.data().get("http_status"));
        assertFalse(e.getMessage().contains("s3cr3t"));
        assertTrue(e.getMessage().contains("***"));
    }

    @Test
    void requireOkParsesJsonAndToleratesPlainText() {
        FilesSupport.HttpResult json = new FilesSupport.HttpResult(
                200, "{\"a\":1}".getBytes(StandardCharsets.UTF_8), Map.of(), null);
        assertEquals(1, FilesSupport.requireOk(json, "Box", null).get("a"));

        FilesSupport.HttpResult plain = new FilesSupport.HttpResult(
                200, "plain".getBytes(StandardCharsets.UTF_8), Map.of(), null);
        assertTrue(FilesSupport.requireOk(plain, "Box", null).isEmpty());
    }

    @Test
    void requireOkNoJsonOnlyThrowsOnFailure() {
        FilesSupport.HttpResult ok = new FilesSupport.HttpResult(
                200, "not json".getBytes(StandardCharsets.UTF_8), Map.of(), null);
        FilesSupport.requireOkNoJson(ok, "Box", null); // must not throw

        FilesSupport.HttpResult bad = new FilesSupport.HttpResult(404, new byte[0], Map.of(), null);
        FilesSupport.Fail e404 = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.requireOkNoJson(bad, "Box", null));
        assertEquals("HTTP_404", e404.errorCode());
        assertEquals(404, e404.data().get("http_status"));
    }

    // --- JWT / keys ---------------------------------------------------------------------------------------------

    @Test
    void signJwtRs256ProducesVerifiableToken() throws Exception {
        KeyPair kp = rsaKeyPair();
        String jwt = FilesSupport.signJwtRs256(
                Map.of("alg", "RS256", "typ", "JWT", "kid", "key-1"),
                Map.of("sub", "user@example.com"), kp.getPrivate());
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);

        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        assertTrue(header.contains("\"alg\":\"RS256\""));
        assertTrue(header.contains("\"kid\":\"key-1\""));

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(kp.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])));
    }

    @Test
    void loadPkcs8ValidatesInput() {
        FilesSupport.Fail missing = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.loadPkcs8(null, null));
        assertEquals("INVALID_INPUT", missing.errorCode());
        assertEquals("private_key is missing", missing.getMessage());
        assertThrows(FilesSupport.Fail.class, () -> FilesSupport.loadPkcs8("  ", null));

        FilesSupport.Fail badB64 = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.loadPkcs8("!!!not-base64!!!", null));
        assertEquals("INVALID_INPUT", badB64.errorCode());
        assertEquals("invalid private_key PEM", badB64.getMessage());

        // valid base64 but not a PKCS8 key
        String notAKey = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        FilesSupport.Fail badKey = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.loadPkcs8(notAKey, null));
        assertTrue(badKey.getMessage().startsWith("invalid PKCS8 private_key"));
    }

    @Test
    void loadPkcs8EncryptedRequiresPassphrase() {
        String pem = "-----BEGIN ENCRYPTED PRIVATE KEY-----\nAAAA\n-----END ENCRYPTED PRIVATE KEY-----";
        FilesSupport.Fail e = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.loadPkcs8(pem, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("encrypted private_key requires passphrase", e.getMessage());
    }

    @Test
    void loadPkcs8RoundTripsUnencryptedKey() throws Exception {
        KeyPair kp = rsaKeyPair();
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(kp.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----";
        PrivateKey key = FilesSupport.loadPkcs8(pem, null);
        assertEquals("RSA", key.getAlgorithm());

        // escaped \n sequences (as stored in designer JSON) are accepted too
        PrivateKey escaped = FilesSupport.loadPkcs8(pem.replace("\n", "\\n"), null);
        assertEquals("RSA", escaped.getAlgorithm());
    }

    // --- multipart / boundary ---------------------------------------------------------------------------------------

    @Test
    void multipartFramesPartsWithCrlf() {
        byte[] out = FilesSupport.multipart("b", List.of(
                "part1".getBytes(StandardCharsets.US_ASCII),
                "part2".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("--b\r\npart1\r\n--b\r\npart2\r\n--b--\r\n",
                new String(out, StandardCharsets.US_ASCII));
    }

    @Test
    void multipartEmptyPartsIsJustTheTerminator() {
        assertEquals("--b--\r\n", new String(FilesSupport.multipart("b", List.of()), StandardCharsets.US_ASCII));
    }

    @Test
    void newBoundaryIsPrefixedHexAndUnique() {
        String a = FilesSupport.newBoundary();
        assertTrue(a.matches("sqb[0-9a-f]{32}"));
        assertNotEquals(a, FilesSupport.newBoundary());
    }

    // --- asciiJsonHeader -------------------------------------------------------------------------------------------------

    @Test
    void asciiJsonHeaderEscapesNonAscii() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", "Müller");
        assertEquals("{\"name\":\"M\\u00fcller\"}", FilesSupport.asciiJsonHeader(json));
        assertEquals("{\"a\":\"b\"}", FilesSupport.asciiJsonHeader(Map.of("a", "b")));
    }

    // --- firstNonBlank / secret collection -----------------------------------------------------------------------------------

    @Test
    void firstNonBlankTrimsAndSkipsBlanks() {
        assertEquals("x", FilesSupport.firstNonBlank(null, "  ", " x ", "y"));
        assertNull(FilesSupport.firstNonBlank(null, " ", ""));
        assertNull(FilesSupport.firstNonBlank((Object[]) null));
    }

    @Test
    void collectSecretSkipsBlank() {
        List<String> secrets = new ArrayList<>();
        assertEquals("tok", FilesSupport.collectSecret(" tok ", secrets));
        assertNull(FilesSupport.collectSecret("  ", secrets));
        assertEquals(List.of("tok"), secrets);
    }

    @Test
    void collectMapSecretsSerializesMaps() {
        List<String> secrets = new ArrayList<>();
        FilesSupport.collectMapSecrets("plain", secrets);
        FilesSupport.collectMapSecrets("  ", secrets);
        FilesSupport.collectMapSecrets(Map.of("a", 1), secrets);
        assertEquals(List.of("plain", "{\"a\":1}"), secrets);
    }

    // --- readCapped / guessMime -------------------------------------------------------------------------------------------------

    @Test
    void readCappedBoundsBytes() throws Exception {
        assertEquals(0, FilesSupport.readCapped(null, 10).length);
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8),
                FilesSupport.readCapped(new ByteArrayInputStream(
                        "hello".getBytes(StandardCharsets.UTF_8)), 10));
        assertArrayEquals("hel".getBytes(StandardCharsets.UTF_8),
                FilesSupport.readCapped(new ByteArrayInputStream(
                        "hello".getBytes(StandardCharsets.UTF_8)), 3));
    }

    @Test
    void guessMimeByExtension() {
        assertEquals("application/pdf", FilesSupport.guessMime("a.PDF"));
        assertEquals("image/png", FilesSupport.guessMime("a.png"));
        assertEquals("image/jpeg", FilesSupport.guessMime("a.jpg"));
        assertEquals("image/jpeg", FilesSupport.guessMime("a.jpeg"));
        assertEquals("text/plain", FilesSupport.guessMime("a.txt"));
        assertEquals("text/plain", FilesSupport.guessMime("a.csv"));
        assertEquals("application/json", FilesSupport.guessMime("a.json"));
        assertEquals("application/octet-stream", FilesSupport.guessMime("a.bin"));
        assertEquals("application/octet-stream", FilesSupport.guessMime(null));
    }

    // --- readFileValue -------------------------------------------------------------------------------------------------------------

    @Test
    void readFileValueRejectsNullAndUnresolvedHandles() {
        FilesSupport.Fail required = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.readFileValue(null, "file"));
        assertEquals("INVALID_INPUT", required.errorCode());

        FilesSupport.Fail handle = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.readFileValue("sq-file:abc", "file"));
        assertEquals("REQUEST_FAILED", handle.errorCode());
        assertTrue(handle.getMessage().contains("unresolved sq-file handle"));

        FilesSupport.Fail type = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.readFileValue(123, "file"));
        assertEquals("INVALID_INPUT", type.errorCode());
        assertTrue(type.getMessage().contains("must be a file-typed value"));
    }

    @Test
    void readFileValueEnforcesSandboxRoot() {
        FilesSupport.Fail outside = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.readFileValue("/etc/passwd", "file"));
        assertEquals("INVALID_INPUT", outside.errorCode());
        assertTrue(outside.getMessage().contains("must be under /sandbox/files"));

        String missing = "/sandbox/files/absent-" + UUID.randomUUID();
        FilesSupport.Fail notFound = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.readFileValue(missing, "file"));
        String msg = notFound.getMessage();
        assertTrue(msg.contains("sandbox file not found: " + missing)
                || msg.contains("must be under /sandbox/files"), msg);
    }

    @Test
    void readFileValueDecodesInlineBase64() {
        FilesSupport.FileBytes fb = FilesSupport.readFileValue(
                Map.of("name", "a.txt", "data", "aGk="), "file");
        assertArrayEquals("hi".getBytes(StandardCharsets.UTF_8), fb.bytes());
        assertEquals("a.txt", fb.name());
        assertEquals("application/octet-stream", fb.mimeType());
    }

    @Test
    void readFileValueUnwrapsFirstListItemLikeBareMap() {
        Map<String, Object> block = Map.of("name", "a.txt", "data", "aGk=");
        FilesSupport.FileBytes fromMap = FilesSupport.readFileValue(block, "file");
        FilesSupport.FileBytes fromList = FilesSupport.readFileValue(List.of(block), "file");
        assertArrayEquals(fromMap.bytes(), fromList.bytes());
        assertEquals(fromMap.name(), fromList.name());
        assertEquals(fromMap.mimeType(), fromList.mimeType());
    }

    @Test
    void readFileValueAppliesDefaultsAndAlternateKeys() {
        FilesSupport.FileBytes fb = FilesSupport.readFileValue(
                Map.of("mimeType", "text/plain", "contentBase64", "aGk="), "file");
        assertEquals("file", fb.name());
        assertEquals("text/plain", fb.mimeType());
    }

    @Test
    void readFileValueUnwrapsFileRef() {
        FilesSupport.FileBytes fb = FilesSupport.readFileValue(
                Map.of("$fileRef", Map.of("name", "b.txt", "data", "aGk=")), "file");
        assertEquals("b.txt", fb.name());
        assertArrayEquals("hi".getBytes(StandardCharsets.UTF_8), fb.bytes());
    }

    @Test
    void readFileValueRejectsBadBase64AndEmptyBlocks() {
        FilesSupport.Fail badB64 = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.readFileValue(Map.of("data", "!!!"), "file"));
        assertEquals("INVALID_INPUT", badB64.errorCode());
        assertTrue(badB64.getMessage().contains("not valid base64"));

        FilesSupport.Fail empty = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.readFileValue(Map.of(), "file"));
        assertEquals("INVALID_INPUT", empty.errorCode());
        assertTrue(empty.getMessage().contains("no data / contentBase64 / sandbox path"));

        // an sq-file handle hiding in the data field must not be decoded inline
        FilesSupport.Fail handle = assertThrows(FilesSupport.Fail.class,
                () -> FilesSupport.readFileValue(Map.of("data", "sq-file:xyz"), "file"));
        assertTrue(handle.getMessage().contains("no data / contentBase64 / sandbox path"));
    }

    // --- exchange error shaping (no socket is opened: URI parsing fails first) ------------------------------------------------------

    @Test
    void exchangeWithUnparseableUrlReturnsInvalidUrlResult() {
        FilesSupport.HttpResult r = FilesSupport.exchange("GET", "not a url", null, null, null, 30);
        assertEquals(-1, r.status());
        assertEquals("invalid URL", r.error());
        assertFalse(r.ok());
        assertEquals("", r.text());
    }

    // --- helpers -----------------------------------------------------------------------------------------------------------------------

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }
}
