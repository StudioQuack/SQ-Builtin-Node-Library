package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline contract audit of every {@code nodes/<key>/} package: {@code node_definition.json}
 * ports and configuration against {@code api_spec.json} and the {@link OfficialApiNode}
 * resolution rules.
 *
 * <p>Resolution rule the audit encodes (see {@link OfficialApiNode#handle}): path
 * placeholders, {@code queryFrom}, {@code required}, header placeholders and {@code bodyFrom}
 * read the INPUT port first and fall back to configuration, so an input value overrides config.
 * A field must therefore be declared on the INPUT schema or as a config parameter.
 *
 * <p>Writes {@code target/node-audit/report.md} and {@code report.json}. Always fails on an
 * ERROR in a {@link #VERIFIED} node; {@code -Dnodeaudit.strict=true} fails on any ERROR.
 */
class NodeContractAuditTest {

    static final Set<String> VERIFIED = Set.of(
            "http-request", "web-browser", "wikipedia", "wikidata", "wikidata-sparql", "crossref",
            "datamuse", "duckduckgo", "open-library", "openverse", "openthesaurus", "ip-api",
            "world-bank", "arxiv", "hackernews", "openalex", "devto", "github", "dbpedia", "rss",
            "nasa");

    private static final Path NODES = Path.of("nodes");
    private static final Path MAIN_SRC = Path.of("src", "main", "java");
    private static final Path REPORT_DIR = Path.of("target", "node-audit");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
    private static final Pattern SECRET_NAME = Pattern.compile(
            "(^|_)(api_?key|token|secret|password|passwd|private_key|access_key|client_secret)($|_)",
            Pattern.CASE_INSENSITIVE);
    /** Nodes whose own contract keeps an error value on a port (emulator error port, agent answer). */
    private static final Set<String> ERROR_PORT_NODES = Set.of("node-test-emulator", "ai-agent");
    private static final Set<String> RUNTIME_INPUT_KEYS = Set.of("operation", "action", "stream", "streaming");

    enum Severity { ERROR, WARN }

    record Finding(String node, Severity severity, String rule, String detail) {
    }

    @Test
    void auditAllNodePackages() throws IOException {
        List<Finding> findings = auditAll();
        writeReports(findings);

        List<Finding> verifiedErrors = findings.stream()
                .filter(f -> f.severity == Severity.ERROR && VERIFIED.contains(f.node))
                .toList();
        assertTrue(verifiedErrors.isEmpty(), "verified nodes regressed:\n" + lines(verifiedErrors));

        if (Boolean.getBoolean("nodeaudit.strict")) {
            List<Finding> errors = findings.stream().filter(f -> f.severity == Severity.ERROR).toList();
            assertTrue(errors.isEmpty(), errors.size() + " contract errors, see " + REPORT_DIR.resolve("report.md"));
        }
    }

    static List<Finding> auditAll() throws IOException {
        String mainSources = readMainSources();
        NodeCatalog catalog = NodeCatalog.load();
        NodeRegistry registry = new NodeRegistry();
        com.studioquack.nodelibrary.nodes.NodeHandlers.registerAll(registry, catalog);
        List<Finding> out = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(NODES)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                out.addAll(auditNode(dir, mainSources));
                String key = dir.getFileName().toString();
                Map<String, Object> def = Json.asMap(Json.parse(
                        Files.readString(dir.resolve("node_definition.json"), StandardCharsets.UTF_8)));
                if (!Boolean.FALSE.equals(def.get("published")) && registry.get(catalog.templateId(key)) == null) {
                    out.add(new Finding(key, Severity.ERROR, "DEF_NO_HANDLER",
                            "published, but no handler is registered for its templateId — every run fails"
                                    + " with 'unknown templateId'"));
                }
            }
        }
        return out;
    }

    static List<Finding> auditNode(Path dir, String mainSources) throws IOException {
        String key = dir.getFileName().toString();
        List<Finding> f = new ArrayList<>();
        Path defPath = dir.resolve("node_definition.json");
        if (!Files.exists(defPath)) {
            f.add(new Finding(key, Severity.ERROR, "DEF_MISSING", "node_definition.json missing"));
            return f;
        }
        Map<String, Object> def = Json.asMap(Json.parse(Files.readString(defPath, StandardCharsets.UTF_8)));
        Definition d = Definition.from(def);
        auditDefinition(key, d, f);

        Path specPath = dir.resolve("api_spec.json");
        if (Files.exists(specPath)) {
            Map<String, Object> raw = Json.asMap(Json.parse(Files.readString(specPath, StandardCharsets.UTF_8)));
            auditSpec(key, d, raw, OfficialApiSpec.fromMap(key, raw), f);
        } else {
            for (String code : d.config.keySet()) {
                if (!mainSources.contains("\"" + code + "\"")) {
                    f.add(new Finding(key, Severity.WARN, "CUSTOM_CONFIG_UNREFERENCED",
                            "config '" + code + "' is never referenced as a string literal by any handler"));
                }
            }
        }
        return f;
    }

    // ---------------------------------------------------------------- definition rules

    private static void auditDefinition(String key, Definition d, List<Finding> f) {
        for (String dup : d.duplicates) {
            f.add(new Finding(key, Severity.ERROR, "DEF_DUPLICATE", dup));
        }
        if (d.outputs.isEmpty()) {
            f.add(new Finding(key, Severity.ERROR, "DEF_NO_OUTPUT", "no OUTPUT port"));
        }
        for (Map.Entry<String, Map<String, Object>> e : d.config.entrySet()) {
            Map<String, Object> c = e.getValue();
            String type = str(c.get("type"));
            String value = str(c.get("value"));
            checkDefault(key, "config '" + e.getKey() + "'", type, value, f);
            if ("select".equals(type)) {
                List<String> opts = strings(c.get("options"));
                if (opts.isEmpty()) {
                    f.add(new Finding(key, Severity.WARN, "DEF_SELECT_OPTIONS",
                            "select '" + e.getKey() + "' has no static options (fine only if the designer fills them)"));
                } else if (!value.isEmpty() && !opts.contains(value)) {
                    f.add(new Finding(key, Severity.ERROR, "DEF_SELECT_DEFAULT",
                            "select '" + e.getKey() + "' default '" + value + "' is not one of " + opts));
                }
            }
            if (!"password".equals(type) && SECRET_NAME.matcher(e.getKey()).find()) {
                f.add(new Finding(key, Severity.WARN, "DEF_SECRET_TYPE",
                        "config '" + e.getKey() + "' looks like a secret but type is '" + type + "'"));
            }
        }
        for (Port p : d.ports) {
            for (Map<String, Object> field : p.schema) {
                String code = str(field.get("shortCode"));
                String type = str(field.get("type"));
                checkDefault(key, p.direction + " '" + p.value + "." + code + "'", type, str(field.get("value")), f);
                if ("INPUT".equals(p.direction) && ("password".equals(type) || d.secretConfig().contains(code))) {
                    f.add(new Finding(key, Severity.WARN, "DEF_SECRET_ON_INPUT",
                            "INPUT '" + p.value + "." + code + "' is secret-typed; credentials belong in config"));
                }
            }
            if ("FUNCTION".equals(p.direction) && p.value.isBlank()) {
                f.add(new Finding(key, Severity.ERROR, "DEF_FUNCTION_PORT", "FUNCTION port without value"));
            }
            if ("OUTPUT".equals(p.direction) && !ERROR_PORT_NODES.contains(key)
                    && p.schema.stream().anyMatch(m -> "error".equals(str(m.get("shortCode"))))) {
                f.add(new Finding(key, Severity.ERROR, "DEF_ERROR_ON_PORT",
                        "OUTPUT '" + p.value + "' declares an error field; failures must raise a BPMN error"
                                + " for the error boundary event"));
            }
        }
    }

    private static void checkDefault(String key, String where, String type, String value, List<Finding> f) {
        if (value.isEmpty()) {
            return;
        }
        String problem = switch (type) {
            case "number" -> isNumber(value) ? null : "not a number";
            case "boolean" -> "true".equals(value) || "false".equals(value) ? null : "not true|false";
            case "object" -> parsesAs(value, Map.class) || parsesAs(value, List.class) ? null : "not JSON";
            case "array", "list" -> parsesAs(value, List.class) ? null : "not a JSON array";
            default -> null;
        };
        if (problem != null) {
            f.add(new Finding(key, Severity.ERROR, "DEF_DEFAULT_TYPE",
                    where + " default '" + value + "' is " + problem + " (type " + type + ")"));
        }
    }

    // ---------------------------------------------------------------- spec rules

    private static void auditSpec(String key, Definition d, Map<String, Object> raw,
                                  OfficialApiSpec spec, List<Finding> f) {
        Set<String> input = d.inputFields();
        Set<String> config = d.config.keySet();
        Set<String> usedConfig = new HashSet<>(Set.of("operation", spec.timeoutConfigKey, "stream", "streaming"));
        Set<String> usedInput = new HashSet<>(RUNTIME_INPUT_KEYS);

        if (spec.baseUrl.isBlank() && spec.operations.values().stream().anyMatch(o -> o.baseUrl.isBlank()
                && !"ingress".equals(o.kind))) {
            f.add(new Finding(key, Severity.ERROR, "SPEC_BASEURL", "baseUrl missing"));
        }
        if (spec.operations.isEmpty()) {
            f.add(new Finding(key, Severity.ERROR, "SPEC_NO_OPERATIONS", "api_spec.json has no operations"));
            return;
        }

        Map<String, Object> opSelect = d.config.get("operation");
        if (opSelect != null) {
            Set<String> options = new LinkedHashSet<>(strings(opSelect.get("options")));
            Set<String> ops = spec.operations.keySet();
            Set<String> notInSpec = minus(options, ops);
            Set<String> notInSelect = minus(ops, options);
            if (!notInSpec.isEmpty()) {
                f.add(new Finding(key, Severity.ERROR, "SPEC_OPERATION_UNKNOWN",
                        "config operation options not in api_spec (fail with Unknown operation): " + notInSpec));
            }
            if (!notInSelect.isEmpty()) {
                f.add(new Finding(key, Severity.WARN, "SPEC_OPERATION_UNSELECTABLE",
                        "api_spec operations missing from the config select: " + notInSelect));
            }
        } else if (spec.operations.size() > 1 && !input.contains("operation") && !input.contains("action")) {
            f.add(new Finding(key, Severity.ERROR, "SPEC_OPERATION_UNREACHABLE",
                    "multiple operations but neither config nor INPUT declares 'operation'"));
        }

        auditAuth(key, d, spec, usedConfig, f);
        Set<String> topHeaderVars = placeholders(headerValues(raw.get("extraHeaders")));

        Map<String, Object> rawOps = Json.asMap(raw.get("operations"));
        Set<String> outputPorts = d.outputs.stream().map(p -> p.value).collect(Collectors.toSet());
        for (OfficialApiSpec.Operation op : spec.operations.values()) {
            if ("ingress".equals(op.kind)) {
                continue;
            }
            String where = "op '" + op.name + "'";
            String portKey = OfficialApiNode.outputPortKey(spec, op);
            if (!outputPorts.contains(portKey)) {
                f.add(new Finding(key, Severity.ERROR, "SPEC_OUTPUT_PORT",
                        where + " returns on port '" + portKey + "' but OUTPUT ports are " + outputPorts));
            }

            Map<String, Object> rawOp = Json.asMap(rawOps.get(op.name));
            Map<String, Object> rawStream = Json.asMap(rawOp.get("stream"));
            String base = op.baseUrl.isBlank() ? spec.baseUrl : op.baseUrl;
            String path = op.stream != null && !op.stream.path.isBlank() ? op.stream.path : op.path;
            Set<String> pathVars = placeholders(List.of(base, path));
            Set<String> headerVars = new LinkedHashSet<>(topHeaderVars);
            headerVars.addAll(placeholders(headerValues(rawOp.get("extraHeaders"))));
            headerVars.addAll(placeholders(headerValues(rawStream.get("extraHeaders"))));
            headerVars.removeIf(v -> v.startsWith("$"));

            for (String v : union(pathVars, op.required)) {
                if (pathVars.contains(v) && spec.placeholderDefaults != null
                        && spec.placeholderDefaults.containsKey(v)) {
                    continue;
                }
                if (!input.contains(v) && !config.contains(v)) {
                    f.add(new Finding(key, Severity.ERROR, "SPEC_REQUIRED_UNREACHABLE",
                            where + " needs '" + v + "' but neither INPUT schema nor config declares it"));
                }
            }
            for (String v : op.queryFrom) {
                if (!input.contains(v) && !config.contains(v) && !op.required.contains(v)) {
                    f.add(new Finding(key, Severity.WARN, "SPEC_QUERY_UNMAPPED",
                            where + " query '" + v + "' is not in INPUT schema or config"));
                }
            }
            for (String v : headerVars) {
                if (!input.contains(v) && !config.contains(v)) {
                    f.add(new Finding(key, Severity.WARN, "SPEC_HEADER_UNMAPPED",
                            where + " header placeholder '" + v + "' is not in INPUT schema or config"));
                }
            }
            List<String> body = new ArrayList<>(op.bodyFrom);
            if ("raw".equals(op.bodyFormat) && !op.bodyParam.isBlank()) {
                body.add(op.bodyParam);
            }
            if (op.mime != null) {
                usedInput.addAll(OfficialApiSpec.MimeBlock.FIELDS);
                usedConfig.addAll(OfficialApiSpec.MimeBlock.FIELDS);
            }
            for (String v : body) {
                if (!input.contains(v) && !config.contains(v) && !RUNTIME_INPUT_KEYS.contains(v)) {
                    f.add(new Finding(key, Severity.WARN, "SPEC_BODY_UNMAPPED",
                            where + " body field '" + v + "' is not in the INPUT schema"));
                }
            }

            usedInput.addAll(pathVars);
            usedInput.addAll(op.required);
            usedInput.addAll(op.queryFrom);
            usedInput.addAll(headerVars);
            usedInput.addAll(body);
            usedConfig.addAll(pathVars);
            usedConfig.addAll(op.required);
            usedConfig.addAll(op.queryFrom);
            usedConfig.addAll(headerVars);
            usedConfig.addAll(body);
        }

        if (outputPorts.contains("output")) {
            Set<String> outFields = d.outputs.stream().filter(p -> "output".equals(p.value))
                    .flatMap(p -> p.schema.stream()).map(m -> str(m.get("shortCode")))
                    .collect(Collectors.toSet());
            Set<String> missing = minus(new LinkedHashSet<>(List.of("status", "http_status", "data")), outFields);
            if (!missing.isEmpty()) {
                f.add(new Finding(key, Severity.WARN, "SPEC_OUTPUT_SCHEMA",
                        "OUTPUT 'output' schema lacks fields the engine emits: " + missing));
            }
        }
        for (String c : minus(config, usedConfig)) {
            f.add(new Finding(key, Severity.WARN, "SPEC_CONFIG_UNUSED",
                    "config '" + c + "' is read by no operation (no path/query/header/required/auth use)"));
        }
        for (String i : minus(input, usedInput)) {
            f.add(new Finding(key, Severity.WARN, "SPEC_INPUT_UNUSED",
                    "INPUT field '" + i + "' is read by no operation"));
        }
    }

    private static void auditAuth(String key, Definition d, OfficialApiSpec spec,
                                  Set<String> usedConfig, List<Finding> f) {
        String type = spec.authType == null ? "" : spec.authType;
        if (type.isBlank() || "none".equals(type)) {
            return;
        }
        List<String> keys = new ArrayList<>(List.of(spec.authConfigKey));
        if ("basic".equals(type) && !spec.authUserKey.isBlank()) {
            keys.add(spec.authUserKey);
        }
        if ("aws-sigv4".equals(type)) {
            keys.add(spec.secretKeyConfigKey);
            if (spec.sigv4 != null) {
                keys.add(spec.sigv4.regionConfigKey);
                if (!spec.sigv4.serviceConfigKey.isBlank()) {
                    keys.add(spec.sigv4.serviceConfigKey);
                }
                if (!spec.sigv4.sessionTokenConfigKey.isBlank()) {
                    keys.add(spec.sigv4.sessionTokenConfigKey);
                }
            }
        }
        for (String k : keys) {
            usedConfig.add(k);
            if (!d.config.containsKey(k)) {
                f.add(new Finding(key, Severity.ERROR, "SPEC_AUTH_KEY",
                        "auth " + type + " reads config '" + k + "' which is not declared — every call fails"));
            }
        }
        Map<String, Object> secret = d.config.get(spec.authConfigKey);
        Map<String, Object> user = d.config.get(spec.authUserKey);
        boolean keyIsUsername = "basic".equals(type) && user != null && "password".equals(str(user.get("type")));
        if (secret != null && !keyIsUsername && !"password".equals(str(secret.get("type")))) {
            f.add(new Finding(key, Severity.ERROR, "SPEC_AUTH_NOT_SECRET",
                    "auth config '" + spec.authConfigKey + "' must be type password, is '" + str(secret.get("type")) + "'"));
        }
    }

    // ---------------------------------------------------------------- model

    record Port(String direction, String value, List<Map<String, Object>> schema) {
    }

    static final class Definition {
        final List<Port> ports = new ArrayList<>();
        final List<Port> outputs = new ArrayList<>();
        final Map<String, Map<String, Object>> config = new LinkedHashMap<>();
        final List<String> duplicates = new ArrayList<>();

        static Definition from(Map<String, Object> def) {
            Definition d = new Definition();
            Set<String> portValues = new HashSet<>();
            for (Object o : Json.asList(def.get("ports"))) {
                Map<String, Object> p = Json.asMap(o);
                List<Map<String, Object>> schema = new ArrayList<>();
                Set<String> codes = new HashSet<>();
                for (Object s : Json.asList(p.get("schema"))) {
                    Map<String, Object> field = Json.asMap(s);
                    if (!codes.add(str(field.get("shortCode")))) {
                        d.duplicates.add("port '" + str(p.get("value")) + "' repeats field '" + str(field.get("shortCode")) + "'");
                    }
                    schema.add(field);
                }
                Port port = new Port(str(p.get("type")), str(p.get("value")), schema);
                if (!portValues.add(port.direction + ":" + port.value)) {
                    d.duplicates.add("port value '" + port.value + "' repeated on " + port.direction);
                }
                d.ports.add(port);
                if ("OUTPUT".equals(port.direction)) {
                    d.outputs.add(port);
                }
            }
            for (Object o : Json.asList(def.get("configurationParameters"))) {
                Map<String, Object> c = Json.asMap(o);
                String code = str(c.get("shortCode"));
                if (d.config.put(code, c) != null) {
                    d.duplicates.add("config shortCode '" + code + "' repeated");
                }
            }
            return d;
        }

        Set<String> inputFields() {
            return ports.stream().filter(p -> "INPUT".equals(p.direction))
                    .flatMap(p -> p.schema.stream()).map(m -> str(m.get("shortCode")))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Set<String> secretConfig() {
            return config.entrySet().stream().filter(e -> "password".equals(str(e.getValue().get("type"))))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
        }
    }

    // ---------------------------------------------------------------- report

    private static void writeReports(List<Finding> findings) throws IOException {
        Files.createDirectories(REPORT_DIR);
        Map<String, List<Finding>> byNode = new TreeMap<>();
        Map<String, Integer> byRule = new TreeMap<>();
        for (Finding f : findings) {
            byNode.computeIfAbsent(f.node, k -> new ArrayList<>()).add(f);
            byRule.merge(f.severity + " " + f.rule, 1, Integer::sum);
        }
        List<String> allNodes;
        try (Stream<Path> s = Files.list(NODES)) {
            allNodes = s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        }
        long errorNodes = byNode.values().stream()
                .filter(l -> l.stream().anyMatch(f -> f.severity == Severity.ERROR)).count();

        StringBuilder md = new StringBuilder("# Node contract audit\n\n");
        md.append("Nodes: ").append(allNodes.size()).append(" · with ERROR: ").append(errorNodes)
                .append(" · clean: ").append(allNodes.size() - byNode.size()).append("\n\n");
        md.append("| Rule | Count |\n|---|---|\n");
        byRule.forEach((r, n) -> md.append("| ").append(r).append(" | ").append(n).append(" |\n"));
        md.append("\n");
        for (var e : byNode.entrySet()) {
            md.append("## ").append(e.getKey()).append(VERIFIED.contains(e.getKey()) ? " (verified)" : "").append("\n\n");
            for (Finding f : e.getValue()) {
                md.append("- **").append(f.severity).append("** `").append(f.rule).append("` ").append(f.detail).append("\n");
            }
            md.append("\n");
        }
        Files.writeString(REPORT_DIR.resolve("report.md"), md.toString(), StandardCharsets.UTF_8);

        List<Map<String, Object>> json = new ArrayList<>();
        for (String node : allNodes) {
            List<Finding> l = byNode.getOrDefault(node, List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("node", node);
            row.put("verified", VERIFIED.contains(node));
            row.put("errors", l.stream().filter(f -> f.severity == Severity.ERROR).count());
            row.put("warnings", l.stream().filter(f -> f.severity == Severity.WARN).count());
            row.put("findings", l.stream().map(f -> Map.of(
                    "severity", f.severity.name(), "rule", f.rule, "detail", f.detail)).toList());
            json.add(row);
        }
        Files.writeString(REPORT_DIR.resolve("report.json"), Json.write(json), StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- helpers

    private static String readMainSources() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN_SRC)) {
            for (Path p : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                sb.append(Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    static List<String> headerValues(Object raw) {
        return Json.asMap(raw).values().stream().map(NodeContractAuditTest::str).toList();
    }

    static Set<String> placeholders(Collection<String> texts) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : texts) {
            Matcher m = PLACEHOLDER.matcher(t == null ? "" : t);
            while (m.find()) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    private static Set<String> union(Collection<String> a, Collection<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static Set<String> minus(Collection<String> a, Collection<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    private static boolean isNumber(String v) {
        try {
            Double.parseDouble(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean parsesAs(String v, Class<?> type) {
        try {
            return type.isInstance(Json.parse(v));
        } catch (RuntimeException e) {
            return false;
        }
    }

    static List<String> strings(Object raw) {
        return Json.asList(raw).stream().map(NodeContractAuditTest::str).filter(s -> !s.isEmpty()).toList();
    }

    static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String lines(List<Finding> fs) {
        return fs.stream().map(x -> x.node + " " + x.rule + ": " + x.detail).collect(Collectors.joining("\n"));
    }
}
