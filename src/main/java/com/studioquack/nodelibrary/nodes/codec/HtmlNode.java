package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML node: {@code template | extract | to_table}. Pure JDK — no crawler, no
 * network.
 *
 * <p>{@code template} is a Mustache-like variable substituter over the
 * {@code item} object: {@code {{name}}} HTML-escaped (the XSS-safe default),
 * {@code {{{name}}}} raw, dotted paths ({{a.b}}) supported. {@code extract}
 * CSS-selects from the document (whole-document text when no selector is set).
 * {@code to_table} emits the selected table(s) as row/cell arrays. HTML comes
 * from the {@code html} input field, or from a type-{@code file} ref when
 * {@code source=file}.
 */
public final class HtmlNode implements NodeHandler {

    public static final String DEFAULT_ID = "9c0e85a8-e56b-4f7e-8213-f7fd99ee5acb";

    /** Bound extract payloads so a pathological document cannot bloat a run. */
    private static final int MAX_MATCHES = 500;

    private static final Pattern RAW_VAR = Pattern.compile("\\{\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}}");
    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final String templateId;

    public HtmlNode() {
        this(DEFAULT_ID);
    }

    public HtmlNode(String templateId) {
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
        Map<String, Object> in = DataSupport.overlayConfig(
                DataSupport.readInput(request, helper), cfg, "template", "item", "html", "file");
        Object itemRaw = in.get("item");
        if (itemRaw instanceof String itemText) {
            String trimmed = itemText.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    in.put("item", studioquack.Json.parse(trimmed));
                } catch (RuntimeException ignored) {
                    // template() treats a non-object item as an empty model
                }
            }
        }
        try {
            String op = CodecSupport.op(cfg, in);
            if (op == null) {
                return CodecSupport.error("operation is required. Accepted: [template, extract, to_table]");
            }
            return switch (op) {
                case "template" -> template(in);
                case "extract" -> extract(cfg, in);
                case "to_table" -> toTable(cfg, in);
                default -> CodecSupport.error("Unknown operation: " + op
                        + ". Accepted: [template, extract, to_table]");
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("Error: " + e.getClass().getSimpleName());
        }
    }

    // --- template ---------------------------------------------------------------

    private static Map<String, Object> template(Map<String, Object> in) {
        String template = DataSupport.inStr(in, "template", null);
        if (template == null) {
            return CodecSupport.error("template is required for operation template");
        }
        Object item = in.get("item");
        Map<String, Object> data = item instanceof Map<?, ?> m
                ? com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil.asMap(m) : Map.of();

        // Raw triple-mustache first, then the escaped default on the remainder.
        StringBuffer sb = new StringBuffer();
        Matcher raw = RAW_VAR.matcher(template);
        while (raw.find()) {
            raw.appendReplacement(sb, Matcher.quoteReplacement(
                    lookup(data, raw.group(1))));
        }
        raw.appendTail(sb);
        Matcher esc = VAR.matcher(sb.toString());
        StringBuffer out = new StringBuffer();
        while (esc.find()) {
            esc.appendReplacement(out, Matcher.quoteReplacement(
                    HtmlMini.escape(lookup(data, esc.group(1)))));
        }
        esc.appendTail(out);

        Map<String, Object> result = CodecSupport.data();
        result.put("html", out.toString());
        return CodecSupport.ok(result);
    }

    private static String lookup(Map<String, Object> data, String path) {
        Object cur = data;
        for (String key : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m)) {
                return "";
            }
            cur = m.get(key);
        }
        return cur == null ? "" : String.valueOf(cur);
    }

    // --- extract ------------------------------------------------------------------

    private static Map<String, Object> extract(Map<String, Object> cfg, Map<String, Object> in) {
        String html = htmlSource(cfg, in);
        if (html == null) {
            return CodecSupport.error("html input (or a file ref with source=file) is required");
        }
        String selector = DataSupport.inStr(in, "css_selector",
                DataSupport.cfgStr(cfg, "css_selector", null));
        HtmlMini.El root = HtmlMini.parse(html);
        Map<String, Object> data = CodecSupport.data();
        if (selector == null) {
            // No selector: whole-document text extraction (tag strip + entity decode).
            data.put("selector", "");
            data.put("text", normalize(HtmlMini.text(root)));
            data.put("count", 1);
            return CodecSupport.ok(data);
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        for (HtmlMini.El el : HtmlMini.select(root, selector)) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("tag", el.tag);
            row.put("attributes", el.attrs);
            row.put("text", normalize(HtmlMini.text(el)));
            row.put("html", HtmlMini.outerHtml(el));
            matches.add(row);
            if (matches.size() >= MAX_MATCHES) {
                break;
            }
        }
        data.put("selector", selector);
        data.put("matches", matches);
        data.put("count", matches.size());
        return CodecSupport.ok(data);
    }

    // --- to_table -------------------------------------------------------------------

    private static Map<String, Object> toTable(Map<String, Object> cfg, Map<String, Object> in) {
        String html = htmlSource(cfg, in);
        if (html == null) {
            return CodecSupport.error("html input (or a file ref with source=file) is required");
        }
        String selector = DataSupport.inStr(in, "css_selector",
                DataSupport.cfgStr(cfg, "css_selector", "table"));
        HtmlMini.El root = HtmlMini.parse(html);
        List<HtmlMini.El> tables = HtmlMini.select(root, selector);
        List<Map<String, Object>> out = new ArrayList<>();
        for (HtmlMini.El table : tables) {
            if (!"table".equals(table.tag)) {
                continue;
            }
            Map<String, Object> t = new java.util.LinkedHashMap<>();
            List<List<String>> headers = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();
            for (HtmlMini.El tr : HtmlMini.select(table, "tr")) {
                List<String> cells = new ArrayList<>();
                boolean headerRow = false;
                for (HtmlMini.El cell : tr.children.stream()
                        .filter(HtmlMini.El.class::isInstance)
                        .map(HtmlMini.El.class::cast).toList()) {
                    if ("th".equals(cell.tag)) {
                        headerRow = true;
                    }
                    if ("td".equals(cell.tag) || "th".equals(cell.tag)) {
                        cells.add(normalize(HtmlMini.text(cell)));
                    }
                }
                if (cells.isEmpty()) {
                    continue;
                }
                if (headerRow && rows.isEmpty()) {
                    headers.add(cells);
                } else {
                    rows.add(cells);
                }
                if (rows.size() >= MAX_MATCHES) {
                    break;
                }
            }
            t.put("headers", headers.isEmpty() ? List.of() : headers.get(0));
            t.put("rows", rows);
            t.put("row_count", rows.size());
            out.add(t);
            if (out.size() >= 100) {
                break;
            }
        }
        Map<String, Object> data = CodecSupport.data();
        data.put("selector", selector);
        data.put("tables", out);
        data.put("count", out.size());
        return CodecSupport.ok(data);
    }

    // --- helpers ------------------------------------------------------------------------

    private static String htmlSource(Map<String, Object> cfg, Map<String, Object> in) {
        String source = CodecSupport.lower(DataSupport.cfgStr(cfg, "source", "json"), "json");
        if ("file".equals(source)) {
            try {
                FilesSupport.FileBytes fb = FilesSupport.readFileValue(in.get("file"), "file");
                return new String(fb.bytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (FilesSupport.Fail e) {
                return null;
            }
        }
        return DataSupport.inStr(in, "html", null);
    }

    /** Collapse intra-line whitespace, trim lines, drop empty runs. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String[] lines = text.replace('\r', '\n').split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.replaceAll("[ \\t\\u00a0]+", " ").trim();
            if (!t.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(t);
            }
        }
        return sb.toString();
    }
}
