package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crypto node: known-answer hash/HMAC, AES-GCM and ChaCha20 round-trips,
 * wrong-key failure, RSA sign, random/uuid shape, and secret redaction.
 */
class CryptoNodeTest {

    private final CryptoNode node = new CryptoNode();

    @Test
    void hashSha256KnownAnswer() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "hash", "algorithm", "sha256"),
                CodecTestSupport.input("data", "abc")));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                CodecTestSupport.data(result).get("result"));
    }

    @Test
    void hashBase64Encoding() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "hash", "algorithm", "sha256", "encoding", "base64"),
                CodecTestSupport.input("data", "abc")));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=",
                CodecTestSupport.data(result).get("result"));
    }

    @Test
    void hmacSha256KnownAnswer() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "hmac", "algorithm", "sha256", "key", "key"),
                CodecTestSupport.input("data", "The quick brown fox jumps over the lazy dog")));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                CodecTestSupport.data(result).get("result"));
    }

    @Test
    void hmacRequiresKey() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "hmac"),
                CodecTestSupport.input("data", "x")));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void aesGcmRoundTrip() {
        Map<String, Object> cfg = CodecTestSupport.cfg("operation", "encrypt",
                "cipher", "aes-256-gcm", "key", "correct horse battery staple",
                "encoding", "base64");
        Map<String, Object> enc = CodecTestSupport.run(node, CodecTestSupport.request(cfg,
                CodecTestSupport.input("data", "attack at dawn")));
        assertTrue(CodecTestSupport.isOk(enc), () -> CodecTestSupport.error(enc));
        String ciphertext = String.valueOf(CodecTestSupport.data(enc).get("result"));

        Map<String, Object> decCfg = CodecTestSupport.cfg("operation", "decrypt",
                "cipher", "aes-256-gcm", "key", "correct horse battery staple",
                "encoding", "base64");
        Map<String, Object> dec = CodecTestSupport.run(node, CodecTestSupport.request(decCfg,
                CodecTestSupport.input("data", ciphertext)));
        assertTrue(CodecTestSupport.isOk(dec), () -> CodecTestSupport.error(dec));
        assertEquals("attack at dawn", CodecTestSupport.data(dec).get("result"));
    }

    @Test
    void aes128GcmRoundTrip() {
        Map<String, Object> enc = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "encrypt", "cipher", "aes-128-gcm",
                        "passphrase", "pw"),
                CodecTestSupport.input("data", "payload")));
        assertTrue(CodecTestSupport.isOk(enc), () -> CodecTestSupport.error(enc));
        Map<String, Object> dec = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "decrypt", "cipher", "aes-128-gcm",
                        "passphrase", "pw"),
                CodecTestSupport.input("data",
                        String.valueOf(CodecTestSupport.data(enc).get("result")))));
        assertEquals("payload", CodecTestSupport.data(dec).get("result"));
    }

    @Test
    void chacha20RoundTrip() {
        Map<String, Object> enc = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "encrypt", "cipher", "chacha20-poly1305",
                        "key", "k"),
                CodecTestSupport.input("data", "secret text")));
        assertTrue(CodecTestSupport.isOk(enc), () -> CodecTestSupport.error(enc));
        Map<String, Object> dec = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "decrypt", "cipher", "chacha20-poly1305",
                        "key", "k"),
                CodecTestSupport.input("data",
                        String.valueOf(CodecTestSupport.data(enc).get("result")))));
        assertEquals("secret text", CodecTestSupport.data(dec).get("result"));
    }

    @Test
    void wrongKeyFails() {
        Map<String, Object> enc = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "encrypt", "key", "right"),
                CodecTestSupport.input("data", "sensitive")));
        String ciphertext = String.valueOf(CodecTestSupport.data(enc).get("result"));
        Map<String, Object> dec = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "decrypt", "key", "wrong"),
                CodecTestSupport.input("data", ciphertext)));
        assertNotNull(CodecTestSupport.error(dec));
    }

    @Test
    void decryptGarbageFails() {
        Map<String, Object> dec = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "decrypt", "key", "k"),
                CodecTestSupport.input("data", "!!!not-hex!!!")));
        assertNotNull(CodecTestSupport.error(dec));
    }

    @Test
    void signProducesVerifiableRsaSignature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "sign", "key", pem, "encoding", "base64"),
                CodecTestSupport.input("data", "message")));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        byte[] signature = Base64.getDecoder().decode(
                String.valueOf(CodecTestSupport.data(result).get("result")));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(kp.getPublic());
        verifier.update("message".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(verifier.verify(signature));
    }

    @Test
    void randomHonoursLengthAndEncoding() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "random", "encoding", "hex"),
                CodecTestSupport.input("length", "16")));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals(32, String.valueOf(CodecTestSupport.data(result).get("result")).length());
    }

    @Test
    void randomRejectsAbsurdLength() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "random"),
                CodecTestSupport.input("length", "99999999")));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void uuidShape() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "uuid"), CodecTestSupport.input()));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        String uuid = String.valueOf(CodecTestSupport.data(result).get("result"));
        assertTrue(uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void twoRandomCallsDiffer() {
        Map<String, Object> a = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "random"), CodecTestSupport.input()));
        Map<String, Object> b = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "random"), CodecTestSupport.input()));
        assertNotEquals(CodecTestSupport.data(a).get("result"), CodecTestSupport.data(b).get("result"));
    }

    @Test
    void unknownOperationFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "warp"), CodecTestSupport.input()));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void keyNeverAppearsInError() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "decrypt", "key", "supersecretkeyvalue"),
                CodecTestSupport.input("data", "zzzz")));
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(!error.contains("supersecretkeyvalue"));
    }
}
