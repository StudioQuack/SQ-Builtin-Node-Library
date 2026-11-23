package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remove Duplicates node ({@code nodes/remove-duplicates}) — unique rows.
 *
 * <p>{@code unique} keeps the first occurrence of each identity inside the batch.
 * Identity is the canonical form of the configured {@code keys} values, or of the
 * whole item when no keys are given.
 *
 * <p>{@code unique_history} additionally drops identities already seen under the
 * configured {@code history_scope}. The definition calls for a per-environment
 * shared store; the in-process library has no store handle, so this is a bounded
 * in-JVM approximation (LRU, {@value #MAX_SCOPES} scopes × {@value #MAX_KEYS_PER_SCOPE}
 * identities) — it never grows without bound and is safe under concurrency, but it
 * does not survive restarts and is not shared across replicas.
 */
public final class RemoveDuplicatesNode implements NodeHandler {

    public static final String DEFAULT_ID = "12ce5af7-8f2c-429e-9814-51a17de8bed0";

    static final int MAX_SCOPES = 100;
    static final int MAX_KEYS_PER_SCOPE = 10_000;

    /** scope → bounded insertion-ordered identity set (LRU eviction). */
    private static final ConcurrentHashMap<String, Map<String, Boolean>> HISTORY = new ConcurrentHashMap<>();

    private final String templateId;

    public RemoveDuplicatesNode() {
        this(DEFAULT_ID);
    }

    public RemoveDuplicatesNode(String templateId) {
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
            Object rawKeysIn = in.get("keys");
            if (rawKeysIn instanceof String ks && !ks.trim().isEmpty() && !ks.trim().startsWith("[")) {
                return DataSupport.invalidInput("keys must be a JSON array.");
            }
            List<String> keys = TransformSupport.stringList(
                    in.get("keys") != null ? in.get("keys") : cfg.get("keys"));

            return switch (op) {
                case "unique" -> TransformSupport.ok(unique(items, keys));
                case "unique_history" -> TransformSupport.ok(uniqueHistory(items, keys, cfg));
                default -> DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            };
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("remove-duplicates failed");
        }
    }

    static List<Object> unique(List<Object> items, List<String> keys) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        List<Object> out = new ArrayList<>();
        for (Object item : items) {
            if (seen.putIfAbsent(identity(item, keys), Boolean.TRUE) == null) {
                out.add(item);
            }
        }
        return out;
    }

    private static List<Object> uniqueHistory(List<Object> items, List<String> keys, Map<String, Object> cfg) {
        String scope = DataSupport.cfgStr(cfg, "history_scope", "default");
        if (HISTORY.size() >= MAX_SCOPES && !HISTORY.containsKey(scope)) {
            // scope table full: degrade to batch-unique rather than growing unbounded
            return unique(items, keys);
        }
        Map<String, Boolean> history = HISTORY.computeIfAbsent(scope, s -> newLinkedSet());
        List<Object> out = new ArrayList<>();
        synchronized (history) {
            for (Object item : items) {
                String id = identity(item, keys);
                if (!history.containsKey(id)) {
                    history.put(id, Boolean.TRUE);
                    out.add(item);
                }
            }
        }
        return out;
    }

    /** Access-ordered map capped at {@link #MAX_KEYS_PER_SCOPE} (eldest evicted). */
    private static Map<String, Boolean> newLinkedSet() {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > MAX_KEYS_PER_SCOPE;
            }
        };
    }

    static String identity(Object item, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return TransformSupport.canonical(item);
        }
        if (!(item instanceof Map<?, ?>)) {
            return TransformSupport.canonical(item);
        }
        List<Object> values = new ArrayList<>(keys.size());
        for (String key : keys) {
            values.add(TransformSupport.getPath(item, key));
        }
        return TransformSupport.canonical(values);
    }
}
