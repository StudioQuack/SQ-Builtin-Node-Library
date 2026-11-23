package com.studioquack.nodelibrary.nodes.testemulator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Timed subprocess capture (stdout + stderr + exit code).
 *
 * <p>Used by script-mode (python3 / node / tsx). The timeout is a hard kill:
 * destroyForcibly after {@code timeoutSeconds}.
 */
public final class ProcessRunner {

    private ProcessRunner() {
        // Utility — never instantiated.
    }

    /**
     * Run {@code argv} in {@code cwd} with optional extra env.
     *
     * @param timeoutSeconds wall-clock budget; {@code <= 0} treated as 60
     */
    public static ProcessResult run(List<String> argv, Path cwd, Map<String, String> extraEnv,
                                    int timeoutSeconds) {
        int timeout = timeoutSeconds <= 0 ? 60 : timeoutSeconds;
        ProcessBuilder pb = new ProcessBuilder(argv);
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        pb.redirectErrorStream(false);
        if (extraEnv != null && !extraEnv.isEmpty()) {
            pb.environment().putAll(extraEnv);
        }
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            return ProcessResult.failed("failed to start " + argv.get(0) + ": " + e.getMessage());
        }
        try {
            boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
                String stdout = readQuiet(p.getInputStream());
                String stderr = readQuiet(p.getErrorStream());
                return new ProcessResult(false, -1, stdout, stderr,
                        "timed out after " + timeout + "s: " + String.join(" ", argv));
            }
            String stdout = readQuiet(p.getInputStream());
            String stderr = readQuiet(p.getErrorStream());
            int code = p.exitValue();
            return new ProcessResult(code == 0, code, stdout, stderr, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            return ProcessResult.failed("interrupted while running " + argv.get(0));
        }
    }

    private static String readQuiet(java.io.InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Outcome of one subprocess. {@code error} is set on timeout / spawn failure;
     * a non-zero exit is {@code success=false} with {@code error == null} (caller
     * decides how to phrase it).
     */
    public record ProcessResult(
            boolean success,
            int exitCode,
            String stdout,
            String stderr,
            String error
    ) {
        static ProcessResult failed(String message) {
            return new ProcessResult(false, -1, "", "", message);
        }
    }
}
