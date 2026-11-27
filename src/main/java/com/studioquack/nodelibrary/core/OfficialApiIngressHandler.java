package com.studioquack.nodelibrary.core;

import studioquack.Json;
import studioquack.StudioQuackHelper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Library-internal inbound webhook processor for {@link OfficialApiNode} (and custom handlers
 * such as {@code GithubNode}).
 *
 * <p>Implements provider handshakes, constant-time cryptographic/token verification (pure JDK,
 * zero third-party deps), path-based batch fan-out (capped at {@value #MAX_EVENTS}), and
 * normalized event emission over the universal {@code __sq_webhook_*} contract.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>Every secret/signature comparison uses {@link MessageDigest#isEqual} (constant time).</li>
 *   <li>Secrets, raw webhook bodies, and auth headers are never logged or echoed in errors.</li>
 *   <li>Event fan-out is hard-capped at {@value #MAX_EVENTS} items per webhook invocation.</li>
 * </ul>
 */
public final class OfficialApiIngressHandler {

    /** Maximum events emitted from a single webhook POST (DoS / fan-out bound). */
    public static final int MAX_EVENTS = 100;

    /** ASN.1 DER X.509 SubjectPublicKeyInfo prefix for a 32-byte raw Ed25519 public key. */
    private static final byte[] ED25519_X509_PREFIX = new byte[] {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();

    private OfficialApiIngressHandler() {
    }

    /**
     * True when the incoming {@code /invoke} request (or bound {@link StudioQuackHelper})
     * represents an inbound webhook delivery ({@code invocationMode == "ingress"} or
     * a non-null {@code webhookRequest} map).
     */
    public static boolean isIngressInvocation(Map<String, Object> request, StudioQuackHelper helper) {
        if (helper != null && helper.isWebhookInvocation()) {
            return true;
        }
        if (request == null) {
            return false;
        }
        if ("ingress".equalsIgnoreCase(str(request.get("invocationMode")))) {
            return true;
        }
        if (request.get("webhookRequest") instanceof Map<?, ?>) {
            return true;
        }
        Map<String, Object> ctx = Json.asMap(request.get("executionContext"));
        return "ingress".equalsIgnoreCase(str(ctx.get("invocationMode")))
                || ctx.get("webhookRequest") instanceof Map<?, ?>;
    }

    /** Extract the {@code webhookRequest} envelope from top-level request, executionContext, or helper. */
    public static Map<String, Object> extractWebhookRequest(Map<String, Object> request, StudioQuackHelper helper) {
        if (request != null && request.get("webhookRequest") instanceof Map<?, ?> m) {
            return Json.asMap(m);
        }
        if (request != null) {
            Map<String, Object> ctx = Json.asMap(request.get("executionContext"));
            if (ctx.get("webhookRequest") instanceof Map<?, ?> m) {
                return Json.asMap(m);
            }
        }
        if (helper != null && helper.getWebhookRequest() != null) {
            return helper.getWebhookRequest();
        }
        return Map.of();
    }

    /**
     * Execute an inbound webhook invocation using {@code spec} (or generic JSON/form pass-through
     * when {@code spec} is {@code null}).
     */
    public static Map<String, Object> handle(Map<String, Object> request,
                                             StudioQuackHelper helper,
                                             OfficialApiSpec.IngressSpec spec,
                                             String nodeKey,
                                             Map<String, Object> cfg,
                                             Clock clock) {
        Map<String, Object> wr = extractWebhookRequest(request, helper);
        Clock effectiveClock = clock == null ? Clock.systemUTC() : clock;
        Map<String, Object> safeCfg = cfg == null ? Map.of() : cfg;
        String method = str(wr.get("method")).toUpperCase(Locale.ROOT);
        if (method.isEmpty()) {
            method = "POST";
        }
        Map<String, String> headers = lowerCaseHeaders(wr.get("headers"));
        Map<String, String> query = extractQueryMap(wr);
        byte[] rawBytes = extractRawBodyBytes(wr);
        Object parsedBody = extractParsedBody(wr, rawBytes, headers.get("content-type"));

        OfficialApiSpec.HandshakeSpec hs = spec == null ? null : spec.handshake;
        OfficialApiSpec.VerifySpec vs = spec == null ? null : spec.verify;
        OfficialApiSpec.ExtractSpec ex = spec == null ? null : spec.extract;

        // 1. Handshake BEFORE verification when handshake does not require signature verification
        // (e.g. Meta GET hub.challenge, Microsoft Graph POST/GET ?validationToken=..., Asana X-Hook-Secret).
        if (hs != null && !"none".equals(hs.type) && !hs.verifyBeforeHandshake) {
            Map<String, Object> hsOutcome = tryHandshake(hs, method, query, headers, parsedBody, safeCfg, helper);
            if (hsOutcome != null) {
                return hsOutcome;
            }
        }

        // 2. Cryptographic signature or shared-secret verification.
        if (vs != null && !"none".equals(vs.type)) {
            Map<String, Object> reject = verify(vs, method, wr, query, headers, rawBytes, parsedBody,
                    safeCfg, effectiveClock, helper);
            if (reject != null) {
                return reject;
            }
        }

        // 3. Handshake AFTER verification for signed POST handshakes
        // (e.g. Slack url_verification, Discord Ed25519 PING, Zoom endpoint.url_validation, GitHub ping).
        if (hs != null && !"none".equals(hs.type) && hs.verifyBeforeHandshake) {
            Map<String, Object> hsOutcome = tryHandshake(hs, method, query, headers, parsedBody, safeCfg, helper);
            if (hsOutcome != null) {
                return hsOutcome;
            }
        }

        // 4. Extract 0..N normalized events (capped at MAX_EVENTS).
        List<Map<String, Object>> events = extractEvents(ex, nodeKey, headers, parsedBody);
        return emitEvents(events, helper);
    }

    // ---------------------------------------------------------------- handshakes

    private static Map<String, Object> tryHandshake(OfficialApiSpec.HandshakeSpec hs,
                                                    String method,
                                                    Map<String, String> query,
                                                    Map<String, String> headers,
                                                    Object parsedBody,
                                                    Map<String, Object> cfg,
                                                    StudioQuackHelper helper) {
        if (!"ANY".equals(hs.method) && !hs.method.equalsIgnoreCase(method)) {
            return null;
        }
        return switch (hs.type) {
            case "query_challenge" -> handleQueryChallenge(hs, query, cfg, helper);
            case "json_challenge" -> handleJsonChallenge(hs, parsedBody, helper);
            case "header_echo" -> handleHeaderEcho(hs, headers, helper);
            case "zoom_url_validation" -> handleZoomUrlValidation(hs, parsedBody, cfg, helper);
            case "header_value_challenge" -> handleHeaderValueChallenge(hs, headers, helper);
            default -> null;
        };
    }

    private static Map<String, Object> handleQueryChallenge(OfficialApiSpec.HandshakeSpec hs,
                                                            Map<String, String> query,
                                                            Map<String, Object> cfg,
                                                            StudioQuackHelper helper) {
        String challengeParam = hs.challengeParam;
        if (challengeParam.isEmpty() || !query.containsKey(challengeParam)) {
            return null;
        }
        if (!hs.modeParam.isEmpty()) {
            String mode = query.getOrDefault(hs.modeParam, "");
            if (!hs.modeValue.isEmpty() && !hs.modeValue.equals(mode)) {
                return null;
            }
        }
        if (!hs.tokenParam.isEmpty() && !hs.tokenConfigKey.isEmpty()) {
            String expectedToken = DataSupport.cfgStr(cfg, hs.tokenConfigKey, "");
            if (!expectedToken.isEmpty()) {
                String presented = query.getOrDefault(hs.tokenParam, "");
                if (!constantTimeEquals(expectedToken, presented)) {
                    return reject(403, "invalid_verify_token", helper);
                }
            }
        }
        String challenge = query.getOrDefault(challengeParam, "");
        return challenge(challenge, hs.responseStatus, hs.responseContentType, null, helper);
    }

    private static Map<String, Object> handleJsonChallenge(OfficialApiSpec.HandshakeSpec hs,
                                                           Object parsedBody,
                                                           StudioQuackHelper helper) {
        if (parsedBody == null) {
            return null;
        }
        if (!hs.typePath.isEmpty()) {
            Object actualType = evalFirst(parsedBody, hs.typePath);
            if (actualType == null || !hs.typeValue.equals(String.valueOf(actualType))) {
                return null;
            }
        }
        if (!hs.staticResponseBody.isEmpty()) {
            return challenge(hs.staticResponseBody, hs.responseStatus, hs.responseContentType, null, helper);
        }
        Object challengeVal = hs.challengePath.isEmpty() ? null : evalFirst(parsedBody, hs.challengePath);
        if (challengeVal == null) {
            return null;
        }
        String challengeText = String.valueOf(challengeVal);
        if (!hs.responseKey.isEmpty()) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put(hs.responseKey, challengeText);
            return challenge(Json.write(json), hs.responseStatus, "application/json", null, helper);
        }
        return challenge(challengeText, hs.responseStatus, hs.responseContentType, null, helper);
    }

    private static Map<String, Object> handleHeaderEcho(OfficialApiSpec.HandshakeSpec hs,
                                                        Map<String, String> headers,
                                                        StudioQuackHelper helper) {
        if (hs.headerName.isEmpty()) {
            return null;
        }
        String value = headers.get(hs.headerName.toLowerCase(Locale.ROOT));
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (!hs.responseJsonKey.isEmpty()) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put(hs.responseJsonKey, value);
            return challenge(Json.write(json), hs.responseStatus, "application/json", null, helper);
        }
        String respHeader = hs.responseHeader.isEmpty() ? hs.headerName : hs.responseHeader;
        return challenge("", hs.responseStatus, hs.responseContentType, Map.of(respHeader, value), helper);
    }

    private static Map<String, Object> handleZoomUrlValidation(OfficialApiSpec.HandshakeSpec hs,
                                                               Object parsedBody,
                                                               Map<String, Object> cfg,
                                                               StudioQuackHelper helper) {
        if (parsedBody == null) {
            return null;
        }
        Object event = evalFirst(parsedBody, "event");
        if (!"endpoint.url_validation".equals(String.valueOf(event))) {
            return null;
        }
        Object plainTokenObj = evalFirst(parsedBody, "payload.plainToken");
        if (plainTokenObj == null) {
            return null;
        }
        String plainToken = String.valueOf(plainTokenObj);
        String secret = hs.secretConfigKey.isEmpty() ? "" : DataSupport.cfgStr(cfg, hs.secretConfigKey, "");
        if (secret.isEmpty()) {
            return reject(401, "missing_webhook_secret", helper);
        }
        byte[] digest = hmac("HmacSHA256", secret, plainToken.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plainToken", plainToken);
        body.put("encryptedToken", toHexLower(digest));
        return challenge(Json.write(body), 200, "application/json", null, helper);
    }

    private static Map<String, Object> handleHeaderValueChallenge(OfficialApiSpec.HandshakeSpec hs,
                                                                  Map<String, String> headers,
                                                                  StudioQuackHelper helper) {
        if (hs.headerName.isEmpty() || hs.headerValue.isEmpty()) {
            return null;
        }
        String actual = headers.get(hs.headerName.toLowerCase(Locale.ROOT));
        if (actual == null || !hs.headerValue.equalsIgnoreCase(actual.trim())) {
            return null;
        }
        String body = hs.staticResponseBody.isEmpty() ? "{\"ok\":true}" : hs.staticResponseBody;
        return challenge(body, hs.responseStatus, hs.responseContentType, null, helper);
    }

    // ---------------------------------------------------------------- verifiers

    private static Map<String, Object> verify(OfficialApiSpec.VerifySpec vs,
                                              String method,
                                              Map<String, Object> wr,
                                              Map<String, String> query,
                                              Map<String, String> headers,
                                              byte[] rawBytes,
                                              Object parsedBody,
                                              Map<String, Object> cfg,
                                              Clock clock,
                                              StudioQuackHelper helper) {
        String secret = vs.secretConfigKey.isEmpty() ? "" : DataSupport.cfgStr(cfg, vs.secretConfigKey, "");
        if (secret.isEmpty()) {
            // Secret not configured on this node instance: skip provider signature verification
            // (the daemon has already verified the StudioQuack webhook_key secret).
            return null;
        }
        return switch (vs.type) {
            case "hmac_sha256_hex" -> verifyHmacSha256Hex(vs, headers, rawBytes, secret, cfg, helper);
            case "hmac_sha256_base64" -> verifyHmacSha256Base64(vs, headers, rawBytes, secret, cfg, helper);
            case "hmac_sha256_timestamped" -> verifyHmacSha256Timestamped(vs, headers, rawBytes, secret, clock, helper);
            case "hmac_sha1_form" -> verifyHmacSha1Form(vs, wr, headers, rawBytes, parsedBody, secret, cfg, helper);
            case "ed25519" -> verifyEd25519(vs, headers, rawBytes, secret, helper);
            case "header_secret" -> verifyHeaderSecret(vs, headers, secret, cfg, helper);
            case "body_field_secret" -> verifyBodyFieldSecret(vs, parsedBody, secret, helper);
            default -> null;
        };
    }

    private static Map<String, Object> verifyHmacSha256Hex(OfficialApiSpec.VerifySpec vs,
                                                           Map<String, String> headers,
                                                           byte[] rawBytes,
                                                           String secret,
                                                           Map<String, Object> cfg,
                                                           StudioQuackHelper helper) {
        String headerName = resolveHeaderName(vs, cfg);
        String presented = headerName.isEmpty() ? null : headers.get(headerName);
        if (presented == null || presented.isBlank()) {
            return reject(401, "missing_signature", helper);
        }
        String candidate = presented.trim();
        if (!vs.prefix.isEmpty()) {
            if (!candidate.startsWith(vs.prefix)) {
                return reject(401, "invalid_signature", helper);
            }
            candidate = candidate.substring(vs.prefix.length());
        }
        String expected = toHexLower(hmac("HmacSHA256", secret, rawBytes));
        if (!constantTimeEquals(expected, candidate.toLowerCase(Locale.ROOT))) {
            return reject(401, "invalid_signature", helper);
        }
        return null;
    }

    private static Map<String, Object> verifyHmacSha256Base64(OfficialApiSpec.VerifySpec vs,
                                                              Map<String, String> headers,
                                                              byte[] rawBytes,
                                                              String secret,
                                                              Map<String, Object> cfg,
                                                              StudioQuackHelper helper) {
        String headerName = resolveHeaderName(vs, cfg);
        String presented = headerName.isEmpty() ? null : headers.get(headerName);
        if (presented == null || presented.isBlank()) {
            return reject(401, "missing_signature", helper);
        }
        String candidate = presented.trim();
        if (!vs.prefix.isEmpty()) {
            if (!candidate.startsWith(vs.prefix)) {
                return reject(401, "invalid_signature", helper);
            }
            candidate = candidate.substring(vs.prefix.length());
        }
        String expected = Base64.getEncoder().encodeToString(hmac("HmacSHA256", secret, rawBytes));
        if (!constantTimeEquals(expected, candidate)) {
            return reject(401, "invalid_signature", helper);
        }
        return null;
    }

    private static Map<String, Object> verifyHmacSha256Timestamped(OfficialApiSpec.VerifySpec vs,
                                                                   Map<String, String> headers,
                                                                   byte[] rawBytes,
                                                                   String secret,
                                                                   Clock clock,
                                                                   StudioQuackHelper helper) {
        String headerName = vs.header.toLowerCase(Locale.ROOT);
        String sigHeader = headers.get(headerName);
        if (sigHeader == null || sigHeader.isBlank()) {
            return reject(401, "missing_signature", helper);
        }
        if ("slack".equals(vs.signatureHeaderFormat) || "zoom".equals(vs.signatureHeaderFormat)) {
            String tsHeader = vs.timestampHeader.toLowerCase(Locale.ROOT);
            String ts = headers.get(tsHeader);
            if (ts == null || ts.isBlank()) {
                return reject(401, "missing_timestamp", helper);
            }
            if (!isTimestampFresh(ts.trim(), vs.timestampToleranceSeconds, clock)) {
                return reject(401, "timestamp_out_of_tolerance", helper);
            }
            String candidate = sigHeader.trim();
            if (!vs.prefix.isEmpty()) {
                if (!candidate.startsWith(vs.prefix)) {
                    return reject(401, "invalid_signature", helper);
                }
                candidate = candidate.substring(vs.prefix.length());
            }
            byte[] prefixBytes = (vs.timestampPrefix + ts.trim() + vs.timestampSeparator)
                    .getBytes(StandardCharsets.UTF_8);
            byte[] signed = concat(prefixBytes, rawBytes);
            String expected = toHexLower(hmac("HmacSHA256", secret, signed));
            if (!constantTimeEquals(expected, candidate.toLowerCase(Locale.ROOT))) {
                return reject(401, "invalid_signature", helper);
            }
            return null;
        }

        // Stripe ("t=...,v1=...") or Paddle ("ts=...;h1=...") structured header
        String delimiter = "paddle".equals(vs.signatureHeaderFormat) ? ";" : ",";
        String ts = null;
        List<String> signatures = new ArrayList<>();
        for (String part : sigHeader.split(delimiter)) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = part.substring(0, eq).trim();
            String v = part.substring(eq + 1).trim();
            if (vs.timestampKey.equals(k)) {
                ts = v;
            } else if (vs.signatureKey.equals(k) && !v.isEmpty()) {
                signatures.add(v);
            }
        }
        if (ts == null || ts.isEmpty() || signatures.isEmpty()) {
            return reject(401, "invalid_signature", helper);
        }
        if (!isTimestampFresh(ts, vs.timestampToleranceSeconds, clock)) {
            return reject(401, "timestamp_out_of_tolerance", helper);
        }
        byte[] signed = concat((ts + vs.timestampSeparator).getBytes(StandardCharsets.UTF_8), rawBytes);
        String expected = toHexLower(hmac("HmacSHA256", secret, signed));
        boolean matched = false;
        for (String candidate : signatures) {
            if (constantTimeEquals(expected, candidate.toLowerCase(Locale.ROOT))) {
                matched = true;
            }
        }
        if (!matched) {
            return reject(401, "invalid_signature", helper);
        }
        return null;
    }

    private static Map<String, Object> verifyHmacSha1Form(OfficialApiSpec.VerifySpec vs,
                                                          Map<String, Object> wr,
                                                          Map<String, String> headers,
                                                          byte[] rawBytes,
                                                          Object parsedBody,
                                                          String secret,
                                                          Map<String, Object> cfg,
                                                          StudioQuackHelper helper) {
        String headerName = vs.header.toLowerCase(Locale.ROOT);
        String presented = headers.get(headerName);
        String configuredUrl = vs.urlConfigKey.isEmpty() ? "" : DataSupport.cfgStr(cfg, vs.urlConfigKey, "");
        if (presented == null || presented.isBlank()) {
            // If operator did not configure webhook_url AND provider sent no signature header,
            // allow unsigned test deliveries; if webhook_url IS configured, require valid signature.
            if (configuredUrl.isEmpty()) {
                return null;
            }
            return reject(401, "missing_signature", helper);
        }
        String url = !configuredUrl.isEmpty() ? configuredUrl : str(wr.get("url"));
        if (url.isEmpty()) {
            url = str(wr.get("path"));
        }
        StringBuilder data = new StringBuilder(url);
        Map<String, String> sortedParams = new TreeMap<>();
        if (parsedBody instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (e.getKey() != null) {
                    sortedParams.put(String.valueOf(e.getKey()),
                            e.getValue() == null ? "" : String.valueOf(e.getValue()));
                }
            }
        } else {
            String rawText = new String(rawBytes, StandardCharsets.UTF_8);
            for (var e : parseFormUrlEncoded(rawText).entrySet()) {
                sortedParams.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        for (var e : sortedParams.entrySet()) {
            data.append(e.getKey()).append(e.getValue());
        }
        String expected = Base64.getEncoder().encodeToString(
                hmac("HmacSHA1", secret, data.toString().getBytes(StandardCharsets.UTF_8)));
        if (!constantTimeEquals(expected, presented.trim())) {
            return reject(401, "invalid_signature", helper);
        }
        return null;
    }

    private static Map<String, Object> verifyEd25519(OfficialApiSpec.VerifySpec vs,
                                                     Map<String, String> headers,
                                                     byte[] rawBytes,
                                                     String publicKeyHex,
                                                     StudioQuackHelper helper) {
        String sigHex = headers.get(vs.header.toLowerCase(Locale.ROOT));
        String ts = vs.timestampHeader.isEmpty() ? "" : headers.getOrDefault(vs.timestampHeader.toLowerCase(Locale.ROOT), "");
        if (sigHex == null || sigHex.isBlank() || (!vs.timestampHeader.isEmpty() && ts.isBlank())) {
            return reject(401, "missing_signature", helper);
        }
        try {
            byte[] rawKey = decodeHex(publicKeyHex.trim());
            byte[] sigBytes = decodeHex(sigHex.trim());
            if (rawKey.length != 32 || sigBytes.length != 64) {
                return reject(401, "invalid_signature", helper);
            }
            byte[] x509Der = concat(ED25519_X509_PREFIX, rawKey);
            PublicKey pub = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509Der));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(pub);
            if (!ts.isEmpty()) {
                verifier.update(ts.getBytes(StandardCharsets.UTF_8));
            }
            verifier.update(rawBytes);
            if (!verifier.verify(sigBytes)) {
                return reject(401, "invalid_signature", helper);
            }
            return null;
        } catch (Exception e) {
            return reject(401, "invalid_signature", helper);
        }
    }

    private static Map<String, Object> verifyHeaderSecret(OfficialApiSpec.VerifySpec vs,
                                                          Map<String, String> headers,
                                                          String secret,
                                                          Map<String, Object> cfg,
                                                          StudioQuackHelper helper) {
        String headerName = resolveHeaderName(vs, cfg);
        if (headerName.isEmpty()) {
            return null;
        }
        String presented = headers.get(headerName);
        if (presented == null || presented.isBlank()) {
            return reject(401, "missing_auth_header", helper);
        }
        String candidate = presented.trim();
        if (!vs.prefix.isEmpty()) {
            if (!candidate.startsWith(vs.prefix)) {
                return reject(401, "invalid_token", helper);
            }
            candidate = candidate.substring(vs.prefix.length());
        }
        if (!constantTimeEquals(secret, candidate)) {
            return reject(401, "invalid_token", helper);
        }
        return null;
    }

    private static Map<String, Object> verifyBodyFieldSecret(OfficialApiSpec.VerifySpec vs,
                                                             Object parsedBody,
                                                             String secret,
                                                             StudioQuackHelper helper) {
        if (vs.bodyPath.isEmpty()) {
            return null;
        }
        List<Object> values = evalAll(parsedBody, vs.bodyPath);
        if (values.isEmpty()) {
            return reject(401, "invalid_client_state", helper);
        }
        for (Object v : values) {
            String presented = v == null ? "" : String.valueOf(v);
            if (!constantTimeEquals(secret, presented)) {
                return reject(401, "invalid_client_state", helper);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- extraction

    private static List<Map<String, Object>> extractEvents(OfficialApiSpec.ExtractSpec ex,
                                                           String nodeKey,
                                                           Map<String, String> headers,
                                                           Object parsedBody) {
        List<Object> items;
        if (ex != null && !ex.itemsPath.isEmpty()) {
            items = evalAll(parsedBody, ex.itemsPath);
        } else if (parsedBody != null) {
            items = List.of(parsedBody);
        } else {
            items = List.of(Map.of());
        }

        int cap = ex == null ? MAX_EVENTS : Math.min(ex.maxItems, MAX_EVENTS);
        List<Map<String, Object>> events = new ArrayList<>();
        for (Object item : items) {
            if (events.size() >= cap) {
                break;
            }
            if (ex != null && !ex.filterPath.isEmpty()) {
                Object fv = evalFirst(item, ex.filterPath);
                if (fv == null || !ex.filterEquals.equals(String.valueOf(fv))) {
                    continue;
                }
            }
            String idempotencyKey = resolveIdempotencyKey(ex, headers, item, parsedBody);
            String correlationKey = resolveCorrelationKey(ex, item, parsedBody);
            String eventType = resolveEventType(ex, headers, item, parsedBody);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", "ok");
            payload.put("http_status", 200);
            if (nodeKey != null && !nodeKey.isBlank()) {
                payload.put("provider", nodeKey);
            }
            if (!eventType.isEmpty()) {
                payload.put("event", eventType);
            }
            if (ex != null && !ex.fields.isEmpty()) {
                for (var fe : ex.fields.entrySet()) {
                    Object val = evalFirst(item, fe.getValue());
                    if (val == null && item != parsedBody) {
                        val = evalFirst(parsedBody, fe.getValue());
                    }
                    if (val != null) {
                        payload.put(fe.getKey(), val);
                    }
                }
            }
            payload.put("data", item);

            Map<String, Object> event = new LinkedHashMap<>();
            if (!idempotencyKey.isEmpty()) {
                event.put("idempotencyKey", idempotencyKey);
            }
            if (!correlationKey.isEmpty()) {
                event.put("correlationKey", correlationKey);
            }
            event.put("payload", payload);
            events.add(event);
        }
        return events;
    }

    private static String resolveIdempotencyKey(OfficialApiSpec.ExtractSpec ex,
                                                Map<String, String> headers,
                                                Object item,
                                                Object root) {
        if (ex != null) {
            if (!ex.idempotencyHeader.isEmpty()) {
                String hv = headers.get(ex.idempotencyHeader.toLowerCase(Locale.ROOT));
                if (hv != null && !hv.isBlank()) {
                    return hv.trim();
                }
            }
            if (!ex.idempotencyPath.isEmpty()) {
                String v = firstString(item, root, ex.idempotencyPath);
                if (!v.isEmpty()) {
                    return v;
                }
            }
            for (String p : ex.fallbackIdempotencyPaths) {
                String v = firstString(item, root, p);
                if (!v.isEmpty()) {
                    return v;
                }
            }
            return "";
        }
        // Generic fallback: look for common delivery/event ID headers or fields.
        for (String h : List.of("x-request-id", "x-event-id", "webhook-id", "x-webhook-id")) {
            String hv = headers.get(h);
            if (hv != null && !hv.isBlank()) {
                return hv.trim();
            }
        }
        for (String p : List.of("id", "event_id", "eventId", "delivery_id", "uuid")) {
            String v = firstString(item, root, p);
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static String resolveCorrelationKey(OfficialApiSpec.ExtractSpec ex, Object item, Object root) {
        if (ex == null) {
            return "";
        }
        if (!ex.correlationKeyPath.isEmpty()) {
            String v = firstString(item, root, ex.correlationKeyPath);
            if (!v.isEmpty()) {
                return v;
            }
        }
        for (String p : ex.fallbackCorrelationKeyPaths) {
            String v = firstString(item, root, p);
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static String resolveEventType(OfficialApiSpec.ExtractSpec ex,
                                           Map<String, String> headers,
                                           Object item,
                                           Object root) {
        if (ex == null) {
            for (String p : List.of("event", "type", "event_type", "eventType")) {
                String v = firstString(item, root, p);
                if (!v.isEmpty()) {
                    return v;
                }
            }
            return "";
        }
        if (!ex.eventTypeHeader.isEmpty()) {
            String hv = headers.get(ex.eventTypeHeader.toLowerCase(Locale.ROOT));
            if (hv != null && !hv.isBlank()) {
                return hv.trim();
            }
        }
        if (!ex.eventTypePath.isEmpty()) {
            return firstString(item, root, ex.eventTypePath);
        }
        return "";
    }

    // ---------------------------------------------------------------- send-and-wait Phase 1

    /**
     * Resolve the outbound {@code correlationKey} for Phase 1 of {@code send-and-wait}
     * from input/config fields or the outbound HTTP response {@code output.data}.
     */
    public static String resolveSendAndWaitCorrelationKey(OfficialApiSpec.SendAndWaitSpec sw,
                                                          Map<String, Object> bodySrc,
                                                          Map<String, Object> cfg,
                                                          Map<String, Object> outboundResult) {
        if (sw == null || sw.correlationKeyFrom.isEmpty()) {
            return "";
        }
        Object outputPort = outboundResult == null ? null : outboundResult.get("output");
        Object responseData = outputPort instanceof Map<?, ?> om ? om.get("data") : null;
        for (String candidate : sw.correlationKeyFrom) {
            if (candidate.startsWith("data.")) {
                String sub = candidate.substring("data.".length());
                String v = scalarString(evalFirst(responseData, sub));
                if (!v.isEmpty()) {
                    return v;
                }
            } else {
                String v = scalarString(evalFirst(bodySrc, candidate));
                if (v.isEmpty()) {
                    v = scalarString(evalFirst(cfg, candidate));
                }
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- path evaluator

    /**
     * Evaluate a path supporting dotted keys, numeric indices ({@code [0]}), wildcards ({@code [*]}),
     * and equality filters ({@code [?field==value]}). Returns all matched values in order.
     */
    public static List<Object> evalAll(Object root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return List.of();
        }
        List<Object> current = new ArrayList<>();
        current.add(root);
        int i = 0;
        int len = path.length();
        while (i < len && !current.isEmpty()) {
            while (i < len && path.charAt(i) == '.') {
                i++;
            }
            if (i >= len) {
                break;
            }
            if (path.charAt(i) == '[') {
                int end = path.indexOf(']', i + 1);
                if (end < 0) {
                    return List.of();
                }
                String expr = path.substring(i + 1, end).trim();
                List<Object> next = new ArrayList<>();
                if ("*".equals(expr)) {
                    for (Object obj : current) {
                        if (obj instanceof List<?> list) {
                            next.addAll(list);
                        }
                    }
                } else if (expr.startsWith("?")) {
                    String cond = expr.substring(1).trim();
                    int eq = cond.indexOf("==");
                    if (eq > 0) {
                        String fk = cond.substring(0, eq).trim();
                        String fv = stripQuotes(cond.substring(eq + 2).trim());
                        for (Object obj : current) {
                            if (obj instanceof List<?> list) {
                                for (Object elem : list) {
                                    Object actual = evalFirst(elem, fk);
                                    if (actual != null && fv.equals(String.valueOf(actual))) {
                                        next.add(elem);
                                    }
                                }
                            } else if (obj instanceof Map<?, ?>) {
                                Object actual = evalFirst(obj, fk);
                                if (actual != null && fv.equals(String.valueOf(actual))) {
                                    next.add(obj);
                                }
                            }
                        }
                    }
                } else {
                    try {
                        int idx = Integer.parseInt(expr);
                        for (Object obj : current) {
                            if (obj instanceof List<?> list && idx >= 0 && idx < list.size()) {
                                next.add(list.get(idx));
                            }
                        }
                    } catch (NumberFormatException ignored) {
                        return List.of();
                    }
                }
                current = next;
                i = end + 1;
            } else {
                int start = i;
                while (i < len && path.charAt(i) != '.' && path.charAt(i) != '[') {
                    i++;
                }
                String key = path.substring(start, i);
                List<Object> next = new ArrayList<>();
                for (Object obj : current) {
                    if (obj instanceof Map<?, ?> m && m.containsKey(key) && m.get(key) != null) {
                        next.add(m.get(key));
                    }
                }
                current = next;
            }
        }
        return current;
    }

    /** First value matched by {@link #evalAll}, or {@code null}. */
    public static Object evalFirst(Object root, String path) {
        List<Object> all = evalAll(root, path);
        return all.isEmpty() ? null : all.get(0);
    }

    // ---------------------------------------------------------------- outcome envelopes

    private static Map<String, Object> challenge(String body, int status, String contentType,
                                                 Map<String, String> headers, StudioQuackHelper helper) {
        if (helper != null) {
            return helper.respondWebhookChallenge(body, status, contentType, headers);
        }
        Map<String, Object> ch = new LinkedHashMap<>();
        ch.put("status", status > 0 ? status : 200);
        ch.put("contentType", contentType == null || contentType.isBlank() ? "text/plain" : contentType);
        ch.put("body", body == null ? "" : body);
        if (headers != null && !headers.isEmpty()) {
            ch.put("headers", new LinkedHashMap<>(headers));
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("__sq_webhook_challenge", ch);
        return envelope;
    }

    private static Map<String, Object> emitEvents(List<Map<String, Object>> events, StudioQuackHelper helper) {
        if (helper != null) {
            return helper.emitWebhookEvents(events);
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("__sq_webhook_events", events == null ? List.of() : events);
        return envelope;
    }

    private static Map<String, Object> reject(int status, String reason, StudioQuackHelper helper) {
        if (helper != null) {
            return helper.rejectWebhook(status, reason);
        }
        Map<String, Object> rej = new LinkedHashMap<>();
        rej.put("status", status > 0 ? status : 401);
        rej.put("reason", reason == null || reason.isBlank() ? "unauthorized" : reason);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("__sq_webhook_reject", rej);
        return envelope;
    }

    // ---------------------------------------------------------------- crypto & request helpers

    private static boolean isTimestampFresh(String tsStr, int toleranceSeconds, Clock clock) {
        if (toleranceSeconds <= 0) {
            return true;
        }
        try {
            long ts = Long.parseLong(tsStr);
            long now = clock.instant().getEpochSecond();
            return Math.abs(now - ts) <= toleranceSeconds;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static byte[] hmac(String algorithm, String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return mac.doFinal(data == null ? new byte[0] : data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed: " + algorithm, e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String resolveHeaderName(OfficialApiSpec.VerifySpec vs, Map<String, Object> cfg) {
        if (!vs.headerConfigKey.isEmpty()) {
            String fromCfg = DataSupport.cfgStr(cfg, vs.headerConfigKey, "");
            if (!fromCfg.isEmpty()) {
                return fromCfg.toLowerCase(Locale.ROOT);
            }
        }
        return vs.header.toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> lowerCaseHeaders(Object raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    out.put(String.valueOf(e.getKey()).trim().toLowerCase(Locale.ROOT),
                            String.valueOf(e.getValue()));
                }
            }
        }
        return out;
    }

    private static Map<String, String> extractQueryMap(Map<String, Object> wr) {
        Map<String, String> out = new LinkedHashMap<>();
        Object q = wr.get("query");
        if (q instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }
        Object qm = wr.get("queryMulti");
        if (qm instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String k = String.valueOf(e.getKey());
                if (!out.containsKey(k) && e.getValue() instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
                    out.put(k, String.valueOf(list.get(0)));
                }
            }
        }
        return out;
    }

    private static byte[] extractRawBodyBytes(Map<String, Object> wr) {
        String b64 = str(wr.get("rawBodyBase64"));
        if (!b64.isEmpty()) {
            try {
                return Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        Object rawObj = wr.get("rawBody");
        String raw = rawObj == null ? "" : String.valueOf(rawObj);
        String enc = str(wr.get("rawBodyEncoding"));
        if ("base64".equalsIgnoreCase(enc) && !raw.isEmpty()) {
            try {
                return Base64.getDecoder().decode(raw);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    private static Object extractParsedBody(Map<String, Object> wr, byte[] rawBytes, String contentType) {
        Object body = wr.get("body");
        if (body != null) {
            return body;
        }
        if (rawBytes.length == 0) {
            return null;
        }
        String text = new String(rawBytes, StandardCharsets.UTF_8);
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/x-www-form-urlencoded")) {
            return parseFormUrlEncoded(text);
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return Json.parse(trimmed);
            } catch (Exception ignored) {
                return text;
            }
        }
        return text;
    }

    private static Map<String, Object> parseFormUrlEncoded(String raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            try {
                k = URLDecoder.decode(k, StandardCharsets.UTF_8);
                v = URLDecoder.decode(v, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // keep literal
            }
            map.put(k, v);
        }
        return map;
    }

    private static String firstString(Object item, Object root, String path) {
        String v = scalarString(evalFirst(item, path));
        if (!v.isEmpty()) {
            return v;
        }
        return item == root ? "" : scalarString(evalFirst(root, path));
    }

    private static String scalarString(Object v) {
        if (v == null || v instanceof Map<?, ?> || v instanceof List<?>) {
            return "";
        }
        return String.valueOf(v).trim();
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String toHexLower(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX_LOWER[v >>> 4];
            out[i * 2 + 1] = HEX_LOWER[v & 0x0F];
        }
        return new String(out);
    }

    private static byte[] decodeHex(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("invalid hex");
        }
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("invalid hex char");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
