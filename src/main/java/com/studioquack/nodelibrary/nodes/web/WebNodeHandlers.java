package com.studioquack.nodelibrary.nodes.web;

import com.studioquack.nodelibrary.core.NodeCatalog;
import com.studioquack.nodelibrary.core.NodeRegistry;

/**
 * Registers the three web-tier in-process handlers (web-browser, web-search, http-request).
 *
 * <p>Master wiring ({@code NodeHandlers.registerAll}) calls {@link #register}. This class
 * does not edit the master registrar.
 */
public final class WebNodeHandlers {

    /** Fallback when the catalog JSON is missing; must match {@code nodes/web-browser/_id}. */
    static final String WEB_BROWSER_DEFAULT_ID = "cc63350b-4d0d-4328-87c0-624f6018d0e1";
    /** Fallback when the catalog JSON is missing; must match {@code nodes/web-search/_id}. */
    static final String WEB_SEARCH_DEFAULT_ID = "e154fd82-f17e-4a8e-a457-7334cee69d24";
    /** Fallback when the catalog JSON is missing; must match {@code nodes/http-request/_id}. */
    static final String HTTP_REQUEST_DEFAULT_ID = "30f59c3b-afa1-463a-9a60-9cda6ccae790";

    private WebNodeHandlers() {
        // registrar — never instantiated
    }

    /**
     * Wire the three web-tier {@link com.studioquack.nodelibrary.core.NodeHandler}s.
     * Template ids come from {@link NodeCatalog} ({@code SQ_TEMPLATE_ID_* } wins over JSON {@code _id}).
     */
    public static void register(NodeRegistry r) {
        if (r == null) {
            return;
        }
        NodeCatalog catalog = NodeCatalog.load();
        r.register(new WebBrowserNode(resolve(catalog, "web-browser", WEB_BROWSER_DEFAULT_ID)));
        r.register(new WebSearchNode(resolve(catalog, "web-search", WEB_SEARCH_DEFAULT_ID)));
        r.register(new HttpRequestNode(resolve(catalog, "http-request", HTTP_REQUEST_DEFAULT_ID)));
    }

    private static String resolve(NodeCatalog catalog, String nodeKey, String fallback) {
        String id = catalog == null ? null : catalog.templateId(nodeKey);
        return (id == null || id.isBlank()) ? fallback : id;
    }
}
