package com.studioquack.nodelibrary.core;

import com.studioquack.nodelibrary.core.NodeContractAuditTest.Definition;
import com.studioquack.nodelibrary.core.NodeContractAuditTest.Finding;
import com.studioquack.nodelibrary.core.NodeContractAuditTest.Severity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studioquack.Json;
import studioquack.StudioQuackError;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.studioquack.nodelibrary.core.NodeContractAuditTest.headerValues;
import static com.studioquack.nodelibrary.core.NodeContractAuditTest.placeholders;
import static com.studioquack.nodelibrary.core.NodeContractAuditTest.str;
import static com.studioquack.nodelibrary.core.NodeContractAuditTest.strings;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline behaviour audit: every non-ingress operation of every {@code api_spec.json} node
 * runs through {@link OfficialApiNode} against a loopback server (spec {@code baseUrl}s are
 * rewritten; host placeholders stay in the URL so they are still exercised).
 *
 * <p>Per operation: input overrides config, config fills in when input is absent, body
 * fields reach the wire, auth is applied, required fields are enforced before any request,
 * a 2xx lands on the declared output port, and a provider error is surfaced without echoing
 * the secret back.
 *
 * <p>Writes {@code target/node-audit/behaviour.md} and {@code behaviour.json}. Fails on an
 * ERROR in a {@link NodeContractAuditTest#VERIFIED} node; {@code -Dnodeaudit.strict=true}
 * fails on any ERROR.
 */
class NodeBehaviourAuditTest {

    private static final Path NODES = Path.of("nodes");
    private static final Path REPORT_DIR = Path.of("target", "node-audit");

    private static HttpServer server;
    private static String local;
    private static final AtomicReference<Captured> last = new AtomicReference<>();
    private static volatile int respondStatus = 200;
    private static volatile String respondBody = "{\"ok\":true}";

    record Captured(String method, String uri, Map<String, String> headers, String body) {
        String all() {
            return method + " " + uri + "\n" + headers + "\n" + body;
        }
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            last.set(new Captured(exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery(),
                    headers, body));
            byte[] bytes = respondBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(respondStatus, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        local = "http://127.0.0.1:" + server.getAddress().getPort() + "/__base";
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void exerciseEverySpecOperation() throws IOException {
        List<Finding> findings = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(NODES)) {
            for (Path dir : dirs.filter(d -> Files.exists(d.resolve("api_spec.json"))).sorted().toList()) {
                findings.addAll(auditNode(dir));
            }
        }
        writeReports(findings);

        List<Finding> verified = findings.stream()
                .filter(f -> f.severity() == Severity.ERROR && NodeContractAuditTest.VERIFIED.contains(f.node()))
                .toList();
        assertTrue(verified.isEmpty(), "verified nodes regressed:\n" + lines(verified));
        if (Boolean.getBoolean("nodeaudit.strict")) {
            long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
            assertTrue(errors == 0, errors + " behaviour errors, see " + REPORT_DIR.resolve("behaviour.md"));
        }
    }

    // ---------------------------------------------------------------- per node / op

    static List<Finding> auditNode(Path dir) throws IOException {
        String key = dir.getFileName().toString();
        Definition d = Definition.from(Json.asMap(Json.parse(
                Files.readString(dir.resolve("node_definition.json"), StandardCharsets.UTF_8))));
        Map<String, Object> raw = Json.asMap(Json.parse(
                Files.readString(dir.resolve("api_spec.json"), StandardCharsets.UTF_8)));
        OfficialApiSpec original = OfficialApiSpec.fromMap(key, raw);
        OfficialApiSpec spec = OfficialApiSpec.fromMap(key, localized(raw));
        OfficialApiNode node = new OfficialApiNode("audit-" + key, spec);
        List<Finding> out = new ArrayList<>();
        Map<String, Object> rawOps = Json.asMap(raw.get("operations"));
        for (OfficialApiSpec.Operation op : spec.operations.values()) {
            if ("ingress".equals(op.kind)) {
                continue;
            }
            try {
                new OpAudit(key, d, spec, original, op, Json.asMap(rawOps.get(op.name)), node, raw, out).run();
            } catch (RuntimeException e) {
                out.add(new Finding(key, Severity.ERROR, "L2_EXCEPTION",
                        "op '" + op.name + "' threw " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return collapse(out, "L2_SECRET_IN_ERROR");
    }

    /** One finding per node for a rule that fails on every operation the same way. */
    private static List<Finding> collapse(List<Finding> findings, String rule) {
        List<String> ops = findings.stream().filter(f -> rule.equals(f.rule()))
                .map(f -> f.detail().replaceFirst("^op '([^']*)'.*", "$1")).toList();
        if (ops.size() < 2) {
            return findings;
        }
        List<Finding> out = new ArrayList<>(findings.stream().filter(f -> !rule.equals(f.rule())).toList());
        Finding first = findings.stream().filter(f -> rule.equals(f.rule())).findFirst().orElseThrow();
        out.add(new Finding(first.node(), first.severity(), rule,
                ops.size() + " ops echo a provider error containing the credential into the node error: " + ops));
        return out;
    }

    /** Same spec with every baseUrl pointed at the loopback server; host placeholders kept as path segments. */
    private static Map<String, Object> localized(Map<String, Object> raw) {
        Map<String, Object> root = new LinkedHashMap<>(raw);
        root.put("baseUrl", localBase(str(raw.get("baseUrl"))));
        Map<String, Object> ops = new LinkedHashMap<>();
        for (var e : Json.asMap(raw.get("operations")).entrySet()) {
            Map<String, Object> op = new LinkedHashMap<>(Json.asMap(e.getValue()));
            if (!str(op.get("baseUrl")).isBlank()) {
                op.put("baseUrl", localBase(str(op.get("baseUrl"))));
            }
            ops.put(e.getKey(), op);
        }
        root.put("operations", ops);
        return root;
    }

    private static String localBase(String base) {
        StringBuilder sb = new StringBuilder(local);
        for (String v : placeholders(List.of(base))) {
            sb.append("/{").append(v).append('}');
        }
        return sb.toString();
    }

    private static final class OpAudit {
        final String key;
        final Definition d;
        final OfficialApiSpec spec;
        final OfficialApiSpec.Operation op;
        final OfficialApiNode node;
        final List<Finding> out;
        final String where;
        final String portKey;
        final Set<String> urlFields = new LinkedHashSet<>();
        final Set<String> bodyFields = new LinkedHashSet<>();
        /** Body fields other inputs are nested into (or the {@code $} root); sent as JSON objects. */
        final Set<String> envelopes = new LinkedHashSet<>();
        final Set<String> fileFields = new LinkedHashSet<>();
        final Set<String> credentialKeys = new LinkedHashSet<>();
        final String secret;
        final Map<String, String> cfgTypes = new LinkedHashMap<>();

        OpAudit(String key, Definition d, OfficialApiSpec spec, OfficialApiSpec original,
                OfficialApiSpec.Operation op, Map<String, Object> rawOp, OfficialApiNode node,
                Map<String, Object> rawSpec, List<Finding> out) {
            this.key = key;
            this.d = d;
            this.spec = spec;
            this.op = op;
            this.node = node;
            this.out = out;
            this.where = "op '" + op.name + "'";
            this.portKey = OfficialApiNode.outputPortKey(spec, op);
            this.secret = "SECRET__" + spec.authConfigKey + "__";

            String base = op.baseUrl.isBlank() ? original.baseUrl : original.operation(op.name).baseUrl;
            String path = op.stream != null && !op.stream.path.isBlank() ? op.stream.path : op.path;
            urlFields.addAll(placeholders(List.of(base == null ? "" : base, path)));
            urlFields.addAll(op.queryFrom);
            op.required.stream().filter(r -> !op.bodyFrom.contains(r)).forEach(urlFields::add);
            urlFields.addAll(placeholders(headerValues(rawSpec.get("extraHeaders"))));
            urlFields.addAll(placeholders(headerValues(rawOp.get("extraHeaders"))));
            urlFields.addAll(placeholders(headerValues(Json.asMap(rawOp.get("stream")).get("extraHeaders"))));
            urlFields.removeIf(v -> v.startsWith("$"));

            String authType = spec.authType == null ? "" : spec.authType;
            if (!authType.isBlank() && !"none".equals(authType)) {
                credentialKeys.add(spec.authConfigKey);
                credentialKeys.add(spec.authUserKey);
                credentialKeys.add(spec.secretKeyConfigKey);
                if (spec.sigv4 != null) {
                    credentialKeys.add(spec.sigv4.sessionTokenConfigKey);
                }
            }
            credentialKeys.removeIf(String::isBlank);

            for (var e : d.config.entrySet()) {
                cfgTypes.put(e.getKey(), str(e.getValue().get("type")));
                if ("file".equals(str(e.getValue().get("type")))) {
                    fileFields.add(e.getKey());
                }
            }
            d.ports.stream().filter(p -> "INPUT".equals(p.direction())).flatMap(p -> p.schema().stream())
                    .filter(m -> "file".equals(str(m.get("type"))))
                    .forEach(m -> fileFields.add(str(m.get("shortCode"))));
            bodyFields.addAll(op.bodyFrom);
            if ("raw".equals(op.bodyFormat) && !op.bodyParam.isBlank()) {
                bodyFields.add(op.bodyParam);
            }
            urlFields.removeAll(credentialKeys);
            bodyFields.removeAll(credentialKeys);
            for (var e : op.bodyMap.entrySet()) {
                String target = e.getValue();
                if ("$".equals(target)) {
                    envelopes.add(e.getKey());
                }
                for (String f : bodyFields) {
                    if (!f.equals(e.getKey()) && (target.startsWith(f + ".") || target.startsWith(f + "["))) {
                        envelopes.add(f);
                    }
                }
            }
            urlFields.removeAll(SELECTORS);
            bodyFields.removeAll(SELECTORS);
            authHeaderOverridden = Stream.of(rawSpec.get("extraHeaders"), rawOp.get("extraHeaders"))
                    .flatMap(h -> Json.asMap(h).keySet().stream())
                    .anyMatch(h -> h.equalsIgnoreCase(spec.authHeader));
        }

        private static final Set<String> SELECTORS = Set.of("operation", "stream", "streaming");
        boolean authHeaderOverridden;

        void run() {
            Set<String> neededFiles = new LinkedHashSet<>(op.required);
            neededFiles.retainAll(fileFields);
            if (!neededFiles.isEmpty()) {
                out.add(new Finding(key, Severity.WARN, "L2_SKIPPED_FILE",
                        where + " requires platform file(s) " + neededFiles + "; not exercised offline"));
                return;
            }
            overrideAndBody();
            configFallback();
            requiredEnforced();
            providerError();
            actionNotShadowingOperation();
        }

        /**
         * The engine reads the operation name from INPUT {@code operation}, then {@code action},
         * then config. An op whose own parameter is {@code action} must still run when the
         * operation is chosen in config only.
         */
        private void actionNotShadowingOperation() {
            if (!urlFields.contains("action") && !bodyFields.contains("action")) {
                return;
            }
            Map<String, Object> cfg = baseConfig();
            Map<String, Object> in = baseInput();
            in.remove("operation");
            urlFields.forEach(f -> in.put(f, inArg(f)));
            bodyFields.stream().filter(f -> !fileFields.contains(f)).forEach(f -> in.put(f, inArg(f)));
            last.set(null);
            String error;
            try {
                error = errorOf(node.handle(request(cfg, in, cfgTypes), null));
            } catch (StudioQuackError e) {
                error = e.getMessage();
            }
            if (last.get() == null) {
                out.add(new Finding(key, Severity.ERROR, "L2_ACTION_SHADOWS_OPERATION",
                        where + " input 'action' is taken as the operation name when operation is set in config: "
                                + error));
            }
        }

        /** Input and config both set: the input value must be the one on the wire. */
        private void overrideAndBody() {
            Map<String, Object> cfg = baseConfig();
            Map<String, Object> in = baseInput();
            for (String f : urlFields) {
                if (d.config.containsKey(f)) {
                    cfg.put(f, cfgVal(f));
                }
                in.put(f, inArg(f));
            }
            for (String f : bodyFields) {
                if (!fileFields.contains(f)) {
                    in.put(f, inArg(f));
                }
            }
            Captured c = call(cfg, in, "override");
            if (c == null) {
                return;
            }
            String wire = decode(c.all());
            for (String f : urlFields) {
                if (!wire.contains(inVal(f))) {
                    out.add(new Finding(key, Severity.ERROR, "L2_INPUT_NOT_SENT",
                            where + " input '" + f + "' did not reach the request"));
                }
                if (d.config.containsKey(f) && wire.contains(cfgVal(f))) {
                    out.add(new Finding(key, Severity.ERROR, "L2_CONFIG_WINS",
                            where + " config '" + f + "' was sent although input supplied it"));
                }
            }
            if (op.stream == null) {
                for (String f : bodyFields) {
                    if (!fileFields.contains(f) && !wire.contains(inVal(f))) {
                        out.add(new Finding(key, Severity.ERROR, "L2_BODY_FIELD_NOT_SENT",
                                where + " body field '" + f + "' from input did not reach the request body"));
                    }
                }
            }
            checkAuth(c);
        }

        /**
         * Only config set for config-declared fields: the config value must be used. Covers
         * URL fields and every config-declared body field.
         */
        private void configFallback() {
            Set<String> cfgFields = urlFields.stream().filter(d.config::containsKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (op.stream == null) {
                bodyFields.stream().filter(d.config::containsKey)
                        .filter(f -> !fileFields.contains(f) && !"raw".equals(f)).forEach(cfgFields::add);
            }
            if (cfgFields.isEmpty()) {
                return;
            }
            Map<String, Object> cfg = baseConfig();
            Map<String, Object> in = baseInput();
            for (String f : union(urlFields, bodyFields)) {
                if (cfgFields.contains(f)) {
                    cfg.put(f, cfgVal(f));
                } else if (!fileFields.contains(f)) {
                    in.put(f, inArg(f));
                }
            }
            Captured c = call(cfg, in, "config fallback");
            if (c == null) {
                return;
            }
            String wire = decode(c.all());
            for (String f : cfgFields) {
                if (!wire.contains(cfgVal(f))) {
                    out.add(new Finding(key, Severity.ERROR, "L2_CONFIG_NOT_USED",
                            where + " config '" + f + "' was not used when input omitted it"));
                }
            }
        }

        /** Each required field missing from both sides must fail before any request. */
        private void requiredEnforced() {
            for (String f : op.required) {
                if (credentialKeys.contains(f)) {
                    continue;
                }
                Map<String, Object> cfg = baseConfig();
                Map<String, Object> in = baseInput();
                for (String g : urlFields) {
                    if (!g.equals(f)) {
                        in.put(g, inArg(g));
                    }
                }
                for (String g : bodyFields) {
                    if (!g.equals(f) && !fileFields.contains(g)) {
                        in.put(g, inArg(g));
                    }
                }
                cfg.remove(f);
                in.remove(f);
                last.set(null);
                String error;
                try {
                    error = errorOf(node.handle(request(cfg, in, cfgTypes), null));
                    if (error != null) {
                        out.add(new Finding(key, Severity.ERROR, "L2_ERROR_ON_PORT",
                                where + " missing '" + f + "' returned {error} on the port instead of raising"
                                        + " a BPMN error"));
                    }
                } catch (StudioQuackError e) {
                    error = e.getMessage();
                    if (!"INVALID_INPUT".equals(e.errorCode())) {
                        out.add(new Finding(key, Severity.ERROR, "L2_ERROR_CODE",
                                where + " missing '" + f + "' raised " + e.errorCode() + ", expected INVALID_INPUT"));
                    }
                }
                if (last.get() != null) {
                    out.add(new Finding(key, Severity.ERROR, "L2_REQUIRED_NOT_ENFORCED",
                            where + " sent a request with required '" + f + "' missing"));
                } else if (error == null || !error.contains(f)) {
                    out.add(new Finding(key, Severity.WARN, "L2_REQUIRED_MESSAGE",
                            where + " missing '" + f + "' failed without naming it: " + error));
                }
            }
        }

        /** Provider 4xx/5xx echoing the credential: error surfaces, secret does not. */
        private void providerError() {
            Map<String, Object> cfg = baseConfig();
            Map<String, Object> in = baseInput();
            urlFields.forEach(f -> in.put(f, inArg(f)));
            bodyFields.stream().filter(f -> !fileFields.contains(f)).forEach(f -> in.put(f, inArg(f)));
            respondStatus = 401;
            respondBody = "{\"error\":\"invalid key " + secret + "\"}";
            String error = null;
            try {
                Map<String, Object> result = node.handle(request(cfg, in, cfgTypes), null);
                error = errorOf(result);
                out.add(new Finding(key, Severity.ERROR, error == null ? "L2_HTTP_ERROR_SWALLOWED" : "L2_ERROR_ON_PORT",
                        where + " HTTP 401 " + (error == null ? "produced no error"
                                : "was returned as {error} on port '" + portKey + "'")
                                + " instead of raising a BPMN error"));
            } catch (StudioQuackError e) {
                error = e.getMessage();
                if (last.get() != null) {
                    if (!"AUTH_FAILED".equals(e.errorCode())) {
                        out.add(new Finding(key, Severity.ERROR, "L2_ERROR_CODE",
                                where + " HTTP 401 raised " + e.errorCode() + ", expected AUTH_FAILED"));
                    }
                    if (e.data() == null || !Integer.valueOf(401).equals(e.data().get("http_status"))) {
                        out.add(new Finding(key, Severity.WARN, "L2_HTTP_STATUS_MISSING",
                                where + " HTTP 401 error carries no http_status"));
                    }
                }
            } finally {
                respondStatus = 200;
                respondBody = "{\"ok\":true}";
            }
            if (error != null && !credentialKeys.isEmpty() && error.contains(secret)) {
                out.add(new Finding(key, Severity.ERROR, "L2_SECRET_IN_ERROR",
                        where + " provider error text echoed the credential into the node error"));
            }
        }

        /** Input value for {@code f}: an object carrying the marker for envelope fields. */
        private String inArg(String f) {
            return envelopes.contains(f) ? "{\"audit\":\"" + inVal(f) + "\"}" : inVal(f);
        }

        private void checkAuth(Captured c) {
            String type = spec.authType == null ? "" : spec.authType;
            if (type.isBlank() || "none".equals(type) || authHeaderOverridden) {
                return;
            }
            boolean ok = switch (type) {
                case "basic" -> c.headers().getOrDefault("authorization", "").startsWith("Basic ");
                case "aws-sigv4" -> c.headers().getOrDefault("authorization", "").startsWith("AWS4-HMAC-SHA256");
                case "query" -> c.uri().contains(secret);
                default -> c.headers().getOrDefault(spec.authHeader.toLowerCase(), "").contains(secret);
            };
            if (!ok) {
                out.add(new Finding(key, Severity.ERROR, "L2_AUTH_NOT_APPLIED",
                        where + " auth " + type + " credential was not on the request"));
            }
        }

        /** Runs one call that should reach the provider; records why when it does not. */
        private Captured call(Map<String, Object> cfg, Map<String, Object> in, String caseName) {
            last.set(null);
            Map<String, Object> result;
            try {
                result = node.handle(request(cfg, in, cfgTypes), null);
            } catch (StudioQuackError e) {
                out.add(new Finding(key, Severity.ERROR, "L2_CALL_FAILED",
                        where + " [" + caseName + "] failed before/at the request: " + e.getMessage()));
                return null;
            }
            Captured c = last.get();
            if (c == null) {
                out.add(new Finding(key, Severity.ERROR, "L2_NO_REQUEST",
                        where + " [" + caseName + "] made no request: " + errorOf(result)));
                return null;
            }
            Map<String, Object> p = port(result);
            if (p == null) {
                out.add(new Finding(key, Severity.ERROR, "L2_OUTPUT_PORT",
                        where + " 2xx result not on port '" + portKey + "', got " + result.keySet()));
            } else if ("output".equals(portKey)
                    ? !"ok".equals(p.get("status")) || !Integer.valueOf(200).equals(p.get("http_status"))
                    : !p.containsKey("result")) {
                out.add(new Finding(key, Severity.ERROR, "L2_OUTPUT_SHAPE",
                        where + " 2xx result lacks status=ok/http_status=200: " + p.keySet()));
            } else if (!c.method().equals(op.method)) {
                out.add(new Finding(key, Severity.ERROR, "L2_METHOD",
                        where + " sent " + c.method() + ", spec says " + op.method));
            }
            return c;
        }

        private Map<String, Object> baseConfig() {
            Map<String, Object> cfg = new LinkedHashMap<>();
            for (var e : d.config.entrySet()) {
                String code = e.getKey();
                Map<String, Object> c = e.getValue();
                String value = str(c.get("value"));
                if (value.isEmpty() && "select".equals(str(c.get("type")))) {
                    List<String> opts = strings(c.get("options"));
                    value = opts.isEmpty() ? "" : opts.get(0);
                }
                if (!value.isEmpty()) {
                    cfg.put(code, value);
                }
            }
            for (String k : credentialKeys) {
                cfg.put(k, "SECRET__" + k + "__");
            }
            if (spec.sigv4 != null) {
                cfg.putIfAbsent(spec.sigv4.regionConfigKey, "us-east-1");
                if (!spec.sigv4.serviceConfigKey.isBlank()) {
                    cfg.putIfAbsent(spec.sigv4.serviceConfigKey, "execute-api");
                }
            }
            cfg.put("operation", op.name);
            cfg.put(spec.timeoutConfigKey, "5");
            return cfg;
        }

        private Map<String, Object> baseInput() {
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("operation", op.name);
            if (op.stream != null) {
                in.put("stream", false);
            }
            return in;
        }

        private Map<String, Object> port(Map<String, Object> result) {
            return result != null && result.get(portKey) instanceof Map<?, ?> m ? Json.asMap(m) : null;
        }

        private String errorOf(Map<String, Object> result) {
            Map<String, Object> p = port(result);
            return p == null || p.get("error") == null ? null : str(p.get("error"));
        }
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static String inVal(String f) {
        return "IN__" + f + "__";
    }

    private static String cfgVal(String f) {
        return "CFG__" + f + "__";
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    private static Map<String, Object> request(Map<String, Object> cfg, Map<String, Object> input,
                                               Map<String, String> types) {
        List<Map<String, Object>> params = new ArrayList<>();
        cfg.forEach((k, v) -> params.add(Map.of("shortCode", k, "value", v, "type", types.getOrDefault(k, "string"))));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("executionContext", Map.of("currentNode", Map.of("data", Map.of("configurationParameters", params))));
        req.put("inputVariables", Map.of("input", input));
        return req;
    }

    // ---------------------------------------------------------------- report

    private static void writeReports(List<Finding> findings) throws IOException {
        Files.createDirectories(REPORT_DIR);
        Map<String, List<Finding>> byNode = new TreeMap<>();
        Map<String, Integer> byRule = new TreeMap<>();
        for (Finding f : findings) {
            byNode.computeIfAbsent(f.node(), k -> new ArrayList<>()).add(f);
            byRule.merge(f.severity() + " " + f.rule(), 1, Integer::sum);
        }
        List<String> specNodes;
        try (Stream<Path> s = Files.list(NODES)) {
            specNodes = s.filter(p -> Files.exists(p.resolve("api_spec.json")))
                    .map(p -> p.getFileName().toString()).sorted().toList();
        }
        long errorNodes = byNode.values().stream()
                .filter(l -> l.stream().anyMatch(f -> f.severity() == Severity.ERROR)).count();
        StringBuilder md = new StringBuilder("# Node behaviour audit (offline, loopback)\n\n");
        md.append("Spec nodes: ").append(specNodes.size()).append(" · with ERROR: ").append(errorNodes)
                .append(" · clean: ").append(specNodes.size() - byNode.size()).append("\n\n");
        md.append("| Rule | Count |\n|---|---|\n");
        byRule.forEach((r, n) -> md.append("| ").append(r).append(" | ").append(n).append(" |\n"));
        md.append("\n");
        for (var e : byNode.entrySet()) {
            md.append("## ").append(e.getKey())
                    .append(NodeContractAuditTest.VERIFIED.contains(e.getKey()) ? " (verified)" : "").append("\n\n");
            for (Finding f : e.getValue()) {
                md.append("- **").append(f.severity()).append("** `").append(f.rule()).append("` ")
                        .append(f.detail()).append("\n");
            }
            md.append("\n");
        }
        Files.writeString(REPORT_DIR.resolve("behaviour.md"), md.toString(), StandardCharsets.UTF_8);

        List<Map<String, Object>> json = new ArrayList<>();
        for (String node : specNodes) {
            List<Finding> l = byNode.getOrDefault(node, List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("node", node);
            row.put("errors", l.stream().filter(f -> f.severity() == Severity.ERROR).count());
            row.put("warnings", l.stream().filter(f -> f.severity() == Severity.WARN).count());
            row.put("findings", l.stream().map(f -> Map.of(
                    "severity", f.severity().name(), "rule", f.rule(), "detail", f.detail())).toList());
            json.add(row);
        }
        Files.writeString(REPORT_DIR.resolve("behaviour.json"), Json.write(json), StandardCharsets.UTF_8);
    }

    private static String lines(List<Finding> fs) {
        return fs.stream().map(x -> x.node() + " " + x.rule() + ": " + x.detail()).collect(Collectors.joining("\n"));
    }
}
