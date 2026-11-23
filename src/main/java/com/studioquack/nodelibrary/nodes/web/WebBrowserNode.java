package com.studioquack.nodelibrary.nodes.web;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import com.studioquack.nodelibrary.nodes.web.WebNodeSupport.HttpResult;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web Browser in-process handler: GET one public http(s) URL, honor robots.txt
 * (fail-open), regex-strip HTML to text, return the OUTPUT port {@code output}.
 *
 * <p>Thread-safe: the only instance field is the final template id. robots.txt
 * is cached in a process-lifetime {@link ConcurrentHashMap}. Never logs
 * {@code helperToken}, cookies, or full page HTML.
 */
public final class WebBrowserNode implements NodeHandler {

    static final String DEFAULT_TEMPLATE_ID = "cc63350b-4d0d-4328-87c0-624f6018d0e1";

    private static final int DEFAULT_TIMEOUT_SECONDS = 25;
    private static final int DEFAULT_MAX_CHARS = 12_000;
    private static final int HTML_MAX_BYTES = 2_000_000;
    private static final int ROBOTS_MAX_BYTES = 524_288;

    private static final Pattern SCRIPT = Pattern.compile(
            "<script[\\s\\S]*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE = Pattern.compile(
            "<style[\\s\\S]*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COMMENT = Pattern.compile("<!--[\\s\\S]*?-->");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern TITLE = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern H1 = Pattern.compile(
            "<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** ISO 3166-1 alpha-2 (lower) → Accept-Language. Same table as the Python tool. */
    private static final Map<String, String> ACCEPT_LANGUAGE = Map.ofEntries(
            Map.entry("us", "en-US,en;q=0.9"),
            Map.entry("gb", "en-GB,en;q=0.9"),
            Map.entry("uk", "en-GB,en;q=0.9"),
            Map.entry("tr", "tr-TR,tr;q=0.9,en;q=0.5"),
            Map.entry("de", "de-DE,de;q=0.9,en;q=0.5"),
            Map.entry("fr", "fr-FR,fr;q=0.9,en;q=0.5"),
            Map.entry("es", "es-ES,es;q=0.9,en;q=0.5"),
            Map.entry("it", "it-IT,it;q=0.9,en;q=0.5"),
            Map.entry("nl", "nl-NL,nl;q=0.9,en;q=0.5"),
            Map.entry("pt", "pt-PT,pt;q=0.9,en;q=0.5"),
            Map.entry("br", "pt-BR,pt;q=0.9,en;q=0.5"),
            Map.entry("jp", "ja-JP,ja;q=0.9,en;q=0.5"),
            Map.entry("cn", "zh-CN,zh;q=0.9,en;q=0.5"),
            Map.entry("kr", "ko-KR,ko;q=0.9,en;q=0.5"),
            Map.entry("in", "en-IN,en;q=0.9,hi;q=0.7"),
            Map.entry("au", "en-AU,en;q=0.9"),
            Map.entry("ca", "en-CA,en;q=0.9,fr-CA;q=0.7"),
            Map.entry("mx", "es-MX,es;q=0.9,en;q=0.5"),
            Map.entry("pl", "pl-PL,pl;q=0.9,en;q=0.5"),
            Map.entry("se", "sv-SE,sv;q=0.9,en;q=0.5"));

    /** Process-lifetime robots.txt cache. Key is {@code scheme://netloc} lower-cased. */
    private static final ConcurrentHashMap<String, CachedRobots> ROBOTS_CACHE = new ConcurrentHashMap<>();

    private final String templateId;

    public WebBrowserNode() {
        this(DEFAULT_TEMPLATE_ID);
    }

    public WebBrowserNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? DEFAULT_TEMPLATE_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Success returns OUTPUT {@code output}. Failures raise {@link StudioQuackError}.
     * {@code blocked_by_robots} is a successful outcome, not a failure.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper unusedHelper) {
        HelperClient helper = WebNodeSupport.helperClient(request);
        try {
            return port(run(request, helper));
        } catch (StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (isHttpFamily(e)) {
                return DataSupport.fail("Web browser HTTP error: " + exceptionMessage(e));
            }
            return DataSupport.fail("Web browser error: " + e.getClass().getSimpleName() + ": "
                    + exceptionMessage(e));
        }
    }

    private static Map<String, Object> run(Map<String, Object> request, HelperClient helper)
            throws Exception {
        Map<String, Object> input = WebNodeSupport.readInput(request);
        Map<String, Object> config = WebNodeSupport.readConfig(request);

        Object rawUrl = input.get("url");
        if (rawUrl != null && !(rawUrl instanceof String)) {
            return validationError("url must be a string.");
        }
        String url = WebNodeSupport.trim(rawUrl);
        if (url.isEmpty()) {
            return validationError("url is required.");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return schemeValidationError(url);
        }
        if (invalidHttpUrl(uri)) {
            return schemeValidationError(url);
        }

        int timeout = WebNodeSupport.toInt(config.get("timeout_seconds"), DEFAULT_TIMEOUT_SECONDS);
        int maxChars = resolveMaxChars(input.get("max_chars"), config.get("max_chars"));
        String userAgent = WebNodeSupport.trim(config.get("user_agent"));
        if (userAgent.isEmpty()) {
            userAgent = WebNodeSupport.CHROME_UA;
        }
        // robots.txt is honored unless the instance config explicitly turns it off.
        // Input ports cannot override this. Unknown/blank config → true (fail closed).
        boolean respectRobots = WebNodeSupport.asBool(config.get("respect_robots_txt"), true);
        String location = WebNodeSupport.trim(config.get("location"));

        WebNodeSupport.log(helper, "web-browser start host=" + safeHost(uri)
                + " timeoutSeconds=" + timeout + " respectRobots=" + respectRobots);

        String robotsStatus;
        if (!respectRobots) {
            robotsStatus = "skipped";
            WebNodeSupport.log(helper,
                    "web-browser warning: robots.txt was not consulted for host=" + safeHost(uri)
                            + " (respect_robots_txt is off). The workflow operator is responsible for "
                            + "complying with the target site's terms.");
        } else {
            CachedRobots cached = loadRobots(uri, userAgent, timeout, helper);
            if (!cached.reachable) {
                robotsStatus = "unreachable";
            } else if (cached.parser.canFetch(userAgent, url)) {
                robotsStatus = "allowed";
            } else {
                robotsStatus = "disallowed";
            }
        }
        if ("disallowed".equals(robotsStatus)) {
            // Not a throw: robots denial is a first-class output status.
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("robots", robotsStatus);
            metadata.put("fetched_at", Instant.now().toString());
            if (!location.isEmpty()) {
                metadata.put("location", location);
            }
            Map<String, Object> blocked = new LinkedHashMap<>();
            blocked.put("status", "blocked_by_robots");
            blocked.put("url", url);
            blocked.put("error", "Fetch disallowed by robots.txt");
            blocked.put("metadata", metadata);
            WebNodeSupport.log(helper, "web-browser blocked_by_robots host=" + safeHost(uri));
            return blocked;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        String acceptLanguage = acceptLanguageFor(location);
        if (acceptLanguage != null) {
            headers.put("Accept-Language", acceptLanguage);
        }

        HttpResult page = WebNodeSupport.send(
                "GET", uri, headers, null, timeout, true, HTML_MAX_BYTES, HttpClient.Redirect.NORMAL);
        if (page.statusCode < 200 || page.statusCode >= 300) {
            return DataSupport.fail(
                    "Web browser HTTP error: HTTP " + page.statusCode + " for url: " + page.url);
        }

        String html = page.body;
        String text = stripHtml(html);
        if (text.length() > maxChars) {
            text = text.substring(0, Math.max(0, maxChars)) + "... [truncated]";
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("content_type", page.contentType);
        metadata.put("status_code", page.statusCode);
        metadata.put("fetched_at", Instant.now().toString());
        metadata.put("robots", robotsStatus);
        if (!location.isEmpty()) {
            metadata.put("location", location);
        }

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("status", "ok");
        ok.put("url", page.url);
        ok.put("title", extractTitle(html));
        ok.put("content", text);
        ok.put("metadata", metadata);
        WebNodeSupport.log(helper, "web-browser ok host=" + safeHost(uri)
                + " statusCode=" + page.statusCode
                + " contentChars=" + text.length()
                + " htmlTruncated=" + page.truncated
                + " robots=" + robotsStatus);
        return ok;
    }

    /**
     * Input {@code max_chars} overrides config when present and non-zero (Python
     * {@code input or config}). Invalid non-empty strings fall through to
     * {@link WebNodeSupport#toInt} default 12000, not the config value.
     */
    static int resolveMaxChars(Object inputMax, Object configMax) {
        Object raw = omittedMaxChars(inputMax) ? configMax : inputMax;
        return WebNodeSupport.toInt(raw, DEFAULT_MAX_CHARS);
    }

    private static boolean omittedMaxChars(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof Number n) {
            return n.doubleValue() == 0.0;
        }
        String s = WebNodeSupport.trim(raw);
        if (s.isEmpty()) {
            return true;
        }
        try {
            return Double.parseDouble(s) == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean invalidHttpUrl(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            return true;
        }
        String lower = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(lower) && !"https".equals(lower)) {
            return true;
        }
        String host = uri.getHost();
        return host == null || host.isBlank();
    }

    private static Map<String, Object> validationError(String message) {
        return DataSupport.invalidInput(message == null || message.isBlank() ? "invalid input" : message);
    }

    private static Map<String, Object> schemeValidationError(String requestedUrl) {
        return DataSupport.invalidInput("url must be http or https.");
    }

    private static Map<String, Object> port(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("output", payload);
        return out;
    }

    /**
     * GET {@code {scheme}://{netloc}/robots.txt}. Fail-open: network / timeout /
     * non-2xx → status unreachable and the page fetch is allowed. Two concurrent
     * misses may both GET once; {@code putIfAbsent} keeps the first cached value.
     * Blocking I/O is intentionally outside {@code computeIfAbsent} (CHM can hang).
     */
    private static CachedRobots loadRobots(URI pageUri, String userAgent, int timeout,
                                           HelperClient helper) {
        String authority = pageUri.getRawAuthority();
        String key = (pageUri.getScheme() + "://" + authority).toLowerCase(Locale.ROOT);
        CachedRobots hit = ROBOTS_CACHE.get(key);
        if (hit != null) {
            return hit;
        }
        URI robotsUri;
        try {
            robotsUri = new URI(pageUri.getScheme() + "://" + authority + "/robots.txt");
        } catch (URISyntaxException e) {
            return cache(key, CachedRobots.unreachable());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept", "text/plain,*/*;q=0.8");
        CachedRobots fresh;
        try {
            HttpResult r = WebNodeSupport.send(
                    "GET", robotsUri, headers, null, timeout, true, ROBOTS_MAX_BYTES,
                    HttpClient.Redirect.NORMAL);
            if (r.statusCode >= 200 && r.statusCode < 300) {
                fresh = CachedRobots.ok(RobotsTxt.parse(r.body));
            } else {
                fresh = CachedRobots.unreachable();
            }
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            WebNodeSupport.log(helper, "web-browser robots unreachable host=" + safeHost(pageUri));
            fresh = CachedRobots.unreachable();
        }
        return cache(key, fresh);
    }

    private static CachedRobots cache(String key, CachedRobots entry) {
        CachedRobots raced = ROBOTS_CACHE.putIfAbsent(key, entry);
        return raced != null ? raced : entry;
    }

    static String acceptLanguageFor(String location) {
        String code = location == null ? "" : location.trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty()) {
            return null;
        }
        String mapped = ACCEPT_LANGUAGE.get(code);
        if (mapped != null) {
            return mapped;
        }
        return code + "-" + code.toUpperCase(Locale.ROOT) + "," + code + ";q=0.9,en;q=0.5";
    }

    static String stripHtml(String html) {
        String text = html == null ? "" : html;
        text = SCRIPT.matcher(text).replaceAll(" ");
        text = STYLE.matcher(text).replaceAll(" ");
        text = COMMENT.matcher(text).replaceAll(" ");
        text = TAG.matcher(text).replaceAll(" ");
        text = WHITESPACE.matcher(text).replaceAll(" ");
        return text.trim();
    }

    static String extractTitle(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher title = TITLE.matcher(html);
        if (title.find()) {
            return stripHtml(title.group(1));
        }
        Matcher h1 = H1.matcher(html);
        return h1.find() ? stripHtml(h1.group(1)) : "";
    }

    private static String safeHost(URI uri) {
        String host = uri == null ? "" : uri.getHost();
        return host == null ? "" : host;
    }

    private static boolean isHttpFamily(Throwable e) {
        return e instanceof IOException
                || e instanceof InterruptedException
                || e instanceof TimeoutException;
    }

    private static String exceptionMessage(Throwable e) {
        if (e == null) {
            return "";
        }
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }

    private static final class CachedRobots {
        final RobotsTxt parser;
        final boolean reachable;

        private CachedRobots(RobotsTxt parser, boolean reachable) {
            this.parser = parser;
            this.reachable = reachable;
        }

        static CachedRobots ok(RobotsTxt parser) {
            return new CachedRobots(parser, true);
        }

        static CachedRobots unreachable() {
            return new CachedRobots(null, false);
        }
    }
}
