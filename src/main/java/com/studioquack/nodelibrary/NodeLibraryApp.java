package com.studioquack.nodelibrary;

import com.studioquack.nodelibrary.core.NodeCatalog;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.NodeRegistry;
import com.studioquack.nodelibrary.nodes.NodeHandlers;
import studioquack.StudioQuackDispatch;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.util.Map;

/**
 * Entrypoint of the SQ Builtin Node Library image.
 *
 * <p>Uses {@link StudioQuackDispatch} to bind {@code 0.0.0.0:$SQ_APP_PORT$SQ_APP_PATH},
 * treat {@code SQ_SELF_TEMPLATE_ID} (default Node Test Emulator id) as the master
 * self-route, and keep compile-on-miss / client relay available for non-catalog ids.
 *
 * <p>{@code localHandler} routes by request {@code templateId} through {@link NodeRegistry},
 * and every catalog id is registered via {@code localTemplateIds} so all built-in nodes are
 * served in-process without opening per-node loopback sockets.
 *
 * <p>Never log {@code helperToken}. Responses are always HTTP 200 JSON.
 */
public final class NodeLibraryApp {

    static final String DEFAULT_SELF_TEMPLATE_ID = "17dcc655-08e2-4b45-b3a0-08d5aa608aed";

    public static void main(String[] args) throws Exception {
        String selfTemplateId = env("SQ_SELF_TEMPLATE_ID", DEFAULT_SELF_TEMPLATE_ID);
        NodeCatalog catalog = NodeCatalog.load();
        NodeRegistry registry = new NodeRegistry();
        NodeHandlers.registerAll(registry, catalog);

        StudioQuackDispatch.InvokeHandler router = (req, helper) -> route(req, helper, registry);

        StudioQuackDispatch.dispatcher(selfTemplateId)
                .localHandler(router)
                .localTemplateIds(catalog.templateIds())
                .streamLookup(tid -> {
                    NodeHandler h = registry.get(tid);
                    if (h == null) {
                        return null;
                    }
                    return h::handleStream;
                })
                .serve();
    }

    /**
     * Registry lookup. Unknown templateId stays HTTP 200 with known ids listed.
     * Never logs the request (it may contain helperToken).
     */
    static Map<String, Object> route(Map<String, Object> req, StudioQuackHelper helper,
                                     NodeRegistry registry) {
        String tid = "";
        if (req != null && req.get("templateId") != null) {
            tid = String.valueOf(req.get("templateId"));
        }
        NodeHandler handler = registry == null ? null : registry.get(tid);
        if (handler == null) {
            throw new StudioQuackError("NODE_ERROR", "unknown templateId: " + tid);
        }
        return handler.handle(req, helper);
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
