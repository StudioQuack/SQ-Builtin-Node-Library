package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON node ({@code nodes/json}) — RFC 8259 parse/stringify, a jq-compatible query
 * subset (no JS eval), deep merge, RFC 6902 patch, flatten/unflatten.
 *
 * <p>jq subset: identity {@code .}, field access {@code .a.b}, index {@code .a[0]},
 * iteration {@code .a[]}, quoted keys {@code .["a b"]}, and pipes {@code |}. Anything
 * else (functions, {@code ..}, string interpolation) is rejected with an error rather
 * than silently mis-evaluated. Multiple results come back as an array.
 *
 * <p>Patch implements RFC 6902 add/remove/replace/move/copy/test over JSON Pointers
 * ({@code ~0}/{@code ~1} escapes, {@code -} array append). A failed {@code test} aborts
 * with an error and no partial result is emitted.
 */
public final class JsonNode implements NodeHandler {

    public static final String DEFAULT_ID = "62d91522-b258-4a71-bffc-1e36d6ca36e0";

    private final String templateId;

    public JsonNode() {
        this(DEFAULT_ID);
    }

    public JsonNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        try {
            if (request == null) {
                return DataSupport.invalidInput("request body must be a JSON object");
            }
            Map<String, Object> cfg = DataSupport.readConfig(request);
            Map<String, Object> in = DataSupport.overlayConfig(
                    DataSupport.readInput(request, helper), cfg, "value", "expression", "patch", "other");
            parseJsonText(in, "value", "other", "patch");
            String op = TransformSupport.operation(cfg, in);

            return switch (op) {
                case "parse" -> parse(in);
                case "stringify" -> stringify(cfg, in);
                case "jq" -> jq(cfg, in);
                case "merge" -> merge(in);
                case "patch" -> patch(in);
                case "flatten" -> flatten(cfg, in);
                case "unflatten" -> unflatten(in);
                default -> DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            };
        } catch (IllegalArgumentException e) {
            return DataSupport.fail(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("json failed");
        }
    }

    // ---------- parse / stringify ----------

    private Map<String, Object> parse(Map<String, Object> in) {
        Object value = in.get("value");
        if (value == null) {
            return DataSupport.invalidInput("value is required for parse.");
        }
        if (value instanceof String s) {
            try {
                return TransformSupport.ok(Json.parse(s));
            } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
                return DataSupport.invalidInput("invalid JSON text.");
            }
        }
        // already a parsed JSON value — pass through
        return TransformSupport.ok(value);
    }

    private Map<String, Object> stringify(Map<String, Object> cfg, Map<String, Object> in) {
        if (!in.containsKey("value")) {
            return DataSupport.invalidInput("value is required for stringify.");
        }
        Object value = in.get("value");
        boolean pretty = TransformSupport.cfgBool(cfg, "pretty", false);
        return TransformSupport.ok(pretty ? TransformSupport.pretty(value) : Json.write(value));
    }

    // ---------- jq subset ----------

    private Map<String, Object> jq(Map<String, Object> cfg, Map<String, Object> in) {
        Object value = in.get("value");
        if (value == null) {
            return DataSupport.invalidInput("value is required for jq.");
        }
        String expr = DataSupport.inStr(in, "expression", DataSupport.cfgStr(cfg, "path", null));
        if (expr == null) {
            return DataSupport.invalidInput("expression is required for jq.");
        }
        List<Object> current = new ArrayList<>();
        current.add(value);
        for (String stage : expr.trim().split("\\|")) {
            String s = stage.trim();
            if (s.isEmpty()) {
                return DataSupport.invalidInput("unsupported jq expression: empty pipe stage");
            }
            List<Object> next = new ArrayList<>();
            for (Object v : current) {
                if (!applyStage(s, v, next)) {
                    return DataSupport.invalidInput("unsupported jq expression: " + s);
                }
            }
            current = next;
        }
        return TransformSupport.ok(current.size() == 1 ? current.get(0) : current);
    }

    /** One pipe stage: a chain of .name / ["key"] / [n] / [] steps. False on unsupported syntax. */
    private static boolean applyStage(String stage, Object value, List<Object> out) {
        if (stage.isEmpty() || stage.charAt(0) != '.') {
            return false;
        }
        List<Object> current = new ArrayList<>();
        current.add(value);
        int i = 1;
        while (i < stage.length()) {
            char c = stage.charAt(i);
            if (c == '[') {
                int close = stage.indexOf(']', i);
                if (close < 0) {
                    return false;
                }
                String inside = stage.substring(i + 1, close).trim();
                List<Object> next = new ArrayList<>();
                if (inside.isEmpty()) {
                    for (Object v : current) {
                        if (v instanceof List<?> list) {
                            next.addAll(list);
                        } else if (v instanceof Map<?, ?> m) {
                            next.addAll(m.values());
                        }
                    }
                } else if (inside.matches("-?\\d+")) {
                    int idx = Integer.parseInt(inside);
                    for (Object v : current) {
                        if (v instanceof List<?> list) {
                            int real = idx < 0 ? list.size() + idx : idx;
                            next.add(real >= 0 && real < list.size() ? list.get(real) : null);
                        } else {
                            next.add(null);
                        }
                    }
                } else if (inside.startsWith("\"") && inside.endsWith("\"") && inside.length() >= 2) {
                    String key = inside.substring(1, inside.length() - 1);
                    for (Object v : current) {
                        next.add(v instanceof Map<?, ?> m ? m.get(key) : null);
                    }
                } else {
                    return false;
                }
                current = next;
                i = close + 1;
            } else {
                if (c == '.') {
                    // segment separator between field steps (.a.b)
                    i++;
                }
                int start = i;
                while (i < stage.length()) {
                    char ch = stage.charAt(i);
                    if (ch == '.' || ch == '[') {
                        break;
                    }
                    i++;
                }
                String name = stage.substring(start, i);
                if (name.isEmpty() || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    return false;
                }
                List<Object> next = new ArrayList<>();
                for (Object v : current) {
                    next.add(v instanceof Map<?, ?> m ? m.get(name) : null);
                }
                current = next;
            }
        }
        out.addAll(current);
        return true;
    }

    // ---------- merge ----------

    private Map<String, Object> merge(Map<String, Object> in) {
        Object value = in.get("value");
        if (value == null) {
            return DataSupport.invalidInput("value is required for merge.");
        }
        if (value instanceof List<?> list) {
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Object el : list) {
                if (!(el instanceof Map<?, ?> m)) {
                    return DataSupport.invalidInput("merge array elements must be objects.");
                }
                merged = deepMerge(merged, EmulatorUtil.mutableMap(m));
            }
            return TransformSupport.ok(merged);
        }
        Object other = in.get("other");
        if (value instanceof Map<?, ?> && other instanceof Map<?, ?>) {
            return TransformSupport.ok(deepMerge(
                    EmulatorUtil.mutableMap(value), EmulatorUtil.mutableMap(other)));
        }
        return DataSupport.invalidInput("merge requires an array of objects or an 'other' object.");
    }

    /** Right wins on scalar/array conflicts; maps merge recursively. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> deepMerge(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> out = (Map<String, Object>) TransformSupport.deepCopy(left);
        for (Map.Entry<String, Object> e : right.entrySet()) {
            Object existing = out.get(e.getKey());
            if (existing instanceof Map && e.getValue() instanceof Map<?, ?> rm) {
                out.put(e.getKey(), deepMerge((Map<String, Object>) existing, EmulatorUtil.mutableMap(rm)));
            } else {
                out.put(e.getKey(), TransformSupport.deepCopy(e.getValue()));
            }
        }
        return out;
    }

    // ---------- RFC 6902 patch ----------

    private Map<String, Object> patch(Map<String, Object> in) {
        Object value = in.get("value");
        List<Object> ops = TransformSupport.listAt(in, "patch");
        if (value == null) {
            return DataSupport.invalidInput("value is required for patch.");
        }
        if (ops == null) {
            return DataSupport.invalidInput("patch is required and must be a JSON array of RFC 6902 operations.");
        }
        Object doc = TransformSupport.deepCopy(value);
        try {
            for (Object el : ops) {
                if (!(el instanceof Map<?, ?> opMap)) {
                    return DataSupport.invalidInput("patch entries must be objects.");
                }
                String opName = EmulatorUtil.str(opMap.get("op"));
                String path = EmulatorUtil.str(opMap.get("path"));
                doc = applyPatchOp(doc, opName, path, opMap.get("value"), EmulatorUtil.str(opMap.get("from")));
            }
        } catch (PatchException e) {
            return DataSupport.fail(e.getMessage());
        }
        return TransformSupport.ok(doc);
    }

    private static Object applyPatchOp(Object doc, String op, String path, Object value, String from) {
        switch (op) {
            case "add" -> {
                return pointerSet(doc, path, TransformSupport.deepCopy(value), true);
            }
            case "replace" -> {
                if (pointerGet(doc, path).missing) {
                    throw new PatchException("replace target does not exist: " + path);
                }
                return pointerSet(doc, path, TransformSupport.deepCopy(value), false);
            }
            case "remove" -> {
                PointerResult removed = pointerGet(doc, path);
                if (removed.missing) {
                    throw new PatchException("remove target does not exist: " + path);
                }
                return pointerRemove(doc, path);
            }
            case "move" -> {
                PointerResult moved = pointerGet(doc, from);
                if (moved.missing) {
                    throw new PatchException("move source does not exist: " + from);
                }
                Object without = pointerRemove(doc, from);
                return pointerSet(without, path, moved.value, true);
            }
            case "copy" -> {
                PointerResult copied = pointerGet(doc, from);
                if (copied.missing) {
                    throw new PatchException("copy source does not exist: " + from);
                }
                return pointerSet(doc, path, TransformSupport.deepCopy(copied.value), true);
            }
            case "test" -> {
                PointerResult tested = pointerGet(doc, path);
                if (tested.missing || !TransformSupport.deepEquals(tested.value, value, false)) {
                    throw new PatchException("test operation failed at: " + path);
                }
                return doc;
            }
            default -> throw new PatchException("unsupported patch op: " + op);
        }
    }

    private record PointerResult(Object value, boolean missing) {
    }

    private static final class PatchException extends RuntimeException {
        PatchException(String message) {
            super(message);
        }
    }

    private static List<String> pointerSegments(String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return List.of();
        }
        if (!pointer.startsWith("/")) {
            throw new PatchException("invalid JSON pointer: " + pointer);
        }
        List<String> segs = new ArrayList<>();
        for (String raw : pointer.substring(1).split("/", -1)) {
            segs.add(raw.replace("~1", "/").replace("~0", "~"));
        }
        return segs;
    }

    private static PointerResult pointerGet(Object doc, String pointer) {
        Object cur = doc;
        for (String seg : pointerSegments(pointer)) {
            if (cur instanceof Map<?, ?> m) {
                if (!m.containsKey(seg)) {
                    return new PointerResult(null, true);
                }
                cur = m.get(seg);
            } else if (cur instanceof List<?> list && seg.matches("\\d+")) {
                int idx = Integer.parseInt(seg);
                if (idx >= list.size()) {
                    return new PointerResult(null, true);
                }
                cur = list.get(idx);
            } else {
                return new PointerResult(null, true);
            }
        }
        return new PointerResult(cur, false);
    }

    /** add/replace at a pointer; returns the (possibly new) root. */
    @SuppressWarnings("unchecked")
    private static Object pointerSet(Object doc, String pointer, Object value, boolean add) {
        List<String> segs = pointerSegments(pointer);
        if (segs.isEmpty()) {
            return value;
        }
        Object parent = doc;
        for (int i = 0; i < segs.size() - 1; i++) {
            parent = child(parent, segs.get(i));
            if (parent == null) {
                throw new PatchException("path does not exist: " + pointer);
            }
        }
        String last = segs.get(segs.size() - 1);
        if (parent instanceof Map<?, ?> m) {
            if (!add && !m.containsKey(last)) {
                throw new PatchException("replace target does not exist: " + pointer);
            }
            ((Map<String, Object>) parent).put(last, value);
            return doc;
        }
        if (parent instanceof List<?> list) {
            List<Object> l = (List<Object>) parent;
            if ("-".equals(last)) {
                if (!add) {
                    throw new PatchException("invalid replace target: " + pointer);
                }
                l.add(value);
                return doc;
            }
            if (!last.matches("\\d+")) {
                throw new PatchException("invalid array index: " + pointer);
            }
            int idx = Integer.parseInt(last);
            if (add) {
                if (idx > l.size()) {
                    throw new PatchException("array index out of bounds: " + pointer);
                }
                l.add(idx, value);
            } else {
                if (idx >= l.size()) {
                    throw new PatchException("array index out of bounds: " + pointer);
                }
                l.set(idx, value);
            }
            return doc;
        }
        throw new PatchException("path does not exist: " + pointer);
    }

    @SuppressWarnings("unchecked")
    private static Object pointerRemove(Object doc, String pointer) {
        List<String> segs = pointerSegments(pointer);
        if (segs.isEmpty()) {
            return null;
        }
        Object parent = doc;
        for (int i = 0; i < segs.size() - 1; i++) {
            parent = child(parent, segs.get(i));
            if (parent == null) {
                throw new PatchException("path does not exist: " + pointer);
            }
        }
        String last = segs.get(segs.size() - 1);
        if (parent instanceof Map<?, ?> m) {
            ((Map<String, Object>) parent).remove(last);
            return doc;
        }
        if (parent instanceof List<?> list && last.matches("\\d+")) {
            int idx = Integer.parseInt(last);
            if (idx >= list.size()) {
                throw new PatchException("array index out of bounds: " + pointer);
            }
            ((List<Object>) parent).remove(idx);
            return doc;
        }
        throw new PatchException("path does not exist: " + pointer);
    }

    private static Object child(Object node, String seg) {
        if (node instanceof Map<?, ?> m) {
            return m.get(seg);
        }
        if (node instanceof List<?> list && seg.matches("\\d+")) {
            int idx = Integer.parseInt(seg);
            return idx < list.size() ? list.get(idx) : null;
        }
        return null;
    }

    // ---------- flatten / unflatten ----------

    private Map<String, Object> flatten(Map<String, Object> cfg, Map<String, Object> in) {
        Object value = in.get("value");
        if (!(value instanceof Map) && !(value instanceof List)) {
            return DataSupport.invalidInput("value is required and must be an object or array for flatten.");
        }
        String prefix = DataSupport.cfgStr(cfg, "path", "");
        Map<String, Object> out = new LinkedHashMap<>();
        flattenInto(value, prefix == null ? "" : prefix, out);
        return TransformSupport.ok(out);
    }

    /** Empty objects/arrays are emitted as-is at their key; everything else recurses. */
    private static void flattenInto(Object v, String prefix, Map<String, Object> out) {
        if (v instanceof Map<?, ?> m && !m.isEmpty()) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey();
                flattenInto(e.getValue(), key, out);
            }
            return;
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                flattenInto(list.get(i), prefix.isEmpty() ? String.valueOf(i) : prefix + "." + i, out);
            }
            return;
        }
        out.put(prefix, v);
    }

    private Map<String, Object> unflatten(Map<String, Object> in) {
        Object value = in.get("value");
        if (!(value instanceof Map<?, ?> m)) {
            return DataSupport.invalidInput("value is required and must be an object for unflatten.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            TransformSupport.setPath(out, String.valueOf(e.getKey()), TransformSupport.deepCopy(e.getValue()));
        }
        return TransformSupport.ok(out);
    }

    /** Config stores objects as JSON text. A wired object is left as-is. */
    private static void parseJsonText(Map<String, Object> in, String... keys) {
        if (in == null || keys == null) {
            return;
        }
        for (String key : keys) {
            Object value = in.get(key);
            if (!(value instanceof String text)) {
                continue;
            }
            String trimmed = text.trim();
            if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                continue;
            }
            try {
                in.put(key, Json.parse(trimmed));
            } catch (studioquack.StudioQuackError e) {
                throw e;
            } catch (Exception ignored) {
                // leave the text; parse reports invalid JSON
            }
        }
    }
}
