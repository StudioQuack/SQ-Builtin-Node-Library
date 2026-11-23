package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry semantics: null-safety, replacement, insertion order. */
class NodeRegistryTest {

    private static NodeHandler handler(String id) {
        return new NodeHandler() {
            @Override
            public String templateId() {
                return id;
            }

            @Override
            public Map<String, Object> handle(Map<String, Object> request,
                                              studioquack.StudioQuackHelper helper) {
                return Map.of();
            }
        };
    }

    @Test
    void registersAndLooksUpByTemplateId() {
        NodeRegistry r = new NodeRegistry();
        NodeHandler h = handler("id-1");
        r.register(h);
        assertSame(h, r.get("id-1"));
    }

    @Test
    void ignoresNullAndBlankIds() {
        NodeRegistry r = new NodeRegistry();
        r.register(null);
        r.register(handler(null));
        r.register(handler(""));
        r.register(handler("   "));
        assertTrue(r.knownTemplateIds().isEmpty());
    }

    @Test
    void getRejectsNullAndBlank() {
        NodeRegistry r = new NodeRegistry();
        r.register(handler("id-1"));
        assertNull(r.get(null));
        assertNull(r.get(""));
        assertNull(r.get("  "));
        assertNull(r.get("unknown"));
    }

    @Test
    void reRegisterReplacesHandlerButKeepsOrderSlot() {
        NodeRegistry r = new NodeRegistry();
        NodeHandler first = handler("a");
        NodeHandler second = handler("a");
        r.register(first);
        r.register(handler("b"));
        r.register(second);
        assertSame(second, r.get("a"));
        assertEquals(List.of("a", "b"), r.knownTemplateIds());
    }

    @Test
    void knownTemplateIdsIsInsertionOrderedCopy() {
        NodeRegistry r = new NodeRegistry();
        r.register(handler("x"));
        r.register(handler("y"));
        List<String> ids = r.knownTemplateIds();
        assertEquals(List.of("x", "y"), ids);
        ids.clear(); // mutating the copy must not affect the registry
        assertEquals(List.of("x", "y"), r.knownTemplateIds());
    }
}
