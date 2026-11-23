package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import studioquack.StudioQuackHelper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;
import java.util.Map;

/**
 * RFC 6238 TOTP generator ({@code generate} only — the definition's operation
 * select lists exactly that one value).
 *
 * <p>The Base32 shared secret arrives via the {@code secret} password config and
 * is redacted from every error path; codes are returned on the output port and
 * never logged. HmacSHA1 (default) or HmacSHA256, 6 or 8 digits, configurable
 * period. INPUT {@code timestamp} (unix seconds) overrides the clock for tests.
 */
public final class TotpNode implements NodeHandler {

    public static final String DEFAULT_ID = "e19a4703-bc1c-4741-be40-861fbcac98ee";

    private final String templateId;

    public TotpNode() {
        this(DEFAULT_ID);
    }

    public TotpNode(String templateId) {
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
        List<String> secrets = new java.util.ArrayList<>();
        try {
            String op = CodecSupport.op(cfg, in);
            if (op == null) {
                op = "generate";
            }
            if (!"generate".equals(op)) {
                return CodecSupport.error("Unknown operation: " + op + ". Accepted: [generate]");
            }

            String secret = DataSupport.cfgStr(cfg, "secret", null);
            if (secret != null) {
                secrets.add(secret);
            }
            byte[] key = Base32.decode(secret);
            if (key == null || key.length == 0) {
                return CodecSupport.error("secret is required and must be a valid Base32 shared secret");
            }

            String algorithm = CodecSupport.lower(DataSupport.cfgStr(cfg, "algorithm", "sha1"), "sha1");
            String jca = switch (algorithm) {
                case "sha1" -> "HmacSHA1";
                case "sha256" -> "HmacSHA256";
                default -> null;
            };
            if (jca == null) {
                return CodecSupport.error("Unknown algorithm: " + algorithm + ". Accepted: sha1 | sha256");
            }

            int digits = DataSupport.cfgInt(cfg, "digits", 6);
            if (digits != 6 && digits != 8) {
                return CodecSupport.error("digits must be 6 or 8");
            }
            int period = DataSupport.cfgInt(cfg, "period", 30);

            long timestamp = nowSeconds(in);
            if (timestamp < 0) {
                return CodecSupport.error("timestamp must be unix seconds");
            }

            String code = totp(key, jca, timestamp, period, digits);
            if (code == null) {
                return CodecSupport.error("TOTP generation failed", secrets);
            }

            Map<String, Object> data = CodecSupport.data();
            data.put("code", code);
            data.put("digits", digits);
            data.put("period", period);
            data.put("algorithm", algorithm);
            data.put("for_timestamp", timestamp);
            long counter = timestamp / period;
            data.put("window_start", counter * period);
            data.put("window_end", (counter + 1) * period);
            return CodecSupport.ok(data);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("Error: " + e.getClass().getSimpleName(), secrets);
        }
    }

    private static long nowSeconds(Map<String, Object> in) {
        Object raw = in == null ? null : in.get("timestamp");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return System.currentTimeMillis() / 1000L;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** RFC 6238 §5.3: HMAC over the 8-byte counter, dynamic truncation, mod 10^digits. */
    static String totp(byte[] key, String jcaMac, long timestampSeconds, int period, int digits) {
        try {
            long counter = timestampSeconds / period;
            byte[] msg = new byte[8];
            for (int i = 7; i >= 0; i--) {
                msg[i] = (byte) (counter & 0xff);
                counter >>>= 8;
            }
            Mac mac = Mac.getInstance(jcaMac);
            mac.init(new SecretKeySpec(key, jcaMac));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int mod = digits == 8 ? 100_000_000 : 1_000_000;
            int otp = binary % mod;
            String s = Integer.toString(otp);
            return "0".repeat(digits - s.length()) + s;
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }
}
