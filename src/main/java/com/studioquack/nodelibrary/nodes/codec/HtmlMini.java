package com.studioquack.nodelibrary.nodes.codec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tolerant, dependency-free HTML mini-DOM: parser, text extraction, entity
 * codec, and a small CSS-selector subset (tag, {@code *}, .class, #id,
 * [attr], [attr=value], descendant combinator).
 *
 * <p>Not a browser: no scripting, no network, no DTD fetch. {@code script} and
 * {@code style} subtrees are parsed but excluded from {@link #text}. Attribute
 * values and text are entity-decoded on read; {@link #escape} re-encodes for
 * safe interpolation.
 */
final class HtmlMini {

    private HtmlMini() {
        // utility — never instantiated
    }

    /** Element node; children are {@code El} or {@code String} (raw text). */
    static final class El {
        final String tag;
        final Map<String, String> attrs = new LinkedHashMap<>();
        final List<Object> children = new ArrayList<>();
        El parent;

        El(String tag) {
            this.tag = tag;
        }

        String attr(String name) {
            return attrs.get(name);
        }
    }

    private static final List<String> VOID = List.of("area", "base", "br", "col", "embed",
            "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr");
    private static final List<String> RAW_TEXT = List.of("script", "style", "textarea", "title");
    private static final List<String> SKIP_TEXT = List.of("script", "style");
    private static final List<String> BLOCK = List.of("p", "div", "section", "article", "header",
            "footer", "main", "aside", "nav", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li",
            "table", "thead", "tbody", "tfoot", "tr", "td", "th", "blockquote", "pre", "form",
            "fieldset", "figure", "figcaption", "dl", "dt", "dd", "hr", "br");

    // --- parser -----------------------------------------------------------------

    static El parse(String html) {
        El root = new El("#root");
        if (html == null || html.isEmpty()) {
            return root;
        }
        El cur = root;
        StringBuilder text = new StringBuilder();
        int i = 0;
        int n = html.length();
        while (i < n) {
            int lt = html.indexOf('<', i);
            if (lt < 0) {
                text.append(html, i, n);
                break;
            }
            text.append(html, i, lt);
            if (html.startsWith("<!--", lt)) {
                int end = html.indexOf("-->", lt + 4);
                i = end < 0 ? n : end + 3;
                continue;
            }
            if (lt + 1 < n && (html.charAt(lt + 1) == '!' || html.charAt(lt + 1) == '?')) {
                int gt = html.indexOf('>', lt);
                i = gt < 0 ? n : gt + 1;
                continue;
            }
            int gt = tagEnd(html, lt);
            if (gt < 0) {
                text.append(html, lt, n);
                break;
            }
            flushText(cur, text);
            String inside = html.substring(lt + 1, gt).trim();
            i = gt + 1;
            if (inside.isEmpty()) {
                continue;
            }
            if (inside.charAt(0) == '/') {
                String name = inside.substring(1).trim().toLowerCase(Locale.ROOT);
                El walk = cur;
                while (walk != root && !walk.tag.equals(name)) {
                    walk = walk.parent;
                }
                if (walk != root) {
                    cur = walk.parent;
                }
                continue;
            }
            boolean selfClose = inside.endsWith("/");
            if (selfClose) {
                inside = inside.substring(0, inside.length() - 1).trim();
            }
            int sp = firstSpace(inside);
            String name = (sp < 0 ? inside : inside.substring(0, sp)).toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            // HTML auto-closing for the table/list/paragraph cases this library serves.
            while (cur != root && closesFor(cur.tag, name)) {
                cur = cur.parent;
            }
            El el = new El(name);
            el.parent = cur;
            cur.children.add(el);
            if (sp >= 0) {
                parseAttrs(inside.substring(sp + 1), el.attrs);
            }
            if (selfClose || VOID.contains(name)) {
                continue;
            }
            if (RAW_TEXT.contains(name)) {
                int close = indexOfIgnoreCase(html, "</" + name, i);
                String raw = close < 0 ? html.substring(i) : html.substring(i, close);
                if (!raw.isEmpty()) {
                    el.children.add(raw);
                }
                if (close < 0) {
                    i = n;
                } else {
                    int closeGt = html.indexOf('>', close);
                    i = closeGt < 0 ? n : closeGt + 1;
                }
                continue;
            }
            cur = el;
        }
        flushText(cur, text);
        return root;
    }

    private static boolean closesFor(String open, String incoming) {
        return switch (open) {
            case "li" -> "li".equals(incoming);
            case "tr" -> "tr".equals(incoming) || "tbody".equals(incoming) || "thead".equals(incoming)
                    || "tfoot".equals(incoming) || "table".equals(incoming);
            case "td", "th" -> "td".equals(incoming) || "th".equals(incoming) || "tr".equals(incoming)
                    || "tbody".equals(incoming) || "thead".equals(incoming) || "tfoot".equals(incoming)
                    || "table".equals(incoming);
            case "p" -> BLOCK.contains(incoming);
            case "option" -> "option".equals(incoming) || "optgroup".equals(incoming);
            case "thead", "tbody", "tfoot" -> "tbody".equals(incoming) || "tfoot".equals(incoming)
                    || "table".equals(incoming);
            default -> false;
        };
    }

    private static int tagEnd(String html, int lt) {
        char quote = 0;
        for (int i = lt + 1; i < html.length(); i++) {
            char c = html.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static int firstSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void parseAttrs(String s, Map<String, String> out) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < n && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '=') {
                i++;
            }
            if (i == start) {
                break;
            }
            String name = s.substring(start, i).toLowerCase(Locale.ROOT);
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            String value = "";
            if (i < n && s.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(s.charAt(i))) {
                    i++;
                }
                if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                    char q = s.charAt(i++);
                    int end = s.indexOf(q, i);
                    if (end < 0) {
                        end = n;
                    }
                    value = s.substring(i, end);
                    i = end + 1;
                } else {
                    int end = i;
                    while (end < n && !Character.isWhitespace(s.charAt(end))) {
                        end++;
                    }
                    value = s.substring(i, end);
                    i = end;
                }
            }
            out.put(name, unescape(value));
        }
    }

    private static void flushText(El cur, StringBuilder text) {
        if (text.length() > 0) {
            cur.children.add(text.toString());
            text.setLength(0);
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        String h = haystack.toLowerCase(Locale.ROOT);
        return h.indexOf(needle.toLowerCase(Locale.ROOT), from);
    }

    // --- text extraction ------------------------------------------------------------

    /** Visible text: entities decoded, script/style skipped, block breaks as newlines. */
    static String text(El el) {
        StringBuilder sb = new StringBuilder();
        appendText(el, sb);
        return sb.toString();
    }

    private static void appendText(El el, StringBuilder sb) {
        if (SKIP_TEXT.contains(el.tag)) {
            return;
        }
        for (Object child : el.children) {
            if (child instanceof String s) {
                sb.append(unescape(s));
            } else if (child instanceof El childEl) {
                appendText(childEl, sb);
                if (BLOCK.contains(childEl.tag)) {
                    sb.append('\n');
                }
            }
        }
    }

    // --- CSS selector subset -----------------------------------------------------------

    /** Descendant-combinator selector; returns matches in document order. */
    static List<El> select(El root, String selector) {
        List<El> out = new ArrayList<>();
        if (root == null || selector == null || selector.isBlank()) {
            return out;
        }
        String[] parts = selector.trim().split("\\s+");
        List<SimpleSelector> chain = new ArrayList<>();
        for (String part : parts) {
            SimpleSelector ss = SimpleSelector.parse(part);
            if (ss == null) {
                return out; // unsupported selector shape → no matches, never throw
            }
            chain.add(ss);
        }
        List<El> all = new ArrayList<>();
        collect(root, all);
        SimpleSelector last = chain.get(chain.size() - 1);
        for (El el : all) {
            if (!last.matches(el)) {
                continue;
            }
            El ancestor = el.parent;
            int idx = chain.size() - 2;
            while (idx >= 0 && ancestor != null) {
                if (chain.get(idx).matches(ancestor)) {
                    idx--;
                }
                ancestor = ancestor.parent;
            }
            if (idx < 0) {
                out.add(el);
            }
        }
        return out;
    }

    private static void collect(El el, List<El> out) {
        for (Object child : el.children) {
            if (child instanceof El childEl) {
                out.add(childEl);
                collect(childEl, out);
            }
        }
    }

    private static final class SimpleSelector {
        String tag;      // null = any
        String id;
        final List<String> classes = new ArrayList<>();
        final List<String[]> attrs = new ArrayList<>(); // [name, valueOrNull]

        static SimpleSelector parse(String s) {
            SimpleSelector ss = new SimpleSelector();
            int i = 0;
            int n = s.length();
            if (i < n && (Character.isLetter(s.charAt(i)) || s.charAt(i) == '*')) {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '-'
                        || s.charAt(i) == '_' || s.charAt(i) == '*')) {
                    i++;
                }
                String t = s.substring(start, i).toLowerCase(Locale.ROOT);
                ss.tag = "*".equals(t) ? null : t;
            }
            while (i < n) {
                char c = s.charAt(i);
                if (c == '#') {
                    int[] r = readIdent(s, i + 1);
                    if (r[0] == r[1]) {
                        return null;
                    }
                    ss.id = s.substring(r[0], r[1]);
                    i = r[1];
                } else if (c == '.') {
                    int[] r = readIdent(s, i + 1);
                    if (r[0] == r[1]) {
                        return null;
                    }
                    ss.classes.add(s.substring(r[0], r[1]));
                    i = r[1];
                } else if (c == '[') {
                    int end = s.indexOf(']', i);
                    if (end < 0) {
                        return null;
                    }
                    String body = s.substring(i + 1, end).trim();
                    int eq = body.indexOf('=');
                    if (eq < 0) {
                        ss.attrs.add(new String[]{body.toLowerCase(Locale.ROOT), null});
                    } else {
                        String name = body.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                        String val = body.substring(eq + 1).trim()
                                .replaceAll("^['\"]|['\"]$", "");
                        ss.attrs.add(new String[]{name, val});
                    }
                    i = end + 1;
                } else {
                    return null;
                }
            }
            return ss;
        }

        private static int[] readIdent(String s, int from) {
            int i = from;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '-'
                    || s.charAt(i) == '_')) {
                i++;
            }
            return new int[]{from, i};
        }

        boolean matches(El el) {
            if (el == null || el.tag.startsWith("#")) {
                return false;
            }
            if (tag != null && !tag.equals(el.tag)) {
                return false;
            }
            if (id != null && !id.equals(el.attr("id"))) {
                return false;
            }
            for (String cls : classes) {
                String c = el.attr("class");
                if (c == null || !List.of(c.trim().split("\\s+")).contains(cls)) {
                    return false;
                }
            }
            for (String[] a : attrs) {
                String v = el.attr(a[0]);
                if (v == null || (a[1] != null && !a[1].equals(v))) {
                    return false;
                }
            }
            return true;
        }
    }

    // --- serialization -----------------------------------------------------------------

    static String outerHtml(El el) {
        StringBuilder sb = new StringBuilder();
        serialize(el, sb);
        return sb.toString();
    }

    private static void serialize(El el, StringBuilder sb) {
        sb.append('<').append(el.tag);
        el.attrs.forEach((k, v) -> sb.append(' ').append(k).append("=\"").append(escape(v)).append('"'));
        sb.append('>');
        for (Object child : el.children) {
            if (child instanceof String s) {
                sb.append(RAW_TEXT.contains(el.tag) ? s : escape(s));
            } else if (child instanceof El childEl) {
                serialize(childEl, sb);
            }
        }
        if (!VOID.contains(el.tag)) {
            sb.append("</").append(el.tag).append('>');
        }
    }

    // --- entities -------------------------------------------------------------------------

    /** Escape text for safe HTML interpolation (template default). */
    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Decode named (common set) and numeric entities; unknown names pass through. */
    static String unescape(String s) {
        if (s == null || s.indexOf('&') < 0) {
            return s == null ? "" : s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c != '&') {
                sb.append(c);
                i++;
                continue;
            }
            int semi = s.indexOf(';', i);
            if (semi < 0 || semi - i > 12) {
                sb.append('&');
                i++;
                continue;
            }
            String ent = s.substring(i + 1, semi);
            String decoded = switch (ent) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                case "ndash" -> "–";
                case "mdash" -> "—";
                case "hellip" -> "…";
                case "copy" -> "©";
                case "reg" -> "®";
                case "trade" -> "™";
                case "laquo" -> "«";
                case "raquo" -> "»";
                default -> numeric(ent);
            };
            if (decoded == null) {
                sb.append(s, i, semi + 1);
            } else {
                sb.append(decoded);
            }
            i = semi + 1;
        }
        return sb.toString();
    }

    private static String numeric(String ent) {
        try {
            if (ent.startsWith("#x") || ent.startsWith("#X")) {
                return new String(Character.toChars(Integer.parseInt(ent.substring(2), 16)));
            }
            if (ent.startsWith("#")) {
                return new String(Character.toChars(Integer.parseInt(ent.substring(1))));
            }
        } catch (Exception ignored) {
            // falls through: unknown entity passes through verbatim
        }
        return null;
    }
}
