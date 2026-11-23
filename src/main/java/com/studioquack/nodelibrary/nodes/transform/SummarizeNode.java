package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Summarize node ({@code nodes/summarize}) — numeric/string aggregations over fields.
 *
 * <p>Emits {@code {field: {agg: value}}} for each requested field × aggregation
 * (sum | count | avg | min | max | concat; default {@code ["count"]}). Non-numeric
 * values are skipped by sum/avg/min/max; {@code count} counts non-null values;
 * {@code concat} joins string forms with {@code ,}. With no {@code fields}, only
 * {@code count} over the whole batch is supported ({@code {"count": n}}); other
 * aggregations require {@code fields}. Each {@code items} entry may be a JSON object,
 * a JSON array (positional columns), or an encoded string (CSV, {@code field=value},
 * {@code field:value}, tab/newline-separated values, or a scalar for a single field).
 */
public final class SummarizeNode implements NodeHandler {

    public static final String DEFAULT_ID = "c19e2bd3-2e6e-404a-8494-6c950a7ff04d";

    private static final List<String> KNOWN_AGGS = List.of("sum", "count", "avg", "min", "max", "concat");

    private final String templateId;

    public SummarizeNode() {
        this(DEFAULT_ID);
    }

    public SummarizeNode(String templateId) {
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
            if (!"summarize".equals(op)) {
                return DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            }

            List<Object> items = TransformSupport.listAt(in, "items");
            if (items == null) {
                return DataSupport.invalidInput("items is required and must be a JSON array.");
            }
            List<String> fields = TransformSupport.stringList(
                    in.get("fields") != null ? in.get("fields") : cfg.get("fields"));
            List<String> aggs = TransformSupport.stringList(
                    in.get("aggregations") != null ? in.get("aggregations") : cfg.get("aggregations"));
            if (aggs.isEmpty()) {
                aggs = List.of("count");
            }
            for (String agg : aggs) {
                if (!KNOWN_AGGS.contains(agg)) {
                    return DataSupport.invalidInput("unknown aggregation: " + agg);
                }
            }
            if (fields.isEmpty()) {
                if (!aggs.equals(List.of("count"))) {
                    return DataSupport.invalidInput("fields is required for aggregations other than count.");
                }
                return TransformSupport.ok(Map.of("count", (long) items.size()));
            }

            SummarizeRowSupport.CsvState csv = new SummarizeRowSupport.CsvState();
            Map<String, Object> data = new LinkedHashMap<>();
            for (String field : fields) {
                List<Object> values = new ArrayList<>();
                for (Object item : items) {
                    values.addAll(SummarizeRowSupport.valuesForField(item, field, fields, csv));
                }
                Map<String, Object> perField = new LinkedHashMap<>();
                for (String agg : aggs) {
                    Object result = aggregate(agg, values);
                    perField.put(agg, result);
                }
                data.put(field, perField);
            }
            return TransformSupport.ok(data);
        } catch (IllegalArgumentException e) {
            return DataSupport.fail(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("summarize failed");
        }
    }

    private static Object aggregate(String agg, List<Object> values) {
        switch (agg) {
            case "count" -> {
                long n = values.stream().filter(v -> v != null).count();
                return n;
            }
            case "concat" -> {
                StringBuilder sb = new StringBuilder();
                for (Object v : values) {
                    if (v == null) {
                        continue;
                    }
                    if (!sb.isEmpty()) {
                        sb.append(',');
                    }
                    sb.append(v instanceof String s ? s : TransformSupport.canonical(v));
                }
                return sb.toString();
            }
            default -> {
                // numeric aggregations skip non-numeric values
                List<Double> nums = new ArrayList<>();
                for (Object v : values) {
                    Double d = TransformSupport.toDouble(v);
                    if (d != null) {
                        nums.add(d);
                    }
                }
                if (nums.isEmpty()) {
                    return "sum".equals(agg) ? 0L : null;
                }
                double result = switch (agg) {
                    case "sum" -> nums.stream().mapToDouble(Double::doubleValue).sum();
                    case "avg" -> nums.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    case "min" -> nums.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                    case "max" -> nums.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                    default -> 0;
                };
                // integral results serialize as longs so 1+2 reads as 3, not 3.0
                if (result == Math.rint(result) && !Double.isInfinite(result) && Math.abs(result) < 1e15) {
                    return (long) result;
                }
                return result;
            }
        }
    }
}
