package com.studioquack.nodelibrary.nodes.codec;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal ustar reader/writer for the Compression node. Regular files only —
 * links, devices, and sparse entries are refused. Reader rejects absolute names
 * and {@code ..} segments (tar-slip) and caps the cumulative payload.
 */
final class Tar {

    private static final int BLOCK = 512;

    private Tar() {
        // utility — never instantiated
    }

    record Entry(String name, byte[] bytes) {
    }

    /** Single-entry ustar archive. */
    static byte[] write(String name, byte[] bytes) {
        String safeName = name == null || name.isBlank() ? "file" : name;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = new byte[BLOCK];
        writeString(header, 0, 100, safeName);
        writeOctal(header, 100, 8, 0644);            // mode
        writeOctal(header, 108, 8, 0);               // uid
        writeOctal(header, 116, 8, 0);               // gid
        writeOctal(header, 124, 12, bytes.length);   // size
        writeOctal(header, 136, 12, 0);              // mtime
        header[156] = '0';                           // typeflag: regular file
        writeString(header, 257, 6, "ustar");
        // checksum: field treated as spaces
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        long sum = 0;
        for (byte b : header) {
            sum += b & 0xff;
        }
        writeOctal(header, 148, 7, sum);
        header[155] = ' ';
        out.write(header, 0, BLOCK);
        out.write(bytes, 0, bytes.length);
        int pad = (BLOCK - (bytes.length % BLOCK)) % BLOCK;
        out.write(new byte[pad], 0, pad);
        out.write(new byte[BLOCK * 2], 0, BLOCK * 2); // end-of-archive markers
        return out.toByteArray();
    }

    /**
     * All regular-file entries. Throws {@link IllegalArgumentException} on
     * tar-slip names, unsupported entry types, or payloads over {@code maxTotalBytes}.
     */
    static List<Entry> read(byte[] tar, long maxTotalBytes) {
        List<Entry> entries = new ArrayList<>();
        long total = 0;
        int pos = 0;
        while (pos + BLOCK <= tar.length) {
            if (isZeroBlock(tar, pos)) {
                break;
            }
            String name = readString(tar, pos, 100);
            long size = readOctal(tar, pos + 124, 12);
            char type = (char) (tar[pos + 156] & 0xff);
            if (name.isBlank()) {
                break;
            }
            String normalized = normalizeName(name);
            if (normalized == null) {
                throw new IllegalArgumentException("unsafe entry name in archive: " + name);
            }
            if (type != '0' && type != '\0') {
                throw new IllegalArgumentException("unsupported tar entry type '" + type + "' for " + name);
            }
            if (size < 0 || total + size > maxTotalBytes) {
                throw new IllegalArgumentException("archive payload exceeds the byte cap");
            }
            pos += BLOCK;
            if (pos + size > tar.length) {
                throw new IllegalArgumentException("truncated tar archive");
            }
            byte[] bytes = new byte[(int) size];
            System.arraycopy(tar, pos, bytes, 0, (int) size);
            entries.add(new Entry(normalized, bytes));
            total += size;
            pos += (int) (((size + BLOCK - 1) / BLOCK) * BLOCK);
        }
        return entries;
    }

    /** Null when the name is absolute or escapes via {@code ..}. */
    static String normalizeName(String name) {
        String n = name.replace('\\', '/');
        if (n.startsWith("/") || n.matches("^[A-Za-z]:.*")) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : n.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                return null;
            }
            parts.add(part);
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    private static boolean isZeroBlock(byte[] tar, int pos) {
        for (int i = 0; i < BLOCK; i++) {
            if (tar[pos + i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static void writeString(byte[] buf, int off, int len, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(b.length, len - 1);
        System.arraycopy(b, 0, buf, off, n);
    }

    private static void writeOctal(byte[] buf, int off, int len, long value) {
        String s = Long.toOctalString(value);
        String padded = "0".repeat(Math.max(0, len - 1 - s.length())) + s;
        byte[] b = padded.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, buf, off, Math.min(b.length, len - 1));
        buf[off + len - 1] = 0;
    }

    private static String readString(byte[] buf, int off, int len) {
        int end = off;
        int limit = off + len;
        while (end < limit && buf[end] != 0) {
            end++;
        }
        return new String(buf, off, end - off, StandardCharsets.UTF_8).trim();
    }

    private static long readOctal(byte[] buf, int off, int len) {
        String s = readString(buf, off, len).trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
