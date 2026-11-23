package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.core.DataSupport;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RFC 959 FTP control/data channels over JDK sockets. Explicit FTPS (RFC 4217)
 * is {@code AUTH TLS} then {@code PBSZ 0} / {@code PROT P}. Passive mode only;
 * the data connection uses the control-channel address (PASV advertised IP is
 * ignored) so a hostile server cannot redirect the client.
 */
final class FtpControlClient implements AutoCloseable {

    private static final Pattern PASV = Pattern.compile(
            "\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");
    private static final byte[] CRLF = {'\r', '\n'};

    private final String host;
    private final int timeoutMs;
    private final boolean ftps;
    private Socket control;
    private BufferedInputStream ctrlIn;
    private OutputStream ctrlOut;
    private SSLContext ssl;

    FtpControlClient(String host, int port, String user, String password,
                     boolean ftps, int timeoutSeconds) throws Exception {
        this.host = host;
        this.timeoutMs = timeoutSeconds * 1000;
        this.ftps = ftps;
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), timeoutMs);
        raw.setSoTimeout(timeoutMs);
        this.control = raw;
        this.ctrlIn = new BufferedInputStream(raw.getInputStream());
        this.ctrlOut = raw.getOutputStream();
        String greet = readReply();
        requireCode(greet, 200, 299, "FTP greeting");
        if (ftps) {
            startTls();
        }
        String userReply = command("USER " + sanitizeToken(user, "user"));
        if (codeOf(userReply) == 331) {
            if (password == null) {
                DataSupport.invalidInput("password is required");
            }
            String passReply = command("PASS " + password);
            requireCode(passReply, 200, 299, "FTP login");
        } else {
            requireCode(userReply, 200, 299, "FTP login");
        }
        requireCode(command("TYPE I"), 200, 299, "TYPE I");
    }

    Map<String, Object> list(String remotePath) throws IOException {
        byte[] listing = withData("LIST " + dataArg(remotePath), null);
        List<Map<String, Object>> entries = parseList(listing);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entries", entries);
        data.put("count", entries.size());
        return ProtocolSupport.ok(data);
    }

    Map<String, Object> download(String remotePath) throws IOException {
        byte[] bytes = withData("RETR " + dataArg(remotePath), null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("remote_path", remotePath);
        data.put("bytes", bytes.length);
        return ProtocolSupport.okWithFile(data, ProtocolSupport.fileNameOf(remotePath), bytes);
    }

    Map<String, Object> upload(String remotePath, byte[] bytes) throws IOException {
        if (bytes == null) {
            bytes = new byte[0];
        }
        if (bytes.length > ProtocolSupport.MAX_FILE_BYTES) {
            DataSupport.fail("upload is " + bytes.length + " bytes, above the "
                    + ProtocolSupport.MAX_FILE_BYTES + " byte node limit");
        }
        withData("STOR " + dataArg(remotePath), bytes);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("remote_path", remotePath);
        data.put("bytes", bytes.length);
        return ProtocolSupport.ok(data);
    }

    Map<String, Object> delete(String remotePath) throws IOException {
        requireCode(command("DELE " + dataArg(remotePath)), 200, 299, "DELE");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("remote_path", remotePath);
        return ProtocolSupport.ok(data);
    }

    Map<String, Object> rename(String remotePath, String newPath) throws IOException {
        requireCode(command("RNFR " + dataArg(remotePath)), 300, 399, "RNFR");
        requireCode(command("RNTO " + dataArg(newPath)), 200, 299, "RNTO");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("remote_path", remotePath);
        data.put("new_path", newPath);
        return ProtocolSupport.ok(data);
    }

    @Override
    public void close() {
        try {
            if (control != null && control.isConnected()) {
                try {
                    command("QUIT");
                } catch (Exception ignored) {
                    // closing
                }
                control.close();
            }
        } catch (IOException ignored) {
            // closing
        }
    }

    private void startTls() throws Exception {
        String auth = command("AUTH TLS");
        requireCode(auth, 200, 299, "AUTH TLS");
        ssl = SSLContext.getInstance("TLS");
        ssl.init(null, null, new SecureRandom());
        SSLSocket wrapped = (SSLSocket) ssl.getSocketFactory()
                .createSocket(control, host, control.getPort(), true);
        SSLParameters params = wrapped.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        wrapped.setSSLParameters(params);
        wrapped.setSoTimeout(timeoutMs);
        wrapped.startHandshake();
        control = wrapped;
        ctrlIn = new BufferedInputStream(wrapped.getInputStream());
        ctrlOut = wrapped.getOutputStream();
        requireCode(command("PBSZ 0"), 200, 299, "PBSZ");
        requireCode(command("PROT P"), 200, 299, "PROT P");
    }

    private byte[] withData(String cmd, byte[] upload) throws IOException {
        requireCode(command("PASV"), 200, 299, "PASV");
        int dataPort = lastPasvPort;
        if (dataPort <= 0) {
            DataSupport.fail("PASV did not return a data port");
        }
        InetAddress addr = control.getInetAddress();
        try (Socket data = openData(addr, dataPort)) {
            String prelim = command(cmd);
            int c = codeOf(prelim);
            if (c == 550 || c == 553) {
                DataSupport.fail("FTP " + cmd.split(" ")[0] + " refused: " + prelim);
            }
            if (c < 100 || c > 199) {
                requireCode(prelim, 100, 199, cmd.split(" ")[0]);
            }
            byte[] result;
            if (upload != null) {
                data.getOutputStream().write(upload);
                data.getOutputStream().flush();
                result = new byte[0];
            } else {
                result = ProtocolSupport.readCapped(data.getInputStream(), ProtocolSupport.MAX_FILE_BYTES);
            }
            data.shutdownOutput();
            String done = readReply();
            requireCode(done, 200, 299, cmd.split(" ")[0]);
            return result;
        }
    }

    private int lastPasvPort;

    private Socket openData(InetAddress addr, int port) throws IOException {
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(addr, port), timeoutMs);
        raw.setSoTimeout(timeoutMs);
        if (!ftps) {
            return raw;
        }
        try {
            SSLSocket wrapped = (SSLSocket) ssl.getSocketFactory()
                    .createSocket(raw, host, port, true);
            SSLParameters params = wrapped.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            wrapped.setSSLParameters(params);
            wrapped.setSoTimeout(timeoutMs);
            wrapped.startHandshake();
            return wrapped;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("FTPS data channel handshake failed", e);
        }
    }

    private String command(String line) throws IOException {
        ctrlOut.write(line.getBytes(StandardCharsets.US_ASCII));
        ctrlOut.write(CRLF);
        ctrlOut.flush();
        String reply = readReply();
        if (line.startsWith("PASV")) {
            lastPasvPort = parsePasvPort(reply);
        }
        return reply;
    }

    private String readReply() throws IOException {
        String first = readLine();
        if (first == null) {
            DataSupport.fail("FTP control connection closed");
        }
        if (first.length() >= 4 && first.charAt(3) == '-') {
            String code = first.substring(0, 3);
            while (true) {
                String line = readLine();
                if (line == null) {
                    DataSupport.fail("FTP control connection closed");
                }
                if (line.startsWith(code) && line.length() >= 4 && line.charAt(3) == ' ') {
                    break;
                }
            }
        }
        return first;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = ctrlIn.read();
            if (b < 0) {
                return buf.size() == 0 ? null : buf.toString(StandardCharsets.US_ASCII);
            }
            if (b == '\n' && prev == '\r') {
                break;
            }
            if (b != '\r' && b != '\n') {
                if (buf.size() > 4096) {
                    DataSupport.fail("FTP control line exceeded 4096 bytes");
                }
                buf.write(b);
            }
            prev = b;
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }

    private static int parsePasvPort(String reply) {
        Matcher m = PASV.matcher(reply);
        if (!m.find()) {
            return -1;
        }
        int p1 = Integer.parseInt(m.group(5));
        int p2 = Integer.parseInt(m.group(6));
        return p1 * 256 + p2;
    }

    private static int codeOf(String reply) {
        if (reply == null || reply.length() < 3) {
            return -1;
        }
        try {
            return Integer.parseInt(reply.substring(0, 3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void requireCode(String reply, int min, int max, String what) {
        int c = codeOf(reply);
        if (c < min || c > max) {
            if (c == 530 || c == 332 || c == 331) {
                throw new studioquack.StudioQuackError("AUTH_FAILED",
                        "HTTP 401: FTP authentication failed", Map.of("http_status", 401));
            }
            DataSupport.fail("FTP " + what + " failed: " + (reply == null ? "" : reply));
        }
    }

    private static String sanitizeToken(String value, String field) {
        if (value == null || value.isBlank()) {
            DataSupport.invalidInput(field + " is required");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) {
            DataSupport.invalidInput(field + " contains invalid characters");
        }
        return value;
    }

    private static String dataArg(String path) {
        if (path == null || path.isBlank()) {
            return ".";
        }
        if (path.indexOf('\r') >= 0 || path.indexOf('\n') >= 0) {
            DataSupport.invalidInput("remote_path contains invalid characters");
        }
        return path;
    }

    private static List<Map<String, Object>> parseList(byte[] listing) {
        List<Map<String, Object>> entries = new ArrayList<>();
        String text = new String(listing, StandardCharsets.UTF_8);
        for (String line : text.split("\\R")) {
            if (entries.size() >= ProtocolSupport.MAX_LIST_ENTRIES) {
                break;
            }
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            String name = t;
            int last = t.lastIndexOf(' ');
            if (last >= 0 && last < t.length() - 1) {
                name = t.substring(last + 1);
            }
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("dir", t.toLowerCase(Locale.ROOT).startsWith("d"));
            entries.add(row);
        }
        return entries;
    }
}
