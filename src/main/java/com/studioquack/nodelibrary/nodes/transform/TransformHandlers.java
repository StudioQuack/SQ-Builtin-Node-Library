package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeCatalog;
import com.studioquack.nodelibrary.core.NodeRegistry;
import com.studioquack.nodelibrary.core.DataSupport;

/**
 * Registers the thirteen in-process data-transform nodes (aggregate, compare-datasets,
 * date-time, edit-fields, json, limit, numbers, remove-duplicates, rename-keys, sort,
 * summarize, text, xml).
 *
 * <p>Template ids prefer {@code SQ_TEMPLATE_ID_<KEY>} over the bundled definition
 * {@code _id}. The {@code catalog} parameter is accepted for registrar symmetry; id
 * resolution here goes through {@link DataSupport#templateId} which already implements
 * the env-over-definition rule.
 */
public final class TransformHandlers {

    private TransformHandlers() {
        // registrar — never instantiated
    }

    /** Wire all transform handlers. Null registry is a no-op. */
    public static void register(NodeRegistry registry, NodeCatalog catalog) {
        if (registry == null) {
            return;
        }
        registry.register(new AggregateNode(
                DataSupport.templateId(NodeCatalog.envName("aggregate"), AggregateNode.DEFAULT_ID)));
        registry.register(new CompareDatasetsNode(
                DataSupport.templateId(NodeCatalog.envName("compare-datasets"), CompareDatasetsNode.DEFAULT_ID)));
        registry.register(new DateTimeNode(
                DataSupport.templateId(NodeCatalog.envName("date-time"), DateTimeNode.DEFAULT_ID)));
        registry.register(new EditFieldsNode(
                DataSupport.templateId(NodeCatalog.envName("edit-fields"), EditFieldsNode.DEFAULT_ID)));
        registry.register(new JsonNode(
                DataSupport.templateId(NodeCatalog.envName("json"), JsonNode.DEFAULT_ID)));
        registry.register(new LimitNode(
                DataSupport.templateId(NodeCatalog.envName("limit"), LimitNode.DEFAULT_ID)));
        registry.register(new NumbersNode(
                DataSupport.templateId(NodeCatalog.envName("numbers"), NumbersNode.DEFAULT_ID)));
        registry.register(new RemoveDuplicatesNode(
                DataSupport.templateId(NodeCatalog.envName("remove-duplicates"), RemoveDuplicatesNode.DEFAULT_ID)));
        registry.register(new RenameKeysNode(
                DataSupport.templateId(NodeCatalog.envName("rename-keys"), RenameKeysNode.DEFAULT_ID)));
        registry.register(new SortNode(
                DataSupport.templateId(NodeCatalog.envName("sort"), SortNode.DEFAULT_ID)));
        registry.register(new SummarizeNode(
                DataSupport.templateId(NodeCatalog.envName("summarize"), SummarizeNode.DEFAULT_ID)));
        registry.register(new TextNode(
                DataSupport.templateId(NodeCatalog.envName("text"), TextNode.DEFAULT_ID)));
        registry.register(new XmlNode(
                DataSupport.templateId(NodeCatalog.envName("xml"), XmlNode.DEFAULT_ID)));
    }
}
