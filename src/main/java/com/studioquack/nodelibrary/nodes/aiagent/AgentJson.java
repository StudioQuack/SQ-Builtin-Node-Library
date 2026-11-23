package com.studioquack.nodelibrary.nodes.aiagent;

import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * JSON sanitisation, string coercions, multimodal file blocks, and log-shape helpers.
 *
 * <p>Never logs tokens, prompts, or file bytes — only ids, counts, and structural shapes.
 */
final class AgentJson {

    static final Set<String> MEDIA_BLOCK_KINDS = Set.of("image", "audio", "video", "document");

    private static final int LOG_CAP = 800;

    private AgentJson() {
        // namespace — never instantiated
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new LinkedHashMap<>();
    }

    static Map<String, Object> mutableMap(Object o) {
        return new LinkedHashMap<>(asMap(o));
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) {
        if (o instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Non-blank stripped Java String, else {@code null} (Python {@code isinstance(..., str) and strip()}). */
    static String nonBlankString(Object o) {
        if (!(o instanceof String s)) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    /**
     * Interpret a port value as a conversation thread: the Duckbot start {@code msg}
     * port's conversation-mode form (a JSON array string of {@code {role, content}}
     * maps) or an already-parsed list of the same shape. Returns normalized
     * {@code {role, content}} maps, or {@code null} when the value is not a thread —
     * callers then treat it as plain text. Strict on purpose: one non-message item
     * disqualifies the whole list, so a plain-text prompt that merely parses as JSON
     * (e.g. {@code [1, 2, 3]}) is never eaten. Never throws.
     */
    static List<Map<String, Object>> parseThreadMessages(Object raw) {
        try {
            Object parsed = raw instanceof String ? maybeParseString(raw) : raw;
            if (!(parsed instanceof List<?> list) || list.isEmpty()) {
                return null;
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?>)) {
                    return null;
                }
                Map<String, Object> row = asMap(item);
                String role = nonBlankString(row.get("role"));
                if (role == null) {
                    return null;
                }
                role = role.toLowerCase(Locale.ROOT);
                if (!role.equals("user") && !role.equals("assistant") && !role.equals("system")) {
                    return null;
                }
                Object content = row.get("content");
                if (!(content instanceof String) && !(content instanceof List)) {
                    return null;
                }
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", role);
                msg.put("content", content);
                out.add(msg);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    static void log(StudioQuackHelper helper, String message) {
        if (helper == null) {
            return;
        }
        String msg = message == null ? "" : message;
        if (msg.length() > LOG_CAP) {
            msg = msg.substring(0, LOG_CAP) + "...<capped>";
        }
        helper.log(msg, "info");
    }

    static Object safeJson(Object obj) {
        try {
            Json.write(obj);
            return obj;
        } catch (Exception e) {
            return sanitizeForJson(obj);
        }
    }

    static Object sanitizeForJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String s) {
            return s;
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(str(sanitizeForJson(e.getKey())), sanitizeForJson(e.getValue()));
            }
            return out;
        }
        if (obj instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(sanitizeForJson(item));
            }
            return out;
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj;
        }
        return str(obj);
    }

    static String extractTextFromContent(Object content) {
        return extractTextFromContent(content, 0);
    }

    static String extractTextFromContent(Object content, int depth) {
        if (content == null || depth > 10) {
            return "";
        }
        if (content instanceof String s) {
            return s.strip();
        }
        if (content instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text instanceof String ts && !ts.isBlank()) {
                return ts.strip();
            }
            for (String key : List.of("content", "value", "result", "response", "output", "data", "llm_output")) {
                if (map.containsKey(key)) {
                    String t = extractTextFromContent(map.get(key), depth + 1);
                    if (!t.isEmpty()) {
                        return t;
                    }
                }
            }
            for (Object v : map.values()) {
                if (v instanceof Map || v instanceof List) {
                    String t = extractTextFromContent(v, depth + 1);
                    if (!t.isEmpty()) {
                        return t;
                    }
                }
            }
            return "";
        }
        if (content instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object b : list) {
                String t = extractTextFromContent(b, depth + 1);
                if (!t.isEmpty()) {
                    parts.add(t);
                }
            }
            return String.join("\n", parts).strip();
        }
        return str(content).strip();
    }

    /** Parse JSON, a {@code :::result:} stdout marker, or a log-prefixed structured tail (JSON only). */
    static Object maybeParseString(Object value) {
        if (!(value instanceof String raw)) {
            return value;
        }
        String s = raw.strip();
        String marker = ":::result:";
        int mi = s.indexOf(marker);
        if (mi >= 0) {
            s = s.substring(mi + marker.length()).strip();
        }
        s = stripCodeFence(s);
        if (!isWrappedJson(s)) {
            int firstObj = indexOfJsonStart(s);
            if (firstObj >= 0) {
                String candidate = s.substring(firstObj).strip();
                if (isWrappedJson(candidate)) {
                    s = candidate;
                } else {
                    return value;
                }
            } else {
                return value;
            }
        }
        if (!isWrappedJson(s)) {
            return value;
        }
        try {
            return Json.parse(s);
        } catch (Exception e) {
            return value;
        }
    }

    /** LLMs frequently wrap JSON in {@code ```json ... ```} fences — unwrap them. */
    private static String stripCodeFence(String s) {
        if (s == null || !s.startsWith("```") || !s.endsWith("```") || s.length() < 6) {
            return s;
        }
        int nl = s.indexOf('\n');
        if (nl < 0) {
            return s; // single-line fence is not a fence
        }
        String body = s.substring(nl + 1, s.length() - 3).strip();
        return body.isEmpty() ? s : body;
    }

    private static boolean isWrappedJson(String s) {
        return s != null && ((s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]")));
    }

    private static int indexOfJsonStart(String s) {
        int brace = s.indexOf('{');
        int bracket = s.indexOf('[');
        if (brace < 0) {
            return bracket;
        }
        if (bracket < 0) {
            return brace;
        }
        return Math.min(brace, bracket);
    }

    /** First non-empty list entry (Python {@code _unwrap_list_envelope}). Empty list → {@code {}}. */
    static Object unwrapListEnvelope(Object value) {
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        while (value instanceof List<?> list) {
            if (visited.containsKey(value)) {
                break;
            }
            visited.put(value, Boolean.TRUE);
            if (list.isEmpty()) {
                return new LinkedHashMap<>();
            }
            Object chosen = null;
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                if (item instanceof Map<?, ?> m && m.isEmpty()) {
                    continue;
                }
                if (item instanceof List<?> l && l.isEmpty()) {
                    continue;
                }
                if (item instanceof String st && st.isBlank()) {
                    continue;
                }
                chosen = item;
                break;
            }
            if (chosen == null) {
                chosen = list.get(0);
            }
            if (chosen == value) {
                break;
            }
            value = chosen;
        }
        return value;
    }

    /** Unwrap a single-item list envelope (Python {@code _unwrap_single_item_envelope}). */
    static Object unwrapSingleItemEnvelope(Object value) {
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        while (value instanceof List<?> list) {
            if (visited.containsKey(value)) {
                break;
            }
            visited.put(value, Boolean.TRUE);
            List<Object> meaningful = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                if (item instanceof Map<?, ?> m && m.isEmpty()) {
                    continue;
                }
                if (item instanceof List<?> l && l.isEmpty()) {
                    continue;
                }
                if (item instanceof String st && st.isBlank()) {
                    continue;
                }
                meaningful.add(item);
            }
            if (meaningful.isEmpty()) {
                return new LinkedHashMap<>();
            }
            if (meaningful.size() == 1) {
                value = meaningful.get(0);
                continue;
            }
            return meaningful;
        }
        return value;
    }

    /** Prefix of a sealed client-store handle ({@code sq-file:<id>}). */
    static final String SQ_FILE_PREFIX = "sq-file:";

    /** True when {@code source} is {@code sq-file} or starts with {@code sq-file:}. */
    static boolean isSqFileSource(Object source) {
        String s = str(source).strip();
        if (s.isEmpty()) {
            return false;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return "sq-file".equals(lower) || lower.startsWith(SQ_FILE_PREFIX);
    }

    static boolean sourceStartsWithSqFileColon(Object source) {
        return str(source).strip().toLowerCase(Locale.ROOT).startsWith(SQ_FILE_PREFIX);
    }

    /**
     * Handle identity: non-blank {@code fileId}, else the {@code sq-file:} source string.
     */
    static String fileIdOf(Map<String, Object> m) {
        if (m == null) {
            return "";
        }
        String id = str(m.get("fileId")).strip();
        if (!id.isEmpty()) {
            return id;
        }
        String source = str(m.get("source")).strip();
        return source.toLowerCase(Locale.ROOT).startsWith(SQ_FILE_PREFIX) ? source : "";
    }

    /** Handle-bearing: non-blank {@code fileId} or {@code source} starting with {@code sq-file:}. */
    static boolean hasFileHandle(Map<String, Object> m) {
        if (m == null) {
            return false;
        }
        return !str(m.get("fileId")).isBlank() || sourceStartsWithSqFileColon(m.get("source"));
    }

    static Map<String, Object> filePlaceholder(Object name) {
        String n = str(name).strip();
        if (n.isEmpty()) {
            n = "file";
        }
        return Map.of("type", "text", "text", "[file: " + n + "]");
    }

    static boolean isFileShapedBlock(Map<String, Object> m) {
        if (m == null) {
            return false;
        }
        String type = str(m.get("type")).isBlank() ? str(m.get("kind")) : str(m.get("type"));
        type = type.toLowerCase(Locale.ROOT);
        if ("text".equals(type) || "input_text".equals(type)) {
            return false;
        }
        if ("file".equals(type)) {
            type = "document";
        }
        return MEDIA_BLOCK_KINDS.contains(type) || hasFileHandle(m);
    }

    static String mimeOf(Map<String, Object> m) {
        if (m == null) {
            return "";
        }
        Object mime = m.get("mime_type");
        if (mime == null) {
            mime = m.get("mimeType");
        }
        return str(mime);
    }

    /**
     * Same file as one on {@code inputFiles}: matching {@code fileId}, matching
     * {@code sq-file:} source, or (when those are absent) matching name+mime.
     */
    static boolean carriedOnInputFiles(Map<String, Object> block, List<Map<String, Object>> inputFiles) {
        if (block == null || inputFiles == null) {
            return false;
        }
        String id = fileIdOf(block);
        String source = str(block.get("source")).strip();
        boolean sourceHandle = sourceStartsWithSqFileColon(source);
        String name = str(block.get("name"));
        String mime = mimeOf(block);
        for (Map<String, Object> f : inputFiles) {
            if (f == null) {
                continue;
            }
            String fid = fileIdOf(f);
            if (!id.isEmpty() && id.equals(fid)) {
                return true;
            }
            String fsrc = str(f.get("source")).strip();
            if (sourceHandle && source.equals(fsrc)) {
                return true;
            }
            if (id.isEmpty() && fid.isEmpty()
                    && !sourceHandle && !sourceStartsWithSqFileColon(fsrc)
                    && !name.isBlank() && name.equals(str(f.get("name")))
                    && mime.equals(mimeOf(f))) {
                return true;
            }
        }
        return false;
    }

    static List<Map<String, Object>> coerceFilesList(Object files) {
        return coerceFilesListDetailed(files).rows();
    }

    /** Coercion outcome: the usable rows plus how many entries were dropped as empty. */
    record CoerceResult(List<Map<String, Object>> rows, int dropped) {
    }

    /**
     * Coerces the raw {@code files}/{@code voices} input into normalized file rows. Entries that
     * carry NO payload and NO resolvable handle — blank strings, empty maps, name-only rows —
     * are DROPPED, not forwarded: the duckbot create-node probe sends exactly those
     * ({@code files:[{}]}, {@code voices:[""]}) for "no attachment", and the resulting
     * empty-data block on the model wire makes providers reject the request or hang on it (the
     * call then outlives the stateful request deadline and surfaces as SANDBOX_DEADLINE).
     */
    static CoerceResult coerceFilesListDetailed(Object files) {
        List<Map<String, Object>> out = new ArrayList<>();
        int dropped = 0;
        Object parsed = files;
        if (parsed instanceof String) {
            parsed = maybeParseString(parsed);
        }
        if (parsed instanceof Map<?, ?> one) {
            parsed = List.of(one);
        }
        if (!(parsed instanceof List<?> list)) {
            return new CoerceResult(out, 0);
        }
        for (Object f : list) {
            if (f instanceof String url) {
                if (url.isBlank()) {
                    dropped++;
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", "document");
                row.put("mime_type", "application/octet-stream");
                row.put("source", "url");
                row.put("data", url);
                row.put("name", "");
                out.add(row);
                continue;
            }
            if (!(f instanceof Map<?, ?> fm)) {
                dropped++;
                continue;
            }
            Map<String, Object> m = asMap(fm);
            String kind = str(m.get("kind")).isBlank() ? str(m.get("type")) : str(m.get("kind"));
            kind = kind.toLowerCase(Locale.ROOT);
            if (kind.isBlank()) {
                kind = "document";
            }
            if ("file".equals(kind)) {
                kind = "document";
            }
            if (!MEDIA_BLOCK_KINDS.contains(kind)) {
                kind = "document";
            }
            String rawSource = str(m.get("source"));
            String sourceLower = rawSource.toLowerCase(Locale.ROOT);
            String source;
            if (sourceLower.isBlank()) {
                source = "base64";
            } else if ("sq-file".equals(sourceLower) || sourceLower.startsWith(SQ_FILE_PREFIX)) {
                // Keep handle sources — do not remap sq-file / sq-file:… to base64.
                source = sourceLower.startsWith(SQ_FILE_PREFIX) ? rawSource.strip() : "sq-file";
            } else if (!Set.of("base64", "url", "file_id").contains(sourceLower)) {
                source = "base64";
            } else {
                source = sourceLower;
            }
            String fileId = "";
            Object rawFileId = m.get("fileId");
            if (rawFileId != null && !str(rawFileId).isBlank()) {
                fileId = str(rawFileId).strip();
            } else if (sourceLower.startsWith(SQ_FILE_PREFIX)) {
                fileId = rawSource.strip();
            }
            Object data = m.get("data");
            if (data == null) {
                data = m.get("content");
            }
            if (data == null) {
                data = m.get("url");
            }
            Object mime = m.get("mime_type");
            if (mime == null) {
                mime = m.get("mimeType");
            }
            boolean handleOnly = !fileId.isBlank() || sourceLower.startsWith(SQ_FILE_PREFIX);
            if (!handleOnly && str(data).isBlank()) {
                // No inline payload and no store handle — nothing a model could consume.
                dropped++;
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", kind);
            row.put("mime_type", mime == null || str(mime).isBlank() ? "application/octet-stream" : mime);
            row.put("source", source);
            if (!handleOnly) {
                row.put("data", data == null ? "" : data);
            }
            row.put("name", m.get("name") == null ? "" : m.get("name"));
            if (!fileId.isBlank()) {
                row.put("fileId", fileId);
            }
            out.add(row);
        }
        return new CoerceResult(out, dropped);
    }

    static List<Map<String, Object>> filesToContentBlocks(List<Map<String, Object>> filesList) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (filesList == null) {
            return out;
        }
        for (Map<String, Object> f : filesList) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", f.get("kind"));
            block.put("mime_type", f.get("mime_type"));
            block.put("source", f.get("source"));
            if (!hasFileHandle(f)) {
                block.put("data", f.get("data"));
            }
            block.put("name", f.get("name") == null ? "" : f.get("name"));
            Object fid = f.get("fileId");
            if (fid != null && !str(fid).isBlank()) {
                block.put("fileId", fid);
            }
            out.add(block);
        }
        return out;
    }

    static String compactLookupText(Object value) {
        if (value == null) {
            return "";
        }
        String text = str(value).strip().toLowerCase(Locale.ROOT);
        for (String suffix : List.of(".md", ".markdown", ".json", ".py", ".txt")) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String snakeAlias(Object value) {
        if (value == null) {
            return "";
        }
        String text = str(value).strip().toLowerCase(Locale.ROOT);
        StringBuilder chars = new StringBuilder();
        boolean prevSep = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                chars.append(ch);
                prevSep = false;
            } else if (!prevSep) {
                chars.append('_');
                prevSep = true;
            }
        }
        return chars.toString().replaceAll("^_+|_+$", "");
    }

    static List<String> lookupTokens(Object value) {
        String snake = snakeAlias(value);
        List<String> out = new ArrayList<>();
        for (String p : snake.split("_")) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    static List<String> collectConnectionText(Object value, int depth) {
        List<String> out = new ArrayList<>();
        if (depth > 5 || value == null) {
            return out;
        }
        Set<String> useful = Set.of("nodeId", "nodeName", "name", "label", "title", "shortCode",
                "value", "role", "instructions", "prompt", "description");
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String k = str(e.getKey());
                Object nv = e.getValue();
                if (useful.contains(k) && (nv instanceof String || nv instanceof Number)) {
                    String s = str(nv).strip();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                } else if (nv instanceof Map || nv instanceof List) {
                    out.addAll(collectConnectionText(nv, depth + 1));
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                out.addAll(collectConnectionText(item, depth + 1));
            }
        } else if (value instanceof String || value instanceof Number) {
            String s = str(value).strip();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** Sorted-key JSON for duplicate tool-call cache keys (Python {@code json.dumps(..., sort_keys=True)}). */
    static String canonicalJson(Object o) {
        try {
            return Json.write(sortKeys(o));
        } catch (Exception e) {
            return str(o);
        }
    }

    static Object sortKeys(Object o) {
        if (o instanceof Map<?, ?> map) {
            TreeMap<String, Object> t = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                t.put(str(e.getKey()), sortKeys(e.getValue()));
            }
            return t;
        }
        if (o instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(sortKeys(item));
            }
            return out;
        }
        return o;
    }

    static String jsonString(Object o) {
        try {
            return Json.write(safeJson(o));
        } catch (Exception e) {
            return str(o);
        }
    }

    static String takeNonemptyStr(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            if (v instanceof String s && !s.isBlank()) {
                return s.strip();
            }
        }
        return null;
    }
}

