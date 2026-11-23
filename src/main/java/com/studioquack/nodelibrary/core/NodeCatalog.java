package com.studioquack.nodelibrary.core;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers {@code nodes/<key>/node_definition.json} on the classpath and maps
 * each node's resolved template id.
 *
 * <p>{@code SQ_TEMPLATE_ID_<SCREAMING_KEY>} (folder uppercased, dashes → underscores)
 * wins over the JSON {@code _id}. Some bundled definitions are JS-style (unquoted
 * keys / single quotes); field extraction is tolerant.
 */
public final class NodeCatalog {

    private static final Pattern FIELD = Pattern.compile(
            "[\"']?(_id|name)[\"']?\\s*:\\s*[\"']([^\"']+)[\"']");

    private final Map<String, String> keyToTemplateId = new LinkedHashMap<>();
    private final Map<String, String> templateIdToKey = new LinkedHashMap<>();
    private final Map<String, String> keyToName = new LinkedHashMap<>();

    private NodeCatalog() {
    }

    /** Scan the classpath once. Bad definitions are skipped, never thrown. */
    public static NodeCatalog load() {
        NodeCatalog cat = new NodeCatalog();
        cat.scanCodeSource();
        cat.scanClassLoaderRoots();
        return cat;
    }

    /** Resolved template id for a node folder key, or {@code null}. */
    public String templateId(String nodeKey) {
        return nodeKey == null ? null : keyToTemplateId.get(nodeKey);
    }

    /** Folder key for a template id, or {@code null}. */
    public String nodeKey(String templateId) {
        return templateId == null ? null : templateIdToKey.get(templateId);
    }

    /** Node folder names, sorted. */
    public List<String> nodeKeys() {
        List<String> keys = new ArrayList<>(keyToTemplateId.keySet());
        Collections.sort(keys);
        return keys;
    }

    /** Resolved template ids in sorted-key order. */
    public List<String> templateIds() {
        List<String> ids = new ArrayList<>();
        for (String key : nodeKeys()) {
            String id = keyToTemplateId.get(key);
            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Display name from the definition, or empty. */
    public String name(String nodeKey) {
        String n = keyToName.get(nodeKey);
        return n == null ? "" : n;
    }

    private void scanCodeSource() {
        try {
            CodeSource cs = NodeCatalog.class.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) {
                return;
            }
            Path loc = Path.of(cs.getLocation().toURI());
            if (Files.isRegularFile(loc) && loc.getFileName().toString().endsWith(".jar")) {
                scanJar(loc);
            } else if (Files.isDirectory(loc)) {
                scanDir(loc.resolve("nodes"));
            }
        } catch (Exception ignored) {
            // one unreadable origin must not hide the rest
        }
    }

    private void scanClassLoaderRoots() {
        try {
            ClassLoader cl = NodeCatalog.class.getClassLoader();
            if (cl == null) {
                return;
            }
            Enumeration<URL> roots = cl.getResources("nodes");
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                if (url == null) {
                    continue;
                }
                if ("file".equals(url.getProtocol())) {
                    scanDir(Path.of(url.toURI()));
                } else if ("jar".equals(url.getProtocol())) {
                    scanJarUrl(url);
                }
            }
        } catch (Exception ignored) {
            // classpath listing is best-effort
        }
    }

    private void scanJarUrl(URL url) {
        try {
            String spec = url.toString();
            int bang = spec.indexOf("!/");
            if (bang < 0 || !spec.startsWith("jar:")) {
                return;
            }
            Path jar = Path.of(URI.create(spec.substring("jar:".length(), bang)));
            scanJar(jar);
        } catch (Exception ignored) {
            // skip this jar
        }
    }

    private void scanDir(Path nodesDir) {
        if (nodesDir == null || !Files.isDirectory(nodesDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(nodesDir)) {
            for (Path keyDir : stream) {
                if (!Files.isDirectory(keyDir)) {
                    continue;
                }
                Path def = keyDir.resolve("node_definition.json");
                if (!Files.isRegularFile(def)) {
                    continue;
                }
                try {
                    ingest(keyDir.getFileName().toString(), Files.readString(def, StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                    // skip one bad file
                }
            }
        } catch (Exception ignored) {
            // skip unreadable nodes dir
        }
    }

    private void scanJar(Path jarPath) {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            return;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("nodes/") || !name.endsWith("/node_definition.json")) {
                    continue;
                }
                String rest = name.substring("nodes/".length());
                int slash = rest.indexOf('/');
                if (slash < 0 || rest.indexOf('/', slash + 1) >= 0) {
                    continue;
                }
                String key = rest.substring(0, slash);
                try (InputStream in = jar.getInputStream(entry)) {
                    ingest(key, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                    // skip one bad entry
                }
            }
        } catch (Exception ignored) {
            // skip unreadable jar
        }
    }

    private void ingest(String nodeKey, String text) {
        if (nodeKey == null || nodeKey.isBlank() || text == null) {
            return;
        }
        if (keyToTemplateId.containsKey(nodeKey)) {
            return;
        }
        String jsonId = "";
        String jsonName = "";
        Matcher m = FIELD.matcher(text);
        while (m.find()) {
            String field = m.group(1);
            String value = m.group(2);
            if ("_id".equals(field) && jsonId.isEmpty()) {
                jsonId = value;
            } else if ("name".equals(field) && jsonName.isEmpty()) {
                jsonName = value;
            }
            if (!jsonId.isEmpty() && !jsonName.isEmpty()) {
                break;
            }
        }
        String env = System.getenv(envName(nodeKey));
        String templateId = (env != null && !env.isBlank()) ? env.trim() : jsonId;
        if (templateId == null || templateId.isBlank()) {
            return;
        }
        keyToTemplateId.put(nodeKey, templateId);
        templateIdToKey.put(templateId, nodeKey);
        keyToName.put(nodeKey, jsonName);
    }

    /** {@code node-test-emulator} → {@code SQ_TEMPLATE_ID_NODE_TEST_EMULATOR}. */
    public static String envName(String nodeKey) {
        if (nodeKey == null) {
            return "SQ_TEMPLATE_ID_";
        }
        return "SQ_TEMPLATE_ID_" + nodeKey.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
