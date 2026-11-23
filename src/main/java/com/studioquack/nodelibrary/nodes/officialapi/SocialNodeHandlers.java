package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.NodeRegistry;

/**
 * Wires the five social-tier {@link com.studioquack.nodelibrary.core.NodeHandler}s.
 *
 * <p>Kept out of {@code nodes/NodeHandlers.java} so that registrar can be composed
 * from {@link com.studioquack.nodelibrary.NodeLibraryApp} (or a later bootstrap)
 * without editing the emulator-only registrar.
 *
 * <p>Template ids: {@code SQ_TEMPLATE_ID_<KEY>} wins over the bundled definition
 * {@code _id} (same rule as {@link com.studioquack.nodelibrary.core.NodeCatalog}).
 */
public final class SocialNodeHandlers {

    private SocialNodeHandlers() {
        // bootstrap — never instantiated
    }

    /**
     * Register YouTube, Instagram, Reddit, Bluesky, and Twitter handlers.
     * Null registry is ignored.
     */
    public static void register(NodeRegistry r) {
        if (r == null) {
            return;
        }
        r.register(new YoutubeNode(SocialSupport.templateId("youtube", "5fa127c0-dceb-4397-8432-2cfe24beeb19")));
        r.register(new InstagramNode(SocialSupport.templateId("instagram",
                "a733c45d-a5b1-42e6-ac5c-2dc7a7770b65")));
        r.register(new RedditNode(SocialSupport.templateId("reddit", "309005be-14d0-4b62-8b0c-ac65d43df7d0")));
        r.register(new BlueskyNode(SocialSupport.templateId("bluesky", "24a7c5c6-9070-4d0b-91c4-2e386f1c8dee")));
        r.register(new TwitterNode(SocialSupport.templateId("twitter",
                "f534a86d-ebd3-475b-a7e9-0d8b650751b1")));
    }
}
