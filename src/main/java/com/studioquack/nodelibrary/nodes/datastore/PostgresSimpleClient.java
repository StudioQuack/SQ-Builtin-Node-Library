package com.studioquack.nodelibrary.nodes.datastore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL v3 startup + simple-query. Supports trust, cleartext, MD5, and
 * SCRAM-SHA-256. One connection per invoke. Never logs the password.
 */
final class PostgresSimpleClient implements AutoCloseable {

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;

    private PostgresSimpleClient(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = socket.getOutputStream();
    }

    static PostgresSimpleClient connect(String host, int port, String user, String password,
                                        String database, int timeoutSeconds, boolean ssl,
                                        boolean verifyTls) throws Exception {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host is required");
        }
        int ms = Math.max(1, timeoutSeconds) * 1000;
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host.trim(), port > 0 ? port : 5432), ms);
        raw.setSoTimeout(ms);
        Socket sock = ssl ? wrapSsl(raw, host.trim(), port > 0 ? port : 5432, verifyTls) : raw;
        PostgresSimpleClient c = new PostgresSimpleClient(sock);
        try {
            c.startup(user == null ? "" : user, database == null || database.isBlank() ? user : database);
            c.authenticate(user == null ? "" : user, password == null ? "" : password);
            c.readUntilReady();
            return c;
        } catch (Exception e) {
            c.close();
            throw e;
        }
    }

    List<Map<String, String>> query(String sql) throws IOException {
        write('Q', (sql == null ? "" : sql).getBytes(StandardCharsets.UTF_8), true);
        List<String> names = List.of();
        List<Map<String, String>> rows = new ArrayList<>();
        while (true) {
            Msg m = readMsg();
            switch (m.type) {
                case 'T' -> names = rowDescription(m.body);
                case 'D' -> rows.add(dataRow(names, m.body));
                case 'C', 'I', 'n', 'N', 'A', 'S' -> {
                    // command complete / empty / notice / notify / parameter — ignore
                }
                case 'Z' -> {
                    return rows;
                }
                case 'E' -> throw new IOException(errorText(m.body));
                default -> {
                    // skip unknown
                }
            }
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private void startup(String user, String database) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        writeI32(buf, 196608);
        writeCstr(buf, "user");
        writeCstr(buf, user);
        writeCstr(buf, "database");
        writeCstr(buf, database);
        buf.write(0);
        byte[] payload = buf.toByteArray();
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        writeI32(framed, payload.length + 4);
        framed.write(payload);
        out.write(framed.toByteArray());
        out.flush();
    }

    private void authenticate(String user, String password) throws IOException {
        PostgresScram.Start scram = null;
        while (true) {
            Msg m = readMsg();
            if (m.type == 'E') {
                throw new IOException(errorText(m.body));
            }
            if (m.type == 'R') {
                int method = readI32(m.body, 0);
                if (method == 0) {
                    return;
                }
                if (method == 3) {
                    write('p', password.getBytes(StandardCharsets.UTF_8), true);
                    continue;
                }
                if (method == 5) {
                    byte[] salt = new byte[4];
                    System.arraycopy(m.body, 4, salt, 0, 4);
                    write('p', md5(user, password, salt), true);
                    continue;
                }
                if (method == 10) {
                    scram = PostgresScram.begin(user);
                    writeSaslInitial("SCRAM-SHA-256", scram.clientFirst());
                    continue;
                }
                if (method == 11) {
                    String serverFirst = new String(m.body, 4, m.body.length - 4, StandardCharsets.UTF_8);
                    try {
                        String fin = PostgresScram.clientFinal(scram, serverFirst, password);
                        write('p', fin.getBytes(StandardCharsets.UTF_8), true);
                    } catch (Exception e) {
                        throw new IOException("scram failed");
                    }
                    continue;
                }
                if (method == 12) {
                    continue;
                }
                throw new IOException("unsupported postgres auth " + method);
            }
            if (m.type == 'S' || m.type == 'K' || m.type == 'N') {
                continue;
            }
            if (m.type == 'Z') {
                return;
            }
        }
    }

    private void readUntilReady() throws IOException {
        while (true) {
            Msg m = readMsg();
            if (m.type == 'Z') {
                return;
            }
            if (m.type == 'E') {
                throw new IOException(errorText(m.body));
            }
        }
    }

    private void writeSaslInitial(String mechanism, String clientFirst) throws IOException {
        byte[] mech = (mechanism == null ? "" : mechanism).getBytes(StandardCharsets.US_ASCII);
        byte[] first = (clientFirst == null ? "" : clientFirst).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write('p');
        writeI32(buf, 4 + mech.length + 1 + 4 + first.length);
        buf.write(mech);
        buf.write(0);
        writeI32(buf, first.length);
        buf.write(first);
        out.write(buf.toByteArray());
        out.flush();
    }

    private void write(char type, byte[] payload, boolean cstr) throws IOException {
        int extra = cstr ? 1 : 0;
        int len = 4 + payload.length + extra;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write((byte) type);
        writeI32(buf, len);
        buf.write(payload);
        if (cstr) {
            buf.write(0);
        }
        out.write(buf.toByteArray());
        out.flush();
    }

    private Msg readMsg() throws IOException {
        int type = in.read();
        if (type < 0) {
            throw new EOFException("postgres closed");
        }
        int len = in.readInt();
        if (len < 4 || len > 8_000_000) {
            throw new IOException("postgres message too large");
        }
        byte[] body = in.readNBytes(len - 4);
        if (body.length != len - 4) {
            throw new EOFException("postgres truncated");
        }
        return new Msg((char) type, body);
    }

    private static Socket wrapSsl(Socket raw, String host, int port, boolean verify) throws Exception {
        raw.getOutputStream().write(new byte[]{0, 0, 0, 8, 0x04, (byte) 0xd2, 0x16, 0x2f});
        raw.getOutputStream().flush();
        int ans = raw.getInputStream().read();
        if (ans != 'S') {
            raw.close();
            throw new IOException("postgres ssl required");
        }
        SSLContext ctx = SSLContext.getInstance("TLS");
        if (verify) {
            ctx.init(null, null, new SecureRandom());
        } else {
            ctx.init(null, new TrustManager[]{INSECURE}, new SecureRandom());
        }
        var ssl = (javax.net.ssl.SSLSocket) ctx.getSocketFactory().createSocket(raw, host, port, true);
        if (verify) {
            SSLParameters p = ssl.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(p);
        }
        ssl.startHandshake();
        return ssl;
    }

    private static byte[] md5(String user, String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String inner = HexFormat.of().formatHex(md.digest((password + user).getBytes(StandardCharsets.UTF_8)));
            md.reset();
            md.update(inner.getBytes(StandardCharsets.US_ASCII));
            md.update(salt);
            return ("md5" + HexFormat.of().formatHex(md.digest())).getBytes(StandardCharsets.US_ASCII);
        } catch (Exception e) {
            throw new IllegalStateException("md5 unavailable");
        }
    }

    private static List<String> rowDescription(byte[] body) {
        int n = ((body[0] & 0xff) << 8) | (body[1] & 0xff);
        List<String> names = new ArrayList<>(n);
        int i = 2;
        for (int c = 0; c < n; c++) {
            int z = i;
            while (z < body.length && body[z] != 0) {
                z++;
            }
            names.add(new String(body, i, z - i, StandardCharsets.UTF_8));
            i = z + 1 + 18;
        }
        return names;
    }

    private static Map<String, String> dataRow(List<String> names, byte[] body) {
        Map<String, String> row = new LinkedHashMap<>();
        int n = ((body[0] & 0xff) << 8) | (body[1] & 0xff);
        int i = 2;
        for (int c = 0; c < n; c++) {
            int len = readI32(body, i);
            i += 4;
            String name = c < names.size() ? names.get(c) : ("c" + c);
            if (len < 0) {
                row.put(name, null);
            } else {
                row.put(name, new String(body, i, len, StandardCharsets.UTF_8));
                i += len;
            }
        }
        return row;
    }

    private static String errorText(byte[] body) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < body.length && body[i] != 0) {
            char f = (char) body[i++];
            int z = i;
            while (z < body.length && body[z] != 0) {
                z++;
            }
            String v = new String(body, i, z - i, StandardCharsets.UTF_8);
            i = z + 1;
            if (f == 'M' || f == 'C') {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(v);
            }
        }
        return sb.isEmpty() ? "postgres error" : sb.toString();
    }

    private static void writeI32(ByteArrayOutputStream buf, int v) {
        buf.write((v >>> 24) & 0xff);
        buf.write((v >>> 16) & 0xff);
        buf.write((v >>> 8) & 0xff);
        buf.write(v & 0xff);
    }

    private static void writeCstr(ByteArrayOutputStream buf, String s) throws IOException {
        buf.write((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
        buf.write(0);
    }

    private static int readI32(byte[] b, int i) {
        return ((b[i] & 0xff) << 24) | ((b[i + 1] & 0xff) << 16) | ((b[i + 2] & 0xff) << 8) | (b[i + 3] & 0xff);
    }

    private record Msg(char type, byte[] body) {
    }

    private static final X509ExtendedTrustManager INSECURE = new X509ExtendedTrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] c, String a) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] c, String a, Socket s) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a, Socket s) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] c, String a, javax.net.ssl.SSLEngine e) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a, javax.net.ssl.SSLEngine e) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
