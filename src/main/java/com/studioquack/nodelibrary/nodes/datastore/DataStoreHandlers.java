package com.studioquack.nodelibrary.nodes.datastore;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeCatalog;
import com.studioquack.nodelibrary.core.NodeRegistry;

/**
 * Registers the generic Redis cache, PGVector and SQL database nodes.
 *
 * <p>Wired from {@code NodeHandlers.registerAll} before OfficialApiHandlers so
 * these custom handlers own their template ids.
 */
public final class DataStoreHandlers {

    private DataStoreHandlers() {
        // registrar — never instantiated
    }

    public static void register(NodeRegistry registry) {
        if (registry == null) {
            return;
        }
        registry.register(new RedisCacheNode(DataSupport.templateId(
                NodeCatalog.envName("redis-cache"), RedisCacheNode.DEFAULT_ID)));
        registry.register(new PgVectorNode(DataSupport.templateId(
                NodeCatalog.envName("pgvector"), PgVectorNode.DEFAULT_ID)));
        for (SqlDatabaseNode.Dialect dialect : SqlDatabaseNode.Dialect.values()) {
            registry.register(new SqlDatabaseNode(dialect, DataSupport.templateId(
                    NodeCatalog.envName(dialect.nodeKey()), dialect.defaultId())));
        }
    }
}
