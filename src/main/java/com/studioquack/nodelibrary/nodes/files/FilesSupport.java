package com.studioquack.nodelibrary.nodes.files;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shared HTTP, file-ref, and JWT helpers for Files &amp; Documents nodes.
 *
 * <p>Thread-safe. Never logs credentials, tokens, or file bytes. File payloads are
 * bounded at {@link #MAX_FILE_BYTES} so a provider cannot OOM the sandbox.
 */
public final class FilesSupport {

    /** Product User-Agent on every outbound call. */
    public static final String USER_AGENT = DataSupport.USER_AGENT;

    /** Decoded-byte ceiling for upload and download (8 MiB; matches the invoke-body cap). */
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

    /** JSON error-body excerpt. */
    public static final int ERR_BODY_CHARS = 500;

    /** Daemon plants materialised file-type values under this directory. */
    public static final String SANDBOX_FILES = "/sandbox/files";

    private static final int DEFAULT_TIMEOUT = 30;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private FilesSupport() {
        // utility — never instantiated
    }

    /** Template id from {@code SQ_TEMPLATE_ID_<KEY>} or the definition fallback. */
    public static String templateId(String envKey, String fallback) {
        return DataSupport.templateId(envKey, fallback);
    }

    public static Map<String, Object> readConfig(Map<String, Object> request) {
        return DataSupport.readConfig(request);
    }

    public static Map<String, Object> readInput(Map<String, Object> request, StudioQuackHelper helper) {
        return DataSupport.readInput(request, helper);
    }

    /** Resolved file bytes plus original name and MIME. Never logs content. */
    public record FileBytes(byte[] bytes, String name, String mimeType) {
    }

    /** HTTP result. {@code error} is set only on transport failure. */
    public record HttpResult(int status, byte[] body, Map<String, List<String>> headers, String error) {
        public boolean ok() {
            return error == null && status >= 200 && status < 300;
        }

        public String text() {
            return body == null ? "" : new String(body, StandardCharsets.UTF_8);
        }

        public Map<String, Object> jsonObject() {
            return DataSupport.parseObject(text());
        }

        public String header(String name) {
            if (headers == null || name == null) {
                return null;
            }
            List<String> vals = headers.get(name.toLowerCase(Locale.ROOT));
            return vals == null || vals.isEmpty() ? null : vals.get(0);
        }
    }

    /**
     * Outbound HTTP with a capped body. {@code extraHeaders} may hold Authorization —
     * never log this map. {@code bodyBytes} null means no entity (GET/DELETE).
     */
    public static HttpResult exchange(String method, String url, Map<String, String> extraHeaders,
                                      byte[] bodyBytes, String contentType, int timeoutSeconds) {
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", USER_AGENT);
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null
                            || e.getKey().isBlank() || e.getValue().isBlank()) {
                        continue;
                    }
                    b.header(e.getKey(), e.getValue());
                }
            }
            String m = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
            boolean hasBody = "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m);
            if (hasBody) {
                byte[] payload = bodyBytes == null ? new byte[0] : bodyBytes;
                if (contentType != null && !contentType.isBlank()
                        && (extraHeaders == null || extraHeaders.get("Content-Type") == null)) {
                    b.header("Content-Type", contentType);
                }
                b.method(m, HttpRequest.BodyPublishers.ofByteArray(payload));
            } else if ("DELETE".equals(m)) {
                b.method("DELETE", HttpRequest.BodyPublishers.noBody());
            } else {
                b.GET();
            }
            HttpResponse<InputStream> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] body = readCapped(resp.body(), MAX_FILE_BYTES + 1);
            Map<String, List<String>> hdrs = new LinkedHashMap<>();
            resp.headers().map().forEach((k, v) -> hdrs.put(k.toLowerCase(Locale.ROOT), v));
            return new HttpResult(resp.statusCode(), body, hdrs, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HttpResult(-1, new byte[0], Map.of(), "request interrupted");
        } catch (IllegalArgumentException e) {
            return new HttpResult(-1, new byte[0], Map.of(), "invalid URL");
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return new HttpResult(-1, new byte[0], Map.of(),
                    "HTTP transport failed" + DataSupport.transportFailureDetail(e));
        }
    }

    public static HttpResult get(String url, Map<String, String> headers, int timeout) {
        return exchange("GET", url, headers, null, null, timeout);
    }

    public static HttpResult delete(String url, Map<String, String> headers, int timeout) {
        return exchange("DELETE", url, headers, null, null, timeout);
    }

    public static HttpResult postJson(String url, Map<String, String> headers, Object json, int timeout) {
        byte[] body = Json.write(json == null ? Map.of() : json).getBytes(StandardCharsets.UTF_8);
        return exchange("POST", url, headers, body, "application/json", timeout);
    }

    public static HttpResult putJson(String url, Map<String, String> headers, Object json, int timeout) {
        byte[] body = Json.write(json == null ? Map.of() : json).getBytes(StandardCharsets.UTF_8);
        return exchange("PUT", url, headers, body, "application/json", timeout);
    }

    public static HttpResult patchJson(String url, Map<String, String> headers, Object json, int timeout) {
        byte[] body = Json.write(json == null ? Map.of() : json).getBytes(StandardCharsets.UTF_8);
        return exchange("PATCH", url, headers, body, "application/json", timeout);
    }

    /**
     * Resolve a platform {@code file}-typed value: sandbox path after materialisation,
     * ContentBlock ({@code name}/{@code mime_type}/{@code data}), or {@code $fileRef}.
     * A List unwraps to its first item — callers are single-source / single-destination
     * (write, extract, compression, htmlSource).
     */
    public static FileBytes readFileValue(Object raw, String fieldName) {
        if (raw == null) {
            throw fail(fileRequiredMessage(fieldName));
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            throw fail(fieldName + " must be a file-typed value (path, ContentBlock, or $fileRef).");
        }
        if (raw instanceof List<?> list) {
            if (list.isEmpty() || list.get(0) == null) {
                throw fail(fileRequiredMessage(fieldName));
            }
            raw = list.get(0);
            if (raw instanceof Number || raw instanceof Boolean) {
                throw fail(fieldName + " must be a file-typed value (path, ContentBlock, or $fileRef).");
            }
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                throw fail(fileRequiredMessage(fieldName));
            }
            if (t.startsWith("sq-file:")) {
                throw fail(fieldName + " is still an unresolved sq-file handle. The daemon should materialise it to "
                        + SANDBOX_FILES + " before invoke.");
            }
            return readSandboxPath(t, fieldName);
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> m = Json.asMap(map);
            Object inner = m.get("$fileRef");
            if (inner instanceof Map<?, ?>) {
                m = Json.asMap(inner);
            }
            String name = firstNonBlank(m.get("name"), m.get("fileName"), "file");
            String mime = firstNonBlank(m.get("mime_type"), m.get("mimeType"), "application/octet-stream");
            String b64 = firstNonBlank(m.get("data"), m.get("contentBase64"), m.get("content"));
            if (b64 != null && !b64.startsWith("sq-file:")) {
                byte[] bytes = decodeB64(b64, fieldName);
                return new FileBytes(bytes, name, mime);
            }
            String path = firstNonBlank(m.get("path"), m.get("fileId"));
            if (path != null && path.startsWith("/")) {
                FileBytes fromPath = readSandboxPath(path, fieldName);
                String n = name == null || "file".equals(name) ? fromPath.name() : name;
                return new FileBytes(fromPath.bytes(), n, mime);
            }
            throw fail(fieldName + " ContentBlock has no data / contentBase64 / sandbox path.");
        }
        throw fail(fieldName + " must be a file-typed value (path, ContentBlock, or $fileRef).");
    }

    /** ContentBlock for a download OUTPUT field of platform type {@code file}. */
    public static Map<String, Object> fileBlock(String name, String mimeType, byte[] bytes) {
        if (bytes == null) {
            bytes = new byte[0];
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw fail("Downloaded file is " + bytes.length + " bytes, above the "
                    + MAX_FILE_BYTES + " byte node limit.");
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "file");
        block.put("name", name == null || name.isBlank() ? "download.bin" : name);
        block.put("mime_type", mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType);
        block.put("source", "base64");
        block.put("data", Base64.getEncoder().encodeToString(bytes));
        block.put("size", bytes.length);
        return block;
    }

    public static Map<String, Object> ok(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        if (body != null) {
            out.putAll(body);
        }
        return DataSupport.ports(out);
    }

    /** Raise a redacted BPMN error. Declared as {@code Map} only so {@code return} sites compile. */
    public static Map<String, Object> failPorts(String message, List<String> secrets) {
        return DataSupport.fail(DataSupport.redact(message, secrets == null ? new String[0]
                : secrets.toArray(String[]::new)));
    }

    public static Fail fail(String message) {
        return new Fail(message == null ? "request failed" : message);
    }

    public static Map<String, Object> requireOk(HttpResult resp, String provider, List<String> secrets) {
        if (resp.error() != null) {
            throw fail(DataSupport.redact(provider + " API Error: " + resp.error(), secretsArray(secrets)));
        }
        if (!resp.ok()) {
            String body = resp.text();
            if (body.length() > ERR_BODY_CHARS) {
                body = body.substring(0, ERR_BODY_CHARS);
            }
            throw fail(DataSupport.redact(
                    provider + " API Error: HTTP " + resp.status() + ": " + body, secretsArray(secrets)));
        }
        Map<String, Object> json = resp.jsonObject();
        return json == null ? new LinkedHashMap<>() : json;
    }

    public static void requireOkNoJson(HttpResult resp, String provider, List<String> secrets) {
        if (resp.error() != null || !resp.ok()) {
            requireOk(resp, provider, secrets);
        }
    }

    /** Compact JWS RS256. Optional {@code kid} for Box JWT. */
    public static String signJwtRs256(Map<String, Object> header, Map<String, Object> payload, PrivateKey key) {
        String header64 = b64Url(Json.write(header).getBytes(StandardCharsets.UTF_8));
        String payload64 = b64Url(Json.write(payload).getBytes(StandardCharsets.UTF_8));
        String signingInput = header64 + "." + payload64;
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(key);
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64Url(sig.sign());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            throw fail("JWT RS256 sign failed: " + e.getClass().getSimpleName());
        }
    }

    /** PKCS8 PEM, optionally encrypted with {@code passphrase} (Box config.json). */
    public static PrivateKey loadPkcs8(String pem, String passphrase) {
        if (pem == null || pem.isBlank()) {
            throw fail("private_key is missing");
        }
        String raw = pem.replace("\\n", "\n");
        boolean encrypted = raw.contains("ENCRYPTED");
        String body = raw
                .replace("-----BEGIN ENCRYPTED PRIVATE KEY-----", "")
                .replace("-----END ENCRYPTED PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw fail("invalid private_key PEM");
        }
        try {
            if (encrypted) {
                if (passphrase == null || passphrase.isBlank()) {
                    throw fail("encrypted private_key requires passphrase");
                }
                EncryptedPrivateKeyInfo epk = new EncryptedPrivateKeyInfo(der);
                SecretKeyFactory skf = SecretKeyFactory.getInstance(epk.getAlgName());
                SecretKey sk = skf.generateSecret(new PBEKeySpec(passphrase.toCharArray()));
                Cipher cipher = Cipher.getInstance(epk.getAlgName());
                cipher.init(Cipher.DECRYPT_MODE, sk, epk.getAlgParameters());
                der = cipher.doFinal(epk.getEncryptedData());
            }
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Fail e) {
            throw e;
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            throw fail("invalid PKCS8 private_key: " + e.getClass().getSimpleName());
        }
    }

    /** RFC 2046 multipart body. Parts are already fully formatted headers+blank-line+payload. */
    public static byte[] multipart(String boundary, List<byte[]> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] dash = ("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] end = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] crlf = "\r\n".getBytes(StandardCharsets.US_ASCII);
        try {
            for (byte[] part : parts) {
                out.write(dash);
                out.write(part);
                out.write(crlf);
            }
            out.write(end);
        } catch (IOException e) {
            throw fail("multipart encode failed");
        }
        return out.toByteArray();
    }

    public static String newBoundary() {
        return "sqb" + UUID.randomUUID().toString().replace("-", "");
    }

    /** Dropbox-API-Arg must be ASCII; escape non-ASCII code points. */
    public static String asciiJsonHeader(Object json) {
        String s = Json.write(json);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String firstNonBlank(Object... vals) {
        if (vals == null) {
            return null;
        }
        for (Object v : vals) {
            String s = DataSupport.trimToNull(EmulatorUtil.str(v));
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    public static String collectSecret(Object raw, List<String> secrets) {
        String s = DataSupport.trimToNull(EmulatorUtil.str(raw));
        if (s != null) {
            secrets.add(s);
        }
        return s;
    }

    public static void collectMapSecrets(Object raw, List<String> secrets) {
        if (raw instanceof String s && !s.isBlank()) {
            secrets.add(s);
        } else if (raw instanceof Map<?, ?>) {
            secrets.add(Json.write(raw));
        }
    }

    static byte[] readCapped(InputStream in, int maxPlusOne) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        try (InputStream stream = in) {
            byte[] buf = stream.readNBytes(maxPlusOne);
            return buf == null ? new byte[0] : buf;
        }
    }

    private static FileBytes readSandboxPath(String path, String fieldName) {
        Path p;
        try {
            p = Path.of(path).normalize();
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            throw fail(fieldName + " is not a usable sandbox path.");
        }
        String abs = p.toAbsolutePath().toString();
        if (!abs.equals(SANDBOX_FILES) && !abs.startsWith(SANDBOX_FILES + "/")) {
            throw fail(fieldName + " path must be under " + SANDBOX_FILES + ".");
        }
        try {
            if (!Files.isRegularFile(p)) {
                throw fail("sandbox file not found: " + path);
            }
            long size = Files.size(p);
            if (size > MAX_FILE_BYTES) {
                throw fail(fieldName + " is " + size + " bytes, above the " + MAX_FILE_BYTES + " byte node limit.");
            }
            byte[] bytes = Files.readAllBytes(p);
            String name = p.getFileName() == null ? "file" : p.getFileName().toString();
            return new FileBytes(bytes, name, guessMime(name));
        } catch (Fail e) {
            throw e;
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            throw fail(fieldName + " could not be read from the sandbox.");
        }
    }

    private static String fileRequiredMessage(String fieldName) {
        return fieldName + " is required (sandbox path under " + SANDBOX_FILES
                + ", ContentBlock with data/base64, or $fileRef — never inline bytes on the wire).";
    }

    private static byte[] decodeB64(String b64, String fieldName) {
        try {
            byte[] bytes = Base64.getDecoder().decode(b64.replaceAll("\\s+", ""));
            if (bytes.length > MAX_FILE_BYTES) {
                throw fail(fieldName + " is " + bytes.length + " bytes, above the "
                        + MAX_FILE_BYTES + " byte node limit.");
            }
            return bytes;
        } catch (Fail e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw fail(fieldName + " is not valid base64.");
        }
    }

    static String guessMime(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".txt") || n.endsWith(".csv")) {
            return "text/plain";
        }
        if (n.endsWith(".json")) {
            return "application/json";
        }
        return "application/octet-stream";
    }

    private static String b64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String[] secretsArray(List<String> secrets) {
        return secrets == null ? new String[0] : secrets.toArray(String[]::new);
    }

    /**
     * Validation / provider failure that must not become {@code Error: Type: …}.
     * HTTP statuses in the message map through {@link DataSupport#failError};
     * pre-request validation phrases raise {@code INVALID_INPUT}.
     */
    public static final class Fail extends studioquack.StudioQuackError {
        Fail(String message) {
            this(classified(message));
        }

        private Fail(studioquack.StudioQuackError cause) {
            super(cause.errorCode(), cause.getMessage(), cause.data());
        }

        private static studioquack.StudioQuackError classified(String message) {
            String msg = message == null || message.isBlank() ? "request failed" : message;
            String lower = msg.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("required") || lower.contains("requires") || lower.contains("must be")
                    || lower.contains("missing")
                    || lower.contains("invalid private_key") || lower.contains("invalid pkcs8")
                    || lower.contains("not valid base64") || lower.contains("no data")
                    || lower.contains("above the")) {
                return new studioquack.StudioQuackError("INVALID_INPUT", msg);
            }
            return DataSupport.failError(msg);
        }
    }
}
