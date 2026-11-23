package com.studioquack.nodelibrary.core;

/**
 * Registers an {@link OfficialApiNode} for every catalog key that has
 * {@code nodes/<key>/api_spec.json} and is not already claimed by a custom handler.
 *
 * <p>Call last from {@code NodeHandlers.registerAll} so shipped Java nodes win.
 */
public final class OfficialApiHandlers {

    private OfficialApiHandlers() {
        // bootstrap — never instantiated
    }

    public static void register(NodeRegistry registry, NodeCatalog catalog) {
        if (registry == null || catalog == null) {
            return;
        }
        for (String key : catalog.nodeKeys()) {
            OfficialApiSpec spec = OfficialApiSpec.load(key);
            if (spec == null) {
                continue;
            }
            String tid = catalog.templateId(key);
            if (tid == null || tid.isBlank()) {
                continue;
            }
            if (registry.get(tid) != null) {
                continue; // custom handler already owns this id
            }
            registry.register(new OfficialApiNode(tid, spec));
        }
    }
}
