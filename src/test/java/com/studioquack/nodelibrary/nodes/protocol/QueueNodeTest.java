package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.nodes.datastore.RedisResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QueueNode: in-memory backend, fake RESP Redis, ingress, AMQP rejection.
 */
class QueueNodeTest {

    private final QueueNode node = new QueueNode("queue-test");

    @AfterEach
    void tearDown() {
        QueueNode.clearMemory();
    }

    @Test
    void templateIdUsesConstructorArgument() {
        assertEquals("queue-test", node.templateId());
        assertEquals(QueueNode.DEFAULT_ID, new QueueNode(" ").templateId());
    }

    @Test
    void memoryEnqueueDequeuePeekLength() {
        Map<String, Object> cfg = Map.of("operation", "enqueue", "backend", "memory", "key", "jobs");
        Map<String, Object> enq = output(node.handle(request(cfg, Map.of("value", Map.of("n", 1))), null));
        assertEquals("ok", enq.get("status"));
        assertEquals(1L, ((Number) data(enq).get("length")).longValue());

        Map<String, Object> peek = output(node.handle(request(
                Map.of("operation", "peek", "backend", "memory", "key", "jobs"), Map.of()), null));
        assertTrue(String.valueOf(data(peek).get("value")).contains("\"n\""));

        Map<String, Object> len = output(node.handle(request(
                Map.of("operation", "length", "backend", "memory", "key", "jobs"), Map.of()), null));
        assertEquals(1L, ((Number) data(len).get("length")).longValue());

        Map<String, Object> deq = output(node.handle(request(
                Map.of("operation", "dequeue", "backend", "memory", "key", "jobs"), Map.of()), null));
        assertTrue(String.valueOf(data(deq).get("value")).contains("\"n\""));

        Map<String, Object> empty = output(node.handle(request(
                Map.of("operation", "dequeue", "backend", "memory", "key", "jobs"), Map.of()), null));
        assertNull(data(empty).get("value"));
    }

    @Test
    void memoryRejectsWhenFull() {
        node.handle(request(Map.of("operation", "enqueue", "backend", "memory",
                "key", "cap", "max_len", "1"), Map.of("value", "a")), null);
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "enqueue", "backend", "memory", "key", "cap", "max_len", "1"),
                Map.of("value", "b")), null));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertTrue(e.getMessage().contains("full"), e.getMessage());
    }

    @Test
    void listenIsIngress() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "listen", "backend", "memory", "key", "j"), Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("BPMN message"), e.getMessage());
    }

    @Test
    void amqpBackendPointsAtRabbitmqNode() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "enqueue", "backend", "amqp", "key", "j"),
                Map.of("value", "x")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("rabbitmq"), e.getMessage());
    }

    @Test
    void enqueueRequiresValueAndKey() {
        StudioQuackError value = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "enqueue", "backend", "memory", "key", "j"), Map.of()), null));
        assertEquals("INVALID_INPUT", value.errorCode());
        assertTrue(value.getMessage().contains("value"), value.getMessage());

        StudioQuackError key = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "enqueue", "backend", "memory"), Map.of("value", "x")), null));
        assertEquals("INVALID_INPUT", key.errorCode());
        assertTrue(key.getMessage().contains("key"), key.getMessage());
    }

    @Test
    void parseUrlExtractsHostPortDbAndTls() {
        QueueNode.Target t = QueueNode.parseUrl("rediss://:s3cret@cache.example.com:6380/2");
        assertEquals("cache.example.com", t.host());
        assertEquals(6380, t.port());
        assertEquals(2, t.db());
        assertTrue(t.tls());
        assertEquals("s3cret", QueueNode.passwordFromUrl("rediss://:s3cret@cache.example.com:6380/2"));
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> QueueNode.parseUrl("amqp://broker"));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("rabbitmq"), e.getMessage());
    }

    @Test
    void encodeValueJsonForObjects() {
        assertEquals("hi", QueueNode.encodeValue("hi"));
        assertEquals("1", QueueNode.encodeValue(1));
        assertTrue(QueueNode.encodeValue(Map.of("role", "user")).contains("user"));
    }

    @Test
    void redisEnqueueDequeueAgainstFakeResp() throws Exception {
        try (FakeRedis redis = FakeRedis.start()) {
            String url = "redis://127.0.0.1:" + redis.port() + "/0";
            Map<String, Object> enq = output(node.handle(request(
                    Map.of("operation", "enqueue", "backend", "redis", "url", url,
                            "key", "jobs", "timeout_seconds", "5"),
                    Map.of("value", "alpha")), null));
            assertEquals("ok", enq.get("status"));
            assertEquals(1L, ((Number) data(enq).get("length")).longValue());

            Map<String, Object> peek = output(node.handle(request(
                    Map.of("operation", "peek", "backend", "redis", "url", url,
                            "key", "jobs", "timeout_seconds", "5"), Map.of()), null));
            assertEquals("alpha", data(peek).get("value"));

            Map<String, Object> deq = output(node.handle(request(
                    Map.of("operation", "dequeue", "backend", "redis", "url", url,
                            "key", "jobs", "timeout_seconds", "5"), Map.of()), null));
            assertEquals("alpha", data(deq).get("value"));

            Map<String, Object> len = output(node.handle(request(
                    Map.of("operation", "length", "backend", "redis", "url", url,
                            "key", "jobs", "timeout_seconds", "5"), Map.of()), null));
            assertEquals(0L, ((Number) data(len).get("length")).longValue());
        }
    }

    @Test
    void redisPasswordIsRedactedOnFailure() throws Exception {
        try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = closed.getLocalPort();
            closed.close();
            StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(
                    Map.of("operation", "enqueue", "backend", "redis",
                            "url", "redis://:s3cret-redis@127.0.0.1:" + port + "/0",
                            "key", "jobs", "timeout_seconds", "1"),
                    Map.of("value", "x")), null));
            assertEquals("REQUEST_FAILED", e.errorCode());
            assertFalse(e.getMessage().contains("s3cret-redis"), e.getMessage());
        }
    }

    private static Map<String, Object> request(Map<String, Object> cfg, Map<String, Object> input) {
        List<Object> params = new ArrayList<>();
        for (var e : cfg.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", e.getKey());
            row.put("value", e.getValue());
            params.add(row);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        request.put("inputVariables", Map.of("input", input));
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> ports) {
        return (Map<String, Object>) ports.get("output");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> output) {
        return (Map<String, Object>) output.get("data");
    }

    private static final class FakeRedis implements AutoCloseable {
        private final ServerSocket server;
        private final java.util.concurrent.ExecutorService exec;
        private final ConcurrentHashMap<String, ArrayDeque<String>> lists = new ConcurrentHashMap<>();
        private volatile boolean running = true;

        static FakeRedis start() throws Exception {
            return new FakeRedis();
        }

        private FakeRedis() throws Exception {
            server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            exec = Executors.newCachedThreadPool();
            exec.submit(this::acceptLoop);
        }

        int port() {
            return server.getLocalPort();
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket s = server.accept();
                    exec.submit(() -> handle(s));
                } catch (Exception e) {
                    return;
                }
            }
        }

        private void handle(Socket s) {
            try (s) {
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                while (true) {
                    Object raw = RedisResp.read(in);
                    List<String> cmd = cmd(raw);
                    if (cmd.isEmpty()) {
                        continue;
                    }
                    String op = cmd.get(0).toUpperCase();
                    switch (op) {
                        case "AUTH", "SELECT", "EXPIRE" -> out.write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
                        case "LLEN" -> {
                            int n = list(cmd.get(1)).size();
                            out.write((":" + n + "\r\n").getBytes(StandardCharsets.UTF_8));
                        }
                        case "LPUSH" -> {
                            ArrayDeque<String> q = list(cmd.get(1));
                            q.addFirst(cmd.size() > 2 ? cmd.get(2) : "");
                            out.write((":" + q.size() + "\r\n").getBytes(StandardCharsets.UTF_8));
                        }
                        case "RPOP" -> {
                            String v = list(cmd.get(1)).pollLast();
                            writeBulk(out, v);
                        }
                        case "LINDEX" -> {
                            ArrayDeque<String> q = list(cmd.get(1));
                            int idx = cmd.size() > 2 ? Integer.parseInt(cmd.get(2)) : -1;
                            String v;
                            if (idx < 0) {
                                v = q.isEmpty() ? null : q.peekLast();
                            } else {
                                v = idx < q.size() ? q.toArray(String[]::new)[idx] : null;
                            }
                            writeBulk(out, v);
                        }
                        default -> out.write("-ERR unknown\r\n".getBytes(StandardCharsets.UTF_8));
                    }
                    out.flush();
                }
            } catch (Exception ignored) {
                // client closed
            }
        }

        private ArrayDeque<String> list(String key) {
            return lists.computeIfAbsent(key, k -> new ArrayDeque<>());
        }

        private static void writeBulk(OutputStream out, String v) throws Exception {
            if (v == null) {
                out.write("$-1\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] b = v.getBytes(StandardCharsets.UTF_8);
            out.write(("$" + b.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(b);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        @SuppressWarnings("unchecked")
        private static List<String> cmd(Object raw) {
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    out.add(o == null ? "" : String.valueOf(o));
                }
                return out;
            }
            return List.of(raw == null ? "" : String.valueOf(raw));
        }

        @Override
        public void close() {
            running = false;
            exec.shutdownNow();
            try {
                server.close();
            } catch (Exception ignored) {
                // test teardown
            }
        }
    }
}
