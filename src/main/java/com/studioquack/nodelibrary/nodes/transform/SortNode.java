package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.OfficialApiNode;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackHelper;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Sort node ({@code nodes/sort}) — in-process sort of a JSON array.
 *
 * <p>{@code type=simple} sorts by {@code keys} ([{@code {path, direction}}] from INPUT
 * or config); with no keys, items compare naturally (numbers numeric, strings
 * lexicographic, nulls last). {@code type=random} shuffles with {@link SecureRandom}
 * (CSPRNG per the definition). {@code type=code} is a sandbox-expression entitlement
 * and is refused here — the in-process library never evals.
 */
public final class SortNode implements NodeHandler {

    public static final String DEFAULT_ID = "80136193-861d-442d-bc39-bfe3674e2c68";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String templateId;

    public SortNode() {
        this(DEFAULT_ID);
    }

    public SortNode(String templateId) {
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
            Map<String, Object> in = OfficialApiNode.flattenNestedToolInput(
                    OfficialApiNode.mergeToolCallArgs(DataSupport.readInput(request, helper)));
            String op = TransformSupport.operation(cfg, in);
            if (!"sort".equals(op)) {
                return DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            }

            List<Object> items = TransformSupport.listAt(in, "items");
            if (items == null) {
                return DataSupport.invalidInput("items is required and must be a JSON array.");
            }
            String type = DataSupport.cfgStr(cfg, "type", "simple");

            switch (type) {
                case "random" -> {
                    List<Object> shuffled = new ArrayList<>(items);
                    Collections.shuffle(shuffled, RANDOM);
                    return TransformSupport.ok(shuffled);
                }
                case "code" -> {
                    return DataSupport.invalidInput(
                            "type=code requires the sandbox expression entitlement; not available in-process.");
                }
                case "simple" -> {
                    // fall through to keyed sort below
                }
                default -> {
                    return DataSupport.invalidInput("unknown type: " + type);
                }
            }

            List<Object> keysRaw = TransformSupport.listAt(in, "keys");
            if (keysRaw == null) {
                keysRaw = parseKeysJson(cfg.get("keys"));
            }
            List<SortKey> keys = new ArrayList<>();
            if (keysRaw != null) {
                for (Object k : keysRaw) {
                    if (k instanceof Map<?, ?> m) {
                        String path = EmulatorUtil.str(m.get("path")).trim();
                        if (path.isEmpty()) {
                            continue;
                        }
                        boolean desc = "desc".equalsIgnoreCase(EmulatorUtil.str(m.get("direction")).trim());
                        keys.add(new SortKey(path, desc));
                    } else {
                        String path = EmulatorUtil.str(k).trim();
                        if (!path.isEmpty()) {
                            keys.add(new SortKey(path, false));
                        }
                    }
                }
            }

            List<Object> sorted = new ArrayList<>(items);
            Comparator<Object> cmp = null;
            for (SortKey key : keys) {
                Comparator<Object> c = Comparator.nullsLast(
                        (x, y) -> TransformSupport.compareValues(
                                TransformSupport.getPath(x, key.path()), TransformSupport.getPath(y, key.path())));
                if (key.desc()) {
                    c = c.reversed();
                }
                cmp = cmp == null ? c : cmp.thenComparing(c);
            }
            if (cmp == null) {
                cmp = TransformSupport::compareValues;
            }
            sorted.sort(cmp);
            return TransformSupport.ok(sorted);
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("sort failed");
        }
    }

    /** Config {@code keys} arrives as a JSON array string; parse tolerantly. */
    private static List<Object> parseKeysJson(Object raw) {
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        String s = EmulatorUtil.str(raw).trim();
        if (s.isEmpty() || !s.startsWith("[")) {
            return null;
        }
        try {
            Object parsed = studioquack.Json.parse(s);
            return parsed instanceof List<?> list ? new ArrayList<>(list) : null;
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private record SortKey(String path, boolean desc) {
    }
}
