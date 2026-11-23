package com.studioquack.nodelibrary.nodes.datastore;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generic Redis cache: SET / GET / DEL / LPUSH / RPUSH / LRANGE / LTRIM / EXPIRE.
 *
 * <p>Customer owns the Redis. Password and URL stay in configurationParameters
 * and are never logged. One TCP (or TLS) connection per invoke.
 */
public final class RedisCacheNode implements NodeHandler {

    public static final String DEFAULT_ID = "7c2e1a90-4b6d-4f11-9e3a-2d8c5f0a1b77";

    private static final Set<String> OPS = Set.of(
            "SET", "GET", "DEL", "LPUSH", "RPUSH", "LRANGE", "LTRIM", "EXPIRE");

    private final String templateId;

    public RedisCacheNode() {
        this(DEFAULT_ID);
    }

    public RedisCacheNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        Map<String, Object> cfg = DataSupport.readConfig(request);
        Map<String, Object> in = DataSupport.readInput(request, helper);
        String password = firstNonBlank(DataSupport.cfgStr(cfg, "password", null),
                passwordFromUrl(DataSupport.cfgStr(cfg, "url", null)));
        int timeout = clampTimeout(DataSupport.cfgInt(cfg, "timeout_seconds", 25));
        try {
            Target t = resolveTarget(cfg, in);
            String op = DataSupport.inStrAny(in, DataSupport.cfgStr(cfg, "operation", "GET"),
                    "operation", "command", "op").trim().toUpperCase(Locale.ROOT);
            if (!OPS.contains(op)) {
                return DataSupport.invalidInput("unsupported operation");
            }
            String key = DataSupport.inStrAny(in, DataSupport.cfgStr(cfg, "key", null), "key");
            if (key == null || key.isBlank()) {
                return DataSupport.invalidInput("key is required");
            }
            Object result = exchange(t, password, timeout, op, key, in, cfg);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", result);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "ok");
            body.put("data", data);
            return DataSupport.ports(body);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(DataSupport.redact(
                    e.getMessage() == null || e.getMessage().isBlank() ? "invalid input" : e.getMessage(),
                    password));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return fail(e.getClass().getSimpleName() + ": " + safeMsg(e), password);
        }
    }

    private static Object exchange(Target t, String password, int timeout, String op, String key,
                                   Map<String, Object> in, Map<String, Object> cfg) throws Exception {
        try (Socket sock = open(t, timeout, DataSupport.cfgStr(cfg, "verify_tls", "true"))) {
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
                case "GET" -> {
                    RedisResp.write(out, "GET", key);
                    yield RedisResp.read(inStream);
                }
                case "DEL" -> {
                    RedisResp.write(out, "DEL", key);
                    yield RedisResp.read(inStream);
                }
                case "SET" -> {
                    RedisResp.write(out, "SET", key, encodeValue(in.get("value")));
                    Object ok = RedisResp.read(inStream);
                    int ttl = ttlOf(in, cfg);
                    if (ttl > 0) {
                        RedisResp.write(out, "EXPIRE", key, Integer.toString(ttl));
                        RedisResp.read(inStream);
                    }
                    yield ok;
                }
                case "LPUSH", "RPUSH" -> {
                    RedisResp.write(out, op, key, encodeValue(in.get("value")));
                    Object n = RedisResp.read(inStream);
                    int ttl = ttlOf(in, cfg);
                    if (ttl > 0) {
                        RedisResp.write(out, "EXPIRE", key, Integer.toString(ttl));
                        RedisResp.read(inStream);
                    }
                    yield n;
                }
                case "LRANGE" -> {
                    RedisResp.write(out, "LRANGE", key,
                            Integer.toString(DataSupport.inInt(in, "start", 0)),
                            Integer.toString(stopOf(in)));
                    yield RedisResp.read(inStream);
                }
                case "LTRIM" -> {
                    RedisResp.write(out, "LTRIM", key,
                            Integer.toString(DataSupport.inInt(in, "start", 0)),
                            Integer.toString(stopOf(in)));
                    yield RedisResp.read(inStream);
                }
                case "EXPIRE" -> {
                    int ttl = ttlOf(in, cfg);
                    if (ttl <= 0) {
                        throw new IllegalArgumentException("ttl_seconds must be a positive integer");
                    }
                    RedisResp.write(out, "EXPIRE", key, Integer.toString(ttl));
                    yield RedisResp.read(inStream);
                }
                default -> throw new IllegalArgumentException("unsupported operation");
            };
        }
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

    private static int stopOf(Map<String, Object> in) {
        Object v = in == null ? null : in.get("stop");
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static int ttlOf(Map<String, Object> in, Map<String, Object> cfg) {
        int fromIn = DataSupport.inInt(in, "ttl_seconds", 0);
        if (fromIn <= 0) {
            fromIn = DataSupport.inInt(in, "ttl", 0);
        }
        if (fromIn > 0) {
            return fromIn;
        }
        return DataSupport.cfgInt(cfg, "ttl_seconds", 0);
    }

    static Target resolveTarget(Map<String, Object> cfg, Map<String, Object> in) {
        String url = firstNonBlank(DataSupport.inStr(in, "url", null), DataSupport.cfgStr(cfg, "url", null));
        if (url != null && (url.startsWith("redis://") || url.startsWith("rediss://"))) {
            return parseUrl(url);
        }
        String host = firstNonBlank(DataSupport.inStr(in, "host", null), DataSupport.cfgStr(cfg, "host", "127.0.0.1"));
        int port = DataSupport.inInt(in, "port", DataSupport.cfgInt(cfg, "port", 6379));
        int db = DataSupport.cfgInt(cfg, "db", 0);
        if (db <= 0) {
            db = 0;
        }
        boolean tls = url != null && url.startsWith("rediss://");
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host is required");
        }
        return new Target(host.trim(), port > 0 ? port : 6379, db, tls);
    }

    static Target parseUrl(String url) {
        URI u = URI.create(url);
        String host = u.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("redis url host is required");
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
        return new Target(host, port, Math.max(db, 0), url.startsWith("rediss://"));
    }

    static String passwordFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI u = URI.create(url);
            String ui = u.getUserInfo();
            if (ui == null || ui.isBlank()) {
                return null;
            }
            int colon = ui.indexOf(':');
            return colon >= 0 ? ui.substring(colon + 1) : ui;
        } catch (Exception e) {
            return null;
        }
    }

    private static Socket open(Target t, int timeoutSeconds, String verifyTls) throws Exception {
        int ms = timeoutSeconds * 1000;
        if (!t.tls) {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(t.host, t.port), ms);
            return s;
        }
        boolean verify = verifyTls == null || !"false".equalsIgnoreCase(verifyTls.trim());
        SSLContext ctx = SSLContext.getInstance("TLS");
        if (verify) {
            ctx.init(null, null, new SecureRandom());
        } else {
            ctx.init(null, new TrustManager[]{INSECURE}, new SecureRandom());
        }
        var factory = ctx.getSocketFactory();
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(t.host, t.port), ms);
        var ssl = (javax.net.ssl.SSLSocket) factory.createSocket(raw, t.host, t.port, true);
        if (verify) {
            SSLParameters p = ssl.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(p);
        }
        ssl.startHandshake();
        return ssl;
    }

    private static Map<String, Object> fail(String message, String password) {
        return DataSupport.fail(DataSupport.redact(
                message == null || message.isBlank() ? "request failed" : message, password));
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? "" : m;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static int clampTimeout(int seconds) {
        if (seconds <= 0) {
            return 25;
        }
        return Math.min(seconds, 60);
    }

    record Target(String host, int port, int db, boolean tls) {
    }

    private static final X509ExtendedTrustManager INSECURE = new X509ExtendedTrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] c, String a) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] c, String a, java.net.Socket s) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a, java.net.Socket s) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] c, String a, javax.net.ssl.SSLEngine e) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a, javax.net.ssl.SSLEngine e) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
