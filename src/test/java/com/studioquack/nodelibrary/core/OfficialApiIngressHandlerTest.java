package com.studioquack.nodelibrary.core;

import com.studioquack.nodelibrary.nodes.officialapi.GithubNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive verification of {@link OfficialApiIngressHandler}, {@link OfficialApiNode}
 * ingress &amp; {@code send-and-wait} Phase 1 execution, {@link GithubNode} webhook ingress,
 * {@link StudioQuackHelper} ingress SDK methods, and {@link SelectOptionSyntax}.
 *
 * <p>Covers all 10 flagship providers (WhatsApp, Microsoft Teams, Slack, Telegram, Stripe,
 * Shopify, GitHub, GitLab, Twilio, Jira) plus Discord Ed25519, Asana header echo, Zoom
 * URL validation, Okta challenge, batch fan-out cap (100), and generic JSON pass-through.
 */
class OfficialApiIngressHandlerTest {

    private static final Instant FIXED_NOW = Instant.ofEpochSecond(1_760_000_000L);
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private HttpServer server;
    private String localBase;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile String outboundReply = "{\"ok\":true}";

    @BeforeEach
    void startLoopback() throws IOException {
        lastPath.set(null);
        lastBody.set(null);
        outboundReply = "{\"ok\":true}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastPath.set(ex.getRequestURI().toString());
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = outboundReply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        localBase = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopLoopback() {
        server.stop(0);
    }

    // =========================================================================
    // 1. WhatsApp (Flagship): GET challenge + valid/invalid HMAC + batched messages + send-and-wait
    // =========================================================================

    @Test
    void whatsapp_getChallenge_validAndInvalidVerifyToken() {
        OfficialApiNode node = loadNode("whatsapp");

        Map<String, Object> validReq = ingressReq(
                Map.of("operation", "listen", "verify_token", "my-meta-verify-token"),
                webhookReq("GET", Map.of(
                        "hub.mode", "subscribe",
                        "hub.verify_token", "my-meta-verify-token",
                        "hub.challenge", "challenge_98765"), Map.of(), "", null));
        Map<String, Object> res = node.handle(validReq, null);
        Map<String, Object> ch = Json.asMap(res.get("__sq_webhook_challenge"));
        assertEquals(200, ch.get("status"));
        assertEquals("text/plain", ch.get("contentType"));
        assertEquals("challenge_98765", ch.get("body"));

        Map<String, Object> badReq = ingressReq(
                Map.of("operation", "listen", "verify_token", "my-meta-verify-token"),
                webhookReq("GET", Map.of(
                        "hub.mode", "subscribe",
                        "hub.verify_token", "wrong-token",
                        "hub.challenge", "challenge_98765"), Map.of(), "", null));
        Map<String, Object> badRes = node.handle(badReq, null);
        Map<String, Object> rej = Json.asMap(badRes.get("__sq_webhook_reject"));
        assertEquals(403, rej.get("status"));
        assertEquals("invalid_verify_token", rej.get("reason"));
    }

    @Test
    void whatsapp_postBatchedMessages_validAndInvalidHmac() {
        OfficialApiNode node = loadNode("whatsapp");
        String secret = "wa-app-secret-01";
        String rawBody = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "waba_1",
                    "changes": [
                      {"field": "account_alerts", "value": {"alert": "ignored"}},
                      {
                        "field": "messages",
                        "value": {
                          "messaging_product": "whatsapp",
                          "messages": [
                            {"from": "15551112222", "id": "wamid.001", "timestamp": "1760000001", "type": "text", "text": {"body": "Hello 1"}},
                            {"from": "15553334444", "id": "wamid.002", "timestamp": "1760000002", "type": "text", "text": {"body": "Hello 2"}}
                          ]
                        }
                      }
                    ]
                  }]
                }""";
        String sig = "sha256=" + hmacHex("HmacSHA256", secret, rawBody.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "listen", "app_secret", secret),
                webhookReq("POST", Map.of(), Map.of("x-hub-signature-256", sig, "content-type", "application/json"),
                        rawBody, Json.parse(rawBody))), null);
        List<Object> events = Json.asList(okRes.get("__sq_webhook_events"));
        assertEquals(2, events.size());
        Map<String, Object> e1 = Json.asMap(events.get(0));
        assertEquals("wamid.001", e1.get("idempotencyKey"));
        assertEquals("15551112222", e1.get("correlationKey"));
        Map<String, Object> p1 = Json.asMap(e1.get("payload"));
        assertEquals("whatsapp", p1.get("provider"));
        assertEquals("Hello 1", p1.get("text"));
        Map<String, Object> e2 = Json.asMap(events.get(1));
        assertEquals("wamid.002", e2.get("idempotencyKey"));
        assertEquals("15553334444", e2.get("correlationKey"));

        // Invalid signature must return 401 reject
        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "listen", "app_secret", secret),
                webhookReq("POST", Map.of(), Map.of("x-hub-signature-256", "sha256=deadbeef"),
                        rawBody, Json.parse(rawBody))), null);
        Map<String, Object> rej = Json.asMap(badRes.get("__sq_webhook_reject"));
        assertEquals(401, rej.get("status"));
        assertEquals("invalid_signature", rej.get("reason"));
    }

    @Test
    void whatsapp_sendAndWait_phase1_delegatesToSendMessageAndEmitsCorrelationKey() {
        outboundReply = "{\"messaging_product\":\"whatsapp\",\"contacts\":[{\"wa_id\":\"15551234567\"}],\"messages\":[{\"id\":\"wamid.out1\"}]}";
        OfficialApiNode node = loadLocalizedNode("whatsapp");
        StudioQuackHelper helper = new StudioQuackHelper(Map.of());

        Map<String, Object> req = normalReq(
                Map.of("operation", "send-and-wait", "access_token", "meta-tok", "phone_number_id", "999001"),
                Map.of("to", "15551234567", "type", "text", "text", Map.of("body", "Approve order?")));
        req.put("activeSelectRole", "send_and_wait");

        Map<String, Object> res = node.handle(req, helper);
        assertEquals("/999001/messages", lastPath.get());
        assertEquals("15551234567", res.get("__sq_correlation_key"));
        Map<String, Object> out = Json.asMap(res.get("output"));
        assertEquals("ok", out.get("status"));
        assertEquals(200, out.get("http_status"));
    }

    // =========================================================================
    // 2. Microsoft Teams (Flagship): validationToken + clientState + extraction + send-and-wait
    // =========================================================================

    @Test
    void microsoftTeams_validationToken_clientState_payloadAndSendAndWait() {
        OfficialApiNode node = loadNode("microsoft-teams");

        // Handshake (Graph sends POST ?validationToken=...)
        Map<String, Object> hsRes = node.handle(ingressReq(
                Map.of("operation", "listen", "client_state", "teams-state-secret"),
                webhookReq("POST", Map.of("validationToken", "graph-token-abc"), Map.of(), "", null)), null);
        Map<String, Object> ch = Json.asMap(hsRes.get("__sq_webhook_challenge"));
        assertEquals(200, ch.get("status"));
        assertEquals("text/plain", ch.get("contentType"));
        assertEquals("graph-token-abc", ch.get("body"));

        // Valid notification with matching clientState
        String rawBody = """
                {
                  "value": [{
                    "subscriptionId": "sub-1",
                    "changeType": "created",
                    "clientState": "teams-state-secret",
                    "tenantId": "tenant-1",
                    "resource": "chats('19:chat-42')/messages('msg-99')",
                    "resourceData": {
                      "id": "msg-99",
                      "chatId": "19:chat-42"
                    }
                  }]
                }""";
        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "listen", "client_state", "teams-state-secret"),
                webhookReq("POST", Map.of(), Map.of("content-type", "application/json"),
                        rawBody, Json.parse(rawBody))), null);
        List<Object> events = Json.asList(okRes.get("__sq_webhook_events"));
        assertEquals(1, events.size());
        Map<String, Object> ev = Json.asMap(events.get(0));
        assertEquals("msg-99", ev.get("idempotencyKey"));
        assertEquals("19:chat-42", ev.get("correlationKey"));

        // Mismatched clientState -> 401 reject
        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "listen", "client_state", "different-secret"),
                webhookReq("POST", Map.of(), Map.of("content-type", "application/json"),
                        rawBody, Json.parse(rawBody))), null);
        assertEquals(401, Json.asMap(badRes.get("__sq_webhook_reject")).get("status"));

        // Send-and-wait Phase 1 (fallback to create-chat-message when chat_id set & channel_id blank)
        outboundReply = "{\"id\":\"m-sent-1\",\"chatId\":\"19:chat-42\"}";
        OfficialApiNode localNode = loadLocalizedNode("microsoft-teams");
        Map<String, Object> swRes = localNode.handle(normalReq(
                Map.of("operation", "send-and-wait", "access_token", "graph-tok"),
                Map.of("chat_id", "19:chat-42", "body", Map.of("contentType", "text", "content", "Confirm?"))), null);
        assertEquals("/chats/19%3Achat-42/messages", lastPath.get());
        assertEquals("19:chat-42", swRes.get("__sq_correlation_key"));
    }

    // =========================================================================
    // 3. Slack (Flagship): url_verification + v0:ts:rawBody HMAC + send-and-wait
    // =========================================================================

    @Test
    void slack_urlVerification_signedEvents_staleTimestampAndSendAndWait() {
        OfficialApiNode node = loadNode("slack");
        String secret = "slack-signing-secret";
        String ts = String.valueOf(FIXED_NOW.getEpochSecond());

        // Signed url_verification handshake
        String challengeBody = "{\"type\":\"url_verification\",\"challenge\":\"slack-chal-123\"}";
        String chalSig = "v0=" + hmacHex("HmacSHA256", secret,
                ("v0:" + ts + ":" + challengeBody).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> hsRes = node.handle(ingressReq(
                Map.of("operation", "listen", "signing_secret", secret),
                webhookReq("POST", Map.of(), Map.of(
                        "x-slack-request-timestamp", ts,
                        "x-slack-signature", chalSig,
                        "content-type", "application/json"), challengeBody, Json.parse(challengeBody))), null);
        Map<String, Object> ch = Json.asMap(hsRes.get("__sq_webhook_challenge"));
        assertEquals(200, ch.get("status"));
        assertEquals("application/json", ch.get("contentType"));
        assertEquals("slack-chal-123", Json.asMap(Json.parse(String.valueOf(ch.get("body")))).get("challenge"));

        // Valid signed event callback
        String eventBody = "{\"type\":\"event_callback\",\"event_id\":\"Ev01\",\"team_id\":\"T01\",\"event\":{\"type\":\"message\",\"channel\":\"C01\",\"user\":\"U01\",\"text\":\"Approved\",\"ts\":\"1760000000.001\",\"thread_ts\":\"1759999999.000\"}}";
        String evSig = "v0=" + hmacHex("HmacSHA256", secret,
                ("v0:" + ts + ":" + eventBody).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "listen", "signing_secret", secret),
                webhookReq("POST", Map.of(), Map.of(
                        "x-slack-request-timestamp", ts,
                        "x-slack-signature", evSig), eventBody, Json.parse(eventBody))), null);
        Map<String, Object> ev = Json.asMap(Json.asList(okRes.get("__sq_webhook_events")).get(0));
        assertEquals("Ev01", ev.get("idempotencyKey"));
        assertEquals("1759999999.000", ev.get("correlationKey"));

        // Stale timestamp (replay protection) -> 401
        String staleTs = String.valueOf(FIXED_NOW.getEpochSecond() - 600);
        String staleSig = "v0=" + hmacHex("HmacSHA256", secret,
                ("v0:" + staleTs + ":" + eventBody).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> staleRes = node.handle(ingressReq(
                Map.of("operation", "listen", "signing_secret", secret),
                webhookReq("POST", Map.of(), Map.of(
                        "x-slack-request-timestamp", staleTs,
                        "x-slack-signature", staleSig), eventBody, Json.parse(eventBody))), null);
        assertEquals("timestamp_out_of_tolerance", Json.asMap(staleRes.get("__sq_webhook_reject")).get("reason"));

        // Send-and-wait Phase 1 -> calls /chat.postMessage and emits thread/ts correlation key
        outboundReply = "{\"ok\":true,\"channel\":\"C01\",\"ts\":\"1760000010.000\"}";
        OfficialApiNode localNode = loadLocalizedNode("slack");
        Map<String, Object> swRes = localNode.handle(normalReq(
                Map.of("operation", "send-and-wait", "bot_token", "xoxb-test"),
                Map.of("channel", "C01", "text", "Reply in thread")), null);
        assertEquals("/chat.postMessage", lastPath.get());
        assertEquals("1760000010.000", swRes.get("__sq_correlation_key"));
    }

    // =========================================================================
    // 4. Telegram (Flagship Group B): X-Telegram-Bot-Api-Secret-Token
    // =========================================================================

    @Test
    void telegram_headerSecretToken_validAndInvalid() {
        OfficialApiNode node = loadNode("telegram");
        String body = "{\"update_id\":42001,\"message\":{\"message_id\":7,\"chat\":{\"id\":987654},\"from\":{\"id\":123,\"username\":\"alice\"},\"text\":\"/start\"}}";

        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "listen", "secret_token", "tg-secret-xyz"),
                webhookReq("POST", Map.of(), Map.of("X-Telegram-Bot-Api-Secret-Token", "tg-secret-xyz"),
                        body, Json.parse(body))), null);
        Map<String, Object> ev = Json.asMap(Json.asList(okRes.get("__sq_webhook_events")).get(0));
        assertEquals("42001", ev.get("idempotencyKey"));
        assertEquals("987654", ev.get("correlationKey"));
        assertEquals("/start", Json.asMap(ev.get("payload")).get("text"));

        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "listen", "secret_token", "tg-secret-xyz"),
                webhookReq("POST", Map.of(), Map.of("X-Telegram-Bot-Api-Secret-Token", "wrong"),
                        body, Json.parse(body))), null);
        assertEquals(401, Json.asMap(badRes.get("__sq_webhook_reject")).get("status"));
    }

    // =========================================================================
    // 5. Stripe (Flagship): Stripe-Signature t={ts},v1={sig}
    // =========================================================================

    @Test
    void stripe_timestampedHmac_validAndInvalid() {
        OfficialApiNode node = loadNode("stripe");
        String secret = "whsec_test_stripe";
        String ts = String.valueOf(FIXED_NOW.getEpochSecond());
        String body = "{\"id\":\"evt_123\",\"type\":\"payment_intent.succeeded\",\"livemode\":false,\"data\":{\"object\":{\"id\":\"pi_999\",\"customer\":\"cus_888\"}}}";
        String v1 = hmacHex("HmacSHA256", secret, (ts + "." + body).getBytes(StandardCharsets.UTF_8));
        String header = "t=" + ts + ",v1=" + v1;

        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "trigger", "webhook_signing_secret", secret),
                webhookReq("POST", Map.of(), Map.of("Stripe-Signature", header), body, Json.parse(body))), null);
        Map<String, Object> ev = Json.asMap(Json.asList(okRes.get("__sq_webhook_events")).get(0));
        assertEquals("evt_123", ev.get("idempotencyKey"));
        assertEquals("pi_999", ev.get("correlationKey"));
        assertEquals("payment_intent.succeeded", Json.asMap(ev.get("payload")).get("event"));

        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "trigger", "webhook_signing_secret", secret),
                webhookReq("POST", Map.of(), Map.of("Stripe-Signature", "t=" + ts + ",v1=0000"),
                        body, Json.parse(body))), null);
        assertEquals(401, Json.asMap(badRes.get("__sq_webhook_reject")).get("status"));
    }

    // =========================================================================
    // 6. Shopify (Flagship): X-Shopify-Hmac-Sha256 Base64
    // =========================================================================

    @Test
    void shopify_base64Hmac_validAndInvalid() {
        OfficialApiNode node = loadNode("shopify");
        String secret = "shpss_secret_key";
        String body = "{\"id\":820982911946154508,\"name\":\"#1001\",\"email\":\"buyer@example.com\",\"total_price\":\"99.50\"}";
        String sig = hmacBase64("HmacSHA256", secret, body.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "trigger", "webhook_secret", secret),
                webhookReq("POST", Map.of(), Map.of(
                        "X-Shopify-Hmac-Sha256", sig,
                        "X-Shopify-Webhook-Id", "wh-shopify-1",
                        "X-Shopify-Topic", "orders/create"), body, Json.parse(body))), null);
        Map<String, Object> ev = Json.asMap(Json.asList(okRes.get("__sq_webhook_events")).get(0));
        assertEquals("wh-shopify-1", ev.get("idempotencyKey"));
        assertEquals("orders/create", Json.asMap(ev.get("payload")).get("event"));

        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "trigger", "webhook_secret", secret),
                webhookReq("POST", Map.of(), Map.of("X-Shopify-Hmac-Sha256", "notValidB64=="),
                        body, Json.parse(body))), null);
        assertEquals(401, Json.asMap(badRes.get("__sq_webhook_reject")).get("status"));
    }

    // =========================================================================
    // 7. GitHub (Flagship Custom Java GithubNode): X-Hub-Signature-256 + ping + push
    // =========================================================================

    @Test
    void githubNode_webhookPingAndPush_validAndInvalidHmac() {
        GithubNode node = new GithubNode("cc8bd8a8-4294-4b53-9ae5-a7a1923e87ec");
        String secret = "gh-webhook-secret";

        // Signed ping handshake
        String pingBody = "{\"zen\":\"Keep it logically awesome.\"}";
        String pingSig = "sha256=" + hmacHex("HmacSHA256", secret, pingBody.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> pingRes = node.handle(ingressReq(
                Map.of("operation", "listen", "webhook_secret", secret),
                webhookReq("POST", Map.of(), Map.of(
                        "X-Hub-Signature-256", pingSig,
                        "X-GitHub-Event", "ping",
                        "X-GitHub-Delivery", "deliv-ping"), pingBody, Json.parse(pingBody))), null);
        assertEquals(200, Json.asMap(pingRes.get("__sq_webhook_challenge")).get("status"));

        // Signed push event
        String pushBody = "{\"ref\":\"refs/heads/main\",\"repository\":{\"full_name\":\"acme/engine\"},\"sender\":{\"login\":\"octocat\"}}";
        String pushSig = "sha256=" + hmacHex("HmacSHA256", secret, pushBody.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> pushRes = node.handle(ingressReq(
                Map.of("operation", "listen", "webhook_secret", secret),
                webhookReq("POST", Map.of(), Map.of(
                        "X-Hub-Signature-256", pushSig,
                        "X-GitHub-Event", "push",
                        "X-GitHub-Delivery", "deliv-42"), pushBody, Json.parse(pushBody))), null);
        Map<String, Object> ev = Json.asMap(Json.asList(pushRes.get("__sq_webhook_events")).get(0));
        assertEquals("deliv-42", ev.get("idempotencyKey"));
        assertEquals("acme/engine", ev.get("correlationKey"));
        assertEquals("push", Json.asMap(ev.get("payload")).get("event"));

        // Invalid signature -> 401
        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "listen", "webhook_secret", secret),
                webhookReq("POST", Map.of(), Map.of(
                        "X-Hub-Signature-256", "sha256=bad",
                        "X-GitHub-Event", "push"), pushBody, Json.parse(pushBody))), null);
        assertEquals(401, Json.asMap(badRes.get("__sq_webhook_reject")).get("status"));

        // Mis-invoking listen in normal mode -> INVALID_INPUT
        StudioQuackError err = assertThrows(StudioQuackError.class, () ->
                node.handle(normalReq(Map.of("operation", "listen"), Map.of()), null));
        assertEquals("INVALID_INPUT", err.errorCode());
    }

    // =========================================================================
    // 8. GitLab (Flagship Group B): X-Gitlab-Token
    // =========================================================================

    @Test
    void gitlab_headerToken_validAndInvalid() {
        OfficialApiNode node = loadNode("gitlab");
        String body = "{\"object_kind\":\"merge_request\",\"event_type\":\"merge_request\",\"project\":{\"id\":77,\"path_with_namespace\":\"team/app\"},\"object_attributes\":{\"id\":501,\"iid\":12}}";

        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "listen", "webhook_token", "gl-hook-secret"),
                webhookReq("POST", Map.of(), Map.of(
                        "X-Gitlab-Token", "gl-hook-secret",
                        "X-Gitlab-Event-UUID", "gl-uuid-9"), body, Json.parse(body))), null);
        Map<String, Object> ev = Json.asMap(Json.asList(okRes.get("__sq_webhook_events")).get(0));
        assertEquals("gl-uuid-9", ev.get("idempotencyKey"));
        assertEquals("team/app", ev.get("correlationKey"));

        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "listen", "webhook_token", "gl-hook-secret"),
                webhookReq("POST", Map.of(), Map.of("X-Gitlab-Token", "wrong"), body, Json.parse(body))), null);
        assertEquals(401, Json.asMap(badRes.get("__sq_webhook_reject")).get("status"));
    }

    // =========================================================================
    // 9. Twilio (Flagship): X-Twilio-Signature HMAC-SHA1 over URL + sorted form params
    // =========================================================================

    @Test
    void twilio_hmacSha1Form_validAndInvalid() {
        OfficialApiNode node = loadNode("twilio");
        String authToken = "twilio-auth-token-secret";
        String url = "https://api.studioquack.com/be/v1/environments/env1/runtime/message-events/sqwh_1_abc";
        Map<String, String> form = new TreeMap<>();
        form.put("AccountSid", "AC123");
        form.put("Body", "Yes");
        form.put("From", "+15551234567");
        form.put("MessageSid", "SM999");
        form.put("To", "+15559990000");

        StringBuilder sigInput = new StringBuilder(url);
        form.forEach((k, v) -> sigInput.append(k).append(v));
        String sig = hmacBase64("HmacSHA1", authToken, sigInput.toString().getBytes(StandardCharsets.UTF_8));
        String rawForm = "MessageSid=SM999&From=%2B15551234567&To=%2B15559990000&Body=Yes&AccountSid=AC123";

        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "listen", "auth_token", authToken, "webhook_url", url),
                webhookReq("POST", Map.of(), Map.of(
                        "content-type", "application/x-www-form-urlencoded",
                        "X-Twilio-Signature", sig), rawForm, null)), null);
        Map<String, Object> ev = Json.asMap(Json.asList(okRes.get("__sq_webhook_events")).get(0));
        assertEquals("SM999", ev.get("idempotencyKey"));
        assertEquals("+15551234567", ev.get("correlationKey"));
        assertEquals("Yes", Json.asMap(ev.get("payload")).get("body"));

        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "listen", "auth_token", authToken, "webhook_url", url),
                webhookReq("POST", Map.of(), Map.of(
                        "content-type", "application/x-www-form-urlencoded",
                        "X-Twilio-Signature", "invalid-sig"), rawForm, null)), null);
        assertEquals(401, Json.asMap(badRes.get("__sq_webhook_reject")).get("status"));
    }

    // =========================================================================
    // 10. Jira (Flagship): X-Hub-Signature HMAC-SHA256 + issue correlation
    // =========================================================================

    @Test
    void jira_hmacSha256_validAndInvalid() {
        OfficialApiNode node = loadNode("jira");
        String secret = "jira-webhook-secret";
        String body = "{\"timestamp\":1760000005,\"webhookEvent\":\"jira:issue_updated\",\"issue\":{\"id\":\"10042\",\"key\":\"ENG-42\",\"fields\":{\"summary\":\"Fix ingress\"}},\"user\":{\"displayName\":\"Dev\"}}";
        String sig = "sha256=" + hmacHex("HmacSHA256", secret, body.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "listen", "webhook_secret", secret),
                webhookReq("POST", Map.of(), Map.of("X-Hub-Signature", sig), body, Json.parse(body))), null);
        Map<String, Object> ev = Json.asMap(Json.asList(okRes.get("__sq_webhook_events")).get(0));
        assertEquals("1760000005", ev.get("idempotencyKey"));
        assertEquals("ENG-42", ev.get("correlationKey"));
        assertEquals("Fix ingress", Json.asMap(ev.get("payload")).get("summary"));

        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "listen", "webhook_secret", secret),
                webhookReq("POST", Map.of(), Map.of("X-Hub-Signature", "sha256=wrong"), body, Json.parse(body))), null);
        assertEquals(401, Json.asMap(badRes.get("__sq_webhook_reject")).get("status"));
    }

    // =========================================================================
    // 11. Bonus Verifiers & Handshakes: Discord Ed25519, Asana Header Echo, Zoom, Okta, Generic, Fan-out Cap
    // =========================================================================

    @Test
    void discord_ed25519_pingHandshakeAndInvalidSignature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        byte[] encodedPub = kp.getPublic().getEncoded();
        byte[] rawPub32 = Arrays.copyOfRange(encodedPub, encodedPub.length - 32, encodedPub.length);
        String pubHex = toHex(rawPub32);

        String ts = "1760000000";
        String body = "{\"type\":1}";
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(ts.getBytes(StandardCharsets.UTF_8));
        sig.update(body.getBytes(StandardCharsets.UTF_8));
        String sigHex = toHex(sig.sign());

        OfficialApiNode node = loadNode("discord");
        Map<String, Object> okRes = node.handle(ingressReq(
                Map.of("operation", "listen", "public_key", pubHex),
                webhookReq("POST", Map.of(), Map.of(
                        "X-Signature-Ed25519", sigHex,
                        "X-Signature-Timestamp", ts), body, Json.parse(body))), null);
        Map<String, Object> ch = Json.asMap(okRes.get("__sq_webhook_challenge"));
        assertEquals(200, ch.get("status"));
        assertEquals("{\"type\":1}", ch.get("body"));

        Map<String, Object> badRes = node.handle(ingressReq(
                Map.of("operation", "listen", "public_key", pubHex),
                webhookReq("POST", Map.of(), Map.of(
                        "X-Signature-Ed25519", "00".repeat(64),
                        "X-Signature-Timestamp", ts), body, Json.parse(body))), null);
        assertEquals(401, Json.asMap(badRes.get("__sq_webhook_reject")).get("status"));
    }

    @Test
    void asana_headerEcho_and_zoom_urlValidation_and_okta_challenge() {
        // Asana X-Hook-Secret header echo
        OfficialApiNode asana = loadNode("asana");
        Map<String, Object> asanaRes = asana.handle(ingressReq(
                Map.of("operation", "listen"),
                webhookReq("POST", Map.of(), Map.of("X-Hook-Secret", "asana-secret-99"), "", null)), null);
        Map<String, Object> asanaCh = Json.asMap(asanaRes.get("__sq_webhook_challenge"));
        assertEquals("asana-secret-99", Json.asMap(asanaCh.get("headers")).get("X-Hook-Secret"));

        // Zoom endpoint.url_validation
        OfficialApiNode zoom = loadNode("zoom");
        String zoomBody = "{\"event\":\"endpoint.url_validation\",\"payload\":{\"plainToken\":\"pt_abc\"}}";
        Map<String, Object> zoomRes = zoom.handle(ingressReq(
                Map.of("operation", "listen", "webhook_secret_token", "zoom-sec"),
                webhookReq("POST", Map.of(), Map.of(), zoomBody, Json.parse(zoomBody))), null);
        Map<String, Object> zoomJson = Json.asMap(Json.parse(
                String.valueOf(Json.asMap(zoomRes.get("__sq_webhook_challenge")).get("body"))));
        assertEquals("pt_abc", zoomJson.get("plainToken"));
        assertEquals(hmacHex("HmacSHA256", "zoom-sec", "pt_abc".getBytes(StandardCharsets.UTF_8)),
                zoomJson.get("encryptedToken"));

        // Okta GET X-Okta-Verification-Challenge -> {"verification": "..."}
        OfficialApiNode okta = loadNode("okta");
        Map<String, Object> oktaRes = okta.handle(ingressReq(
                Map.of("operation", "listen"),
                webhookReq("GET", Map.of(), Map.of("X-Okta-Verification-Challenge", "okta-val-1"), "", null)), null);
        Map<String, Object> oktaJson = Json.asMap(Json.parse(
                String.valueOf(Json.asMap(oktaRes.get("__sq_webhook_challenge")).get("body"))));
        assertEquals("okta-val-1", oktaJson.get("verification"));
    }

    @Test
    void genericFallback_and_fanOutCap100() {
        // Group A node without a custom ingress spec block (e.g. hubspot) falls back to generic JSON pass-through
        OfficialApiNode hubspot = loadNode("hubspot");
        String body = "{\"eventId\":\"hs-100\",\"subscriptionType\":\"contact.creation\",\"objectId\":55}";
        Map<String, Object> res = hubspot.handle(ingressReq(
                Map.of("operation", "trigger.listen"),
                webhookReq("POST", Map.of(), Map.of("content-type", "application/json"),
                        body, Json.parse(body))), null);
        List<Object> events = Json.asList(res.get("__sq_webhook_events"));
        assertEquals(1, events.size());
        assertEquals("hs-100", Json.asMap(events.get(0)).get("idempotencyKey"));

        // Batch fan-out cap at 100 items
        List<Map<String, Object>> msgs = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            msgs.add(Map.of("id", "m-" + i, "from", "1555000" + i, "type", "text", "text", Map.of("body", "hi")));
        }
        Map<String, Object> hugeBody = Map.of("entry", List.of(Map.of("changes", List.of(
                Map.of("field", "messages", "value", Map.of("messages", msgs))))));
        OfficialApiNode wa = loadNode("whatsapp");
        Map<String, Object> capped = wa.handle(ingressReq(
                Map.of("operation", "listen"),
                webhookReq("POST", Map.of(), Map.of("content-type", "application/json"),
                        Json.write(hugeBody), hugeBody)), null);
        assertEquals(100, Json.asList(capped.get("__sq_webhook_events")).size());
    }

    @Test
    void studioQuackHelper_ingressSdkMethods_and_selectOptionSyntax() {
        String raw = "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"from\":\"1555\",\"text\":{\"body\":\"Yo\"}}]}}]}]}";
        String sig = "sha256=" + hmacHex("HmacSHA256", "my-secret", raw.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> wr = webhookReq("POST", Map.of("k", "v"),
                Map.of("X-Hub-Signature-256", sig, "Content-Type", "application/json"), raw, Json.parse(raw));

        StudioQuackHelper helper = new StudioQuackHelper(Map.of());
        helper.setInvocationEnvelope(Map.of("invocationMode", "ingress", "activeSelectRole", "listen", "webhookRequest", wr));

        assertTrue(helper.isWebhookInvocation());
        assertEquals("ingress", helper.getInvocationMode());
        assertEquals("listen", helper.getActiveSelectRole());
        assertEquals(sig, helper.getWebhookHeader("x-hub-signature-256"));
        assertEquals("Yo", helper.getWebhookPayload("entry[0].changes[0].value.messages[0].text.body"));
        assertTrue(helper.verifyHmacSha256("my-secret", "X-Hub-Signature-256"));
        assertFalse(helper.verifyHmacSha256("wrong-secret", "X-Hub-Signature-256"));

        Map<String, Object> ch = helper.respondWebhookChallenge("ok-chal");
        assertEquals("ok-chal", Json.asMap(ch.get("__sq_webhook_challenge")).get("body"));
        Map<String, Object> rej = helper.rejectWebhook(401, "bad_sig");
        assertEquals("bad_sig", Json.asMap(rej.get("__sq_webhook_reject")).get("reason"));

        // SelectOptionSyntax parity
        assertEquals(new SelectOptionSyntax.ParsedOption("listen", "Listen (Incoming)", "listen"),
                SelectOptionSyntax.parseOption("listen:Listen (Incoming):listen"));
        assertEquals(new SelectOptionSyntax.ParsedOption("send-and-wait", "Send & Wait", "send_and_wait"),
                SelectOptionSyntax.parseOption("send-and-wait:Send & Wait:send_and_wait"));
        assertEquals(new SelectOptionSyntax.ParsedOption("https://example.com", "Example", "normal"),
                SelectOptionSyntax.parseOption("https://example.com:Example"));
        assertEquals("send-message", SelectOptionSyntax.extractValue("send-message:Send Message"));
    }

    @Test
    void nodeLibraryRouter_dispatchesIngressEnvelopeForAllFlagshipNodes() {
        NodeCatalog catalog = NodeCatalog.load();
        NodeRegistry registry = new NodeRegistry();
        com.studioquack.nodelibrary.nodes.NodeHandlers.registerAll(registry, catalog);

        // WhatsApp GET challenge via registered catalog templateId
        String waId = catalog.templateId("whatsapp");
        NodeHandler waHandler = registry.get(waId);
        assertNotNull(waHandler);
        Map<String, Object> waReq = ingressReq(
                Map.of("operation", "listen", "verify_token", "tok1"),
                webhookReq("GET", Map.of(
                        "hub.mode", "subscribe",
                        "hub.verify_token", "tok1",
                        "hub.challenge", "chal-router"), Map.of(), "", null));
        waReq.put("templateId", waId);
        StudioQuackHelper helper = new StudioQuackHelper(Json.asMap(waReq.get("executionContext")));
        helper.setInvocationEnvelope(waReq);
        Map<String, Object> waRes = waHandler.handle(waReq, helper);
        assertEquals("chal-router", Json.asMap(waRes.get("__sq_webhook_challenge")).get("body"));

        // GitHub custom handler via registered catalog templateId
        String ghId = catalog.templateId("github");
        NodeHandler ghHandler = registry.get(ghId);
        assertNotNull(ghHandler);
        String ghBody = "{\"repository\":{\"full_name\":\"org/repo\"}}";
        String ghSig = "sha256=" + hmacHex("HmacSHA256", "gh-sec", ghBody.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> ghReq = ingressReq(
                Map.of("operation", "listen", "webhook_secret", "gh-sec"),
                webhookReq("POST", Map.of(), Map.of(
                        "X-Hub-Signature-256", ghSig,
                        "X-GitHub-Event", "push",
                        "X-GitHub-Delivery", "d-router"), ghBody, Json.parse(ghBody)));
        ghReq.put("templateId", ghId);
        Map<String, Object> ghRes = ghHandler.handle(ghReq, null);
        assertEquals("d-router", Json.asMap(Json.asList(ghRes.get("__sq_webhook_events")).get(0)).get("idempotencyKey"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private OfficialApiNode loadNode(String key) {
        OfficialApiSpec spec = OfficialApiSpec.load(key);
        assertNotNull(spec, "api_spec.json missing for " + key);
        return new OfficialApiNode("test-" + key, spec, FIXED_CLOCK);
    }

    private OfficialApiNode loadLocalizedNode(String key) {
        try {
            String raw = java.nio.file.Files.readString(
                    java.nio.file.Path.of("nodes", key, "api_spec.json"), StandardCharsets.UTF_8);
            Map<String, Object> map = new LinkedHashMap<>(Json.asMap(Json.parse(raw)));
            map.put("baseUrl", localBase);
            return new OfficialApiNode("test-" + key, OfficialApiSpec.fromMap(key, map), FIXED_CLOCK);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> webhookReq(String method, Map<String, Object> query,
                                                  Map<String, Object> headers, String rawBody, Object body) {
        Map<String, Object> wr = new LinkedHashMap<>();
        wr.put("method", method);
        wr.put("path", "/be/v1/environments/env-1/runtime/message-events/<REDACTED>");
        wr.put("query", query);
        wr.put("headers", headers);
        wr.put("rawBody", rawBody);
        wr.put("rawBodyBase64", Base64.getEncoder().encodeToString(rawBody.getBytes(StandardCharsets.UTF_8)));
        wr.put("rawBodyEncoding", "utf8");
        if (body != null) {
            wr.put("body", body);
        }
        return wr;
    }

    private static Map<String, Object> ingressReq(Map<String, Object> cfg, Map<String, Object> wr) {
        Map<String, Object> req = normalReq(cfg, Map.of());
        req.put("invocationMode", "ingress");
        req.put("activeSelectRole", "listen");
        req.put("webhookRequest", wr);
        return req;
    }

    private static Map<String, Object> normalReq(Map<String, Object> cfg, Map<String, Object> input) {
        List<Map<String, Object>> params = new ArrayList<>();
        if (cfg != null) {
            cfg.forEach((k, v) -> params.add(Map.of("shortCode", k, "value", v)));
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("executionContext", Map.of("currentNode", Map.of("data",
                Map.of("configurationParameters", params))));
        req.put("inputVariables", Map.of("input", input == null ? Map.of() : input));
        return req;
    }

    private static String hmacHex(String alg, String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(alg);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), alg));
            return toHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String hmacBase64(String alg, String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(alg);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), alg));
            return Base64.getEncoder().encodeToString(mac.doFinal(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
