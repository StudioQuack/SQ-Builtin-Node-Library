package com.studioquack.nodelibrary.nodes;

import com.studioquack.nodelibrary.core.NodeCatalog;
import com.studioquack.nodelibrary.core.NodeRegistry;
import com.studioquack.nodelibrary.core.OfficialApiHandlers;
import com.studioquack.nodelibrary.nodes.ai.AiNodeHandlers;
import com.studioquack.nodelibrary.nodes.codec.CodecHandlers;
import com.studioquack.nodelibrary.nodes.datastore.DataStoreHandlers;
import com.studioquack.nodelibrary.nodes.officialapi.Data2NodeHandlers;
import com.studioquack.nodelibrary.nodes.officialapi.DataNodeHandlers;
import com.studioquack.nodelibrary.nodes.officialapi.SocialNodeHandlers;
import com.studioquack.nodelibrary.nodes.protocol.ProtocolHandlers;
import com.studioquack.nodelibrary.nodes.testemulator.TestEmulatorNode;
import com.studioquack.nodelibrary.nodes.transform.TransformHandlers;
import com.studioquack.nodelibrary.nodes.web.WebNodeHandlers;

/**
 * Wires in-process {@link com.studioquack.nodelibrary.core.NodeHandler}s into the registry.
 *
 * <p>Single wiring site for every tier of the library: the Node Test Emulator plus the
 * AI, web, data, social and protocol tiers. Each tier registrar resolves its templateIds through
 * the catalog/env-override pattern.
 */
public final class NodeHandlers {

    private static final String EMULATOR_DEFAULT_ID = "17dcc655-08e2-4b45-b3a0-08d5aa608aed";

    private NodeHandlers() {
        // bootstrap — never instantiated
    }

    /**
     * Register every available in-process handler.
     *
     * <p>EXTENSION POINT: add the tier's {@code register(registry)} call here when a new
     * tier lands. Unregistered catalog ids still belong to this image and return
     * {@code unknown templateId} from {@code NodeLibraryApp}.
     */
    public static void registerAll(NodeRegistry registry, NodeCatalog catalog) {
        if (registry == null) {
            return;
        }
        String emulatorId = catalog == null ? null : catalog.templateId("node-test-emulator");
        if (emulatorId == null || emulatorId.isBlank()) {
            emulatorId = EMULATOR_DEFAULT_ID;
        }
        registry.register(new TestEmulatorNode(emulatorId));
        AiNodeHandlers.register(registry);
        WebNodeHandlers.register(registry);
        DataNodeHandlers.register(registry);
        Data2NodeHandlers.register(registry);
        SocialNodeHandlers.register(registry);
        CodecHandlers.register(registry, catalog);
        TransformHandlers.register(registry, catalog);
        DataStoreHandlers.register(registry);
        ProtocolHandlers.register(registry, catalog);
        // Spec-driven official-API nodes last — never override a custom handler.
        OfficialApiHandlers.register(registry, catalog);
    }
}
