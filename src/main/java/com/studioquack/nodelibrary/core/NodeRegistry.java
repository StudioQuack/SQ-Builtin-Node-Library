package com.studioquack.nodelibrary.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process {@code templateId → NodeHandler} map. Concurrent: many stateful
 * requests may be in flight against the same container.
 */
public final class NodeRegistry {

    private final ConcurrentHashMap<String, NodeHandler> byId = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> insertionOrder = new CopyOnWriteArrayList<>();

    /**
     * Register a handler. Null or blank {@code templateId()} is ignored.
     * A later register for the same id replaces the handler and keeps the original order slot.
     */
    public void register(NodeHandler handler) {
        if (handler == null) {
            return;
        }
        String id = handler.templateId();
        if (id == null || id.isBlank()) {
            return;
        }
        NodeHandler prev = byId.put(id, handler);
        if (prev == null) {
            insertionOrder.add(id);
        }
    }

    /** Handler for {@code templateId}, or {@code null} when unknown. */
    public NodeHandler get(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return null;
        }
        return byId.get(templateId);
    }

    /** Registered template ids in insertion order (never null). */
    public List<String> knownTemplateIds() {
        return new ArrayList<>(insertionOrder);
    }
}
