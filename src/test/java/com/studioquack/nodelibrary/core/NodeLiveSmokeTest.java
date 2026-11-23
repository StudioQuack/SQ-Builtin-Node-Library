package com.studioquack.nodelibrary.core;

import com.studioquack.nodelibrary.core.NodeContractAuditTest.Definition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import studioquack.Json;
import studioquack.StudioQuackError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.studioquack.nodelibrary.core.NodeContractAuditTest.str;
import static com.studioquack.nodelibrary.core.NodeContractAuditTest.strings;

/**
 * Opt-in live smoke test ({@code -Dnodeaudit.live=true}): calls the REAL provider for every
 * read-only ({@code GET}) operation of every {@code api_spec.json} node whose credentials are
 * available. Nodes without auth run as-is; authenticated nodes run only when
 * {@code ~/.studioquack/node-live.json} (or {@code -Dnodeaudit.live.file=…}) supplies them:
 *
 * <pre>{"stripe": {"config": {"secret_key": "sk_test_…"}, "input": {"customer_get": {"id": "cus_…"}}}}</pre>
 *
 * <p>Inputs come from that file, then the committed {@code src/test/resources/node-live-samples.json},
 * then config defaults, then (required fields only) the INPUT schema default or an
 * {@code Example:} value in the field description. Operations whose required fields stay blank
 * are skipped, and so are local-only providers (Ollama, LM Studio, …) when nothing listens.
 * Never sends POST/PUT/PATCH/DELETE. Network results never fail the build; see
 * {@code target/node-audit/live.md}.
 */
@EnabledIfSystemProperty(named = "nodeaudit.live", matches = "true")
class NodeLiveSmokeTest {

    private static final Path NODES = Path.of("nodes");
    private static final Path REPORT_DIR = Path.of("target", "node-audit");
    private static final Path SAMPLES = Path.of("src", "test", "resources", "node-live-samples.json");
    private static final Pattern EXAMPLE = Pattern.compile(
            "(?:Example|e\\.g\\.)[:,]?\\s*(?:'([^']+)'|\"([^\"]+)\"|`([^`]+)`|([^\\s,;)]+))");

    record Result(String node, String op, String outcome, String detail) {
    }

    @Test
    void callEveryReadOnlyOperation() throws IOException {
        Map<String, Object> samples = read(SAMPLES);
        Map<String, Object> creds = read(Path.of(System.getProperty("nodeaudit.live.file",
                Path.of(System.getProperty("user.home"), ".studioquack", "node-live.json").toString())));
        List<Result> results = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(NODES)) {
            for (Path dir : dirs.filter(d -> Files.exists(d.resolve("api_spec.json"))).sorted().toList()) {
                String key = dir.getFileName().toString();
                results.addAll(runNode(dir, merge(Json.asMap(samples.get(key)), Json.asMap(creds.get(key)))));
            }
        }
        writeReport(results);
    }

    private static List<Result> runNode(Path dir, Map<String, Object> cred) throws IOException {
        String key = dir.getFileName().toString();
        Definition d = Definition.from(Json.asMap(Json.parse(
                Files.readString(dir.resolve("node_definition.json"), StandardCharsets.UTF_8))));
        OfficialApiSpec spec = OfficialApiSpec.fromMap(key, Json.asMap(Json.parse(
                Files.readString(dir.resolve("api_spec.json"), StandardCharsets.UTF_8))));
        String auth = spec.authType == null ? "" : spec.authType;
        boolean needsAuth = !auth.isBlank() && !"none".equals(auth);
        Map<String, Object> credCfg = Json.asMap(cred.get("config"));
        List<Result> out = new ArrayList<>();
        if (needsAuth && credCfg.isEmpty()) {
            return out;
        }
        OfficialApiNode node = new OfficialApiNode("live-" + key, spec);
        for (OfficialApiSpec.Operation op : spec.operations.values()) {
            if ("ingress".equals(op.kind) || !"GET".equals(op.method)) {
                continue;
            }
            Map<String, Object> cfg = defaults(d);
            cfg.putAll(credCfg);
            cfg.put("operation", op.name);
            cfg.putIfAbsent(spec.timeoutConfigKey, "20");
            Map<String, Object> in = sampleInput(d, op);
            in.putAll(Json.asMap(Json.asMap(cred.get("input")).get(op.name)));
            in.put("operation", op.name);
            if (op.stream != null) {
                in.put("stream", false);
            }
            List<String> missing = new ArrayList<>();
            for (String r : op.required) {
                if (str(in.get(r)).isBlank() && str(cfg.get(r)).isBlank()) {
                    missing.add(r);
                }
            }
            if (!missing.isEmpty()) {
                out.add(new Result(key, op.name, "SKIP", "no sample value for " + missing));
                continue;
            }
            Result r = call(key, op, node, cfg, in);
            if ("FAIL".equals(r.outcome()) && r.detail().contains("HTTP transport failed")
                    && isLocal(spec.baseUrl + " " + cfg.values())) {
                r = new Result(key, op.name, "SKIP", "local server not running: " + r.detail());
            }
            out.add(r);
        }
        return out;
    }

    private static boolean isLocal(String text) {
        return text.contains("localhost") || text.contains("127.0.0.1");
    }

    /** Credentials file entries win over the committed samples; per-op input maps merge. */
    private static Map<String, Object> merge(Map<String, Object> sample, Map<String, Object> cred) {
        Map<String, Object> out = new LinkedHashMap<>(sample);
        Map<String, Object> cfg = new LinkedHashMap<>(Json.asMap(sample.get("config")));
        cfg.putAll(Json.asMap(cred.get("config")));
        out.put("config", cfg);
        Map<String, Object> input = new LinkedHashMap<>(Json.asMap(sample.get("input")));
        Json.asMap(cred.get("input")).forEach((op, v) -> {
            Map<String, Object> m = new LinkedHashMap<>(Json.asMap(input.get(op)));
            m.putAll(Json.asMap(v));
            input.put(op, m);
        });
        out.put("input", input);
        return out;
    }

    private static Result call(String key, OfficialApiSpec.Operation op, OfficialApiNode node,
                               Map<String, Object> cfg, Map<String, Object> in) {
        List<Map<String, Object>> params = new ArrayList<>();
        cfg.forEach((k, v) -> params.add(Map.of("shortCode", k, "value", v)));
        Map<String, Object> req = Map.of(
                "executionContext", Map.of("currentNode", Map.of("data", Map.of("configurationParameters", params))),
                "inputVariables", Map.of("input", in));
        try {
            Map<String, Object> result = node.handle(req, null);
            Map<String, Object> port = result.values().stream().filter(v -> v instanceof Map<?, ?>)
                    .map(Json::asMap).findFirst().orElse(Map.of());
            if ("ok".equals(port.get("status")) || port.containsKey("result") && port.get("error") == null) {
                return new Result(key, op.name, "PASS", "HTTP " + port.getOrDefault("http_status", 200));
            }
            return new Result(key, op.name, "FAIL", oneLine(str(port.get("error"))));
        } catch (StudioQuackError e) {
            return new Result(key, op.name, "FAIL", e.errorCode() + ": " + oneLine(e.getMessage()));
        } catch (RuntimeException e) {
            return new Result(key, op.name, "FAIL", e.getClass().getSimpleName() + ": " + oneLine(e.getMessage()));
        }
    }

    private static Map<String, Object> defaults(Definition d) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        d.config.forEach((code, c) -> {
            String v = str(c.get("value"));
            if (v.isEmpty() && "select".equals(str(c.get("type")))) {
                List<String> opts = strings(c.get("options"));
                v = opts.isEmpty() ? "" : opts.get(0);
            }
            if (!v.isEmpty() && !"password".equals(str(c.get("type")))) {
                cfg.put(code, v);
            }
        });
        return cfg;
    }

    private static Map<String, Object> sampleInput(Definition d, OfficialApiSpec.Operation op) {
        Map<String, Object> in = new LinkedHashMap<>();
        d.ports.stream().filter(p -> "INPUT".equals(p.direction())).flatMap(p -> p.schema().stream()).forEach(f -> {
            String code = str(f.get("shortCode"));
            if (!op.required.contains(code)) {
                return;
            }
            String v = str(f.get("value"));
            if (v.isEmpty()) {
                Matcher m = EXAMPLE.matcher(str(f.get("description")));
                if (m.find()) {
                    for (int g = 1; g <= 4; g++) {
                        if (m.group(g) != null) {
                            v = m.group(g);
                            break;
                        }
                    }
                    if (m.group(4) != null && v.endsWith(".")) {
                        v = v.substring(0, v.length() - 1);
                    }
                }
            }
            if (!v.isEmpty() && !v.startsWith("(") && !"{}".equals(v) && !"[]".equals(v)) {
                in.put(code, v);
            }
        });
        return in;
    }

    private static Map<String, Object> read(Path file) throws IOException {
        return Files.exists(file) ? Json.asMap(Json.parse(Files.readString(file, StandardCharsets.UTF_8))) : Map.of();
    }

    private static String oneLine(String s) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }

    private static void writeReport(List<Result> results) throws IOException {
        Files.createDirectories(REPORT_DIR);
        Map<String, Integer> counts = new TreeMap<>();
        results.forEach(r -> counts.merge(r.outcome(), 1, Integer::sum));
        StringBuilder md = new StringBuilder("# Node live smoke (read-only GET operations)\n\n");
        md.append(counts).append("\n\n| Node | Operation | Outcome | Detail |\n|---|---|---|---|\n");
        for (Result r : results) {
            md.append("| ").append(r.node()).append(" | ").append(r.op()).append(" | ").append(r.outcome())
                    .append(" | ").append(r.detail().replace("|", "\\|")).append(" |\n");
        }
        Files.writeString(REPORT_DIR.resolve("live.md"), md.toString(), StandardCharsets.UTF_8);
        List<Map<String, Object>> json = results.stream().map(r -> Map.<String, Object>of(
                "node", r.node(), "operation", r.op(), "outcome", r.outcome(), "detail", r.detail())).toList();
        Files.writeString(REPORT_DIR.resolve("live.json"), Json.write(json), StandardCharsets.UTF_8);
    }
}
