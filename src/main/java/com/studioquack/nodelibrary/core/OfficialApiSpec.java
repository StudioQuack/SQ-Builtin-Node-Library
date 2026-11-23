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

    private OfficialApiSpec(String key, String baseUrl, String authType, String authHeader,
                            String authPrefix, String authConfigKey, String authUserKey,
                            String secretKeyConfigKey, String basicKeyPosition,
                            String timeoutConfigKey, int defaultTimeout,
                            Map<String, String> extraHeaders, SigV4Block sigv4,
                            Map<String, Operation> operations,
                            Map<String, String> placeholderDefaults,
                            boolean operationStrict) {
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
                strictOp);
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

        Operation(String name, String kind, String method, String path,
                  List<String> required, List<String> queryFrom, List<String> bodyFrom,
                  String contentType, String bodyFormat, String bodyParam, boolean fileBytes, String baseUrl,
                  Map<String, String> extraHeaders, StreamBlock stream,
                  Map<String, String> bodyMap, Map<String, String> queryMap, MimeBlock mime,
                  Map<String, Object> bodyDefaults) {
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
                    objectMapRaw(m.get("bodyDefaults")));
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
