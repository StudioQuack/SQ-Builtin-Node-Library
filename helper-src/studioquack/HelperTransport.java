package studioquack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper-channel transport: HTTP over the Unix socket in {@code SQ_HELPER_SOCKET}, via
 * {@code curl --unix-socket} (JDK {@link java.net.http.HttpClient} cannot speak UDS).
 *
 * <p>Bearer is taken from, in order: the per-request override ({@code helperToken} on a
 * stateful POST), {@code SQ_HELPER_BEARER}, {@code SQVM_HELPER_TOKEN}. When the token or
 * the socket is blank this class is a silent no-op — it never throws. That matches
 * {@code HelperClient} in the stateful-redis-java example and lets a local dry-run still
 * print stdout markers.
 *
 * <p>There is no network fallback. Do not log the bearer.
 */
public final class HelperTransport {

    /** Recv poll used by {@link ConnectTunnel} (Python helper uses 2000 ms). */
    static final int CONNECT_RECV_TIMEOUT_MS = 2000;

    /**
     * {@code timeoutSec} value for "no time limit": LLM generations and sub-process calls wait for
     * as long as they take (the daemon ends them when the run is cancelled).
     */
    public static final int NO_TIME_LIMIT = 0;

    /** {@code curl -sS [--max-time N] args…}; no {@code --max-time} when {@code timeoutSec <= 0}. */
    private static java.util.List<String> curlArgs(int timeoutSec, String... args) {
        java.util.List<String> argv = new java.util.ArrayList<>(java.util.List.of("curl", "-sS"));
        if (timeoutSec > 0) {
            argv.add("--max-time");
            argv.add(String.valueOf(timeoutSec));
        }
        argv.addAll(java.util.Arrays.asList(args));
        return argv;
    }

    /** Container-log rendering of a call's time budget. */
    private static String budget(int timeoutSec) {
        return timeoutSec > 0 ? timeoutSec + "s" : "none";
    }

    /** Reuse one local listener per {@code container:port} like the Python helper. */
    private static final ConcurrentHashMap<String, ConnectTunnel> TUNNELS = new ConcurrentHashMap<>();

    /** Per-request override from the stateful body field {@code helperToken}. */
    private String helperTokenOverride;

    /**
     * Bind the bearer for this request. Call from {@code setRequestContext}.
     *
     * @param helperToken stateful {@code helperToken}, or {@code null} to fall back to env
     */
    public void setHelperToken(String helperToken) {
        this.helperTokenOverride = helperToken;
    }

    /**
     * POST JSON to {@code /v1/helper/<endpoint>} (pass a path starting with {@code /}).
     * Never throws. Returns parsed JSON or {@code null} when the channel is down.
     *
     * @param path        e.g. {@code /v1/helper/log}
     * @param body        JSON-shaped map/list (serialised with {@link Json#write})
     * @param timeoutSec  curl {@code --max-time}; {@link #NO_TIME_LIMIT} for none
     * @return parsed response, empty map on blank 2xx body, or {@code null}
     */
    public Object post(String path, Object body, int timeoutSec) {
        String token = resolveToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        String socket = System.getenv("SQ_HELPER_SOCKET");
        if (socket == null || socket.isBlank()) {
            return null;
        }
        String json = body instanceof String s ? s : Json.write(body);
        // Container-log trace: a node parked on the helper channel (broker accepted the UDS
        // connection but frames never reached the daemon — 2026-08-28 ai-agent 300s
        // SANDBOX_DEADLINE) is otherwise invisible: podman logs showed the invoke arrive and
        // nothing after. One line per call brackets exactly where the node stopped. The
        // connect-tunnel recv/send poll loop is exempt (it would emit a line per chunk);
        // its failures still trace. Never log the bearer or the body.
        boolean quiet = path != null && path.startsWith("/v1/helper/connect/");
        long t0 = System.nanoTime();
        if (!quiet) {
            System.out.println("sq-helper call start path=" + path + " budget=" + budget(timeoutSec));
        }
        try {
            // Host in the URL is ignored when --unix-socket is set; pass the body on stdin
            // (--data-binary @-) to avoid Linux argv size limits on large prompts.
            ProcessBuilder pb = new ProcessBuilder(curlArgs(timeoutSec,
                    "--unix-socket", socket,
                    "-H", "Authorization: Bearer " + token,
                    "-H", "Content-Type: application/json",
                    "--data-binary", "@-",
                    "http://localhost" + path));
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            // Write the full body and close stdin before reading the response so curl can upload cleanly.
            try (java.io.OutputStream curlIn = p.getOutputStream()) {
                curlIn.write((json == null ? "" : json).getBytes(StandardCharsets.UTF_8));
            }
            String raw = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (p.exitValue() != 0) {
                // Non-zero curl exit (e.g. 28 timeout, 7 connect refused): return null so callers
                // distinguish a channel failure from an empty 2xx response.
                System.out.println("sq-helper call fail path=" + path + " ms=" + ms
                        + " curlExit=" + p.exitValue());
                return null;
            }
            if (!quiet) {
                System.out.println("sq-helper call done path=" + path + " ms=" + ms);
            }
            if (raw == null || raw.isBlank()) {
                return new LinkedHashMap<>();
            }
            return Json.parse(raw);
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("sq-helper call error path=" + path + " ms=" + ms
                    + " err=" + e.getClass().getSimpleName());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    /**
     * GET {@code /v1/helper/<endpoint>} (pass a path starting with {@code /}).
     * Never throws. Same token/socket guards as {@link #post}: {@code null} when
     * the channel is down. Never log the bearer.
     *
     * @param path       e.g. {@code /v1/helper/cancelled}
     * @param timeoutSec curl {@code --max-time}; {@link #NO_TIME_LIMIT} for none
     * @return parsed response, empty map on blank 2xx body, or {@code null}
     */
    public Object get(String path, int timeoutSec) {
        String token = resolveToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        String socket = System.getenv("SQ_HELPER_SOCKET");
        if (socket == null || socket.isBlank()) {
            return null;
        }
        long t0 = System.nanoTime();
        System.out.println("sq-helper call start path=" + path + " budget=" + budget(timeoutSec));
        try {
            ProcessBuilder pb = new ProcessBuilder(curlArgs(timeoutSec,
                    "--unix-socket", socket,
                    "-H", "Authorization: Bearer " + token,
                    "http://localhost" + path));
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            String raw = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (p.exitValue() != 0) {
                System.out.println("sq-helper call fail path=" + path + " ms=" + ms
                        + " curlExit=" + p.exitValue());
                return null;
            }
            System.out.println("sq-helper call done path=" + path + " ms=" + ms);
            if (raw == null || raw.isBlank()) {
                return new LinkedHashMap<>();
            }
            return Json.parse(raw);
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("sq-helper call error path=" + path + " ms=" + ms
                    + " err=" + e.getClass().getSimpleName());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    /**
     * POST JSON and read a chunked {@code text/event-stream} via {@code curl -sS -N}.
     * Never throws. Missing token/socket, or a curl that cannot start, returns an
     * already-failed empty {@link SseStream}. Never log the bearer or the request body.
     *
     * @param path       e.g. {@code /v1/helper/execute/stream}
     * @param body       JSON-shaped map/list (serialised with {@link Json#write})
     * <p>No time limit: the stream stays open until the daemon ends it (done, error, or the run
     * being cancelled).
     *
     * @return live stream, or a failed empty stream; never {@code null}
     */
    SseStream postSse(String path, Object body) {
        String token = resolveToken();
        if (token == null || token.isBlank()) {
            return SseStream.failed("missing token");
        }
        String socket = System.getenv("SQ_HELPER_SOCKET");
        if (socket == null || socket.isBlank()) {
            return SseStream.failed("missing socket");
        }
        String json = body instanceof String s ? s : Json.write(body);
        // Same container-log trace as post(): when the channel wedges, this start line is
        // the last thing the node prints — the wedge marker.
        System.out.println("sq-helper stream start path=" + path + " budget=none");
        try {
            // Host in the URL is ignored when --unix-socket is set; pass the body on stdin.
            ProcessBuilder pb = new ProcessBuilder(curlArgs(NO_TIME_LIMIT, "-N",
                    "--unix-socket", socket,
                    "-H", "Authorization: Bearer " + token,
                    "-H", "Accept: text/event-stream",
                    "-H", "Content-Type: application/json",
                    "--data-binary", "@-",
                    "http://localhost" + path));
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            try (java.io.OutputStream curlIn = p.getOutputStream()) {
                curlIn.write((json == null ? "" : json).getBytes(StandardCharsets.UTF_8));
            }
            return SseStream.fromProcess(p);
        } catch (Exception e) {
            System.out.println("sq-helper stream error path=" + path
                    + " err=" + e.getClass().getSimpleName());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return SseStream.failed("curl start failed: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Resolve the current bearer. Never logs the value.
     *
     * @return token or {@code null} when none is configured
     */
    public String resolveToken() {
        if (helperTokenOverride != null && !helperTokenOverride.isBlank()) {
            return helperTokenOverride;
        }
        String a = System.getenv("SQ_HELPER_BEARER");
        if (a != null && !a.isBlank()) {
            return a;
        }
        String b = System.getenv("SQVM_HELPER_TOKEN");
        return (b != null && !b.isBlank()) ? b : null;
    }

    /**
     * Open (or reuse) a daemon-mediated tunnel to a named stateful instance.
     * Returns {@code null} when the helper channel is absent — never throws.
     *
     * @param containerName Engine Settings instance name (never an IP)
     * @param port          TCP port inside that container
     * @return local {@code 127.0.0.1:<ephemeral>} listener, or {@code null}
     */
    public ConnectTunnel connect(String containerName, int port) {
        if (containerName == null || containerName.isBlank()) {
            return null;
        }
        String key = containerName + ":" + port;
        ConnectTunnel existing = TUNNELS.get(key);
        if (existing != null && !existing.closed) {
            return existing;
        }
        ConnectTunnel tunnel = ConnectTunnel.open(this, containerName, port);
        if (tunnel == null) {
            return null;
        }
        TUNNELS.put(key, tunnel);
        return tunnel;
    }

    /**
     * Local loopback listener that relays bytes through {@code /v1/helper/connect/*}.
     *
     * <p>The sandbox has no route to sibling containers. Point a normal client
     * (JDBC, Redis, HTTP) at {@link #getHost()}:{@link #getPort()}.
     */
    public static final class ConnectTunnel implements AutoCloseable {
        private final HelperTransport transport;
        private final String containerName;
        private final int targetPort;
        private final ServerSocket listener;
        private final String host;
        private final int port;
        private volatile boolean closed;

        private ConnectTunnel(HelperTransport transport, String containerName, int targetPort,
                              ServerSocket listener) {
            this.transport = transport;
            this.containerName = containerName;
            this.targetPort = targetPort;
            this.listener = listener;
            this.host = listener.getInetAddress().getHostAddress();
            this.port = listener.getLocalPort();
        }

        /**
         * Probe {@code connect/open} then bind {@code 127.0.0.1:0}. {@code null} on failure.
         */
        static ConnectTunnel open(HelperTransport transport, String containerName, int targetPort) {
            try {
                String probe = openStream(transport, containerName, targetPort);
                if (probe == null || probe.isBlank()) {
                    return null;
                }
                closeStream(transport, probe);
                ServerSocket ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
                ConnectTunnel t = new ConnectTunnel(transport, containerName, targetPort, ss);
                Thread accept = new Thread(t::acceptLoop, "sq-connect-" + containerName);
                accept.setDaemon(true);
                accept.start();
                return t;
            } catch (Exception e) {
                return null;
            }
        }

        /** Loopback host to hand to a client library (always {@code 127.0.0.1}). */
        public String getHost() {
            return host;
        }

        /** Ephemeral local port the client library should dial. */
        public int getPort() {
            return port;
        }

        /**
         * HTTP URL for this tunnel. Use when the target speaks HTTP.
         *
         * <pre>{@code
         * ConnectTunnel api = helper.connect("my-service", 8080);
         * String health = api.url("http", "/health");
         * }</pre>
         */
        public String url(String scheme, String path) {
            String p = path == null ? "" : path;
            if (!p.isEmpty() && !p.startsWith("/")) {
                p = "/" + p;
            }
            return (scheme == null ? "http" : scheme) + "://" + host + ":" + port + p;
        }

        /** Stop accepting. Open streams die with the job on the daemon side. */
        @Override
        public void close() {
            closed = true;
            try {
                listener.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }

        private void acceptLoop() {
            while (!closed) {
                try {
                    Socket conn = listener.accept();
                    Thread serve = new Thread(() -> serve(conn), "sq-connect-stream");
                    serve.setDaemon(true);
                    serve.start();
                } catch (Exception e) {
                    return;
                }
            }
        }

        private void serve(Socket conn) {
            String streamId = openStream(transport, containerName, targetPort);
            if (streamId == null) {
                try {
                    conn.close();
                } catch (IOException ignored) {
                    // ignore
                }
                return;
            }
            Thread down = new Thread(() -> downlink(conn, streamId), "sq-connect-down");
            down.setDaemon(true);
            down.start();
            uplink(conn, streamId);
            try {
                down.join(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closeStream(transport, streamId);
            try {
                conn.close();
            } catch (IOException ignored) {
                // ignore
            }
        }

        /** Local client → daemon → instance. */
        private void uplink(Socket conn, String streamId) {
            byte[] buf = new byte[65536];
            try (InputStream in = conn.getInputStream()) {
                while (!closed) {
                    int n = in.read(buf);
                    if (n <= 0) {
                        return;
                    }
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("streamId", streamId);
                    body.put("dataB64", Base64.getEncoder().encodeToString(
                            java.util.Arrays.copyOf(buf, n)));
                    transport.post("/v1/helper/connect/send", body, 60);
                }
            } catch (Exception ignored) {
                // connection closed
            }
        }

        /** Instance → daemon → local client. */
        private void downlink(Socket conn, String streamId) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("streamId", streamId);
            body.put("timeoutMs", CONNECT_RECV_TIMEOUT_MS);
            int timeoutSec = (CONNECT_RECV_TIMEOUT_MS / 1000) + 30;
            try (OutputStream out = conn.getOutputStream()) {
                while (!closed) {
                    Object resp = transport.post("/v1/helper/connect/recv", body, timeoutSec);
                    Map<String, Object> map = Json.asMap(resp);
                    String chunk = Json.str(map.get("dataB64"));
                    if (!chunk.isEmpty()) {
                        out.write(Base64.getDecoder().decode(chunk));
                        out.flush();
                    }
                    if (Boolean.TRUE.equals(map.get("eof"))) {
                        return;
                    }
                }
            } catch (Exception ignored) {
                // connection closed
            }
        }

        private static String openStream(HelperTransport transport, String containerName, int port) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("containerName", containerName);
            body.put("port", port);
            Object resp = transport.post("/v1/helper/connect/open", body, 30);
            String id = Json.str(Json.asMap(resp).get("streamId"));
            return id.isBlank() ? null : id;
        }

        private static void closeStream(HelperTransport transport, String streamId) {
            if (streamId == null || streamId.isBlank()) {
                return;
            }
            transport.post("/v1/helper/connect/close", Map.of("streamId", streamId), 15);
        }
    }
}
