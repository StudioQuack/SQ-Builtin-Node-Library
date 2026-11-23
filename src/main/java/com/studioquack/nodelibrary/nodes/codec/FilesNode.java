package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import studioquack.StudioQuackHelper;

import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Sandbox filesystem node: {@code read | write | list | delete | watch}.
 *
 * <p>Confinement is the security invariant: every path resolves against the
 * sandbox root ({@code SQ_FILES_ROOT} env, default {@link FilesSupport#SANDBOX_FILES})
 * and then the configured {@code base_dir}; normalized results must stay inside
 * {@code base_dir} — absolute host paths and {@code ..} escapes are refused.
 * {@code watch} is a trigger, not request/response: it returns the BPMN
 * message-event guidance (same convention as webhook ingress).
 */
public final class FilesNode implements NodeHandler {

    public static final String DEFAULT_ID = "dd4217bc-4e64-4eff-9e48-677595c28e6a";

    /** Hard cap on list entries so a huge directory cannot bloat the payload. */
    private static final int MAX_LIST_ENTRIES = 1000;

    private final String templateId;
    private final Path root;

    public FilesNode() {
        this(DEFAULT_ID);
    }

    public FilesNode(String templateId) {
        this(templateId, defaultRoot());
    }

    /** Test seam: explicit sandbox root. */
    public FilesNode(String templateId, Path root) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
        this.root = root.toAbsolutePath().normalize();
    }

    private static Path defaultRoot() {
        String env = System.getenv("SQ_FILES_ROOT");
        return Path.of(env == null || env.isBlank() ? FilesSupport.SANDBOX_FILES : env.trim());
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
            String op = effectiveOperation(CodecSupport.op(cfg, in), in);
            if (op == null || op.isBlank()) {
                return CodecSupport.error("operation is required. Accepted: [read, write, list, delete, watch]");
            }
            switch (op) {
                case "read" -> {
                    return read(cfg, in);
                }
                case "write" -> {
                    return write(cfg, in);
                }
                case "list" -> {
                    return list(cfg, in);
                }
                case "delete" -> {
                    return delete(cfg, in);
                }
                case "watch" -> {
                    return CodecSupport.error(
                            "Inbound file watching is a trigger, not a request/response operation. "
                                    + "Use a BPMN message start / intermediate catch / boundary message event; "
                                    + "the daemon's file-watch ingress correlates onto those events. For a "
                                    + "timer-driven poll, use the 'list' operation (watch_glob applies).");
                }
                default -> {
                    return CodecSupport.error("Unknown operation: " + op
                            + ". Accepted: [read, write, list, delete, watch]");
                }
            }
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("Error: " + e.getClass().getSimpleName());
        }
    }

    // --- operations ---------------------------------------------------------------

    private Map<String, Object> read(Map<String, Object> cfg, Map<String, Object> in) {
        ResolveResult resolved = resolveFileTarget(cfg, DataSupport.inStr(in, "path", null));
        if (resolved.error != null) {
            return CodecSupport.error(resolved.error);
        }
        Path target = resolved.path;
        try {
            if (!Files.isRegularFile(target)) {
                return CodecSupport.error("file not found: " + relative(target));
            }
            long size = Files.size(target);
            if (size > FilesSupport.MAX_FILE_BYTES) {
                return CodecSupport.error("file is " + size + " bytes, above the "
                        + FilesSupport.MAX_FILE_BYTES + " byte node limit");
            }
            byte[] bytes = Files.readAllBytes(target);
            String name = target.getFileName().toString();
            Map<String, Object> data = CodecSupport.data();
            data.put("path", relative(target));
            data.put("name", name);
            data.put("size", bytes.length);
            data.put("mime_type", mime(name));
            return CodecSupport.okWithFile(data, name, mime(name), bytes);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("read failed: " + e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> write(Map<String, Object> cfg, Map<String, Object> in) {
        ResolveResult resolved = resolveFileTarget(cfg, DataSupport.inStr(in, "path", null));
        if (resolved.error != null) {
            return CodecSupport.error(resolved.error);
        }
        Path target = resolved.path;
        Path base = baseDir(cfg);
        if (base != null && target.equals(base)) {
            return CodecSupport.error("path must name a file, not the base_dir itself");
        }
        try {
            byte[] bytes;
            Object fileRaw = in.get("file");
            boolean textRequested = in.containsKey("text") && in.get("text") != null;
            if (fileRaw != null && !String.valueOf(fileRaw).isBlank()) {
                bytes = FilesSupport.readFileValue(fileRaw, "file").bytes();
            } else if (textRequested) {
                Charset cs = charset(DataSupport.cfgStr(cfg, "encoding", "UTF-8"));
                if (cs == null) {
                    return CodecSupport.error("Unknown encoding: " + DataSupport.cfgStr(cfg, "encoding", ""));
                }
                String text = com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil.str(in.get("text"));
                bytes = text.getBytes(cs);
            } else {
                return CodecSupport.error("write requires a file ref or text");
            }
            if (bytes.length > FilesSupport.MAX_FILE_BYTES) {
                return CodecSupport.error("payload exceeds the " + FilesSupport.MAX_FILE_BYTES + " byte node limit");
            }
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, bytes);
            Map<String, Object> data = CodecSupport.data();
            data.put("path", relative(target));
            data.put("size", bytes.length);
            data.put("written", true);
            return CodecSupport.ok(data);
        } catch (FilesSupport.Fail e) {
            return CodecSupport.error(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("write failed: " + e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> list(Map<String, Object> cfg, Map<String, Object> in) {
        String relPath = DataSupport.inStr(in, "path", null);
        ResolveResult resolved = resolveDirTarget(cfg, relPath);
        if (resolved.error != null) {
            return CodecSupport.error(resolved.error);
        }
        Path target = resolved.path;
        String glob = DataSupport.cfgStr(cfg, "watch_glob", null);
        PathMatcher matcher = null;
        if (glob != null) {
            try {
                matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
                return CodecSupport.error("watch_glob is not a valid glob: " + glob);
            }
        }
        try (Stream<Path> stream = Files.list(target)) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Path p : stream.sorted(Comparator.comparing(Path::toString)).toList()) {
                String name = p.getFileName() == null ? "" : p.getFileName().toString();
                if (matcher != null && !matcher.matches(p.getFileName())) {
                    continue;
                }
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("name", name);
                row.put("path", relative(p));
                row.put("directory", Files.isDirectory(p));
                row.put("size", Files.isRegularFile(p) ? Files.size(p) : 0);
                row.put("last_modified", Files.getLastModifiedTime(p).toMillis());
                entries.add(row);
                if (entries.size() >= MAX_LIST_ENTRIES) {
                    break;
                }
            }
            Map<String, Object> data = CodecSupport.data();
            data.put("dir", relative(target));
            data.put("entries", entries);
            data.put("count", entries.size());
            if (glob != null) {
                data.put("glob", glob);
            }
            return CodecSupport.ok(data);
        } catch (java.nio.file.NotDirectoryException | java.nio.file.NoSuchFileException e) {
            return CodecSupport.error("directory not found: " + relative(target));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("list failed: " + e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> delete(Map<String, Object> cfg, Map<String, Object> in) {
        ResolveResult resolved = resolveFileTarget(cfg, DataSupport.inStr(in, "path", null));
        if (resolved.error != null) {
            return CodecSupport.error(resolved.error);
        }
        Path target = resolved.path;
        if (target.equals(baseDir(cfg)) || target.equals(root)) {
            return CodecSupport.error("refusing to delete the base_dir / sandbox root itself");
        }
        try {
            if (!Files.exists(target)) {
                return CodecSupport.error("path not found: " + relative(target));
            }
            Files.delete(target); // non-empty directories fail here — by design
            Map<String, Object> data = CodecSupport.data();
            data.put("path", relative(target));
            data.put("deleted", true);
            return CodecSupport.ok(data);
        } catch (java.nio.file.DirectoryNotEmptyException e) {
            return CodecSupport.error("directory is not empty: " + relative(target));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("delete failed: " + e.getClass().getSimpleName());
        }
    }

    // --- confinement ------------------------------------------------------------------

    /** Configured base_dir resolved inside the sandbox root; null on escape. */
    private Path baseDir(Map<String, Object> cfg) {
        String base = DataSupport.cfgStr(cfg, "base_dir", ".");
        if (base == null || base.isBlank()) {
            base = ".";
        }
        Path b = Path.of(base);
        if (b.isAbsolute()) {
            return null; // host paths rejected
        }
        Path resolved = root.resolve(b).normalize();
        if (!resolved.startsWith(root)) {
            return null;
        }
        return resolved;
    }

    /**
     * Agent tools often send {@code text} without {@code operation}; treat that as
     * {@code write} even when instance config defaults to {@code read}.
     */
    static String effectiveOperation(String configured, Map<String, Object> in) {
        String fromInput = in == null ? null : DataSupport.inStr(in, "operation", null);
        if (fromInput != null && !fromInput.isBlank()) {
            return fromInput.trim().toLowerCase(Locale.ROOT);
        }
        if (configured != null && !configured.isBlank()) {
            String c = configured.trim().toLowerCase(Locale.ROOT);
            if (!"read".equals(c)) {
                return c;
            }
        }
        if (in != null && in.containsKey("text")) {
            return "write";
        }
        return configured == null || configured.isBlank()
                ? "read"
                : configured.trim().toLowerCase(Locale.ROOT);
    }

    private record ResolveResult(Path path, String error) {
    }

    private ResolveResult resolveFileTarget(Map<String, Object> cfg, String relPath) {
        if (relPath == null || relPath.isBlank()) {
            return new ResolveResult(null, "path is required.");
        }
        return resolveUnderBase(cfg, relPath, false);
    }

    private ResolveResult resolveDirTarget(Map<String, Object> cfg, String relPath) {
        if (relPath == null || relPath.isBlank()) {
            Path base = baseDir(cfg);
            if (base == null) {
                return new ResolveResult(null,
                        "path escapes the configured base_dir (or is an absolute host path)");
            }
            return new ResolveResult(base, null);
        }
        return resolveUnderBase(cfg, relPath, true);
    }

  /** Resolve a sandbox-relative path under base_dir. */
    private ResolveResult resolveUnderBase(Map<String, Object> cfg, String relPath, boolean allowDirectoryPath) {
        Path base = baseDir(cfg);
        if (base == null) {
            return new ResolveResult(null,
                    "path escapes the configured base_dir (or is an absolute host path)");
        }
        String norm = normalizeRelative(relPath);
        if (norm.isEmpty()) {
            return new ResolveResult(null, "path is required.");
        }
        if (!allowDirectoryPath && ".".equals(norm)) {
            return new ResolveResult(null, "path must name a file, not '.' or the base_dir itself");
        }
        if (norm.contains("..")) {
            return new ResolveResult(null,
                    "path escapes the configured base_dir (or is an absolute host path)");
        }
        Path p = Path.of(norm);
        if (p.isAbsolute()) {
            return new ResolveResult(null,
                    "path escapes the configured base_dir (or is an absolute host path)");
        }
        Path resolved = base.resolve(p).normalize();
        if (!resolved.startsWith(base)) {
            return new ResolveResult(null,
                    "path escapes the configured base_dir (or is an absolute host path)");
        }
        if (!allowDirectoryPath && resolved.equals(base)) {
            return new ResolveResult(null, "path must name a file, not the base_dir itself");
        }
        return new ResolveResult(resolved, null);
    }

    static String normalizeRelative(String relPath) {
        if (relPath == null) {
            return "";
        }
        String norm = relPath.trim().replace('\\', '/');
        while (norm.startsWith("./")) {
            norm = norm.substring(2);
        }
        if (norm.endsWith("/") && norm.length() > 1) {
            norm = norm.substring(0, norm.length() - 1);
        }
        return norm;
    }

    private String relative(Path p) {
        Path abs = p.toAbsolutePath().normalize();
        String rel = abs.startsWith(root) ? root.relativize(abs).toString() : abs.toString();
        return rel.replace('\\', '/');
    }

    private static Charset charset(String name) {
        try {
            return Charset.forName(name == null || name.isBlank() ? "UTF-8" : name.trim());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private static String mime(String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".json")) {
            return "application/json";
        }
        if (n.endsWith(".xml")) {
            return "application/xml";
        }
        if (n.endsWith(".html") || n.endsWith(".htm")) {
            return "text/html";
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
        if (n.endsWith(".txt") || n.endsWith(".csv") || n.endsWith(".md") || n.endsWith(".log")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }
}
