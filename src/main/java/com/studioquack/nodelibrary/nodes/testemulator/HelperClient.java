package com.studioquack.nodelibrary.nodes.testemulator;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Helper-channel client over {@code SQ_HELPER_SOCKET} (HTTP via {@code curl --unix-socket}).
 *
 * <p>Bearer comes from the invoke body's {@code helperToken}. {@link #log} is
 * fire-and-forget and never fails a test. {@link #buildRun} is the gated
 * build-mode RPC and requires both the socket and the token.
 *
 * <p>Do not log the token itself.
 */
public final class HelperClient {

    private static final String MISSING_CHANNEL =
            "build-mode requires the daemon helper channel (SQ_HELPER_SOCKET + helperToken)";

    /** Build wall-clock (600s) plus margin, added to the invoke timeout. */
    private static final int BUILD_MARGIN_SECONDS = 660;

    private final String helperToken;
    private final boolean enabled;

    public HelperClient(String helperToken) {
        this.helperToken = helperToken;
        this.enabled = helperToken != null && !helperToken.isBlank();
    }

    /** Best-effort structured log. Never throws. Never logs the bearer. */
    public void log(Map<String, Object> fields) {
        if (!enabled) {
            return;
        }
        post("log", Json.write(fields == null ? Map.of() : fields));
    }

    public void log(String message) {
        log(Map.of("level", "INFO", "message", message == null ? "" : message));
    }

    /**
     * POST {@code /v1/helper/test/build-run} over the helper Unix socket.
     *
     * <p>{@code --max-time} is {@code timeoutSeconds + 660} (build wall clock + margin).
     * Curl {@code --fail} is not used, so 401/403/400/503 JSON bodies are returned for
     * the caller to map. Transport failure (missing socket/token, curl exit, unreadable
     * or non-object body) returns a map containing only {@code {"error": "<message>"}}.
     * Never logs the bearer.
     */
    public Map<String, Object> buildRun(Map<String, Object> body, int timeoutSeconds) {
        if (!enabled) {
            return Map.of("error", MISSING_CHANNEL);
        }
        String socket = System.getenv("SQ_HELPER_SOCKET");
        if (socket == null || socket.isBlank()) {
            return Map.of("error", MISSING_CHANNEL);
        }
        int maxTime = Math.max(0, timeoutSeconds) + BUILD_MARGIN_SECONDS;
        String json = Json.write(body == null ? Map.of() : body);
        Process p = null;
        try {
            // JDK HttpClient cannot speak Unix sockets — same curl transport as log().
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-sS", "--max-time", String.valueOf(maxTime), "--unix-socket", socket,
                    "-H", "Authorization: Bearer " + helperToken,
                    "-H", "Content-Type: application/json",
                    "-d", json,
                    "http://localhost/v1/helper/test/build-run");
            p = pb.start();
            String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            if (code != 0) {
                return errorMap(transportMessage(stderr, code));
            }
            if (stdout.isBlank()) {
                return errorMap("empty response from helper test/build-run");
            }
            try {
                return Json.readObject(stdout);
            } catch (Exception parse) {
                String detail = parse.getMessage();
                return errorMap("invalid JSON from helper test/build-run"
                        + (detail == null || detail.isBlank() ? "" : ": " + detail));
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (p != null) {
                p.destroyForcibly();
            }
            String msg = e.getMessage();
            return errorMap("helper test/build-run unreachable"
                    + (msg == null || msg.isBlank() ? "" : ": " + msg));
        }
    }

    /** Curl stderr only — never the argv (that would leak the bearer). */
    private static String transportMessage(String stderr, int code) {
        String tail = stderr == null ? "" : stderr.trim();
        if (tail.length() > 500) {
            tail = tail.substring(0, 500);
        }
        if (tail.isEmpty()) {
            return "helper test/build-run transport failed (curl exit " + code + ")";
        }
        return "helper test/build-run unreachable: " + tail;
    }

    private Map<String, Object> errorMap(String message) {
        return Map.of("error", redact(message));
    }

    /** Strip the bearer if an exception/stderr ever echoed it. */
    private String redact(String message) {
        if (message == null || message.isBlank()) {
            return "helper test/build-run failed";
        }
        if (helperToken != null && !helperToken.isBlank()) {
            return message.replace(helperToken, "***");
        }
        return message;
    }

    private String post(String endpoint, String body) {
        String socket = System.getenv("SQ_HELPER_SOCKET");
        if (socket == null || socket.isBlank()) {
            return null;
        }
        try {
            // JDK HttpClient cannot speak Unix sockets — shell out to curl.
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-sS", "--max-time", "5", "--unix-socket", socket,
                    "-H", "Authorization: Bearer " + helperToken,
                    "-H", "Content-Type: application/json",
                    "-d", body,
                    "http://localhost/v1/helper/" + endpoint);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            String response = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return response;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }
}
