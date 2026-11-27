package com.studioquack.nodelibrary.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import studioquack.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates every {@code nodes/<key>/} package against the library contract:
 * definition shape, UUID ids, port/parameter vocabularies, Containerfile
 * {@code SQ_TEMPLATE_ID_*} sync, api_spec sanity, per-node README, and the
 * no-competitor-mentions legal rule.
 *
 * <p>Runs against the filesystem (surefire working dir = project basedir), so a
 * broken package fails the build instead of shipping in the image.
 */
class NodePackageValidationTest {

    private static final Path NODES = Path.of("nodes");
    private static final Path CONTAINERFILE = Path.of("Containerfile");

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    // Base64 so the vendor names themselves never appear verbatim anywhere in the repository.
    private static final Pattern COMPETITOR = Pattern.compile(
            new String(java.util.Base64.getDecoder().decode(
                    "bjhufHphcGllcnxpbnRlZ3JvbWF0fG1ha2VcLmNvbXxhY3RpdmVwaWVjZXN8cGlwZWRyZWFt"),
                    java.nio.charset.StandardCharsets.UTF_8),
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> PORT_TYPES = Set.of("INPUT", "OUTPUT", "FUNCTION");
    private static final Set<String> PARAM_TYPES = Set.of(
            "string", "password", "number", "select", "boolean", "longtext", "file", "object", "list",
            "webhook_key");
    private static final Set<String> CATEGORIES = Set.of(
            "AI & Machine Learning", "CRM & Marketing", "Communication & Messaging",
            "Data & Analytics", "Databases & Storage", "Developer Tools",
            "E-Commerce & Payments", "Files & Documents", "Finance & Accounting",
            "News & Reference", "Productivity & Collaboration", "Security & Identity",
            "Social Media", "Utilities & Core");
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD",
            "COPY", "MOVE", "MKCOL", "PROPFIND"); // WebDAV (nextcloud/owncloud)
    private static final Set<String> AUTH_TYPES = Set.of(
            "", "none", "bearer", "header", "basic", "query", "aws-sigv4");
    private static final Set<String> OP_KINDS = Set.of("", "http", "ingress");
    private static final Set<String> BODY_FORMATS = Set.of("json", "form", "multipart", "raw");
    private static final Set<String> BASIC_KEY_POSITIONS = Set.of("username", "password");

    static Stream<String> nodeKeys() throws IOException {
        try (Stream<Path> dirs = Files.list(NODES)) {
            return dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    @Test
    void libraryShipsTheFullCatalog() throws IOException {
        assertTrue(nodeKeys().count() >= 500, "expected the full node library under nodes/");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeKeys")
    void definitionMatchesTemplateContract(String key) throws IOException {
        Path defPath = NODES.resolve(key).resolve("node_definition.json");
        assertTrue(Files.isRegularFile(defPath), "node_definition.json missing");
        Map<String, Object> def = Json.asMap(Json.parse(Files.readString(defPath)));

        String id = str(def.get("_id"));
        assertTrue(UUID.matcher(id).matches(), "_id not a UUID: " + id);
        assertFalse(str(def.get("name")).isBlank(), "name blank");
        assertFalse(str(def.get("label")).isBlank(), "label blank");
        assertFalse(str(def.get("description")).isBlank(), "description blank");
        assertTrue(CATEGORIES.contains(str(def.get("category"))),
                "unknown category: " + def.get("category"));
        assertTrue(def.get("tags") instanceof List<?> tags && !tags.isEmpty(), "tags empty");
        assertEquals(Boolean.TRUE, def.get("published"), "published must be true");
        assertEquals("custom-script-executor", def.get("jobType"));
        assertEquals("com.studioquack.flow.entity.document.TemplateDocumentEntity",
                def.get("_class"));

        assertPorts(def.get("ports"));
        assertParams(def.get("configurationParameters"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeKeys")
    void containerfileEnvMatchesDefinitionId(String key) throws IOException {
        Map<String, Object> def = Json.asMap(Json.parse(
                Files.readString(NODES.resolve(key).resolve("node_definition.json"))));
        String envName = "SQ_TEMPLATE_ID_" + key.toUpperCase().replace('-', '_');
        String line = "ENV " + envName + "=" + def.get("_id");
        assertTrue(containerfileText().contains(line),
                "Containerfile missing: " + line);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeKeys")
    void apiSpecIsSaneWhenPresent(String key) throws IOException {
        Path specPath = NODES.resolve(key).resolve("api_spec.json");
        if (!Files.isRegularFile(specPath)) {
            return; // custom Java handlers need no spec
        }
        Map<String, Object> spec = Json.asMap(Json.parse(Files.readString(specPath)));
        String baseUrl = str(spec.get("baseUrl"));
        // absolute URL, or config-templated host (e.g. graphql "{endpoint}", segment "{data_plane_url}/v1")
        assertTrue(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")
                        || baseUrl.matches("\\{[a-zA-Z0-9_]+}.*"),
                "baseUrl not absolute: " + baseUrl);

        Object auth = spec.get("auth");
        if (auth instanceof Map<?, ?> am) {
            String type = str(am.get("type")).toLowerCase();
            assertTrue(AUTH_TYPES.contains(type), "unknown auth.type: " + type);
            if (am.containsKey("basicKeyPosition")) {
                String pos = str(am.get("basicKeyPosition")).toLowerCase();
                assertTrue(BASIC_KEY_POSITIONS.contains(pos),
                        "bad auth.basicKeyPosition: " + am.get("basicKeyPosition"));
            }
            if (am.containsKey("secretKeyConfigKey")) {
                assertTrue(am.get("secretKeyConfigKey") instanceof String,
                        "auth.secretKeyConfigKey must be a string");
            }
        }

        if (spec.containsKey("extraHeaders")) {
            assertStringMap("extraHeaders", spec.get("extraHeaders"));
        }
        if (spec.containsKey("sigv4")) {
            assertSigV4(spec.get("sigv4"));
        }
        if ("aws-sigv4".equals(auth instanceof Map<?, ?> am
                ? str(am.get("type")).toLowerCase() : "")) {
            assertTrue(spec.get("sigv4") instanceof Map<?, ?>,
                    "auth.type aws-sigv4 requires a sigv4 object");
        }

        Object ops = spec.get("operations");
        assertTrue(ops instanceof Map<?, ?> om && !om.isEmpty(), "operations empty");
        for (var e : ((Map<?, ?>) ops).entrySet()) {
            assertTrue(e.getValue() instanceof Map<?, ?>, "op not an object: " + e.getKey());
            Map<?, ?> op = (Map<?, ?>) e.getValue();
            String method = str(op.get("method"));
            if (!method.isBlank()) {
                assertTrue(HTTP_METHODS.contains(method.toUpperCase()),
                        "bad method " + method + " in " + e.getKey());
            }
            String kind = str(op.get("kind")).toLowerCase();
            assertTrue(OP_KINDS.contains(kind), "bad kind " + kind + " in " + e.getKey());
            if (op.containsKey("extraHeaders")) {
                assertStringMap(e.getKey() + ".extraHeaders", op.get("extraHeaders"));
            }
            if (op.containsKey("bodyFormat")) {
                String bf = str(op.get("bodyFormat")).toLowerCase();
                assertTrue(BODY_FORMATS.contains(bf),
                        "bad bodyFormat " + op.get("bodyFormat") + " in " + e.getKey());
                if ("raw".equals(bf)) {
                    assertFalse(str(op.get("bodyParam")).isBlank(),
                            e.getKey() + ": bodyFormat=raw requires bodyParam");
                }
            }
            String contentType = str(op.get("contentType")).toLowerCase();
            if (contentType.startsWith("multipart/form-data")) {
                assertEquals("multipart", str(op.get("bodyFormat")).toLowerCase(),
                        key + "." + e.getKey() + ": multipart contentType requires bodyFormat=multipart");
            }
            Object queryMap = op.get("queryMap");
            if (queryMap instanceof Map<?, ?> qm) {
                List<?> qf = op.get("queryFrom") instanceof List<?> list ? list : List.of();
                Set<String> queryFrom = qf.stream().map(NodePackageValidationTest::str).collect(Collectors.toSet());
                for (Object mapKey : qm.keySet()) {
                    String k = str(mapKey);
                    assertTrue(queryFrom.contains(k),
                            key + "." + e.getKey() + ": queryMap key " + k + " missing from queryFrom");
                }
            }
            Object bodyMap = op.get("bodyMap");
            if (bodyMap instanceof Map<?, ?> bm) {
                List<?> bf = op.get("bodyFrom") instanceof List<?> list ? list : List.of();
                Set<String> bodyFrom = bf.stream().map(NodePackageValidationTest::str).collect(Collectors.toSet());
                for (Object mapKey : bm.keySet()) {
                    String k = str(mapKey);
                    assertTrue(bodyFrom.contains(k),
                            key + "." + e.getKey() + ": bodyMap key " + k + " missing from bodyFrom");
                }
            }
            if (op.containsKey("bodyParam") && !(op.get("bodyParam") instanceof String)) {
                throw new AssertionError(e.getKey() + ".bodyParam must be a string");
            }
            if (op.containsKey("baseUrl")) {
                String opBase = str(op.get("baseUrl"));
                assertTrue(opBase.startsWith("https://") || opBase.startsWith("http://")
                                || opBase.matches("\\{[a-zA-Z0-9_]+}.*"),
                        e.getKey() + " baseUrl not absolute: " + opBase);
            }
        }
        if (spec.containsKey("ingress")) {
            assertIngressSpec(key, spec.get("ingress"), (Map<?, ?>) ops);
        }
    }

    @Test
    void ingressClassificationManifestHasNoCompetitorMentions() throws IOException {
        Path manifest = Path.of("docs", "INGRESS_CLASSIFICATION.md");
        assertTrue(Files.isRegularFile(manifest), "docs/INGRESS_CLASSIFICATION.md missing");
        assertFalse(COMPETITOR.matcher(Files.readString(manifest)).find(),
                "competitor mention in docs/INGRESS_CLASSIFICATION.md");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeKeys")
    void readmeExistsAndIsSubstantial(String key) throws IOException {
        Path readme = NODES.resolve(key).resolve("README.md");
        assertTrue(Files.isRegularFile(readme), "README.md missing");
        assertTrue(Files.size(readme) > 300, "README.md too thin");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeKeys")
    void noCompetitorMentions(String key) throws IOException {
        for (String file : List.of("node_definition.json", "api_spec.json", "README.md")) {
            Path p = NODES.resolve(key).resolve(file);
            if (Files.isRegularFile(p)) {
                assertFalse(COMPETITOR.matcher(Files.readString(p)).find(),
                        "competitor mention in " + p);
            }
        }
    }

    // ---- helpers -----------------------------------------------------------

    private static void assertPorts(Object raw) {
        assertTrue(raw instanceof List<?> ports && !ports.isEmpty(), "ports empty");
        boolean hasInput = false;
        boolean hasOutput = false;
        for (Object item : (List<?>) raw) {
            Map<String, Object> port = Json.asMap(item);
            assertTrue(UUID.matcher(str(port.get("_id"))).matches(),
                    "port _id not a UUID: " + port.get("_id"));
            String type = str(port.get("type"));
            assertTrue(PORT_TYPES.contains(type), "unknown port type: " + type);
            hasInput |= "INPUT".equals(type);
            hasOutput |= "OUTPUT".equals(type);
        }
        assertTrue(hasInput, "no INPUT port");
        assertTrue(hasOutput, "no OUTPUT port");
    }

    private static void assertParams(Object raw) {
        assertTrue(raw instanceof List<?>, "configurationParameters not a list");
        for (Object item : (List<?>) raw) {
            Map<String, Object> param = Json.asMap(item);
            assertTrue(UUID.matcher(str(param.get("_id"))).matches(),
                    "param _id not a UUID: " + param.get("_id"));
            assertFalse(str(param.get("shortCode")).isBlank(), "param shortCode blank");
            assertTrue(PARAM_TYPES.contains(str(param.get("type"))),
                    "unknown param type: " + param.get("type"));
        }
    }

    private static String containerfileText() throws IOException {
        return Files.readString(CONTAINERFILE);
    }

    private static void assertStringMap(String where, Object raw) {
        if (raw == null) {
            return;
        }
        if (!(raw instanceof Map<?, ?> m)) {
            throw new AssertionError(where + " must be an object");
        }
        for (var e : m.entrySet()) {
            if (e.getKey() == null || str(e.getKey()).isBlank()) {
                throw new AssertionError(where + ": blank key");
            }
            if (!(e.getValue() instanceof String) && !(e.getValue() instanceof Number)
                    && !(e.getValue() instanceof Boolean)) {
                throw new AssertionError(where + "." + e.getKey() + " must be a string/number/boolean");
            }
        }
    }

    private static void assertSigV4(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            throw new AssertionError("sigv4 must be an object");
        }
        boolean hasService = !str(m.get("service")).isBlank();
        boolean hasServiceKey = !str(m.get("serviceConfigKey")).isBlank();
        assertTrue(hasService || hasServiceKey,
                "sigv4 requires service or serviceConfigKey");
        for (String k : List.of("service", "serviceConfigKey", "regionConfigKey", "sessionTokenConfigKey")) {
            if (m.containsKey(k) && m.get(k) != null && !(m.get(k) instanceof String)) {
                throw new AssertionError("sigv4." + k + " must be a string");
            }
        }
    }

    private static void assertIngressSpec(String key, Object raw, Map<?, ?> ops) {
        assertTrue(raw instanceof Map<?, ?>, key + ": ingress must be an object");
        Map<?, ?> ing = (Map<?, ?>) raw;
        if (ing.get("handshake") instanceof Map<?, ?> hs) {
            String t = str(hs.get("type")).toLowerCase();
            if (!t.isBlank()) {
                assertTrue(com.studioquack.nodelibrary.core.OfficialApiSpec.IngressSpec.HANDSHAKE_TYPES.contains(t),
                        key + ": unknown ingress.handshake.type: " + t);
            }
        }
        if (ing.get("verify") instanceof Map<?, ?> vs) {
            String t = str(vs.get("type")).toLowerCase();
            if (!t.isBlank()) {
                assertTrue(com.studioquack.nodelibrary.core.OfficialApiSpec.IngressSpec.VERIFY_TYPES.contains(t),
                        key + ": unknown ingress.verify.type: " + t);
            }
        }
        if (ing.get("sendAndWait") instanceof Map<?, ?> sw) {
            String sendOp = str(sw.get("sendOperation"));
            assertFalse(sendOp.isBlank(), key + ": ingress.sendAndWait.sendOperation blank");
            assertTrue(ops.containsKey(sendOp), key + ": sendAndWait.sendOperation not in operations: " + sendOp);
            String fb = str(sw.get("fallbackSendOperation"));
            if (!fb.isBlank()) {
                assertTrue(ops.containsKey(fb), key + ": sendAndWait.fallbackSendOperation not in operations: " + fb);
            }
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
