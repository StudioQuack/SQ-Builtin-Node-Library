package com.studioquack.nodelibrary.nodes.datastore;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import studioquack.StudioQuackError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisCacheNodeTest {

    @Test
    void encodeValueJsonForObjects() {
        assertEquals("hi", RedisCacheNode.encodeValue("hi"));
        assertEquals("1", RedisCacheNode.encodeValue(1));
        assertTrue(RedisCacheNode.encodeValue(Map.of("role", "user")).contains("user"));
    }

    @Test
    void parseUrlExtractsHostPortDbAndTls() {
        RedisCacheNode.Target t = RedisCacheNode.parseUrl("rediss://:s3cret@cache.internal:6380/2");
        assertEquals("cache.internal", t.host());
        assertEquals(6380, t.port());
        assertEquals(2, t.db());
        assertTrue(t.tls());
        assertEquals("s3cret", RedisCacheNode.passwordFromUrl("rediss://:s3cret@cache.internal:6380/2"));
    }

    @Test
    void respRoundTripArrayAndBulk() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RedisResp.write(out, "LRANGE", "k", "0", "-1");
        String wire = out.toString(StandardCharsets.UTF_8);
        assertTrue(wire.startsWith("*4\r\n"));
        InputStream in = new ByteArrayInputStream("*2\r\n$2\r\nhi\r\n$3\r\nbye\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("hi", "bye"), RedisResp.read(in));
    }

    @Test
    void getAgainstMockRedis() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            var exec = Executors.newSingleThreadExecutor();
            exec.submit(() -> {
                try (Socket s = server.accept()) {
                    RedisResp.read(s.getInputStream());
                    s.getOutputStream().write("$3\r\nxyz\r\n".getBytes(StandardCharsets.UTF_8));
                    s.getOutputStream().flush();
                } catch (Exception ignored) {
                    // test teardown
                }
            });
            RedisCacheNode node = new RedisCacheNode();
            Map<String, Object> req = Map.of(
                    "inputVariables", Map.of("input", Map.of("operation", "GET", "key", "k")),
                    "executionContext", Map.of("currentNode", Map.of("data", Map.of(
                            "configurationParameters", List.of(
                                    Map.of("shortCode", "host", "value", "127.0.0.1"),
                                    Map.of("shortCode", "port", "value", server.getLocalPort()),
                                    Map.of("shortCode", "timeout_seconds", "value", "5"))))));
            Map<String, Object> out = node.handle(req, null);
            Map<String, Object> output = (Map<String, Object>) out.get("output");
            assertEquals("ok", output.get("status"));
            assertEquals("xyz", ((Map<?, ?>) output.get("data")).get("value"));
            exec.shutdownNow();
        }
    }

    @Test
    void missingKeyIsInvalidInput() {
        RedisCacheNode node = new RedisCacheNode();
        Map<String, Object> req = Map.of(
                "inputVariables", Map.of("input", Map.of("operation", "GET")),
                "executionContext", Map.of("currentNode", Map.of("data", Map.of(
                        "configurationParameters", List.of(
                                Map.of("shortCode", "host", "value", "127.0.0.1"))))));
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(req, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("key is required", e.getMessage());
    }

    @Test
    void unsupportedOperationIsInvalidInput() {
        RedisCacheNode node = new RedisCacheNode();
        Map<String, Object> req = Map.of(
                "inputVariables", Map.of("input", Map.of("operation", "FLUSHALL", "key", "k")),
                "executionContext", Map.of("currentNode", Map.of("data", Map.of(
                        "configurationParameters", List.of(
                                Map.of("shortCode", "host", "value", "127.0.0.1"))))));
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(req, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("unsupported operation", e.getMessage());
    }

    @Test
    void connectionFailureThrowsAndRedactsPassword() {
        RedisCacheNode node = new RedisCacheNode();
        String password = "redis-secret-pass";
        Map<String, Object> req = Map.of(
                "inputVariables", Map.of("input", Map.of("operation", "GET", "key", "k")),
                "executionContext", Map.of("currentNode", Map.of("data", Map.of(
                        "configurationParameters", List.of(
                                Map.of("shortCode", "host", "value", "127.0.0.1"),
                                Map.of("shortCode", "port", "value", 1),
                                Map.of("shortCode", "password", "value", password),
                                Map.of("shortCode", "timeout_seconds", "value", "1"))))));
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(req, null));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertFalse(e.getMessage().contains(password), e.getMessage());
    }
}
