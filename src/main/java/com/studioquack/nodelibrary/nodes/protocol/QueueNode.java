package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.datastore.RedisResp;
import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Queue: {@code enqueue | dequeue | peek | length | listen} against Redis RESP
 * or a bounded in-memory backend (lab / single-replica only).
 *
 * <p>{@code listen} is ingress. AMQP is not implemented here — use the
 * {@code rabbitmq} node. Redis passwords and URL user-info are never logged.
 */
public final class QueueNode implements NodeHandler {

    public static final String DEFAULT_ID = "eddd9c84-944a-437b-8ddf-35b80ef07f9e";

    static final int DEFAULT_TIMEOUT = 25;
    static final int MAX_TIMEOUT = 60;
    static final int DEFAULT_MAX_LEN = 10_000;
    static final int HARD_MAX_LEN = 10_000;
    static final int MAX_VALUE_CHARS = RedisResp.MAX_BULK;
    static final int MAX_MEMORY_KEYS = 64;

    private static final String INGRESS_MESSAGE =
            "Inbound events are not a node operation. Use a BPMN message start / "
                    + "intermediate catch / boundary message event; the StudioQuack webhook "
                    + "ingress correlates onto those events.";

    private static final Set<String> OPS = Set.of("enqueue", "dequeue", "peek", "length", "listen");
    private static final Set<String> BACKENDS = Set.of("redis", "memory");

    private static final MemoryBackend MEMORY = new MemoryBackend();

    private final String templateId;

    public QueueNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
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
        Map<String, Object> in = DataSupport.readInput(request, helper);
        String url = DataSupport.cfgStr(cfg, "url", null);
        String password = passwordFromUrl(url);
        try {
            String op = operation(in, cfg);
            if ("listen".equals(op)) {
                return DataSupport.invalidInput(INGRESS_MESSAGE);
            }
            String backend = backend(cfg);
            String key = firstNonBlank(DataSupport.inStr(in, "key", null), DataSupport.cfgStr(cfg, "key", null));
            if (key == null || key.isBlank()) {
                return DataSupport.invalidInput("key is required");
            }
            int maxLen = clampMaxLen(DataSupport.cfgInt(cfg, "max_len", DEFAULT_MAX_LEN));
            int ttl = DataSupport.cfgInt(cfg, "ttl_seconds", 0);
            int timeout = clampTimeout(DataSupport.cfgInt(cfg, "timeout_seconds", DEFAULT_TIMEOUT));
            return switch (backend) {
                case "memory" -> memory(op, key, in, maxLen, ttl);
                default -> redis(op, key, in, url, password, maxLen, ttl, timeout);
            };
        } catch (StudioQuackError e) {
            throw redactError(e, password);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return DataSupport.fail(DataSupport.redact(safeMsg(e), password));
        }
    }

    static String operation(Map<String, Object> in, Map<String, Object> cfg) {
        String op = DataSupport.inStrAny(in, DataSupport.cfgStr(cfg, "operation", "enqueue"),
                "operation", "op");
        if (op == null || op.isBlank()) {
            throw invalid("operation is required. Accepted: enqueue, dequeue, peek, length, listen");
        }
        op = op.trim().toLowerCase(Locale.ROOT);
        if (!OPS.contains(op)) {
            throw invalid("Unknown operation: " + op
                    + ". Accepted: enqueue, dequeue, peek, length, listen");
        }
        return op;
    }

    static String backend(Map<String, Object> cfg) {
        String raw = DataSupport.cfgStr(cfg, "backend", "redis");
        String b = raw == null || raw.isBlank() ? "redis" : raw.trim().toLowerCase(Locale.ROOT);
        if ("amqp".equals(b)) {
            throw invalid("Unknown backend: amqp. Accepted: redis, memory. For AMQP use the rabbitmq node.");
        }
        if (!BACKENDS.contains(b)) {
            throw invalid("Unknown backend: " + b + ". Accepted: redis, memory. For AMQP use the rabbitmq node.");
        }
        return b;
    }

    static String encodeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return Json.write(value);
    }

    static Target parseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw invalid("url is required for backend=redis (redis:// or rediss://)");
        }
        String t = url.trim();
        if (!t.startsWith("redis://") && !t.startsWith("rediss://")) {
            throw invalid("url must be redis:// or rediss://. For AMQP use the rabbitmq node.");
        }
        URI u = URI.create(t);
        String host = u.getHost();
        if (host == null || host.isBlank()) {
            throw invalid("redis url host is required");
        }
        int port = u.getPort() > 0 ? u.getPort() : 6379;
        int db = 0;
        String path = u.getPath();
        if (path != null && path.length() > 1) {
            try {
                db = Integer.parseInt(path.substring(1));
            } catch (NumberFormatException ignored) {
                db = 0;
            }
        }
        return new Target(host, port, Math.max(db, 0), t.startsWith("rediss://"));
    }

    static String passwordFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String ui = URI.create(url).getUserInfo();
            if (ui == null || ui.isBlank()) {
                return null;
            }
            int colon = ui.indexOf(':');
            return colon >= 0 ? ui.substring(colon + 1) : ui;
        } catch (Exception e) {
            return null;
        }
    }

    static void clearMemory() {
        MEMORY.clear();
    }

    private static Map<String, Object> memory(String op, String key, Map<String, Object> in,
                                              int maxLen, int ttl) {
        return switch (op) {
            case "enqueue" -> {
                String value = requireValue(in);
                int length = MEMORY.enqueue(key, value, maxLen, ttl);
                yield ok(Map.of("key", key, "length", length));
            }
            case "dequeue" -> ok(mapOf("key", key, "value", MEMORY.dequeue(key)));
            case "peek" -> ok(mapOf("key", key, "value", MEMORY.peek(key)));
            default -> ok(Map.of("key", key, "length", MEMORY.length(key)));
        };
    }

    private static Map<String, Object> redis(String op, String key, Map<String, Object> in,
                                             String url, String password, int maxLen, int ttl,
                                             int timeout) throws Exception {
        Target t = parseUrl(url);
        try (Socket sock = open(t, timeout)) {
            sock.setSoTimeout(timeout * 1000);
            OutputStream out = sock.getOutputStream();
            InputStream inStream = sock.getInputStream();
            if (password != null && !password.isBlank()) {
                RedisResp.write(out, "AUTH", password);
                RedisResp.read(inStream);
            }
            if (t.db > 0) {
                RedisResp.write(out, "SELECT", Integer.toString(t.db));
                RedisResp.read(inStream);
            }
            return switch (op) {
                case "enqueue" -> {
                    String value = requireValue(in);
                    RedisResp.write(out, "LLEN", key);
                    long len = asLong(RedisResp.read(inStream));
                    if (len >= maxLen) {
                        throw DataSupport.failError("queue is full (max_len=" + maxLen + ")");
                    }
                    RedisResp.write(out, "LPUSH", key, value);
                    Object n = RedisResp.read(inStream);
                    if (ttl > 0) {
                        RedisResp.write(out, "EXPIRE", key, Integer.toString(ttl));
                        RedisResp.read(inStream);
                    }
                    yield ok(Map.of("key", key, "length", asLong(n)));
                }
                case "dequeue" -> {
                    RedisResp.write(out, "RPOP", key);
                    yield ok(mapOf("key", key, "value", RedisResp.read(inStream)));
                }
                case "peek" -> {
                    RedisResp.write(out, "LINDEX", key, "-1");
                    yield ok(mapOf("key", key, "value", RedisResp.read(inStream)));
                }
                default -> {
                    RedisResp.write(out, "LLEN", key);
                    yield ok(Map.of("key", key, "length", asLong(RedisResp.read(inStream))));
                }
            };
        }
    }

    private static String requireValue(Map<String, Object> in) {
        if (in == null || !in.containsKey("value")) {
            throw invalid("value is required for enqueue");
        }
        String encoded = encodeValue(in.get("value"));
        if (encoded.length() > MAX_VALUE_CHARS) {
            throw invalid("value exceeds " + MAX_VALUE_CHARS + " characters");
        }
        return encoded;
    }

    private static Socket open(Target t, int timeoutSeconds) throws Exception {
        int ms = timeoutSeconds * 1000;
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(t.host, t.port), ms);
        if (!t.tls) {
            return raw;
        }
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, null, new SecureRandom());
        var ssl = (javax.net.ssl.SSLSocket) ctx.getSocketFactory().createSocket(raw, t.host, t.port, true);
        SSLParameters p = ssl.getSSLParameters();
        p.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(p);
        ssl.startHandshake();
        return ssl;
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int clampMaxLen(int n) {
        if (n <= 0) {
            return DEFAULT_MAX_LEN;
        }
        return Math.min(n, HARD_MAX_LEN);
    }

    private static int clampTimeout(int seconds) {
        if (seconds <= 0) {
            return DEFAULT_TIMEOUT;
        }
        return Math.min(seconds, MAX_TIMEOUT);
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("data", data);
        return DataSupport.ports(body);
    }

    private static StudioQuackError invalid(String message) {
        return new StudioQuackError("INVALID_INPUT",
                message == null || message.isBlank() ? "invalid input" : message);
    }

    private static StudioQuackError redactError(StudioQuackError e, String password) {
        String msg = DataSupport.redact(e.getMessage(), password);
        if (msg.equals(e.getMessage())) {
            return e;
        }
        return new StudioQuackError(e.errorCode(), msg, e.data());
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + m;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    record Target(String host, int port, int db, boolean tls) {
    }

    static final class MemoryBackend {
        private final ConcurrentHashMap<String, MemoryQ> qs = new ConcurrentHashMap<>();

        int enqueue(String key, String value, int maxLen, int ttlSeconds) {
            MemoryQ created = new MemoryQ();
            MemoryQ q = qs.computeIfAbsent(key, k -> {
                if (qs.size() >= MAX_MEMORY_KEYS) {
                    throw DataSupport.failError("memory backend key cap (" + MAX_MEMORY_KEYS + ") reached");
                }
                return created;
            });
            synchronized (q) {
                q.purge();
                if (q.items.size() >= maxLen) {
                    throw DataSupport.failError("queue is full (max_len=" + maxLen + ")");
                }
                long exp = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000L : 0L;
                q.items.addLast(new MemoryItem(value, exp));
                return q.items.size();
            }
        }

        String dequeue(String key) {
            MemoryQ q = qs.get(key);
            if (q == null) {
                return null;
            }
            synchronized (q) {
                q.purge();
                MemoryItem item = q.items.pollFirst();
                if (q.items.isEmpty()) {
                    qs.remove(key, q);
                }
                return item == null ? null : item.value;
            }
        }

        String peek(String key) {
            MemoryQ q = qs.get(key);
            if (q == null) {
                return null;
            }
            synchronized (q) {
                q.purge();
                MemoryItem item = q.items.peekFirst();
                return item == null ? null : item.value;
            }
        }

        int length(String key) {
            MemoryQ q = qs.get(key);
            if (q == null) {
                return 0;
            }
            synchronized (q) {
                q.purge();
                return q.items.size();
            }
        }

        void clear() {
            qs.clear();
        }
    }

    private static final class MemoryQ {
        final ArrayDeque<MemoryItem> items = new ArrayDeque<>();

        void purge() {
            long now = System.currentTimeMillis();
            while (!items.isEmpty()) {
                MemoryItem head = items.peekFirst();
                if (head == null || !head.expired(now)) {
                    break;
                }
                items.pollFirst();
            }
        }
    }

    private record MemoryItem(String value, long expiresAtMs) {
        boolean expired(long now) {
            return expiresAtMs > 0 && now >= expiresAtMs;
        }
    }
}
