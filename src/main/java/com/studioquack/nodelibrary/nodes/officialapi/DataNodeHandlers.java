package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.NodeRegistry;

/**
 * Registers the five data/API search nodes (GitHub, DEV.to, NewsAPI, Hacker News, arXiv).
 *
 * <p>Call from the master {@code NodeHandlers.registerAll} once that tier is wired.
 * Does not register OpenAlex / Product Hunt / Semantic Scholar / Google Trends /
 * Google Sheets — those belong to a separate registrar.
 */
public final class DataNodeHandlers {

    private DataNodeHandlers() {
        // registrar — never instantiated
    }

    /**
     * Wire the five data-node handlers. Null registry is a no-op.
     * Template ids prefer {@code SQ_TEMPLATE_ID_<KEY>} over the bundled definition {@code _id}.
     */
    public static void register(NodeRegistry r) {
        if (r == null) {
            return;
        }
        r.register(new GithubNode(DataNodeSupport.templateId("github", GithubNode.DEFAULT_ID)));
        r.register(new DevtoNode(DataNodeSupport.templateId("devto", DevtoNode.DEFAULT_ID)));
        r.register(new NewsapiNode(DataNodeSupport.templateId("newsapi", NewsapiNode.DEFAULT_ID)));
        r.register(new HackernewsNode(DataNodeSupport.templateId("hackernews", HackernewsNode.DEFAULT_ID)));
        r.register(new ArxivNode(DataNodeSupport.templateId("arxiv", ArxivNode.DEFAULT_ID)));
    }
}
