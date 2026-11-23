package com.studioquack.nodelibrary.nodes.datastore;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SCRAM-SHA-256 (RFC 7677) for PostgreSQL AuthenticationSASL. ASCII passwords
 * only (SASLprep is not applied). Never logs the password.
 */
final class PostgresScram {

    private PostgresScram() {
        // crypto — never instantiated
    }

    static Start begin(String user) {
        byte[] nonce = new byte[18];
        new SecureRandom().nextBytes(nonce);
        String clientNonce = Base64.getEncoder().encodeToString(nonce);
        String gs2 = "n,,";
        String bare = "n=" + saslName(user) + ",r=" + clientNonce;
        return new Start(gs2, bare, gs2 + bare, clientNonce);
    }

    static String clientFinal(Start start, String serverFirst, String password) throws Exception {
        String nonce = attr(serverFirst, 'r');
        String saltB64 = attr(serverFirst, 's');
        int iter = Integer.parseInt(attr(serverFirst, 'i'));
        if (nonce == null || !nonce.startsWith(start.clientNonce) || saltB64 == null || iter < 1 || iter > 1_000_000) {
            throw new IllegalArgumentException("scram server-first rejected");
        }
        String withoutProof = "c=" + Base64.getEncoder().encodeToString(start.gs2.getBytes(StandardCharsets.UTF_8))
                + ",r=" + nonce;
        String authMessage = start.clientFirstBare + "," + serverFirst + "," + withoutProof;
        byte[] salt = Base64.getDecoder().decode(saltB64);
        byte[] salted = pbkdf2(password == null ? "" : password, salt, iter);
        byte[] clientKey = hmac(salted, "Client Key");
        byte[] storedKey = sha256(clientKey);
        byte[] proof = xor(clientKey, hmac(storedKey, authMessage));
        return withoutProof + ",p=" + Base64.getEncoder().encodeToString(proof);
    }

    private static String saslName(String user) {
        String u = user == null ? "" : user;
        return u.replace("=", "=3D").replace(",", "=2C");
    }

    private static String attr(String msg, char key) {
        if (msg == null) {
            return null;
        }
        for (String part : msg.split(",")) {
            if (part.length() >= 2 && part.charAt(0) == key && part.charAt(1) == '=') {
                return part.substring(2);
            }
        }
        return null;
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iter) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iter, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i]);
        }
        return out;
    }

    record Start(String gs2, String clientFirstBare, String clientFirst, String clientNonce) {
    }
}
