package com.studioquack.nodelibrary.core;

import studioquack.Json;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Machine-readable HTTP surface for a planned provider node
 * ({@code nodes/<key>/api_spec.json}).
 *
 * <p>Custom Java handlers (OpenAI, AI Agent, Web Browser, …) do not use this.
 * Official-API nodes that only wrap REST/GraphQL do.
 */
public final class OfficialApiSpec {

    static final Set<String> BODY_FORMATS = Set.of("json", "form", "multipart", "raw");
    static final Set<String> BASIC_KEY_POSITIONS = Set.of("username", "password");

    public final String key;
    public final String baseUrl;
    public final String authType;
    public final String authHeader;
    public final String authPrefix;
    public final String authConfigKey;
    public final String authUserKey;
    /**
     * Config shortCode for the AWS secret access key when {@code auth.type} is
     * {@code aws-sigv4}. Empty for every other auth type. Defaults to
     * {@code secret_access_key} (the AWS-node convention) when the spec omits it.
     */
    public final String secretKeyConfigKey;
    /**
     * Which side of the Basic pair {@link #authConfigKey} occupies:
     * {@code password} (default, today's {@code user:secret}) or {@code username}
     * ({@code secret:} with an empty password — EasyPost / Onfleet / PrestaShop).
     */
    public final String basicKeyPosition;
    public final String timeoutConfigKey;
    public final int defaultTimeout;
    /** Top-level extra headers; op-level and {@code stream.extraHeaders} overlay later. */
    public final Map<String, String> extraHeaders;
    /** Null when the spec has no {@code sigv4} object. */
    public final SigV4Block sigv4;
    public final Map<String, Operation> operations;
    /**
     * {@code baseUrl} / {@code path} placeholder name → default when INPUT and config both omit the key
     * (e.g. CloudConvert {@code {sandbox}} → {@code api}).
     */
    public final Map<String, String> placeholderDefaults;
    /**
     * When true, {@link #operation(String)} matches names case-sensitively only (bogus casing → unknown op).
     * Default false preserves legacy case-insensitive matching.
     */
    public final boolean operationStrict;
    /** Optional library-internal inbound webhook & send-and-wait contract. Never read by the daemon. */
    public final IngressSpec ingress;

    private OfficialApiSpec(String key, String baseUrl, String authType, String authHeader,
                            String authPrefix, String authConfigKey, String authUserKey,
                            String secretKeyConfigKey, String basicKeyPosition,
                            String timeoutConfigKey, int defaultTimeout,
                            Map<String, String> extraHeaders, SigV4Block sigv4,
                            Map<String, Operation> operations,
                            Map<String, String> placeholderDefaults,
                            boolean operationStrict,
                            IngressSpec ingress) {
        this.key = key;
        this.baseUrl = baseUrl;
        this.authType = authType;
        this.authHeader = authHeader;
        this.authPrefix = authPrefix;
        this.authConfigKey = authConfigKey;
        this.authUserKey = authUserKey;
        this.secretKeyConfigKey = secretKeyConfigKey;
        this.basicKeyPosition = basicKeyPosition;
        this.timeoutConfigKey = timeoutConfigKey;
        this.defaultTimeout = defaultTimeout;
        this.extraHeaders = extraHeaders;
        this.sigv4 = sigv4;
        this.operations = operations;
        this.placeholderDefaults = placeholderDefaults;
        this.operationStrict = operationStrict;
        this.ingress = ingress;
    }

    /** Load {@code nodes/<key>/api_spec.json} from the classpath, or {@code null}. */
    public static OfficialApiSpec load(String nodeKey) {
        if (nodeKey == null || nodeKey.isBlank()) {
            return null;
        }
        String path = "nodes/" + nodeKey + "/api_spec.json";
        try (InputStream in = OfficialApiSpec.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Object parsed = Json.parse(raw);
            if (!(parsed instanceof Map<?, ?>)) {
                return null;
            }
            return fromMap(nodeKey, Json.asMap(parsed));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static OfficialApiSpec fromMap(String key, Map<String, Object> root) {
        String base = str(root.get("baseUrl"));
        Map<String, Object> auth = root.get("auth") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Operation> ops = new LinkedHashMap<>();
        Object opsRaw = root.get("operations");
        if (opsRaw instanceof Map<?, ?> om) {
            for (var e : om.entrySet()) {
                if (e.getKey() == null || !(e.getValue() instanceof Map<?, ?> vm)) {
                    continue;
                }
                String name = String.valueOf(e.getKey()).trim();
                if (!name.isEmpty()) {
                    ops.put(name, Operation.fromMap(name, (Map<String, Object>) vm));
                }
            }
        }
        int timeout = toInt(root.get("defaultTimeout"), 25);
        String authType = str(auth.get("type")).toLowerCase(Locale.ROOT);
        String secretKey = str(auth.get("secretKeyConfigKey"));
        if (secretKey.isEmpty() && "aws-sigv4".equals(authType)) {
            secretKey = "secret_access_key";
        }
        String basicPos = str(auth.get("basicKeyPosition")).toLowerCase(Locale.ROOT);
        if (!BASIC_KEY_POSITIONS.contains(basicPos)) {
            basicPos = "password";
        }
        boolean strictOp = truthy(root.get("operationStrict"));
        return new OfficialApiSpec(
                key,
                base,
                authType,
                firstNonBlank(str(auth.get("header")), "Authorization"),
                // Trailing space is the separator ("Bearer ", "Zoho-oauthtoken "). str() would delete it.
                strKeepTrailing(auth.get("prefix")),
                firstNonBlank(str(auth.get("configKey")), "api_key"),
                str(auth.get("userKey")),
                secretKey,
                basicPos,
                firstNonBlank(str(root.get("timeoutConfigKey")), "timeout_seconds"),
                timeout > 0 ? timeout : 25,
                extraHeadersMap(root.get("extraHeaders")),
                SigV4Block.from(root.get("sigv4")),
                ops,
                extraHeadersMap(root.get("placeholderDefaults")),
                strictOp,
                IngressSpec.from(root.get("ingress")));
    }

    public Operation operation(String name) {
        if (name == null) {
            return null;
        }
        Operation op = operations.get(name);
        if (op != null) {
            return op;
        }
        if (operationStrict) {
            return null;
        }
        for (var e : operations.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static final class Operation {
        public final String name;
        public final String kind; // http | ingress
        public final String method;
        public final String path;
        public final List<String> required;
        public final List<String> queryFrom;
        public final List<String> bodyFrom;
        public final String contentType;
        /**
         * {@code json} (default) | {@code form} | {@code multipart} | {@code raw}.
         * Unknown values collapse to {@code json} so existing specs stay byte-identical.
         */
        public final String bodyFormat;
        /** Input key whose string is the verbatim body when {@link #bodyFormat} is {@code raw}. */
        public final String bodyParam;
        /**
         * Raw {@link #bodyParam} is a platform file. A string must be a sandbox path under
         * {@code /sandbox/files}; inline text is rejected. ContentBlock maps stay on the file-part path.
         */
        public final boolean fileBytes;
        /**
         * Per-operation host override. Blank keeps the spec-level {@code baseUrl}.
         * Placeholders interpolate the same way as the top-level baseUrl.
         */
        public final String baseUrl;
        /** Op-level extra headers; override same-named top-level entries. */
        public final Map<String, String> extraHeaders;
        /** Null when the op has no stream block or the format is unknown/missing. */
        public final StreamBlock stream;
        /**
         * Input key → wire location for a {@code bodyFrom} field: a dotted path in a JSON body
         * ({@code generationConfig.temperature}), or the literal field / part name in a
         * form or multipart body ({@code metadata[order_id]}). Unmapped keys keep their name.
         */
        public final Map<String, String> bodyMap;
        /** Input key → wire name for a {@code queryFrom} parameter. Unmapped keys keep their name. */
        public final Map<String, String> queryMap;
        /** Null unless the op builds an RFC 5322 message into a body field. */
        public final MimeBlock mime;
        /**
         * Wire-body field → default value when INPUT/config omit the key or send a blank string.
         * Applied after config underlay, before {@link OfficialApiBody#encode}; nested maps and
         * lists are valid. Input values always win when non-blank.
         */
        public final Map<String, Object> bodyDefaults;
        /** Optional per-operation ingress override (falls back to top-level {@link OfficialApiSpec#ingress}). */
        public final IngressSpec ingress;

        Operation(String name, String kind, String method, String path,
                  List<String> required, List<String> queryFrom, List<String> bodyFrom,
                  String contentType, String bodyFormat, String bodyParam, boolean fileBytes, String baseUrl,
                  Map<String, String> extraHeaders, StreamBlock stream,
                  Map<String, String> bodyMap, Map<String, String> queryMap, MimeBlock mime,
                  Map<String, Object> bodyDefaults, IngressSpec ingress) {
            this.name = name;
            this.kind = kind;
            this.method = method;
            this.path = path;
            this.required = required;
            this.queryFrom = queryFrom;
            this.bodyFrom = bodyFrom;
            this.contentType = contentType;
            this.bodyFormat = bodyFormat;
            this.bodyParam = bodyParam;
            this.fileBytes = fileBytes;
            this.baseUrl = baseUrl;
            this.extraHeaders = extraHeaders;
            this.stream = stream;
            this.bodyMap = bodyMap;
            this.queryMap = queryMap;
            this.mime = mime;
            this.bodyDefaults = bodyDefaults;
            this.ingress = ingress;
        }

        /** Wire name of an input key in this op's body. */
        public String bodyName(String key) {
            String mapped = bodyMap.get(key);
            return mapped == null || mapped.isBlank() ? key : mapped;
        }

        /** Wire name of an input key in this op's query string. */
        public String queryName(String key) {
            String mapped = queryMap.get(key);
            return mapped == null || mapped.isBlank() ? key : mapped;
        }

        static Operation fromMap(String name, Map<String, Object> m) {
            String format = str(m.get("bodyFormat")).toLowerCase(Locale.ROOT);
            if (!BODY_FORMATS.contains(format)) {
                format = "json";
            }
            String bodyParam = m.get("bodyParam") instanceof String s ? s.trim() : "";
            return new Operation(
                    name,
                    firstNonBlank(str(m.get("kind")), "http").toLowerCase(Locale.ROOT),
                    firstNonBlank(str(m.get("method")), "GET").toUpperCase(Locale.ROOT),
                    str(m.get("path")),
                    stringList(m.get("required")),
                    stringList(m.get("queryFrom")),
                    stringList(m.get("bodyFrom")),
                    firstNonBlank(str(m.get("contentType")), "application/json"),
                    format,
                    bodyParam,
                    Boolean.TRUE.equals(m.get("fileBytes")),
                    str(m.get("baseUrl")),
                    extraHeadersMap(m.get("extraHeaders")),
                    StreamBlock.from(m.get("stream")),
                    extraHeadersMap(m.get("bodyMap")),
                    extraHeadersMap(m.get("queryMap")),
                    MimeBlock.from(m.get("mime")),
                    objectMapRaw(m.get("bodyDefaults")),
                    IngressSpec.from(m.get("ingress")));
        }
    }

    /**
     * Library-internal webhook ingress &amp; send-and-wait specification ({@code api_spec.json#ingress}).
     * Never read by the engine daemon — lives exclusively inside {@code SQ-Builtin-Node-Library}.
     */
    public static final class IngressSpec {
        public static final Set<String> HANDSHAKE_TYPES = Set.of(
                "none", "query_challenge", "json_challenge", "header_echo",
                "zoom_url_validation", "header_value_challenge");
        public static final Set<String> VERIFY_TYPES = Set.of(
                "none", "hmac_sha256_hex", "hmac_sha256_base64", "hmac_sha256_timestamped",
                "hmac_sha1_form", "ed25519", "header_secret", "body_field_secret");

        public final HandshakeSpec handshake;
        public final VerifySpec verify;
        public final ExtractSpec extract;
        public final SendAndWaitSpec sendAndWait;

        public IngressSpec(HandshakeSpec handshake, VerifySpec verify,
                           ExtractSpec extract, SendAndWaitSpec sendAndWait) {
            this.handshake = handshake;
            this.verify = verify;
            this.extract = extract;
            this.sendAndWait = sendAndWait;
        }

        public static IngressSpec from(Object raw) {
            if (!(raw instanceof Map<?, ?> m)) {
                return null;
            }
            return new IngressSpec(
                    HandshakeSpec.from(m.get("handshake")),
                    VerifySpec.from(m.get("verify")),
                    ExtractSpec.from(m.get("extract")),
                    SendAndWaitSpec.from(m.get("sendAndWait")));
        }

        /** All config parameter shortCodes referenced by this ingress spec (for contract audits). */
        public List<String> referencedConfigKeys() {
            List<String> keys = new ArrayList<>();
            if (handshake != null) {
                if (!handshake.tokenConfigKey.isBlank()) {
                    keys.add(handshake.tokenConfigKey);
                }
                if (!handshake.secretConfigKey.isBlank()) {
                    keys.add(handshake.secretConfigKey);
                }
            }
            if (verify != null) {
                if (!verify.secretConfigKey.isBlank()) {
                    keys.add(verify.secretConfigKey);
                }
                if (!verify.headerConfigKey.isBlank()) {
                    keys.add(verify.headerConfigKey);
                }
                if (!verify.urlConfigKey.isBlank()) {
                    keys.add(verify.urlConfigKey);
                }
            }
            return keys;
        }
    }

    /** Inbound webhook verification handshake configuration. */
    public static final class HandshakeSpec {
        public final String type;
        public final String method;
        public final String modeParam;
        public final String modeValue;
        public final String tokenParam;
        public final String tokenConfigKey;
        public final String challengeParam;
        public final String typePath;
        public final String typeValue;
        public final String challengePath;
        public final String responseKey;
        public final String staticResponseBody;
        public final String headerName;
        public final String headerValue;
        public final String responseHeader;
        public final String responseJsonKey;
        public final String secretConfigKey;
        public final int responseStatus;
        public final String responseContentType;
        public final boolean verifyBeforeHandshake;

        public HandshakeSpec(String type, String method, String modeParam, String modeValue,
                             String tokenParam, String tokenConfigKey, String challengeParam,
                             String typePath, String typeValue, String challengePath, String responseKey,
                             String staticResponseBody, String headerName, String headerValue,
                             String responseHeader, String responseJsonKey, String secretConfigKey,
                             int responseStatus, String responseContentType, boolean verifyBeforeHandshake) {
            this.type = type;
            this.method = method;
            this.modeParam = modeParam;
            this.modeValue = modeValue;
            this.tokenParam = tokenParam;
            this.tokenConfigKey = tokenConfigKey;
            this.challengeParam = challengeParam;
            this.typePath = typePath;
            this.typeValue = typeValue;
            this.challengePath = challengePath;
            this.responseKey = responseKey;
            this.staticResponseBody = staticResponseBody;
            this.headerName = headerName;
            this.headerValue = headerValue;
            this.responseHeader = responseHeader;
            this.responseJsonKey = responseJsonKey;
            this.secretConfigKey = secretConfigKey;
            this.responseStatus = responseStatus;
            this.responseContentType = responseContentType;
            this.verifyBeforeHandshake = verifyBeforeHandshake;
        }

        static HandshakeSpec from(Object raw) {
            if (!(raw instanceof Map<?, ?> m)) {
                return null;
            }
            String type = firstNonBlank(str(m.get("type")), "none").toLowerCase(Locale.ROOT);
            String defaultMethod = switch (type) {
                case "query_challenge" -> "GET";
                case "json_challenge", "zoom_url_validation" -> "POST";
                default -> "ANY";
            };
            String defaultCt = ("json_challenge".equals(type) || "zoom_url_validation".equals(type)
                    || !str(m.get("responseJsonKey")).isBlank() || !str(m.get("staticResponseBody")).isBlank())
                    ? "application/json" : "text/plain";
            int status = toInt(m.get("responseStatus"), 200);
            return new HandshakeSpec(
                    type,
                    firstNonBlank(str(m.get("method")), defaultMethod).toUpperCase(Locale.ROOT),
                    str(m.get("modeParam")),
                    str(m.get("modeValue")),
                    str(m.get("tokenParam")),
                    str(m.get("tokenConfigKey")),
                    str(m.get("challengeParam")),
                    str(m.get("typePath")),
                    str(m.get("typeValue")),
                    str(m.get("challengePath")),
                    str(m.get("responseKey")),
                    str(m.get("staticResponseBody")),
                    str(m.get("headerName")),
                    str(m.get("headerValue")),
                    str(m.get("responseHeader")),
                    str(m.get("responseJsonKey")),
                    str(m.get("secretConfigKey")),
                    status > 0 ? status : 200,
                    firstNonBlank(str(m.get("responseContentType")), defaultCt),
                    truthy(m.get("verifyBeforeHandshake")));
        }
    }

    /** Inbound webhook cryptographic signature or shared-secret verification configuration. */
    public static final class VerifySpec {
        public final String type;
        public final String header;
        public final String headerConfigKey;
        public final String prefix;
        public final String secretConfigKey;
        public final String timestampHeader;
        public final String timestampPrefix;
        public final String signatureHeaderFormat;
        public final String timestampKey;
        public final String signatureKey;
        public final String timestampSeparator;
        public final int timestampToleranceSeconds;
        public final String bodyPath;
        public final String urlConfigKey;

        public VerifySpec(String type, String header, String headerConfigKey, String prefix,
                          String secretConfigKey, String timestampHeader, String timestampPrefix,
                          String signatureHeaderFormat, String timestampKey, String signatureKey,
                          String timestampSeparator, int timestampToleranceSeconds,
                          String bodyPath, String urlConfigKey) {
            this.type = type;
            this.header = header;
            this.headerConfigKey = headerConfigKey;
            this.prefix = prefix;
            this.secretConfigKey = secretConfigKey;
            this.timestampHeader = timestampHeader;
            this.timestampPrefix = timestampPrefix;
            this.signatureHeaderFormat = signatureHeaderFormat;
            this.timestampKey = timestampKey;
            this.signatureKey = signatureKey;
            this.timestampSeparator = timestampSeparator;
            this.timestampToleranceSeconds = timestampToleranceSeconds;
            this.bodyPath = bodyPath;
            this.urlConfigKey = urlConfigKey;
        }

        static VerifySpec from(Object raw) {
            if (!(raw instanceof Map<?, ?> m)) {
                return null;
            }
            String type = firstNonBlank(str(m.get("type")), "none").toLowerCase(Locale.ROOT);
            String tsHeader = str(m.get("timestampHeader"));
            String format = str(m.get("signatureHeaderFormat")).toLowerCase(Locale.ROOT);
            if (format.isEmpty() && "hmac_sha256_timestamped".equals(type)) {
                format = tsHeader.isEmpty() ? "stripe" : "slack";
            }
            String defaultSep = ("paddle".equals(format) || "slack".equals(format) || "zoom".equals(format))
                    ? ":" : ".";
            int tol = m.containsKey("timestampToleranceSeconds")
                    ? toInt(m.get("timestampToleranceSeconds"), 300) : 300;
            return new VerifySpec(
                    type,
                    str(m.get("header")),
                    str(m.get("headerConfigKey")),
                    strKeepTrailing(m.get("prefix")),
                    str(m.get("secretConfigKey")),
                    tsHeader,
                    strKeepTrailing(m.get("timestampPrefix")),
                    format,
                    firstNonBlank(str(m.get("timestampKey")), "paddle".equals(format) ? "ts" : "t"),
                    firstNonBlank(str(m.get("signatureKey")), "paddle".equals(format) ? "h1" : "v1"),
                    firstNonBlank(str(m.get("timestampSeparator")), defaultSep),
                    Math.max(0, tol),
                    str(m.get("bodyPath")),
                    str(m.get("urlConfigKey")));
        }
    }

    /** Path-based inbound webhook fan-out &amp; normalized event extraction configuration. */
    public static final class ExtractSpec {
        public final String itemsPath;
        public final String filterPath;
        public final String filterEquals;
        public final String idempotencyHeader;
        public final String idempotencyPath;
        public final List<String> fallbackIdempotencyPaths;
        public final String correlationKeyPath;
        public final List<String> fallbackCorrelationKeyPaths;
        public final String eventTypeHeader;
        public final String eventTypePath;
        public final Map<String, String> fields;
        public final int maxItems;

        public ExtractSpec(String itemsPath, String filterPath, String filterEquals,
                           String idempotencyHeader, String idempotencyPath,
                           List<String> fallbackIdempotencyPaths, String correlationKeyPath,
                           List<String> fallbackCorrelationKeyPaths, String eventTypeHeader,
                           String eventTypePath, Map<String, String> fields, int maxItems) {
            this.itemsPath = itemsPath;
            this.filterPath = filterPath;
            this.filterEquals = filterEquals;
            this.idempotencyHeader = idempotencyHeader;
            this.idempotencyPath = idempotencyPath;
            this.fallbackIdempotencyPaths = fallbackIdempotencyPaths;
            this.correlationKeyPath = correlationKeyPath;
            this.fallbackCorrelationKeyPaths = fallbackCorrelationKeyPaths;
            this.eventTypeHeader = eventTypeHeader;
            this.eventTypePath = eventTypePath;
            this.fields = fields;
            this.maxItems = maxItems;
        }

        static ExtractSpec from(Object raw) {
            if (!(raw instanceof Map<?, ?> m)) {
                return null;
            }
            int max = toInt(m.get("maxItems"), 100);
            if (max <= 0 || max > 100) {
                max = 100;
            }
            return new ExtractSpec(
                    str(m.get("itemsPath")),
                    str(m.get("filterPath")),
                    str(m.get("filterEquals")),
                    str(m.get("idempotencyHeader")),
                    str(m.get("idempotencyPath")),
                    stringList(m.get("fallbackIdempotencyPaths")),
                    str(m.get("correlationKeyPath")),
                    stringList(m.get("fallbackCorrelationKeyPaths")),
                    str(m.get("eventTypeHeader")),
                    str(m.get("eventTypePath")),
                    stringMap(m.get("fields")),
                    max);
        }
    }

    /** Phase-1 outbound delegation and correlation-key extraction for {@code send-and-wait}. */
    public static final class SendAndWaitSpec {
        public final String sendOperation;
        public final String fallbackSendOperation;
        public final String fallbackWhenPresent;
        public final String fallbackWhenBlank;
        public final List<String> correlationKeyFrom;

        public SendAndWaitSpec(String sendOperation, String fallbackSendOperation,
                               String fallbackWhenPresent, String fallbackWhenBlank,
                               List<String> correlationKeyFrom) {
            this.sendOperation = sendOperation;
            this.fallbackSendOperation = fallbackSendOperation;
            this.fallbackWhenPresent = fallbackWhenPresent;
            this.fallbackWhenBlank = fallbackWhenBlank;
            this.correlationKeyFrom = correlationKeyFrom;
        }

        static SendAndWaitSpec from(Object raw) {
            if (!(raw instanceof Map<?, ?> m)) {
                return null;
            }
            String sendOp = str(m.get("sendOperation"));
            if (sendOp.isEmpty()) {
                return null;
            }
            return new SendAndWaitSpec(
                    sendOp,
                    str(m.get("fallbackSendOperation")),
                    str(m.get("fallbackWhenPresent")),
                    str(m.get("fallbackWhenBlank")),
                    stringList(m.get("correlationKeyFrom")));
        }
    }

    /**
     * Builds an RFC 5322 message from the input fields in {@link #FIELDS} and writes it,
     * encoded, into body field {@code target} (Gmail {@code raw}, SES {@code RawMessage.Data}).
     * A non-blank {@code target} value already on the input is sent as-is.
     */
    public static final class MimeBlock {
        /** Input keys the message is built from. */
        public static final List<String> FIELDS = List.of("from", "to", "cc", "bcc", "reply_to",
                "subject", "text", "html", "in_reply_to", "references", "attachments");
        /** {@code base64url} | {@code base64} | {@code none} (the message text itself). */
        static final Set<String> ENCODINGS = Set.of("base64url", "base64", "none");

        public final String target;
        public final String encoding;

        MimeBlock(String target, String encoding) {
            this.target = target;
            this.encoding = encoding;
        }

        static MimeBlock from(Object raw) {
            if (!(raw instanceof Map<?, ?> m)) {
                return null;
            }
            String target = str(m.get("target"));
            if (target.isEmpty()) {
                return null;
            }
            String encoding = str(m.get("encoding")).toLowerCase(Locale.ROOT);
            return new MimeBlock(target, ENCODINGS.contains(encoding) ? encoding : "base64url");
        }
    }

    /**
     * AWS Signature Version 4 parameters. {@code service} is a literal service
     * code ({@code execute-api}, {@code s3}, …); when blank the engine reads
     * {@code serviceConfigKey} from instance config (AWS nodes use {@code service}).
     * Region comes from {@code regionConfigKey} (default {@code region}).
     * {@code sessionTokenConfigKey} is optional; a present config value becomes
     * {@code x-amz-security-token}.
     */
    public static final class SigV4Block {
        public final String service;
        public final String serviceConfigKey;
        public final String regionConfigKey;
        public final String sessionTokenConfigKey;

        SigV4Block(String service, String serviceConfigKey, String regionConfigKey,
                   String sessionTokenConfigKey) {
            this.service = service;
            this.serviceConfigKey = serviceConfigKey;
            this.regionConfigKey = regionConfigKey;
            this.sessionTokenConfigKey = sessionTokenConfigKey;
        }

        static SigV4Block from(Object raw) {
            if (!(raw instanceof Map<?, ?> m)) {
                return null;
            }
            return new SigV4Block(
                    str(m.get("service")),
                    str(m.get("serviceConfigKey")),
                    firstNonBlank(str(m.get("regionConfigKey")), "region"),
                    str(m.get("sessionTokenConfigKey")));
        }
    }

    /**
     * Optional per-operation SSE/NDJSON streaming contract. Unknown or missing
     * {@code format} yields {@code null} (the node stays on the single-shot path).
     */
    public static final class StreamBlock {
        public final String format;
        public final String requestFlag;
        public final Object requestValue;
        /** Alternate path used only when streaming; blank keeps the operation path. */
        public final String path;
        public final Map<String, String> query;
        public final Map<String, String> extraHeaders;
        public final String port;

        StreamBlock(String format, String requestFlag, Object requestValue, String path,
                    Map<String, String> query, Map<String, String> extraHeaders, String port) {
            this.format = format;
            this.requestFlag = requestFlag;
            this.requestValue = requestValue;
            this.path = path;
            this.query = query;
            this.extraHeaders = extraHeaders;
            this.port = port;
        }

        static StreamBlock from(Object raw) {
            if (!(raw instanceof Map<?, ?> m)) {
                return null;
            }
            String format = str(m.get("format")).toLowerCase(Locale.ROOT);
            if (!ProviderStream.knownFormat(format)) {
                return null;
            }
            Object flagVal = m.get("requestValue");
            return new StreamBlock(
                    format,
                    firstNonBlank(str(m.get("requestFlag")), "stream"),
                    flagVal == null ? Boolean.TRUE : flagVal,
                    str(m.get("path")),
                    stringMap(m.get("query")),
                    extraHeadersMap(m.get("extraHeaders")),
                    firstNonBlank(str(m.get("port")), "llm_output"));
        }
    }

    private static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String s = str(item);
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static Map<String, String> stringMap(Object raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String k = String.valueOf(e.getKey()).trim();
                String v = String.valueOf(e.getValue()).trim();
                if (!k.isEmpty() && !v.isEmpty()) {
                    out.put(k, v);
                }
            }
        }
        return out;
    }

    /**
     * Arbitrary JSON object for {@code bodyDefaults}; preserves nested maps/lists and scalars.
     */
    private static Map<String, Object> objectMapRaw(Object raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> m)) {
            return out;
        }
        for (var e : m.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = String.valueOf(e.getKey()).trim();
            if (!k.isEmpty()) {
                out.put(k, e.getValue());
            }
        }
        return out;
    }

    /**
     * Header maps reject nested objects/arrays (malformed) rather than
     * {@code String.valueOf}-ing them into a sent value. Scalars stringify.
     */
    static Map<String, String> extraHeadersMap(Object raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> m)) {
            return out;
        }
        for (var e : m.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue() instanceof Map<?, ?> || e.getValue() instanceof List<?>) {
                continue;
            }
            String k = String.valueOf(e.getKey()).trim();
            String v = String.valueOf(e.getValue()).trim();
            if (!k.isEmpty() && !v.isEmpty()) {
                out.put(k, v);
            }
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** Trim only the start. A trailing space in an auth prefix is the header separator. */
    private static String strKeepTrailing(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? (b == null ? "" : b) : a;
    }

    private static int toInt(Object v, int fallback) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return false;
        }
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return "1".equals(s) || "true".equals(s) || "yes".equals(s);
    }
}
