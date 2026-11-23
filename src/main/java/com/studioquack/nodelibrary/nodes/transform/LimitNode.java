package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Limit node ({@code nodes/limit}) — first/last slice of a JSON array.
 *
 * <p>{@code first} keeps the leading N items, {@code last} the trailing N.
 * N comes from INPUT {@code max_items} over config {@code max_items} (default 1 when
 * omitted). Explicit {@code 0} returns an empty slice; negative values are rejected.
 * Positive values clamp to 1–10000.
 */
public final class LimitNode implements NodeHandler {

    public static final String DEFAULT_ID = "b8c74e8d-456d-4b80-b24b-c939d1ba7b0a";

    private static final int MAX_LIMIT = 10_000;

    private final String templateId;

    public LimitNode() {
        this(DEFAULT_ID);
    }

    public LimitNode(String templateId) {
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

            Object rawItems = in.get("items");
            if (rawItems instanceof String s && !s.trim().startsWith("[")) {
                return DataSupport.invalidInput("items is required and must be a JSON array.");
            }
            List<Object> items = TransformSupport.listAt(in, "items");
            if (items == null) {
                return DataSupport.invalidInput("items is required and must be a JSON array.");
            }
            int n = resolveMaxItems(in, cfg);

            return switch (op) {
                case "first" -> TransformSupport.ok(new ArrayList<>(items.subList(0, Math.min(n, items.size()))));
                case "last" -> TransformSupport.ok(
                        new ArrayList<>(items.subList(Math.max(0, items.size() - n), items.size())));
                default -> DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            };
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("limit failed");
        }
    }

    static int resolveMaxItems(Map<String, Object> in, Map<String, Object> cfg) {
        if (in != null && in.containsKey("max_items") && in.get("max_items") != null) {
            int n = parseMaxItems(in.get("max_items"));
            if (n < 0) {
                throw new IllegalArgumentException("max_items must be a non-negative integer");
            }
            if (n == 0) {
                return 0;
            }
            return Math.min(n, MAX_LIMIT);
        }
        int fromCfg = DataSupport.cfgInt(cfg, "max_items", 1);
        fromCfg = Math.max(1, Math.min(fromCfg, MAX_LIMIT));
        return fromCfg;
    }

    private static int parseMaxItems(Object raw) {
        if (raw instanceof Number n) {
            if (raw instanceof Double d && d != Math.rint(d)) {
                throw new IllegalArgumentException("max_items must be an integer");
            }
            if (raw instanceof Float f && f != Math.rint(f)) {
                throw new IllegalArgumentException("max_items must be an integer");
            }
            return n.intValue();
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                throw new IllegalArgumentException("max_items must be a non-negative integer");
            }
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("max_items must be a non-negative integer");
            }
        }
        throw new IllegalArgumentException("max_items must be a non-negative integer");
    }
}
