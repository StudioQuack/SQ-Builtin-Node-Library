package com.studioquack.nodelibrary.nodes.protocol;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EmailNode: fake SMTP send, TLS default, IMAP request validation, ingress.
 */
class EmailNodeTest {

    private final EmailNode node = new EmailNode("email-test");

    @Test
    void templateIdUsesConstructorArgument() {
        assertEquals("email-test", node.templateId());
        assertEquals(EmailNode.DEFAULT_ID, new EmailNode(null).templateId());
    }

    @Test
    void sendDeliversToLoopbackSmtpWhenPlainAllowed() throws Exception {
        try (FakeSmtp smtp = FakeSmtp.start()) {
            Map<String, Object> out = output(node.handle(request(
                    Map.of("operation", "send",
                            "smtp_host", "127.0.0.1",
                            "smtp_port", smtp.port(),
                            "tls", "false",
                            "from", "from@example.com",
                            "timeout_seconds", "5"),
                    Map.of("to", List.of("to@example.com"),
                            "subject", "hi",
                            "text", "body-plain")), null));
            assertEquals("ok", out.get("status"));
            assertTrue(smtp.await(5), "SMTP DATA not received");
            assertTrue(smtp.lastData().contains("body-plain"), smtp.lastData());
            assertTrue(smtp.lastData().contains("to@example.com"), smtp.lastData());
            assertFalse(smtp.transcript().toLowerCase(Locale.ROOT).contains("secret"),
                    smtp.transcript());
        }
    }

    @Test
    void sendAttachesContentBlock() throws Exception {
        try (FakeSmtp smtp = FakeSmtp.start()) {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("kind", "file");
            file.put("name", "note.txt");
            file.put("mime_type", "text/plain");
            file.put("source", "base64");
            file.put("data", Base64.getEncoder().encodeToString("hello-file".getBytes(StandardCharsets.UTF_8)));
            Map<String, Object> out = output(node.handle(request(
                    Map.of("operation", "send",
                            "smtp_host", "127.0.0.1",
                            "smtp_port", smtp.port(),
                            "tls", "false",
                            "from", "from@example.com",
                            "timeout_seconds", "5"),
                    Map.of("to", "to@example.com",
                            "subject", "file",
                            "text", "see attach",
                            "attachments", List.of(file))), null));
            assertEquals("ok", out.get("status"));
            assertTrue(smtp.await(5), "SMTP DATA not received");
            assertTrue(smtp.lastData().contains("note.txt"), smtp.lastData());
            assertTrue(smtp.lastData().contains("hello-file")
                    || smtp.lastData().contains(Base64.getEncoder().encodeToString(
                    "hello-file".getBytes(StandardCharsets.UTF_8))), smtp.lastData());
        }
    }

    @Test
    void tlsRequiredByDefaultFailsAgainstPlainSmtp() throws Exception {
        try (FakeSmtp smtp = FakeSmtp.start()) {
            StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(
                    Map.of("operation", "send",
                            "smtp_host", "127.0.0.1",
                            "smtp_port", smtp.port(),
                            "from", "from@example.com",
                            "timeout_seconds", "3"),
                    Map.of("to", "to@example.com", "text", "x")), null));
            assertEquals("REQUEST_FAILED", e.errorCode());
            assertFalse(e.getMessage().contains("password"));
        }
    }

    @Test
    void listenImapIsIngress() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "listen_imap"), Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("BPMN message"), e.getMessage());
    }

    @Test
    void unknownOperationIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "reply_all"), Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("Unknown operation"), e.getMessage());
    }

    @Test
    void sendRequiresHostFromAndTo() {
        StudioQuackError host = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "send", "tls", "false", "from", "a@b.com"),
                Map.of("to", "c@d.com")), null));
        assertEquals("INVALID_INPUT", host.errorCode());
        assertTrue(host.getMessage().contains("smtp_host"), host.getMessage());

        StudioQuackError from = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "send", "tls", "false", "smtp_host", "127.0.0.1"),
                Map.of("to", "c@d.com")), null));
        assertEquals("INVALID_INPUT", from.errorCode());
        assertTrue(from.getMessage().contains("from"), from.getMessage());

        StudioQuackError to = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "send", "tls", "false", "smtp_host", "127.0.0.1", "from", "a@b.com"),
                Map.of("subject", "x")), null));
        assertEquals("INVALID_INPUT", to.errorCode());
        assertTrue(to.getMessage().contains("to"), to.getMessage());
    }

    @Test
    void imapOpsRequireHostUserPasswordAndUid() {
        StudioQuackError host = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "search", "user", "u", "password", "p"),
                Map.of("query", "ALL")), null));
        assertEquals("INVALID_INPUT", host.errorCode());
        assertTrue(host.getMessage().contains("imap_host"), host.getMessage());

        StudioQuackError uid = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "get", "imap_host", "127.0.0.1", "user", "u", "password", "s3cret-mail"),
                Map.of()), null));
        assertEquals("INVALID_INPUT", uid.errorCode());
        assertTrue(uid.getMessage().contains("uid"), uid.getMessage());
        assertFalse(uid.getMessage().contains("s3cret-mail"), uid.getMessage());

        StudioQuackError dest = assertThrows(StudioQuackError.class, () -> node.handle(request(
                Map.of("operation", "move", "imap_host", "127.0.0.1", "user", "u", "password", "s3cret-mail"),
                Map.of("uid", "1")), null));
        assertEquals("INVALID_INPUT", dest.errorCode());
        assertTrue(dest.getMessage().contains("target_folder"), dest.getMessage());
        assertFalse(dest.getMessage().contains("s3cret-mail"), dest.getMessage());
    }

    @Test
    void searchCriteriaDefaultsAndRejectsControls() {
        assertEquals("ALL", EmailNode.searchCriteria(null));
        assertEquals("ALL", EmailNode.searchCriteria("  "));
        assertEquals("UNSEEN FROM a@b.com", EmailNode.searchCriteria("UNSEEN FROM a@b.com"));
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> EmailNode.searchCriteria("ALL\r\nUID FETCH"));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void imapFlagAcceptsKnownValues() {
        assertEquals(jakarta.mail.Flags.Flag.SEEN, EmailNode.imapFlag("seen"));
        assertEquals(jakarta.mail.Flags.Flag.FLAGGED, EmailNode.imapFlag("FLAGGED"));
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> EmailNode.imapFlag("draft"));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void smtpPropertiesRequireStartTlsUnlessPlainFlag() {
        Properties tls = EmailNode.smtpProperties("h", 587, true, false, 1000);
        assertEquals("true", tls.get("mail.smtp.starttls.required"));
        assertEquals("true", tls.get("mail.smtp.starttls.enable"));
        Properties smtps = EmailNode.smtpProperties("h", 465, true, false, 1000);
        assertEquals("true", smtps.get("mail.smtp.ssl.enable"));
        Properties plain = EmailNode.smtpProperties("h", 25, false, false, 1000);
        assertEquals("false", plain.get("mail.smtp.starttls.required"));
        assertEquals("false", plain.get("mail.smtp.ssl.enable"));
        assertTrue(EmailNode.tlsRequired(Map.of()));
        assertFalse(EmailNode.tlsRequired(Map.of("tls", "false")));
    }

    @Test
    void passwordNeverAppearsInSmtpAuthFailure() throws Exception {
        try (FakeSmtp smtp = FakeSmtp.start()) {
            StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(request(
                    Map.of("operation", "send",
                            "smtp_host", "127.0.0.1",
                            "smtp_port", smtp.port(),
                            "tls", "true",
                            "user", "mailer",
                            "password", "s3cret-mail",
                            "from", "from@example.com",
                            "timeout_seconds", "3"),
                    Map.of("to", "to@example.com", "text", "x")), null));
            assertFalse(e.getMessage().contains("s3cret-mail"), e.getMessage());
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

    private static final class FakeSmtp implements AutoCloseable {
        private final ServerSocket server;
        private final java.util.concurrent.ExecutorService exec;
        private final StringBuilder transcript = new StringBuilder();
        private final AtomicReference<String> lastData = new AtomicReference<>("");
        private final CountDownLatch dataLatch = new CountDownLatch(1);

        static FakeSmtp start() throws Exception {
            return new FakeSmtp();
        }

        private FakeSmtp() throws Exception {
            server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
            exec = Executors.newSingleThreadExecutor();
            exec.submit(this::serve);
        }

        int port() {
            return server.getLocalPort();
        }

        String lastData() {
            return lastData.get();
        }

        String transcript() {
            return transcript.toString();
        }

        boolean await(int seconds) throws InterruptedException {
            return dataLatch.await(seconds, TimeUnit.SECONDS);
        }

        private void serve() {
            try (Socket s = server.accept();
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                 OutputStream out = s.getOutputStream()) {
                write(out, "220 localhost\r\n");
                boolean data = false;
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    transcript.append(line).append('\n');
                    if (data) {
                        if (".".equals(line)) {
                            lastData.set(buf.toString());
                            data = false;
                            write(out, "250 OK\r\n");
                            dataLatch.countDown();
                        } else {
                            buf.append(line).append('\n');
                        }
                        continue;
                    }
                    String u = line.toUpperCase(Locale.ROOT);
                    if (u.startsWith("EHLO") || u.startsWith("HELO")) {
                        write(out, "250-localhost\r\n250 AUTH PLAIN LOGIN\r\n");
                    } else if (u.startsWith("AUTH")) {
                        write(out, "235 OK\r\n");
                    } else if (u.startsWith("MAIL") || u.startsWith("RCPT")
                            || u.startsWith("RSET") || u.startsWith("NOOP")) {
                        write(out, "250 OK\r\n");
                    } else if (u.startsWith("DATA")) {
                        write(out, "354 go\r\n");
                        data = true;
                    } else if (u.startsWith("QUIT")) {
                        write(out, "221 bye\r\n");
                        break;
                    } else {
                        write(out, "250 OK\r\n");
                    }
                }
            } catch (Exception ignored) {
                // test teardown
            }
        }

        private static void write(OutputStream out, String s) throws Exception {
            out.write(s.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        @Override
        public void close() {
            exec.shutdownNow();
            try {
                server.close();
            } catch (Exception ignored) {
                // test teardown
            }
        }
    }
}
