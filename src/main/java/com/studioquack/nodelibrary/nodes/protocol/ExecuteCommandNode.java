package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Allowlisted argv execution inside the node-library container (Podman sandbox).
 *
 * <p>Default off: {@code enabled} must be {@code true} on the instance. An empty
 * allowlist is a refusal. Arguments are an argv list — never a shell string.
 * The executable is resolved to an absolute path and refused unless its basename
 * is on the allowlist. The working directory is confined to a dedicated workspace.
 * The child environment is cleared except {@code PATH} and a few safe vars.
 * Wall-clock timeout and stdout/stderr caps are constants. A non-zero exit code
 * is a normal result ({@code status=ok}); refusals are {@code INVALID_INPUT};
 * launch failures are {@code REQUEST_FAILED}.
 */
public final class ExecuteCommandNode implements NodeHandler {

    public static final String DEFAULT_ID = "d3e8777b-a46d-4611-b034-94a8c67910e9";

    /** Default command budget when {@code timeout_ms} / {@code timeout_seconds} are blank. */
    static final int DEFAULT_TIMEOUT_MS = 15_000;

    /** Upper bound on the operator-supplied command budget. */
    static final int MAX_TIMEOUT_MS = 60_000;

    /** Per-stream stdout/stderr cap (bytes). Excess is discarded after the cap. */
    static final int MAX_OUTPUT_BYTES = 64 * 1024;

    static final String DEFAULT_WORKSPACE = "/sandbox/exec";

    static final String SAFE_PATH = "/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin";

    private final String templateId;
    private final Path workspace;

    public ExecuteCommandNode() {
        this(DEFAULT_ID);
    }

    public ExecuteCommandNode(String templateId) {
        this(templateId, defaultWorkspace());
    }

    /** Test seam: explicit workspace root. */
    public ExecuteCommandNode(String templateId, Path workspace) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    private static Path defaultWorkspace() {
        String env = System.getenv("SQ_EXEC_WORKSPACE");
        return Path.of(env == null || env.isBlank() ? DEFAULT_WORKSPACE : env.trim());
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        if (request == null) {
            return DataSupport.invalidInput("request body must be a JSON object");
        }
        Map<String, Object> cfg = DataSupport.readConfig(request);
        Map<String, Object> in = DataSupport.overlayConfig(
                DataSupport.readInput(request, helper), cfg, "argv", "stdin", "working_dir");
        String enabled = DataSupport.cfgStr(cfg, "enabled", "false");
        if (!EmulatorUtil.isTruthy(enabled)) {
            return DataSupport.invalidInput(
                    "execute-command is disabled; set configuration enabled=true");
        }
        String op = DataSupport.inStrAny(in, DataSupport.cfgStr(cfg, "operation", "exec"),
                "operation").trim().toLowerCase(Locale.ROOT);
        if (!"exec".equals(op)) {
            return DataSupport.invalidInput("unknown operation: "
                    + (op.isEmpty() ? "<missing>" : op) + ". Accepted: exec");
        }
        Set<String> allowlist = parseAllowlist(cfg.get("allowlist"));
        if (allowlist.isEmpty()) {
            return DataSupport.invalidInput("allowlist is required and must name at least one executable");
        }
        List<String> argv = parseArgv(in.get("argv"));
        if (argv.isEmpty()) {
            return DataSupport.invalidInput("argv is required and must be a non-empty list");
        }
        for (String arg : argv) {
            if (arg.indexOf('\0') >= 0) {
                return DataSupport.invalidInput("argv must not contain NUL");
            }
        }
        Path executable = resolveExecutable(argv.get(0), allowlist);
        Path cwd = resolveWorkingDir(
                DataSupport.inStr(in, "working_dir", DataSupport.cfgStr(cfg, "working_dir", null)));
        String stdin = DataSupport.inStr(in, "stdin", null);
        int timeoutMs = resolveTimeoutMs(cfg);
        List<String> command = new ArrayList<>(argv.size());
        command.add(executable.toString());
        command.addAll(argv.subList(1, argv.size()));
        try {
            return run(command, cwd, stdin, timeoutMs);
        } catch (StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return DataSupport.fail("failed to launch command: " + e.getClass().getSimpleName());
        }
    }

    static Set<String> parseAllowlist(Object raw) {
        Set<String> out = new LinkedHashSet<>();
        Object value = raw;
        if (value instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return out;
            }
            if (t.startsWith("[")) {
                value = DataSupport.parseJson(t);
            } else {
                for (String part : t.split(",")) {
                    String name = basename(part.trim());
                    if (!name.isEmpty()) {
                        out.add(name);
                    }
                }
                return out;
            }
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String name = basename(EmulatorUtil.str(item).trim());
                if (!name.isEmpty()) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    static List<String> parseArgv(Object raw) {
        List<String> out = new ArrayList<>();
        Object value = raw;
        if (value instanceof String s) {
            String t = s.trim();
            if (t.startsWith("[")) {
                try {
                    value = Json.parse(t);
                } catch (Exception ignored) {
                    return out;
                }
            } else if (!t.isEmpty()) {
                return out;
            }
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
        }
        return out;
    }

    /**
     * Resolve {@code token} to an absolute regular file whose basename is on the
     * allowlist. Path-bearing tokens are accepted only when that basename matches.
     */
    static Path resolveExecutable(String token, Set<String> allowlist) {
        if (token == null || token.isBlank()) {
            DataSupport.invalidInput("argv[0] is required");
        }
        if (token.indexOf('\0') >= 0) {
            DataSupport.invalidInput("argv must not contain NUL");
        }
        String base = basename(token);
        if (base.isEmpty() || !allowlist.contains(base)) {
            DataSupport.invalidInput("executable is not on the allowlist: "
                    + (base.isEmpty() ? "<missing>" : base));
        }
        Path raw = Path.of(token);
        if (raw.isAbsolute() || token.indexOf('/') >= 0 || token.indexOf('\\') >= 0) {
            Path abs = raw.toAbsolutePath().normalize();
            String absBase = abs.getFileName() == null ? "" : abs.getFileName().toString();
            if (!allowlist.contains(absBase)) {
                DataSupport.invalidInput("executable is not on the allowlist: " + absBase);
            }
            if (!Files.isRegularFile(abs)) {
                DataSupport.fail("executable not found");
            }
            return abs;
        }
        for (String dir : SAFE_PATH.split(":")) {
            Path cand = Path.of(dir).resolve(base).normalize();
            if (Files.isRegularFile(cand) && Files.isExecutable(cand)) {
                return cand.toAbsolutePath();
            }
        }
        DataSupport.fail("executable not found: " + base);
        return Path.of(token);
    }

    Path resolveWorkingDir(String requested) {
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            DataSupport.fail("workspace is not usable");
        }
        if (requested == null || requested.isBlank() || ".".equals(requested.trim())) {
            return workspace;
        }
        String rel = requested.trim();
        if (rel.indexOf('\0') >= 0) {
            DataSupport.invalidInput("working_dir must not contain NUL");
        }
        Path raw = Path.of(rel);
        Path resolved = raw.isAbsolute()
                ? raw.toAbsolutePath().normalize()
                : workspace.resolve(raw).toAbsolutePath().normalize();
        if (!resolved.startsWith(workspace)) {
            DataSupport.invalidInput("working_dir escapes the workspace");
        }
        if (!Files.isDirectory(resolved)) {
            DataSupport.invalidInput("working_dir is not a directory inside the workspace");
        }
        return resolved;
    }

    static int resolveTimeoutMs(Map<String, Object> cfg) {
        int ms = DataSupport.cfgInt(cfg, "timeout_ms", 0);
        if (ms <= 0) {
            int sec = DataSupport.cfgInt(cfg, "timeout_seconds", 0);
            ms = sec > 0 ? sec * 1000 : DEFAULT_TIMEOUT_MS;
        }
        return Math.min(ms, MAX_TIMEOUT_MS);
    }

    private Map<String, Object> run(List<String> command, Path cwd, String stdin, int timeoutMs)
            throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(false);
        Map<String, String> env = pb.environment();
        env.clear();
        env.put("PATH", SAFE_PATH);
        env.put("HOME", workspace.toString());
        env.put("LANG", "C");
        env.put("LC_ALL", "C");
        env.put("TMPDIR", workspace.toString());
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            return DataSupport.fail("failed to launch command: " + e.getClass().getSimpleName());
        }
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread tOut = Thread.startVirtualThread(() -> copyCapped(proc.getInputStream(), stdout));
        Thread tErr = Thread.startVirtualThread(() -> copyCapped(proc.getErrorStream(), stderr));
        try (OutputStream os = proc.getOutputStream()) {
            if (stdin != null) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // child closed stdin
        }
        boolean finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        boolean timedOut = !finished;
        if (timedOut) {
            proc.destroyForcibly();
            proc.waitFor(2, TimeUnit.SECONDS);
        }
        tOut.join(1_000);
        tErr.join(1_000);
        int exit = proc.isAlive() ? -1 : proc.exitValue();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("exit_code", exit);
        body.put("stdout", stdout.toString(StandardCharsets.UTF_8));
        body.put("stderr", stderr.toString(StandardCharsets.UTF_8));
        body.put("timed_out", timedOut);
        return DataSupport.ports(body);
    }

    static void copyCapped(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[4096];
        try {
            int n;
            while ((n = in.read(buf)) >= 0) {
                int room = MAX_OUTPUT_BYTES - out.size();
                if (room <= 0) {
                    while (in.read(buf) >= 0) {
                        // discard past the cap so the child is not blocked on a full pipe
                    }
                    return;
                }
                out.write(buf, 0, Math.min(n, room));
            }
        } catch (IOException ignored) {
            // process ended
        }
    }

    static String basename(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String s = token.trim();
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        return slash >= 0 ? s.substring(slash + 1) : s;
    }
}
