package com.studioquack.nodelibrary.nodes.officialapi;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studioquack.StudioQuackError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NewsAPI error mapping against a loopback server. Official rate-limit status is
 * HTTP 429 (https://newsapi.org/docs/errors); 401 stays a dedicated invalid-key path.
 */
class NewsApiNodeTest {

    private final NewsapiNode node = new NewsapiNode();
    private HttpServer server;
    private String base;
    private volatile int respondStatus = 200;
    private volatile String respondBody = "{\"status\":\"ok\",\"totalResults\":0,\"articles\":[]}";

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
        assertEquals("At least one of query, q_in_title, sources, or domains is required.",
                e.getMessage());
    }

    @Test
    void missingKeyFailsBeforeHttp() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of(), Map.of("query", "ai")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("Missing NewsAPI API key. Provide it in node parameters (api_key).",
                e.getMessage());
    }

    @Test
    void http429MapsToRateLimited() {
        respondStatus = 429;
        respondBody = "{\"status\":\"error\",\"code\":\"rateLimited\"}";
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(searchRequest(), null));
        assertEquals("RATE_LIMITED", e.errorCode());
        assertTrue(e.getMessage().contains("HTTP 429"), e.getMessage());
        assertTrue(e.getMessage().contains("daily limit"), e.getMessage());
    }

    @Test
    void http401MapsToAuthFailed() {
        respondStatus = 401;
        respondBody = "{\"status\":\"error\",\"code\":\"apiKeyInvalid\"}";
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(searchRequest(), null));
        assertEquals("AUTH_FAILED", e.errorCode());
        assertTrue(e.getMessage().contains("HTTP 401"), e.getMessage());
        assertTrue(e.getMessage().contains("Invalid NewsAPI API key"), e.getMessage());
    }

    @Test
    void http426IsGenericHttpErrorNotRateLimit() {
        respondStatus = 426;
        respondBody = "{\"status\":\"error\"}";
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(searchRequest(), null));
        assertEquals("HTTP_426", e.errorCode());
        assertTrue(e.getMessage().startsWith("NewsAPI HTTP Error: HTTP 426"), e.getMessage());
    }

    private Map<String, Object> searchRequest() {
        return request(Map.of("api_key", "test-key", "base_url", base), Map.of("query", "ai"));
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
