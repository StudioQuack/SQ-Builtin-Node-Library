package com.studioquack.nodelibrary.nodes.codec;

/**
 * RFC 4648 Base32 decoder (no encoder needed by the TOTP node). Tolerates
 * lowercase, whitespace, and missing/resent padding — authenticator apps and
 * secret managers emit all of these shapes.
 */
final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
        // utility — never instantiated
    }

    /** Decoded bytes, or {@code null} when the input is not valid Base32. */
    static byte[] decode(String input) {
        if (input == null) {
            return null;
        }
        String s = input.replaceAll("[\\s=]+", "").toUpperCase(java.util.Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = ALPHABET.indexOf(s.charAt(i));
            if (v < 0) {
                return null;
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >>> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }
}
