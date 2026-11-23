package com.studioquack.nodelibrary.nodes.web;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.helperClient;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.log;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.readConfig;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.readInput;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.send;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.str;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.toInt;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.trim;
import static com.studioquack.nodelibrary.nodes.web.WebNodeSupport.utf8;

/**
 * Tavily-compatible web search. Thread-safe: the only field is the final template id.
 *
 * <p>Never logs {@code api_key} / {@code helperToken} and never puts the key on any output
 * port. The key is sent only as the Tavily JSON field {@code api_key}.
 */
public final class WebSearchNode implements NodeHandler {

    /** Bundled {@code nodes/web-search/node_definition.json} {@code _id}. */
    static final String DEFAULT_TEMPLATE_ID = "e154fd82-f17e-4a8e-a457-7334cee69d24";

    static final int RATE_LIMIT_RETRY_SECONDS = 60;
    static final int TRANSIENT_RETRY_SECONDS = 30;

    private static final String DEFAULT_BASE_URL = "https://api.tavily.com";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int DEFAULT_TIMEOUT_SECONDS = 25;
    /** DoS bound for the provider body (~1 MiB). */
    private static final int MAX_BODY_BYTES = 1_000_000;
    private static final int ERROR_BODY_CHARS = 240;
    private static final String USER_AGENT = "StudioQuack/1.0";

    private final String templateId;

    public WebSearchNode() {
        this(DEFAULT_TEMPLATE_ID);
    }

    public WebSearchNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? DEFAULT_TEMPLATE_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Success returns OUTPUT {@code output}. Failures raise {@link StudioQuackError}.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper _helper) {
        Map<String, Object> req = request == null ? Map.of() : request;
        HelperClient logs = helperClient(req);
        String apiKey = "";
        try {
            Map<String, Object> input = readInput(req);
            Map<String, Object> cfg = readConfig(req);
            apiKey = trim(cfg.get("api_key"));

            String query = trim(input.get("query"));
            if (query.isEmpty()) {
                log(logs, "web-search query missing");
                return DataSupport.invalidInput("Query is missing");
            }
            if (apiKey.isEmpty()) {
                log(logs, "web-search api_key missing");
                return DataSupport.invalidInput(
                        "Missing Tavily API Key. Provide it in node parameters (apiKey).");
            }

            // include_answer is schema-only (Python never sent it; Java matches).
            int maxResultsDefault = positiveOrDefault(toInt(cfg.get("maxResultsDefault"), DEFAULT_MAX_RESULTS),
                    DEFAULT_MAX_RESULTS);
            int maxResults = positiveOrDefault(toInt(input.get("max_results"), maxResultsDefault),
                    maxResultsDefault);
            // Java honors timeout_seconds; Python hardcoded 25.
            int timeoutSeconds = positiveOrDefault(toInt(cfg.get("timeout_seconds"), DEFAULT_TIMEOUT_SECONDS),
                    DEFAULT_TIMEOUT_SECONDS);

            String base = trim(cfg.get("baseUrl"));
            if (base.isEmpty()) {
                base = DEFAULT_BASE_URL;
            }
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }

            URI uri;
            try {
                uri = URI.create(base + "/search");
            } catch (IllegalArgumentException e) {
                return httpError(redact(str(e.getMessage()), apiKey), apiKey, logs);
            }
            String scheme = uri.getScheme();
            if (!isHttpOrHttps(scheme)) {
                return DataSupport.invalidInput("baseUrl must be http or https");
            }

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("User-Agent", USER_AGENT);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("api_key", apiKey);
            payload.put("query", query);
            payload.put("max_results", maxResults);
            byte[] body = utf8(Json.write(payload));

            log(logs, "web-search start max_results=" + maxResults + " timeout_s=" + timeoutSeconds);
            WebNodeSupport.HttpResult http;
            try {
                http = send("POST", uri, headers, body, timeoutSeconds, true, MAX_BODY_BYTES,
                        HttpClient.Redirect.NEVER);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return typeError(e, apiKey, logs);
            } catch (StudioQuackError e) {
                throw e;
            } catch (Exception e) {
                if (isTimeout(e)) {
                    log(logs, "web-search request timed out");
                    return transientError("request timed out");
                }
                return httpError(redact(str(e.getMessage()), apiKey), apiKey, logs);
            }

            int code = http.statusCode;
            String detail = httpDetail(code, http.url, http.body, apiKey);
            if (code == 429) {
                log(logs, "web-search HTTP 429");
                return rateLimited(detail);
            }
            if (code >= 500) {
                log(logs, "web-search HTTP " + code);
                return transientHttp(code, detail);
            }
            if (code < 200 || code >= 300) {
                return httpError(detail, apiKey, logs);
            }

            Object parsed;
            try {
                parsed = Json.parse(http.body);
            } catch (RuntimeException e) {
                return typeError(e, apiKey, logs);
            }
            if (!(parsed instanceof Map<?, ?>)) {
                return typeError(new IllegalArgumentException("search response is not a JSON object"),
                        apiKey, logs);
            }
            List<Object> results = Json.asList(Json.asMap(parsed).get("results"));
            String text = formatResults(results, maxResults);
            log(logs, "web-search ok results=" + Math.min(results.size(), maxResults));
            return resultOf(text);
        } catch (StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return typeError(e, apiKey, logs);
        }
    }

    private static String formatResults(List<Object> results, int maxResults) {
        List<String> lines = new ArrayList<>();
        int n = Math.min(results.size(), maxResults);
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = Json.asMap(results.get(i));
            String title = trim(row.get("title"));
            String url = trim(row.get("url"));
            if (title.isEmpty()) {
                title = url;
            }
            String snippet = trim(row.get("content"));
            if (snippet.isEmpty()) {
                snippet = trim(row.get("snippet"));
            }
            snippet = snippet.replace("\n", " ");
            lines.add((i + 1) + ". " + title + " - " + url + "\n" + snippet);
        }
        return lines.isEmpty() ? "No results found." : String.join("\n\n", lines);
    }

    private static Map<String, Object> rateLimited(String detail) {
        return DataSupport.fail("Web search HTTP 429: " + detail
                + ". Web search provider rate limited the request. Wait "
                + RATE_LIMIT_RETRY_SECONDS
                + "s before retrying once; keep any prior usable results.");
    }

    private static Map<String, Object> transientHttp(int statusCode, String detail) {
        return DataSupport.fail("Web search HTTP " + statusCode + ": " + detail
                + ". Web search provider returned a transient error. Wait "
                + TRANSIENT_RETRY_SECONDS
                + "s before retrying once; keep any prior usable results.");
    }

    /** Timeout / 5xx shared throw. Message mentions {@code timed out} when {@code detail} does. */
    private static Map<String, Object> transientError(String detail) {
        return DataSupport.fail("Web search: " + detail
                + ". Web search provider returned a transient error. Wait "
                + TRANSIENT_RETRY_SECONDS
                + "s before retrying once; keep any prior usable results.");
    }

    private static Map<String, Object> resultOf(Object result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result);
        Map<String, Object> ports = new LinkedHashMap<>();
        ports.put("output", payload);
        return ports;
    }

    private static Map<String, Object> httpError(String detail, String apiKey, HelperClient logs) {
        String msg = "Tavily Search HTTP Error: " + redact(detail, apiKey);
        log(logs, "web-search http error");
        return DataSupport.fail(msg);
    }

    private static Map<String, Object> typeError(Exception e, String apiKey, HelperClient logs) {
        String msg = "Error: " + e.getClass().getSimpleName() + ": " + redact(str(e.getMessage()), apiKey);
        log(logs, "web-search " + e.getClass().getSimpleName());
        return DataSupport.fail(msg);
    }

    private static String httpDetail(int status, String url, String body, String apiKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP ").append(status).append(" for url: ").append(url == null ? "" : url);
        String snippet = trim(body).replace('\n', ' ');
        if (!snippet.isEmpty()) {
            if (snippet.length() > ERROR_BODY_CHARS) {
                snippet = snippet.substring(0, ERROR_BODY_CHARS);
            }
            sb.append(" — ").append(snippet);
        }
        return redact(sb.toString(), apiKey);
    }

    /** Replace the secret literal anywhere it leaked into an exception/body snippet. */
    private static String redact(String message, String secret) {
        String out = message == null ? "" : message;
        if (secret != null && !secret.isEmpty()) {
            out = out.replace(secret, "***");
        }
        return out;
    }

    private static boolean isHttpOrHttps(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    /** HttpTimeoutException plus connect/read timeouts wrapped as IOException. */
    private static boolean isTimeout(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof HttpTimeoutException) {
                return true;
            }
            String m = c.getMessage();
            if (c instanceof IOException && m != null
                    && m.toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
        }
        return false;
    }
}
