package studioquack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * In-container multi-app dispatch for stateful BYO-runtime images.
 *
 * <h2>The single-listener trap</h2>
 * <p>One instance hosts exactly <b>one</b> listener: {@code 0.0.0.0:$SQ_APP_PORT$SQ_APP_PATH}
 * (defaults {@code 8080} / {@code /invoke}). Every node whose execution target is this
 * instance POSTs to that same endpoint. The request body's {@code templateId} is the
 * dispatch key. This class is the Java helper for that contract: a <b>master</b> app
 * owns the process listener and routes other ids to <b>client</b> apps running as
 * sibling processes. The dispatcher never runs client code in its own JVM.
 *
 * <p>Application results are always HTTP <b>200</b> with a JSON object.
 * GET/HEAD on the path is {@code {"status":"up"}}. Handler faults call
 * {@code helper.fail} and answer {@code 200 {}}. {@code POST $SQ_APP_PATH/cancel}
 * marks a live {@code requestId} cancelled. Never log {@code helperToken}.
 *
 * <h2>Quick start — dispatcher (master / router)</h2>
 * <pre>{@code
 * StudioQuackDispatch.dispatcher("tpl_master")
 *         .localHandler((req, helper) -> Map.of("echo", "master"))
 *         .serve();
 * }</pre>
 *
 * <h2>Quick start — client (one node added later)</h2>
 * <pre>{@code
 * StudioQuackDispatch.client("tpl_web").serve((req, helper) -> Map.of("title", "ok"));
 * }</pre>
 *
 * <h2>The client's templateId</h2>
 * When the dispatcher compile-spawns a client from {@code /data/sq-bespoke/<templateId>},
 * it sets {@code SQ_TEMPLATE_ID} to that folder's id in the child's environment. A client
 * main should prefer it over any compiled-in constant so a re-imported node (new id) never
 * strands the registration:
 * <pre>{@code
 * String id = System.getenv().getOrDefault("SQ_TEMPLATE_ID", "tpl_web");
 * StudioQuackDispatch.client(id).serve((req, helper) -> ...);
 * }</pre>
 *
 * <h2>workspace.zip</h2>
 * The daemon bespoke channel is <em>flat</em> (basenames only). The FE uploads the
 * node workspace as {@code /data/sq-bespoke/&lt;templateId&gt;/workspace.zip}.
 * Compile-on-miss unpacks that zip into the bespoke dir when the zip mtime differs
 * from {@code .sq-unpacked-mtime}, then compiles every {@code *.java}. Entries with
 * absolute paths or {@code ..} are refused. Unpacked files overwrite same-named
 * paths; a matching marker skips a no-op unpack.
 *
 * <p>JDK 21, zero Maven deps. Reuses {@link Json} and {@link StudioQuackHelper}.
 */
public final class StudioQuackDispatch {

    /**
     * Stateful POST body cap (DoS bound). Oversize → 200 {@code request body too large}.
     *
     * <p>Sized for the daemon's inline-file transport, not for text: the daemon hydrates every
     * {@code sq-file:} handle to base64 {@code data} on <em>both</em> {@code executionContext.currentNode}
     * and {@code inputVariables} (node handlers read either), so one file rides the body twice at
     * 4/3 of its size. 32 MiB therefore admits files up to ~12 MiB, and stays inside the default
     * 128 MiB heap a 512 MiB stateful container gives this JVM once the body, its {@code String}
     * and the parsed map are all live. Raising this further needs a bigger container RAM cap.
     * {@link InvokeStreamSupport#MAX_LINE_CHARS} and
     * {@link StreamInvocation#MAX_ASSEMBLED_CHARS_PER_PORT} follow this value.
     */
    static final int MAX_BODY_BYTES = 32 * 1024 * 1024;

    /** Same allow-list as the daemon's bespoke-path rule — never interpolate an id into a path first. */
    private static final Pattern TEMPLATE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private static final String DEFAULT_PORT = "8080";
    private static final String DEFAULT_PATH = "/invoke";
    private static final String DEFAULT_DATA_ROOT = "/data";
    private static final String DEFAULT_APPS_DIR = "/data/sq-apps";
    private static final String DEFAULT_BESPOKE_DIR = "/data/sq-bespoke";
    private static final String DEFAULT_HELPER_JAR = "/app/sq-helper.jar";
    private static final String DEFAULT_TMP_APPS = "/tmp/sq-apps";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String WORKSPACE_ZIP = "workspace.zip";
    private static final String UNPACK_MARKER = ".sq-unpacked-mtime";
    private static final long MAX_UNPACK_BYTES = 32L * 1024 * 1024;

    /** One in-flight compile+spawn per templateId; waiters share the same lock object. */
    private static final ConcurrentHashMap<String, Object> COMPILE_LOCKS = new ConcurrentHashMap<>();

    /** Liveness probe: 1s connect. Any HTTP status = alive. */
    private static final HttpClient PROBE_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /** Forward path: 5s connect; no per-request read budget (the daemon's idle reaper owns liveness). */
    private static final HttpClient FORWARD_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /** Utility / factory class — no instances. */
    private StudioQuackDispatch() {
    }

    /**
     * Build a process-wide dispatcher that treats {@code selfTemplateId} as the local node.
     *
     * <p>Use this as {@code main}'s first call on a master / router image, then
     * {@link Dispatcher#onSelf} + {@link Dispatcher#serve()}.
     *
     * @param selfTemplateId this process's template id (null becomes blank; blank never matches)
     * @return a new dispatcher; call {@link Dispatcher#serve()} to bind and block
     */
    public static Dispatcher dispatcher(String selfTemplateId) {
        if (selfTemplateId == null || selfTemplateId.isBlank() || !validTemplateId(selfTemplateId)) {
            throw new IllegalArgumentException("invalid templateId");
        }
        return new Dispatcher(selfTemplateId);
    }

    /**
     * Client for a later node app that registers under {@code templateId}.
     *
     * <pre>{@code
     * StudioQuackDispatch.client("tpl_web").serve(handler);
     * }</pre>
     */
    public static Client client(String templateId) {
        if (templateId == null || templateId.isBlank() || !validTemplateId(templateId)) {
            throw new IllegalArgumentException("invalid templateId");
        }
        return new Client(templateId);
    }

    /**
     * Later node app: bind loopback, register, serve, delete the registry on shutdown.
     */
    public static final class Client {
        private final String templateId;

        private Client(String templateId) {
            this.templateId = templateId;
        }

        public void serve(InvokeHandler handler) {
            serveClient(templateId, handler);
        }
    }

    /**
     * Package-visible for tests — compile+spawn under the per-template lock.
     *
     * @return {@code null} on success (or nothing to compile), or an error map
     */
    static Map<String, Object> compileOnMiss(String templateId) {
        synchronized (COMPILE_LOCKS.computeIfAbsent(templateId, k -> new Object())) {
            return compileAndSpawn(templateId);
        }
    }

    /**
     * Client-mode listener: bind loopback ephemeral port, register under
     * {@code $SQ_APPS_DIR/<selfTemplateId>.json} (default {@code /data/sq-apps}),
     * serve {@code POST /invoke}, never return.
     *
     * <p>The dispatcher POSTs the original daemon body to
     * {@code http://127.0.0.1:<port>/invoke}. Invalid {@code selfTemplateId} / null
     * handler: one stderr line, return (no throw). Registry I/O fault after bind:
     * stderr, stop server, return. Never logs {@code helperToken}.
     *
     * @param selfTemplateId this process's template id ({@code ^[A-Za-z0-9._-]{1,64}$})
     * @param handler        per-request work; return an output-port-keyed map
     */
    public static void serveClient(String selfTemplateId, InvokeHandler handler) {
        if (selfTemplateId == null || selfTemplateId.isBlank() || !validTemplateId(selfTemplateId)) {
            System.err.println("StudioQuackDispatch.serveClient: missing or invalid templateId");
            return;
        }
        if (handler == null) {
            System.err.println("StudioQuackDispatch.serveClient: handler is required");
            return;
        }

        HttpServer server;
        ExecutorService exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "sq-client");
            t.setDaemon(true);
            return t;
        });
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(exec);
            server.createContext(appPath(), ex -> handleLocalExchange(ex, handler));
            server.createContext(streamPath(), ex -> InvokeStreamSupport.handle(
                    ex, tid -> InvokeStreamSupport.wrapUnary(handler)));
            server.createContext(cancelPath(), StudioQuackDispatch::handleCancel);
            server.start();
        } catch (Exception e) {
            System.err.println("StudioQuackDispatch.serveClient: bind failed: " + describe(e));
            exec.close();
            return;
        }

        int port = server.getAddress().getPort();
        if (!writeRegistry(selfTemplateId, port)) {
            System.err.println("StudioQuackDispatch.serveClient: registry write failed");
            server.stop(0);
            exec.close();
            return;
        }

        try {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> deleteRegistry(selfTemplateId), "sq-dispatch-unreg"));
        } catch (IllegalStateException ignored) {
            deleteRegistry(selfTemplateId);
            server.stop(0);
            exec.close();
            return;
        }

        System.out.println("StudioQuackDispatch.serveClient listening 127.0.0.1:"
                + port + appPath() + " id=" + selfTemplateId);
        parkForever();
    }

    /**
     * Reserved response key for a <b>dispatcher-level</b> failure: a request the dispatcher could
     * not even hand to a node handler — non-POST, unparseable or oversize body, missing/invalid
     * {@code templateId}, no handler registered, compile-on-miss failure, relay target unreachable,
     * or an unexpected exception outside {@link #runHandler}. Such a request carries no trustworthy
     * per-job helper token to {@code POST /v1/helper/fail} with, so the envelope itself is the
     * signal: the daemon's stateful path reads {@code {"__sq_fail":{errorCode,message}}} and fails
     * the step with that code, never binding the body to output ports. Node errors never use this
     * key — they go through {@code helper.fail} (see {@code runHandler}).
     */
    public static final String DISPATCH_FAIL_KEY = "__sq_fail";
    /** Non-POST, oversize or unparseable body, missing/invalid {@code templateId}. */
    public static final String DISPATCH_BAD_REQUEST = "DISPATCH_BAD_REQUEST";
    /** No handler is registered for the addressed {@code templateId} (and none could be compiled). */
    public static final String DISPATCH_NO_HANDLER = "DISPATCH_NO_HANDLER";
    /** The relay target for the {@code templateId} did not answer. */
    public static final String DISPATCH_UNREACHABLE = "DISPATCH_UNREACHABLE";
    /** Compile-on-miss of a non-resident node failed. */
    public static final String DISPATCH_COMPILE_FAILED = "DISPATCH_COMPILE_FAILED";
    /** Any other dispatcher exception. */
    public static final String DISPATCH_ERROR = "DISPATCH_ERROR";

    /** Builds the {@link #DISPATCH_FAIL_KEY} envelope. */
    public static Map<String, Object> dispatchFail(String errorCode, String message) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("errorCode", errorCode == null || errorCode.isBlank() ? DISPATCH_ERROR : errorCode);
        f.put("message", message == null ? "" : message);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(DISPATCH_FAIL_KEY, f);
        return out;
    }

    /**
     * One stateful invocation. Implementations must not log {@link InvokeRequest#helperToken()}.
     *
     * <p>Return a JSON object keyed by the node's OUTPUT port names. {@code null} becomes
     * {@code {}}. {@link StudioQuackError} and any other {@code Throwable} are folded
     * into {@code helper.fail} and answered as 200 {@code {}}. A returned top-level
     * {@code "error"} key is treated the same (legacy; logged as deprecated).
     */
    @FunctionalInterface
    public interface InvokeHandler {
        /**
         * Handle one POST. {@code requestBody} is the decoded JSON object (never log
         * {@code helperToken}). Return a JSON object keyed by OUTPUT port names.
         */
        Map<String, Object> handle(Map<String, Object> requestBody, StudioQuackHelper helper)
                throws Exception;
    }

    /**
     * Bidirectional stream node. Implementations must not log frame deltas or
     * {@code helperToken}. First {@link StreamEmitter#complete} / {@link StreamEmitter#error}
     * wins; the dispatcher always appends {@code event: done}.
     */
    @FunctionalInterface
    public interface StreamInvokeHandler {
        void handle(StreamInvocation in, StreamEmitter out, StudioQuackHelper helper) throws Exception;
    }

    /**
     * Resolve a stream handler by {@code templateId} (same key as unary {@code /invoke}).
     * {@code null} → {@code event: error} {@code unknown templateId}.
     */
    @FunctionalInterface
    public interface StreamHandlerLookup {
        StreamInvokeHandler lookup(String templateId);
    }

    /** @deprecated use {@link InvokeHandler} */
    @Deprecated
    @FunctionalInterface
    public interface Handler extends InvokeHandler {
    }

    /**
     * One daemon POST request. Accessors never return {@code null}.
     *
     * <p>{@link #helperToken()} exists so handlers can pass the bearer into
     * {@link StudioQuackHelper} — <b>never log this value</b>.
     */
    public static final class InvokeRequest {
        private final String rawBody;
        private final Map<String, Object> body;

        InvokeRequest(String rawBody, Map<String, Object> body) {
            this.rawBody = rawBody == null ? "" : rawBody;
            this.body = body == null ? Map.of() : body;
        }

        public String rawBody() { return rawBody; }
        public Map<String, Object> body() { return body; }
        public String templateId() { return Json.str(body.get("templateId")); }
        public String requestId() { return Json.str(body.get("requestId")); }
        public String workflowExecutionId() { return Json.str(body.get("workflowExecutionId")); }
        public String currentNodeId() { return Json.str(body.get("currentNodeId")); }
        public String envId() { return Json.str(body.get("envId")); }
        public String bespokeDir() { return Json.str(body.get("bespokeDir")); }
        public Map<String, Object> executionContext() { return Json.asMap(body.get("executionContext")); }
        public Map<String, Object> inputVariables() { return Json.asMap(body.get("inputVariables")); }

        /** Helper-channel bearer for this request. <b>Never log this.</b> */
        public String helperToken() { return Json.str(body.get("helperToken")); }
    }

    /**
     * Process listener: bind once, dispatch on {@code templateId}, never return.
     * Self-run wins: a POST whose {@code templateId} equals the constructor id (or is listed in
     * {@link #localTemplateIds}) never relays.
     */
    public static final class Dispatcher {
        private final String selfTemplateId;
        private volatile InvokeHandler selfHandler;
        private volatile StreamHandlerLookup streamLookup;
        private volatile java.util.Set<String> localTemplateIds = java.util.Set.of();

        Dispatcher(String selfTemplateId) {
            this.selfTemplateId = selfTemplateId == null ? "" : selfTemplateId;
        }

        /**
         * Install the handler for {@code selfTemplateId}.
         *
         * @return {@code this} for chaining into {@link #serve()}
         */
        public Dispatcher localHandler(InvokeHandler handler) {
            this.selfHandler = handler;
            return this;
        }

        /**
         * Extra template ids this process serves in-process through the {@link #localHandler},
         * exactly like {@code selfTemplateId} — no loopback client listener, no registry file.
         * A multi-node app that routes on {@code templateId} itself lists its ids here instead of
         * binding one {@link #client} listener per id (hundreds of loopback servers at boot).
         *
         * @return {@code this} for chaining into {@link #serve()}
         */
        public Dispatcher localTemplateIds(java.util.Collection<String> ids) {
            this.localTemplateIds = ids == null ? java.util.Set.of() : java.util.Set.copyOf(ids);
            return this;
        }

        private boolean servesLocally(String templateId) {
            return templateId.equals(selfTemplateId) || localTemplateIds.contains(templateId);
        }

        /**
         * Resolve {@code POST /invoke/stream} by {@code open.templateId} (NodeRegistry
         * path). When unset, the self {@link #localHandler} is wrapped as one-shot.
         */
        public Dispatcher streamLookup(StreamHandlerLookup lookup) {
            this.streamLookup = lookup;
            return this;
        }

        /** @deprecated use {@link #localHandler} */
        @Deprecated
        public Dispatcher onSelf(InvokeHandler handler) {
            return localHandler(handler);
        }

        /**
         * Bind {@code 0.0.0.0:$SQ_APP_PORT} (default 8080), context {@code $SQ_APP_PATH}
         * (default {@code /invoke}), then block forever.
         */
        public void serve() {
            if (this.selfHandler == null) {
                throw new IllegalStateException("localHandler required");
            }
            int port = envInt("SQ_APP_PORT", Integer.parseInt(DEFAULT_PORT));
            String path = appPath();
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                server.createContext(path, this::handleExchange);
                server.createContext(streamPath(), this::handleStreamExchange);
                server.createContext(cancelPath(), StudioQuackDispatch::handleCancel);
                server.setExecutor(Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "sq-dispatch");
                    t.setDaemon(true);
                    return t;
                }));
                server.start();
                System.out.println("StudioQuackDispatch dispatcher " + selfTemplateId
                        + " on 0.0.0.0:" + port + path + " and " + streamPath());
            } catch (Exception e) {
                System.err.println("StudioQuackDispatch: bind failed on 0.0.0.0:" + port + path
                        + " (" + describe(e) + ")");
                System.exit(1);
                return;
            }
            parkForever();
        }

        /**
         * Loopback bind for tests: {@code /invoke} + {@code /invoke/stream} on an
         * ephemeral port. Caller must {@code server.stop(0)}. Does not park.
         */
        HttpServer startLocalForTest() throws java.io.IOException {
            if (this.selfHandler == null) {
                throw new IllegalStateException("localHandler required");
            }
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(appPath(), this::handleExchange);
            server.createContext(streamPath(), this::handleStreamExchange);
            server.createContext(cancelPath(), StudioQuackDispatch::handleCancel);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "sq-dispatch-test");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            return server;
        }

        private void handleStreamExchange(HttpExchange ex) {
            StreamHandlerLookup lookup = this.streamLookup;
            if (lookup == null) {
                InvokeHandler self = this.selfHandler;
                lookup = tid -> {
                    if (tid == null || !servesLocally(tid) || self == null) {
                        return null;
                    }
                    return InvokeStreamSupport.wrapUnary(self);
                };
            }
            InvokeStreamSupport.handle(ex, lookup);
        }

        private void handleExchange(HttpExchange ex) {
            try {
                dispatch(ex);
            } catch (Exception | OutOfMemoryError e) {
                // OutOfMemoryError too: a large body that fails allocation must still close the
                // exchange with a reply, or the launcher's curl waits on it until the daemon reaper.
                // Never a silent {} — that would read as a successful node with empty outputs.
                sendJson(ex, 200, errorJson(e));
            }
        }

        private void dispatch(HttpExchange ex) {
            ParsedInvoke parsed = parseInvoke(ex);
            if (parsed == null) {
                return;
            }
            String templateId = parsed.templateId;
            if (templateId.isBlank()) {
                sendJson(ex, 200, dispatchFail(DISPATCH_BAD_REQUEST, "missing templateId"));
                return;
            }
            if (!validTemplateId(templateId)) {
                sendJson(ex, 200, dispatchFail(DISPATCH_BAD_REQUEST, "invalid templateId"));
                return;
            }
            if (servesLocally(templateId)) {
                InvokeHandler handler = this.selfHandler;
                if (handler == null) {
                    sendJson(ex, 200, dispatchFail(DISPATCH_NO_HANDLER, "no self handler"));
                    return;
                }
                runHandler(ex, handler, parsed.rawBody, parsed.body);
                return;
            }
            relayOrCompile(ex, templateId, parsed.rawBody);
        }
    }

    private static void handleLocalExchange(HttpExchange ex, InvokeHandler handler) {
        try {
            ParsedInvoke parsed = parseInvoke(ex);
            if (parsed == null) {
                return;
            }
            if (parsed.templateId.isBlank()) {
                sendJson(ex, 200, dispatchFail(DISPATCH_BAD_REQUEST, "missing templateId"));
                return;
            }
            if (!validTemplateId(parsed.templateId)) {
                sendJson(ex, 200, dispatchFail(DISPATCH_BAD_REQUEST, "invalid templateId"));
                return;
            }
            runHandler(ex, handler, parsed.rawBody, parsed.body);
        } catch (Exception | OutOfMemoryError e) {
            sendJson(ex, 200, errorJson(e));
        }
    }

    private static void runHandler(HttpExchange ex, InvokeHandler handler, String rawBody,
                                   Map<String, Object> body) {
        // Per-request stdout lines are the ONLY runtime trace `podman logs` (and therefore
        // Engine Settings → container logs) ever sees of an in-container node: node handlers
        // report through the helper channel and catch their own exceptions, so without these
        // lines the container log showed the boot banner and nothing else — errors included.
        // Content-free on purpose: ids, timing, error message — never payloads.
        String templateId = Json.str(body.get("templateId"));
        String requestId = Json.str(body.get("requestId"));
        if (templateId.isBlank()) {
            templateId = "<unknown>";
        }
        if (requestId.isBlank()) {
            requestId = "<none>";
        }
        long started = System.currentTimeMillis();
        System.out.println("sq-node invoke start id=" + templateId + " req=" + requestId);
        StudioQuackHelper helper = helperFor(body);
        try {
            Map<String, Object> out = handler.handle(body, helper);
            long ms = System.currentTimeMillis() - started;
            String failure = failureErrorText(out);
            if (failure != null) {
                System.out.println("sq-node invoke error id=" + templateId + " req=" + requestId
                        + " ms=" + ms + " error=" + capLog(failure));
                System.err.println("StudioQuackDispatch: deprecated top-level \"error\" key; "
                        + "throw StudioQuackError or call helper.fail instead");
                foldFail(helper, "NODE_ERROR", failure, null);
                sendJson(ex, 200, Map.of());
                return;
            }
            System.out.println("sq-node invoke done id=" + templateId + " req=" + requestId
                    + " ms=" + ms);
            Map<String, Object> reply = out == null ? new LinkedHashMap<>() : new LinkedHashMap<>(out);
            if (reply.isEmpty() && helper != null && helper.recordedWebhookOutcome() != null) {
                reply.putAll(helper.recordedWebhookOutcome());
            }
            if (helper != null && helper.recordedCorrelationKey() != null
                    && !reply.containsKey("__sq_correlation_key")) {
                reply.put("__sq_correlation_key", helper.recordedCorrelationKey());
            }
            sendJson(ex, 200, reply);
        } catch (StudioQuackError e) {
            long ms = System.currentTimeMillis() - started;
            System.out.println("sq-node invoke exception id=" + templateId + " req=" + requestId
                    + " ms=" + ms + " error=" + capLog(e.errorCode() + ": " + e.getMessage()));
            foldFail(helper, e.errorCode(), e.getMessage(), e.data());
            sendJson(ex, 200, Map.of());
        } catch (Throwable e) {
            long ms = System.currentTimeMillis() - started;
            System.out.println("sq-node invoke exception id=" + templateId + " req=" + requestId
                    + " ms=" + ms + " error=" + capLog(String.valueOf(e)));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("exceptionType", e.getClass().getName());
            String msg = e.getClass().getSimpleName() + ": "
                    + (e.getMessage() == null ? "" : e.getMessage());
            foldFail(helper, "SANDBOX_FAILED", msg, data);
            sendJson(ex, 200, Map.of());
        } finally {
            CancellationRegistry.unregister(Json.str(body.get("requestId")));
        }
    }

    /**
     * {@code POST $SQ_APP_PATH/cancel} {@code {requestId, reason}} →
     * {@code 200 {"ok":true}} when that request is live, else {@code {"ok":false}}.
     */
    static void handleCancel(HttpExchange ex) {
        if (ex == null) {
            return;
        }
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            sendJson(ex, 200, Map.of("status", "up"));
            return;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            sendJson(ex, 200, Map.of("ok", false));
            return;
        }
        try {
            String text = readUtf8(ex);
            Map<String, Object> body = Json.readObject(text);
            String requestId = Json.str(body.get("requestId"));
            boolean ok = CancellationRegistry.cancel(requestId);
            sendJson(ex, 200, Map.of("ok", ok));
        } catch (Exception e) {
            sendJson(ex, 200, Map.of("ok", false));
        }
    }

    /**
     * Non-blank top-level {@code error} is the deprecated failure channel.
     * A blank string is an output port (the test emulator's empty error channel)
     * and must be returned with the rest of the port map.
     */
    static String failureErrorText(Map<String, Object> out) {
        if (out == null || !out.containsKey("error")) {
            return null;
        }
        Object err = out.get("error");
        if (err == null) {
            return null;
        }
        String text = String.valueOf(err);
        return text.isBlank() ? null : text;
    }

    /** Best-effort {@code helper.fail}; never throws into the HTTP path. */
    static void foldFail(StudioQuackHelper helper, String code, String message,
                         Map<String, Object> data) {
        if (helper == null) {
            return;
        }
        try {
            helper.fail(code, message, data);
        } catch (Exception ignored) {
            // validation/transport must not prevent the 200 {} reply
        }
    }

    /** Caps a log fragment so a giant error message can never flood the container log. */
    private static String capLog(String s) {
        return s != null && s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private static ParsedInvoke parseInvoke(HttpExchange ex) {
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            sendJson(ex, 200, Map.of("status", "up"));
            return null;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            sendJson(ex, 200, dispatchFail(DISPATCH_BAD_REQUEST, "POST required"));
            return null;
        }
        BodyRead read = readBody(ex);
        if (read.tooLarge) {
            sendJson(ex, 200, dispatchFail(DISPATCH_BAD_REQUEST, "request body too large"));
            return null;
        }
        if (read.fault) {
            sendJson(ex, 200, dispatchFail(DISPATCH_BAD_REQUEST, "invalid JSON"));
            return null;
        }
        Map<String, Object> body;
        try {
            body = Json.readObject(read.text);
        } catch (Exception e) {
            sendJson(ex, 200, dispatchFail(DISPATCH_BAD_REQUEST, "invalid JSON"));
            return null;
        }
        return new ParsedInvoke(read.text, body, Json.str(body.get("templateId")));
    }

    static boolean validTemplateId(String id) {
        return id != null && TEMPLATE_ID.matcher(id).matches();
    }

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    static int envInt(String key, int fallback) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    static String appPath() {
        String path = env("SQ_APP_PATH", DEFAULT_PATH);
        return path.startsWith("/") ? path : "/" + path;
    }

    /** {@code POST $SQ_APP_PATH/cancel} — daemon marks the live request cancelled. */
    static String cancelPath() {
        String path = appPath();
        return path.endsWith("/") ? path + "cancel" : path + "/cancel";
    }

    /** {@code POST} path for bidirectional NDJSON→SSE invoke (prefix of {@link #appPath()}). */
    static String streamPath() {
        String path = appPath();
        return path.endsWith("/") ? path + "stream" : path + "/stream";
    }

    static Path dataRoot() {
        return Path.of(env("SQ_APPS_ROOT", DEFAULT_DATA_ROOT));
    }

    static boolean dataRootWritable() {
        try {
            Path root = dataRoot();
            if (!Files.isDirectory(root)) {
                Files.createDirectories(root);
            }
            return Files.isWritable(root);
        } catch (Exception e) {
            return false;
        }
    }

    static Path appsDir() {
        String explicit = System.getenv("SQ_APPS_DIR");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        if (dataRootWritable()) {
            return dataRoot().resolve("sq-apps");
        }
        return Path.of(env("SQ_TMP_APPS_DIR", DEFAULT_TMP_APPS));
    }

    static Path bespokeRoot() {
        String explicit = System.getenv("SQ_BESPOKE_DIR");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        return dataRoot().resolve("sq-bespoke");
    }

    static String helperJarPath() {
        return env("SQ_HELPER_JAR", DEFAULT_HELPER_JAR);
    }

    static void sendJson(HttpExchange ex, int status, Object body) {
        try {
            byte[] bytes = Json.write(body == null ? Map.of() : body).getBytes(StandardCharsets.UTF_8);
            sendRaw(ex, status, bytes, JSON_CONTENT_TYPE);
        } catch (Exception ignored) {
            try {
                ex.close();
            } catch (Exception ignoredClose) {
                // never throw
            }
        }
    }

    static void sendRaw(HttpExchange ex, int status, byte[] body, String contentType) {
        if (ex == null) {
            return;
        }
        try {
            byte[] bytes = body == null ? new byte[0] : body;
            String ct = (contentType == null || contentType.isBlank()) ? JSON_CONTENT_TYPE : contentType;
            ex.getResponseHeaders().set("Content-Type", ct);
            if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(status, -1);
                ex.close();
                return;
            }
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        } catch (Exception ignored) {
            // never throw
        }
    }

    static String readUtf8(HttpExchange ex) {
        BodyRead read = readBody(ex);
        return (read.fault || read.tooLarge || read.text == null) ? "" : read.text;
    }

    static StudioQuackHelper helperFor(Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        Map<String, Object> ctx = Json.asMap(safe.get("executionContext"));
        StudioQuackHelper helper = new StudioQuackHelper(ctx);
        helper.setRequestContext(ctx, Json.str(safe.get("helperToken")));
        helper.setInvocationEnvelope(safe);
        String requestId = Json.str(safe.get("requestId"));
        RequestCancellation cancel = CancellationRegistry.register(requestId);
        cancel.attachHandlerThread(Thread.currentThread());
        helper.bindCancellation(cancel);
        return helper;
    }

    static boolean writeRegistry(String templateId, int port) {
        if (templateId == null || !validTemplateId(templateId) || port < 1 || port > 65535) {
            return false;
        }
        Path tmp = null;
        try {
            Path dir = appsDir();
            Files.createDirectories(dir);
            Path dest = dir.resolve(templateId + ".json");
            tmp = dir.resolve(templateId + ".json.tmp");
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("host", "127.0.0.1");
            rec.put("port", port);
            rec.put("pid", ProcessHandle.current().pid());
            rec.put("startedAt", Instant.now().toString());
            Files.writeString(tmp, Json.write(rec), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception atomicFailed) {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            return false;
        }
    }

    static Map<String, Object> readRegistry(String templateId) {
        if (templateId == null || !validTemplateId(templateId)) {
            return null;
        }
        try {
            Path dest = appsDir().resolve(templateId + ".json");
            if (!Files.isRegularFile(dest, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            Object parsed = Json.parse(Files.readString(dest, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?>)) {
                return null;
            }
            Map<String, Object> rec = new LinkedHashMap<>(Json.asMap(parsed));
            Object portObj = rec.get("port");
            if (!(portObj instanceof Number n)) {
                return null;
            }
            int port = n.intValue();
            if (port < 1 || port > 65535) {
                return null;
            }
            rec.put("port", port);
            return rec;
        } catch (Exception e) {
            return null;
        }
    }

    static void deleteRegistry(String templateId) {
        if (templateId == null || !validTemplateId(templateId)) {
            return;
        }
        try {
            Files.deleteIfExists(appsDir().resolve(templateId + ".json"));
        } catch (Exception ignored) {
            // best-effort
        }
    }

    static void relayOrCompile(HttpExchange ex, String templateId, String rawBody) {
        try {
            if (!validTemplateId(templateId)) {
                sendJson(ex, 200, dispatchFail(DISPATCH_BAD_REQUEST, "invalid templateId"));
                return;
            }
            Integer port = liveRegisteredPort(templateId);
            if (port != null) {
                forwardRaw(ex, port, rawBody, templateId);
                return;
            }
            Map<String, Object> compileErr;
            synchronized (COMPILE_LOCKS.computeIfAbsent(templateId, k -> new Object())) {
                if (liveRegisteredPort(templateId) == null) {
                    compileErr = compileAndSpawn(templateId);
                } else {
                    compileErr = null;
                }
            }
            if (compileErr != null) {
                System.out.println("sq-relay compile-failed id=" + templateId
                        + " error=" + capLog(String.valueOf(compileErr.get("error"))));
                sendJson(ex, 200, dispatchFail(DISPATCH_COMPILE_FAILED,
                        String.valueOf(compileErr.get("error"))));
                return;
            }
            port = liveRegisteredPort(templateId);
            if (port != null) {
                forwardRaw(ex, port, rawBody, templateId);
                return;
            }
            System.out.println("sq-relay unregistered id=" + templateId);
            sendJson(ex, 200, dispatchFail(DISPATCH_NO_HANDLER,
                    "no app registered for templateId '" + templateId + "'"));
        } catch (Exception e) {
            sendJson(ex, 200, errorJson(e));
        }
    }

    static boolean isLive(int port) {
        if (port <= 0 || port > 65535) {
            return false;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(loopbackInvokeUri(port))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
            PROBE_HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    static void forwardRaw(HttpExchange ex, int port, String rawBody, String templateId) {
        String id = validTemplateId(templateId) ? templateId : "";
        if (port <= 0 || port > 65535) {
            System.out.println("sq-relay unreachable id=" + id + " port=" + port);
            sendJson(ex, 200, dispatchFail(DISPATCH_UNREACHABLE, "app for templateId '" + id + "' unreachable"));
            return;
        }
        long started = System.currentTimeMillis();
        System.out.println("sq-relay start id=" + id + " port=" + port);
        try {
            byte[] payload = (rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8);
            // No per-request read timeout: a long AI-agent LLM/tool loop (or a master agent parked
            // awaiting a delegated sub-agent) legitimately runs far past any fixed cap. Liveness is
            // owned by the daemon launcher's activity-driven idle reaper, which only reaps a genuinely
            // silent request. A fixed cap here fired "did not answer within 300000ms" while the
            // sub-agent was still producing — the master agent then aborted on a healthy child.
            // (SQ_DISPATCH_FORWARD_TIMEOUT_MS is honoured only as an explicit positive override.)
            int timeoutMs = envInt("SQ_DISPATCH_FORWARD_TIMEOUT_MS", 0);
            HttpRequest.Builder reqB = HttpRequest.newBuilder()
                    .uri(loopbackInvokeUri(port))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
            if (timeoutMs > 0) {
                reqB.timeout(Duration.ofMillis(timeoutMs));
            }
            HttpRequest req = reqB.build();
            HttpResponse<byte[]> resp = FORWARD_HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            System.out.println("sq-relay done id=" + id + " port=" + port
                    + " ms=" + (System.currentTimeMillis() - started) + " status=" + resp.statusCode());
            sendRaw(ex, 200, resp.body(), JSON_CONTENT_TYPE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("sq-relay interrupted id=" + id + " port=" + port);
            sendJson(ex, 200, dispatchFail(DISPATCH_UNREACHABLE, "app for templateId '" + id + "' unreachable"));
        } catch (java.net.http.HttpTimeoutException e) {
            // Only reachable when an operator set an explicit SQ_DISPATCH_FORWARD_TIMEOUT_MS override
            // (the default is unbounded — the daemon's idle reaper owns liveness). Keep the distinct
            // "did not answer" wording so a real override timeout is never mistaken for a
            // registration fault ("unreachable").
            System.out.println("sq-relay timeout id=" + id + " port=" + port
                    + " ms=" + (System.currentTimeMillis() - started));
            sendJson(ex, 200, dispatchFail(DISPATCH_UNREACHABLE, "app for templateId '" + id
                    + "' did not answer within " + envInt("SQ_DISPATCH_FORWARD_TIMEOUT_MS", 0) + "ms"));
        } catch (Exception e) {
            System.out.println("sq-relay error id=" + id + " port=" + port
                    + " error=" + capLog(String.valueOf(e)));
            sendJson(ex, 200, dispatchFail(DISPATCH_UNREACHABLE, "app for templateId '" + id + "' unreachable"));
        }
    }

    static Map<String, Object> compileAndSpawn(String templateId) {
        if (!validTemplateId(templateId)) {
            return err("invalid templateId");
        }
        Process child = null;
        try {
            if (liveRegisteredPort(templateId) != null) {
                return null;
            }
            Path dir = resolveBespokeDir(templateId);
            if (dir == null) {
                return null;
            }
            boolean hadZip = Files.isRegularFile(dir.resolve(WORKSPACE_ZIP), LinkOption.NOFOLLOW_LINKS);
            Map<String, Object> unpacked = unpackWorkspaceZip(dir);
            if (unpacked != null) {
                return unpacked;
            }
            List<Path> javaFiles = listJavaSources(dir);
            if (javaFiles.isEmpty()) {
                return hadZip ? err("compile failed", "no .java sources under " + dir) : null;
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return err("image cannot compile Java (JRE-only; use a JDK base image)");
            }
            Path classesDir = dir.resolve("classes");
            if (Files.isSymbolicLink(classesDir)) {
                return err("compile failed", "classes directory is a symlink");
            }
            Files.createDirectories(classesDir);
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fm = compiler.getStandardFileManager(
                    diagnostics, Locale.US, StandardCharsets.UTF_8)) {
                Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(javaFiles);
                List<String> options = List.of(
                        "-encoding", "UTF-8",
                        "-d", classesDir.toAbsolutePath().toString(),
                        "-cp", helperJarPath());
                Boolean ok = compiler.getTask(null, fm, diagnostics, options, null, units).call();
                if (!Boolean.TRUE.equals(ok)) {
                    return err("compile failed", cap(formatDiagnostics(diagnostics), 4000));
                }
            }
            String mainClass = determineMainClass(dir, javaFiles);
            if (mainClass == null || mainClass.isBlank()) {
                return err("compile failed",
                        "no mainClass (set sq-app.properties or add public static void main)");
            }
            Path log = dir.resolve("sq-app.err");
            Path out = dir.resolve("sq-app.out");
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            String cp = classesDir.toAbsolutePath() + File.pathSeparator + helperJarPath();
            ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", cp, mainClass.trim());
            pb.directory(dir.toFile());
            // The client learns its dispatch id from its injection folder — never from a
            // hardcoded constant that a re-import would stale. Client mains should read
            // SQ_TEMPLATE_ID first (see the class javadoc).
            pb.environment().put("SQ_TEMPLATE_ID", templateId);
            pb.redirectOutput(out.toFile());
            pb.redirectError(log.toFile());
            child = pb.start();
            int waitMs = Math.max(0, envInt("SQ_DISPATCH_REGISTER_WAIT_MS", 15_000));
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs);
            while (System.nanoTime() < deadline) {
                if (!child.isAlive()) {
                    return err("compile failed", "client process exited before registering");
                }
                if (liveRegisteredPort(templateId) != null) {
                    return null;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    destroyQuietly(child);
                    return err("compile failed", "interrupted while waiting for client to register");
                }
            }
            destroyQuietly(child);
            deleteRegistry(templateId);
            return err("client did not register in time");
        } catch (Exception e) {
            destroyQuietly(child);
            return err("compile failed", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    /**
     * Unpack {@code workspace.zip} when its mtime differs from {@code .sq-unpacked-mtime}.
     * Absolute paths and {@code ..} are refused. Same-named files are overwritten.
     *
     * @return {@code null} when there is no zip, the marker matches, or unpack succeeded
     */
    static Map<String, Object> unpackWorkspaceZip(Path bespoke) {
        Path zip = bespoke.resolve(WORKSPACE_ZIP);
        try {
            if (!Files.isRegularFile(zip, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            long mtime = Files.getLastModifiedTime(zip, LinkOption.NOFOLLOW_LINKS).toMillis();
            Path marker = bespoke.resolve(UNPACK_MARKER);
            if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    if (Long.parseLong(Files.readString(marker, StandardCharsets.UTF_8).trim()) == mtime) {
                        return null;
                    }
                } catch (Exception ignored) {
                    // stale marker — unpack
                }
            }
            Path root = bespoke.toAbsolutePath().normalize();
            long total = 0;
            try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    String name = entry.getName();
                    Path dest = safeZipDest(root, name);
                    if (dest == null) {
                        return err("compile failed", "unsafe zip entry: " + name);
                    }
                    if (dest.equals(zip) || dest.equals(marker)) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(dest);
                        continue;
                    }
                    Files.createDirectories(dest.getParent());
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = zin.read(chunk)) >= 0) {
                        total += n;
                        if (total > MAX_UNPACK_BYTES) {
                            return err("compile failed", "workspace.zip too large");
                        }
                        buf.write(chunk, 0, n);
                    }
                    Files.write(dest, buf.toByteArray());
                }
            }
            Files.writeString(marker, Long.toString(mtime), StandardCharsets.UTF_8);
            return null;
        } catch (Exception e) {
            return err("compile failed", "unpack failed: " + e.getClass().getSimpleName());
        }
    }

    /** {@code null} when the entry is absolute, contains {@code ..}, or escapes {@code root}. */
    static Path safeZipDest(Path root, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.replace('\\', '/');
        if (n.startsWith("/") || n.startsWith("/")) {
            return null;
        }
        Path rel;
        try {
            rel = Path.of(n);
        } catch (Exception e) {
            return null;
        }
        if (rel.isAbsolute()) {
            return null;
        }
        for (Path part : rel) {
            if ("..".equals(part.toString())) {
                return null;
            }
        }
        Path dest = root.resolve(rel).normalize();
        if (!dest.startsWith(root)) {
            return null;
        }
        return dest;
    }

    static Map<String, Object> err(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message == null ? "" : message);
        return m;
    }

    static Map<String, Object> err(String message, String detail) {
        Map<String, Object> m = err(message);
        if (detail != null && !detail.isBlank()) {
            m.put("detail", cap(detail, 4000));
        }
        return m;
    }

    static String tailFile(Path file, int maxChars) {
        if (file == null || maxChars <= 0) {
            return "";
        }
        try {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return "";
            }
            long size = Files.size(file);
            if (size <= 0) {
                return "";
            }
            int byteTake = (int) Math.min(size, ((long) maxChars * 4L) + 16L);
            byte[] buf = new byte[byteTake];
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                ch.position(size - byteTake);
                ByteBuffer bb = ByteBuffer.wrap(buf);
                while (bb.hasRemaining()) {
                    int n = ch.read(bb);
                    if (n < 0) {
                        break;
                    }
                }
                String s = new String(buf, 0, bb.position(), StandardCharsets.UTF_8);
                return s.length() > maxChars ? s.substring(s.length() - maxChars) : s;
            }
        } catch (Exception e) {
            return "";
        }
    }

    static String determineMainClass(Path dir, List<Path> javaFiles) {
        if (dir != null) {
            Path props = dir.resolve("sq-app.properties");
            if (!Files.isSymbolicLink(props)
                    && Files.isRegularFile(props, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Properties p = new Properties();
                    try (InputStream in = Files.newInputStream(props)) {
                        p.load(in);
                    }
                    String mc = p.getProperty("mainClass");
                    if (mc != null && !mc.isBlank()) {
                        return mc.trim();
                    }
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        if (javaFiles == null || javaFiles.isEmpty()) {
            return null;
        }
        List<Path> sorted = new ArrayList<>(javaFiles);
        sorted.sort(Comparator.comparing(p -> p.toAbsolutePath().toString()));
        for (Path src : sorted) {
            String fqcn = extractMainFqcn(src);
            if (fqcn != null && !fqcn.isBlank()) {
                return fqcn;
            }
        }
        return null;
    }

    static Integer liveRegisteredPort(String templateId) {
        if (!validTemplateId(templateId)) {
            return null;
        }
        Map<String, Object> reg = readRegistry(templateId);
        if (reg == null) {
            return null;
        }
        int port = registryPort(reg);
        if (port <= 0) {
            deleteRegistry(templateId);
            return null;
        }
        if (isLive(port)) {
            return port;
        }
        deleteRegistry(templateId);
        return null;
    }

    static int registryPort(Map<String, Object> reg) {
        if (reg == null) {
            return -1;
        }
        Object portObj = reg.get("port");
        int port = -1;
        if (portObj instanceof Number n) {
            port = n.intValue();
        } else {
            try {
                String s = Json.str(portObj).trim();
                if (!s.isEmpty()) {
                    port = Integer.parseInt(s);
                }
            } catch (Exception ignored) {
                return -1;
            }
        }
        return (port <= 0 || port > 65535) ? -1 : port;
    }

    static URI loopbackInvokeUri(int port) {
        return URI.create("http://127.0.0.1:" + port + appPath());
    }

    static Path resolveBespokeDir(String templateId) {
        if (!validTemplateId(templateId)) {
            return null;
        }
        Path root = bespokeRoot().toAbsolutePath().normalize();
        Path dir = root.resolve(templateId).normalize();
        if (!dir.startsWith(root) || dir.equals(root)) {
            return null;
        }
        try {
            if (Files.isSymbolicLink(dir)) {
                Path real = dir.toRealPath();
                Path realRoot = Files.exists(root) ? root.toRealPath() : root;
                if (!real.startsWith(realRoot)) {
                    return null;
                }
                return Files.isDirectory(real) ? real : null;
            }
            if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            return dir;
        } catch (Exception e) {
            return null;
        }
    }

    static List<Path> listJavaSources(Path dir) {
        List<Path> out = new ArrayList<>();
        if (dir == null) {
            return out;
        }
        Path root = dir.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.forEach(p -> {
                try {
                    if (Files.isSymbolicLink(p)) {
                        return;
                    }
                    Path abs = p.toAbsolutePath().normalize();
                    Path classesDir = root.resolve("classes");
                    if (abs.startsWith(classesDir)) {
                        return;
                    }
                    if (!abs.startsWith(root) || !abs.getFileName().toString().endsWith(".java")) {
                        return;
                    }
                    if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                        out.add(abs);
                    }
                } catch (Exception ignored) {
                    // skip
                }
            });
        } catch (Exception ignored) {
            return out;
        }
        out.sort(Comparator.comparing(Path::toString));
        return out;
    }

    static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        if (diagnostics == null || diagnostics.getDiagnostics().isEmpty()) {
            return "compilation failed";
        }
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(d.getKind()).append(' ');
            JavaFileObject src = d.getSource();
            if (src != null) {
                sb.append(src.getName());
                if (d.getLineNumber() != Diagnostic.NOPOS) {
                    sb.append(':').append(d.getLineNumber());
                }
                sb.append(": ");
            }
            sb.append(d.getMessage(Locale.US));
        }
        return sb.toString();
    }

    static String cap(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        return (maxChars <= 0 || s.length() <= maxChars) ? s : s.substring(0, maxChars);
    }

    static String extractMainFqcn(Path src) {
        if (src == null || Files.isSymbolicLink(src)) {
            return null;
        }
        String text;
        try {
            text = Files.readString(src, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
        if (!text.contains("public static void main(")) {
            return null;
        }
        String pkg = null;
        String cls = null;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (pkg == null && trimmed.startsWith("package ")) {
                String rest = trimmed.substring("package ".length()).trim();
                if (rest.endsWith(";")) {
                    rest = rest.substring(0, rest.length() - 1).trim();
                }
                if (!rest.isBlank()) {
                    pkg = rest;
                }
            }
            if (cls == null) {
                int idx = trimmed.indexOf("public class ");
                if (idx >= 0) {
                    String rest = trimmed.substring(idx + "public class ".length()).trim();
                    StringBuilder name = new StringBuilder();
                    for (int i = 0; i < rest.length(); i++) {
                        char c = rest.charAt(i);
                        if (i == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c)) {
                            name.append(c);
                        } else {
                            break;
                        }
                    }
                    if (name.length() > 0) {
                        cls = name.toString();
                    }
                }
            }
        }
        if (cls == null) {
            return null;
        }
        return (pkg == null || pkg.isBlank()) ? cls : pkg + "." + cls;
    }

    private static void destroyQuietly(Process child) {
        if (child == null || !child.isAlive()) {
            return;
        }
        try {
            child.destroyForcibly();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    /** Dispatcher-level exception → {@link #DISPATCH_FAIL_KEY} envelope (code {@link #DISPATCH_ERROR}). */
    private static Map<String, Object> errorJson(Throwable e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        return dispatchFail(DISPATCH_ERROR, e.getClass().getSimpleName() + ": " + message);
    }

    private static BodyRead readBody(HttpExchange ex) {
        try {
            long declared = -1L;
            String cl = ex.getRequestHeaders().getFirst("Content-Length");
            if (cl != null && !cl.isBlank()) {
                try {
                    declared = Long.parseLong(cl.trim());
                } catch (NumberFormatException ignored) {
                    // stream cap below
                }
                if (declared > MAX_BODY_BYTES) {
                    drainQuietly(ex.getRequestBody());
                    return BodyRead.tooLarge();
                }
            }
            InputStream in = ex.getRequestBody();
            if (in == null) {
                return BodyRead.ok("");
            }
            // Pre-size from Content-Length so a multi-MiB body does not double its own footprint
            // through the default grow-by-copy — the heap this JVM gets in a stateful container is
            // small (see MAX_BODY_BYTES), and the spike is what turns a large file into an OOM.
            ByteArrayOutputStream buf = new ByteArrayOutputStream(
                    declared > 0 ? (int) Math.min(declared, MAX_BODY_BYTES) : 8192);
            byte[] chunk = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(chunk)) >= 0) {
                total += n;
                if (total > MAX_BODY_BYTES) {
                    drainQuietly(in);
                    return BodyRead.tooLarge();
                }
                buf.write(chunk, 0, n);
            }
            return BodyRead.ok(buf.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return BodyRead.fault();
        }
    }

    /**
     * Consumes the rest of an oversize request body so the reply is not written mid-upload.
     *
     * <p>Deliberately a {@code read} loop, never {@code skip}: on JDK 21 the server's request-body
     * stream does not override {@code skip}, so {@code skip(Long.MAX_VALUE)} fell through to the raw
     * socket and, once the body was consumed, blocked waiting for bytes that never come on the
     * keep-alive connection. The client then never received the "too large" reply and the daemon's
     * launcher hung until its idle reaper. {@code read} goes through the fixed-length wrapper, which
     * returns {@code -1} at Content-Length.
     */
    private static void drainQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            byte[] sink = new byte[8192];
            while (in.read(sink) >= 0) {
                // discard
            }
        } catch (Exception ignored) {
            // never throw
        }
    }

    /**
     * Exception class + message (the OS errno text, e.g. "Address already in use" vs
     * "Cannot assign requested address"): the class name alone cannot tell the causes apart.
     */
    private static String describe(Exception e) {
        String msg = e.getMessage();
        return msg == null || msg.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + msg;
    }

    private static void parkForever() {
        CountDownLatch hold = new CountDownLatch(1);
        while (true) {
            try {
                hold.await();
            } catch (InterruptedException ignored) {
                // stay up
            }
        }
    }

    private static final class ParsedInvoke {
        final String rawBody;
        final Map<String, Object> body;
        final String templateId;

        ParsedInvoke(String rawBody, Map<String, Object> body, String templateId) {
            this.rawBody = rawBody;
            this.body = body;
            this.templateId = templateId;
        }
    }

    private static final class BodyRead {
        final String text;
        final boolean tooLarge;
        final boolean fault;

        private BodyRead(String text, boolean tooLarge, boolean fault) {
            this.text = text;
            this.tooLarge = tooLarge;
            this.fault = fault;
        }

        static BodyRead ok(String text) {
            return new BodyRead(text, false, false);
        }

        static BodyRead tooLarge() {
            return new BodyRead("", true, false);
        }

        static BodyRead fault() {
            return new BodyRead("", false, true);
        }
    }
}
