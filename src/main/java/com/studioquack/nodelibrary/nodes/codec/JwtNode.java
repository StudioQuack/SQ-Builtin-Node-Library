package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RFC 7519 JWT (compact JWS): {@code sign | verify | decode}.
 *
 * <p>HS256/384/512 via {@code Mac} over the ASCII signing input; RS256 via
 * {@code SHA256withRSA} with PKCS8 private / X509 public PEM from the secret
 * configs. ES256 returns a clear unsupported-in-this-build error. {@code verify}
 * pins the header {@code alg} to the configured algorithm (no alg confusion),
 * compares signatures in constant time, and enforces {@code exp}/{@code nbf}
 * with {@code clock_skew_seconds}. {@code decode} skips verification and is
 * marked unsafe in its payload. Keys are redacted from every error path.
 */
public final class JwtNode implements NodeHandler {

    public static final String DEFAULT_ID = "2285af36-2355-4afe-966f-8200e7df362a";

    private final String templateId;

    public JwtNode() {
        this(DEFAULT_ID);
    }

    public JwtNode(String templateId) {
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
            String op = resolveJwtOperation(cfg, in);
            if (op == null) {
                return CodecSupport.error(
                        "either `claims` (to sign) or `token` (to verify/decode) must be provided");
            }
            if (!List.of("sign", "verify", "decode").contains(op)) {
                return CodecSupport.error("Unknown operation: " + op + ". Accepted: [sign, verify, decode]");
            }
            for (String k : List.of("secret", "private_key", "public_key")) {
                String v = DataSupport.cfgStr(cfg, k, null);
                if (v != null) {
                    secrets.add(v);
                }
            }
            String alg = DataSupport.cfgStr(cfg, "alg", "HS256").trim();
            return switch (op) {
                case "sign" -> sign(cfg, in, alg, secrets);
                case "verify" -> verify(cfg, in, alg, secrets);
                case "decode" -> decode(in);
                default -> CodecSupport.error("Unknown operation: " + op + ". Accepted: [sign, verify, decode]");
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("Error: " + e.getClass().getSimpleName(), secrets);
        }
    }

    /**
     * INPUT {@code operation} wins; otherwise infer sign vs verify from {@code claims}/{@code token}
     * so agent tools are not stuck on config default {@code sign}. Empty INPUT with no signing
     * material yields {@code null} (usage error) instead of a misleading secret error.
     */
    static String resolveJwtOperation(Map<String, Object> cfg, Map<String, Object> in) {
        String inputOp = DataSupport.inStr(in, "operation", null);
        if (inputOp != null) {
            return inputOp.toLowerCase(Locale.ROOT);
        }
        String cfgOp = DataSupport.cfgStr(cfg, "operation", "sign").trim().toLowerCase(Locale.ROOT);
        boolean hasClaims = in != null && in.containsKey("claims");
        boolean hasToken = in != null && in.containsKey("token");
        if (hasToken && !hasClaims) {
            return "decode".equals(cfgOp) ? "decode" : "verify";
        }
        if (hasClaims && !hasToken) {
            return "sign";
        }
        if (hasClaims && hasToken) {
            return cfgOp;
        }
        if ("sign".equals(cfgOp) && !hasSignMaterial(cfg)) {
            return null;
        }
        return cfgOp;
    }

    private static boolean hasSignMaterial(Map<String, Object> cfg) {
        String alg = DataSupport.cfgStr(cfg, "alg", "HS256").trim();
        if (hmacFor(alg) != null) {
            return DataSupport.cfgStr(cfg, "secret", null) != null;
        }
        if ("RS256".equals(alg)) {
            return DataSupport.cfgStr(cfg, "private_key", null) != null;
        }
        return false;
    }

    // --- sign -----------------------------------------------------------------

    private static Map<String, Object> sign(Map<String, Object> cfg, Map<String, Object> in,
                                            String alg, List<String> secrets) {
        Object claimsRaw = in.get("claims");
        Map<String, Object> claims = claimsRaw == null ? new LinkedHashMap<>() : Json.asMap(claimsRaw);
        if (claimsRaw != null && !(claimsRaw instanceof Map<?, ?>)) {
            return CodecSupport.error("claims must be a JSON object");
        }
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "JWT");
        header.put("alg", alg);
        String signingInput = b64Url(Json.write(header).getBytes(StandardCharsets.UTF_8))
                + "." + b64Url(Json.write(claims).getBytes(StandardCharsets.UTF_8));
        byte[] signature;
        String macName = hmacFor(alg);
        try {
            if (macName != null) {
                String secret = DataSupport.cfgStr(cfg, "secret", null);
                if (secret == null) {
                    return CodecSupport.error("secret is required for " + alg + " sign");
                }
                Mac mac = Mac.getInstance(macName);
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macName));
                signature = mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
            } else if ("RS256".equals(alg)) {
                String pem = DataSupport.cfgStr(cfg, "private_key", null);
                if (pem == null) {
                    return CodecSupport.error("private_key (PKCS8 PEM) is required for RS256 sign");
                }
                PrivateKey key = FilesSupport.loadPkcs8(pem, null);
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initSign(key);
                sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
                signature = sig.sign();
            } else if ("ES256".equals(alg)) {
                return CodecSupport.error("ES256 is not supported in this build; use HS256 | HS384 | HS512 | RS256");
            } else {
                return CodecSupport.error("Unknown alg: " + alg
                        + ". Accepted: HS256 | HS384 | HS512 | RS256 | ES256");
            }
        } catch (FilesSupport.Fail e) {
            return CodecSupport.error(e.getMessage(), secrets);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("JWT sign failed: " + e.getClass().getSimpleName(), secrets);
        }
        Map<String, Object> data = CodecSupport.data();
        data.put("token", signingInput + "." + b64Url(signature));
        data.put("alg", alg);
        return CodecSupport.ok(data);
    }

    // --- verify ----------------------------------------------------------------

    private static Map<String, Object> verify(Map<String, Object> cfg, Map<String, Object> in,
                                              String alg, List<String> secrets) {
        String token = DataSupport.inStr(in, "token", null);
        if (token == null) {
            return CodecSupport.error("token is required for operation verify");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return CodecSupport.error("token is not a compact JWS (header.payload.signature)");
        }
        Map<String, Object> header = DataSupport.parseObject(b64UrlDecode(parts[0]));
        Map<String, Object> claims = DataSupport.parseObject(b64UrlDecode(parts[1]));
        if (header == null || claims == null) {
            return CodecSupport.error("token header/payload are not valid base64url JSON");
        }
        String headerAlg = DataSupport.trimToNull(String.valueOf(header.getOrDefault("alg", "")));
        if (headerAlg == null || !headerAlg.equals(alg)) {
            // Pinning to the configured alg blocks the classic alg-confusion downgrade.
            return CodecSupport.error("token alg does not match the configured alg");
        }
        byte[] expected;
        String signingInput = parts[0] + "." + parts[1];
        String macName = hmacFor(alg);
        try {
            if (macName != null) {
                String secret = DataSupport.cfgStr(cfg, "secret", null);
                if (secret == null) {
                    return CodecSupport.error("secret is required for " + alg + " verify");
                }
                Mac mac = Mac.getInstance(macName);
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macName));
                expected = mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
            } else if ("RS256".equals(alg)) {
                String pem = DataSupport.cfgStr(cfg, "public_key", null);
                if (pem == null) {
                    return CodecSupport.error("public_key (X509 PEM) is required for RS256 verify");
                }
                PublicKey key = loadX509(pem);
                if (key == null) {
                    return CodecSupport.error("public_key is not a valid X509 PEM public key");
                }
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initVerify(key);
                sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
                byte[] presented = b64UrlDecodeBytes(parts[2]);
                if (presented == null || !sig.verify(presented)) {
                    return CodecSupport.error("JWT verify failed: signature mismatch");
                }
                expected = null;
            } else if ("ES256".equals(alg)) {
                return CodecSupport.error("ES256 is not supported in this build; use HS256 | HS384 | HS512 | RS256");
            } else {
                return CodecSupport.error("Unknown alg: " + alg
                        + ". Accepted: HS256 | HS384 | HS512 | RS256 | ES256");
            }
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("JWT verify failed: " + e.getClass().getSimpleName(), secrets);
        }
        if (expected != null) {
            byte[] presented = b64UrlDecodeBytes(parts[2]);
            if (presented == null || !MessageDigest.isEqual(expected, presented)) {
                return CodecSupport.error("JWT verify failed: signature mismatch");
            }
        }
        String timeError = checkTimes(claims, DataSupport.cfgInt(cfg, "clock_skew_seconds", 60));
        if (timeError != null) {
            return CodecSupport.error(timeError);
        }
        Map<String, Object> data = CodecSupport.data();
        data.put("valid", true);
        data.put("alg", alg);
        data.put("header", header);
        data.put("claims", claims);
        return CodecSupport.ok(data);
    }

    /** exp / nbf enforcement with configured skew; null when the token is in bounds. */
    private static String checkTimes(Map<String, Object> claims, int skewSeconds) {
        long now = System.currentTimeMillis() / 1000L;
        long skew = Math.max(skewSeconds, 0);
        Object exp = claims.get("exp");
        if (exp != null) {
            Long expSec = toLong(exp);
            if (expSec == null) {
                return "JWT verify failed: exp claim is not numeric";
            }
            if (now - skew > expSec) {
                return "JWT verify failed: token expired";
            }
        }
        Object nbf = claims.get("nbf");
        if (nbf != null) {
            Long nbfSec = toLong(nbf);
            if (nbfSec == null) {
                return "JWT verify failed: nbf claim is not numeric";
            }
            if (now + skew < nbfSec) {
                return "JWT verify failed: token not yet valid (nbf)";
            }
        }
        return null;
    }

    // --- decode (unsafe, no verification) ----------------------------------------

    private static Map<String, Object> decode(Map<String, Object> in) {
        String token = DataSupport.inStr(in, "token", null);
        if (token == null) {
            return CodecSupport.error("token is required for operation decode");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length < 2) {
            return CodecSupport.error("token is not a compact JWS (header.payload.signature)");
        }
        Map<String, Object> header = DataSupport.parseObject(b64UrlDecode(parts[0]));
        Map<String, Object> claims = DataSupport.parseObject(b64UrlDecode(parts[1]));
        if (header == null || claims == null) {
            return CodecSupport.error("token header/payload are not valid base64url JSON");
        }
        Map<String, Object> data = CodecSupport.data();
        data.put("header", header);
        data.put("claims", claims);
        data.put("signature_present", parts.length == 3 && !parts[2].isBlank());
        data.put("warning", "decoded without signature verification — unsafe for authorization decisions");
        return CodecSupport.ok(data);
    }

    // --- helpers ------------------------------------------------------------------

    private static String hmacFor(String alg) {
        return switch (alg) {
            case "HS256" -> "HmacSHA256";
            case "HS384" -> "HmacSHA384";
            case "HS512" -> "HmacSHA512";
            default -> null;
        };
    }

    /** X509 {@code -----BEGIN PUBLIC KEY-----} PEM → RSA public key; null when unusable. */
    static PublicKey loadX509(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        String body = pem.replace("\\n", "\n")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private static String b64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static byte[] b64UrlDecodeBytes(String s) {
        try {
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String b64UrlDecode(String s) {
        byte[] b = b64UrlDecodeBytes(s);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }
}
