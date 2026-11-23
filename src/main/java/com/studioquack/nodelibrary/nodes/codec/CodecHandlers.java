package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeCatalog;
import com.studioquack.nodelibrary.core.NodeRegistry;
import com.studioquack.nodelibrary.core.DataSupport;

/**
 * Registrar for the local codec / security / file tier: Compression, Crypto,
 * Digest, Extract From File, Files, HTML, JWT, Markdown, TOTP.
 *
 * <p>Wired from {@code NodeHandlers.registerAll} by the parent change — this
 * class is the tier's single entry point. Template ids resolve via
 * {@code SQ_TEMPLATE_ID_<KEY>} env override, falling back to the bundled
 * definition {@code _id}. {@code catalog} is accepted for signature parity
 * with the other registrars; id resolution intentionally follows the same
 * env-then-definition pattern everywhere.
 */
public final class CodecHandlers {

    private CodecHandlers() {
        // registrar — never instantiated
    }

    public static void register(NodeRegistry registry, NodeCatalog catalog) {
        if (registry == null) {
            return;
        }
        registry.register(new CompressionNode(DataSupport.templateId(
                NodeCatalog.envName("compression"), CompressionNode.DEFAULT_ID)));
        registry.register(new CryptoNode(DataSupport.templateId(
                NodeCatalog.envName("crypto"), CryptoNode.DEFAULT_ID)));
        registry.register(new DigestNode(DataSupport.templateId(
                NodeCatalog.envName("digest"), DigestNode.DEFAULT_ID)));
        registry.register(new ExtractFromFileNode(DataSupport.templateId(
                NodeCatalog.envName("extract-from-file"), ExtractFromFileNode.DEFAULT_ID)));
        registry.register(new FilesNode(DataSupport.templateId(
                NodeCatalog.envName("files"), FilesNode.DEFAULT_ID)));
        registry.register(new HtmlNode(DataSupport.templateId(
                NodeCatalog.envName("html"), HtmlNode.DEFAULT_ID)));
        registry.register(new JwtNode(DataSupport.templateId(
                NodeCatalog.envName("jwt"), JwtNode.DEFAULT_ID)));
        registry.register(new MarkdownNode(DataSupport.templateId(
                NodeCatalog.envName("markdown"), MarkdownNode.DEFAULT_ID)));
        registry.register(new TotpNode(DataSupport.templateId(
                NodeCatalog.envName("totp"), TotpNode.DEFAULT_ID)));
    }
}
