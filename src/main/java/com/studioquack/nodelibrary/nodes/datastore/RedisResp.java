package com.studioquack.nodelibrary.nodes.datastore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Redis RESP2 codec. Zero dependencies. Bulk replies are capped so a
 * hostile server cannot OOM the sandbox.
 */
public final class RedisResp {

    public static final int MAX_BULK = 1_000_000;

    private RedisResp() {
        // codec — never instantiated
    }

    public static void write(OutputStream out, String... parts) throws IOException {
        if (out == null || parts == null) {
            throw new IOException("missing redis command");
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(('*'));
        buf.write(Integer.toString(parts.length).getBytes(StandardCharsets.US_ASCII));
        buf.write('\r');
        buf.write('\n');
        for (String p : parts) {
            byte[] b = (p == null ? "" : p).getBytes(StandardCharsets.UTF_8);
            buf.write('$');
            buf.write(Integer.toString(b.length).getBytes(StandardCharsets.US_ASCII));
            buf.write('\r');
            buf.write('\n');
            buf.write(b);
            buf.write('\r');
            buf.write('\n');
        }
        out.write(buf.toByteArray());
        out.flush();
    }

    public static Object read(InputStream in) throws IOException {
        int type = in.read();
        if (type < 0) {
            throw new IOException("redis closed");
        }
        return switch (type) {
            case '+' -> readLine(in);
            case '-' -> {
                String err = readLine(in);
                throw new IOException("redis: " + err);
            }
            case ':' -> {
                String n = readLine(in);
                yield Long.parseLong(n);
            }
            case '$' -> readBulk(in);
            case '*' -> readArray(in);
            default -> throw new IOException("unsupported RESP type");
        };
    }

    private static Object readBulk(InputStream in) throws IOException {
        int len = Integer.parseInt(readLine(in));
        if (len < 0) {
            return null;
        }
        if (len > MAX_BULK) {
            throw new IOException("redis bulk too large");
        }
        byte[] buf = in.readNBytes(len);
        if (buf.length != len) {
            throw new IOException("redis bulk truncated");
        }
        if (in.read() != '\r' || in.read() != '\n') {
            throw new IOException("redis bulk trailer");
        }
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static Object readArray(InputStream in) throws IOException {
        int n = Integer.parseInt(readLine(in));
        if (n < 0) {
            return null;
        }
        if (n > 50_000) {
            throw new IOException("redis array too large");
        }
        List<Object> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(read(in));
        }
        return out;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int c = in.read();
            if (c < 0) {
                throw new IOException("redis closed");
            }
            if (c == '\r') {
                int n = in.read();
                if (n != '\n') {
                    throw new IOException("redis bad line");
                }
                break;
            }
            if (buf.size() > 16_384) {
                throw new IOException("redis line too long");
            }
            buf.write(c);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}
