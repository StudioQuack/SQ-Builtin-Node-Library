package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackHelper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Text node ({@code nodes/text}) — string utilities. No network.
 *
 * <p>Operations: {@code replace} (literal), {@code regex} (regex replace when
 * {@code replacement} is set, otherwise list all matches), {@code split}, {@code join},
 * {@code case} (upper|lower|title|camel|snake), {@code trim}, {@code template}
 * ({@code {{path}}} placeholders resolved against the INPUT object), {@code slug},
 * {@code strip_html} (tag strip + basic entity decode; not a crawler).
 *
 * <p>Regex work runs against a budgeted CharSequence ({@link TransformSupport#bounded})
 * so a catastrophic-backtracking pattern fails fast instead of pinning the listener
 * pool; inputs are capped at {@value TransformSupport#MAX_TEXT_CHARS} chars.
 * {@code limit} caps replacements/splits (0 = unlimited, still size-capped).
 */
public final class TextNode implements NodeHandler {

    public static final String DEFAULT_ID = "6a45ada5-d149-4851-9aa3-22456c0b7312";

    private final String templateId;

    public TextNode() {
        this(DEFAULT_ID);
    }

    public TextNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        try {
            if (request == null) {
                return DataSupport.invalidInput("request body must be a JSON object");
            }
            Map<String, Object> cfg = DataSupport.readConfig(request);
            Map<String, Object> in = DataSupport.readInput(request, helper);
            String op = TransformSupport.operation(cfg, in);
            int limit = Math.max(0, EmulatorUtil.toInt(cfg.get("limit"), 0));
            int flags = TransformSupport.regexFlags(DataSupport.cfgStr(cfg, "flags", ""));

            return switch (op) {
                case "replace" -> replace(in, limit, flags);
                case "regex" -> regex(in, flags, limit);
                case "split" -> split(in, limit);
                case "join" -> join(in);
                case "case" -> changeCase(in);
                case "trim" -> {
                    String text = requireText(in);
                    if (text == null) {
                        yield DataSupport.invalidInput("text is required.");
                    }
                    yield TransformSupport.ok(Map.of("text", text.trim()));
                }
                case "template" -> template(in);
                case "slug" -> {
                    String text = requireText(in);
                    if (text == null) {
                        yield DataSupport.invalidInput("text is required.");
                    }
                    yield TransformSupport.ok(Map.of("text", slug(text)));
                }
                case "strip_html" -> {
                    String text = requireText(in);
                    if (text == null) {
                        yield DataSupport.invalidInput("text is required.");
                    }
                    yield TransformSupport.ok(Map.of("text", stripHtml(text)));
                }
                default -> DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            };
        } catch (TransformSupport.RegexBudgetExceededException e) {
            return DataSupport.fail("regex exceeded its evaluation budget (possible ReDoS).");
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("text failed");
        }
    }

    /**
     * Default {@code replace} operation: literal replace when only pattern/replacement are set;
     * also supports agent-style combos (text-only passthrough, {@code case}, split+join via
     * {@code separator}, regex when the pattern contains metacharacters other than {@code .} alone).
     */
    private Map<String, Object> replace(Map<String, Object> in, int limit, int flags) {
        String text = requireText(in);
        if (text == null) {
            return DataSupport.invalidInput("text is required.");
        }
        String pattern = EmulatorUtil.str(in.get("pattern"));
        String separator = EmulatorUtil.str(in.get("separator"));
        String caseMode = resolveCaseMode(in.get("case"), in.containsKey("case"));

        if (pattern.isEmpty()) {
            if (in.containsKey("replacement")) {
                return DataSupport.invalidInput("pattern is required for replace.");
            }
            if (caseMode != null) {
                return TransformSupport.ok(Map.of("text", applyCase(text, caseMode)));
            }
            if (!separator.isEmpty()) {
                return TransformSupport.ok(Map.of("text", splitAndJoin(text, separator, "", limit)));
            }
            return TransformSupport.ok(Map.of("text", text));
        }

        String result;
        if (!separator.isEmpty()) {
            result = splitAndJoin(text, pattern, separator, limit);
        } else if (preferRegex(pattern)) {
            String replacement = in.containsKey("replacement")
                    ? EmulatorUtil.str(in.get("replacement")) : "";
            result = regexReplaceText(text, pattern, replacement, flags, limit);
        } else {
            String replacement = in.containsKey("replacement")
                    ? EmulatorUtil.str(in.get("replacement")) : "";
            result = literalReplace(text, pattern, replacement, limit);
        }
        if (caseMode != null) {
            result = applyCase(result, caseMode);
        }
        return TransformSupport.ok(Map.of("text", result));
    }

    /** Literal replace; {@code limit} caps replacements (0 = unlimited). */
    static String literalReplace(String text, String pattern, String replacement, int limit) {
        StringBuilder sb = new StringBuilder();
        int from = 0;
        int count = 0;
        while (true) {
            int at = text.indexOf(pattern, from);
            if (at < 0 || (limit > 0 && count >= limit)) {
                sb.append(text, from, text.length());
                break;
            }
            sb.append(text, from, at).append(replacement);
            from = at + pattern.length();
            count++;
        }
        return sb.toString();
    }

    static String splitAndJoin(String text, String splitOn, String joinWith, int limit) {
        int splitLimit = limit > 0 ? limit + 1 : 0;
        String[] parts = text.split(Pattern.quote(splitOn), splitLimit);
        return String.join(joinWith, parts);
    }

    /** Regex metacharacters except a lone {@code .} (still treated as literal in replace). */
    static boolean preferRegex(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        if (".".equals(pattern)) {
            return false;
        }
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '+' || c == '*' || c == '?' || c == '[' || c == ']' || c == '(' || c == ')'
                    || c == '{' || c == '}' || c == '|' || c == '^' || c == '$' || c == '\\') {
                return true;
            }
        }
        return false;
    }

    private static String regexReplaceText(String text, String pattern, String replacement, int flags, int limit) {
        Pattern p;
        try {
            p = Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid regex pattern.");
        }
        Matcher m = p.matcher(TransformSupport.bounded(text));
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (m.find()) {
            if (limit > 0 && count >= limit) {
                break;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            count++;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String resolveCaseMode(Object raw, boolean keyPresent) {
        if (raw == null && !keyPresent) {
            return null;
        }
        String s = EmulatorUtil.str(raw).trim();
        if (s.isEmpty()) {
            if (keyPresent) {
                throw new IllegalArgumentException("unknown case: <missing>");
            }
            return null;
        }
        String mode = s.toLowerCase(Locale.ROOT);
        if (List.of("upper", "lower", "title", "camel", "snake").contains(mode)) {
            return mode;
        }
        throw new IllegalArgumentException("unknown case: " + s);
    }

    private static String applyCase(String text, String mode) {
        return switch (mode) {
            case "upper" -> text.toUpperCase(Locale.ROOT);
            case "lower" -> text.toLowerCase(Locale.ROOT);
            case "title" -> titleCase(text);
            case "camel" -> camelCase(text);
            case "snake" -> snakeCase(text);
            default -> throw new IllegalArgumentException("unknown case: " + mode);
        };
    }

    /** Regex replace when {@code replacement} is set; otherwise list all matches. */
    private Map<String, Object> regex(Map<String, Object> in, int flags, int limit) {
        String text = requireText(in);
        String pattern = EmulatorUtil.str(in.get("pattern"));
        if (text == null) {
            return DataSupport.invalidInput("text is required.");
        }
        if (pattern.isEmpty()) {
            return DataSupport.invalidInput("pattern is required for regex.");
        }
        Pattern p;
        try {
            p = Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            return DataSupport.invalidInput("invalid regex pattern.");
        }
        Matcher m = p.matcher(TransformSupport.bounded(text));
        String replacement = EmulatorUtil.str(in.get("replacement"));
        if (!replacement.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            while (m.find()) {
                if (limit > 0 && count >= limit) {
                    break;
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                count++;
            }
            m.appendTail(sb);
            return TransformSupport.ok(Map.of("text", sb.toString()));
        }
        List<Object> matches = new ArrayList<>();
        while (m.find()) {
            matches.add(m.group());
            if (limit > 0 && matches.size() >= limit) {
                break;
            }
        }
        return TransformSupport.ok(matches);
    }

    private Map<String, Object> split(Map<String, Object> in, int limit) {
        String text = requireText(in);
        if (text == null) {
            return DataSupport.invalidInput("text is required.");
        }
        String separator = EmulatorUtil.str(in.get("separator"));
        if (separator.isEmpty()) {
            separator = ",";
        }
        // literal separator: quoted so regex metacharacters split literally
        String[] parts = text.split(Pattern.quote(separator), limit);
        return TransformSupport.ok(new ArrayList<>(List.of(parts)));
    }

    private Map<String, Object> join(Map<String, Object> in) {
        List<Object> items = TransformSupport.listAt(in, "text");
        if (items == null) {
            return DataSupport.invalidInput("join requires text to be a JSON array.");
        }
        String separator = EmulatorUtil.str(in.get("separator"));
        if (separator.isEmpty()) {
            separator = ",";
        }
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            if (!sb.isEmpty()) {
                sb.append(separator);
            }
            sb.append(item instanceof String s ? s : TransformSupport.canonical(item));
        }
        return TransformSupport.ok(Map.of("text", sb.toString()));
    }

    private Map<String, Object> changeCase(Map<String, Object> in) {
        String text = requireText(in);
        if (text == null) {
            return DataSupport.invalidInput("text is required.");
        }
        String mode = resolveCaseMode(in.get("case"), in.containsKey("case"));
        if (mode == null) {
            return DataSupport.invalidInput("unknown case: <missing>");
        }
        return TransformSupport.ok(Map.of("text", applyCase(text, mode)));
    }

    /** {@code {{dotted.path}}} placeholders resolve against the INPUT object. */
    private Map<String, Object> template(Map<String, Object> in) {
        String text = requireText(in);
        if (text == null) {
            return DataSupport.invalidInput("text is required.");
        }
        Matcher m = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}").matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = TransformSupport.getPath(in, m.group(1));
            String replacement = v == null ? "" : v instanceof String s ? s : TransformSupport.canonical(v);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return TransformSupport.ok(Map.of("text", sb.toString()));
    }

    static String slug(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("-{2,}", "-");
        return slug.replaceAll("^-|-$", "");
    }

    static String stripHtml(String html) {
        // drop script/style bodies first so their source does not leak into text
        String noScripts = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        String noTags = noScripts.replaceAll("<[^>]*>", " ");
        String decoded = noTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return decoded.replaceAll("\\s+", " ").trim();
    }

    private static String titleCase(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static String camelCase(String text) {
        String[] words = text.split("[^A-Za-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            String lower = word.toLowerCase(Locale.ROOT);
            if (sb.isEmpty()) {
                sb.append(lower);
            } else {
                sb.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
        }
        return sb.toString();
    }

    private static String snakeCase(String text) {
        String spaced = text.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        String[] words = spaced.split("[^A-Za-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('_');
            }
            sb.append(word.toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static String requireText(Map<String, Object> in) {
        Object v = in.get("text");
        if (v == null) {
            return null;
        }
        String s = EmulatorUtil.str(v);
        if (s.length() > TransformSupport.MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("text exceeds the 1000000 character cap.");
        }
        return s;
    }
}
