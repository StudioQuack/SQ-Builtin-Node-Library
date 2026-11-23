package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edit Fields node ({@code nodes/edit-fields}) — set / keep / rebuild JSON fields.
 *
 * <p>Operations:
 * <ul>
 *   <li>{@code set} — apply {@code fields} [{@code {path, value}}] onto a copy of
 *   {@code item}. A bare path (no value) removes that path. Each list entry may be a
 *   map, a JSON string, or {@code path=value}.</li>
 *   <li>{@code keep_set} — emit only the listed paths from {@code item}.</li>
 *   <li>{@code json_output} — build from {@code fields}, or return a copy of
 *   {@code item} when {@code fields} is empty.</li>
 * </ul>
 *
 * <p>{@code dot_notation} (default true) treats paths as dotted keys. When
 * {@code include_binary} is true, an INPUT {@code file} ref passes through to the
 * OUTPUT {@code file} field untouched (platform ref-id, never inline bytes).
 */
public final class EditFieldsNode implements NodeHandler {

    public static final String DEFAULT_ID = "94443761-4a86-481d-a5e8-f8e61c34c8b0";

    private final String templateId;

    public EditFieldsNode() {
        this(DEFAULT_ID);
    }

    public EditFieldsNode(String templateId) {
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
            Map<String, Object> in = DataSupport.readInput(request, helper);
            String op = TransformSupport.operation(cfg, in);
            boolean dot = TransformSupport.cfgBool(cfg, "dot_notation", true);
            List<Field> fields = fields(in.get("fields") != null ? in.get("fields") : cfg.get("fields"));

            Map<String, Object> result;
            switch (op) {
                case "set" -> {
                    Map<String, Object> item = requireItem(in);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> edited = (Map<String, Object>) TransformSupport.deepCopy(item);
                    for (Field f : fields) {
                        if (f.path().isEmpty()) {
                            continue;
                        }
                        if (f.remove()) {
                            if (dot) {
                                TransformSupport.removePath(edited, f.path());
                            } else {
                                edited.remove(f.path());
                            }
                        } else {
                            apply(edited, f.path(), f.value(), dot);
                        }
                    }
                    result = edited;
                }
                case "keep_set" -> {
                    Map<String, Object> item = requireItem(in);
                    Map<String, Object> kept = new LinkedHashMap<>();
                    for (Field f : fields) {
                        if (f.path().isEmpty()) {
                            continue;
                        }
                        Object v = dot ? TransformSupport.getPath(item, f.path()) : item.get(f.path());
                        if (v != null) {
                            apply(kept, f.path(), TransformSupport.deepCopy(v), dot);
                        }
                    }
                    result = kept;
                }
                case "json_output" -> {
                    if (fields.isEmpty()) {
                        if (in.containsKey("item")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> copied = (Map<String, Object>) TransformSupport
                                    .deepCopy(requireItem(in));
                            result = copied;
                        } else {
                            result = new LinkedHashMap<>();
                        }
                    } else {
                        Map<String, Object> built = new LinkedHashMap<>();
                        for (Field f : fields) {
                            if (f.path().isEmpty() || f.remove()) {
                                continue;
                            }
                            apply(built, f.path(), f.value(), dot);
                        }
                        result = built;
                    }
                }
                default -> {
                    return DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
                }
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "ok");
            body.put("data", result);
            if (TransformSupport.cfgBool(cfg, "include_binary", false) && in.get("file") != null) {
                body.put("file", in.get("file"));
            }
            return DataSupport.ports(body);
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("edit-fields failed");
        }
    }

    private static Map<String, Object> requireItem(Map<String, Object> in) {
        Object item = in.get("item");
        if (item instanceof String s && s.trim().startsWith("{")) {
            try {
                item = studioquack.Json.parse(s.trim());
            } catch (studioquack.StudioQuackError e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("item must be a JSON object");
            }
        }
        if (!(item instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("item is required and must be a JSON object.");
        }
        return EmulatorUtil.mutableMap(item);
    }

    private static void apply(Map<String, Object> target, String path, Object value, boolean dot) {
        if (dot) {
            TransformSupport.setPath(target, path, value);
        } else {
            target.put(path, value);
        }
    }

    private static List<Field> fields(Object raw) {
        List<Field> out = new ArrayList<>();
        Object val = raw;
        if (val instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return out;
            }
            try {
                val = studioquack.Json.parse(t);
            } catch (studioquack.StudioQuackError e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("fields must be a JSON array of {path,value}");
            }
        }
        if (!(val instanceof List<?> list)) {
            if (val != null) {
                throw new IllegalArgumentException("fields must be a JSON array of {path,value}");
            }
            return out;
        }
        int idx = 0;
        for (Object el : list) {
            out.add(parseFieldEntry(el, idx));
            idx++;
        }
        return out;
    }

    private static Field parseFieldEntry(Object el, int index) {
        if (el instanceof Map<?, ?> m) {
            String path = EmulatorUtil.str(m.get("path")).trim();
            if (path.isEmpty()) {
                throw new IllegalArgumentException("fields[" + index + "]: path is required");
            }
            boolean remove = !m.containsKey("value");
            return new Field(path, m.get("value"), remove);
        }
        if (el instanceof String s) {
            return parseFieldString(s, index);
        }
        throw new IllegalArgumentException("fields[" + index
                + "]: must be a {path,value} object or a JSON/string instruction");
    }

    private static Field parseFieldString(String s, int index) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("fields[" + index + "]: empty field entry");
        }
        if (t.startsWith("{")) {
            try {
                Object parsed = studioquack.Json.parse(t);
                if (parsed instanceof Map<?, ?> m) {
                    return parseFieldEntry(m, index);
                }
            } catch (studioquack.StudioQuackError e) {
                throw e;
            } catch (Exception ignored) {
                // fall through to explicit error below
            }
            throw new IllegalArgumentException("fields[" + index + "]: invalid JSON field instruction");
        }
        int eq = t.indexOf('=');
        if (eq > 0) {
            String path = t.substring(0, eq).trim();
            String rawVal = t.substring(eq + 1).trim();
            if (path.isEmpty()) {
                throw new IllegalArgumentException("fields[" + index + "]: path is required before '='");
            }
            Object value = coerceFieldValue(rawVal);
            return new Field(path, value, false);
        }
        return new Field(t, null, true);
    }

    private static Object coerceFieldValue(String rawVal) {
        if (rawVal.equalsIgnoreCase("null")) {
            return null;
        }
        if (rawVal.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (rawVal.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (rawVal.startsWith("{") || rawVal.startsWith("[") || rawVal.startsWith("\"")) {
            try {
                return studioquack.Json.parse(rawVal);
            } catch (Exception e) {
                return rawVal;
            }
        }
        try {
            if (rawVal.contains(".")) {
                return Double.parseDouble(rawVal);
            }
            return Long.parseLong(rawVal);
        } catch (NumberFormatException e) {
            return rawVal;
        }
    }

    private record Field(String path, Object value, boolean remove) {
    }
}
