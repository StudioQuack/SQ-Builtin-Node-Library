package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.NodeRegistry;

/**
 * Registers the second data/API slice (OpenAlex, Product Hunt, Semantic Scholar,
 * Google Trends, Google Sheets). Call from the library bootstrap.
 *
 * <p>Do not register GitHub / Dev.to / NewsAPI / Hacker News / arXiv here.
 */
public final class Data2NodeHandlers {

    private Data2NodeHandlers() {
        // registrar — never instantiated
    }

    /**
     * Wire the five data nodes. Null {@code r} is a no-op.
     * Template ids come from {@code SQ_TEMPLATE_ID_*} or each node's definition {@code _id}.
     */
    public static void register(NodeRegistry r) {
        if (r == null) {
            return;
        }
        r.register(new OpenalexNode());
        r.register(new ProducthuntNode());
        r.register(new SemanticScholarNode());
        r.register(new GoogleTrendsNode());
        r.register(new GoogleSheetsNode());
    }
}
