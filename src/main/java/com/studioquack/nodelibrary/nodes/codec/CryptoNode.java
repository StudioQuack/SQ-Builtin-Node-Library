package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import studioquack.StudioQuackHelper;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-process JDK cryptography: {@code hash | hmac | sign | encrypt | decrypt |
 * random | uuid}.
 *
 * <p>Keys arrive via the {@code key} / {@code passphrase} password configs, are
 * collected into a redaction list, and never appear in errors or logs. Symmetric
 * keys are derived as {@code SHA-256(secret)} truncated to the cipher's key size
 * — deterministic across invocations without a KDF round-trip, and uniform
 * regardless of passphrase length. GCM uses a random 12-byte IV prepended to the
 * ciphertext; the {@code encoding} param (hex|base64) transports the binary.
 */
public final class CryptoNode implements NodeHandler {

    public static final String DEFAULT_ID = "5b97e104-1029-407d-a20b-a6a14dfee66d";

    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_RANDOM_BYTES = 65_536;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String templateId;

    public CryptoNode() {
        this(DEFAULT_ID);
    }

    public CryptoNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        if (request == null) {
            return CodecSupport.error("request body must be a JSON object");
        }
        Map<String, Object> cfg = DataSupport.readConfig(request);
        Map<String, Object> in = DataSupport.readInput(request, helper);
        List<String> secrets = new ArrayList<>();
        try {
            String op = CodecSupport.op(cfg, in);
            if (op == null) {
                return CodecSupport.error("operation is required. Accepted: "
                        + "[hash, hmac, sign, encrypt, decrypt, random, uuid]");
            }
            String key = DataSupport.cfgStr(cfg, "key", null);
            String passphrase = DataSupport.cfgStr(cfg, "passphrase", null);
            if (key != null) {
                secrets.add(key);
            }
            if (passphrase != null) {
                secrets.add(passphrase);
            }
            String secret = key != null ? key : passphrase;

            return switch (op) {
                case "hash" -> hash(cfg, in);
                case "hmac" -> hmac(cfg, in, secret, secrets);
                case "sign" -> sign(cfg, in, secret, secrets);
                case "encrypt" -> crypt(cfg, in, secret, true, secrets);
                case "decrypt" -> crypt(cfg, in, secret, false, secrets);
                case "random" -> random(cfg, in);
                case "uuid" -> uuid();
                default -> CodecSupport.error("Unknown operation: " + op
                        + ". Accepted: [hash, hmac, sign, encrypt, decrypt, random, uuid]");
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("Error: " + e.getClass().getSimpleName(), secrets);
        }
    }

    // --- hash -----------------------------------------------------------------

    private static Map<String, Object> hash(Map<String, Object> cfg, Map<String, Object> in) {
        String data = DataSupport.inStr(in, "data", null);
        if (data == null) {
            return CodecSupport.error("data is required for operation hash");
        }
        String algorithm = CodecSupport.lower(DataSupport.cfgStr(cfg, "algorithm", "sha256"), "sha256");
        String jca = digestName(algorithm);
        if (jca == null) {
            return CodecSupport.error("Unknown algorithm: " + algorithm
                    + ". Accepted: sha256 | sha384 | sha512 (sha1/md5 checksum-compat only)");
        }
        String encoding = encoding(cfg);
        try {
            byte[] digest = MessageDigest.getInstance(jca).digest(data.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> out = CodecSupport.data();
            out.put("operation", "hash");
            out.put("algorithm", algorithm);
            out.put("encoding", encoding);
            out.put("result", CodecSupport.encode(digest, encoding));
            return CodecSupport.ok(out);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("hash failed: " + e.getClass().getSimpleName());
        }
    }

    // --- hmac -----------------------------------------------------------------

    private static Map<String, Object> hmac(Map<String, Object> cfg, Map<String, Object> in,
                                            String secret, List<String> secrets) {
        String data = DataSupport.inStr(in, "data", null);
        if (data == null) {
            return CodecSupport.error("data is required for operation hmac");
        }
        if (secret == null) {
            return CodecSupport.error("key (or passphrase) is required for operation hmac");
        }
        String algorithm = CodecSupport.lower(DataSupport.cfgStr(cfg, "algorithm", "sha256"), "sha256");
        String jca = hmacName(algorithm);
        if (jca == null) {
            return CodecSupport.error("Unknown algorithm: " + algorithm
                    + ". Accepted: sha256 | sha384 | sha512 (sha1/md5 checksum-compat only)");
        }
        String encoding = encoding(cfg);
        try {
            Mac mac = Mac.getInstance(jca);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), jca));
            byte[] tag = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> out = CodecSupport.data();
            out.put("operation", "hmac");
            out.put("algorithm", algorithm);
            out.put("encoding", encoding);
            out.put("result", CodecSupport.encode(tag, encoding));
            return CodecSupport.ok(out);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("hmac failed: " + e.getClass().getSimpleName(), secrets);
        }
    }

    // --- sign (RSA) -------------------------------------------------------------

    private static Map<String, Object> sign(Map<String, Object> cfg, Map<String, Object> in,
                                            String secret, List<String> secrets) {
        String data = DataSupport.inStr(in, "data", null);
        if (data == null) {
            return CodecSupport.error("data is required for operation sign");
        }
        if (secret == null) {
            return CodecSupport.error("key holding a PKCS8 PEM private key is required for operation sign");
        }
        String encoding = encoding(cfg);
        try {
            // Definition fixes sign at SHA256withRSA; key PEM may be passphrase-encrypted.
            PrivateKey pk = FilesSupport.loadPkcs8(secret,
                    DataSupport.cfgStr(cfg, "passphrase", null));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(pk);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> out = CodecSupport.data();
            out.put("operation", "sign");
            out.put("algorithm", "SHA256withRSA");
            out.put("encoding", encoding);
            out.put("result", CodecSupport.encode(sig.sign(), encoding));
            return CodecSupport.ok(out);
        } catch (FilesSupport.Fail e) {
            return CodecSupport.error(e.getMessage(), secrets);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("sign failed: " + e.getClass().getSimpleName(), secrets);
        }
    }

    // --- encrypt / decrypt ------------------------------------------------------

    private static Map<String, Object> crypt(Map<String, Object> cfg, Map<String, Object> in,
                                             String secret, boolean encrypt, List<String> secrets) {
        String op = encrypt ? "encrypt" : "decrypt";
        String data = DataSupport.inStr(in, "data", null);
        if (data == null) {
            return CodecSupport.error("data is required for operation " + op);
        }
        if (secret == null) {
            return CodecSupport.error("key (or passphrase) is required for operation " + op);
        }
        String cipherName = CodecSupport.lower(DataSupport.cfgStr(cfg, "cipher", "aes-256-gcm"), "aes-256-gcm");
        String encoding = encoding(cfg);
        try {
            CipherSpec spec = cipherSpec(cipherName);
            if (spec == null) {
                return CodecSupport.error("Unknown cipher: " + cipherName
                        + ". Accepted: aes-256-gcm | aes-128-gcm | chacha20-poly1305");
            }
            byte[] keyBytes = Arrays.copyOf(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)),
                    spec.keyBytes);
            Cipher cipher = Cipher.getInstance(spec.jca);
            if (encrypt) {
                byte[] iv = new byte[GCM_IV_BYTES];
                RANDOM.nextBytes(iv);
                // SunJCE ChaCha20-Poly1305 takes IvParameterSpec (12-byte nonce), not
                // ChaCha20ParameterSpec — that one is for the raw ChaCha20 stream cipher.
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, spec.keyAlgo),
                        spec.gcm ? new GCMParameterSpec(GCM_TAG_BITS, iv)
                                : new javax.crypto.spec.IvParameterSpec(iv));
                byte[] ct = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
                byte[] packed = new byte[iv.length + ct.length];
                System.arraycopy(iv, 0, packed, 0, iv.length);
                System.arraycopy(ct, 0, packed, iv.length, ct.length);
                Map<String, Object> out = CodecSupport.data();
                out.put("operation", "encrypt");
                out.put("cipher", cipherName);
                out.put("encoding", encoding);
                out.put("result", CodecSupport.encode(packed, encoding));
                return CodecSupport.ok(out);
            }
            byte[] packed = CodecSupport.decode(data, encoding);
            if (packed == null || packed.length <= GCM_IV_BYTES) {
                return CodecSupport.error("data is not valid " + encoding + " ciphertext for operation decrypt");
            }
            byte[] iv = Arrays.copyOf(packed, GCM_IV_BYTES);
            byte[] ct = Arrays.copyOfRange(packed, GCM_IV_BYTES, packed.length);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, spec.keyAlgo),
                    spec.gcm ? new GCMParameterSpec(GCM_TAG_BITS, iv)
                            : new javax.crypto.spec.IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(ct);
            Map<String, Object> out = CodecSupport.data();
            out.put("operation", "decrypt");
            out.put("cipher", cipherName);
            out.put("encoding", "utf8");
            out.put("result", new String(plain, StandardCharsets.UTF_8));
            return CodecSupport.ok(out);
        } catch (javax.crypto.AEADBadTagException e) {
            // Wrong key or tampered ciphertext — never say which.
            return CodecSupport.error("decrypt failed: authentication tag mismatch", secrets);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error(op + " failed: " + e.getClass().getSimpleName(), secrets);
        }
    }

    // --- random / uuid ------------------------------------------------------------

    private static Map<String, Object> random(Map<String, Object> cfg, Map<String, Object> in) {
        int length = DataSupport.inInt(in, "length", 32);
        if (length <= 0 || length > MAX_RANDOM_BYTES) {
            return CodecSupport.error("length must be between 1 and " + MAX_RANDOM_BYTES + " bytes");
        }
        String encoding = encoding(cfg);
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        Map<String, Object> out = CodecSupport.data();
        out.put("operation", "random");
        out.put("encoding", encoding);
        out.put("length", length);
        out.put("result", CodecSupport.encode(bytes, encoding));
        return CodecSupport.ok(out);
    }

    private static Map<String, Object> uuid() {
        Map<String, Object> out = CodecSupport.data();
        out.put("operation", "uuid");
        out.put("result", UUID.randomUUID().toString());
        return CodecSupport.ok(out);
    }

    // --- helpers ------------------------------------------------------------------

    private static String encoding(Map<String, Object> cfg) {
        return CodecSupport.lower(DataSupport.cfgStr(cfg, "encoding", "hex"), "hex");
    }

    static String digestName(String algorithm) {
        return switch (algorithm) {
            case "sha256", "sha-256" -> "SHA-256";
            case "sha384", "sha-384" -> "SHA-384";
            case "sha512", "sha-512" -> "SHA-512";
            case "sha1", "sha-1" -> "SHA-1";
            case "md5" -> "MD5";
            default -> null;
        };
    }

    static String hmacName(String algorithm) {
        return switch (algorithm) {
            case "sha256", "sha-256" -> "HmacSHA256";
            case "sha384", "sha-384" -> "HmacSHA384";
            case "sha512", "sha-512" -> "HmacSHA512";
            case "sha1", "sha-1" -> "HmacSHA1";
            case "md5" -> "HmacMD5";
            default -> null;
        };
    }

    private static CipherSpec cipherSpec(String name) {
        return switch (name) {
            case "aes-256-gcm" -> new CipherSpec("AES/GCM/NoPadding", "AES", 32, true);
            case "aes-128-gcm" -> new CipherSpec("AES/GCM/NoPadding", "AES", 16, true);
            case "chacha20-poly1305" -> new CipherSpec("ChaCha20-Poly1305", "ChaCha20", 32, false);
            default -> null;
        };
    }

    private record CipherSpec(String jca, String keyAlgo, int keyBytes, boolean gcm) {
    }
}
