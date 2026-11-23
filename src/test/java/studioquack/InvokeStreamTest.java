package studioquack;

import com.sun.net.httpserver.HttpServer;
import com.studioquack.nodelibrary.core.NodeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /invoke/stream} wire protocol: unary nodes over the stream path,
 * stream-aware interleaving, malformed NDJSON, drop-oldest, and unary {@code /invoke}
 * unchanged.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class InvokeStreamTest {

    private HttpServer server;
    private HttpClient http;
    private int port;

    private final AtomicLong capDropped = new AtomicLong();
    private final AtomicLong capSeen = new AtomicLong();

    @BeforeEach
    void start() throws Exception {
        NodeHandler unary = new NodeHandler() {
            @Override
            public String templateId() {
                return "tpl_unary";
            }

            @Override
            public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("seen", request == null ? Map.of() : request.get("inputVariables"));
                out.put("templateId", request == null ? "" : request.get("templateId"));
                return out;
            }
        };
        NodeHandler streaming = new NodeHandler() {
            @Override
            public String templateId() {
                return "tpl_stream";
            }

            @Override
            public boolean supportsStreamInput() {
                return true;
            }

            @Override
            public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
                return Map.of("error", "unary handle must not run");
            }

            @Override
            public void handleStream(StreamInvocation in, StreamEmitter out, StudioQuackHelper helper) {
                for (Frame f : in.frames()) {
                    out.emitPartial(f.channel(), "echo:" + f.delta());
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                out.complete(result);
            }
        };
        NodeHandler cap = new NodeHandler() {
            @Override
            public String templateId() {
                return "tpl_cap";
            }

            @Override
            public boolean supportsStreamInput() {
                return true;
            }

            @Override
            public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
                return Map.of();
            }

            @Override
            public void handleStream(StreamInvocation in, StreamEmitter out, StudioQuackHelper helper) {
                // Do not poll until drop-oldest has fired so the 1024-cap is observable.
                long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (in.droppedFrameCount() == 0 && !in.isFailed()
                        && System.nanoTime() < waitUntil) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        out.error("interrupted");
                        return;
                    }
                }
                long seen = 0;
                while (true) {
                    Optional<Frame> f = in.pollFrame(Duration.ofMillis(50));
                    if (f.isPresent()) {
                        seen++;
                    } else if (in.isInputComplete() || in.isFailed()) {
                        break;
                    }
                }
                capSeen.set(seen);
                capDropped.set(in.droppedFrameCount());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("alive", true);
                result.put("seen", seen);
                result.put("dropped", in.droppedFrameCount());
                out.complete(result);
            }
        };

        StudioQuackDispatch.Dispatcher d = StudioQuackDispatch.dispatcher("tpl_self")
                .localHandler((req, helper) -> {
                    String mode = req == null ? "" : String.valueOf(
                            Json.asMap(req.get("inputVariables")).get("mode"));
                    if ("throw".equals(mode)) {
                        throw new StudioQuackError("Error_BadInput", "nope");
                    }
                    if ("blank-error".equals(mode)) {
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("output", Map.of("test_passed", true));
                        out.put("error", "");
                        return out;
                    }
                    if ("real-error".equals(mode)) {
                        return Map.of("error", "input.script is required");
                    }
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("ok", true);
                    out.put("templateId", req == null ? "" : req.get("templateId"));
                    return out;
                })
                .localTemplateIds(List.of("tpl_library_node"))
                .streamLookup(tid -> {
                    if ("tpl_unary".equals(tid)) {
                        return unary::handleStream;
                    }
                    if ("tpl_stream".equals(tid)) {
                        return streaming::handleStream;
                    }
                    if ("tpl_cap".equals(tid)) {
                        return cap::handleStream;
                    }
                    return null;
                });
        server = d.startLocalForTest();
        port = server.getAddress().getPort();
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void unaryNodeAssemblesFramesOnStreamEndpoint() throws Exception {
        String body = String.join("\n",
                open("r-unary", "tpl_unary", Map.of("text", "Hi")),
                frame("r-unary", "text", "answer", " there", 1),
                frame("r-unary", "text", "answer", "!", 2),
                close("r-unary"),
                "");
        HttpResponse<String> resp = postStream(body);
        assertEquals(200, resp.statusCode());
        assertTrue(contentType(resp).startsWith("text/event-stream"));
        List<Sse> events = parseSse(resp.body());
        assertFalse(events.isEmpty());
        assertEquals("done", events.get(events.size() - 1).event);
        Sse complete = find(events, "complete");
        assertNotNull(complete);
        Map<String, Object> data = Json.asMap(Json.parse(complete.data));
        assertEquals("tpl_unary", Json.str(data.get("templateId")));
        Map<String, Object> seen = Json.asMap(data.get("seen"));
        assertEquals("Hi there!", Json.str(seen.get("text")));
        assertTrue(events.stream().noneMatch(e -> "error".equals(e.event)));
    }

    @Test
    void streamAwarePartialsPreserveOrder() throws Exception {
        String body = String.join("\n",
                open("r-stream", "tpl_stream", Map.of()),
                frame("r-stream", "in", "thinking", "a", 1),
                frame("r-stream", "in", "answer", "b", 2),
                close("r-stream"),
                "");
        HttpResponse<String> resp = postStream(body);
        assertEquals(200, resp.statusCode());
        List<Sse> events = parseSse(resp.body());
        List<String> names = events.stream().map(e -> e.event).toList();
        assertEquals(List.of("partial", "partial", "complete", "done"), names);
        Map<String, Object> p0 = Json.asMap(Json.parse(events.get(0).data));
        Map<String, Object> p1 = Json.asMap(Json.parse(events.get(1).data));
        assertEquals("thinking", Json.str(p0.get("channel")));
        assertEquals("echo:a", Json.str(p0.get("delta")));
        assertEquals("answer", Json.str(p1.get("channel")));
        assertEquals("echo:b", Json.str(p1.get("delta")));
        Map<String, Object> complete = Json.asMap(Json.parse(events.get(2).data));
        assertEquals(true, complete.get("ok"));
    }

    @Test
    void malformedNdjsonYieldsErrorEventWithoutStack() throws Exception {
        String body = String.join("\n",
                open("r-bad", "tpl_unary", Map.of()),
                "this is not json",
                close("r-bad"),
                "");
        HttpResponse<String> resp = postStream(body);
        assertEquals(200, resp.statusCode());
        List<Sse> events = parseSse(resp.body());
        assertEquals("done", events.get(events.size() - 1).event);
        Sse error = find(events, "error");
        assertNotNull(error);
        Map<String, Object> data = Json.asMap(Json.parse(error.data));
        String msg = Json.str(data.get("error"));
        assertFalse(msg.isBlank());
        assertFalse(msg.contains("at studioquack"), msg);
        assertFalse(msg.contains("Exception:"), "must not leak a stack-shaped payload: " + msg);
        assertTrue(msg.toLowerCase().contains("malformed") || msg.toLowerCase().contains("ndjson"), msg);
    }

    @Test
    void frameCapDropOldestKeepsSessionAlive() throws Exception {
        int extra = 200;
        int total = StreamInvocation.MAX_QUEUED_FRAMES + extra;
        StringBuilder ndjson = new StringBuilder();
        ndjson.append(open("r-cap", "tpl_cap", Map.of())).append('\n');
        for (int i = 0; i < total; i++) {
            ndjson.append(frame("r-cap", "in", "answer", "x" + i, i)).append('\n');
        }
        ndjson.append(close("r-cap")).append('\n');

        HttpResponse<String> resp = http.send(streamRequest(ndjson.toString()),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        List<Sse> events = parseSse(resp.body());
        assertEquals("done", events.get(events.size() - 1).event);
        Sse complete = find(events, "complete");
        assertNotNull(complete, "session must complete, not hang; body=" + resp.body());
        Map<String, Object> data = Json.asMap(Json.parse(complete.data));
        assertEquals(true, data.get("alive"));
        long dropped = ((Number) data.get("dropped")).longValue();
        long seen = ((Number) data.get("seen")).longValue();
        assertTrue(dropped > 0, "drop-oldest should fire; seen=" + seen + " dropped=" + dropped);
        assertTrue(seen <= StreamInvocation.MAX_QUEUED_FRAMES, "seen=" + seen);
        assertTrue(events.stream().noneMatch(e -> "error".equals(e.event)), resp.body());
        assertEquals(dropped, capDropped.get());
        assertEquals(seen, capSeen.get());
    }

    @Test
    void unaryInvokeUnchanged() throws Exception {
        String json = Json.write(Map.of(
                "requestId", "r-unary-invoke",
                "templateId", "tpl_self",
                "executionContext", Map.of(),
                "inputVariables", Map.of("n", 1)));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(contentType(resp).contains("application/json"));
        Map<String, Object> body = Json.readObject(resp.body());
        assertEquals(true, body.get("ok"));
        assertEquals("tpl_self", Json.str(body.get("templateId")));

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> up = http.send(get, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, up.statusCode());
        assertEquals("up", Json.str(Json.readObject(up.body()).get("status")));
    }

    /**
     * A catalog node other than the self id is served by the local handler in-process,
     * without opening a per-templateId loopback listener.
     */
    @Test
    void localTemplateIdIsServedInProcess() throws Exception {
        String json = Json.write(Map.of(
                "requestId", "r-library-node",
                "templateId", "tpl_library_node",
                "executionContext", Map.of(),
                "inputVariables", Map.of()));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), resp.body());
        Map<String, Object> body = Json.readObject(resp.body());
        assertEquals(true, body.get("ok"));
        assertEquals("tpl_library_node", Json.str(body.get("templateId")));
    }

    @Test
    void declaredLocalTemplateIdRunsInProcessWithoutAClientListener() throws Exception {
        String json = Json.write(Map.of(
                "requestId", "r-library-node",
                "templateId", "tpl_library_node",
                "executionContext", Map.of(),
                "inputVariables", Map.of()));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        Map<String, Object> body = Json.readObject(resp.body());
        assertEquals(true, body.get("ok"), "must reach the local handler, not relay/compile: " + resp.body());
        assertEquals("tpl_library_node", Json.str(body.get("templateId")));
    }

    @Test
    void blankErrorPortIsReturnedWithTheReport() throws Exception {
        String json = Json.write(Map.of(
                "requestId", "r-blank",
                "templateId", "tpl_self",
                "executionContext", Map.of(),
                "inputVariables", Map.of("mode", "blank-error")));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        Map<String, Object> body = Json.readObject(resp.body());
        assertEquals(true, Json.asMap(body.get("output")).get("test_passed"), resp.body());
        assertEquals("", body.get("error"));
    }

    @Test
    void nonBlankErrorPortStillFailsTheInvoke() throws Exception {
        String json = Json.write(Map.of(
                "requestId", "r-real",
                "templateId", "tpl_self",
                "executionContext", Map.of(),
                "inputVariables", Map.of("mode", "real-error")));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(Json.readObject(resp.body()).isEmpty(), resp.body());
    }

    @Test
    void unaryInvokeStudioQuackErrorFoldsToEmptyObject() throws Exception {
        String json = Json.write(Map.of(
                "requestId", "r-fail",
                "templateId", "tpl_self",
                "executionContext", Map.of(),
                "inputVariables", Map.of("mode", "throw")));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        Map<String, Object> body = Json.readObject(resp.body());
        assertTrue(body.isEmpty(), resp.body());
        assertFalse(body.containsKey("error"));
    }

    @Test
    void dispatcherProtocolFailuresAnswerTheReservedFailEnvelopeNeverABareError() throws Exception {
        // Missing templateId: the request never reaches a handler and carries no trustworthy helper
        // token, so the daemon learns of the failure from the __sq_fail envelope — not from an
        // "error" key it would otherwise bind to an output port.
        HttpRequest missing = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(Map.of("requestId", "r-x"))))
                .build();
        HttpResponse<String> resp = http.send(missing, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        Map<String, Object> body = Json.readObject(resp.body());
        assertFalse(body.containsKey("error"), resp.body());
        Map<String, Object> fail = Json.asMap(body.get(StudioQuackDispatch.DISPATCH_FAIL_KEY));
        assertEquals(StudioQuackDispatch.DISPATCH_BAD_REQUEST, fail.get("errorCode"));
        assertEquals("missing templateId", fail.get("message"));

        // Non-JSON body → same envelope.
        HttpRequest garbage = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{not json"))
                .build();
        Map<String, Object> garbageBody = Json.readObject(
                http.send(garbage, HttpResponse.BodyHandlers.ofString()).body());
        assertEquals(StudioQuackDispatch.DISPATCH_BAD_REQUEST,
                Json.asMap(garbageBody.get(StudioQuackDispatch.DISPATCH_FAIL_KEY)).get("errorCode"));

        // Unknown templateId with nothing to relay to or compile → DISPATCH_* too, never a port map.
        HttpRequest unknown = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(Map.of(
                        "requestId", "r-y", "templateId", "tpl_nobody_home", "executionContext", Map.of()))))
                .build();
        Map<String, Object> unknownBody = Json.readObject(
                http.send(unknown, HttpResponse.BodyHandlers.ofString()).body());
        assertFalse(unknownBody.containsKey("error"));
        String code = Json.str(Json.asMap(unknownBody.get(StudioQuackDispatch.DISPATCH_FAIL_KEY)).get("errorCode"));
        assertTrue(code.startsWith("DISPATCH_"), code);
    }

    @Test
    void cancelUnknownRequestIdReturnsOkFalse() throws Exception {
        String json = Json.write(Map.of("requestId", "no-such", "reason", "test"));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke/cancel"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertEquals(false, Json.readObject(resp.body()).get("ok"));
    }

    private HttpResponse<String> postStream(String ndjson) throws Exception {
        return http.send(streamRequest(ndjson), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest streamRequest(String ndjson) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/invoke/stream"))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(ndjson, StandardCharsets.UTF_8))
                .build();
    }

    private static String open(String requestId, String templateId, Map<String, Object> input) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "open");
        o.put("requestId", requestId);
        o.put("templateId", templateId);
        o.put("input", input);
        o.put("context", Map.of());
        return Json.write(o);
    }

    private static String frame(String requestId, String port, String channel, String delta, int seq) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "frame");
        o.put("requestId", requestId);
        o.put("port", port);
        o.put("channel", channel);
        o.put("delta", delta);
        o.put("seq", seq);
        return Json.write(o);
    }

    private static String close(String requestId) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "close");
        o.put("requestId", requestId);
        return Json.write(o);
    }

    private static String contentType(HttpResponse<?> resp) {
        return resp.headers().firstValue("Content-Type").orElse("");
    }

    private static Sse find(List<Sse> events, String name) {
        for (Sse e : events) {
            if (name.equals(e.event)) {
                return e;
            }
        }
        return null;
    }

    private static List<Sse> parseSse(String body) {
        List<Sse> out = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return out;
        }
        String event = "";
        StringBuilder data = new StringBuilder();
        // split keeps a trailing empty after the final \n\n
        String[] lines = body.split("\n", -1);
        for (String line : lines) {
            if (line.startsWith("event:")) {
                event = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                String payload = line.substring("data:".length());
                if (!payload.isEmpty() && payload.charAt(0) == ' ') {
                    payload = payload.substring(1);
                }
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(payload);
            } else if (line.isEmpty()) {
                if (!event.isEmpty() || data.length() > 0) {
                    out.add(new Sse(event, data.toString()));
                }
                event = "";
                data.setLength(0);
            }
        }
        return out;
    }

    private record Sse(String event, String data) {
    }
}
