package com.studioquack.nodelibrary.core;

import com.studioquack.nodelibrary.nodes.codec.DocumentText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * v2 media blocks ({@code {type: image|audio|video|document, mime_type, source, data, name}})
 * → OpenAI chat-completions content parts, under a capability profile:
 *
 * <ul>
 *   <li>{@link Profile#FULL} — the real OpenAI API: {@code image_url}, {@code file}
 *   (PDF via {@code file_data} data URL), {@code input_audio} (wav/mp3).</li>
 *   <li>{@link Profile#BASIC} — OpenAI-compatible local servers and third-party gateways
 *   (llama.cpp, LM Studio, vLLM, groq, mistral, deepseek, …) whose chat endpoint accepts
 *   only {@code text} and {@code image_url} parts. Text-like documents are decoded and
 *   inlined as text; binary office documents (XLSX/ODS) and PDFs are extracted to text
 *   in-JVM via {@code DocumentText} and inlined; anything else (audio, video, docx, …)
 *   degrades to an explicit text notice so the model knows an attachment was dropped
 *   (and the author can wire Docling in).</li>
 * </ul>
 *
 * <p>Already-native OpenAI parts ({@code text}/{@code image_url}/{@code input_audio}/
 * {@code file}) pass through untouched, so conversion is idempotent. Never fetches —
 * a {@code source:"url"} payload is forwarded as a URL, never dereferenced.
 * Unhydrated handle-only blocks ({@code fileId} or {@code source} starting
 * {@code sq-file:} with no usable {@code data}) are skipped with one shape-only
 * {@code System.err} WARN — never a fake "not sent" prompt sentence, never file bytes.
 */
public final class OpenAiContentParts {

    /** Capability profile for the target chat-completions server. */
    public enum Profile { FULL, BASIC }

    /** Decoded-text ceiling per inlined document (keeps a giant base64 blob from blowing up the prompt). */
    static final int MAX_INLINE_TEXT_CHARS = 64 * 1024;

    private OpenAiContentParts() {
        // Utility — never instantiated.
    }

    /**
     * Walk v2 {@code messages} and fold media blocks / {@code files} arrays into OpenAI
     * content parts. Text-only messages pass through verbatim (string content stays a
     * string); the {@code files} key is consumed into {@code content} and removed.
     */
    public static List<Map<String, Object>> messagesToOpenAi(List<Object> messages, Profile profile) {
        return messagesToOpenAi(messages, profile, profile == Profile.FULL);
    }

    public static List<Map<String, Object>> messagesToOpenAi(List<Object> messages, Profile profile,
                                                            boolean nativePdfAllowed) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object raw : messages == null ? List.of() : messages) {
            Map<String, Object> msg = ProviderStream.asMap(raw);
            if (msg.isEmpty()) {
                continue;
            }
            List<Object> files = ProviderStream.asList(msg.get("files"));
            Object content = msg.get("content");
            if (files.isEmpty() && !hasMediaBlock(content)) {
                out.add(msg);
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(msg);
            row.put("content", contentToOpenAi(content, files, profile, nativePdfAllowed));
            row.remove("files");
            out.add(row);
        }
        return out;
    }

    /**
     * Append {@code files} onto the last user message (or a new user message if none).
     * Files ride the message-level {@code files} list; {@link #messagesToOpenAi} then
     * folds them under the caller's profile. Returns a new list; never mutates the input.
     * Empty/null {@code files} copies {@code messages} as-is (still a new list).
     */
    public static List<Object> appendFilesToLastUserMessage(List<Object> messages, List<Object> files) {
        List<Object> out = new ArrayList<>();
        for (Object raw : messages == null ? List.of() : messages) {
            if (raw instanceof Map<?, ?>) {
                out.add(new LinkedHashMap<>(ProviderStream.asMap(raw)));
            } else {
                out.add(raw);
            }
        }
        if (files == null || files.isEmpty()) {
            return out;
        }
        Map<String, Object> target = null;
        int idx = -1;
        for (int i = out.size() - 1; i >= 0; i--) {
            Object row = out.get(i);
            if (row instanceof Map<?, ?> m
                    && "user".equalsIgnoreCase(ProviderStream.trim(m.get("role")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> copy = (Map<String, Object>) row;
                target = copy;
                idx = i;
                break;
            }
        }
        if (target == null) {
            target = new LinkedHashMap<>();
            target.put("role", "user");
            target.put("content", "");
            out.add(target);
        } else {
            out.set(idx, target);
        }
        List<Object> existing = new ArrayList<>(ProviderStream.asList(target.get("files")));
        existing.addAll(files);
        target.put("files", existing);
        return out;
    }

    /**
     * Fold request-level file attachments onto the last user message as OpenAI
     * content parts under {@code profile}. Null/empty {@code inputFiles} is a
     * no-op (same list instance). When no user message exists, one is appended.
     * Existing message-level {@code files} are merged in; the {@code files} key
     * is then consumed (same as {@link #messagesToOpenAi}). Never logs file bytes.
     */
    public static List<Object> appendFilesToLastUserMessage(List<Object> messages,
                                                            List<Object> inputFiles,
                                                            Profile profile) {
        if (inputFiles == null || inputFiles.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        Profile p = profile == null ? Profile.FULL : profile;
        List<Object> out = new ArrayList<>();
        if (messages != null) {
            out.addAll(messages);
        }
        int idx = -1;
        for (int i = out.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = ProviderStream.asMap(out.get(i));
            if ("user".equalsIgnoreCase(ProviderStream.trim(msg.get("role")))) {
                idx = i;
                break;
            }
        }
        Map<String, Object> last;
        if (idx < 0) {
            last = new LinkedHashMap<>();
            last.put("role", "user");
            last.put("content", "");
            out.add(last);
            idx = out.size() - 1;
        } else {
            last = new LinkedHashMap<>(ProviderStream.asMap(out.get(idx)));
        }
        List<Object> files = new ArrayList<>(ProviderStream.asList(last.get("files")));
        files.addAll(inputFiles);
        last.put("content", contentToOpenAi(last.get("content"), files, p, p == Profile.FULL));
        last.remove("files");
        out.set(idx, last);
        return out;
    }

    /**
     * Same as {@link #appendFilesToLastUserMessage(List, List, Profile)} but
     * {@code nativePdfAllowed} controls whether a PDF stays a {@code file} part
     * (vision chat) or is extracted to text.
     */
    public static List<Object> appendFilesToLastUserMessage(List<Object> messages,
                                                            List<Object> inputFiles,
                                                            Profile profile,
                                                            boolean nativePdfAllowed) {
        if (inputFiles == null || inputFiles.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        Profile p = profile == null ? Profile.FULL : profile;
        List<Object> out = new ArrayList<>();
        if (messages != null) {
            out.addAll(messages);
        }
        int idx = -1;
        for (int i = out.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = ProviderStream.asMap(out.get(i));
            if ("user".equalsIgnoreCase(ProviderStream.trim(msg.get("role")))) {
                idx = i;
                break;
            }
        }
        Map<String, Object> last;
        if (idx < 0) {
            last = new LinkedHashMap<>();
            last.put("role", "user");
            last.put("content", "");
            out.add(last);
            idx = out.size() - 1;
        } else {
            last = new LinkedHashMap<>(ProviderStream.asMap(out.get(idx)));
        }
        List<Object> files = new ArrayList<>(ProviderStream.asList(last.get("files")));
        files.addAll(inputFiles);
        last.put("content", contentToOpenAi(last.get("content"), files, p, nativePdfAllowed));
        last.remove("files");
        out.set(idx, last);
        return out;
    }

    /** True when a content value carries at least one non-text block that needs conversion. */
    static boolean hasMediaBlock(Object content) {
        if (!(content instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                String type = ProviderStream.trim(m.get("type")).toLowerCase(Locale.ROOT);
                if (!type.isEmpty() && !"text".equals(type) && !"input_text".equals(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * String when everything resolved to text (cheaper, and some servers reject part
     * arrays on certain roles); a list of parts as soon as any media survives.
     */
    public static Object contentToOpenAi(Object content, List<Object> files, Profile profile) {
        return contentToOpenAi(content, files, profile, profile == Profile.FULL);
    }

    public static Object contentToOpenAi(Object content, List<Object> files, Profile profile,
                                        boolean nativePdfAllowed) {
        List<Map<String, Object>> parts = new ArrayList<>();
        boolean allText = true;
        if (content instanceof String s) {
            if (!s.isEmpty()) {
                parts.add(Map.of("type", "text", "text", s));
            }
        } else if (content instanceof List<?> list) {
            for (Object b : list) {
                if (b instanceof Map<?, ?>) {
                    Map<String, Object> conv = blockToOpenAi(ProviderStream.asMap(b), profile, nativePdfAllowed);
                    if (conv != null) {
                        if (!"text".equals(conv.get("type"))) {
                            allText = false;
                        }
                        parts.add(conv);
                    }
                } else if (b instanceof String s && !s.isEmpty()) {
                    parts.add(Map.of("type", "text", "text", s));
                }
            }
        } else if (content != null) {
            parts.add(Map.of("type", "text", "text", String.valueOf(content)));
        }
        for (Object f : files == null ? List.of() : files) {
            Map<String, Object> conv = blockToOpenAi(fileAsBlock(ProviderStream.asMap(f)), profile, nativePdfAllowed);
            if (conv != null) {
                if (!"text".equals(conv.get("type"))) {
                    allText = false;
                }
                parts.add(conv);
            }
        }
        if (allText) {
            StringBuilder joined = new StringBuilder();
            for (Map<String, Object> p : parts) {
                joined.append(ProviderStream.str(p.get("text")));
            }
            if (joined.length() > 0) {
                return joined.toString();
            }
            return content instanceof String s ? s : "";
        }
        return parts;
    }

    /** A {@code files}-list item ({@code kind}/{@code mime_type}/{@code source}/{@code data}/{@code name}) as a media block. */
    static Map<String, Object> fileAsBlock(Map<String, Object> fm) {
        if (fm.isEmpty()) {
            return fm;
        }
        String kind = ProviderStream.trim(fm.getOrDefault("kind", fm.get("type"))).toLowerCase(Locale.ROOT);
        if ("file".equals(kind)) {
            kind = "document";
        }
        Map<String, Object> synth = new LinkedHashMap<>();
        synth.put("type", kind.isEmpty() ? "document" : kind);
        synth.put("mime_type", fm.get("mime_type") != null ? fm.get("mime_type") : fm.get("mimeType"));
        String source = ProviderStream.trim(fm.get("source"));
        synth.put("source", source.isEmpty() ? (fm.get("url") != null ? "url" : "base64") : source);
        synth.put("data", firstNonNull(fm.get("data"), fm.get("file_data"), fm.get("url")));
        synth.put("name", fm.get("name"));
        Object fileId = fm.get("fileId");
        if (fileId != null && !ProviderStream.trim(fileId).isEmpty()) {
            synth.put("fileId", fileId);
        }
        return synth;
    }

    /**
     * One v2 block → one OpenAI part, or {@code null} when the block carries no payload.
     * Native OpenAI parts are returned as-is (idempotency).
     */
    static Map<String, Object> blockToOpenAi(Map<String, Object> block, Profile profile) {
        return blockToOpenAi(block, profile, profile == Profile.FULL);
    }

    static Map<String, Object> blockToOpenAi(Map<String, Object> block, Profile profile,
                                            boolean nativePdfAllowed) {
        if (block == null || block.isEmpty()) {
            return null;
        }
        String btype = ProviderStream.trim(block.get("type")).toLowerCase(Locale.ROOT);
        if ("text".equals(btype)) {
            return Map.of("type", "text", "text", ProviderStream.str(block.get("text")));
        }
        if (isNativeOpenAiPart(block, btype)) {
            return new LinkedHashMap<>(block);
        }
        if (isUnhydratedHandle(block)) {
            String fileId = ProviderStream.trim(block.get("fileId"));
            if (fileId.isEmpty()) {
                fileId = ProviderStream.trim(block.get("source"));
            }
            String name = ProviderStream.trim(block.get("name"));
            System.err.println("openai-parts: skip unhydrated file fileId=" + fileId + " name=" + name);
            return null;
        }
        if ("image".equals(btype)) {
            return imagePart(block);
        }
        if ("audio".equals(btype)) {
            return profile == Profile.FULL ? audioPart(block) : droppedNotice(block, "audio",
                    "this OpenAI-compatible server accepts only text and image_url parts");
        }
        if ("document".equals(btype) || "file".equals(btype)) {
            return documentPart(block, profile, nativePdfAllowed);
        }
        if ("video".equals(btype)) {
            return droppedNotice(block, "video",
                    "chat completions has no video part; extract frames or transcribe first");
        }
        // Unknown block with text — keep the words rather than dropping the turn.
        String text = ProviderStream.str(block.get("text"));
        return text.isEmpty() ? null : Map.of("type", "text", "text", text);
    }

    /**
     * Handle-only block whose bytes were not filled at provider sandbox bake.
     * Skip + WARN — do not emit an empty part or a {@link #droppedNotice}.
     */
    static boolean isUnhydratedHandle(Map<String, Object> block) {
        if (block == null) {
            return false;
        }
        String fileId = ProviderStream.trim(block.get("fileId"));
        String source = ProviderStream.trim(block.get("source"));
        boolean handle = !fileId.isEmpty() || source.toLowerCase(Locale.ROOT).startsWith("sq-file:");
        if (!handle) {
            return false;
        }
        return mediaPayload(block).isEmpty();
    }

    private static boolean isNativeOpenAiPart(Map<String, Object> block, String btype) {
        return ("image_url".equals(btype) && block.get("image_url") instanceof Map<?, ?>)
                || ("input_audio".equals(btype) && block.get("input_audio") instanceof Map<?, ?>)
                || ("file".equals(btype) && block.get("file") instanceof Map<?, ?>);
    }

    private static Map<String, Object> imagePart(Map<String, Object> block) {
        String data = mediaPayload(block);
        if (data.isEmpty()) {
            // Never vanish silently: an unhydrated/unreadable image must be visible in the
            // prompt (and in the model's answer) instead of looking like the model ignored it.
            return droppedNotice(block, "image", "no image payload reached the node");
        }
        String source = ProviderStream.trim(block.get("source"));
        if ("url".equals(source) || looksLikeUrl(data)) {
            return Map.of("type", "image_url", "image_url", Map.of("url", data, "detail", "auto"));
        }
        String mime = ProviderStream.trim(block.get("mime_type"));
        if (mime.isEmpty()) {
            mime = "image/png";
        }
        String url = data.startsWith("data:") ? data : "data:" + mime + ";base64," + stripDataUrl(data);
        return Map.of("type", "image_url", "image_url", Map.of("url", url, "detail", "auto"));
    }

    private static Map<String, Object> audioPart(Map<String, Object> block) {
        String data = mediaPayload(block);
        if (data.isEmpty()) {
            return droppedNotice(block, "audio", "no audio payload reached the node");
        }
        String fmt = ProviderStream.trim(block.get("mime_type"));
        fmt = fmt.contains("/") ? fmt.substring(fmt.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT)
                : fmt.toLowerCase(Locale.ROOT);
        if (!"wav".equals(fmt) && !"mp3".equals(fmt)) {
            fmt = "wav";
        }
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("data", stripDataUrl(data));
        audio.put("format", fmt);
        return Map.of("type", "input_audio", "input_audio", audio);
    }

    private static Map<String, Object> documentPart(Map<String, Object> block, Profile profile,
                                                     boolean nativePdfAllowed) {
        String data = mediaPayload(block);
        if (data.isEmpty()) {
            return droppedNotice(block, "document", "no document payload reached the node");
        }
        String mime = ProviderStream.trim(block.get("mime_type"));
        mime = mime.isEmpty() ? "application/pdf" : mime.split(";", 2)[0].toLowerCase(Locale.ROOT).trim();
        String filename = ProviderStream.trim(block.get("name"));
        if (filename.isEmpty()) {
            filename = "document";
        }
        // Text-like payloads are useful on EVERY server: decode and inline as text.
        if (isTextLike(mime, filename)) {
            String text = decodeText(data);
            if (text == null) {
                return droppedNotice(block, "document", "payload is not valid base64");
            }
            return Map.of("type", "text", "text",
                    "[File '" + filename + "' (" + mime + "), inlined as text]\n" + text);
        }
        boolean pdf = "application/pdf".equals(mime);
        // Office (xlsx/ods) is never a native chat file part — extract on both profiles.
        if (!pdf && DocumentText.isExtractable(mime, filename)) {
            Map<String, Object> extracted = extractedTextPart(mime, filename, data);
            if (extracted != null) {
                return extracted;
            }
        }
        // Native PDF file part only when the selected chat model accepts documents.
        if (pdf && profile == Profile.FULL && nativePdfAllowed) {
            String fileData = data.startsWith("data:") ? data : "data:application/pdf;base64," + stripDataUrl(data);
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("file_data", fileData);
            file.put("filename", filename);
            return Map.of("type", "file", "file", file);
        }
        // PDF without native file, or extractable office whose extract failed: try text.
        if (DocumentText.isExtractable(mime, filename)) {
            Map<String, Object> extracted = extractedTextPart(mime, filename, data);
            if (extracted != null) {
                return extracted;
            }
        }
        // docx/pptx (and failed extract): in-band notice — never send binary.
        if (profile == Profile.FULL) {
            return Map.of("type", "text", "text",
                    "[Document attachment '" + filename + "' has MIME type '" + mime
                            + "', but OpenAI chat file input only accepts application/pdf data URLs. The file was not sent.]");
        }
        return droppedNotice(block, "document",
                "this OpenAI-compatible server accepts only text and image_url parts; "
                        + "run the file through the Docling node and wire its text output");
    }

    /**
     * Binary document → extracted plain text part (XLSX/ODS/PDF via {@code DocumentText}),
     * or {@code null} when the format has no in-JVM extractor, the payload is not base64,
     * exceeds the extraction cap, or yields no text. A {@code source:"url"} payload is never
     * dereferenced — decode fails and the caller degrades to its notice.
     */
    private static Map<String, Object> extractedTextPart(String mime, String filename, String data) {
        String text = extractDocumentText(mime, filename, data);
        if (text == null) {
            return null;
        }
        return Map.of("type", "text", "text",
                "[File '" + filename + "' (" + mime + "), extracted text]\n" + text);
    }

    /**
     * Shared extraction hook for every provider mapper: base64 payload → plain text
     * (XLSX/ODS/PDF), capped at {@link #MAX_INLINE_TEXT_CHARS}; {@code null} when the
     * format is not extractable in-JVM or the payload cannot be decoded. Never fetches.
     */
    public static String extractDocumentText(String mime, String filename, String data) {
        if (!DocumentText.isExtractable(mime, filename)) {
            return null;
        }
        String b64 = stripDataUrl(data).replaceAll("\\s+", "");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String text = DocumentText.extract(mime, filename, bytes);
        if (text == null) {
            return null;
        }
        if (text.length() > MAX_INLINE_TEXT_CHARS) {
            text = text.substring(0, MAX_INLINE_TEXT_CHARS) + "\n[…truncated at "
                    + MAX_INLINE_TEXT_CHARS + " chars]";
        }
        return text;
    }

    /** An explicit in-band marker that an attachment could not be sent — never a silent drop. */
    static Map<String, Object> droppedNotice(Map<String, Object> block, String kind, String why) {
        String name = ProviderStream.trim(block.get("name"));
        String mime = ProviderStream.trim(block.get("mime_type"));
        String label = name.isEmpty() ? kind : name + (mime.isEmpty() ? "" : " (" + mime + ")");
        return Map.of("type", "text", "text",
                "[Attachment '" + label + "' was not sent to the model: " + why + ".]");
    }

    /** MIMEs and extensions whose bytes are safe to decode as UTF-8 prompt text. */
    static boolean isTextLike(String mime, String filename) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.startsWith("text/")) {
            return true;
        }
        if (TEXT_MIMES.contains(m)) {
            return true;
        }
        String n = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        for (String ext : TEXT_EXTENSIONS) {
            if (n.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static final Set<String> TEXT_MIMES = Set.of(
            "application/json", "application/xml", "application/javascript", "application/x-yaml",
            "application/yaml", "application/toml", "application/sql", "application/graphql",
            "application/x-ndjson", "application/csv", "application/xhtml+xml");

    private static final List<String> TEXT_EXTENSIONS = List.of(
            ".txt", ".md", ".markdown", ".csv", ".tsv", ".json", ".jsonl", ".ndjson", ".xml",
            ".yaml", ".yml", ".toml", ".ini", ".cfg", ".log", ".py", ".java", ".js", ".ts",
            ".jsx", ".tsx", ".c", ".h", ".cpp", ".cs", ".go", ".rs", ".rb", ".php", ".sh",
            ".sql", ".html", ".htm", ".css", ".svg");

    /** Base64 (or data-URL) → UTF-8 text, capped at {@link #MAX_INLINE_TEXT_CHARS}; null when undecodable. */
    static String decodeText(String data) {
        String b64 = stripDataUrl(data).replaceAll("\\s+", "");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.length() > MAX_INLINE_TEXT_CHARS) {
            text = text.substring(0, MAX_INLINE_TEXT_CHARS) + "\n[…truncated at "
                    + MAX_INLINE_TEXT_CHARS + " chars]";
        }
        return text;
    }

    static String mediaPayload(Map<String, Object> block) {
        if (block == null) {
            return "";
        }
        String v = ProviderStream.trim(block.get("data"));
        if (v.isEmpty()) {
            v = ProviderStream.trim(block.get("file_data"));
        }
        if (v.isEmpty()) {
            v = ProviderStream.trim(block.get("url"));
        }
        return v;
    }

    static String stripDataUrl(Object data) {
        if (data instanceof String s && s.startsWith("data:") && s.contains(",")) {
            return s.substring(s.indexOf(',') + 1);
        }
        return data instanceof String s ? s : "";
    }

    static boolean looksLikeUrl(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://");
    }

    private static Object firstNonNull(Object... vals) {
        for (Object v : vals) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
