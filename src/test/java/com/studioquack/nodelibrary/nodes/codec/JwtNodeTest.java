package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT: HS256/384/512 sign+verify round-trips, tamper and expiry failures,
 * unsafe decode, RS256 with PEM keys, ES256 unsupported error.
 */
class JwtNodeTest {

    private final JwtNode node = new JwtNode();

    private Map<String, Object> sign(String alg, String secret, Map<String, Object> claims) {
        return CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "sign", "alg", alg, "secret", secret),
                CodecTestSupport.input("claims", claims)));
    }

    private Map<String, Object> verify(String alg, String secret, String token) {
        return CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "verify", "alg", alg, "secret", secret),
                CodecTestSupport.input("token", token)));
    }

    @Test
    void hs256SignVerifyRoundTrip() {
        Map<String, Object> signed = sign("HS256", "shh", Map.of("sub", "user-1"));
        assertTrue(CodecTestSupport.isOk(signed), () -> CodecTestSupport.error(signed));
        String token = String.valueOf(CodecTestSupport.data(signed).get("token"));
        assertEquals(3, token.split("\\.").length);

        Map<String, Object> verified = verify("HS256", "shh", token);
        assertTrue(CodecTestSupport.isOk(verified), () -> CodecTestSupport.error(verified));
        assertEquals(Boolean.TRUE, CodecTestSupport.data(verified).get("valid"));
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) CodecTestSupport.data(verified).get("claims");
        assertEquals("user-1", claims.get("sub"));
    }

    @Test
    void hs384AndHs512RoundTrip() {
        for (String alg : new String[]{"HS384", "HS512"}) {
            Map<String, Object> signed = sign(alg, "shh", Map.of("a", 1));
            assertTrue(CodecTestSupport.isOk(signed), () -> CodecTestSupport.error(signed));
            String token = String.valueOf(CodecTestSupport.data(signed).get("token"));
            assertTrue(CodecTestSupport.isOk(verify(alg, "shh", token)));
        }
    }

    @Test
    void wrongSecretFailsVerify() {
        Map<String, Object> signed = sign("HS256", "right", Map.of("sub", "u"));
        String token = String.valueOf(CodecTestSupport.data(signed).get("token"));
        Map<String, Object> verified = verify("HS256", "wrong", token);
        assertNotNull(CodecTestSupport.error(verified));
    }

    @Test
    void tamperedPayloadFailsVerify() {
        Map<String, Object> signed = sign("HS256", "shh", Map.of("role", "user"));
        String token = String.valueOf(CodecTestSupport.data(signed).get("token"));
        String[] parts = token.split("\\.");
        // flip the payload to {"role":"admin"} without re-signing
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"role\":\"admin\"}".getBytes());
        Map<String, Object> verified = verify("HS256", "shh", String.join(".", parts));
        assertNotNull(CodecTestSupport.error(verified));
    }

    @Test
    void algMismatchIsRejected() {
        Map<String, Object> signed = sign("HS256", "shh", Map.of());
        String token = String.valueOf(CodecTestSupport.data(signed).get("token"));
        Map<String, Object> verified = verify("HS512", "shh", token);
        assertNotNull(CodecTestSupport.error(verified));
    }

    @Test
    void expiredTokenFails() {
        long past = System.currentTimeMillis() / 1000L - 3600;
        Map<String, Object> signed = sign("HS256", "shh", Map.of("exp", past));
        String token = String.valueOf(CodecTestSupport.data(signed).get("token"));
        Map<String, Object> verified = verify("HS256", "shh", token);
        String error = CodecTestSupport.error(verified);
        assertNotNull(error);
        assertTrue(error.contains("expired"));
    }

    @Test
    void futureNbfFails() {
        long future = System.currentTimeMillis() / 1000L + 3600;
        Map<String, Object> signed = sign("HS256", "shh", Map.of("nbf", future));
        String token = String.valueOf(CodecTestSupport.data(signed).get("token"));
        assertNotNull(CodecTestSupport.error(verify("HS256", "shh", token)));
    }

    @Test
    void expWithinSkewPasses() {
        long justExpired = System.currentTimeMillis() / 1000L - 30; // inside the 60s default skew
        Map<String, Object> signed = sign("HS256", "shh", Map.of("exp", justExpired));
        String token = String.valueOf(CodecTestSupport.data(signed).get("token"));
        assertTrue(CodecTestSupport.isOk(verify("HS256", "shh", token)),
                () -> CodecTestSupport.error(verify("HS256", "shh", token)));
    }

    @Test
    void decodeIsMarkedUnsafe() {
        Map<String, Object> signed = sign("HS256", "shh", Map.of("sub", "user-9"));
        String token = String.valueOf(CodecTestSupport.data(signed).get("token"));
        Map<String, Object> decoded = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "decode"),
                CodecTestSupport.input("token", token)));
        assertTrue(CodecTestSupport.isOk(decoded), () -> CodecTestSupport.error(decoded));
        String warning = String.valueOf(CodecTestSupport.data(decoded).get("warning"));
        assertTrue(warning.contains("unsafe"));
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) CodecTestSupport.data(decoded).get("claims");
        assertEquals("user-9", claims.get("sub"));
    }

    @Test
    void malformedTokenFails() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "verify", "secret", "s"),
                CodecTestSupport.input("token", "not-a-jwt")));
        assertNotNull(CodecTestSupport.error(result));
    }

    @Test
    void rs256RoundTripWithPemKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        Base64.Encoder mime = Base64.getMimeEncoder(64, "\n".getBytes());
        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
                + mime.encodeToString(kp.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + mime.encodeToString(kp.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n";

        Map<String, Object> signed = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "sign", "alg", "RS256", "private_key", privatePem),
                CodecTestSupport.input("claims", Map.of("iss", "sq"))));
        assertTrue(CodecTestSupport.isOk(signed), () -> CodecTestSupport.error(signed));
        String token = String.valueOf(CodecTestSupport.data(signed).get("token"));

        Map<String, Object> verified = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "verify", "alg", "RS256", "public_key", publicPem),
                CodecTestSupport.input("token", token)));
        assertTrue(CodecTestSupport.isOk(verified), () -> CodecTestSupport.error(verified));
    }

    @Test
    void es256ReturnsUnsupported() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "sign", "alg", "ES256", "private_key", "x"),
                CodecTestSupport.input("claims", Map.of())));
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(error.contains("not supported"));
    }

    @Test
    void missingClaimsDefaultToEmptyObject() {
        Map<String, Object> signed = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "sign", "secret", "shh"),
                CodecTestSupport.input()));
        assertTrue(CodecTestSupport.isOk(signed), () -> CodecTestSupport.error(signed));
        assertNull(CodecTestSupport.error(signed));
    }

    @Test
    void tokenOnlyInfersVerifyBeforeSecretCheck() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "sign", "alg", "HS256"),
                CodecTestSupport.input("token", "abc")));
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(error.contains("compact JWS"), () -> error);
    }

    @Test
    void emptyInputWithoutSecretReturnsUsageError() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "sign", "alg", "HS256"),
                CodecTestSupport.input()));
        String error = CodecTestSupport.error(result);
        assertNotNull(error);
        assertTrue(error.contains("claims"), () -> error);
        assertTrue(error.contains("token"), () -> error);
    }

    @Test
    void claimsMustBeObjectBeforeSignSecret() {
        Map<String, Object> result = CodecTestSupport.run(node, CodecTestSupport.request(
                CodecTestSupport.cfg("operation", "sign", "alg", "HS256"),
                CodecTestSupport.input("claims", "not-an-object")));
        assertEquals("claims must be a JSON object", CodecTestSupport.error(result));
    }
}
