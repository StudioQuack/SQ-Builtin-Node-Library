package com.studioquack.nodelibrary.nodes.web;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchNodeTest {

    private final WebSearchNode node = new WebSearchNode();
    private HttpServer server;
    private String base;
    private volatile int respondStatus = 200;
    private volatile String respondBody = "{\"results\":[]}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = respondBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(respondStatus, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void missingQueryIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("api_key", "k"), Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("Query is missing", e.getMessage());
    }

    @Test
    void missingApiKeyIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of(), Map.of("query", "bpmn")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Missing Tavily API Key"), e.getMessage());
    }

    @Test
    void badSchemeIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(
                        Map.of("api_key", "k", "baseUrl", "ftp://search.example"),
                        Map.of("query", "bpmn")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("baseUrl must be http or https", e.getMessage());
    }

    @Test
    void http429IsRateLimited() {
        respondStatus = 429;
        respondBody = "{\"error\":\"slow\"}";
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(searchRequest(), null));
        assertEquals("RATE_LIMITED", e.errorCode());
        assertTrue(e.getMessage().contains("HTTP 429"), e.getMessage());
        assertTrue(e.getMessage().contains("Wait"), e.getMessage());
    }

    @Test
    void http503IsTransientHttp() {
        respondStatus = 503;
        respondBody = "{\"error\":\"down\"}";
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(searchRequest(), null));
        assertEquals("HTTP_503", e.errorCode());
        assertTrue(e.getMessage().contains("HTTP 503"), e.getMessage());
        assertTrue(e.getMessage().contains("transient"), e.getMessage());
    }

    @Test
    void http400IsRequestFailedWithHttpStatus() {
        respondStatus = 400;
        respondBody = "{\"error\":\"bad query\"}";
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(searchRequest(), null));
        assertEquals("HTTP_400", e.errorCode());
        assertTrue(e.getMessage().contains("HTTP 400"), e.getMessage());
    }

    @Test
    void successReturnsFormattedResults() {
        respondStatus = 200;
        respondBody = "{\"results\":[{\"title\":\"Example\",\"url\":\"https://example.com\",\"content\":\"snippet\"}]}";
        Map<String, Object> ports = node.handle(searchRequest(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) ports.get("output");
        assertTrue(String.valueOf(out.get("result")).contains("Example"), String.valueOf(out));
        assertFalse(String.valueOf(out).contains("secret-key"));
    }

    private Map<String, Object> searchRequest() {
        return request(Map.of("api_key", "secret-key", "baseUrl", base), Map.of("query", "bpmn"));
    }

    private static Map<String, Object> request(Map<String, Object> cfg, Map<String, Object> input) {
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : cfg.entrySet()) {
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
}
