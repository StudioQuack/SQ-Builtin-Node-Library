package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aggregate node ({@code nodes/aggregate}) — in-process group-by over a JSON array.
 *
 * <p>Operations (config {@code operation}, INPUT {@code operation} wins):
 * <ul>
 *   <li>{@code fields} — collect the values of one field across all items into
 *   {@code {rename||field: [values]}}. {@code merge_lists=true} flattens array values
 *   into the same list; {@code keep_null=true} keeps nulls (dropped by default).</li>
 *   <li>{@code all_items} — combine every item into {@code {rename||"items": [items]}}.</li>
 * </ul>
 */
public final class AggregateNode implements NodeHandler {

    public static final String DEFAULT_ID = "ccd54672-4e3b-489c-967b-ba43a3e3d9ca";

    private final String templateId;

    public AggregateNode() {
        this(DEFAULT_ID);
    }

    public AggregateNode(String templateId) {
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
                    DataSupport.readInput(request, helper), cfg, "items", "field");
            String op = TransformSupport.operation(cfg, in);

            List<Object> items = TransformSupport.listAt(in, "items");
            if (items == null) {
                return DataSupport.invalidInput("items is required and must be a JSON array.");
            }

            return switch (op) {
                case "fields" -> fields(cfg, in, items);
                case "all_items" -> allItems(cfg, items);
                default -> DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            };
        } catch (IllegalArgumentException e) {
            return DataSupport.fail(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("aggregate failed");
        }
    }

    private Map<String, Object> fields(Map<String, Object> cfg, Map<String, Object> in, List<Object> items) {
        String field = DataSupport.inStr(in, "field", DataSupport.cfgStr(cfg, "field", null));
        if (field == null) {
            return DataSupport.invalidInput("field is required for operation=fields.");
        }
        boolean mergeLists = TransformSupport.cfgBool(cfg, "merge_lists", false);
        boolean keepNull = TransformSupport.cfgBool(cfg, "keep_null", false);
        String rename = DataSupport.cfgStr(cfg, "rename", null);

        List<Object> values = new ArrayList<>();
        for (Object item : items) {
            Object v = TransformSupport.getPath(item, field);
            if (v == null) {
                if (keepNull) {
                    values.add(null);
                }
                continue;
            }
            if (mergeLists && v instanceof List<?> list) {
                values.addAll(list);
            } else {
                values.add(v);
            }
        }
        return TransformSupport.ok(Map.of(rename != null ? rename : field, values));
    }

    private Map<String, Object> allItems(Map<String, Object> cfg, List<Object> items) {
        String rename = DataSupport.cfgStr(cfg, "rename", null);
        return TransformSupport.ok(Map.of(rename != null ? rename : "items", items));
    }
}
