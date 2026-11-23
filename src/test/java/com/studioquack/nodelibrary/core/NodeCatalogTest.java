package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Catalog discovery against the packaged {@code /nodes} resources. */
class NodeCatalogTest {

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Test
    void envNameMapsFolderKeyToScreamingSnake() {
        assertEquals("SQ_TEMPLATE_ID_NODE_TEST_EMULATOR", NodeCatalog.envName("node-test-emulator"));
        assertEquals("SQ_TEMPLATE_ID_GOOGLE_BOOKS", NodeCatalog.envName("google-books"));
        assertEquals("SQ_TEMPLATE_ID_", NodeCatalog.envName(null));
    }

    @Test
    void loadDiscoversAllPackagedNodes() {
        NodeCatalog cat = NodeCatalog.load();
        List<String> keys = cat.nodeKeys();
        assertTrue(keys.size() >= 500, "expected the full library, got " + keys.size());
        assertTrue(keys.contains("node-test-emulator"));
        assertTrue(keys.contains("google-books"));
        // sorted contract
        List<String> sorted = keys.stream().sorted().toList();
        assertEquals(sorted, keys);
    }

    @Test
    void templateIdsAreUuidsAndRoundTrip() {
        NodeCatalog cat = NodeCatalog.load();
        List<String> ids = cat.templateIds();
        assertEquals(cat.nodeKeys().size(), ids.size(), "every key must resolve a templateId");
        assertEquals(ids.size(), new HashSet<>(ids).size(), "templateIds must be unique");
        for (String id : ids) {
            assertTrue(UUID.matcher(id).matches(), "not a UUID: " + id);
            String key = cat.nodeKey(id);
            assertNotNull(key, "no key for id " + id);
            assertEquals(id, cat.templateId(key));
        }
    }

    @Test
    void unknownKeysAndIdsReturnNull() {
        NodeCatalog cat = NodeCatalog.load();
        assertNull(cat.templateId("no-such-key-zzz"));
        assertNull(cat.nodeKey("00000000-0000-0000-0000-000000000000"));
        assertNull(cat.templateId(null));
        assertNull(cat.nodeKey(null));
    }

    @Test
    void namesAreLoadedFromDefinitions() {
        NodeCatalog cat = NodeCatalog.load();
        assertTrue(!cat.name("node-test-emulator").isBlank());
        assertEquals("", cat.name("no-such-key-zzz"));
    }
}
