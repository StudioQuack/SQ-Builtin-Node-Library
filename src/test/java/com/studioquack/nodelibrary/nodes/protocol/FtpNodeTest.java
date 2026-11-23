package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation, host-key refusal (SFTP), and an in-process fake FTP server for the
 * plain-FTP path of {@link FtpNode}.
 */
class FtpNodeTest {

    private final FtpNode node = new FtpNode("ftp-test-id");

    @Test
    void constructorKeepsTemplateId() {
        assertEquals("ftp-test-id", node.templateId());
        assertEquals(FtpNode.DEFAULT_ID, new FtpNode(" ").templateId());
    }

    @Test
    void missingHostIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "list"),
                        cfg("user", "string", "alice"),
                        cfg("protocol", "select", "sftp"),
                        cfg("known_hosts", "string", "SHA256:abcd")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("host"), e.getMessage());
    }

    @Test
    void unknownOperationIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "mkdir", "host", "127.0.0.1"),
                        cfg("user", "string", "alice"),
                        cfg("protocol", "select", "sftp"),
                        cfg("known_hosts", "string", "SHA256:abcd")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"), e.getMessage());
    }

    @Test
    void plainFtpRefusedWithoutFlag() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "list", "host", "127.0.0.1"),
                        cfg("user", "string", "alice"),
                        cfg("protocol", "select", "ftp"),
                        cfg("password", "password", "s3cret-pass"),
                        cfg("port", "number", 21)), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("allow_plain_ftp"), e.getMessage());
    }

    @Test
    void sftpMissingKnownHostsRefuses() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "list", "host", "127.0.0.1"),
                        cfg("user", "string", "alice"),
                        cfg("protocol", "select", "sftp"),
                        cfg("password", "password", "s3cret-pass")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("known_hosts"), e.getMessage());
    }

    @Test
    void remotePathTraversalRejected() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "download", "host", "127.0.0.1",
                                "remote_path", "../secret.txt"),
                        cfg("user", "string", "alice"),
                        cfg("protocol", "select", "ftp"),
                        cfg("allow_plain_ftp", "boolean", true),
                        cfg("password", "password", "s3cret-pass")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("path traversal"), e.getMessage());
    }

    @Test
    void passwordTypedRequired() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of("operation", "list", "host", "127.0.0.1"),
                        cfg("user", "string", "alice"),
                        cfg("protocol", "select", "ftp"),
                        cfg("allow_plain_ftp", "boolean", true),
                        cfg("password", "string", "s3cret-pass")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("password-typed"), e.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void plainFtpListDownloadUploadDeleteRename() throws Exception {
        try (FakeFtpServer server = FakeFtpServer.start("s3cret-pass")) {
            server.files.put("notes.txt", "hello-ftp".getBytes(StandardCharsets.UTF_8));

            Map<String, Object> listed = output(node.handle(ftpReq(server, "list",
                    Map.of("remote_path", ".")), null));
            assertEquals("ok", listed.get("status"));
            List<Map<String, Object>> entries = (List<Map<String, Object>>)
                    ((Map<String, Object>) listed.get("data")).get("entries");
            assertTrue(entries.stream().anyMatch(e -> "notes.txt".equals(e.get("name"))));

            Map<String, Object> down = output(node.handle(ftpReq(server, "download",
                    Map.of("remote_path", "notes.txt")), null));
            assertEquals("ok", down.get("status"));
            Map<String, Object> file = (Map<String, Object>) down.get("file");
            assertEquals("notes.txt", file.get("name"));
            assertEquals("hello-ftp", new String(Base64.getDecoder().decode((String) file.get("data")),
                    StandardCharsets.UTF_8));
            List<?> outputFiles = (List<?>) down.get("outputFiles");
            assertEquals(1, outputFiles.size());

            Map<String, Object> uploadIn = new LinkedHashMap<>();
            uploadIn.put("remote_path", "up.txt");
            uploadIn.put("file", FilesSupport.fileBlock("up.txt", "text/plain",
                    "uploaded".getBytes(StandardCharsets.UTF_8)));
            Map<String, Object> up = output(node.handle(ftpReq(server, "upload", uploadIn), null));
            assertEquals("ok", up.get("status"));
            assertEquals("uploaded", new String(server.files.get("up.txt"), StandardCharsets.UTF_8));

            Map<String, Object> ren = output(node.handle(ftpReq(server, "rename",
                    Map.of("remote_path", "up.txt", "new_path", "renamed.txt")), null));
            assertEquals("ok", ren.get("status"));
            assertTrue(server.files.containsKey("renamed.txt"));

            Map<String, Object> del = output(node.handle(ftpReq(server, "delete",
                    Map.of("remote_path", "renamed.txt")), null));
            assertEquals("ok", del.get("status"));
            assertTrue(!server.files.containsKey("renamed.txt"));
        }
    }

    @Test
    void plainFtpRedactsPasswordOnAuthFailure() throws Exception {
        try (FakeFtpServer server = FakeFtpServer.start("s3cret-pass")) {
            StudioQuackError e = assertThrows(StudioQuackError.class,
                    () -> node.handle(ftpReq(server, "list", Map.of(), "wrong-secret-value"), null));
            assertTrue(!e.getMessage().contains("wrong-secret-value"), e.getMessage());
            assertEquals("AUTH_FAILED", e.errorCode());
        }
    }

    private Map<String, Object> ftpReq(FakeFtpServer server, String op, Map<String, Object> extra) {
        return ftpReq(server, op, extra, "s3cret-pass");
    }

    private Map<String, Object> ftpReq(FakeFtpServer server, String op, Map<String, Object> extra, String password) {
        Map<String, Object> in = new LinkedHashMap<>(extra);
        in.put("operation", op);
        in.put("host", "127.0.0.1");
        return request(in,
                cfg("user", "string", "alice"),
                cfg("protocol", "select", "ftp"),
                cfg("allow_plain_ftp", "boolean", true),
                cfg("passive", "select", "true"),
                cfg("port", "number", server.port()),
                cfg("password", "password", password),
                cfg("timeout_seconds", "number", 5));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> handle) {
        return (Map<String, Object>) handle.get("output");
    }

    static Map<String, Object> cfg(String code, String type, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shortCode", code);
        m.put("type", type);
        m.put("value", value);
        return m;
    }

    @SafeVarargs
    static Map<String, Object> request(Map<String, Object> input, Map<String, Object>... params) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configurationParameters", List.of(params));
        Map<String, Object> nodeMap = new LinkedHashMap<>();
        nodeMap.put("data", data);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("currentNode", nodeMap);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("inputVariables", Map.of("input", input));
        req.put("executionContext", ctx);
        return req;
    }

    /** Loopback RFC 959 server: USER/PASS, PASV, LIST/RETR/STOR/DELE/RNFR/RNTO. */
    static final class FakeFtpServer implements AutoCloseable {
        final ConcurrentHashMap<String, byte[]> files = new ConcurrentHashMap<>();
        private final ServerSocket listen;
        private final ExecutorService exec = Executors.newCachedThreadPool();
        private final String password;
        private volatile boolean open = true;

        private FakeFtpServer(ServerSocket listen, String password) {
            this.listen = listen;
            this.password = password;
        }

        static FakeFtpServer start(String password) throws IOException {
            ServerSocket listen = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            FakeFtpServer server = new FakeFtpServer(listen, password);
            server.exec.submit(server::acceptLoop);
            return server;
        }

        int port() {
            return listen.getLocalPort();
        }

        private void acceptLoop() {
            while (open) {
                try {
                    Socket s = listen.accept();
                    exec.submit(() -> handle(s));
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                socket.setSoTimeout(5000);
                BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                write(out, "220 fake ftp");
                ServerSocket dataListen = null;
                String renameFrom = null;
                String logged = null;
                while (open) {
                    String line = readLine(in);
                    if (line == null) {
                        return;
                    }
                    String cmd = line.length() >= 4 ? line.substring(0, 4).toUpperCase() : line.toUpperCase();
                    String arg = line.length() > 5 ? line.substring(5) : "";
                    switch (cmd) {
                        case "USER" -> write(out, "331 password");
                        case "PASS" -> {
                            if (password.equals(arg)) {
                                logged = "ok";
                                write(out, "230 logged in");
                            } else {
                                write(out, "530 denied");
                            }
                        }
                        case "TYPE" -> write(out, "200 type i");
                        case "PASV" -> {
                            if (dataListen != null) {
                                dataListen.close();
                            }
                            dataListen = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                            int p = dataListen.getLocalPort();
                            write(out, "227 Entering Passive Mode (127,0,0,1," + (p / 256) + "," + (p % 256) + ")");
                        }
                        case "LIST" -> {
                            write(out, "150 opening");
                            byte[] listing = listBytes();
                            xfer(dataListen, listing, null);
                            write(out, "226 done");
                        }
                        case "RETR" -> {
                            byte[] body = files.get(arg);
                            if (body == null) {
                                write(out, "550 missing");
                            } else {
                                write(out, "150 opening");
                                xfer(dataListen, body, null);
                                write(out, "226 done");
                            }
                        }
                        case "STOR" -> {
                            write(out, "150 opening");
                            byte[] body = xfer(dataListen, null, true);
                            files.put(arg, body);
                            write(out, "226 done");
                        }
                        case "DELE" -> {
                            files.remove(arg);
                            write(out, "250 deleted");
                        }
                        case "RNFR" -> {
                            renameFrom = arg;
                            write(out, "350 ready");
                        }
                        case "RNTO" -> {
                            if (renameFrom != null && files.containsKey(renameFrom)) {
                                files.put(arg, files.remove(renameFrom));
                            }
                            write(out, "250 renamed");
                        }
                        case "QUIT" -> {
                            write(out, "221 bye");
                            return;
                        }
                        default -> write(out, "502 not implemented");
                    }
                    if (logged == null && "PASS".equals(cmd) && !password.equals(arg)) {
                        return;
                    }
                }
            } catch (Exception ignored) {
                // test teardown
            }
        }

        private byte[] listBytes() {
            StringBuilder sb = new StringBuilder();
            for (String name : files.keySet()) {
                sb.append("-rw-r--r-- 1 owner group ").append(files.get(name).length)
                        .append(" Jan 01 00:00 ").append(name).append("\r\n");
            }
            return sb.toString().getBytes(StandardCharsets.US_ASCII);
        }

        private byte[] xfer(ServerSocket dataListen, byte[] outbound, Boolean inbound) throws IOException {
            if (dataListen == null) {
                throw new IOException("PASV not issued");
            }
            try (Socket data = dataListen.accept()) {
                data.setSoTimeout(5000);
                if (outbound != null) {
                    data.getOutputStream().write(outbound);
                    data.getOutputStream().flush();
                    return outbound;
                }
                return data.getInputStream().readAllBytes();
            }
        }

        private static void write(OutputStream out, String line) throws IOException {
            out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        private static String readLine(BufferedInputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int prev = -1;
            while (true) {
                int b = in.read();
                if (b < 0) {
                    return buf.size() == 0 ? null : buf.toString(StandardCharsets.US_ASCII);
                }
                if (b == '\n' && prev == '\r') {
                    return buf.toString(StandardCharsets.US_ASCII);
                }
                if (b != '\r' && b != '\n') {
                    buf.write(b);
                }
                prev = b;
            }
        }

        @Override
        public void close() {
            open = false;
            try {
                listen.close();
            } catch (IOException ignored) {
                // teardown
            }
            exec.shutdownNow();
            try {
                exec.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
