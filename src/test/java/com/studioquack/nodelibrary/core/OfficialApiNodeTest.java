package com.studioquack.nodelibrary.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import studioquack.StudioQuackError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end {@link OfficialApiNode} behavior against a loopback JDK HttpServer:
 * operation resolution, URL building, auth, bodies, error and ingress paths.
 */
class OfficialApiNodeTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAccept = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicReference<String> lastXHeader = new AtomicReference<>();
    private final AtomicReference<String> lastMsDate = new AtomicReference<>();
    private final AtomicReference<String> lastAmzDate = new AtomicReference<>();
    private final AtomicReference<String> lastAmzSha = new AtomicReference<>();
    private final AtomicReference<String> lastAmzToken = new AtomicReference<>();
    private volatile int respondStatus = 200;
    private volatile String respondBody = "{\"ok\":true}";
    private volatile String respondContentType = "application/json";

    @BeforeEach
    void startServer() throws IOException {
        respondStatus = 200;
        respondBody = "{\"ok\":true}";
        respondContentType = "application/json";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().toString());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            lastXHeader.set(exchange.getRequestHeaders().getFirst("X-Api-Version"));
            lastMsDate.set(exchange.getRequestHeaders().getFirst("x-ms-date"));
            lastAmzDate.set(exchange.getRequestHeaders().getFirst("x-amz-date"));
            lastAmzSha.set(exchange.getRequestHeaders().getFirst("x-amz-content-sha256"));
            lastAmzToken.set(exchange.getRequestHeaders().getFirst("x-amz-security-token"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String pathOnly = exchange.getRequestURI().getPath();
            String bodyOut = respondBody;
            int statusOut = respondStatus;
            if ("/users/me".equals(pathOnly)) {
                bodyOut = "{\"resource\":{\"uri\":\"https://api.calendly.com/users/me-prefill\"}}";
                statusOut = 200;
            }
            byte[] bytes = bodyOut.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", respondContentType);
            exchange.sendResponseHeaders(statusOut, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ---- helpers -----------------------------------------------------------

    private static Map<String, Object> specMap(String baseUrl, Map<String, Object> auth,
                                               Map<String, Object> ops) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseUrl", baseUrl);
        if (auth != null) {
            m.put("auth", auth);
        }
        m.put("operations", ops);
        return m;
    }

    private OfficialApiNode node(Map<String, Object> specMap) {
        return new OfficialApiNode("tid-test", OfficialApiSpec.fromMap("test", specMap));
    }

    /** Invoke-request envelope matching executionContext.currentNode.data.configurationParameters. */
    private static Map<String, Object> request(Map<String, Object> cfg, Map<String, Object> input) {
        List<Map<String, Object>> params = new ArrayList<>();
        if (cfg != null) {
            for (var e : cfg.entrySet()) {
                params.add(Map.of("shortCode", e.getKey(), "value", e.getValue()));
            }
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("executionContext", Map.of("currentNode", Map.of("data",
                Map.of("configurationParameters", params))));
        if (input != null) {
            req.put("inputVariables", Map.of("input", input));
        }
        return req;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> result) {
        return (Map<String, Object>) result.get("output");
    }

    private static StudioQuackError thrown(java.util.function.Supplier<Map<String, Object>> call) {
        return assertThrows(StudioQuackError.class, call::get);
    }

    // ---- tests -------------------------------------------------------------

    @Test
    void bodyFieldsFallBackToTypedConfigAndInputWins() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("t", Map.of("baseUrl", base,
                "operations", Map.of("c", Map.of("method", "POST",
                        "bodyFrom", List.of("limit", "tags", "name", "flag"))))).operation("c");
        Map<String, Object> cfg = new LinkedHashMap<>(Map.of("limit", "5", "tags", "[\"a\"]", "name", "cfg",
                "flag", "true", "api_key", "k", "timeout_seconds", "9"));
        Map<String, Object> body = OfficialApiNode.underlayBodyConfig(op, Map.of("name", "in", "limit", ""), cfg,
                Map.of("limit", "number", "tags", "array", "flag", "boolean", "name", "string"));
        assertEquals(5L, body.get("limit"));
        assertEquals(List.of("a"), body.get("tags"));
        assertEquals("in", body.get("name"));
        assertEquals(Boolean.TRUE, body.get("flag"));
        assertFalse(body.containsKey("api_key"), "non-body config must stay off the wire");
        assertFalse(body.containsKey("timeout_seconds"));
    }

    @Test
    void emptyBodyFromNeverUnderlaysConfig() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("t", Map.of("baseUrl", base,
                "operations", Map.of("c", Map.of("method", "POST")))).operation("c");
        Map<String, Object> in = Map.of("q", "x");
        assertEquals(in, OfficialApiNode.underlayBodyConfig(op, in, Map.of("api_key", "k"), Map.of()));
    }

    @Test
    void bodyDefaultsFillOnlyMissingOrBlankQuery() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("t", Map.of("baseUrl", base,
                "operations", Map.of("gql", Map.of("method", "POST", "path", "/",
                        "bodyFrom", List.of("query", "organizationId"),
                        "bodyDefaults", Map.of("query", "default-doc"))))).operation("gql");
        assertEquals("default-doc",
                OfficialApiNode.mergeBodyDefaults(op, Map.of("organizationId", "x")).get("query"));
        assertEquals("custom",
                OfficialApiNode.mergeBodyDefaults(op, Map.of("query", "custom")).get("query"));
    }

    @Test
    void calendlyPrefillsUserQueryFromUsersMe() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("calendly", specMap(base,
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ",
                        "configKey", "access_token"),
                Map.of(
                        "list-event-types", Map.of("method", "GET", "path", "/event_types",
                                "queryFrom", List.of("user", "organization", "count", "page_token")),
                        "list-events", Map.of("method", "GET", "path", "/scheduled_events",
                                "queryFrom", List.of("user", "organization", "status",
                                        "min_start_time", "max_start_time", "count")))));
        OfficialApiNode n = new OfficialApiNode("tid-cal", spec);
        Map<String, Object> out = output(n.handle(request(
                Map.of("operation", "list-event-types", "access_token", "pat"), Map.of()), null));
        assertEquals("ok", out.get("status"));
        String etPath = lastPath.get();
        assertTrue(etPath.contains("/event_types?"), etPath);
        assertTrue(etPath.contains("user="), etPath);
        assertTrue(etPath.contains("me-prefill"), etPath);
        assertEquals("https://api.calendly.com/users/me-prefill", n.lastResolvedCalendlyUserUri);

        lastPath.set(null);
        assertEquals("ok", output(n.handle(request(
                Map.of("operation", "list-events", "access_token", "pat"), Map.of()), null)).get("status"));
        String evPath = lastPath.get();
        assertTrue(evPath.contains("/scheduled_events?"), evPath);
        assertTrue(evPath.contains("user="), evPath);
    }

    @Test
    void calendlySkipsUsersMeWhenOrganizationQueryParamPresent() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("calendly", specMap(base,
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ",
                        "configKey", "access_token"),
                Map.of("list-event-types", Map.of("method", "GET", "path", "/event_types",
                        "queryFrom", List.of("user", "organization", "count", "page_token")))));
        OfficialApiNode n = new OfficialApiNode("tid-cal2", spec);
        String org = "https://api.calendly.com/organizations/ORG1";
        Map<String, Object> out = output(n.handle(request(
                Map.of("operation", "list-event-types", "access_token", "pat"),
                Map.of("organization", org)), null));
        assertEquals("ok", out.get("status"));
        assertTrue(lastPath.get().contains("organization="), lastPath.get());
        assertNull(n.lastResolvedCalendlyUserUri);
    }

    @Test
    void placeholderDefaultFillsBaseUrlSandboxSegment() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("x", Map.of(
                "baseUrl", "https://{sandbox}.test",
                "placeholderDefaults", Map.of("sandbox", "api"),
                "operations", Map.of("p", Map.of("method", "GET", "path", "/v2/items"))));
        String url = new OfficialApiNode("tid", spec).buildUrl(spec.operation("p"), Map.of(), Map.of(), null);
        assertTrue(url.startsWith("https://api.test/v2/items"), url);
    }

    @Test
    void commonPathIdAliasesCopyJobIdAndLink() {
        Map<String, Object> merged = OfficialApiNode.mergeCommonPathIdAliases(
                Map.of("job_id", "j1", "link", "https://a.example"));
        assertEquals("j1", merged.get("id"));
        assertEquals("https://a.example", merged.get("url"));
        assertEquals("j1", merged.get("job_id"));
    }

    @Test
    void cloudConvertMapsPlainFileStringToIdAndNameToUrl() {
        Map<String, Object> jobGet = OfficialApiNode.mergeCloudConvertAliases("cloudconvert",
                Map.of("operation", "job_get", "file", "non-existent-job-id-123"));
        assertEquals("non-existent-job-id-123", jobGet.get("id"));
        Map<String, Object> capture = OfficialApiNode.mergeCloudConvertAliases("cloudconvert",
                Map.of("operation", "capture_website", "name", "https://example.com"));
        assertEquals("https://example.com", capture.get("url"));
    }

    @Test
    void inputActionIsARequestFieldUnlessItNamesAnOperation() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "send-chat-action", Map.of("path", "/a", "queryFrom", List.of("action")),
                "other", Map.of("path", "/o"))));
        n.handle(request(Map.of("operation", "send-chat-action"), Map.of("action", "typing")), null);
        assertEquals("/a?action=typing", lastPath.get());
        n.handle(request(null, Map.of("action", "other")), null);
        assertEquals("/o", lastPath.get());
    }

    @Test
    void missingSpecYieldsErrorPort() {
        OfficialApiNode n = new OfficialApiNode("tid-x", null);
        StudioQuackError e = thrown(() -> n.handle(request(null, null), null));
        assertEquals("NODE_ERROR", e.errorCode());
        assertEquals("api_spec.json missing for this node", e.getMessage());
    }

    @Test
    void singleOperationIsAutoSelected() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "ping", Map.of("method", "GET", "path", "/ping"))));
        Map<String, Object> out = output(n.handle(request(null, Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals("/ping", lastPath.get());
    }

    @Test
    void operationRequiredWhenMultipleExist() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "a", Map.of("path", "/a"), "b", Map.of("path", "/b"))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("operation is required"), e.getMessage());
        assertTrue(e.getMessage().contains("a"), e.getMessage());
    }

    @Test
    void unknownOperationListsAccepted() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "a", Map.of("path", "/a"))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of("operation", "zzz")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Unknown operation"));
    }

    @Test
    void ingressOperationPointsToBpmnMessageEvents() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "listen", Map.of("kind", "ingress"))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of("operation", "listen")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("BPMN message"), e.getMessage());
        assertNull(lastPath.get(), "ingress must never open an HTTP call");
    }

    @Test
    void operationNamedListenIsIngressEvenWithoutKind() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "webhook_listen", Map.of("method", "POST", "path", "/hooks"))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of("operation", "webhook_listen")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("BPMN message"));
    }

    @Test
    void missingRequiredParamFailsBeforeHttp() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "get-thing", Map.of("path", "/things/{id}", "required", List.of("id")))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of("operation", "get-thing")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("id is required"));
        assertNull(lastPath.get());
    }

    @Test
    void postHogSwitchesIngestAndAppHostByOperation() {
        OfficialApiSpec.Operation capture = OfficialApiSpec.fromMap("posthog", specMap(base, null,
                Map.of("event.capture", Map.of("method", "POST", "path", "/i/v0/e/")))).operation("event.capture");
        OfficialApiSpec.Operation person = OfficialApiSpec.fromMap("posthog", specMap(base, null,
                Map.of("person.get", Map.of("method", "GET", "path", "/api/projects/{project_id}/persons/"))))
                .operation("person.get");
        assertEquals("https://us.i.posthog.com",
                OfficialApiNode.resolvePostHogBaseUrl("posthog", capture, "https://us.posthog.com"));
        assertEquals("https://us.posthog.com",
                OfficialApiNode.resolvePostHogBaseUrl("posthog", person, "https://us.i.posthog.com"));
    }

    @Test
    void flattenNestedToolInputLiftsIdAndNameFromInputEnvelope() {
        Map<String, Object> flat = OfficialApiNode.flattenNestedToolInput(Map.of(
                "operation", "mailbox_get",
                "input", Map.of("id", 223103, "name", "test-mailbox")));
        assertEquals("mailbox_get", flat.get("operation"));
        assertEquals("223103", flat.get("id"));
        assertEquals("test-mailbox", flat.get("name"));

        Map<String, Object> aliased = OfficialApiNode.mergeCommonPathIdAliases(
                Map.of("operation", "document_get", "parser_id", "42"));
        assertEquals("42", aliased.get("id"));
    }

    @Test
    void parseurMailboxGetWithNestedIdReachesHttp() {
        respondStatus = 200;
        respondBody = "{\"id\":223103}";
        OfficialApiNode n = new OfficialApiNode("tid-parseur", OfficialApiSpec.fromMap("parseur", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "Token ", "configKey", "api_token"),
                Map.of("mailbox_get", Map.of("method", "GET", "path", "/parser/{id}",
                        "required", List.of("id"))))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("api_token", "tok"),
                Map.of("operation", "mailbox_get", "input", Map.of("id", 223103))), null));
        assertEquals("ok", out.get("status"));
        assertTrue(lastPath.get().contains("/parser/223103"), lastPath.get());
    }

    @Test
    void bracketFieldAliasMapsFilterNameToWireKey() {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("filter_name", "prod"));
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("test", specMap(base, null, Map.of(
                "x", Map.of("method", "GET", "path", "/x", "queryFrom", List.of("filter[name]")))))
                .operation("x");
        OfficialApiNode.applyBracketParameterAliases(body, op);
        assertEquals("prod", body.get("filter[name]"));
    }

    @Test
    void mistralEmbeddingsMapsEmbedInputAlias() {
        Map<String, Object> auth = Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ",
                "configKey", "api_key");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("mistral", specMap(base, auth, Map.of(
                "embeddings", Map.of("method", "POST", "path", "/v1/embeddings",
                        "bodyFrom", List.of("model", "input")))));
        OfficialApiNode n = new OfficialApiNode("tid-mistral-embed", spec);
        n.handle(request(Map.of("api_key", "k"), Map.of(
                "operation", "embeddings", "embed_input", "hello world")), null);
        assertEquals("{\"input\":\"hello world\"}", lastBody.get());
    }

    @Test
    void newRelicNrqlQueryRequiresNonemptyQuery() {
        Map<String, Object> auth = Map.of("type", "header", "header", "API-Key", "configKey", "user_api_key");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("new-relic", specMap(base, auth, Map.of(
                "nrql_query", Map.of("method", "POST", "path", "/graphql",
                        "required", List.of("query"),
                        "bodyFrom", List.of("query", "variables")))));
        OfficialApiNode n = new OfficialApiNode("tid-nr", spec);
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("user_api_key", "k"), Map.of("operation", "nrql_query", "query", "")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("query is required"), e.getMessage());
    }

    @Test
    void newRelicIncidentGetAcceptsIncidentIdAlias() {
        Map<String, Object> auth = Map.of("type", "header", "header", "API-Key", "configKey", "user_api_key");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("new-relic", specMap(base, auth, Map.of(
                "incident_get", Map.of("method", "GET", "path", "/v2/alerts_incidents/{incident_id}.json",
                        "required", List.of("incident_id")))));
        OfficialApiNode n = new OfficialApiNode("tid-nr-inc", spec);
        n.handle(request(Map.of("user_api_key", "k"), Map.of(
                "operation", "incident_get", "incidentId", "999")), null);
        assertTrue(lastPath.get().contains("/999.json"), lastPath.get());
    }

    @Test
    void newRelicEventIngestUnwrapsNestedInputAndEventsJson() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("new-relic", specMap(base, null,
                Map.of("event_ingest", Map.of("method", "POST", "path", "/x",
                        "bodyFrom", List.of("events"),
                        "bodyMap", Map.of("events", "$"))))).operation("event_ingest");
        Map<String, Object> event = Map.of("eventType", "AutomatedTestEvent", "testFlag", true);
        Map<String, Object> merged = OfficialApiNode.mergeNewRelicEventIngest("new-relic", op,
                Map.of("input", Map.of("operation", "event_ingest", "events", List.of(event))));
        assertTrue(merged.get("events") instanceof List<?>);
        assertEquals(1, ((List<?>) merged.get("events")).size());

        Map<String, Object> fromJson = OfficialApiNode.mergeNewRelicEventIngest("new-relic", op,
                Map.of("events_json", "[{\"eventType\":\"T\"}]"));
        assertTrue(fromJson.get("events") instanceof List<?>);
    }

    @Test
    void newRelicEuRegionRewritesBaseHost() {
        assertEquals("https://api.eu.newrelic.com",
                OfficialApiNode.applyNewRelicDataRegionHosts("new-relic", "https://api.newrelic.com",
                        Map.of("data_region", "eu")));
        assertEquals("https://api.newrelic.com",
                OfficialApiNode.applyNewRelicDataRegionHosts("new-relic", "https://api.newrelic.com",
                        Map.of("data_region", "us")));
    }

    @Test
    void postmarkListBouncesAppliesCountOffsetDefaultsAndQuery() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("postmark", specMap(base,
                Map.of("type", "header", "header", "X-Postmark-Server-Token", "prefix", "", "configKey", "server_token"),
                Map.of("list-bounces", Map.of("method", "GET", "path", "/bounces",
                        "required", List.of("count", "offset"),
                        "queryFrom", List.of("count", "offset"))))).operation("list-bounces");
        Map<String, Object> merged = OfficialApiNode.mergePostmarkInputs("postmark", op,
                Map.of("operation", "list-bounces", "Count", 10));
        assertEquals(10, merged.get("count"));
        assertEquals(0, merged.get("offset"));

        OfficialApiNode n = new OfficialApiNode("tid-pm", OfficialApiSpec.fromMap("postmark", specMap(base,
                Map.of("type", "header", "header", "X-Postmark-Server-Token", "prefix", "", "configKey", "server_token"),
                Map.of("list-bounces", Map.of("method", "GET", "path", "/bounces",
                        "required", List.of("count", "offset"),
                        "queryFrom", List.of("count", "offset"))))));
        output(n.handle(request(Map.of("server_token", "tok"), Map.of("operation", "list-bounces")), null));
        assertNotNull(lastPath.get());
        assertTrue(lastPath.get().contains("count=50"), lastPath.get());
        assertTrue(lastPath.get().contains("offset=0"), lastPath.get());
    }

    @Test
    void postmarkGetBounceAcceptsBounceIdAlias() {
        OfficialApiNode n = new OfficialApiNode("tid-pm", OfficialApiSpec.fromMap("postmark", specMap(base,
                Map.of("type", "header", "header", "X-Postmark-Server-Token", "prefix", "", "configKey", "server_token"),
                Map.of("get-bounce", Map.of("method", "GET", "path", "/bounces/{id}", "required", List.of("id"))))));
        output(n.handle(request(Map.of("server_token", "tok"),
                Map.of("operation", "get-bounce", "bounceID", "00000000-0000-0000-0000-000000000000")), null));
        assertTrue(lastPath.get().contains("/bounces/00000000-0000-0000-0000-000000000000"), lastPath.get());
    }

    @Test
    void postmarkSendBatchRootArrayBody() {
        OfficialApiNode n = new OfficialApiNode("tid-pm", OfficialApiSpec.fromMap("postmark", specMap(base,
                Map.of("type", "header", "header", "X-Postmark-Server-Token", "prefix", "", "configKey", "server_token"),
                Map.of("send-batch", Map.of("method", "POST", "path", "/email/batch",
                        "required", List.of("Messages"),
                        "bodyFrom", List.of("Messages"),
                        "bodyMap", Map.of("Messages", "$"))))));
        Map<String, Object> msg = Map.of("From", "a@b.c", "To", "d@e.f", "Subject", "t", "TextBody", "x");
        output(n.handle(request(Map.of("server_token", "tok"),
                Map.of("operation", "send-batch", "messages", List.of(msg))), null));
        assertNotNull(lastBody.get());
        assertTrue(lastBody.get().startsWith("[") && lastBody.get().contains("\"From\""), lastBody.get());
    }

    @Test
    void polygonAggregatesRejectsMalformedFromDateBeforeHttp() {
        Map<String, Object> aggOp = Map.of(
                "method", "GET",
                "path", "/v2/aggs/ticker/{ticker}/range/{multiplier}/{timespan}/{from}/{to}",
                "required", List.of("ticker", "multiplier", "timespan", "from", "to"));
        OfficialApiNode n = new OfficialApiNode("tid-poly", OfficialApiSpec.fromMap("polygon-io", specMap(base,
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of("aggregates", aggOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "aggregates", "ticker", "AAPL", "multiplier", "1", "timespan", "day",
                        "from", "01/02/2024", "to", "2024-01-05")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("from must be a YYYY-MM-DD date"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void shodanSearchForwardsQueryParam() {
        Map<String, Object> searchOp = Map.of(
                "method", "GET",
                "path", "/shodan/host/search",
                "required", List.of("query"),
                "queryFrom", List.of("query", "q", "page", "minify"));
        OfficialApiNode n = new OfficialApiNode("tid-sh", OfficialApiSpec.fromMap("shodan", specMap(base,
                Map.of("type", "query", "configKey", "key"),
                Map.of("search", searchOp))));
        n.handle(request(Map.of("key", "shodan-test-key"),
                Map.of("operation", "search", "query", "apache", "page", 1, "minify", true)), null);
        String uri = lastPath.get();
        assertNotNull(uri);
        assertTrue(uri.contains("query=apache"), uri);
        assertTrue(uri.contains("key=shodan-test-key"), uri);
    }

    @Test
    void shodanSearchAcceptsNestedArgsAndQAlias() {
        Map<String, Object> searchOp = Map.of(
                "method", "GET",
                "path", "/shodan/host/search",
                "required", List.of("query"),
                "queryFrom", List.of("query", "q"));
        OfficialApiNode n = new OfficialApiNode("tid-sh2", OfficialApiSpec.fromMap("shodan", specMap(base,
                Map.of("type", "query", "configKey", "key"),
                Map.of("search", searchOp))));
        n.handle(request(Map.of("key", "k"),
                Map.of("operation", "search", "arguments", Map.of("q", "apache"))), null);
        assertNotNull(lastPath.get());
        assertTrue(lastPath.get().contains("query=apache"), lastPath.get());
    }

    @Test
    void shodanDnsResolveCoercesHostnameList() {
        Map<String, Object> dnsOp = Map.of(
                "method", "GET",
                "path", "/dns/resolve",
                "required", List.of("hostnames"),
                "queryFrom", List.of("hostnames"));
        OfficialApiNode n = new OfficialApiNode("tid-sh3", OfficialApiSpec.fromMap("shodan", specMap(base,
                Map.of("type", "query", "configKey", "key"),
                Map.of("dns-resolve", dnsOp))));
        n.handle(request(Map.of("key", "k"),
                Map.of("operation", "dns-resolve", "hostnames", List.of("example.com", "example.org"))), null);
        assertNotNull(lastPath.get());
        assertTrue(lastPath.get().contains("hostnames=example.com%2Cexample.org")
                        || lastPath.get().contains("hostnames=example.com,example.org"),
                lastPath.get());
    }

    @Test
    void tinkAuthorizeMapsGrantTypeAliasToFormBody() {
        Map<String, Object> authOp = Map.of(
                "method", "POST",
                "path", "/api/v1/oauth/token",
                "required", List.of("grant_type"),
                "bodyFrom", List.of("client_id", "client_secret", "grant_type", "scope"),
                "contentType", "application/x-www-form-urlencoded",
                "bodyFormat", "form");
        OfficialApiNode n = new OfficialApiNode("tid-tink", OfficialApiSpec.fromMap("tink", specMap(base,
                Map.of("type", "bearer", "configKey", "access_token"),
                Map.of("authorize", authOp))));
        n.handle(request(
                Map.of("client_id", "cid", "client_secret", "sec"),
                Map.of("operation", "authorize", "grantType", "client_credentials")), null);
        assertEquals("POST", lastMethod.get());
        assertNotNull(lastBody.get());
        assertTrue(lastBody.get().contains("grant_type=client_credentials"), lastBody.get());
        assertNull(lastAuth.get());
    }

    @Test
    void tallyNestedConfigOperationRequiresFormId() {
        Map<String, Object> getOp = Map.of("method", "GET", "path", "/forms/{form_id}", "required", List.of("form_id"));
        OfficialApiNode n = new OfficialApiNode("tid-tally", OfficialApiSpec.fromMap("tally", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("form.get", getOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "tok"),
                Map.of("config", Map.of("operation", "form.get"))), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("form_id is required"), e.getMessage());
    }

    @Test
    void tallyFormGetAcceptsFormIdAlias() {
        Map<String, Object> getOp = Map.of("method", "GET", "path", "/forms/{form_id}", "required", List.of("form_id"));
        OfficialApiNode n = new OfficialApiNode("tid-tally2", OfficialApiSpec.fromMap("tally", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("form.get", getOp))));
        n.handle(request(Map.of("api_key", "tok"),
                Map.of("operation", "form.get", "formId", "abc123")), null);
        assertNotNull(lastPath.get());
        assertTrue(lastPath.get().contains("/forms/abc123"), lastPath.get());
    }

    @Test
    void tallyUnknownOperationInConfigIsRejected() {
        Map<String, Object> listOp = Map.of("method", "GET", "path", "/forms");
        Map<String, Object> getOp = Map.of("method", "GET", "path", "/forms/{form_id}", "required", List.of("form_id"));
        OfficialApiNode n = new OfficialApiNode("tid-tally3", OfficialApiSpec.fromMap("tally", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("form.list", listOp, "form.get", getOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "tok"),
                Map.of("config", Map.of("operation", "form.bogus"))), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Unknown operation"), e.getMessage());
    }

    @Test
    void sliteDocCreateMapsContentAliasToMarkdownBody() {
        Map<String, Object> createOp = Map.of(
                "method", "POST",
                "path", "/notes",
                "bodyFrom", List.of("title", "markdown", "html", "parentNoteId"));
        OfficialApiNode n = new OfficialApiNode("tid-slite", OfficialApiSpec.fromMap("slite", specMap(base,
                Map.of("type", "header", "header", "x-slite-api-key", "configKey", "api_key"),
                Map.of("doc_create", createOp))));
        n.handle(request(Map.of("api_key", "sk"),
                Map.of("operation", "doc_create", "title", "T", "content", "# hello slite")), null);
        assertEquals("POST", lastMethod.get());
        Map<String, Object> body = jsonBody();
        assertEquals("# hello slite", body.get("markdown"));
        assertFalse(body.containsKey("content"));
    }

    @Test
    void sliteChannelListUsesNotesRouteNotChannels() {
        Map<String, Object> channelOp = Map.of(
                "method", "GET",
                "path", "/notes");
        OfficialApiNode n = new OfficialApiNode("tid-slite2", OfficialApiSpec.fromMap("slite", specMap(base,
                Map.of("type", "header", "header", "x-slite-api-key", "configKey", "api_key"),
                Map.of("channel_list", channelOp))));
        n.handle(request(Map.of("api_key", "sk"), Map.of("operation", "channel_list")), null);
        assertNotNull(lastPath.get());
        assertTrue(lastPath.get().startsWith("/notes"), lastPath.get());
        assertFalse(lastPath.get().contains("channels"), lastPath.get());
    }

    @Test
    void customerIoSearchRejectsBadLimitBeforeHttp() {
        Map<String, Object> searchOp = Map.of(
                "method", "POST",
                "path", "/customers",
                "required", List.of("filter"),
                "queryFrom", List.of("start", "limit"),
                "bodyFrom", List.of("filter"));
        OfficialApiNode n = new OfficialApiNode("tid-cio2", OfficialApiSpec.fromMap("customer-io", specMap(base,
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ", "configKey", "app_api_key"),
                Map.of("customer.search", searchOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("app_api_key", "sk_test"),
                Map.of("operation", "customer.search", "filter", Map.of(), "limit", "abc")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("limit must be"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void intercomDefaultRegionBuildsContactsUrl() {
        Map<String, Object> listOp = Map.of(
                "method", "GET",
                "path", "/contacts",
                "queryFrom", List.of("per_page"));
        Map<String, Object> specRoot = new LinkedHashMap<>(specMap("https://{region}",
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ", "configKey", "access_token"),
                Map.of("contact.list", listOp)));
        specRoot.put("baseUrl", "https://{region}");
        specRoot.put("placeholderDefaults", Map.of("region", "api.intercom.io", "api_version", "2.16"));
        OfficialApiSpec spec = OfficialApiSpec.fromMap("intercom", specRoot);
        OfficialApiNode n = new OfficialApiNode("tid-ic", spec);
        String url = n.buildUrl(spec.operation("contact.list"), Map.of(), Map.of(), null);
        assertTrue(url.startsWith("https://api.intercom.io/contacts"), url);
    }

    @Test
    void enrichTransportErrorAddsHostHint() {
        String err = OfficialApiNode.enrichTransportError("HTTP transport failed",
                "https://api.intercom.io/contacts");
        assertTrue(err.contains("api.intercom.io"), err);
        assertTrue(err.contains("egress"), err);
    }

    @Test
    void clickupTimeStartRequiresTeamIdBeforeHttp() {
        Map<String, Object> op = Map.of(
                "kind", "http",
                "method", "POST",
                "path", "/team/{team_id}/time_entries/start",
                "required", List.of("team_id"));
        OfficialApiNode n = new OfficialApiNode("tid-cu", OfficialApiSpec.fromMap("clickup", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "", "configKey", "api_token"),
                Map.of("time_start", op))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_token", "pk_test"),
                Map.of("operation", "time_start")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("team_id is required for operation time_start"), e.getMessage());
    }

    @Test
    void clickupFolderCreateRequiresSpaceIdBeforeHttp() {
        Map<String, Object> op = Map.of(
                "kind", "http",
                "method", "POST",
                "path", "/space/{space_id}/folder",
                "required", List.of("space_id"),
                "bodyFrom", List.of("name"));
        OfficialApiNode n = new OfficialApiNode("tid-cu2", OfficialApiSpec.fromMap("clickup", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "", "configKey", "api_token"),
                Map.of("folder_create", op))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_token", "pk_test"),
                Map.of("operation", "folder_create", "name", "[AUTOMATED TEST]")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("space_id is required for operation folder_create"), e.getMessage());
    }

    @Test
    void customerIoListRequiresEmailBeforeHttp() {
        Map<String, Object> listOp = Map.of(
                "method", "GET",
                "path", "/customers",
                "required", List.of("email"),
                "queryFrom", List.of("email"));
        OfficialApiNode n = new OfficialApiNode("tid-cio", OfficialApiSpec.fromMap("customer-io", specMap(base,
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ", "configKey", "app_api_key"),
                Map.of("customer.list", listOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("app_api_key", "sk_test"),
                Map.of("operation", "customer.list")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("email is required for operation customer.list"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void codecovRejectsNonHexShaBeforeHttp() {
        OfficialApiNode n = new OfficialApiNode("tid-cc", OfficialApiSpec.fromMap("codecov", specMap(base,
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ", "configKey", "token"),
                Map.of("coverage_get", Map.of("method", "GET",
                        "path", "/api/v2/{service}/{owner}/repos/{repo}/commits/{sha}/",
                        "required", List.of("service", "owner", "repo", "sha"))))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of(
                "operation", "coverage_get",
                "service", "github",
                "owner", "fastapi",
                "repo", "fastapi",
                "sha", "not-a-valid-sha")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("40-character"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void emptyOperationStringRejectsEvenWhenConfigHasDefault() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "a", Map.of("path", "/a"),
                "b", Map.of("path", "/b"))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("operation", "a"), Map.of("operation", "   ")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("operation cannot be blank"));
        assertNull(lastPath.get());
    }

    @Test
    void currentsRejectsInvalidAvailableCatalogBeforeHttp() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("currents", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "available", Map.of("method", "GET", "path", "/available/{catalog}",
                                "required", List.of("catalog")))));
        OfficialApiNode n = new OfficialApiNode("tid-cur", spec);
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "available", "catalog", "news")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("catalog must be one of"));
        assertNull(lastPath.get());
    }

    @Test
    void currentsKindAliasMapsToCatalogInUrl() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("currents", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "available", Map.of("method", "GET", "path", "/available/{catalog}",
                                "required", List.of("catalog")))));
        OfficialApiNode n = new OfficialApiNode("tid-cur2", spec);
        Map<String, Object> out = output(n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "available", "kind", "languages")), null));
        assertEquals("ok", out.get("status"));
        assertTrue(lastPath.get().contains("/available/languages"), lastPath.get());
    }

    @Test
    void currentsNormalizesNoneImageStringInNews() {
        String json = """
                {"status":"ok","news":[{"id":"1","image":"None"},{"id":"2","image":""}]}
                """;
        Object norm = OfficialApiNode.normalizeCurrentsNewsPayload("currents", studioquack.Json.parse(json));
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) norm;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> news = (List<Map<String, Object>>) m.get("news");
        assertEquals("", news.get(0).get("image"));
        assertEquals("", news.get(1).get("image"));
    }

    @Test
    void llmToolCallArgsMapSuppliesPathParameters() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "pr_get", Map.of("method", "GET",
                        "path", "/repositories/{workspace}/{repo_slug}/pullrequests/{id}",
                        "required", List.of("workspace", "repo_slug", "id")))));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("operation", "pr_get");
        nested.put("workspace", "acme");
        nested.put("repo_slug", "api");
        nested.put("id", 1);
        Map<String, Object> out = output(n.handle(request(null, Map.of("args", nested)), null));
        assertEquals("ok", out.get("status"));
        assertTrue(lastPath.get().contains("/pullrequests/1"), lastPath.get());
    }

    @Test
    void bitbucketPullRequestIdAliasMapsToPathPlaceholder() {
        OfficialApiNode n = new OfficialApiNode("tid-bb", OfficialApiSpec.fromMap("bitbucket", specMap(base, null,
                Map.of("pr_get", Map.of("method", "GET",
                        "path", "/repositories/{workspace}/{repo_slug}/pullrequests/{id}",
                        "required", List.of("workspace", "repo_slug", "id"))))));
        Map<String, Object> out = output(n.handle(request(null, Map.of(
                "operation", "pr_get",
                "workspace", "acme",
                "repo_slug", "api",
                "pull_request_id", 42)), null));
        assertEquals("ok", out.get("status"));
        assertTrue(lastPath.get().contains("/pullrequests/42"), lastPath.get());
    }

    @Test
    void pathVarsAreSubstitutedAndUrlEncoded() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "get-thing", Map.of("path", "/things/{id}", "required", List.of("id")))));
        Map<String, Object> out = output(n.handle(request(null,
                Map.of("operation", "get-thing", "id", "a b/c")), null));
        assertEquals("ok", out.get("status"));
        assertEquals("/things/a%20b%2Fc", lastPath.get());
    }

    @Test
    void hostAndPathKeepsSlashesAndQuery() {
        OfficialApiNode n = node(specMap("http://", null, Map.of(
                "read", Map.of("path", "{host_and_path}", "required", List.of("host_and_path")))));
        String host = "127.0.0.1:" + server.getAddress().getPort() + "/news/rss.xml?x=a b";
        Map<String, Object> out = output(n.handle(request(null,
                Map.of("operation", "read", "host_and_path", host)), null));
        assertEquals("ok", out.get("status"), String.valueOf(out.get("error")));
        assertEquals("/news/rss.xml?x=a%20b", lastPath.get());
    }

    @Test
    void hostAndPathEncodesUserinfo() {
        OfficialApiNode n = node(specMap("http://", null, Map.of(
                "read", Map.of("path", "{host_and_path}", "required", List.of("host_and_path")))));
        String host = "user@127.0.0.1:" + server.getAddress().getPort() + "/news";
        StudioQuackError e = thrown(() -> n.handle(request(null,
                Map.of("operation", "read", "host_and_path", host)), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("invalid URL", e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void usCensusRejectsSingleComponentDataset() {
        Map<String, Object> op = Map.of(
                "method", "GET",
                "path", "/{vintage}/{dataset}",
                "required", List.of("vintage", "dataset", "get", "for"),
                "queryFrom", List.of("get", "for", "in"));
        OfficialApiNode n = new OfficialApiNode("tid-census", OfficialApiSpec.fromMap("us-census-bureau",
                specMap(base, null, Map.of("census_get", op))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of(
                "operation", "census_get", "vintage", "2010", "dataset", "dec",
                "get", "NAME", "for", "state:06")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("program/dataset"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void datasetAndJobPathKeepSlashes() {
        OfficialApiNode census = node(specMap(base, null, Map.of(
                "census_get", Map.of("path", "/{vintage}/{dataset}"))));
        Map<String, Object> out = output(census.handle(request(null, Map.of(
                "operation", "census_get", "vintage", "2021", "dataset", "acs/acs1")), null));
        assertEquals("ok", out.get("status"), String.valueOf(out.get("error")));
        assertEquals("/2021/acs/acs1", lastPath.get());

        OfficialApiNode jenkins = node(specMap(base, null, Map.of(
                "job_get", Map.of("path", "/job/{jobPath}/build"))));
        out = output(jenkins.handle(request(null, Map.of(
                "operation", "job_get", "jobPath", "Folder/My Job")), null));
        assertEquals("ok", out.get("status"), String.valueOf(out.get("error")));
        assertEquals("/job/Folder/My%20Job/build", lastPath.get());
    }

    @Test
    void oauthPathKeepsSlashes() {
        OfficialApiNode n = node(specMap("http://", null, Map.of(
                "token", Map.of("path", "{tokenHost}{tokenPath}",
                        "required", List.of("tokenHost", "tokenPath")))));
        String host = "127.0.0.1:" + server.getAddress().getPort();
        Map<String, Object> out = output(n.handle(request(null, Map.of(
                "operation", "token", "tokenHost", host, "tokenPath", "/oauth/token")), null));
        assertEquals("ok", out.get("status"), String.valueOf(out.get("error")));
        assertEquals("/oauth/token", lastPath.get());
    }

    @Test
    void queryParamsComeFromInputThenConfig() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "search", Map.of("path", "/search", "queryFrom", List.of("q", "limit")))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("limit", "5"),
                Map.of("operation", "search", "q", "hello world")), null));
        assertEquals("ok", out.get("status"));
        assertEquals("/search?q=hello%20world&limit=5", lastPath.get());
    }

    @Test
    void integralDoubleQueryParamIsSentAsInteger() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "search", Map.of("path", "/search", "queryFrom", List.of("srlimit", "sroffset")))));
        Map<String, Object> out = output(n.handle(request(null,
                Map.of("operation", "search", "srlimit", 1.0d, "sroffset", 0.0d)), null));
        assertEquals("ok", out.get("status"));
        assertEquals("/search?srlimit=1&sroffset=0", lastPath.get());
    }

    @Test
    void bearerAuthHeaderIsApplied() {
        OfficialApiNode n = node(specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("ping", Map.of("path", "/ping"))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("api_key", "sekret"), Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals("Bearer sekret", lastAuth.get());
    }

    @Test
    void explicitBearerPrefixKeepsTheSeparator() {
        OfficialApiNode n = node(specMap(base,
                Map.of("type", "bearer", "configKey", "api_key", "prefix", "Bearer "),
                Map.of("ping", Map.of("path", "/ping"))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("api_key", "sekret"), Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals("Bearer sekret", lastAuth.get());
    }

    @Test
    void headerPrefixKeepsTheSeparator() {
        OfficialApiNode n = node(specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of("ping", Map.of("path", "/ping"))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("api_key", "sekret"), Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals("Bearer sekret", lastAuth.get());
    }

    @Test
    void missingAuthSecretFailsBeforeHttp() {
        OfficialApiNode n = node(specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("ping", Map.of("path", "/ping"))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("api_key is required"));
        assertNull(lastPath.get());
    }

    @Test
    void minimaxMergesInputPortOperationForGenerateImage() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("minimax", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "chat", Map.of("method", "POST", "path", "/v1/chat/completions",
                                "required", List.of("messages"),
                                "bodyFrom", List.of("model", "messages")),
                        "generate_image", Map.of("method", "POST", "path", "/v1/image_generation",
                                "required", List.of("prompt"),
                                "bodyFrom", List.of("model", "prompt")))));
        OfficialApiNode n = new OfficialApiNode("tid-mm", spec);
        Map<String, Object> req = request(Map.of("operation", "chat", "api_key", "k"), null);
        req.put("inputVariables", Map.of("input",
                Map.of("operation", "generate_image", "prompt", "red square on white")));
        n.handle(req, null);
        assertTrue(lastPath.get().contains("/v1/image_generation"), lastPath.get());
        assertEquals("red square on white", jsonBody().get("prompt"));
    }

    @Test
    void minimaxInfersTtsFromTextWhenConfigDefaultChat() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("minimax", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "chat", Map.of("method", "POST", "path", "/v1/chat/completions",
                                "required", List.of("messages"), "bodyFrom", List.of("messages")),
                        "tts", Map.of("method", "POST", "path", "/v1/t2a_v2",
                                "required", List.of("text"), "bodyFrom", List.of("model", "text")))));
        OfficialApiNode n = new OfficialApiNode("tid-mm", spec);
        Map<String, Object> req = request(Map.of("operation", "chat", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input", Map.of("text", "hello speech")));
        n.handle(req, null);
        assertTrue(lastPath.get().contains("/v1/t2a_v2"), lastPath.get());
    }

    @Test
    void minimaxRejectsStringMessagesBeforeHttp() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("minimax", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("chat", Map.of("method", "POST", "path", "/v1/chat/completions",
                        "required", List.of("messages"), "bodyFrom", List.of("messages")))));
        OfficialApiNode n = new OfficialApiNode("tid-mm", spec);
        Map<String, Object> req = request(Map.of("operation", "chat", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input", Map.of("messages", "Say hello")));
        StudioQuackError e = thrown(() -> n.handle(req, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("array"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void stabilityAiInfersGetResultWhenOnlyIdDespiteConfigGenerate() {
        respondBody = "{\"status\":\"Complete\"}";
        Map<String, Object> generate = Map.of(
                "method", "POST",
                "path", "/v2beta/stable-image/generate/{engine}",
                "required", List.of("prompt"),
                "bodyFrom", List.of("prompt"),
                "contentType", "multipart/form-data",
                "bodyFormat", "multipart");
        Map<String, Object> getResult = Map.of(
                "method", "GET",
                "path", "/v2beta/results/{id}",
                "required", List.of("id"));
        OfficialApiSpec spec = OfficialApiSpec.fromMap("stability-ai", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("generate", generate, "get_result", getResult)));
        OfficialApiNode n = new OfficialApiNode("stability-ai", spec);
        n.handle(request(
                Map.of("operation", "generate", "api_key", "k", "engine", "core"),
                Map.of("id", "test-nonexistent-id-000")), null);
        assertEquals("GET", lastMethod.get());
        assertTrue(lastPath.get().contains("/v2beta/results/test-nonexistent-id-000"), lastPath.get());
    }

    @Test
    void tursoExecuteSqlFromTopLevelSqlBuildsPipeline() {
        respondBody = "{\"results\":[]}";
        Map<String, Object> exec = Map.of(
                "method", "POST",
                "path", "/v2/pipeline",
                "bodyFrom", List.of("requests"),
                "contentType", "application/json");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("turso", specMap(
                "https://databaseName-organizationSlug.turso.io",
                Map.of("type", "bearer", "configKey", "auth_token"),
                Map.of("execute.sql", exec)));
        OfficialApiNode n = new OfficialApiNode("turso", spec);
        n.handle(request(
                Map.of("auth_token", "tok", "database_url", base),
                Map.of("operation", "execute.sql", "sql", "SELECT 1")), null);
        assertEquals("POST", lastMethod.get());
        assertTrue(lastPath.get().contains("/v2/pipeline"), lastPath.get());
        assertTrue(lastBody.get().contains("\"sql\":\"SELECT 1\""), lastBody.get());
        assertTrue(lastBody.get().contains("\"type\":\"close\""), lastBody.get());
    }

    @Test
    void tursoRejectsPlaceholderHostBeforeHttp() {
        Map<String, Object> health = Map.of("method", "GET", "path", "/health");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("turso", specMap(
                "https://databaseName-organizationSlug.turso.io",
                Map.of("type", "bearer", "configKey", "auth_token"),
                Map.of("database.health", health)));
        OfficialApiNode n = new OfficialApiNode("turso", spec);
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("auth_token", "tok"),
                Map.of("operation", "database.health")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("database_url"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void trelloBoardCreateMissingNameFailsBeforeHttp() {
        Map<String, Object> boardCreate = Map.of(
                "method", "POST",
                "path", "/boards",
                "required", List.of("name"),
                "queryFrom", List.of("key", "name"));
        OfficialApiSpec spec = OfficialApiSpec.fromMap("trello", specMap(base,
                Map.of("type", "query", "configKey", "token"),
                Map.of("board_create", boardCreate)));
        OfficialApiNode n = new OfficialApiNode("trello", spec);
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("token", "t", "key", "k"),
                Map.of("operation", "board_create")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("name is required"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void trelloLabelAddMissingIdLabelFailsBeforeHttp() {
        Map<String, Object> labelAdd = Map.of(
                "method", "POST",
                "path", "/cards/{id}/idLabels",
                "required", List.of("id", "idLabel"),
                "queryFrom", List.of("key", "idLabel"),
                "queryMap", Map.of("idLabel", "value"));
        OfficialApiSpec spec = OfficialApiSpec.fromMap("trello", specMap(base,
                Map.of("type", "query", "configKey", "token"),
                Map.of("label_add", labelAdd)));
        OfficialApiNode n = new OfficialApiNode("trello", spec);
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("token", "t", "key", "k"),
                Map.of("operation", "label_add", "id", "000000000000000000000000")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("idLabel is required"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void trelloMemberAddUsesPut() {
        respondBody = "{}";
        Map<String, Object> memberAdd = Map.of(
                "method", "PUT",
                "path", "/boards/{id}/members",
                "required", List.of("id"),
                "queryFrom", List.of("key", "email"));
        OfficialApiSpec spec = OfficialApiSpec.fromMap("trello", specMap(base,
                Map.of("type", "query", "configKey", "token"),
                Map.of("member_add", memberAdd)));
        OfficialApiNode n = new OfficialApiNode("trello", spec);
        n.handle(request(Map.of("token", "t", "key", "k"),
                Map.of("operation", "member_add", "id", "b1",
                        "email", "test@example.com")), null);
        assertEquals("PUT", lastMethod.get());
        assertTrue(lastPath.get().contains("/boards/b1/members"), lastPath.get());
    }

    @Test
    void todoistRejectsOutOfRangePriorityBeforeHttp() {
        Map<String, Object> create = Map.of(
                "method", "POST",
                "path", "/tasks",
                "required", List.of("content"),
                "bodyFrom", List.of("content", "priority"),
                "contentType", "application/json");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("todoist", specMap(base,
                Map.of("type", "bearer", "configKey", "api_token"),
                Map.of("create", create)));
        OfficialApiNode n = new OfficialApiNode("todoist", spec);
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("api_token", "tok"),
                Map.of("operation", "create", "content", "x", "priority", "9")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("priority must be 1"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void todoistCreateMapsSingularLabelToLabelsArray() {
        respondBody = "{\"id\":\"1\"}";
        Map<String, Object> create = Map.of(
                "method", "POST",
                "path", "/tasks",
                "required", List.of("content"),
                "bodyFrom", List.of("content", "labels"),
                "contentType", "application/json");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("todoist", specMap(base,
                Map.of("type", "bearer", "configKey", "api_token"),
                Map.of("create", create)));
        OfficialApiNode n = new OfficialApiNode("todoist", spec);
        n.handle(request(Map.of("api_token", "tok"),
                Map.of("operation", "create", "content", "t", "label", "autotestlabel")), null);
        assertTrue(jsonBody().get("labels") instanceof List<?>);
        assertEquals("autotestlabel", ((List<?>) jsonBody().get("labels")).get(0));
    }

    @Test
    void todoistListFilterUsesDedicatedEndpointAndQueryParam() {
        respondBody = "{\"results\":[]}";
        Map<String, Object> list = Map.of(
                "method", "GET",
                "path", "/tasks",
                "queryFrom", List.of("filter", "lang"),
                "queryMap", Map.of("filter", "query"));
        OfficialApiSpec spec = OfficialApiSpec.fromMap("todoist", specMap(base,
                Map.of("type", "bearer", "configKey", "api_token"),
                Map.of("list", list)));
        OfficialApiNode n = new OfficialApiNode("todoist", spec);
        n.handle(request(Map.of("api_token", "tok"),
                Map.of("operation", "list", "filter", "label:autotestlabel")), null);
        assertEquals("GET", lastMethod.get());
        assertTrue(lastPath.get().contains("/tasks/filter"), lastPath.get());
        assertTrue(lastPath.get().contains("query="), lastPath.get());
    }

    @Test
    void commonAgentPayloadCoercesJsonEncodedRequests() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("t", Map.of("baseUrl", base,
                "operations", Map.of("x", Map.of("method", "POST",
                        "bodyFrom", List.of("requests"))))).operation("x");
        Map<String, Object> out = OfficialApiNode.mergeCommonAgentPayloadFields(op,
                Map.of("requests", "[{\"type\":\"execute\",\"stmt\":{\"sql\":\"select 1\"}}]"));
        assertTrue(out.get("requests") instanceof List<?>);
    }

    @Test
    void commonAgentPayloadMapsQueryAliasToSql() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("t", Map.of("baseUrl", base,
                "operations", Map.of("x", Map.of("method", "POST",
                        "bodyFrom", List.of("sql"))))).operation("x");
        Map<String, Object> out = OfficialApiNode.mergeCommonAgentPayloadFields(op, Map.of("query", "select 1"));
        assertEquals("select 1", out.get("sql"));
    }

    @Test
    void commonAgentPayloadMapsIdLabelToValueWhenBodyExpectsValue() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("t", Map.of("baseUrl", base,
                "operations", Map.of("x", Map.of("method", "POST",
                        "bodyFrom", List.of("value"))))).operation("x");
        Map<String, Object> out = OfficialApiNode.mergeCommonAgentPayloadFields(op,
                Map.of("idLabel", "red"));
        assertEquals("red", out.get("value"));
    }

    @Test
    void gitlabIssueNoteMapsContentToBody() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("gitlab", Map.of("baseUrl", base,
                "operations", Map.of("issue_note", Map.of("method", "POST", "path", "/x",
                        "bodyFrom", List.of("body"))))).operation("issue_note");
        Map<String, Object> out = OfficialApiNode.mergeGitlabInputs("gitlab", op,
                Map.of("content", "note text"));
        assertEquals("note text", out.get("body"));
    }

    @Test
    void gitlabFileCreateKeepsExplicitBranch() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("gitlab", Map.of("baseUrl", base,
                "operations", Map.of("file_create", Map.of("method", "POST", "path", "/x",
                        "required", List.of("branch"))))).operation("file_create");
        Map<String, Object> out = OfficialApiNode.mergeGitlabInputs("gitlab", op,
                Map.of("branch", "main", "content", "x"));
        assertEquals("main", out.get("branch"));
    }

    @Test
    void twilioRejectsInvalidToBeforeHttp() {
        Map<String, Object> smsOp = Map.of(
                "method", "POST",
                "path", "/Accounts/{account_sid}/Messages.json",
                "required", List.of("To", "Body"),
                "bodyFrom", List.of("To", "From", "Body"));
        OfficialApiNode n = new OfficialApiNode("tid-twilio", OfficialApiSpec.fromMap("twilio", specMap(base,
                Map.of("type", "basic", "header", "Authorization", "configKey", "auth_token", "userKey", "account_sid"),
                Map.of("send-sms", smsOp))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("account_sid", "AC" + "0".repeat(32), "auth_token", "secret"),
                Map.of("operation", "send-sms", "To", "12345", "Body", "hi")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("E.164"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void twilioRejectsBadMediaUrlBeforeHttp() {
        Map<String, Object> mmsOp = Map.of(
                "method", "POST",
                "path", "/Accounts/{account_sid}/Messages.json",
                "required", List.of("To"),
                "bodyFrom", List.of("To", "MediaUrl"));
        OfficialApiNode n = new OfficialApiNode("tid-twilio-mms", OfficialApiSpec.fromMap("twilio", specMap(base,
                Map.of("type", "basic", "header", "Authorization", "configKey", "auth_token", "userKey", "account_sid"),
                Map.of("send-mms", mmsOp))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("account_sid", "AC" + "0".repeat(32), "auth_token", "secret"),
                Map.of("operation", "send-mms", "To", "+15551234567", "MediaUrl", "not-a-url")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("MediaUrl"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void webflowRejectsLimitOver100BeforeHttp() {
        Map<String, Object> itemList = Map.of(
                "method", "GET",
                "path", "/collections/{collection_id}/items",
                "required", List.of("collection_id"),
                "queryFrom", List.of("limit", "offset"));
        OfficialApiNode n = new OfficialApiNode("tid-webflow-limit", OfficialApiSpec.fromMap("webflow", specMap(base,
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ", "configKey", "access_token"),
                Map.of("item.list", itemList))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("access_token", "tok"),
                Map.of("operation", "item.list", "collection_id", "col1", "limit", 101)), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("limit"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void webflowRejectsInvalidOrderStatusBeforeHttp() {
        Map<String, Object> orderList = Map.of(
                "method", "GET",
                "path", "/sites/{site_id}/orders",
                "required", List.of("site_id"),
                "queryFrom", List.of("status", "limit", "offset"));
        OfficialApiNode n = new OfficialApiNode("tid-webflow-status", OfficialApiSpec.fromMap("webflow", specMap(base,
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ", "configKey", "access_token"),
                Map.of("order.list", orderList))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("access_token", "tok"),
                Map.of("operation", "order.list", "site_id", "site1", "status", "completed")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("status"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void eurostatRejectsDashYearRangeBeforeHttp() {
        Map<String, Object> queryOp = Map.of(
                "method", "GET",
                "path", "/statistics/1.0/data/{datasetCode}?format=JSON",
                "required", List.of("datasetCode"),
                "queryFrom", List.of("lang", "time", "sinceTimePeriod", "untilTimePeriod", "lastTimePeriod", "geo",
                        "geoLevel", "precision"));
        OfficialApiNode n = new OfficialApiNode("tid-eurostat-time", OfficialApiSpec.fromMap("eurostat",
                specMap(base, Map.of("type", "none", "header", "Authorization", "configKey", "api_key"),
                        Map.of("query", queryOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of(),
                Map.of("operation", "query", "datasetCode", "nama_10_gdp", "geo", "DE", "time", "2020-2022")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("time"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void eurostatRejectsUnsupportedLangBeforeHttp() {
        Map<String, Object> metaOp = Map.of(
                "method", "GET",
                "path", "/sdmx/2.1/dataflow/ESTAT/{datasetCode}?format=JSON",
                "required", List.of("datasetCode"),
                "queryFrom", List.of("lang"));
        OfficialApiNode n = new OfficialApiNode("tid-eurostat-lang", OfficialApiSpec.fromMap("eurostat",
                specMap(base, Map.of("type", "none", "header", "Authorization", "configKey", "api_key"),
                        Map.of("metadata_get", metaOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of(),
                Map.of("operation", "metadata_get", "datasetCode", "nama_10_gdp", "lang", "TR")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("lang"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void fredRejectsMalformedApiKeyBeforeHttp() {
        Map<String, Object> searchOp = Map.of(
                "method", "GET",
                "path", "/series/search?file_type=json",
                "required", List.of("search_text"),
                "queryFrom", List.of("search_text", "limit", "offset"));
        OfficialApiNode n = new OfficialApiNode("tid-fred-key", OfficialApiSpec.fromMap("fred",
                specMap(base, Map.of("type", "query", "header", "Authorization", "configKey", "api_key"),
                        Map.of("series_search", searchOp))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("api_key", "not-a-valid-fred-key"),
                Map.of("operation", "series_search", "search_text", "GDP")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("32-character"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void fredRejectsInvalidUnitsBeforeHttp() {
        String key = "a".repeat(32);
        Map<String, Object> obsOp = Map.of(
                "method", "GET",
                "path", "/series/observations?file_type=json",
                "required", List.of("series_id"),
                "queryFrom", List.of("series_id", "observation_start", "observation_end", "frequency", "units"));
        OfficialApiNode n = new OfficialApiNode("tid-fred-units", OfficialApiSpec.fromMap("fred",
                specMap(base, Map.of("type", "query", "header", "Authorization", "configKey", "api_key"),
                        Map.of("series_observations", obsOp))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("api_key", key),
                Map.of("operation", "series_observations", "series_id", "GDP", "units", "chains")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("units"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void gladiaInfersGetTranscriptForIdOnly() {
        String jobId = "00000000-0000-0000-0000-000000000001";
        Map<String, Object> getOp = Map.of(
                "method", "GET",
                "path", "/v2/pre-recorded/{id}",
                "required", List.of("id"));
        Map<String, Object> postOp = Map.of(
                "method", "POST",
                "path", "/v2/pre-recorded",
                "required", List.of("audio_url"),
                "bodyFrom", List.of("audio_url", "model"));
        OfficialApiNode n = new OfficialApiNode("tid-gladia-infer", OfficialApiSpec.fromMap("gladia",
                specMap(base,
                        Map.of("type", "header", "header", "x-gladia-key", "configKey", "api_key"),
                        Map.of("pre_recorded", postOp, "get_transcript", getOp))));
        n.handle(request(Map.of("api_key", "test-key", "operation", "pre_recorded"),
                Map.of("id", jobId)), null);
        assertEquals("GET", lastMethod.get());
        assertTrue(lastPath.get().contains(jobId), lastPath.get());
    }

    @Test
    void ideogramInfersGetTaskFromGenerationId() {
        Map<String, Object> getTask = Map.of(
                "method", "GET",
                "path", "/v1/generations/{generation_id}",
                "required", List.of("generation_id"));
        Map<String, Object> generate = Map.of(
                "method", "POST",
                "path", "/v1/ideogram-v4/generate",
                "bodyFrom", List.of("text_prompt", "json_prompt"),
                "contentType", "multipart/form-data",
                "bodyFormat", "multipart");
        OfficialApiNode n = new OfficialApiNode("tid-ideogram-poll", OfficialApiSpec.fromMap("ideogram",
                specMap(base, Map.of("type", "header", "header", "Api-Key", "configKey", "api_key"),
                        Map.of("get_task", getTask, "generate", generate))));
        n.handle(request(
                Map.of("api_key", "key", "operation", "generate"),
                Map.of("generation_id", "test-nonexistent-000")), null);
        assertEquals("GET", lastMethod.get());
        assertTrue(lastPath.get().contains("test-nonexistent-000"), lastPath.get());
    }

    @Test
    void ideogramRejectsGenerateWithoutPromptBeforeHttp() {
        Map<String, Object> generate = Map.of(
                "method", "POST",
                "path", "/v1/ideogram-v4/generate",
                "bodyFrom", List.of("text_prompt", "json_prompt"),
                "contentType", "multipart/form-data",
                "bodyFormat", "multipart");
        OfficialApiNode n = new OfficialApiNode("tid-ideogram-gen", OfficialApiSpec.fromMap("ideogram",
                specMap(base, Map.of("type", "header", "header", "Api-Key", "configKey", "api_key"),
                        Map.of("generate", generate))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("api_key", "key", "operation", "generate"), Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("text_prompt"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void hunterRejectsInvalidDepartmentBeforeHttp() {
        Map<String, Object> domainSearch = Map.of(
                "method", "GET",
                "path", "/v2/domain-search",
                "queryFrom", List.of("domain", "company", "limit", "offset", "type", "seniority", "department",
                        "required_field"));
        OfficialApiNode n = new OfficialApiNode("tid-hunter-dept", OfficialApiSpec.fromMap("hunter",
                specMap(base, Map.of("type", "header", "header", "X-API-KEY", "configKey", "api_key"),
                        Map.of("domain.search", domainSearch))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("api_key", "key"),
                Map.of("operation", "domain.search", "domain", "github.com", "department", "engineering")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("department"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void hunterRejectsLimitZeroBeforeHttp() {
        Map<String, Object> domainSearch = Map.of(
                "method", "GET",
                "path", "/v2/domain-search",
                "queryFrom", List.of("domain", "limit"));
        OfficialApiNode n = new OfficialApiNode("tid-hunter-limit", OfficialApiSpec.fromMap("hunter",
                specMap(base, Map.of("type", "header", "header", "X-API-KEY", "configKey", "api_key"),
                        Map.of("domain.search", domainSearch))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("api_key", "key"),
                Map.of("operation", "domain.search", "domain", "github.com", "limit", 0)), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("limit"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void hashnodeRejectsEmptyQueryBeforeHttp() {
        Map<String, Object> postGet = Map.of(
                "method", "POST",
                "path", "/",
                "required", List.of("query"),
                "bodyFrom", List.of("query", "variables"));
        OfficialApiNode n = new OfficialApiNode("tid-hashnode-empty", OfficialApiSpec.fromMap("hashnode",
                specMap(base,
                        Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ",
                                "configKey", "personal_access_token"),
                        Map.of("post_get", postGet))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("personal_access_token", "pat-test", "operation", "post_get"),
                Map.of("operation", "post_get", "query", "")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("query"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void hashnodeRejectsHtmlGraphqlResponse() {
        respondBody = "<!DOCTYPE html><html><body>GraphQL API is moving to a paid offering</body></html>";
        respondContentType = "text/html";
        Map<String, Object> postGet = Map.of(
                "method", "POST",
                "path", "/",
                "required", List.of("query"),
                "bodyFrom", List.of("query", "variables"));
        OfficialApiNode n = new OfficialApiNode("tid-hashnode-html", OfficialApiSpec.fromMap("hashnode",
                specMap(base,
                        Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ",
                                "configKey", "personal_access_token"),
                        Map.of("post_get", postGet))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("personal_access_token", "pat-test", "operation", "post_get"),
                Map.of("operation", "post_get", "query", "query { __typename }")), null));
        assertTrue(e.getMessage().contains("HTML"), e.getMessage());
        assertEquals(200, (int) e.data().get("http_status"));
    }

    @Test
    void gladiaRejectsInvalidModelBeforeHttp() {
        Map<String, Object> postOp = Map.of(
                "method", "POST",
                "path", "/v2/pre-recorded",
                "required", List.of("audio_url"),
                "bodyFrom", List.of("audio_url", "model"));
        OfficialApiNode n = new OfficialApiNode("tid-gladia-model", OfficialApiSpec.fromMap("gladia",
                specMap(base,
                        Map.of("type", "header", "header", "x-gladia-key", "configKey", "api_key"),
                        Map.of("pre_recorded", postOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "test-key"),
                Map.of("audio_url", "https://example.com/a.mp3", "model", "invalid-model-x9")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("model"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void supabaseMergeCoercesRecordJsonString() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("supabase", Map.of("baseUrl", base,
                "operations", Map.of("table.insert", Map.of("method", "POST", "path", "/rest/v1/{table}",
                        "bodyFrom", List.of("record"), "required", List.of("table", "record")))))
                .operation("table.insert");
        Map<String, Object> out = OfficialApiNode.mergeSupabaseInputs("supabase", op,
                Map.of("record", "{\"name\":\"probe\"}"));
        assertTrue(out.get("record") instanceof Map<?, ?>);
        assertEquals("probe", ((Map<?, ?>) out.get("record")).get("name"));
    }

    void commonAgentPayloadMapsIdLabelToValueWhenQueryExpectsValue() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("t", Map.of("baseUrl", base,
                "operations", Map.of("x", Map.of("method", "POST",
                        "queryFrom", List.of("idLabel", "value"),
                        "queryMap", Map.of("idLabel", "value"))))).operation("x");
        Map<String, Object> out = OfficialApiNode.mergeCommonAgentPayloadFields(op,
                Map.of("idLabel", "red"));
        assertEquals("red", out.get("value"));
    }

    @Test
    void commonAgentPayloadMapsOAuthCamelCaseOnTokenBody() {
        OfficialApiSpec.Operation op = OfficialApiSpec.fromMap("t", Map.of("baseUrl", base,
                "operations", Map.of("token", Map.of("method", "POST",
                        "bodyFrom", List.of("grant_type", "client_id", "client_secret", "refresh_token")))))
                .operation("token");
        Map<String, Object> out = OfficialApiNode.mergeCommonAgentPayloadFields(op, Map.of(
                "grantType", "refresh_token",
                "clientId", "cid",
                "clientSecret", "sec",
                "refreshToken", "rt"));
        assertEquals("refresh_token", out.get("grant_type"));
        assertEquals("cid", out.get("client_id"));
        assertEquals("sec", out.get("client_secret"));
        assertEquals("rt", out.get("refresh_token"));
    }

    @Test
    void queryScalarJoinsCollectionForQueryString() {
        assertEquals("a,b", OfficialApiNode.queryScalar(
                Map.of("ids", List.of("a", "b")), "ids"));
        assertNull(OfficialApiNode.queryScalar(Map.of("ids", List.of()), "ids"));
    }

    @Test
    void genericQueryFromCoercesHostnameList() {
        Map<String, Object> opDef = Map.of(
                "method", "GET",
                "path", "/dns/resolve",
                "required", List.of("hostnames"),
                "queryFrom", List.of("hostnames"));
        OfficialApiNode n = new OfficialApiNode("tid-generic", OfficialApiSpec.fromMap("generic", specMap(base,
                Map.of("type", "query", "configKey", "key"),
                Map.of("resolve", opDef))));
        n.handle(request(Map.of("key", "k"),
                Map.of("operation", "resolve", "hostnames", List.of("a.example", "b.example"))), null);
        assertNotNull(lastPath.get());
        assertTrue(lastPath.get().contains("hostnames=a.example"),
                lastPath.get());
        assertTrue(lastPath.get().contains("b.example"), lastPath.get());
    }

    @Test
    void stabilityAiGenerateSendsMultipartBoundary() {
        Map<String, Object> generate = Map.of(
                "method", "POST",
                "path", "/v2beta/stable-image/generate/{engine}",
                "required", List.of("prompt"),
                "bodyFrom", List.of("prompt"),
                "contentType", "multipart/form-data",
                "bodyFormat", "multipart");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("stability-ai", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("generate", generate)));
        OfficialApiNode n = new OfficialApiNode("stability-ai", spec);
        n.handle(request(
                Map.of("operation", "generate", "api_key", "k", "engine", "core"),
                Map.of("prompt", "red circle")), null);
        assertEquals("POST", lastMethod.get());
        assertTrue(lastContentType.get().startsWith("multipart/form-data; boundary="), lastContentType.get());
        assertTrue(lastBody.get().contains("name=\"prompt\""), lastBody.get());
    }

    @Test
    void emeliaCampaignPauseUsesAdvancedPath() {
        respondBody = "{\"success\":true}";
        OfficialApiNode n = new OfficialApiNode("tid-em", OfficialApiSpec.fromMap("emelia",
                specMap(base,
                        Map.of("type", "header", "configKey", "api_key"),
                        Map.of("campaign.pause", Map.of("method", "POST",
                                "path", "/advanced/campaigns/{campaignId}/pause",
                                "required", List.of("campaignId"),
                                "bodyFrom", List.of("_empty"))))));
        output(n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "campaign.pause", "campaignId", "6650f1a2b839d80011c5a0f1")), null));
        assertTrue(lastPath.get().contains("/advanced/campaigns/6650f1a2b839d80011c5a0f1/pause"), lastPath.get());
    }

    @Test
    void emeliaCreateMapsCampaignNameAliasOntoWireBody() {
        respondBody = "{\"success\":true,\"campaignId\":\"6650f1a2b839d80011c5a0f1\"}";
        OfficialApiNode n = new OfficialApiNode("tid-em", OfficialApiSpec.fromMap("emelia",
                specMap(base,
                        Map.of("type", "header", "configKey", "api_key"),
                        Map.of("campaign.create", Map.of("method", "POST", "path", "/advanced/campaigns",
                                "required", List.of("name"),
                                "bodyFrom", List.of("name", "recipients", "sequence"))))));
        output(n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "campaign.create", "campaignName", "[AUTOMATED TEST] emelia")), null));
        assertEquals("[AUTOMATED TEST] emelia", jsonBody().get("name"));
    }

    @Test
    void emeliaRejectsMalformedCampaignIdBeforeHttp() {
        OfficialApiNode n = new OfficialApiNode("tid-em", OfficialApiSpec.fromMap("emelia",
                specMap(base,
                        Map.of("type", "header", "configKey", "api_key"),
                        Map.of("campaign.get", Map.of("method", "GET",
                                "path", "/advanced/campaigns/{campaignId}",
                                "required", List.of("campaignId"))))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "campaign.get", "campaignId", "00000000-0000-0000-0000-000000000000")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("ObjectId"));
        assertNull(lastPath.get());
    }

    @Test
    void confluenceSearchUsesCloudIdFromInstanceConfig() {
        Map<String, Object> searchOp = Map.of(
                "method", "GET",
                "path", "/{cloud_id}/wiki/api/v2/pages",
                "required", List.of("cloud_id"),
                "queryFrom", List.of("limit"));
        OfficialApiNode n = new OfficialApiNode("tid-cf", OfficialApiSpec.fromMap("confluence",
                specMap(base,
                        Map.of("type", "basic", "configKey", "api_token", "userKey", "email"),
                        Map.of("search", searchOp))));
        output(n.handle(request(
                Map.of("email", "ops@example.com", "api_token", "tok", "cloud_id", "site-cloud-uuid"),
                Map.of("operation", "search")), null));
        assertTrue(lastPath.get().contains("/site-cloud-uuid/wiki/api/v2/pages"), lastPath.get());
    }

    @Test
    void confluenceSearchMissingCloudIdMentionsInputOrConfig() {
        Map<String, Object> searchOp = Map.of(
                "method", "GET",
                "path", "/{cloud_id}/wiki/api/v2/pages",
                "required", List.of("cloud_id"));
        OfficialApiNode n = new OfficialApiNode("tid-cf", OfficialApiSpec.fromMap("confluence",
                specMap(base,
                        Map.of("type", "basic", "configKey", "api_token", "userKey", "email"),
                        Map.of("search", searchOp))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("email", "ops@example.com", "api_token", "tok"),
                Map.of("operation", "search")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("configurationParameters"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void basicAuthEncodesUserAndSecret() {
        OfficialApiNode n = node(specMap(base,
                Map.of("type", "basic", "configKey", "password", "userKey", "username"),
                Map.of("ping", Map.of("path", "/ping"))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("username", "u", "password", "p"), Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals("Basic dTpw", lastAuth.get()); // base64("u:p")
    }

    @Test
    void queryAuthAppendsSecretToQueryString() {
        OfficialApiNode n = node(specMap(base,
                Map.of("type", "query", "configKey", "key"),
                Map.of("ping", Map.of("path", "/ping"))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("key", "abc123"), Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals("/ping?key=abc123", lastPath.get());
    }

    @Test
    void blankInputRawUsesConfigRaw() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "send-message", Map.of("method", "POST", "path", "/send",
                        "required", List.of("raw"),
                        "bodyFrom", List.of("raw", "threadId")))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("raw", "Y29uZmln"),
                Map.of("operation", "send-message", "raw", "  ", "threadId", "t1")), null));
        assertEquals("ok", out.get("status"));
        assertEquals("{\"raw\":\"Y29uZmln\",\"threadId\":\"t1\"}", lastBody.get());
    }

    @Test
    void omittedInputRawUsesConfigRaw() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "send-message", Map.of("method", "POST", "path", "/send",
                        "required", List.of("raw"),
                        "bodyFrom", List.of("raw")))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("raw", "Y29uZmln"),
                Map.of("operation", "send-message")), null));
        assertEquals("ok", out.get("status"));
        assertEquals("{\"raw\":\"Y29uZmln\"}", lastBody.get());
    }

    @Test
    void inputRawOverridesConfigRaw() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "send-message", Map.of("method", "POST", "path", "/send",
                        "required", List.of("raw"),
                        "bodyFrom", List.of("raw")))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("raw", "Y29uZmln"),
                Map.of("operation", "send-message", "raw", "aW5wdXQ")), null));
        assertEquals("ok", out.get("status"));
        assertEquals("{\"raw\":\"aW5wdXQ\"}", lastBody.get());
    }

    @Test
    void configRawIsNotSentWhenOperationOmitsIt() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "trash-thread", Map.of("method", "POST", "path", "/threads/{id}/trash",
                        "required", List.of("id")))));
        Map<String, Object> out = output(n.handle(request(
                Map.of("raw", "Y29uZmln"),
                Map.of("operation", "trash-thread", "id", "t1")), null));
        assertEquals("ok", out.get("status"));
        assertFalse(lastBody.get().contains("Y29uZmln"), lastBody.get());
    }

    @Test
    void missingRawOnInputAndConfigFails() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "send-message", Map.of("method", "POST", "path", "/send",
                        "required", List.of("raw"),
                        "bodyFrom", List.of("raw")))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("raw", "  "),
                Map.of("operation", "send-message", "raw", "")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("raw is required for operation send-message"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void postBuildsJsonBodyFromBodyFromSubset() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "create", Map.of("method", "POST", "path", "/items",
                        "bodyFrom", List.of("name")))));
        Map<String, Object> out = output(n.handle(request(null,
                Map.of("operation", "create", "name", "widget", "ignored", "x")), null));
        assertEquals("ok", out.get("status"));
        assertEquals("POST", lastMethod.get());
        assertEquals("{\"name\":\"widget\"}", lastBody.get());
    }

    @Test
    void postWithoutBodyFromSendsWholeInput() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "create", Map.of("method", "POST", "path", "/items"))));
        n.handle(request(null, Map.of("operation", "create", "a", 1)), null);
        assertTrue(lastBody.get().contains("\"a\":1"), lastBody.get());
    }

    @Test
    void nonLlmSpecStillReturnsOutputKey() {
        OfficialApiNode n = node(specMap(base, null, Map.of("ping", Map.of("path", "/ping"))));
        Map<String, Object> result = n.handle(request(null, Map.of()), null);
        assertTrue(result.containsKey("output"), result.keySet().toString());
        assertFalse(result.containsKey("llm_output"));
        assertEquals("ok", output(result).get("status"));
    }

    @Test
    void bedrockCatalogKeyUsesLlmOutputWithoutStreamBlock() {
        OfficialApiSpec spec = OfficialApiSpec.load("aws-bedrock");
        assertNotNull(spec);
        assertEquals("llm_output", OfficialApiNode.outputPortKey(spec, spec.operation("converse")));
        assertEquals("llm_output", OfficialApiNode.outputPortKey(spec, spec.operation("list_foundation_models")));
        OfficialApiNode n = new OfficialApiNode("tid-test", OfficialApiSpec.fromMap("aws-bedrock",
                specMap(base, null, Map.of(
                        "converse", Map.of("method", "POST", "path", "/converse",
                                "required", List.of("messages"),
                                "bodyFrom", List.of("messages"))))));
        Map<String, Object> result = n.handle(request(null,
                Map.of("operation", "converse",
                        "messages", List.of(Map.of("role", "user", "content", "x")))), null);
        assertTrue(result.containsKey("llm_output"), result.keySet().toString());
        assertFalse(result.containsKey("output"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) result.get("llm_output");
        assertEquals("ok", inner.get("status"));
        assertNotNull(inner.get("result"));
    }

    @Test
    void llmShapedHttpErrorUsesLlmOutputNotOutput() {
        respondStatus = 404;
        respondBody = "{\"message\":\"nope\"}";
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/chat",
                        "bodyFrom", List.of("messages"),
                        "stream", Map.of("format", "openai-sse")))));
        StudioQuackError e = thrown(() -> n.handle(request(null,
                Map.of("operation", "chat", "stream", false,
                        "messages", List.of(Map.of("role", "user", "content", "x")))), null));
        assertEquals("HTTP_404", e.errorCode());
        assertEquals("HTTP 404: nope", e.getMessage());
    }

    @Test
    void llmShapedFailUsesLlmOutputNotOutput() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/chat",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("messages"),
                        "stream", Map.of("format", "openai-sse")))));
        StudioQuackError e = thrown(() -> n.handle(request(null,
                Map.of("operation", "chat", "stream", false)), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("messages is required"), e.getMessage());
    }

    @Test
    void siblingOpOnLlmSpecUsesLlmOutputKey() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/messages",
                        "bodyFrom", List.of("messages"),
                        "stream", Map.of("format", "anthropic-sse")),
                "files_list", Map.of("method", "GET", "path", "/v1/files"))));
        Map<String, Object> result = n.handle(request(null,
                Map.of("operation", "files_list")), null);
        assertTrue(result.containsKey("llm_output"), result.keySet().toString());
        assertFalse(result.containsKey("output"));
        assertEquals("/v1/files", lastPath.get());
    }

    @Test
    void successParsesJsonIntoData() {
        respondBody = "{\"items\":[1,2]}";
        OfficialApiNode n = node(specMap(base, null, Map.of("ping", Map.of("path", "/ping"))));
        Map<String, Object> out = output(n.handle(request(null, Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals(200, out.get("http_status"));
        assertNotNull(out.get("data"));
    }

    @Test
    void non2xxYieldsHttpError() {
        respondStatus = 404;
        respondBody = "{\"message\":\"nope\"}";
        OfficialApiNode n = node(specMap(base, null, Map.of("ping", Map.of("path", "/ping"))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of()), null));
        assertEquals("HTTP_404", e.errorCode());
        assertEquals("HTTP 404: nope", e.getMessage());
        assertEquals(404, e.data().get("http_status"));
    }

    @Test
    void transportFailureYieldsErrorPort() {
        OfficialApiNode n = node(specMap("http://127.0.0.1:1", null,
                Map.of("ping", Map.of("path", "/ping"))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of()), null));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertNotNull(e.getMessage());
    }

    @Test
    void templateIdIsExposed() {
        OfficialApiNode n = node(specMap(base, null, Map.of("ping", Map.of("path", "/p"))));
        assertEquals("tid-test", n.templateId());
    }

    @Test
    void streamDefaultOnFansTokensAndReturnsLlmOutputEnvelope() {
        respondContentType = "text/event-stream";
        respondBody = """
                data: {"choices":[{"delta":{"content":"Hi"}}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}

                data: [DONE]
                """;
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/chat",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("messages"),
                        "stream", Map.of("format", "openai-sse")))));
        Map<String, Object> result = n.handle(request(null,
                Map.of("operation", "chat", "messages", List.of(Map.of("role", "user", "content", "x")))), null);
        assertTrue(result.containsKey("llm_output"), result.keySet().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) result.get("llm_output");
        @SuppressWarnings("unchecked")
        Map<String, Object> assistant = (Map<String, Object>) inner.get("result");
        assertEquals("assistant", assistant.get("role"));
        assertEquals("Hi", ((List<Map<String, Object>>) assistant.get("content")).get(0).get("text"));
        assertTrue(lastBody.get().contains("\"stream\":true"), lastBody.get());
        assertEquals("text/event-stream", lastAccept.get());
    }

    @Test
    void streamMergesLlmInputPort() {
        respondContentType = "text/event-stream";
        respondBody = """
                data: {"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}]}

                data: [DONE]
                """;
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/chat",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("messages"),
                        "stream", Map.of("format", "openai-sse")))));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("executionContext", Map.of("currentNode", Map.of("data",
                Map.of("configurationParameters", List.of(
                        Map.of("shortCode", "operation", "value", "chat"))))));
        req.put("inputVariables", Map.of("llm_input",
                Map.of("messages", List.of(Map.of("role", "user", "content", "x")))));
        Map<String, Object> result = n.handle(req, null);
        assertTrue(result.containsKey("llm_output"), result.keySet().toString());
        assertTrue(lastBody.get().contains("messages"), lastBody.get());
    }

    @Test
    void llmInputOperationSelectsImageGenOverConfiguredChat() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiNode n = node(specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "chat", Map.of("method", "POST", "path", "/v1/chat/completions",
                                "required", List.of("messages"),
                                "bodyFrom", List.of("model", "messages")),
                        "image_gen", Map.of("method", "POST", "path", "/v1/images/generations",
                                "required", List.of("prompt"),
                                "bodyFrom", List.of("model", "prompt")))));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("executionContext", Map.of("currentNode", Map.of("data",
                Map.of("configurationParameters", List.of(
                        Map.of("shortCode", "operation", "value", "chat"),
                        Map.of("shortCode", "api_key", "value", "k"))))));
        req.put("inputVariables", Map.of("llm_input",
                Map.of("operation", "image_gen", "prompt", "a red dot", "model", "grok-imagine")));
        n.handle(req, null);
        assertTrue(lastPath.get().contains("/v1/images/generations"), lastPath.get());
        Map<String, Object> body = jsonBody();
        assertEquals("a red dot", body.get("prompt"));
        assertEquals("grok-imagine", body.get("model"));
    }

    @Test
    void cohereEmbedDefaultsInputTypeWhenOnlyTexts() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("cohere", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("embed", Map.of("method", "POST", "path", "/v2/embed",
                        "required", List.of("input_type"),
                        "bodyFrom", List.of("model", "input_type", "texts")))));
        OfficialApiNode n = new OfficialApiNode("tid-cohere-embed-default", spec);
        Map<String, Object> req = request(Map.of("operation", "embed", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input", Map.of("texts", List.of("hello"))));
        n.handle(req, null);
        assertTrue(lastPath.get().contains("/v2/embed"), lastPath.get());
        assertEquals("search_document", jsonBody().get("input_type"));
    }

    @Test
    void cohereInfersRerankWhenOnlyDocumentsBeforeQueryValidation() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("cohere", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "chat", Map.of("method", "POST", "path", "/v2/chat",
                                "required", List.of("messages"), "bodyFrom", List.of("messages")),
                        "rerank", Map.of("method", "POST", "path", "/v2/rerank",
                                "required", List.of("query", "documents"),
                                "bodyFrom", List.of("model", "query", "documents")))));
        OfficialApiNode n = new OfficialApiNode("tid-cohere-rerank-inf", spec);
        Map<String, Object> req = request(Map.of("operation", "chat", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input",
                Map.of("documents", List.of("doc one", "doc two"))));
        StudioQuackError e = thrown(() -> n.handle(req, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("query"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void cohereRerankSwapsChatConfigModelForRerankDefault() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("cohere", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("rerank", Map.of("method", "POST", "path", "/v2/rerank",
                        "required", List.of("query", "documents"),
                        "bodyFrom", List.of("model", "query", "documents")))));
        OfficialApiNode n = new OfficialApiNode("tid-cohere-rerank-model", spec);
        Map<String, Object> req = request(
                Map.of("operation", "rerank", "model", "command-a-plus-05-2026", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input", Map.of(
                "query", "capital of France",
                "documents", List.of("Paris", "London"))));
        n.handle(req, null);
        assertEquals("rerank-v4.0-pro", jsonBody().get("model"));
    }

    @Test
    void cohereInfersEmbedWhenConfigDefaultChatButLlmInputHasEmbedFieldsOnly() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("cohere", specMap(base,
                Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of(
                        "chat", Map.of("method", "POST", "path", "/v2/chat",
                                "required", List.of("messages"),
                                "bodyFrom", List.of("model", "messages", "temperature"),
                                "stream", Map.of("format", "cohere-sse")),
                        "embed", Map.of("method", "POST", "path", "/v2/embed",
                                "required", List.of("input_type"),
                                "bodyFrom", List.of("model", "input_type", "texts", "embedding_types")))));
        OfficialApiNode n = new OfficialApiNode("tid-cohere-embed", spec);
        Map<String, Object> req = request(
                Map.of("operation", "chat", "model", "embed-v4.0", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input",
                Map.of("input_type", "search_document", "text", "hello world")));
        n.handle(req, null);
        assertTrue(lastPath.get().contains("/v2/embed"), lastPath.get());
        Map<String, Object> body = jsonBody();
        assertEquals(List.of("hello world"), body.get("texts"));
        assertEquals("search_document", body.get("input_type"));
    }

    @Test
    void cohereStringTemperatureFromConfigIsNumericInWireBody() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("cohere", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("chat", Map.of("method", "POST", "path", "/v2/chat",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("model", "messages", "temperature")))));
        OfficialApiNode n = new OfficialApiNode("tid-cohere-temp", spec);
        Map<String, Object> req = request(
                Map.of("operation", "chat", "temperature", "0.35", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input",
                Map.of("messages", List.of(Map.of("role", "user", "content", "ping")))));
        n.handle(req, null);
        Map<String, Object> body = jsonBody();
        assertEquals(0.35d, ((Number) body.get("temperature")).doubleValue(), 1e-9);
    }

    @Test
    void vimeoVideoGetStripsVideosPrefixBeforeHttp() {
        respondContentType = "application/json";
        respondBody = "{\"name\":\"clip\"}";
        OfficialApiNode n = new OfficialApiNode("tid-vm", OfficialApiSpec.fromMap("vimeo", specMap(base,
                Map.of("type", "bearer", "configKey", "access_token"),
                Map.of("video_get", Map.of("method", "GET", "path", "/videos/{video_id}",
                        "required", List.of("video_id"))))));
        n.handle(request(Map.of("access_token", "tok"),
                Map.of("operation", "video_get", "video_id", "/videos/258684937")), null);
        assertTrue(lastPath.get().contains("/videos/258684937"), lastPath.get());
        assertFalse(lastPath.get().contains("/videos//videos"), lastPath.get());
    }

    @Test
    void vimeoVideoListRejectsOutOfRangePageBeforeHttp() {
        Map<String, Object> listOp = Map.of("method", "GET", "path", "/me/videos",
                "queryFrom", List.of("page", "per_page"));
        OfficialApiNode n = new OfficialApiNode("tid-vm", OfficialApiSpec.fromMap("vimeo", specMap(base,
                Map.of("type", "bearer", "configKey", "access_token"),
                Map.of("video_list", listOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("access_token", "tok"),
                Map.of("operation", "video_list", "page", 1_000_000)), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("out of range"));
        assertNull(lastPath.get());
    }

    @Test
    void surveyMonkeyResponseListRejectsMalformedSurveyIdBeforeHttp() {
        Map<String, Object> listOp = Map.of("method", "GET", "path", "/surveys/{survey_id}/responses/bulk",
                "required", List.of("survey_id"));
        OfficialApiNode n = new OfficialApiNode("tid-sm", OfficialApiSpec.fromMap("surveymonkey", specMap(base,
                Map.of("type", "bearer", "configKey", "access_token"),
                Map.of("response.list", listOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("access_token", "tok"),
                Map.of("operation", "response.list", "survey_id", "nonexistent-survey-12345")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("does not match"));
        assertNull(lastPath.get());
    }

    @Test
    void sonarQubeQualitygateGetForwardsIdAndNameInQuery() {
        respondContentType = "application/json";
        respondBody = "{\"qualityGate\":{\"id\":1}}";
        Map<String, Object> qgOp = Map.of("method", "GET", "path", "/qualitygates/show",
                "queryFrom", List.of("id", "name"));
        OfficialApiNode n = new OfficialApiNode("tid-sq", OfficialApiSpec.fromMap("sonarqube", specMap(base,
                Map.of("type", "bearer", "configKey", "token"),
                Map.of("qualitygate_get", qgOp))));
        n.handle(request(Map.of("token", "tok"),
                Map.of("operation", "qualitygate_get", "id", "9", "name", "Sonar way")), null);
        String uri = lastPath.get();
        assertNotNull(uri);
        assertTrue(uri.contains("id=9"), uri);
        assertTrue(uri.contains("name=Sonar"), uri);
    }

    @Test
    void sonarQubeIssueSearchForwardsOrganizationAndComponentKeys() {
        respondContentType = "application/json";
        respondBody = "{\"issues\":[]}";
        Map<String, Object> issueOp = Map.of("method", "GET", "path", "/issues/search",
                "queryFrom", List.of("organization", "projects", "componentKeys"));
        OfficialApiNode n = new OfficialApiNode("tid-sq2", OfficialApiSpec.fromMap("sonarqube", specMap(base,
                Map.of("type", "bearer", "configKey", "token"),
                Map.of("issue_search", issueOp))));
        n.handle(request(Map.of("token", "tok"), Map.of(
                "operation", "issue_search",
                "organization", "my-org",
                "componentKeys", "proj-a")), null);
        String uri = lastPath.get();
        assertTrue(uri.contains("organization=my-org"), uri);
        assertTrue(uri.contains("componentKeys=proj-a"), uri);
    }

    @Test
    void shippoAddressValidateNestedPayloadReachesHttpBody() {
        respondContentType = "application/json";
        respondBody = "{\"object_id\":\"addr_1\"}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("shippo", specMap(base,
                Map.of("type", "bearer", "prefix", "ShippoToken ", "configKey", "api_token"),
                Map.of("address_validate", Map.of("method", "POST", "path", "/addresses/",
                        "required", List.of("name", "street1", "city", "zip", "country"),
                        "bodyFrom", List.of("name", "street1", "city", "zip", "country")))));
        OfficialApiNode n = new OfficialApiNode("tid-shippo", spec);
        Map<String, Object> address = Map.of("name", "John Smith", "street1", "Main", "city", "DC",
                "zip", "20500", "country", "US");
        Map<String, Object> req = request(Map.of("operation", "address_validate", "api_token", "k"),
                Map.of("operation", "address_validate", "payload", address));
        n.handle(req, null);
        assertEquals("POST", lastMethod.get());
        Map<String, Object> body = jsonBody();
        assertEquals("John Smith", body.get("name"));
    }

    @Test
    void cohereMergesVirtualInputPortIntoLlmInputForOperationEmbed() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("cohere", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "chat", Map.of("method", "POST", "path", "/v2/chat",
                                "required", List.of("messages"),
                                "bodyFrom", List.of("model", "messages", "temperature")),
                        "embed", Map.of("method", "POST", "path", "/v2/embed",
                                "required", List.of("input_type"),
                                "bodyFrom", List.of("model", "input_type", "texts")))));
        OfficialApiNode n = new OfficialApiNode("tid-cohere-in", spec);
        Map<String, Object> req = request(Map.of("operation", "chat", "api_key", "k"), null);
        req.put("inputVariables", Map.of("input",
                Map.of("operation", "embed", "input_type", "search_document", "texts", List.of("hi"))));
        n.handle(req, null);
        assertTrue(lastPath.get().contains("/v2/embed"), lastPath.get());
    }

    @Test
    void cohereExplicitEmbedNotOverriddenWhenMessagesAlsoPresent() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("cohere", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "chat", Map.of("method", "POST", "path", "/v2/chat",
                                "required", List.of("messages"),
                                "bodyFrom", List.of("messages", "temperature")),
                        "embed", Map.of("method", "POST", "path", "/v2/embed",
                                "required", List.of("input_type"),
                                "bodyFrom", List.of("input_type", "texts")))));
        OfficialApiNode n = new OfficialApiNode("tid-cohere-explicit", spec);
        Map<String, Object> req = request(Map.of("operation", "chat", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input", Map.of(
                "operation", "embed",
                "input_type", "search_document",
                "texts", List.of("doc"),
                "messages", List.of(Map.of("role", "user", "content", "noise")))));
        n.handle(req, null);
        assertTrue(lastPath.get().contains("/v2/embed"), lastPath.get());
    }

    @Test
    void groqOmitsBlankConfigTemperatureFromWireBody() {
        respondContentType = "application/json";
        respondBody = "{\"choices\":[]}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("groq", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("chat", Map.of("method", "POST", "path", "/openai/v1/chat/completions",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("model", "messages", "temperature", "top_p"),
                        "stream", Map.of("format", "openai-sse")))));
        OfficialApiNode n = new OfficialApiNode("tid-groq-temp", spec);
        Map<String, Object> req = request(
                Map.of("operation", "chat", "temperature", "", "top_p", "", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input",
                Map.of("messages", List.of(Map.of("role", "user", "content", "ping")))));
        n.handle(req, null);
        Map<String, Object> body = jsonBody();
        assertFalse(body.containsKey("temperature"), body.toString());
        assertFalse(body.containsKey("top_p"), body.toString());
    }

    @Test
    void groqListModelsViaLlmInputOperation() {
        respondContentType = "application/json";
        respondBody = "{\"data\":[]}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("groq", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "chat", Map.of("method", "POST", "path", "/openai/v1/chat/completions",
                                "required", List.of("messages"),
                                "bodyFrom", List.of("messages", "temperature"),
                                "stream", Map.of("format", "openai-sse")),
                        "list_models", Map.of("method", "GET", "path", "/openai/v1/models",
                                "required", List.of()))));
        OfficialApiNode n = new OfficialApiNode("tid-groq-models", spec);
        Map<String, Object> req = request(Map.of("operation", "chat", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input", Map.of("operation", "list_models")));
        n.handle(req, null);
        assertEquals("GET", lastMethod.get());
        assertTrue(lastPath.get().contains("/openai/v1/models"), lastPath.get());
    }

    @Test
    void groqInfersTtsWhenInputAndVoicePresent() {
        respondContentType = "application/json";
        respondBody = "{}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("groq", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of(
                        "chat", Map.of("method", "POST", "path", "/openai/v1/chat/completions",
                                "required", List.of("messages"),
                                "bodyFrom", List.of("messages")),
                        "tts", Map.of("method", "POST", "path", "/openai/v1/audio/speech",
                                "required", List.of("input", "voice"),
                                "bodyFrom", List.of("input", "voice")))));
        OfficialApiNode n = new OfficialApiNode("tid-groq-tts", spec);
        Map<String, Object> req = request(Map.of("operation", "chat", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input",
                Map.of("input", "Hello", "voice", "austin")));
        n.handle(req, null);
        assertTrue(lastPath.get().contains("/audio/speech"), lastPath.get());
    }

    @Test
    void cohereOmitsBlankConfigTemperatureFromWireBody() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiSpec spec = OfficialApiSpec.fromMap("cohere", specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("chat", Map.of("method", "POST", "path", "/v2/chat",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("messages", "temperature")))));
        OfficialApiNode n = new OfficialApiNode("tid-cohere-omit-temp", spec);
        Map<String, Object> req = request(
                Map.of("operation", "chat", "temperature", "", "api_key", "k"), null);
        req.put("inputVariables", Map.of("llm_input",
                Map.of("messages", List.of(Map.of("role", "user", "content", "ping")))));
        n.handle(req, null);
        Map<String, Object> body = jsonBody();
        assertFalse(body.containsKey("temperature"), body.toString());
    }

    @Test
    void cohereOperationStrictRejectsWrongCasing() {
        Map<String, Object> root = specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("chat", Map.of("method", "POST", "path", "/v2/chat",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("messages"))));
        root.put("operationStrict", true);
        OfficialApiSpec spec = OfficialApiSpec.fromMap("cohere", root);
        assertTrue(spec.operationStrict);
        OfficialApiNode n = new OfficialApiNode("tid-cohere-strict", spec);
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("operation", "Chat", "api_key", "k",
                        "messages", List.of(Map.of("role", "user", "content", "x"))), null), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Unknown operation"));
    }

    @Test
    void chatBodyParsesMessagesWhenPortSendsJsonString() {
        respondContentType = "application/json";
        respondBody = "{\"choices\":[]}";
        OfficialApiNode n = node(specMap(base,
                Map.of("type", "bearer", "configKey", "api_key"),
                Map.of("chat", Map.of("method", "POST", "path", "/v1/chat/completions",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("model", "messages")))));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("executionContext", Map.of("currentNode", Map.of("data",
                Map.of("configurationParameters", List.of(
                        Map.of("shortCode", "api_key", "value", "k"))))));
        String jsonMessages = "[{\"role\":\"user\",\"content\":\"hi\"}]";
        req.put("inputVariables", Map.of("llm_input",
                Map.of("operation", "chat", "model", "grok-4.6", "messages", jsonMessages)));
        n.handle(req, null);
        Map<String, Object> body = jsonBody();
        assertEquals("grok-4.6", body.get("model"));
        assertEquals(List.of(Map.of("role", "user", "content", "hi")), body.get("messages"));
    }

    @Test
    void streamDisabledReturnsLlmOutputKey() {
        respondContentType = "application/json";
        respondBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hi\"}}]}";
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/chat",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("messages"),
                        "stream", Map.of("format", "openai-sse")))));
        Map<String, Object> result = n.handle(request(null,
                Map.of("operation", "chat", "stream", false,
                        "messages", List.of(Map.of("role", "user", "content", "x")))), null);
        assertTrue(result.containsKey("llm_output"), result.keySet().toString());
        assertFalse(result.containsKey("output"), result.keySet().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) result.get("llm_output");
        assertEquals("ok", inner.get("status"));
        assertNotNull(inner.get("result"));
        assertEquals("assistant", ((Map<?, ?>) inner.get("result")).get("role"));
        assertFalse(lastBody.get().contains("\"stream\":true"), lastBody.get());
    }

    @Test
    void streamHttpErrorReturnsV2PayloadOnLlmOutput() {
        respondStatus = 401;
        respondContentType = "application/json";
        respondBody = "{\"error\":{\"message\":\"bad key\"}}";
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/chat",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("messages"),
                        "stream", Map.of("format", "openai-sse")))));
        StudioQuackError e = thrown(() -> n.handle(request(null,
                Map.of("operation", "chat", "messages", List.of(Map.of("role", "user", "content", "x")))), null));
        assertEquals("AUTH_FAILED", e.errorCode());
        assertTrue(e.getMessage().contains("bad key") || e.getMessage().contains("HTTP 401"), e.getMessage());
    }

    @Test
    void streamBodyResolvesModelFromConfigAndAutoEnablesAnthropicThinking() {
        respondContentType = "text/event-stream";
        respondBody = """
                event: content_block_delta
                data: {"type":"content_block_delta","index":1,"delta":{"type":"thinking_delta","thinking":"hmm"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}

                event: message_stop
                data: {"type":"message_stop"}
                """;
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/messages",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("model", "max_tokens", "messages", "temperature", "stream", "thinking"),
                        "stream", Map.of("format", "anthropic-sse")))));
        Map<String, Object> req = request(Map.of(
                "operation", "chat",
                "model", "claude-sonnet-4-5",
                "temperature", 0.7), null);
        req.put("inputVariables", Map.of("llm_input",
                Map.of("messages", List.of(Map.of("role", "user", "content", "x")))));
        Map<String, Object> result = n.handle(req, null);
        assertTrue(result.containsKey("llm_output"), result.keySet().toString());

        // Config underlay: model/max_tokens reached the wire body; thinking auto-enabled
        // for a thinking-capable model; sampling stripped (API rejects it with thinking).
        Map<String, Object> sent = jsonBody();
        assertEquals("claude-sonnet-4-5", sent.get("model"));
        assertEquals(8192, ((Number) sent.get("max_tokens")).intValue());
        Map<?, ?> thinking = (Map<?, ?>) sent.get("thinking");
        assertEquals("enabled", thinking.get("type"));
        assertTrue(((Number) thinking.get("budget_tokens")).longValue() >= 1024);
        assertFalse(sent.containsKey("temperature"), "extended thinking rejects sampling");

        @SuppressWarnings("unchecked")
        Map<String, Object> assistant = (Map<String, Object>) ((Map<String, Object>) result.get("llm_output")).get("result");
        assertEquals("Hi", ((List<Map<String, Object>>) assistant.get("content")).get(0).get("text"));
        assertEquals(List.of(Map.of("type", "thinking", "text", "hmm")), assistant.get("thoughts"));
    }

    @Test
    void streamBodyAutoEnablesGeminiThinkingFromConfigModel() {
        respondContentType = "text/event-stream";
        respondBody = """
                data: {"candidates":[{"content":{"role":"model","parts":[{"text":"pondering","thought":true},{"text":"Hi"}]}}]}

                """;
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1beta/models/{model}:generateContent",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("contents", "messages", "generationConfig"),
                        "stream", Map.of("format", "gemini-sse",
                                "path", "/v1beta/models/{model}:streamGenerateContent")))));
        Map<String, Object> req = request(Map.of(
                "operation", "chat",
                "model", "gemini-2.5-flash"), null);
        req.put("inputVariables", Map.of("llm_input",
                Map.of("messages", List.of(Map.of("role", "user", "content", "x")))));
        Map<String, Object> result = n.handle(req, null);
        assertTrue(result.containsKey("llm_output"), result.keySet().toString());
        assertTrue(lastPath.get().contains("/v1beta/models/gemini-2.5-flash:streamGenerateContent"),
                lastPath.get());

        // model never enters the Gemini body (path param) but still gates thinkingConfig.
        Map<String, Object> sent = jsonBody();
        assertFalse(sent.containsKey("model"));
        Map<?, ?> gen = (Map<?, ?>) sent.get("generationConfig");
        assertEquals(Map.of("includeThoughts", true, "thinkingBudget", -1), gen.get("thinkingConfig"));

        @SuppressWarnings("unchecked")
        Map<String, Object> assistant = (Map<String, Object>) ((Map<String, Object>) result.get("llm_output")).get("result");
        assertEquals(List.of(Map.of("type", "thinking", "text", "pondering")), assistant.get("thoughts"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonBody() {
        return (Map<String, Object>) studioquack.Json.parse(lastBody.get());
    }

    @Test
    void singleShotStreamOpStillMapsGeminiRequestBody() {
        respondContentType = "application/json";
        respondBody = "{\"ok\":true}";
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1beta/models/m:generateContent",
                        "bodyFrom", List.of("messages", "temperature"),
                        "stream", Map.of("format", "gemini-sse")))));
        Map<String, Object> result = n.handle(request(null,
                Map.of("operation", "chat", "stream", false,
                        "messages", List.of(Map.of("role", "user", "content", "hi")),
                        "temperature", 0.2)), null);
        assertTrue(result.containsKey("llm_output"), result.keySet().toString());
        assertFalse(result.containsKey("output"), result.keySet().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) result.get("llm_output");
        assertEquals("ok", inner.get("status"));
        assertNotNull(inner.get("result"));
        assertTrue(lastBody.get().contains("\"contents\""), lastBody.get());
        assertTrue(lastBody.get().contains("\"generationConfig\""), lastBody.get());
        assertFalse(lastBody.get().contains("\"messages\""), lastBody.get());
        assertFalse(lastBody.get().contains("\"stream\":true"), lastBody.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamResponsesSseFansDeltasAndMapsV2Request() {
        respondContentType = "text/event-stream";
        respondBody = """
                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"Hi"}

                event: response.completed
                data: {"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2}}}

                """;
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/agent",
                        "bodyFrom", List.of("messages", "input", "temperature"),
                        "stream", Map.of("format", "responses-sse")))));
        Map<String, Object> result = n.handle(request(null,
                Map.of("operation", "chat",
                        "messages", List.of(Map.of("role", "user", "content", "x")),
                        "temperature", 0.2)), null);
        assertTrue(result.containsKey("llm_output"), result.keySet().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> assistant = (Map<String, Object>) ((Map<String, Object>) result.get("llm_output")).get("result");
        assertEquals("Hi", ((List<Map<String, Object>>) assistant.get("content")).get(0).get("text"));
        assertEquals("completed", assistant.get("finish_reason"));
        assertTrue(lastBody.get().contains("\"input\""), lastBody.get());
        assertTrue(lastBody.get().contains("\"input_text\""), lastBody.get());
        assertFalse(lastBody.get().contains("\"messages\""), lastBody.get());
        assertTrue(lastBody.get().contains("\"stream\":true"), lastBody.get());
        assertEquals("text/event-stream", lastAccept.get());
    }

    @Test
    void extraHeadersTopOpAndStreamMergeAndInterpolate() {
        Map<String, Object> root = specMap(base,
                Map.of("type", "none"),
                Map.of("ping", Map.of(
                        "path", "/ping",
                        "extraHeaders", Map.of("X-Api-Version", "{ver}", "X-Missing", "{nope}"))));
        root.put("extraHeaders", Map.of("X-Api-Version", "top"));
        OfficialApiNode n = node(root);
        n.handle(request(Map.of("ver", "2024-01"), Map.of()), null);
        assertEquals("2024-01", lastXHeader.get(), "op-level {ver} overrides top-level");
    }

    @Test
    void extraHeadersUnresolvableAreOmittedNotBlank() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "ping", Map.of("path", "/ping",
                        "extraHeaders", Map.of("X-Api-Version", "{missing}")))));
        n.handle(request(null, Map.of()), null);
        assertNull(lastXHeader.get());
    }

    @Test
    void extraHeadersDynamicRfc1123UsesClock() {
        Instant frozen = Instant.parse("2015-08-30T12:36:00Z");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("test", specMap(base, null, Map.of(
                "ping", Map.of("path", "/ping",
                        "extraHeaders", Map.of("x-ms-date", "{$now:rfc1123}")))));
        OfficialApiNode n = new OfficialApiNode("tid-test", spec, Clock.fixed(frozen, ZoneOffset.UTC));
        n.handle(request(null, Map.of()), null);
        assertEquals("Sun, 30 Aug 2015 12:36:00 GMT", lastMsDate.get());
    }

    @Test
    void extraHeadersApplyToPost() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "create", Map.of("method", "POST", "path", "/items",
                        "extraHeaders", Map.of("X-Api-Version", "1")))));
        n.handle(request(null, Map.of("operation", "create")), null);
        assertEquals("1", lastXHeader.get());
    }

    @Test
    void streamExtraHeadersStillApplyOnUnaryPath() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "chat", Map.of("method", "POST", "path", "/v1/chat",
                        "bodyFrom", List.of("messages"),
                        "stream", Map.of("format", "openai-sse",
                                "extraHeaders", Map.of("X-Api-Version", "2023-06-01"))))));
        n.handle(request(null, Map.of("operation", "chat", "stream", false,
                "messages", List.of(Map.of("role", "user", "content", "x")))), null);
        assertEquals("2023-06-01", lastXHeader.get());
    }

    @Test
    void opBaseUrlOverridesSpecBaseUrl() {
        OfficialApiNode n = node(specMap("https://example.invalid", null, Map.of(
                "ping", Map.of("path", "/ping", "baseUrl", base))));
        Map<String, Object> out = output(n.handle(request(null, Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertEquals("/ping", lastPath.get());
    }

    @Test
    void basicKeyPositionUsernameEncodesSecretColon() {
        OfficialApiNode n = node(specMap(base,
                Map.of("type", "basic", "configKey", "api_key", "basicKeyPosition", "username"),
                Map.of("ping", Map.of("path", "/ping"))));
        n.handle(request(Map.of("api_key", "KEY"), Map.of()), null);
        assertEquals("Basic " + Base64.getEncoder().encodeToString("KEY:".getBytes(StandardCharsets.UTF_8)),
                lastAuth.get());
    }

    @Test
    void formBodyFormatSendsUrlencoded() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "create", Map.of("method", "POST", "path", "/items",
                        "bodyFormat", "form", "bodyFrom", List.of("q")))));
        n.handle(request(null, Map.of("operation", "create", "q", "a b")), null);
        assertEquals("POST", lastMethod.get());
        assertEquals("q=a%20b", lastBody.get());
        assertTrue(lastContentType.get().startsWith("application/x-www-form-urlencoded"), lastContentType.get());
    }

    @Test
    void rawBodyFormatSendsVerbatimString() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "create", Map.of("method", "POST", "path", "/items",
                        "bodyFormat", "raw", "bodyParam", "payload",
                        "contentType", "text/csv"))));
        n.handle(request(null, Map.of("operation", "create", "payload", "a,b\n1,2")), null);
        assertEquals("a,b\n1,2", lastBody.get());
        assertEquals("text/csv", lastContentType.get());
    }

    @Test
    void emptyRequiredFileListFailsBeforeHttp() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "attachment_add", Map.of("method", "POST", "path", "/attachments",
                        "bodyFormat", "multipart", "bodyFrom", List.of("file"),
                        "required", List.of("file")))));
        StudioQuackError e = thrown(() -> n.handle(request(null, Map.of(
                "operation", "attachment_add", "file", List.of())), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("file is required"));
        assertNull(lastPath.get());
    }

    @Test
    void unstructuredPartitionUsesMultipartBoundary() {
        Map<String, Object> partOp = Map.of(
                "method", "POST",
                "path", "/general/v0/general",
                "required", List.of("files"),
                "bodyFrom", List.of("files", "strategy"),
                "contentType", "multipart/form-data",
                "bodyFormat", "multipart");
        OfficialApiNode n = new OfficialApiNode("tid-unstruct", OfficialApiSpec.fromMap("unstructured", specMap(base,
                Map.of("type", "header", "header", "unstructured-api-key", "configKey", "api_key"),
                Map.of("partition", partOp))));
        n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "partition", "files", "file-ref-1", "strategy", "auto")), null);
        assertEquals("POST", lastMethod.get());
        assertTrue(lastContentType.get().startsWith("multipart/form-data; boundary="), lastContentType.get());
        assertTrue(lastBody.get().contains("name=\"files\""), lastBody.get());
    }

    @Test
    void bitbucketRejectsMissingEmailBeforeHttp() {
        Map<String, Object> repoOp = Map.of(
                "method", "GET",
                "path", "/repositories/{workspace}/{repo_slug}",
                "required", List.of("workspace", "repo_slug"));
        OfficialApiNode n = new OfficialApiNode("tid-bb", OfficialApiSpec.fromMap("bitbucket", specMap(base,
                Map.of("type", "basic", "configKey", "access_token", "userKey", "email"),
                Map.of("repo_get", repoOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("access_token", "tok"),
                Map.of("operation", "repo_get", "workspace", "ws", "repo_slug", "r")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("email is required"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void calComCreateBookingRequiresEventTypeTarget() {
        Map<String, Object> createOp = Map.of(
                "method", "POST",
                "path", "/bookings",
                "bodyFrom", List.of("start", "attendee", "eventTypeId", "bookingFieldsResponses"));
        OfficialApiNode n = new OfficialApiNode("tid-cal", OfficialApiSpec.fromMap("cal-com", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of("create-booking", createOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "create-booking", "start", "2026-12-31T10:00:00Z",
                        "attendee", Map.of("name", "T", "email", "t@example.invalid", "timeZone", "UTC"))), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("eventTypeId"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void calComHttp400ClarifiesMissingBookingField() {
        respondStatus = 400;
        respondBody = "{\"error\":\"responses - {notes}error_required_field,\"}";
        Map<String, Object> createOp = Map.of(
                "method", "POST",
                "path", "/bookings",
                "bodyFrom", List.of("start", "attendee", "eventTypeId"));
        OfficialApiNode n = new OfficialApiNode("tid-cal2", OfficialApiSpec.fromMap("cal-com", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of("create-booking", createOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "create-booking", "eventTypeId", 1, "start", "2026-12-31T10:00:00Z",
                        "attendee", Map.of("name", "T", "email", "t@example.invalid", "timeZone", "UTC"))), null));
        assertEquals("HTTP_400", e.errorCode());
        assertTrue(e.getMessage().contains("bookingFieldsResponses.notes"), e.getMessage());
    }

    @Test
    void calComRescheduleRejectsHollowJsonSuccess() {
        respondBody = "{}";
        Map<String, Object> rescheduleOp = Map.of(
                "method", "POST",
                "path", "/bookings/{uid}/reschedule",
                "required", List.of("uid"),
                "bodyFrom", List.of("start"));
        OfficialApiNode n = new OfficialApiNode("tid-cal3", OfficialApiSpec.fromMap("cal-com", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of("reschedule-booking", rescheduleOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "reschedule-booking", "uid", "fake-uid",
                        "start", "2026-12-31T11:00:00Z")), null));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertTrue(e.getMessage().contains("empty JSON object"), e.getMessage());
    }

    @Test
    void calComRescheduleRequiresStart() {
        Map<String, Object> rescheduleOp = Map.of(
                "method", "POST",
                "path", "/bookings/{uid}/reschedule",
                "required", List.of("uid"),
                "bodyFrom", List.of("start"));
        OfficialApiNode n = new OfficialApiNode("tid-cal4", OfficialApiSpec.fromMap("cal-com", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of("reschedule-booking", rescheduleOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "reschedule-booking", "uid", "fake-uid")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("start is required"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void calComRequiresApiKeyInConfig() {
        Map<String, Object> listOp = Map.of("method", "GET", "path", "/event-types");
        OfficialApiNode n = new OfficialApiNode("tid-cal5", OfficialApiSpec.fromMap("cal-com", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of("list-event-types", listOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of(),
                Map.of("operation", "list-event-types")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("api_key"), e.getMessage());
    }

    @Test
    void calComHttp401ClarifiesApiKey() {
        respondStatus = 401;
        respondBody = "{\"message\":\"Invalid Access Token\"}";
        Map<String, Object> listOp = Map.of("method", "GET", "path", "/event-types");
        OfficialApiNode n = new OfficialApiNode("tid-cal6", OfficialApiSpec.fromMap("cal-com", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of("list-event-types", listOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "bad"),
                Map.of("operation", "list-event-types")), null));
        assertEquals("AUTH_FAILED", e.errorCode());
        assertTrue(e.getMessage().contains("api_key"), e.getMessage());
    }

    @Test
    void calComCreateBookingParsesJsonStringAttendee() {
        Map<String, Object> createOp = Map.of(
                "method", "POST",
                "path", "/bookings",
                "bodyFrom", List.of("start", "attendee", "eventTypeId"));
        OfficialApiNode n = new OfficialApiNode("tid-cal7", OfficialApiSpec.fromMap("cal-com", specMap(base,
                Map.of("type", "header", "header", "Authorization", "prefix", "Bearer ", "configKey", "api_key"),
                Map.of("create-booking", createOp))));
        n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "create-booking", "eventTypeId", 1, "start", "2026-12-31T10:00:00Z",
                        "attendee", "{\"name\":\"T\",\"email\":\"t@example.invalid\",\"timeZone\":\"UTC\"}")), null);
        assertNotNull(lastBody.get());
        assertTrue(lastBody.get().contains("\"name\":\"T\""), lastBody.get());
    }

    @Test
    void unstructuredRejectsInvalidStrategyBeforeHttp() {
        Map<String, Object> partOp = Map.of(
                "method", "POST",
                "path", "/general/v0/general",
                "required", List.of("files"),
                "bodyFrom", List.of("files", "strategy"),
                "bodyFormat", "multipart");
        OfficialApiNode n = new OfficialApiNode("tid-unstruct2", OfficialApiSpec.fromMap("unstructured", specMap(base,
                Map.of("type", "header", "header", "unstructured-api-key", "configKey", "api_key"),
                Map.of("partition", partOp))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "k"),
                Map.of("operation", "partition", "files", "f", "strategy", "banana")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("strategy must be"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void multipartBodyFormatSendsFilePartFromContentBlockList() {
        String b64 = Base64.getEncoder().encodeToString("jira-xlsx".getBytes(StandardCharsets.UTF_8));
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "attachment_add", Map.of("method", "POST", "path", "/issue/KEY-1/attachments",
                        "bodyFormat", "multipart", "bodyFrom", List.of("file"),
                        "required", List.of("file")))));
        n.handle(request(null, Map.of("operation", "attachment_add",
                "file", List.of(Map.of(
                        "kind", "document",
                        "mime_type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "source", "base64",
                        "name", "file.xlsx",
                        "data", b64)))), null);
        assertTrue(lastContentType.get().startsWith("multipart/form-data; boundary="), lastContentType.get());
        String body = lastBody.get();
        assertTrue(body.contains("name=\"file\""), body);
        assertTrue(body.contains("filename=\"file.xlsx\""), body);
        assertTrue(body.contains("jira-xlsx"), body);
        assertFalse(body.contains("\"kind\""), body);
        assertFalse(body.contains("[{"), body);
    }

    @Test
    void multipartBodyFormatSendsFilePartFromDataBase64() {
        String b64 = Base64.getEncoder().encodeToString("file-bytes".getBytes(StandardCharsets.UTF_8));
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "upload", Map.of("method", "POST", "path", "/files",
                        "bodyFormat", "multipart", "bodyFrom", List.of("file")))));
        n.handle(request(null, Map.of("operation", "upload",
                "file", Map.of("dataBase64", b64, "fileName", "a.bin",
                        "contentType", "application/octet-stream"))), null);
        assertTrue(lastContentType.get().startsWith("multipart/form-data; boundary="), lastContentType.get());
        assertTrue(lastBody.get().contains("filename=\"a.bin\""), lastBody.get());
        assertTrue(lastBody.get().contains("file-bytes"), lastBody.get());
    }

    @Test
    void deleteWithBodyFromSendsJsonBody() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "remove", Map.of("method", "DELETE", "path", "/items",
                        "bodyFrom", List.of("id")))));
        n.handle(request(null, Map.of("operation", "remove", "id", "abc")), null);
        assertEquals("DELETE", lastMethod.get());
        assertEquals("{\"id\":\"abc\"}", lastBody.get());
    }

    @Test
    void deleteWithoutBodyFromStaysBodyLess() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "remove", Map.of("method", "DELETE", "path", "/items"))));
        n.handle(request(null, Map.of("operation", "remove")), null);
        assertEquals("DELETE", lastMethod.get());
        assertEquals("", lastBody.get());
    }

    @Test
    void getStaysBodyLessEvenWithBodyFrom() {
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "ping", Map.of("method", "GET", "path", "/ping", "bodyFrom", List.of("q")))));
        n.handle(request(null, Map.of("q", "x")), null);
        assertEquals("GET", lastMethod.get());
        assertEquals("", lastBody.get());
    }

    @Test
    void awsSigv4StampsAuthorizationAndAmzHeaders() {
        Instant frozen = Instant.parse("2015-08-30T12:36:00Z");
        Map<String, Object> root = specMap(base,
                Map.of("type", "aws-sigv4", "configKey", "access_key_id",
                        "secretKeyConfigKey", "secret_access_key"),
                Map.of("ping", Map.of("path", "/ping")));
        root.put("sigv4", Map.of("service", "execute-api", "regionConfigKey", "region",
                "sessionTokenConfigKey", "session_token"));
        OfficialApiSpec spec = OfficialApiSpec.fromMap("test", root);
        OfficialApiNode n = new OfficialApiNode("tid-test", spec, Clock.fixed(frozen, ZoneOffset.UTC));
        Map<String, Object> out = output(n.handle(request(
                Map.of("access_key_id", "AKIDEXAMPLE",
                        "secret_access_key", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
                        "region", "us-east-1",
                        "session_token", "tok"),
                Map.of()), null));
        assertEquals("ok", out.get("status"));
        assertTrue(lastAuth.get().startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/"), lastAuth.get());
        assertEquals("20150830T123600Z", lastAmzDate.get());
        assertEquals(AwsSigV4.EMPTY_PAYLOAD_HASH, lastAmzSha.get());
        assertEquals("tok", lastAmzToken.get());
    }

    @Test
    void multipartUrlFetchesBytesViaDataSupport() {
        respondBody = "from-url";
        respondContentType = "text/plain";
        OfficialApiNode n = node(specMap(base, null, Map.of(
                "upload", Map.of("method", "POST", "path", "/files",
                        "bodyFormat", "multipart", "bodyFrom", List.of("file")))));
        n.handle(request(null, Map.of("operation", "upload",
                "file", Map.of("url", base + "/src.bin", "fileName", "src.bin"))), null);
        assertTrue(lastBody.get().contains("from-url"), lastBody.get());
        assertTrue(lastBody.get().contains("filename=\"src.bin\""), lastBody.get());
    }

    @Test
    void jinaDeepResearchCoercesStringMessages() {
        Map<String, Object> auth = Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ",
                "configKey", "api_key");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("jina-ai", specMap(base, auth, Map.of(
                "deep_research", Map.of("method", "POST", "path", "/v1/chat/completions",
                        "required", List.of("messages"),
                        "bodyFrom", List.of("model", "messages"),
                        "bodyDefaults", Map.of("model", "jina-deepsearch-v1")))));
        OfficialApiNode n = new OfficialApiNode("tid-jina", spec);
        n.handle(request(Map.of("api_key", "k"), Map.of(
                "operation", "deep_research",
                "messages", List.of("What is 2+2?"))), null);
        assertTrue(lastBody.get().contains("\"role\":\"user\""), lastBody.get());
        assertTrue(lastBody.get().contains("What is 2+2?"), lastBody.get());
        assertTrue(lastBody.get().contains("jina-deepsearch-v1"), lastBody.get());
    }

    @Test
    void jinaEmbedMapsEmbedInputAlias() {
        Map<String, Object> auth = Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ",
                "configKey", "api_key");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("jina-ai", specMap(base, auth, Map.of(
                "embed", Map.of("method", "POST", "path", "/v1/embeddings",
                        "required", List.of("input"),
                        "bodyFrom", List.of("input")))));
        OfficialApiNode n = new OfficialApiNode("tid-jina-embed", spec);
        n.handle(request(Map.of("api_key", "k"), Map.of(
                "operation", "embed", "embed_input", "hello")), null);
        assertEquals("{\"input\":\"hello\"}", lastBody.get());
    }

    @Test
    void jiraUserGetUsesCloudIdFromInstanceConfig() {
        Map<String, Object> userOp = Map.of(
                "method", "GET",
                "path", "/{cloud_id}/rest/api/3/myself",
                "required", List.of("cloud_id"));
        OfficialApiNode n = new OfficialApiNode("tid-jira", OfficialApiSpec.fromMap("jira",
                specMap(base,
                        Map.of("type", "basic", "configKey", "api_token", "userKey", "email"),
                        Map.of("user_get", userOp))));
        n.handle(request(
                Map.of("email", "ops@example.com", "api_token", "tok", "cloud_id", "site-cloud-uuid"),
                Map.of("operation", "user_get")), null);
        assertTrue(lastPath.get().contains("/site-cloud-uuid/rest/api/3/myself"), lastPath.get());
    }

    @Test
    void jiraIssueCreateMapsSubjectToSummary() {
        Map<String, Object> createOp = Map.of(
                "method", "POST",
                "path", "/{cloud_id}/rest/api/3/issue",
                "required", List.of("cloud_id"),
                "bodyFrom", List.of("fields", "summary", "project_key"),
                "bodyMap", Map.of("summary", "fields.summary", "project_key", "fields.project.key"));
        OfficialApiNode n = new OfficialApiNode("tid-jira-create", OfficialApiSpec.fromMap("jira",
                specMap(base,
                        Map.of("type", "basic", "configKey", "api_token", "userKey", "email"),
                        Map.of("issue_create", createOp))));
        n.handle(request(
                Map.of("email", "ops@example.com", "api_token", "tok", "cloud_id", "cid"),
                Map.of("operation", "issue_create", "subject", "Test title", "project_key", "ENG")), null);
        assertTrue(lastBody.get().contains("Test title"), lastBody.get());
        assertTrue(lastBody.get().contains("\"fields\""), lastBody.get());
    }

    @Test
    void jiraBare404SurfacesCloudIdHint() {
        respondStatus = 404;
        respondBody = "";
        Map<String, Object> userOp = Map.of(
                "method", "GET",
                "path", "/{cloud_id}/rest/api/3/myself",
                "required", List.of("cloud_id"));
        OfficialApiNode n = new OfficialApiNode("tid-jira-404", OfficialApiSpec.fromMap("jira",
                specMap(base,
                        Map.of("type", "basic", "configKey", "api_token", "userKey", "email"),
                        Map.of("user_get", userOp))));
        StudioQuackError e = thrown(() -> n.handle(request(
                Map.of("email", "ops@example.com", "api_token", "tok", "cloud_id", "bad-id"),
                Map.of("operation", "user_get")), null));
        assertTrue(e.getMessage().contains("cloud_id"), e.getMessage());
    }

    @Test
    void jinaSearchRejectsNonPositiveNum() {
        Map<String, Object> auth = Map.of("type", "bearer", "header", "Authorization", "prefix", "Bearer ",
                "configKey", "api_key");
        OfficialApiSpec spec = OfficialApiSpec.fromMap("jina-ai", specMap(base, auth, Map.of(
                "search", Map.of("method", "POST", "path", "/",
                        "required", List.of("q"),
                        "bodyFrom", List.of("q", "num")))));
        OfficialApiNode n = new OfficialApiNode("tid-jina-search", spec);
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> n.handle(
                request(Map.of("api_key", "k"), Map.of("operation", "search", "q", "test", "num", 0)), null));
        assertTrue(e.getMessage().contains("num must be a positive integer"), e.getMessage());
    }

    @Test
    void amplitudeIdentifySendsFormArrayAndApiKey() throws Exception {
        OfficialApiNode n = amplitudeNode(Map.of(
                "identify", Map.of(
                        "method", "POST",
                        "path", "/identify",
                        "required", List.of("identification"),
                        "bodyFormat", "form",
                        "bodyFrom", List.of("api_key", "identification"))));
        n.handle(request(Map.of("api_key", "KEY"), Map.of(
                "operation", "identify",
                "identification", Map.of("user_id", "u1", "user_properties", Map.of("plan", "test")))), null);
        assertEquals("POST", lastMethod.get());
        assertTrue(lastPath.get().contains("api_key=KEY"), lastPath.get());
        assertTrue(lastContentType.get().startsWith("application/x-www-form-urlencoded"), lastContentType.get());
        String decoded = java.net.URLDecoder.decode(lastBody.get(), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("api_key=KEY"), decoded);
        assertTrue(decoded.contains("identification=[{"), decoded);
        assertTrue(decoded.contains("\"user_id\":\"u1\""), decoded);
    }

    @Test
    void amplitudeGroupIdentifyRejectsUserOnlyPayload() {
        OfficialApiNode n = amplitudeNode(Map.of(
                "group.identify", Map.of(
                        "method", "POST",
                        "path", "/groupidentify",
                        "required", List.of("identification"),
                        "bodyFormat", "form",
                        "bodyFrom", List.of("api_key", "identification"))));
        StudioQuackError e = thrown(() -> n.handle(request(Map.of("api_key", "KEY"), Map.of(
                "operation", "group.identify",
                "identification", Map.of("group_type", "org"))), null));
        assertTrue(e.getMessage().contains("group_type and group_value"), e.getMessage());
        assertNull(lastPath.get());
    }

    @Test
    void lemlistUnsubscribeAndEnrichHitLiveRoutes() {
        OfficialApiSpec spec = OfficialApiSpec.load("lemlist");
        assertNotNull(spec);
        OfficialApiNode n = new OfficialApiNode("tid-lemlist", spec);

        Map<String, Object> domain = OfficialApiNode.mergeLemlistInputs("lemlist",
                spec.operation("unsubscribe.add"), new LinkedHashMap<>(Map.of("domain", "example.com")));
        assertEquals("https://api.lemlist.com/api/v2/unsubscribes/variables/example.com",
                n.buildUrl(spec.operation("unsubscribe.add"), domain, Map.of(), null));

        Map<String, Object> email = OfficialApiNode.mergeLemlistInputs("lemlist",
                spec.operation("unsubscribe.delete"),
                new LinkedHashMap<>(Map.of("email", "alex.doe@example.com")));
        assertEquals("https://api.lemlist.com/api/v2/unsubscribes/variables/alex.doe%40example.com",
                n.buildUrl(spec.operation("unsubscribe.delete"), email, Map.of(), null));

        assertEquals("https://api.lemlist.com/api/v2/unsubscribes/variables",
                n.buildUrl(spec.operation("unsubscribe.list"), Map.of(), Map.of(), null));

        assertEquals("https://api.lemlist.com/api/campaigns/cam_1/leads/alex.doe%40example.com",
                n.buildUrl(spec.operation("lead.unsubscribe"),
                        Map.of("campaign_id", "cam_1", "email", "alex.doe@example.com"), Map.of(), null));

        Map<String, Object> person = OfficialApiNode.mergeLemlistInputs("lemlist",
                spec.operation("enrichment.enrichPerson"),
                new LinkedHashMap<>(Map.of("findLinkedin", true, "linkedinUrl", "https://www.linkedin.com/in/jane")));
        String enrich = n.buildUrl(spec.operation("enrichment.enrichPerson"), person, Map.of(), null);
        assertTrue(enrich.startsWith("https://api.lemlist.com/api/enrich?"), enrich);
        assertTrue(enrich.contains("linkedinEnrichment=true"), enrich);
        assertTrue(enrich.contains("linkedinUrl="), enrich);
        assertFalse(enrich.contains("findLinkedin"), enrich);
    }

    @Test
    void mixpanelTrackPostsAnEventArrayAndEngagePostsAProfileOp() {
        OfficialApiSpec spec = OfficialApiSpec.load("mixpanel");
        assertNotNull(spec);

        Map<String, Object> shorthand = OfficialApiNode.mergeMixpanelInputs("mixpanel",
                spec.operation("track"), new LinkedHashMap<>(Map.of(
                        "event", "Signup",
                        "distinct_id", "u1",
                        "properties", Map.of("test_marker", "wave1"))));
        String track = body(spec.operation("track"), shorthand);
        assertTrue(track.startsWith("[{"), track);
        assertTrue(track.contains("\"event\":\"Signup\""), track);
        assertTrue(track.contains("\"distinct_id\":\"u1\""), track);
        assertFalse(track.contains("\"events\""), track);

        Map<String, Object> strings = OfficialApiNode.mergeMixpanelInputs("mixpanel",
                spec.operation("track"), new LinkedHashMap<>(Map.of(
                        "events", List.of("{\"event\":\"Click\",\"distinct_id\":\"u2\"}"))));
        String parsed = body(spec.operation("track"), strings);
        assertTrue(parsed.contains("\"event\":\"Click\""), parsed);
        assertTrue(parsed.contains("\"distinct_id\":\"u2\""), parsed);
        assertFalse(parsed.contains("\\\""), parsed);

        Map<String, Object> engage = OfficialApiNode.mergeMixpanelInputs("mixpanel",
                spec.operation("engage.set"), new LinkedHashMap<>(Map.of(
                        "distinct_id", "u1",
                        "properties", Map.of("plan", "pro", "token", "proj"))));
        String profile = body(spec.operation("engage.set"), engage);
        assertTrue(profile.contains("\"$distinct_id\":\"u1\""), profile);
        assertTrue(profile.contains("\"$token\":\"proj\""), profile);
        assertTrue(profile.contains("\"$set\":{\"plan\":\"pro\"}"), profile);
        assertFalse(profile.contains("\"properties\""), profile);

        StudioQuackError rejected = assertThrows(StudioQuackError.class, () ->
                OfficialApiNode.rejectMixpanelIngestFailure(spec.operation("track"),
                        Map.of("error", "data, missing or empty", "status", 0)));
        assertTrue(rejected.getMessage().contains("data, missing or empty"), rejected.getMessage());
    }

    private static String body(OfficialApiSpec.Operation op, Map<String, Object> in) {
        OfficialApiBody.Prepared prepared = OfficialApiBody.encode(op, in, 30);
        assertNull(prepared.error());
        return new String(prepared.bytes(), StandardCharsets.UTF_8);
    }

    @Test
    void amplitudeTrackPutsApiKeyInJsonBody() {
        OfficialApiNode n = amplitudeNode(Map.of(
                "event.track", Map.of(
                        "method", "POST",
                        "path", "/2/httpapi",
                        "required", List.of("events"),
                        "bodyFrom", List.of("api_key", "events"))));
        n.handle(request(Map.of("api_key", "KEY"), Map.of(
                "operation", "event.track",
                "events", List.of(Map.of("event_type", "signup", "user_id", "u1")))), null);
        assertTrue(lastBody.get().contains("\"api_key\":\"KEY\""), lastBody.get());
        assertTrue(lastBody.get().contains("\"event_type\":\"signup\""), lastBody.get());
    }

    @Test
    void amplitudeExportRejectsMalformedHourAndInvertedSpan() {
        OfficialApiNode n = amplitudeNode(Map.of(
                "export.download", Map.of(
                        "method", "GET",
                        "path", "/api/2/export",
                        "required", List.of("start", "end"),
                        "queryFrom", List.of("start", "end"),
                        "baseUrl", base)));
        StudioQuackError badHour = thrown(() -> n.handle(request(Map.of("api_key", "KEY"), Map.of(
                "operation", "export.download", "start", "2024-01-01", "end", "20240102T00")), null));
        assertTrue(badHour.getMessage().contains("YYYYMMDDTHH"), badHour.getMessage());
        StudioQuackError inverted = thrown(() -> n.handle(request(Map.of("api_key", "KEY"), Map.of(
                "operation", "export.download", "start", "20240601T00", "end", "20240101T00")), null));
        assertTrue(inverted.getMessage().contains("same hour"), inverted.getMessage());
        StudioQuackError span = thrown(() -> n.handle(request(Map.of("api_key", "KEY"), Map.of(
                "operation", "export.download", "start", "20230101T00", "end", "20240601T00")), null));
        assertTrue(span.getMessage().contains("365"), span.getMessage());
        assertNull(lastPath.get());
    }

    private OfficialApiNode amplitudeNode(Map<String, Object> ops) {
        return new OfficialApiNode("tid-amplitude", OfficialApiSpec.fromMap("amplitude",
                specMap(base, Map.of("type", "query", "configKey", "api_key"), ops)));
    }
}
