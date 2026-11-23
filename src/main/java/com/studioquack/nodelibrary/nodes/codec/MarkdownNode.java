package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown ↔ HTML: {@code md_to_html | html_to_md}. Pure JDK, no dependencies.
 *
 * <p>md_to_html covers headings, paragraphs, **bold**, *italic*, `code`,
 * fenced code blocks, links, images, unordered/ordered lists, blockquotes, and
 * GFM pipe tables (config {@code tables} and {@code gfm}, both default on).
 * Source text is HTML-escaped before inline formatting so markdown cannot
 * inject markup. html_to_md walks the {@link HtmlMini} mini-DOM back to
 * CommonMark.
 */
public final class MarkdownNode implements NodeHandler {

    public static final String DEFAULT_ID = "1b4d5377-2b69-458a-821f-43a49802e6e0";

    /** NUL sentinel: impossible in escaped source text, safe inside the pattern engine. */
    private static final String SENTINEL = "\u0000";

    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^]]*)]\\(([^)\\s]+)\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^]]*)]\\(([^)\\s]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("\\*([^*\\s][^*]*)\\*");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern OL_ITEM = Pattern.compile("^\\d+[.)]\\s+(.*)$");
    private static final Pattern TABLE_DELIM = Pattern.compile("^\\|?[\\s:|-]+\\|?$");

    private final String templateId;

    public MarkdownNode() {
        this(DEFAULT_ID);
    }

    public MarkdownNode(String templateId) {
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
                DataSupport.readInput(request, helper), cfg, "markdown", "html");
        try {
            String op = CodecSupport.op(cfg, in);
            if (op == null) {
                return CodecSupport.error("operation is required. Accepted: [md_to_html, html_to_md]");
            }
            boolean tables = bool(cfg, "tables", true) && bool(cfg, "gfm", true);
            return switch (op) {
                case "md_to_html" -> {
                    String md = DataSupport.inStr(in, "markdown", null);
                    if (md == null) {
                        yield CodecSupport.error("markdown is required for operation md_to_html");
                    }
                    Map<String, Object> data = CodecSupport.data();
                    data.put("html", mdToHtml(md, tables));
                    yield CodecSupport.ok(data);
                }
                case "html_to_md" -> {
                    String html = DataSupport.inStr(in, "html", null);
                    if (html == null) {
                        yield CodecSupport.error("html is required for operation html_to_md");
                    }
                    Map<String, Object> data = CodecSupport.data();
                    data.put("markdown", htmlToMd(html, tables));
                    yield CodecSupport.ok(data);
                }
                default -> CodecSupport.error("Unknown operation: " + op
                        + ". Accepted: [md_to_html, html_to_md]");
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("Error: " + e.getClass().getSimpleName());
        }
    }

    private static boolean bool(Map<String, Object> cfg, String key, boolean fallback) {
        Object v = cfg.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return fallback;
        }
        return com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil.isTruthy(v);
    }

    // --- md → html ------------------------------------------------------------------

    static String mdToHtml(String markdown, boolean tables) {
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.isBlank()) {
                i++;
                continue;
            }
            if (line.stripLeading().startsWith("```")) {
                String lang = line.stripLeading().substring(3).trim();
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].stripLeading().startsWith("```")) {
                    code.append(lines[i]).append('\n');
                    i++;
                }
                i++; // closing fence
                out.append("<pre><code");
                if (!lang.isEmpty()) {
                    out.append(" class=\"language-").append(HtmlMini.escape(lang)).append('"');
                }
                String body = code.toString();
                if (body.endsWith("\n")) {
                    body = body.substring(0, body.length() - 1);
                }
                out.append('>').append(HtmlMini.escape(body)).append("</code></pre>\n");
                continue;
            }
            Matcher h = HEADING.matcher(line);
            if (h.matches()) {
                int level = h.group(1).length();
                out.append("<h").append(level).append('>')
                        .append(inline(h.group(2).trim()))
                        .append("</h").append(level).append(">\n");
                i++;
                continue;
            }
            if (tables && line.contains("|") && i + 1 < lines.length
                    && TABLE_DELIM.matcher(lines[i + 1].trim()).matches()
                    && lines[i + 1].contains("-")) {
                StringBuilder table = new StringBuilder("<table>\n<thead>\n<tr>");
                for (String cell : splitRow(line)) {
                    table.append("<th>").append(inline(cell)).append("</th>");
                }
                table.append("</tr>\n</thead>\n<tbody>\n");
                i += 2;
                while (i < lines.length && lines[i].contains("|") && !lines[i].isBlank()) {
                    table.append("<tr>");
                    for (String cell : splitRow(lines[i])) {
                        table.append("<td>").append(inline(cell)).append("</td>");
                    }
                    table.append("</tr>\n");
                    i++;
                }
                table.append("</tbody>\n</table>\n");
                out.append(table);
                continue;
            }
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                out.append("<ul>\n");
                while (i < lines.length) {
                    String t = lines[i].stripLeading();
                    if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) {
                        out.append("<li>").append(inline(t.substring(2).trim())).append("</li>\n");
                        i++;
                    } else {
                        break;
                    }
                }
                out.append("</ul>\n");
                continue;
            }
            if (OL_ITEM.matcher(trimmed).matches()) {
                out.append("<ol>\n");
                while (i < lines.length) {
                    Matcher m = OL_ITEM.matcher(lines[i].stripLeading());
                    if (m.matches()) {
                        out.append("<li>").append(inline(m.group(1).trim())).append("</li>\n");
                        i++;
                    } else {
                        break;
                    }
                }
                out.append("</ol>\n");
                continue;
            }
            if (trimmed.startsWith(">")) {
                StringBuilder quote = new StringBuilder();
                while (i < lines.length && lines[i].stripLeading().startsWith(">")) {
                    String q = lines[i].stripLeading().substring(1);
                    quote.append(q.startsWith(" ") ? q.substring(1) : q).append('\n');
                    i++;
                }
                out.append("<blockquote>\n<p>")
                        .append(inline(quote.toString().trim()))
                        .append("</p>\n</blockquote>\n");
                continue;
            }
            // Paragraph: gather until a blank line or a new block start.
            StringBuilder para = new StringBuilder();
            while (i < lines.length && !lines[i].isBlank()
                    && !HEADING.matcher(lines[i]).matches()
                    && !lines[i].stripLeading().startsWith("```")
                    && !lines[i].stripLeading().startsWith(">")) {
                String t = lines[i].stripLeading();
                if (para.length() > 0 && (t.startsWith("- ") || t.startsWith("* ")
                        || OL_ITEM.matcher(t).matches())) {
                    break;
                }
                if (para.length() > 0) {
                    para.append('\n');
                }
                para.append(lines[i].trim());
                i++;
            }
            out.append("<p>").append(inline(para.toString())).append("</p>\n");
        }
        return out.toString().trim();
    }

    private static List<String> splitRow(String row) {
        String r = row.trim();
        if (r.startsWith("|")) {
            r = r.substring(1);
        }
        if (r.endsWith("|")) {
            r = r.substring(0, r.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : r.split("\\|", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }

    /**
     * Inline formatting. Source is HTML-escaped first; code spans are lifted
     * behind NUL sentinels so emphasis rules never touch their contents.
     */
    static String inline(String raw) {
        String s = HtmlMini.escape(raw);
        List<String> codes = new ArrayList<>();
        Matcher cm = CODE_SPAN.matcher(s);
        StringBuffer lifted = new StringBuffer();
        while (cm.find()) {
            codes.add(cm.group(1));
            cm.appendReplacement(lifted,
                    Matcher.quoteReplacement(SENTINEL + (codes.size() - 1) + SENTINEL));
        }
        cm.appendTail(lifted);
        s = lifted.toString();
        s = IMAGE.matcher(s).replaceAll(m ->
                "<img alt=\"" + m.group(1) + "\" src=\"" + m.group(2) + "\">");
        s = LINK.matcher(s).replaceAll(m ->
                "<a href=\"" + m.group(2) + "\">" + m.group(1) + "</a>");
        s = BOLD.matcher(s).replaceAll("<strong>$1</strong>");
        s = ITALIC.matcher(s).replaceAll("<em>$1</em>");
        for (int idx = 0; idx < codes.size(); idx++) {
            s = s.replace(SENTINEL + idx + SENTINEL, "<code>" + codes.get(idx) + "</code>");
        }
        return s;
    }

    // --- html → md ---------------------------------------------------------------------

    static String htmlToMd(String html, boolean tables) {
        HtmlMini.El root = HtmlMini.parse(html);
        StringBuilder sb = new StringBuilder();
        renderChildren(root, sb, tables);
        return sb.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static void renderChildren(HtmlMini.El el, StringBuilder sb, boolean tables) {
        for (Object child : el.children) {
            if (child instanceof String s) {
                sb.append(HtmlMini.unescape(s).replaceAll("[ \\t\\u00a0]+", " "));
            } else if (child instanceof HtmlMini.El childEl) {
                render(childEl, sb, tables);
            }
        }
    }

    private static void render(HtmlMini.El el, StringBuilder sb, boolean tables) {
        switch (el.tag) {
            case "script", "style" -> {
                // never carried into markdown
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                sb.append("\n\n").append("#".repeat(el.tag.charAt(1) - '0')).append(' ');
                renderChildren(el, sb, tables);
                sb.append("\n\n");
            }
            case "p" -> {
                sb.append("\n\n");
                renderChildren(el, sb, tables);
                sb.append("\n\n");
            }
            case "br" -> sb.append("  \n");
            case "hr" -> sb.append("\n\n---\n\n");
            case "strong", "b" -> wrap(sb, "**", el, tables);
            case "em", "i" -> wrap(sb, "*", el, tables);
            case "code" -> {
                if (el.parent == null || !"pre".equals(el.parent.tag)) {
                    sb.append('`').append(HtmlMini.text(el)).append('`');
                } else {
                    renderChildren(el, sb, tables);
                }
            }
            case "pre" -> sb.append("\n\n```\n").append(HtmlMini.text(el)).append("\n```\n\n");
            case "a" -> {
                String href = el.attr("href");
                sb.append('[');
                renderChildren(el, sb, tables);
                sb.append("](").append(href == null ? "" : href).append(')');
            }
            case "img" -> sb.append("![").append(el.attr("alt") == null ? "" : el.attr("alt"))
                    .append("](").append(el.attr("src") == null ? "" : el.attr("src")).append(')');
            case "ul" -> {
                sb.append('\n');
                for (HtmlMini.El li : childElements(el, "li")) {
                    sb.append("- ");
                    renderChildren(li, sb, tables);
                    sb.append('\n');
                }
            }
            case "ol" -> {
                sb.append('\n');
                int n = 1;
                for (HtmlMini.El li : childElements(el, "li")) {
                    sb.append(n++).append(". ");
                    renderChildren(li, sb, tables);
                    sb.append('\n');
                }
            }
            case "blockquote" -> {
                StringBuilder inner = new StringBuilder();
                renderChildren(el, inner, tables);
                for (String line : inner.toString().trim().split("\n")) {
                    sb.append("> ").append(line).append('\n');
                }
            }
            case "table" -> {
                if (tables) {
                    renderTable(el, sb);
                } else {
                    renderChildren(el, sb, false);
                }
            }
            default -> renderChildren(el, sb, tables);
        }
    }

    private static void wrap(StringBuilder sb, String marker, HtmlMini.El el, boolean tables) {
        StringBuilder inner = new StringBuilder();
        renderChildren(el, inner, tables);
        String t = inner.toString();
        if (t.isBlank()) {
            sb.append(t);
        } else {
            sb.append(marker).append(t).append(marker);
        }
    }

    private static List<HtmlMini.El> childElements(HtmlMini.El el, String tag) {
        List<HtmlMini.El> out = new ArrayList<>();
        for (Object child : el.children) {
            if (child instanceof HtmlMini.El childEl && childEl.tag.equals(tag)) {
                out.add(childEl);
            }
        }
        return out;
    }

    private static void renderTable(HtmlMini.El table, StringBuilder sb) {
        List<List<String>> rows = new ArrayList<>();
        for (HtmlMini.El tr : HtmlMini.select(table, "tr")) {
            List<String> cells = new ArrayList<>();
            for (Object child : tr.children) {
                if (child instanceof HtmlMini.El cell
                        && ("th".equals(cell.tag) || "td".equals(cell.tag))) {
                    cells.add(HtmlNode.normalize(HtmlMini.text(cell)));
                }
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        sb.append("\n\n");
        List<String> header = rows.get(0);
        sb.append("| ").append(String.join(" | ", header)).append(" |\n");
        sb.append("|").append(" --- |".repeat(header.size())).append('\n');
        for (int r = 1; r < rows.size(); r++) {
            sb.append("| ").append(String.join(" | ", rows.get(r))).append(" |\n");
        }
        sb.append('\n');
    }
}
