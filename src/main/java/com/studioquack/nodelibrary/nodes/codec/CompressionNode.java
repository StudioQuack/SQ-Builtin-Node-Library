package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import studioquack.StudioQuackHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Archive compression over a platform file ref: {@code compress | decompress}
 * for {@code zip | gzip | tar | tgz}.
 *
 * <p>The source arrives as a type-{@code file} value resolved through
 * {@link FilesSupport#readFileValue} (sandbox path / ContentBlock / $fileRef —
 * never inline on the wire). Decompression rejects zip-slip/tar-slip entry
 * names and caps the cumulative payload at {@link FilesSupport#MAX_FILE_BYTES}
 * so a hostile archive cannot OOM the sandbox. Results leave as a type-{@code
 * file} ContentBlock on the {@code file} output field.
 */
public final class CompressionNode implements NodeHandler {

    public static final String DEFAULT_ID = "1fc1eadb-897a-47de-a1f3-a3986e4de255";

    private final String templateId;

    public CompressionNode() {
        this(DEFAULT_ID);
    }

    public CompressionNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
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
                return CodecSupport.error("operation is required. Accepted: [compress, decompress]");
            }
            String format = CodecSupport.lower(
                    DataSupport.inStr(in, "format", DataSupport.cfgStr(cfg, "format", "zip")), "zip");
            if (!List.of("zip", "gzip", "tar", "tgz").contains(format)) {
                return CodecSupport.error("Unknown format: " + format + ". Accepted: zip | gzip | tar | tgz");
            }
            String fileNameError = validateOutputFileName(in);
            if (fileNameError != null) {
                return CodecSupport.error(fileNameError);
            }
            String outName = DataSupport.inStr(in, "file_name", DataSupport.cfgStr(cfg, "file_name", null));
            FilesSupport.FileBytes file;
            try {
                file = FilesSupport.readFileValue(in.get("file"), "file");
            } catch (FilesSupport.Fail e) {
                return CodecSupport.error(e.getMessage());
            }

            return switch (op) {
                case "compress" -> compress(format, file, outName);
                case "decompress" -> decompress(format, file, outName);
                default -> CodecSupport.error("Unknown operation: " + op + ". Accepted: [compress, decompress]");
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("Error: " + e.getClass().getSimpleName());
        }
    }

    /**
     * When {@code file_name} is supplied on INPUT (even as empty string), validate before reading
     * the source file so name bugs surface without a materialised sandbox file.
     */
    private static String validateOutputFileName(Map<String, Object> in) {
        if (in == null || !in.containsKey("file_name")) {
            return null;
        }
        Object raw = in.get("file_name");
        String name = raw == null ? "" : String.valueOf(raw).trim();
        if (name.isEmpty()) {
            return "file_name must be non-empty when provided on INPUT";
        }
        if (name.length() > 255) {
            return "file_name must be at most 255 characters";
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return "file_name must be a single path segment (no / or ..)";
        }
        return null;
    }

    // --- compress ---------------------------------------------------------------

    private static Map<String, Object> compress(String format, FilesSupport.FileBytes file, String outName) {
        try {
            String entryName = file.name() == null || file.name().isBlank() ? "file" : file.name();
            byte[] result;
            String mime;
            String defaultName;
            switch (format) {
                case "gzip" -> {
                    result = gzip(file.bytes());
                    mime = "application/gzip";
                    defaultName = entryName + ".gz";
                }
                case "tar" -> {
                    result = Tar.write(entryName, file.bytes());
                    mime = "application/x-tar";
                    defaultName = entryName + ".tar";
                }
                case "tgz" -> {
                    result = gzip(Tar.write(entryName, file.bytes()));
                    mime = "application/gzip";
                    defaultName = entryName + ".tar.gz";
                }
                default -> {
                    result = zipSingle(entryName, file.bytes());
                    mime = "application/zip";
                    defaultName = entryName + ".zip";
                }
            }
            Map<String, Object> data = CodecSupport.data();
            data.put("operation", "compress");
            data.put("format", format);
            data.put("entry", entryName);
            data.put("original_size", file.bytes().length);
            data.put("result_size", result.length);
            return CodecSupport.okWithFile(data,
                    outName != null ? outName : defaultName, mime, result);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("compress failed: " + e.getClass().getSimpleName());
        }
    }

    // --- decompress -------------------------------------------------------------

    private static Map<String, Object> decompress(String format, FilesSupport.FileBytes file, String outName) {
        try {
            long cap = FilesSupport.MAX_FILE_BYTES;
            List<Tar.Entry> entries = new ArrayList<>();
            switch (format) {
                case "gzip" -> entries.add(new Tar.Entry(stripSuffix(file.name(), ".gz"), gunzip(file.bytes(), cap)));
                case "tar" -> entries.addAll(Tar.read(file.bytes(), cap));
                case "tgz" -> entries.addAll(Tar.read(gunzip(file.bytes(), cap), cap));
                default -> entries.addAll(unzip(file.bytes(), cap));
            }
            if (entries.isEmpty()) {
                return CodecSupport.error("archive contains no files");
            }
            List<Map<String, Object>> listing = new ArrayList<>();
            for (Tar.Entry e : entries) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("name", e.name());
                row.put("size", e.bytes().length);
                listing.add(row);
            }
            // Single-file archives emit that file; multi-entry archives emit the
            // first entry as `file` and the full listing under data.entries.
            Tar.Entry first = entries.get(0);
            Map<String, Object> data = CodecSupport.data();
            data.put("operation", "decompress");
            data.put("format", format);
            data.put("entry_count", entries.size());
            data.put("entries", listing);
            data.put("extracted", first.name());
            String name = outName != null ? outName : first.name();
            return CodecSupport.okWithFile(data, name, guessMime(first.name()), first.bytes());
        } catch (IllegalArgumentException e) {
            return CodecSupport.error("decompress failed: " + e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("decompress failed: not a valid " + format + " archive");
        }
    }

    // --- zip / gzip primitives ------------------------------------------------------

    private static byte[] zipSingle(String entryName, byte[] bytes) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(bytes);
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    private static List<Tar.Entry> unzip(byte[] zip, long cap) throws java.io.IOException {
        List<Tar.Entry> entries = new ArrayList<>();
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory()) {
                    continue;
                }
                String normalized = Tar.normalizeName(name);
                if (normalized == null) {
                    throw new IllegalArgumentException("unsafe entry name in archive: " + name);
                }
                byte[] bytes = zis.readNBytes((int) Math.min(cap + 1, Integer.MAX_VALUE - 1));
                total += bytes.length;
                if (total > cap) {
                    throw new IllegalArgumentException("archive payload exceeds the byte cap");
                }
                entries.add(new Tar.Entry(normalized, bytes));
            }
        }
        return entries;
    }

    private static byte[] gzip(byte[] bytes) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(bytes);
        }
        return bos.toByteArray();
    }

    private static byte[] gunzip(byte[] bytes, long cap) throws java.io.IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] out = gis.readNBytes((int) Math.min(cap + 1, Integer.MAX_VALUE - 1));
            if (out.length > cap) {
                throw new IllegalArgumentException("archive payload exceeds the byte cap");
            }
            return out;
        }
    }

    private static String stripSuffix(String name, String suffix) {
        if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(suffix)) {
            return name.substring(0, name.length() - suffix.length());
        }
        return name == null || name.isBlank() ? "file" : name;
    }

    /** FilesSupport.guessMime is package-private; keep the small table here. */
    private static String guessMime(String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".txt") || n.endsWith(".csv") || n.endsWith(".md")) {
            return "text/plain";
        }
        if (n.endsWith(".json")) {
            return "application/json";
        }
        if (n.endsWith(".html") || n.endsWith(".htm")) {
            return "text/html";
        }
        if (n.endsWith(".xml")) {
            return "application/xml";
        }
        if (n.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
