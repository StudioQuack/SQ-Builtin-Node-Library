package com.studioquack.nodelibrary.nodes.web;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation, non-2xx, and credential-redaction paths. */
class HttpRequestNodeTest {

    private final HttpRequestNode node = new HttpRequestNode();

    @Test
    void emptyUrlIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("url", "", "method", "GET")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("URL is missing", e.getMessage());
    }

    @Test
    void disallowedMethodIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of(
                        "url", "https://example.com/post", "method", "TRACE")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("HTTP Request Error: method not allowed: TRACE", e.getMessage());
    }

    @Test
    void badSchemeIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of(
                        "url", "ftp://files.example.com/a", "method", "GET")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("HTTP Request Error: unsupported URL scheme: ftp", e.getMessage());
    }

    @Test
    void non2xxThrowsHttpStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> respond(ex, 503, "busy"));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            StudioQuackError e = assertThrows(StudioQuackError.class,
                    () -> node.handle(request(Map.of("url", url, "method", "GET")), null));
            assertEquals("HTTP_503", e.errorCode());
            assertTrue(e.getMessage().contains("HTTP 503"), e.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unauthorizedThrowsAuthFailedAndRedactsCredential() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> respond(ex, 401, "nope"));
        server.start();
        try {
            String secret = "supersecret-token";
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/?token=" + secret;
            StudioQuackError e = assertThrows(StudioQuackError.class,
                    () -> node.handle(request(Map.of(
                            "url", url,
                            "method", "GET",
                            "headers", Map.of("Authorization", secret))), null));
            assertEquals("AUTH_FAILED", e.errorCode());
            assertTrue(e.getMessage().contains("HTTP 401"), e.getMessage());
            assertFalse(e.getMessage().contains(secret), e.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rateLimitThrowsRateLimited() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> respond(ex, 429, "slow"));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            StudioQuackError e = assertThrows(StudioQuackError.class,
                    () -> node.handle(request(Map.of("url", url, "method", "GET")), null));
            assertEquals("RATE_LIMITED", e.errorCode());
            assertTrue(e.getMessage().contains("HTTP 429"), e.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transportFailureThrowsRequestFailed() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of(
                        "url", "http://127.0.0.1:1/nope", "method", "GET")), null));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertTrue(e.getMessage().startsWith("HTTP Request Error:"), e.getMessage());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, Object> request(Map<String, Object> input) {
        return Map.of("inputVariables", Map.of("input", input));
    }
}
