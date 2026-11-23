package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.core.NodeCatalog;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.NodeRegistry;

import java.util.function.Function;

/**
 * Registers the protocol tier: mail, queues, file conversion and the other socket / host
 * protocols that have no HTTP {@code api_spec.json}.
 */
public final class ProtocolHandlers {

    private ProtocolHandlers() {
        // registrar — never instantiated
    }

    /** Register every protocol handler whose templateId the catalog knows. */
    public static void register(NodeRegistry registry, NodeCatalog catalog) {
        if (registry == null || catalog == null) {
            return;
        }
        register(registry, catalog, "email", EmailNode::new);
        register(registry, catalog, "queue", QueueNode::new);
        register(registry, catalog, "convert-file", ConvertFileNode::new);
        register(registry, catalog, "ldap", LdapNode::new);
        register(registry, catalog, "execute-command", ExecuteCommandNode::new);
        register(registry, catalog, "ssh", SshNode::new);
        register(registry, catalog, "ftp", FtpNode::new);
        register(registry, catalog, "mcp-server", McpServerNode::new);
    }

    private static void register(NodeRegistry registry, NodeCatalog catalog, String key,
                                 Function<String, NodeHandler> factory) {
        String id = catalog.templateId(key);
        if (id != null && !id.isBlank()) {
            registry.register(factory.apply(id));
        }
    }
}
