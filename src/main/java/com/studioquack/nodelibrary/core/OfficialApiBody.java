package com.studioquack.nodelibrary.core;

import studioquack.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-operation body encoding for Official-API nodes.
 *
 * <p>{@code bodyFormat}: {@code json} (default, existing behaviour), {@code form}
 * ({@code application/x-www-form-urlencoded} from {@code bodyFrom} keys),
 * {@code multipart} (generated boundary; a bodyFrom value that is a platform file ContentBlock
 * {@code {mime_type, source, data, name}} — or the legacy {@code {dataBase64|url, fileName,
 * contentType}} map — becomes a file part; a List of file-shaped items becomes one part
 * per item under the same part name), {@code raw} (the
 * {@code bodyParam} input as verbatim bytes; {@code fileBytes} reads a sandbox path instead of
 * inlining that string. A file List unwraps to its first item;
 * {@code contentType} from the op). {@code bodyMap} renames / nests {@code bodyFrom} keys on the
 * wire; {@code mime} builds an RFC 5322 message into one body field first.
 *
 * <p>Never logs the body (payloads may be PHI / file bytes).
 */
final class OfficialApiBody {

    /** Result of encoding. {@code error} set → do not send. */
    record Prepared(byte[] bytes, String contentType, String error) {
        boolean failed() {
            return error != null && !error.isBlank();
        }

        boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }
    }

    private OfficialApiBody() {
        // Utility — never instantiated.
    }

    /**
     * JSON object collected from {@code bodyFrom} (or the whole input when
     * bodyFrom is empty — the historical default). Stream-capable ops then run
     * {@link ProviderRequestMapper}.
     */
    static Map<String, Object> jsonObject(OfficialApiSpec.Operation op, Map<String, Object> in) {
        Map<String, Object> body;
        if (op == null || op.bodyFrom.isEmpty()) {
            body = in == null ? new LinkedHashMap<>() : new LinkedHashMap<>(in);
        } else {
            body = new LinkedHashMap<>();
            for (String k : op.bodyFrom) {
                if (in != null && in.get(k) != null) {
                    // Port schema may type arrays/objects as string; parse JSON text so the
                    // wire body sends a native array/object (e.g. OpenAI `messages`).
                    body.put(k, jsonValue(in.get(k)));
                }
            }
        }
        if (op != null && op.stream != null) {
            // The full source map rides along as gating context: Gemini puts model in the
            // URL path, so the mapper would otherwise never see which model is called.
            body = ProviderRequestMapper.map(op.stream.format, body, in);
        }
        if (op != null && !op.bodyMap.isEmpty()) {
            for (var e : op.bodyMap.entrySet()) {
                if (!body.containsKey(e.getKey()) || e.getKey().equals(e.getValue())) {
                    continue;
                }
                Object value = body.remove(e.getKey());
                if (ROOT.equals(e.getValue())) {
                    if (jsonValue(value) instanceof Map<?, ?> root) {
                        for (var r : root.entrySet()) {
                            body.putIfAbsent(String.valueOf(r.getKey()), r.getValue());
                        }
                    } else {
                        body.put(e.getKey(), value);
                    }
                } else {
                    putPath(body, e.getValue(), value);
                }
            }
        }
        return body;
    }

    /**
     * Cohere's API rejects string-typed sampling knobs (e.g. config UI stores {@code "0.7"}).
     * Coerce known numeric fields to {@link Number} right before JSON write.
     */
    static Map<String, Object> coerceCohereNumericJsonFields(String nodeKey, OfficialApiSpec.Operation op,
                                                           Map<String, Object> body) {
        if (body == null || body.isEmpty() || op == null || nodeKey == null
                || !"cohere".equalsIgnoreCase(nodeKey) || !"chat".equals(op.name)) {
            return body;
        }
        Map<String, Object> out = new LinkedHashMap<>(body);
        coerceDoubleIfString(out, "temperature");
        coerceDoubleIfString(out, "p");
        coerceDoubleIfString(out, "k");
        coerceDoubleIfString(out, "frequency_penalty");
        coerceDoubleIfString(out, "presence_penalty");
        coerceIntIfString(out, "max_tokens");
        coerceLongIfString(out, "seed");
        dropBlankCohereChatSampling(out);
        return out;
    }

    /**
     * OpenAI-compatible chat/audio bodies: config UI and tool schemas often store sampling knobs as
     * {@code ""}; Groq rejects non-numeric {@code temperature} with HTTP 400.
     */
    static Map<String, Object> coerceOpenAiCompatibleNumericFields(String nodeKey, OfficialApiSpec.Operation op,
                                                                   Map<String, Object> body) {
        if (body == null || body.isEmpty() || op == null || !openAiCompatibleNumericCoerce(nodeKey, op)) {
            return body;
        }
        Map<String, Object> out = new LinkedHashMap<>(body);
        for (String key : List.of("temperature", "top_p", "presence_penalty", "frequency_penalty")) {
            coerceDoubleIfString(out, key);
        }
        for (String key : List.of("max_tokens", "max_completion_tokens", "n")) {
            coerceIntIfString(out, key);
        }
        coerceLongIfString(out, "seed");
        dropBlankOpenAiSampling(out);
        return out;
    }

    private static boolean openAiCompatibleNumericCoerce(String nodeKey, OfficialApiSpec.Operation op) {
        if (op.stream != null && "openai-sse".equals(op.stream.format)) {
            return true;
        }
        return "groq".equalsIgnoreCase(nodeKey) && ("chat".equals(op.name) || "stt".equals(op.name));
    }

    private static void dropBlankOpenAiSampling(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        for (String key : List.of("temperature", "top_p", "max_tokens", "max_completion_tokens", "n",
                "presence_penalty", "frequency_penalty", "seed")) {
            Object v = body.get(key);
            if (v instanceof String s && s.isBlank()) {
                body.remove(key);
            }
        }
    }

    /**
     * Config UI stores unset numerics as {@code ""}; Cohere expects float fields omitted or numeric.
     */
    private static void dropBlankCohereChatSampling(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        for (String key : List.of("temperature", "p", "k", "frequency_penalty", "presence_penalty")) {
            Object v = body.get(key);
            if (v instanceof String s && s.isBlank()) {
                body.remove(key);
            }
        }
    }

    private static void coerceDoubleIfString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof String s && !s.isBlank()) {
            try {
                m.put(key, Double.parseDouble(s.trim()));
            } catch (NumberFormatException ignored) {
                // keep string; API will surface the error
            }
        }
    }

    private static void coerceIntIfString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof String s && s.matches("-?\\d{1,9}")) {
            try {
                m.put(key, Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static void coerceLongIfString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof String s && s.matches("-?\\d{1,18}")) {
            try {
                m.put(key, Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** {@code bodyMap} target that makes one input the whole JSON body. */
    static final String ROOT = "$";

    private static final Pattern PATH_SEGMENT = Pattern.compile("([^\\[\\]]*)((?:\\[\\d+])*)");
    private static final Pattern PATH_INDEX = Pattern.compile("\\[(\\d+)]");
    private static final int MAX_PATH_INDEX = 99;

    /** The input mapped to {@code $} when it is a JSON array; such a body is sent as-is. */
    static List<?> rootArray(OfficialApiSpec.Operation op, Map<String, Object> in) {
        if (op == null || in == null) {
            return null;
        }
        for (var e : op.bodyMap.entrySet()) {
            if (ROOT.equals(e.getValue()) && jsonValue(in.get(e.getKey())) instanceof List<?> list) {
                return list;
            }
        }
        return null;
    }

    private static Object jsonValue(Object value) {
        if (value instanceof String s && (s.trim().startsWith("{") || s.trim().startsWith("["))) {
            try {
                return Json.parse(s);
            } catch (RuntimeException e) {
                return value;
            }
        }
        return value;
    }

    /**
     * {@code a.b.c} → nested objects, {@code items[0].name} → arrays (index ≤ 99); an
     * existing segment of the wrong shape is replaced.
     */
    static void putPath(Map<String, Object> body, String path, Object value) {
        List<Object> keys = new ArrayList<>();
        for (String part : path.split("\\.")) {
            Matcher m = PATH_SEGMENT.matcher(part);
            if (!m.matches()) {
                keys.add(part);
                continue;
            }
            if (!m.group(1).isEmpty()) {
                keys.add(m.group(1));
            }
            Matcher idx = PATH_INDEX.matcher(m.group(2));
            while (idx.find()) {
                keys.add(Math.min(Integer.parseInt(idx.group(1)), MAX_PATH_INDEX));
            }
        }
        Object cur = body;
        for (int i = 0; i < keys.size(); i++) {
            Object key = keys.get(i);
            boolean last = i == keys.size() - 1;
            Object child = last ? value
                    : keys.get(i + 1) instanceof Integer ? new ArrayList<>() : new LinkedHashMap<String, Object>();
            Object existing = get(cur, key);
            if (!last && existing instanceof String) {
                Object parsed = jsonValue(existing);
                if (parsed instanceof Map<?, ?> map) {
                    existing = new LinkedHashMap<>(map);
                    set(cur, key, existing);
                } else if (parsed instanceof List<?> list) {
                    existing = new ArrayList<>(list);
                    set(cur, key, existing);
                }
            }
            if (!last && existing != null && (existing instanceof List<?>) == (child instanceof List<?>)
                    && (existing instanceof Map<?, ?> || existing instanceof List<?>)) {
                cur = existing;
                continue;
            }
            set(cur, key, child);
            cur = child;
        }
    }

    private static Object get(Object container, Object key) {
        if (container instanceof Map<?, ?> map) {
            return map.get(key);
        }
        List<?> list = (List<?>) container;
        int i = (Integer) key;
        return i < list.size() ? list.get(i) : null;
    }

    @SuppressWarnings("unchecked")
    private static void set(Object container, Object key, Object value) {
        if (container instanceof Map<?, ?>) {
            ((Map<String, Object>) container).put(String.valueOf(key), value);
            return;
        }
        List<Object> list = (List<Object>) container;
        int i = (Integer) key;
        while (list.size() <= i) {
            list.add(new LinkedHashMap<String, Object>());
        }
        list.set(i, value);
    }

    static Prepared encode(OfficialApiSpec.Operation op, Map<String, Object> in, int timeoutSeconds) {
        if (op != null && op.mime != null) {
            String target = op.mime.target;
            if (in == null || in.get(target) == null || String.valueOf(in.get(target)).isBlank()) {
                OfficialApiMime.Built built = OfficialApiMime.build(in, op.mime.encoding, timeoutSeconds);
                if (built.error() != null) {
                    return new Prepared(new byte[0], null, built.error());
                }
                Map<String, Object> withMessage = in == null ? new LinkedHashMap<>() : new LinkedHashMap<>(in);
                withMessage.put(target, built.encoded());
                in = withMessage;
            }
        }
        String format = op == null || op.bodyFormat == null || op.bodyFormat.isBlank()
                ? "json" : op.bodyFormat.toLowerCase(Locale.ROOT);
        return switch (format) {
            case "form" -> form(op, in);
            case "multipart" -> multipart(op, in, timeoutSeconds);
            case "raw" -> raw(op, in, timeoutSeconds);
            default -> json(op, in);
        };
    }

    static Prepared json(OfficialApiSpec.Operation op, Map<String, Object> in) {
        List<?> rootArray = rootArray(op, in);
        String text = Json.write(rootArray != null ? rootArray : jsonObject(op, in));
        String ct = op != null && op.contentType != null && !op.contentType.isBlank()
                ? op.contentType : "application/json";
        return new Prepared(text.getBytes(StandardCharsets.UTF_8), ct, null);
    }

    static Prepared jsonFromMap(Map<String, Object> body, String contentType) {
        String text = Json.write(body == null ? Map.of() : body);
        String ct = contentType == null || contentType.isBlank() ? "application/json" : contentType;
        return new Prepared(text.getBytes(StandardCharsets.UTF_8), ct, null);
    }

    private static Prepared form(OfficialApiSpec.Operation op, Map<String, Object> in) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (op != null) {
            for (String k : op.bodyFrom) {
                if (in == null || in.get(k) == null) {
                    continue;
                }
                String v = scalar(in.get(k));
                if (v != null) {
                    fields.put(op.bodyName(k), v);
                }
            }
        }
        String encoded = DataSupport.query(fields);
        return new Prepared(encoded.getBytes(StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded", null);
    }

    private static Prepared raw(OfficialApiSpec.Operation op, Map<String, Object> in,
                                 int timeoutSeconds) {
        String key = op == null ? "" : op.bodyParam;
        if (key == null || key.isBlank()) {
            return new Prepared(new byte[0], contentType(op, "application/octet-stream"),
                    "bodyParam is required for bodyFormat=raw");
        }
        if (in == null || in.get(key) == null) {
            return new Prepared(new byte[0], contentType(op, "application/octet-stream"),
                    key + " is required for bodyFormat=raw");
        }
        Object v = in.get(key);
        // Platform file type is always a List; single-destination raw (e.g. S3 PUT) uses the first item.
        if (v instanceof List<?> list) {
            v = list.isEmpty() ? null : list.get(0);
        }
        if (v == null) {
            return new Prepared(new byte[0], contentType(op, "application/octet-stream"),
                    key + " is required for bodyFormat=raw");
        }
        byte[] bytes;
        if (v instanceof byte[] b) {
            bytes = b;
        } else if (v instanceof String s && op != null && op.fileBytes) {
            Prepared resolved = sandboxFileBytes(s, key);
            if (resolved.failed()) {
                return new Prepared(new byte[0], contentType(op, "application/octet-stream"),
                        resolved.error());
            }
            bytes = resolved.bytes();
        } else if (v instanceof String s) {
            bytes = s.getBytes(StandardCharsets.UTF_8);
        } else {
            FilePart file = filePart(v);
            if (file != null) {
                Prepared resolved = resolveFileBytes(file, timeoutSeconds, "raw");
                if (resolved.failed()) {
                    return new Prepared(new byte[0], contentType(op, "application/octet-stream"),
                            resolved.error());
                }
                bytes = resolved.bytes();
            } else if (op != null && op.fileBytes) {
                return new Prepared(new byte[0], contentType(op, "application/octet-stream"),
                        key + " must be a file-typed value (path, ContentBlock, or $fileRef).");
            } else {
                bytes = scalar(v).getBytes(StandardCharsets.UTF_8);
            }
        }
        return new Prepared(bytes, contentType(op, "application/octet-stream"), null);
    }

    private static Prepared multipart(OfficialApiSpec.Operation op, Map<String, Object> in,
                                      int timeoutSeconds) {
        String boundary = "sqb" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
        List<byte[]> chunks = new java.util.ArrayList<>();
        if (op != null) {
            for (String k : op.bodyFrom) {
                if (in == null || in.get(k) == null) {
                    continue;
                }
                Prepared err = addMultipartField(chunks, boundary, op.bodyName(k), in.get(k), timeoutSeconds);
                if (err != null) {
                    return err;
                }
            }
        }
        chunks.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        int len = 0;
        for (byte[] c : chunks) {
            len += c.length;
        }
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] c : chunks) {
            System.arraycopy(c, 0, out, pos, c.length);
            pos += c.length;
        }
        return new Prepared(out, "multipart/form-data; boundary=" + boundary, null);
    }

    /**
     * One bodyFrom value. A List of file-shaped items becomes N file parts with the same
     * part name (Jira / Confluence accept repeated {@code name="file"}). Empty list emits
     * no part. A list with no file-shaped item stays one JSON text part so non-file
     * multipart arrays stay compatible.
     */
    private static Prepared addMultipartField(List<byte[]> chunks, String boundary, String name,
                                              Object v, int timeoutSeconds) {
        if (v instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            List<FilePart> files = new java.util.ArrayList<>();
            for (Object item : list) {
                FilePart fp = filePart(item);
                if (fp != null) {
                    files.add(fp);
                }
            }
            if (!files.isEmpty()) {
                for (FilePart file : files) {
                    Prepared err = appendFilePart(chunks, boundary, name, file, timeoutSeconds);
                    if (err != null) {
                        return err;
                    }
                }
                return null;
            }
            appendTextPart(chunks, boundary, name, v);
            return null;
        }
        FilePart file = filePart(v);
        if (file != null) {
            return appendFilePart(chunks, boundary, name, file, timeoutSeconds);
        }
        appendTextPart(chunks, boundary, name, v);
        return null;
    }

    private static Prepared appendFilePart(List<byte[]> chunks, String boundary, String partName,
                                           FilePart file, int timeoutSeconds) {
        Prepared resolved = resolveFileBytes(file, timeoutSeconds, "multipart");
        if (resolved.failed()) {
            return resolved;
        }
        byte[] data = resolved.bytes();
        String fn = file.fileName == null || file.fileName.isBlank() ? partName : file.fileName;
        String ct = file.contentType == null || file.contentType.isBlank()
                ? "application/octet-stream" : file.contentType;
        String preamble = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + escapeDisp(partName)
                + "\"; filename=\"" + escapeDisp(fn) + "\"\r\n"
                + "Content-Type: " + ct + "\r\n\r\n";
        chunks.add(preamble.getBytes(StandardCharsets.UTF_8));
        chunks.add(data);
        chunks.add("\r\n".getBytes(StandardCharsets.UTF_8));
        return null;
    }

    private static void appendTextPart(List<byte[]> chunks, String boundary, String name, Object v) {
        String preamble = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + escapeDisp(name) + "\"\r\n\r\n"
                + scalar(v) + "\r\n";
        chunks.add(preamble.getBytes(StandardCharsets.UTF_8));
    }

    /** Decode already-parsed base64, or fetch {@code source=url}. Prefix keeps today's error text. */
    static Prepared resolveFileBytes(FilePart file, int timeoutSeconds, String errorPrefix) {
        if (file.invalidBase64) {
            return new Prepared(new byte[0], null, errorPrefix + " file data is not valid base64");
        }
        byte[] data = file.bytes;
        if (file.url != null) {
            DataSupport.BytesResp fetched = DataSupport.fetchBytes(file.url, timeoutSeconds);
            if (fetched.error() != null) {
                return new Prepared(new byte[0], null, errorPrefix + " url fetch failed");
            }
            data = fetched.body();
        }
        if (data == null) {
            return new Prepared(new byte[0], null, errorPrefix + " file bytes missing");
        }
        return new Prepared(data, null, null);
    }

    /**
     * A multipart file part from either file shape the platform can hand a node:
     *
     * <ul>
     *   <li>the platform ContentBlock a {@code file}-typed port carries —
     *       {@code {kind, mime_type, source: base64|url, data, name}} — read natively, so a workflow
     *       never has to copy {@code data} into {@code dataBase64} (and {@code name}/{@code mime_type}
     *       into {@code fileName}/{@code contentType}) to attach a file; the block is sent once;</li>
     *   <li>the legacy multipart map {@code {dataBase64|url, fileName, contentType}} — still accepted
     *       so existing workflows keep working.</li>
     * </ul>
     *
     * <p>Within one map the ContentBlock fields win for the payload ({@code data}) and the legacy
     * keys fill only what the block does not say, so a map that carries both shapes is still one
     * part with one body.
     */
    static FilePart filePart(Object v) {
        if (!(v instanceof Map<?, ?> m)) {
            return null;
        }
        String source = str(m.get("source")).toLowerCase(Locale.ROOT);
        String data = str(m.get("data"));
        String legacyB64 = str(m.get("dataBase64"));
        String legacyUrl = str(m.get("url"));

        // Payload: ContentBlock `data` first (URL when the block says so), then the legacy keys.
        String b64s = "";
        String urls = "";
        if (!data.isEmpty()) {
            if ("url".equals(source)) {
                urls = data;
            } else {
                b64s = data;
            }
        } else {
            b64s = legacyB64;
            urls = legacyUrl;
        }
        if (b64s.isEmpty() && urls.isEmpty()) {
            return null;
        }
        String name = firstNonEmpty(str(m.get("name")), str(m.get("fileName")));
        String ct = firstNonEmpty(str(m.get("mime_type")), str(m.get("mimeType")), str(m.get("contentType")));
        if (!b64s.isEmpty()) {
            try {
                return new FilePart(Base64.getDecoder().decode(b64s), name, ct, null, false);
            } catch (IllegalArgumentException e) {
                return new FilePart(null, name, ct, null, true);
            }
        }
        return new FilePart(null, name, ct, urls, false);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String s : values) {
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return "";
    }

    record FilePart(byte[] bytes, String fileName, String contentType, String url,
                            boolean invalidBase64) {
    }

    private static String scalar(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Number n) {
            return DataSupport.integralText(n);
        }
        if (v instanceof Map<?, ?> || v instanceof List<?>) {
            return Json.write(v);
        }
        return String.valueOf(v);
    }

    /** Same path rule as {@code FilesSupport.readSandboxPath}. Inline text is not a file. */
    private static Prepared sandboxFileBytes(String path, String fieldName) {
        String t = path == null ? "" : path.trim();
        if (t.isEmpty()) {
            return new Prepared(new byte[0], null,
                    fieldName + " is required (sandbox path under /sandbox/files).");
        }
        if (t.startsWith("sq-file:")) {
            return new Prepared(new byte[0], null,
                    fieldName + " is still an unresolved sq-file handle. The daemon should materialise it to "
                            + "/sandbox/files before invoke.");
        }
        Path p;
        try {
            p = Path.of(t).normalize();
        } catch (Exception e) {
            return new Prepared(new byte[0], null, fieldName + " is not a usable sandbox path.");
        }
        String abs = p.toAbsolutePath().toString();
        if (!abs.equals("/sandbox/files") && !abs.startsWith("/sandbox/files/")) {
            return new Prepared(new byte[0], null,
                    fieldName + " path must be under /sandbox/files.");
        }
        try {
            if (!Files.isRegularFile(p)) {
                return new Prepared(new byte[0], null, "sandbox file not found: " + t);
            }
            long size = Files.size(p);
            if (size > 8 * 1024 * 1024) {
                return new Prepared(new byte[0], null,
                        fieldName + " is " + size + " bytes, above the " + (8 * 1024 * 1024) + " byte node limit.");
            }
            return new Prepared(Files.readAllBytes(p), null, null);
        } catch (IOException e) {
            return new Prepared(new byte[0], null, fieldName + " could not be read from the sandbox.");
        }
    }

    private static String contentType(OfficialApiSpec.Operation op, String fallback) {
        if (op != null && op.contentType != null && !op.contentType.isBlank()) {
            return op.contentType;
        }
        return fallback;
    }

    private static String escapeDisp(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
