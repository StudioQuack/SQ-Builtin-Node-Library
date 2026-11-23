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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebBrowserNodeTest {

    private final WebBrowserNode node = new WebBrowserNode();

    @Test
    void missingUrlIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("url is required.", e.getMessage());
    }

    @Test
    void emptyUrlIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("url", "")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("url is required.", e.getMessage());
    }

    @Test
    void nonStringUrlIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("url", 123)), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("url must be a string.", e.getMessage());
    }

    @Test
    void ftpUrlIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("url", "ftp://nothttp.com")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("url must be http or https.", e.getMessage());
    }

    @Test
    void invalidUrlFormatIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("url", "not a url")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("url must be http or https.", e.getMessage());
    }

    @Test
    void non2xxPageThrowsHttpStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robots.txt", ex -> respond(ex, 200, "User-agent: *\nAllow: /\n"));
        server.createContext("/gone", ex -> respond(ex, 404, "missing"));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/gone";
            StudioQuackError e = assertThrows(StudioQuackError.class,
                    () -> node.handle(request(Map.of("url", url)), null));
            assertEquals("HTTP_404", e.errorCode());
            assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transportFailureThrowsRequestFailed() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("url", "http://127.0.0.1:1/nope")), null));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertTrue(e.getMessage().startsWith("Web browser HTTP error:"), e.getMessage());
    }

    @Test
    void robotsDenialIsSuccessfulBlockedResult() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robots.txt", ex -> respond(ex, 200, "User-agent: *\nDisallow: /\n"));
        server.createContext("/page", ex -> respond(ex, 200, "<html><title>nope</title></html>"));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
            Map<String, Object> payload = output(node.handle(request(Map.of("url", url)), null));
            assertEquals("blocked_by_robots", payload.get("status"));
            assertEquals("Fetch disallowed by robots.txt", payload.get("error"));
            assertEquals(url, payload.get("url"));
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> handleResult) {
        return (Map<String, Object>) handleResult.get("output");
    }

    private static Map<String, Object> request(Map<String, Object> input) {
        return Map.of("inputVariables", Map.of("input", input));
    }
}
