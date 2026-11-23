package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Rename Keys node ({@code nodes/rename-keys}) — rename JSON keys via mappings or regex.
 *
 * <p>{@code item} is an object or an array of objects. {@code mappings} (INPUT wins
 * over config) are a JSON array of {@code "from->to"} strings and/or {@code {from,to}}
 * objects. With {@code regex=false}, {@code from} is the literal key; with
 * {@code regex=true}, {@code from} is a pattern and matching keys are renamed to
 * {@code matcher.replaceAll(to)} (group refs like {@code $1} work). Mappings apply in
 * order; a key that no mapping matches is kept as-is. No eval.
 */
public final class RenameKeysNode implements NodeHandler {

    public static final String DEFAULT_ID = "4f929805-f7f7-4806-ba72-8c5102762396";

    private final String templateId;

    public RenameKeysNode() {
        this(DEFAULT_ID);
    }

    public RenameKeysNode(String templateId) {
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
            if (!"rename".equals(op)) {
                return DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            }

            Object item = in.get("item");
            if (item == null) {
                return DataSupport.invalidInput("item is required (object or array of objects).");
            }
            boolean regex = TransformSupport.cfgBool(cfg, "regex", false);
            Object rawMappings = in.get("mappings") != null ? in.get("mappings") : cfg.get("mappings");
            if (rawMappings instanceof String ms && !ms.trim().isEmpty() && !ms.trim().startsWith("[")) {
                return DataSupport.invalidInput("mappings must be a JSON array.");
            }
            if (rawMappings instanceof Map<?, ?>) {
                return DataSupport.invalidInput("mappings must be a JSON array.");
            }
            List<Mapping> mappings = mappings(rawMappings, regex);

            if (item instanceof Map<?, ?> m) {
                return TransformSupport.ok(renameOne(EmulatorUtil.mutableMap(m), mappings, regex));
            }
            if (item instanceof List<?> list) {
                List<Object> out = new ArrayList<>(list.size());
                for (Object el : list) {
                    if (!(el instanceof Map<?, ?>)) {
                        return DataSupport.invalidInput("item array elements must be objects.");
                    }
                    out.add(renameOne(EmulatorUtil.mutableMap(el), mappings, regex));
                }
                return TransformSupport.ok(out);
            }
            return DataSupport.invalidInput("item must be an object or an array of objects.");
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("rename-keys failed");
        }
    }

    private static Map<String, Object> renameOne(Map<String, Object> item, List<Mapping> mappings, boolean regex) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : item.entrySet()) {
            String key = e.getKey();
            String renamed = key;
            for (Mapping m : mappings) {
                if (regex) {
                    if (m.pattern().matcher(renamed).find()) {
                        renamed = m.pattern().matcher(renamed).replaceAll(m.to());
                    }
                } else if (renamed.equals(m.from())) {
                    renamed = m.to();
                }
            }
            out.put(renamed, e.getValue());
        }
        return out;
    }

    private static List<Mapping> mappings(Object raw, boolean regex) {
        List<Mapping> out = new ArrayList<>();
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
                throw new IllegalArgumentException("mappings must be a JSON array.");
            }
        }
        if (!(val instanceof List<?> list)) {
            if (val != null) {
                throw new IllegalArgumentException("mappings must be a JSON array.");
            }
            return out;
        }
        int i = 0;
        for (Object el : list) {
            out.add(parseMapping(el, regex, i++));
        }
        return out;
    }

    private static Mapping parseMapping(Object el, boolean regex, int index) {
        String from;
        String to;
        if (el instanceof Map<?, ?> m) {
            from = EmulatorUtil.str(m.get("from"));
            to = EmulatorUtil.str(m.get("to"));
            if (from.isEmpty()) {
                throw new IllegalArgumentException("mappings[" + index + "] {from,to} requires non-empty from.");
            }
        } else if (el instanceof String s) {
            String[] pair = arrowPair(s, index);
            from = pair[0];
            to = pair[1];
        } else {
            throw new IllegalArgumentException(
                    "mappings[" + index + "] must be a string 'from->to' or object {from,to}.");
        }
        Pattern pattern = null;
        if (regex) {
            try {
                pattern = Pattern.compile(from);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("invalid regex mapping: " + from);
            }
        }
        return new Mapping(from, to, pattern);
    }

    /** Splits {@code from->to}; only {@code ->} is accepted as the separator. */
    private static String[] arrowPair(String raw, int index) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Invalid mapping '': expected 'from->to'.");
        }
        int sep = t.indexOf("->");
        if (sep < 0) {
            throw new IllegalArgumentException("Invalid mapping '" + t + "': expected 'from->to'.");
        }
        if (sep == 0) {
            throw new IllegalArgumentException("Invalid mapping '" + t + "': expected 'from->to'.");
        }
        return new String[] {t.substring(0, sep), t.substring(sep + 2)};
    }

    private record Mapping(String from, String to, Pattern pattern) {
    }
}
