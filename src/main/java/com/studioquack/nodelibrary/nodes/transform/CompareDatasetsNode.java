package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import studioquack.StudioQuackHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compare Datasets node ({@code nodes/compare-datasets}) — in-process diff of two
 * arrays {@code {a, b}}.
 *
 * <p>Rows pair up on {@code match_fields} (INPUT wins over config; empty means
 * whole-item identity). A paired pair whose rows are equal — ignoring
 * {@code skip_fields}, strings near-matched when {@code fuzzy} — lands in
 * {@code both}; a paired pair that differs contributes its a-side to {@code only_a}
 * and its b-side to {@code only_b}; unpaired rows land in their own side.
 * {@code difference_mode} (all | both | only_a | only_b) selects the emitted buckets.
 * Duplicate keys pair in arrival order (multiset semantics).
 */
public final class CompareDatasetsNode implements NodeHandler {

    public static final String DEFAULT_ID = "b8000247-171c-44cb-a882-b99b38cbfbf9";

    private final String templateId;

    public CompareDatasetsNode() {
        this(DEFAULT_ID);
    }

    public CompareDatasetsNode(String templateId) {
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
            if (!"compare".equals(op)) {
                return DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            }

            List<Object> a = TransformSupport.listAt(in, "a");
            List<Object> b = TransformSupport.listAt(in, "b");
            if (a == null || b == null) {
                return DataSupport.invalidInput("a and b are required and must be JSON arrays.");
            }
            List<String> matchFields = TransformSupport.stringList(
                    in.get("match_fields") != null ? in.get("match_fields") : cfg.get("match_fields"));
            List<String> skipFields = TransformSupport.stringList(cfg.get("skip_fields"));
            boolean fuzzy = TransformSupport.cfgBool(cfg, "fuzzy", false);
            String mode = DataSupport.cfgStr(cfg, "difference_mode", "all");
            if (!List.of("all", "both", "only_a", "only_b").contains(mode)) {
                return DataSupport.invalidInput("unknown difference_mode: " + mode);
            }

            // index b rows by match key; queues keep duplicate keys in arrival order
            Map<String, Deque<Integer>> bByKey = new LinkedHashMap<>();
            for (int i = 0; i < b.size(); i++) {
                bByKey.computeIfAbsent(matchKey(b.get(i), matchFields), k -> new ArrayDeque<>()).add(i);
            }
            boolean[] bUsed = new boolean[b.size()];

            List<Object> both = new ArrayList<>();
            List<Object> onlyA = new ArrayList<>();
            List<Object> onlyB = new ArrayList<>();

            for (Object rowA : a) {
                Deque<Integer> candidates = bByKey.get(matchKey(rowA, matchFields));
                Integer j = null;
                while (candidates != null && !candidates.isEmpty()) {
                    int idx = candidates.poll();
                    if (!bUsed[idx]) {
                        j = idx;
                        break;
                    }
                }
                if (j == null) {
                    onlyA.add(rowA);
                    continue;
                }
                bUsed[j] = true;
                Object rowB = b.get(j);
                if (rowsEqual(rowA, rowB, skipFields, fuzzy)) {
                    both.add(rowA);
                } else {
                    onlyA.add(rowA);
                    onlyB.add(rowB);
                }
            }
            for (int i = 0; i < b.size(); i++) {
                if (!bUsed[i]) {
                    onlyB.add(b.get(i));
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            if ("all".equals(mode) || "both".equals(mode)) {
                data.put("both", both);
            }
            if ("all".equals(mode) || "only_a".equals(mode)) {
                data.put("only_a", onlyA);
            }
            if ("all".equals(mode) || "only_b".equals(mode)) {
                data.put("only_b", onlyB);
            }
            return TransformSupport.ok(data);
        } catch (IllegalArgumentException e) {
            return DataSupport.fail(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("compare failed");
        }
    }

    /** Match identity: the match-field values, or the whole item when none are set. */
    private static String matchKey(Object item, List<String> matchFields) {
        if (matchFields.isEmpty()) {
            return TransformSupport.canonical(item);
        }
        List<Object> values = new ArrayList<>(matchFields.size());
        for (String f : matchFields) {
            values.add(TransformSupport.getPath(item, f));
        }
        return TransformSupport.canonical(values);
    }

    private static boolean rowsEqual(Object a, Object b, List<String> skipFields, boolean fuzzy) {
        if (skipFields.isEmpty()) {
            return TransformSupport.deepEquals(a, b, fuzzy);
        }
        Object ca = strip(a, skipFields);
        Object cb = strip(b, skipFields);
        return TransformSupport.deepEquals(ca, cb, fuzzy);
    }

    /** Deep copy with the skipped top-level/dotted keys removed. */
    private static Object strip(Object item, List<String> skipFields) {
        if (!(item instanceof Map<?, ?>)) {
            return item;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) TransformSupport.deepCopy(item);
        for (String f : skipFields) {
            removePath(copy, f);
        }
        return copy;
    }

    private static void removePath(Map<String, Object> map, String dotted) {
        String[] segs = dotted.split("\\.");
        Map<String, Object> cur = map;
        for (int i = 0; i < segs.length - 1; i++) {
            Object next = cur.get(segs[i]);
            if (!(next instanceof Map)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) next;
            cur = m;
        }
        cur.remove(segs[segs.length - 1]);
    }
}
