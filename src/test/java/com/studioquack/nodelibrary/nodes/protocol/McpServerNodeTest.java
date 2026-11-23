package com.studioquack.nodelibrary.nodes.protocol;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studioquack.Json;
import studioquack.StudioQuackError;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP Server node against a loopback Streamable HTTP peer: session, auth, SSE body, deny paths.
 */
class McpServerNodeTest {

    private static final String TOKEN = "super-secret-token-99";

    private final McpServerNode node = new McpServerNode("mcp-test");
    private HttpServer server;
    private String base;
    private final AtomicInteger deletes = new AtomicInteger();
    private final AtomicInteger posts = new AtomicInteger();
    private volatile String mode = "json";
    private volatile Map<String, String> opHeaders = Map.of();
    private volatile String opBody = "";

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void templateIdUsesConstructorArgument() {
        assertEquals("mcp-test", node.templateId());
        assertEquals(McpServerNode.DEFAULT_ID, new McpServerNode(" ").templateId());
        assertEquals(McpServerNode.DEFAULT_ID, new McpServerNode().templateId());
    }

    @Test
    void listToolsNegotiatesSessionAndDeletesIt() throws Exception {
        start();
        Map<String, Object> out = output(node.handle(request(Map.of(
                "server_url", base,
                "timeout_seconds", "5",
                "protocol_version", "2025-06-18"), Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals(200, ((Number) out.get("http_status")).intValue());
        @SuppressWarnings("unchecked")
        List<Object> tools = (List<Object>) data(out).get("tools");
        assertEquals("echo", Json.asMap(tools.get(0)).get("name"));
        assertEquals("2025-03-26", opHeaders.get("mcp-protocol-version"));
        assertEquals("sess-abc", opHeaders.get("mcp-session-id"));
        assertEquals(base.replace("/mcp", ""), opHeaders.get("origin"));
        assertEquals(1, deletes.get());
        assertTrue(posts.get() >= 3);
    }

    @Test
    void callToolSendsBearerArgumentsAndReadsSse() throws Exception {
        start();
        mode = "sse";
        Map<String, Object> out = output(node.handle(request(Map.of(
                "server_url", base,
                "operation", "list_tools",
                "auth_type", "bearer",
                "auth_token", TOKEN,
                "timeout_seconds", "5"),
                Map.of("operation", "call_tool", "tool_name", "echo",
                        "arguments", Map.of("text", "hi"))), null));
        assertEquals("ok", out.get("status"));
        assertEquals("hi", data(out).get("text"));
        assertEquals("Bearer " + TOKEN, opHeaders.get("authorization"));
        assertTrue(opBody.contains("\"name\":\"echo\""));
        assertTrue(opBody.contains("tools/call"));
        assertFalse(opBody.contains("tools/list"));
    }

    @Test
    void headerAuthUsesConfiguredName() throws Exception {
        start();
        mode = "header";
        Map<String, Object> out = output(node.handle(request(Map.of(
                "server_url", base,
                "operation", "ping",
                "auth_type", "header",
                "auth_header", "X-Api-Key",
                "auth_token", TOKEN,
                "timeout_seconds", "5"), Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals(TOKEN, opHeaders.get("x-api-key"));
        assertFalse(opHeaders.containsKey("authorization"));
    }

    @Test
    void rejectsBadInputBeforeAnyRequest() {
        assertInvalid("server_url is required", Map.of(), Map.of());
        assertInvalid("must be http or https", Map.of("server_url", "ftp://127.0.0.1/mcp"), Map.of());
        assertInvalid("userinfo", Map.of("server_url", "http://user:pass@127.0.0.1/mcp"), Map.of());
        assertInvalid("must not contain the auth token", Map.of(
                "server_url", "http://127.0.0.1/mcp?access=" + TOKEN,
                "auth_token", TOKEN), Map.of());
        assertInvalid("Unknown operation", Map.of("server_url", "http://127.0.0.1/mcp"),
                Map.of("operation", "stdio"));
        assertInvalid("tool_name is required", Map.of("server_url", "http://127.0.0.1/mcp",
                "operation", "call_tool"), Map.of());
        assertInvalid("arguments must be a JSON object", Map.of("server_url", "http://127.0.0.1/mcp",
                "operation", "call_tool"), Map.of("tool_name", "echo", "arguments", List.of(1)));
        assertInvalid("arguments is too large", Map.of("server_url", "http://127.0.0.1/mcp",
                "operation", "call_tool"), Map.of("tool_name", "echo",
                "arguments", Map.of("blob", "x".repeat(McpServerNode.MAX_ARGUMENTS))));
        assertInvalid("protocol_version", Map.of("server_url", "http://127.0.0.1/mcp",
                "protocol_version", "1999-01-01"), Map.of());
        assertInvalid("auth_token is required", Map.of("server_url", "http://127.0.0.1/mcp",
                "auth_type", "bearer"), Map.of());
        assertInvalid("auth_header is invalid", Map.of("server_url", "http://127.0.0.1/mcp",
                "auth_type", "header", "auth_header", "Authorization", "auth_token", TOKEN), Map.of());
        assertEquals(0, posts.get());
    }

    @Test
    void http401IsAuthFailedAndBodyDoesNotLeakToken() throws Exception {
        start();
        mode = "unauthorized";
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(Map.of(
                "server_url", base,
                "auth_type", "bearer",
                "auth_token", TOKEN,
                "timeout_seconds", "5"), Map.of()), null));
        assertEquals("AUTH_FAILED", e.errorCode());
        assertFalse(e.getMessage().contains(TOKEN), e.getMessage());
        assertTrue(e.getMessage().contains("***"), e.getMessage());
        assertEquals(0, deletes.get());
    }

    @Test
    void jsonRpcErrorIsRedacted() throws Exception {
        start();
        mode = "rpc-error";
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(Map.of(
                "server_url", base,
                "auth_type", "bearer",
                "auth_token", TOKEN,
                "timeout_seconds", "5"), Map.of()), null));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertFalse(e.getMessage().contains(TOKEN), e.getMessage());
        assertTrue(e.getMessage().contains("***"), e.getMessage());
        assertEquals(1, deletes.get());
    }

    @Test
    void redirectIsNotFollowed() throws Exception {
        start();
        mode = "redirect";
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(Map.of(
                "server_url", base, "timeout_seconds", "5"), Map.of()), null));
        assertEquals("HTTP_302", e.errorCode());
        assertEquals(1, posts.get());
    }

    @Test
    void catalogRegistersTheHandler() {
        var catalog = com.studioquack.nodelibrary.core.NodeCatalog.load();
        assertEquals(McpServerNode.DEFAULT_ID, catalog.templateId("mcp-server"));
        var registry = new com.studioquack.nodelibrary.core.NodeRegistry();
        ProtocolHandlers.register(registry, catalog);
        assertNotNull(registry.get(McpServerNode.DEFAULT_ID));
    }

    private void assertInvalid(String snippet, Map<String, Object> cfg, Map<String, Object> input) {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(cfg, input), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains(snippet), e.getMessage());
    }

    private void start() throws IOException {
        deletes.set(0);
        posts.set(0);
        mode = "json";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    private void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if ("DELETE".equals(method)) {
            deletes.incrementAndGet();
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        posts.incrementAndGet();
        Map<String, Object> req = Json.readObject(body);
        String rpc = String.valueOf(req.get("method"));
        if ("initialize".equals(rpc)) {
            if ("redirect".equals(mode)) {
                ex.getResponseHeaders().set("Location", "http://127.0.0.1:1/nope");
                ex.sendResponseHeaders(302, -1);
                ex.close();
                return;
            }
            if ("unauthorized".equals(mode)) {
                json(ex, 401, "{\"message\":\"bad " + TOKEN + "\"}", Map.of());
                return;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("protocolVersion", "2025-03-26");
            result.put("capabilities", Map.of());
            result.put("serverInfo", Map.of("name", "test", "version", "1"));
            json(ex, 200, envelope(req.get("id"), result, null),
                    Map.of("Mcp-Session-Id", "sess-abc"));
            return;
        }
        if ("notifications/initialized".equals(rpc)) {
            ex.sendResponseHeaders(202, -1);
            ex.close();
            return;
        }
        opHeaders = lowerHeaders(ex);
        opBody = body;
        if ("sse".equals(mode)) {
            String payload = envelope(req.get("id"), Map.of("text", "hi"), null);
            byte[] bytes = ("event: message\ndata: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
            return;
        }
        if ("rpc-error".equals(mode)) {
            json(ex, 200, envelope(req.get("id"), null,
                    Map.of("code", -32000, "message", "nope " + TOKEN)), Map.of());
            return;
        }
        if ("ping".equals(rpc)) {
            json(ex, 200, envelope(req.get("id"), Map.of(), null), Map.of());
            return;
        }
        json(ex, 200, envelope(req.get("id"),
                Map.of("tools", List.of(Map.of("name", "echo"))), null), Map.of());
    }

    private static Map<String, String> lowerHeaders(HttpExchange ex) {
        Map<String, String> out = new LinkedHashMap<>();
        ex.getRequestHeaders().forEach((k, v) -> {
            if (k != null && v != null && !v.isEmpty()) {
                out.put(k.toLowerCase(), v.get(0));
            }
        });
        return out;
    }

    private static String envelope(Object id, Object result, Object error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        if (error != null) {
            body.put("error", error);
        } else {
            body.put("result", result);
        }
        return Json.write(body);
    }

    private static void json(HttpExchange ex, int status, String body, Map<String, String> headers)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        headers.forEach((k, v) -> ex.getResponseHeaders().set(k, v));
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
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
}
