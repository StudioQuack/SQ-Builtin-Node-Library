package com.studioquack.nodelibrary.nodes.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * robots.txt parser used only by {@link WebBrowserNode}.
 *
 * <p>Reasonable port of CPython {@code urllib.robotparser}: records are grouped on
 * blank lines, and a {@code User-agent} line after Allow/Disallow starts a new
 * record. Sitemap / Crawl-delay / Request-rate / unknown fields are ignored.
 *
 * <p>Matching (stricter than urllib's first-rule-wins, closer to Google/RFC 9309):
 * a specific UA substring match (case-insensitive) wins over {@code *}; then the
 * longest matching path prefix; equal length → Allow wins. No matching rule →
 * allow. Paths are percent-decoded; the query string is not part of the match.
 */
public final class RobotsTxt {

    private final List<Group> specific = new ArrayList<>();
    private final List<Group> wildcards = new ArrayList<>();

    private RobotsTxt() {
        // filled by parse()
    }

    /** Parse a robots.txt body. Null/blank → empty ruleset (default allow). */
    public static RobotsTxt parse(String body) {
        RobotsTxt parsed = new RobotsTxt();
        if (body == null || body.isEmpty()) {
            return parsed;
        }
        String text = body;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        Group current = new Group();
        boolean seenAgent = false;
        boolean seenRule = false;
        for (String raw : text.split("\n", -1)) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                parsed.flush(current);
                current = new Group();
                seenAgent = false;
                seenRule = false;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (field) {
                case "user-agent" -> {
                    // urllib: User-agent after Allow/Disallow starts a new record
                    // even without a blank line.
                    if (seenRule) {
                        parsed.flush(current);
                        current = new Group();
                        seenAgent = false;
                        seenRule = false;
                    }
                    seenAgent = true;
                    if (!value.isEmpty()) {
                        current.userAgents.add(value);
                    }
                }
                case "allow" -> {
                    if (seenAgent) {
                        current.rules.add(Rule.allow(value));
                        seenRule = true;
                    }
                }
                case "disallow" -> {
                    if (seenAgent) {
                        current.rules.add(Rule.disallow(value));
                        seenRule = true;
                    }
                }
                default -> {
                    // Sitemap, Crawl-delay, Request-rate, Host, Unknown — ignore
                }
            }
        }
        parsed.flush(current);
        return parsed;
    }

    /**
     * Whether {@code userAgent} may fetch {@code url}. Default allow when no
     * group matches or the matching group has no applicable rule.
     */
    public boolean canFetch(String userAgent, String url) {
        String ua = userAgent == null ? "" : userAgent.trim().toLowerCase(Locale.ROOT);
        String path = pathOf(url);
        Group matched = firstSpecificMatch(ua);
        if (matched == null) {
            matched = mergedWildcards();
        }
        if (matched == null) {
            return true;
        }
        return matched.allows(path);
    }

    private void flush(Group g) {
        if (g == null || g.rules.isEmpty() || g.userAgents.isEmpty()) {
            return;
        }
        boolean star = false;
        for (String ua : g.userAgents) {
            if ("*".equals(ua)) {
                star = true;
                break;
            }
        }
        // urllib: a group that lists "*" is the default entry, even if it also
        // names a specific crawler.
        if (star) {
            wildcards.add(g);
        } else {
            specific.add(g);
        }
    }

    private Group firstSpecificMatch(String uaLower) {
        for (Group g : specific) {
            if (g.appliesTo(uaLower)) {
                return g;
            }
        }
        return null;
    }

    /** urllib merges later {@code *} records into the default entry. */
    private Group mergedWildcards() {
        if (wildcards.isEmpty()) {
            return null;
        }
        if (wildcards.size() == 1) {
            return wildcards.get(0);
        }
        Group merged = new Group();
        merged.userAgents.add("*");
        for (Group g : wildcards) {
            merged.rules.addAll(g.rules);
        }
        return merged;
    }

    private static String stripComment(String raw) {
        if (raw == null) {
            return "";
        }
        String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    /** Decoded path only (query/fragment dropped). Empty path → {@code /}. */
    static String pathOf(String url) {
        if (url == null || url.isBlank()) {
            return "/";
        }
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                return "/";
            }
            return path;
        } catch (URISyntaxException e) {
            return "/";
        }
    }

    /** Percent-decode a robots.txt path; {@code +} stays {@code +} (not space). */
    static String decodePath(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    private static final class Group {
        final List<String> userAgents = new ArrayList<>();
        final List<Rule> rules = new ArrayList<>();

        boolean appliesTo(String uaLower) {
            for (String agent : userAgents) {
                if (agent.isEmpty() || "*".equals(agent)) {
                    continue;
                }
                if (uaLower.contains(agent.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Longest matching prefix; equal length prefers Allow. No match → allow.
         */
        boolean allows(String urlPath) {
            Rule best = null;
            for (Rule rule : rules) {
                if (!rule.matches(urlPath)) {
                    continue;
                }
                if (best == null
                        || rule.prefixLength > best.prefixLength
                        || (rule.prefixLength == best.prefixLength && rule.allow && !best.allow)) {
                    best = rule;
                }
            }
            return best == null || best.allow;
        }
    }

    private static final class Rule {
        final String path;
        final boolean allow;
        final int prefixLength;

        private Rule(String path, boolean allow, int prefixLength) {
            this.path = path;
            this.allow = allow;
            this.prefixLength = prefixLength;
        }

        static Rule allow(String rawPath) {
            return of(rawPath, true);
        }

        static Rule disallow(String rawPath) {
            // Empty Disallow: means "allow all" (robots.txt spec / urllib).
            if (rawPath == null || rawPath.isBlank()) {
                return new Rule("", true, 0);
            }
            return of(rawPath, false);
        }

        private static Rule of(String rawPath, boolean allow) {
            String decoded = decodePath(rawPath);
            if ("*".equals(decoded)) {
                // urllib: path "*" matches every URL. Length 1 so a real prefix wins.
                return new Rule("*", allow, 1);
            }
            return new Rule(decoded, allow, decoded.length());
        }

        boolean matches(String urlPath) {
            if ("*".equals(path) || path.isEmpty()) {
                return true;
            }
            return urlPath.startsWith(path);
        }
    }
}
