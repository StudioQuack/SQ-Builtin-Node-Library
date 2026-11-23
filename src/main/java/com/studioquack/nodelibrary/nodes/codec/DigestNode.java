package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Time-window digest buffer: {@code buffer | flush}.
 *
 * <p>The store is a JSONL file per (templateId, window, timezone) under
 * {@code SQ_DIGEST_DIR} (default {@code <java.io.tmpdir>/sq-digest}) — durable
 * across restarts and safe for concurrent stateful invocations via
 * {@link FileLock}. It is deliberately file-backed rather than JVM-memory so
 * buffered items survive a container restart; a multi-replica deployment must
 * point {@code SQ_DIGEST_DIR} at shared storage (the definition's "shared
 * store" requirement cannot be met by heap).
 *
 * <p>{@code buffer} appends one item and enforces {@code max_items} (default
 * 1000) as a hard cap. {@code flush} emits every buffered item and clears the
 * file — a BPMN timer is the intended invoker.
 */
public final class DigestNode implements NodeHandler {

    public static final String DEFAULT_ID = "4331e5bb-fb55-48af-b3a9-4b99997f7485";
    /** Upper bound on buffer windows (30 days). */
    static final Duration MAX_WINDOW = Duration.ofDays(30);

    private final String templateId;
    private final Path storeDir;

    public DigestNode() {
        this(DEFAULT_ID);
    }

    public DigestNode(String templateId) {
        this(templateId, defaultDir());
    }

    /** Test seam: explicit store directory. */
    public DigestNode(String templateId, Path storeDir) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
        this.storeDir = storeDir;
    }

    private static Path defaultDir() {
        String env = System.getenv("SQ_DIGEST_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env.trim());
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "sq-digest");
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        if (request == null) {
            return CodecSupport.error("request body must be a JSON object");
        }
        Map<String, Object> cfg = DataSupport.readConfig(request);
        Map<String, Object> in = DataSupport.readInput(request, helper);
        try {
            String op = CodecSupport.op(cfg, in);
            if (op == null) {
                return CodecSupport.error("operation is required. Accepted: [buffer, flush]");
            }
            String windowRaw = DataSupport.inStr(in, "window",
                    DataSupport.cfgStr(cfg, "window", null));
            Duration window = null;
            if (windowRaw != null && !windowRaw.isBlank()) {
                window = parseWindow(windowRaw);
                if (window == null || window.isNegative() || window.isZero()) {
                    return CodecSupport.error("window must be an ISO-8601 duration (PT15M) "
                            + "or a positive minutes integer");
                }
                if (window.compareTo(MAX_WINDOW) > 0) {
                    return CodecSupport.error("window must not exceed 30 days (PT720H or 43200 minutes)");
                }
            }
            String tzRaw = DataSupport.cfgStr(cfg, "timezone", "UTC");
            ZoneId zone;
            try {
                zone = ZoneId.of(tzRaw == null ? "UTC" : tzRaw.trim());
            } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
                return CodecSupport.error("timezone is not a valid IANA zone: " + tzRaw);
            }
            int maxItems = DataSupport.cfgInt(cfg, "max_items", 1000);

            return switch (op) {
                case "buffer" -> buffer(cfg, in, windowRaw, window, zone, maxItems);
                case "flush" -> flush(cfg, windowRaw, window, zone);
                default -> CodecSupport.error("Unknown operation: " + op + ". Accepted: [buffer, flush]");
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("Error: " + e.getClass().getSimpleName());
        }
    }

    // --- buffer -----------------------------------------------------------------

    private Map<String, Object> buffer(Map<String, Object> cfg, Map<String, Object> in,
                                       String windowRaw, Duration window, ZoneId zone, int maxItems) {
        Map<String, Object> item = requireBufferItem(in.get("item"));
        if (item == null) {
            if (!in.containsKey("item") || in.get("item") == null) {
                return CodecSupport.error("item is required for operation buffer");
            }
            return CodecSupport.error("item must be a JSON object");
        }
        Path file = storeFile(windowRaw, zone);
        try {
            Files.createDirectories(storeDir);
            int size;
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = ch.lock()) {
                size = countLines(ch);
                if (size >= maxItems) {
                    return CodecSupport.error("digest buffer is full (max_items=" + maxItems
                            + "); flush it or raise max_items");
                }
                String line = Json.write(item);
                ch.position(ch.size());
                ch.write(java.nio.ByteBuffer.wrap((line + "\n").getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)));
                ch.force(false);
                size++;
            }
            Map<String, Object> data = CodecSupport.data();
            data.put("buffered", true);
            data.put("size", size);
            data.put("max_items", maxItems);
            putWindow(data, windowRaw, window, zone);
            return CodecSupport.ok(data);
        } catch (IOException e) {
            return CodecSupport.error("buffer failed: " + e.getClass().getSimpleName());
        }
    }

    // --- flush --------------------------------------------------------------------

    private Map<String, Object> flush(Map<String, Object> cfg, String windowRaw,
                                      Duration window, ZoneId zone) {
        Path file = storeFile(windowRaw, zone);
        List<Object> items = new ArrayList<>();
        try {
            Files.createDirectories(storeDir);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = ch.lock()) {
                long size = ch.size();
                byte[] bytes = new byte[0];
                if (size > 0) {
                    if (size > Integer.MAX_VALUE) {
                        return CodecSupport.error("flush failed: buffer file too large");
                    }
                    bytes = new byte[(int) size];
                    ch.position(0);
                    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
                    while (buf.hasRemaining()) {
                        int n = ch.read(buf);
                        if (n < 0) {
                            break;
                        }
                    }
                }
                String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                for (String line : content.split("\n")) {
                    String t = line.trim();
                    if (t.isEmpty()) {
                        continue;
                    }
                    Object parsed = DataSupport.parseJson(t);
                    items.add(parsed == null ? t : parsed);
                }
                ch.truncate(0);
                ch.force(false);
            }
        } catch (IOException e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return CodecSupport.error("flush failed: " + detail);
        }
        Map<String, Object> data = CodecSupport.data();
        data.put("items", items);
        data.put("count", items.size());
        data.put("flushed_at", ZonedDateTime.now(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        putWindow(data, windowRaw, window, zone);
        return CodecSupport.ok(data);
    }

    // --- helpers ----------------------------------------------------------------------

    /** One store file per (template, window, timezone); name is a SHA-256 hex digest. */
    private Path storeFile(String windowRaw, ZoneId zone) {
        String key = templateId + "|" + (windowRaw == null ? "" : windowRaw) + "|" + zone.getId();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return storeDir.resolve(HexFormat.of().formatHex(digest) + ".jsonl");
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    /**
     * {@code item} must be a JSON object (map). A string is accepted only when it parses
     * to a JSON object, not a primitive.
     */
    static Map<String, Object> requireBufferItem(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty() || !t.startsWith("{")) {
                return null;
            }
            try {
                Object parsed = Json.parse(t);
                if (parsed instanceof Map<?, ?>) {
                    return EmulatorUtil.mutableMap(parsed);
                }
            } catch (studioquack.StudioQuackError e) {
                throw e;
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }
        if (raw instanceof Map<?, ?>) {
            return EmulatorUtil.mutableMap(raw);
        }
        return null;
    }

    /** ISO-8601 duration, or a plain integer meaning minutes. Null when invalid. */
    static Duration parseWindow(String raw) {
        String t = raw.trim();
        try {
            if (t.matches("\\d+")) {
                return Duration.ofMinutes(Long.parseLong(t));
            }
            return Duration.parse(t);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private static void putWindow(Map<String, Object> data, String windowRaw, Duration window,
                                  ZoneId zone) {
        data.put("timezone", zone.getId());
        if (window == null) {
            data.put("window", windowRaw == null ? "" : windowRaw);
            return;
        }
        long seconds = window.toSeconds();
        long now = Instant.now().getEpochSecond();
        long start = (now / seconds) * seconds;
        data.put("window", windowRaw);
        data.put("window_seconds", seconds);
        data.put("window_start", ZonedDateTime.ofInstant(Instant.ofEpochSecond(start), zone)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        data.put("window_end", ZonedDateTime.ofInstant(Instant.ofEpochSecond(start + seconds), zone)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    private static int countLines(FileChannel ch) throws IOException {
        long size = ch.size();
        if (size == 0) {
            return 0;
        }
        if (size > Integer.MAX_VALUE) {
            throw new IOException("buffer file too large");
        }
        byte[] bytes = new byte[(int) size];
        long pos = ch.position();
        ch.position(0);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
        while (buf.hasRemaining()) {
            int n = ch.read(buf);
            if (n < 0) {
                break;
            }
        }
        ch.position(pos);
        int lines = 0;
        for (byte b : bytes) {
            if (b == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
