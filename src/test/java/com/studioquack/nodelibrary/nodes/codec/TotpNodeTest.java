package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOTP against the RFC 6238 Appendix B vectors (T=59s) plus config validation.
 */
class TotpNodeTest {

    /** ASCII "12345678901234567890" in Base32 — the RFC 6238 SHA-1 test secret. */
    private static final String SECRET_SHA1 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    /** ASCII "12345678901234567890123456789012" — the RFC 6238 SHA-256 test secret. */
    private static final String SECRET_SHA256 =
            "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA====";

    private final TotpNode node = new TotpNode();

    @Test
    void rfc6238Sha1Vector() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "generate", "secret", SECRET_SHA1,
                        "algorithm", "sha1", "digits", "8", "period", "30"),
                CodecTestSupport.input("timestamp", "59")));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals("94287082", CodecTestSupport.data(result).get("code"));
        assertEquals(8, CodecTestSupport.data(result).get("digits"));
    }

    @Test
    void rfc6238Sha1SixDigits() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("secret", SECRET_SHA1, "digits", "6"),
                CodecTestSupport.input("timestamp", "59")));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals("287082", CodecTestSupport.data(result).get("code"));
    }

    @Test
    void rfc6238Sha256Vector() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("secret", SECRET_SHA256, "algorithm", "sha256",
                        "digits", "8", "period", "30"),
                CodecTestSupport.input("timestamp", "59")));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        assertEquals("46119246", CodecTestSupport.data(result).get("code"));
    }

    @Test
    void generateUsesClockWhenNoTimestamp() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("secret", SECRET_SHA1), CodecTestSupport.input()));
        assertTrue(CodecTestSupport.isOk(result), () -> CodecTestSupport.error(result));
        String code = String.valueOf(CodecTestSupport.data(result).get("code"));
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void missingSecretFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "generate"),
                CodecTestSupport.input("timestamp", "59")));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void invalidBase32SecretFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("secret", "not!base32!"),
                CodecTestSupport.input("timestamp", "59")));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void badDigitsFail() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("secret", SECRET_SHA1, "digits", "7"),
                CodecTestSupport.input("timestamp", "59")));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void unknownOperationFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "verify", "secret", SECRET_SHA1),
                CodecTestSupport.input()));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void secretNeverAppearsInError() {
        String secret = SECRET_SHA1;
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("secret", secret, "algorithm", "sha512"),
                CodecTestSupport.input("timestamp", "59")));
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(!error.contains(secret));
    }
}
