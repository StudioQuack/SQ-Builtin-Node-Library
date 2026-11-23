package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;

import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Product Hunt Search — GraphQL API v2 posts query (builtin node).
 *
 * <p>POSTs {@code QUERY_POSTS} to {@code https://api.producthunt.com/v2/api/graphql} and maps
 * launches (id, name, tagline, truncated description, votes, makers, topics). Auth is resolved
 * <strong>per request only</strong> — no cached bearer — so tenants cannot share a token.
 *
 * <p>Auth priority (whitespace stripped; empty is missing):
 * <ol>
 *   <li>{@code developer_token} → {@code Authorization: Bearer} (skips OAuth)</li>
 *   <li>{@code api_key} with no {@code api_secret} → use api_key as Bearer</li>
 *   <li>{@code api_key} + {@code api_secret} → OAuth {@code client_credentials} (up to 3 attempts)</li>
 * </ol>
 *
 * <p>Cloudflare detection: Content-Type {@code text/html} (inferred from an HTML body;
 * {@link DataSupport.HttpResp} does not expose headers) OR the body is not a JSON object.
 * OAuth retries on Cloudflare/transient errors; GraphQL does not.
 *
 * <p>Never logs or returns {@code developer_token}, {@code api_key}, {@code api_secret},
 * or {@code access_token}. Thread-safe (immutable instance state).
 */
public final class ProducthuntNode implements NodeHandler {

    public static final String DEFAULT_TEMPLATE_ID = "83226eb9-3d21-4b15-8404-33ef438b2256";

    static final String OAUTH_URL = "https://api.producthunt.com/v2/oauth/token";
    static final String GRAPHQL_URL = "https://api.producthunt.com/v2/api/graphql";
    static final String USER_AGENT = "StudioQuack-ProductHunt/1.0";
    static final int MAX_PAGE = 20;
    static final int DESC_CHARS = 500;
    static final int OAUTH_ATTEMPTS = 3;

    /** Copied verbatim from the Python {@code QUERY_POSTS} body (whitespace-insensitive to GraphQL). */
    static final String QUERY_POSTS = """
            query GetPosts($postedAfter: DateTime, $postedBefore: DateTime, $topic: String,
                           $order: PostsOrder!, $first: Int!, $after: String) {
              posts(order: $order, postedAfter: $postedAfter, postedBefore: $postedBefore,
                    topic: $topic, first: $first, after: $after) {
                edges {
                  node {
                    id
                    name
                    tagline
                    description
                    votesCount
                    commentsCount
                    website
                    url
                    createdAt
                    makers { name }
                    topics { edges { node { name slug } } }
                  }
                }
                pageInfo { hasNextPage endCursor }
              }
            }
            """;

    static final String AUTH_REQUIRED = "Authentication required. Provide either: "
            + "(1) developer_token (recommended, avoids Cloudflare issues), or "
            + "(2) api_key as your Developer Token (leave api_secret empty), or "
            + "(3) api_key + api_secret for OAuth exchange. "
            + "Create credentials at producthunt.com/v2/oauth/applications. "
            + "NOTE: If you just added these parameters to the node definition, "
            + "you may need to re-deploy/update the node template on the platform "
            + "for the values to be recognized.";

    private final String templateId;

    public ProducthuntNode() {
        this.templateId = DataSupport.templateId("SQ_TEMPLATE_ID_PRODUCTHUNT", DEFAULT_TEMPLATE_ID);
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        String developerToken = null;
        String apiKey = null;
        String apiSecret = null;
        String accessToken = null;
        try {
            Map<String, Object> cfg = DataSupport.readConfig(request);
            developerToken = DataSupport.cfgStr(cfg, "developer_token", null);
            apiKey = DataSupport.cfgStr(cfg, "api_key", null);
            apiSecret = DataSupport.cfgStr(cfg, "api_secret", null);
            int timeout = DataSupport.cfgInt(cfg, "timeout_seconds", 25);
            int defaultLimit = DataSupport.cfgInt(cfg, "default_limit", 20);

            String bearer;
            if (developerToken != null) {
                bearer = developerToken;
            } else if (apiKey != null && apiSecret == null) {
                bearer = apiKey;
            } else if (apiKey != null) {
                TokenOrError tok = obtainBearer(apiKey, apiSecret, timeout, developerToken, apiKey, apiSecret);
                if (tok.error != null) {
                    return tok.error;
                }
                bearer = tok.token;
                accessToken = tok.token;
            } else {
                return DataSupport.invalidInput(AUTH_REQUIRED);
            }

            Map<String, Object> in = DataSupport.readInput(request, helper);
            Map<String, Object> variables = buildVariables(in, defaultLimit);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("query", QUERY_POSTS);
            payload.put("variables", variables);

            DataSupport.HttpResp resp = DataSupport.post(
                    GRAPHQL_URL, jsonHeaders(bearer), Json.write(payload), timeout);
            return interpretGraphql(resp, developerToken, apiKey, apiSecret, accessToken, bearer);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage();
            return fail("Error: " + e.getClass().getSimpleName() + ": "
                            + (msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg),
                    developerToken, apiKey, apiSecret, accessToken);
        }
    }

    /** GraphQL variables: order default NEWEST; first = min(limit or default_limit, 20). */
    static Map<String, Object> buildVariables(Map<String, Object> in, int defaultLimit) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("order", DataSupport.inStr(in, "order", "NEWEST"));
        int limit = DataSupport.inInt(in, "limit", defaultLimit);
        if (in == null || in.get("limit") == null
                || DataSupport.trimToNull(String.valueOf(in.get("limit"))) == null
                || limit == 0) {
            limit = defaultLimit;
        }
        variables.put("first", Math.min(limit, MAX_PAGE));
        putIfPresent(variables, "postedAfter", DataSupport.inStr(in, "posted_after", null));
        putIfPresent(variables, "postedBefore", DataSupport.inStr(in, "posted_before", null));
        putIfPresent(variables, "topic", DataSupport.inStr(in, "topic", null));
        putIfPresent(variables, "after", DataSupport.inStr(in, "after", null));
        return variables;
    }

    private Map<String, Object> interpretGraphql(DataSupport.HttpResp resp, String... secrets) {
        if (resp.error() != null) {
            return fail("Product Hunt API Error: " + resp.error(), secrets);
        }
        if (isCloudflareChallenge(resp)) {
            return fail("Product Hunt API Error: Cloudflare challenge on GraphQL endpoint (HTTP "
                    + resp.status() + "). Product Hunt API may be temporarily blocking automated "
                    + "requests. Try again shortly or verify your credentials.", secrets);
        }
        if (!resp.ok()) {
            return fail("Product Hunt API Error: " + DataSupport.httpStatusMessage(resp.status(), resp.body()),
                    secrets);
        }
        Map<String, Object> data = resp.jsonObject();
        if (data == null) {
            return fail("Product Hunt API Error: GraphQL response was not a JSON object", secrets);
        }
        if (data.containsKey("errors")) {
            return fail("GraphQL errors: " + Json.write(data.get("errors")), secrets);
        }
        Map<String, Object> postsData = Json.asMap(Json.asMap(data.get("data")).get("posts"));
        Map<String, Object> pageInfo = Json.asMap(postsData.get("pageInfo"));
        List<Map<String, Object>> posts = mapPosts(postsData);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "ok");
        output.put("posts_returned", posts.size());
        output.put("has_next_page", Boolean.TRUE.equals(pageInfo.get("hasNextPage")));
        output.put("end_cursor", pageInfo.get("endCursor"));
        output.put("posts", posts);
        return DataSupport.ports(output);
    }

    /**
     * OAuth client_credentials exchange. Retries up to 3 times; sleeps {@code 2*(attempt+1)}
     * seconds on Cloudflare or transport/HTTP failure. Does not cache the token.
     */
    private TokenOrError obtainBearer(String apiKey, String apiSecret, int timeout, String... secrets) {
        Map<String, Object> oauth = new LinkedHashMap<>();
        oauth.put("client_id", apiKey);
        oauth.put("client_secret", apiSecret);
        oauth.put("grant_type", "client_credentials");
        String body = Json.write(oauth);
        Map<String, String> headers = jsonHeaders(null);

        String lastError = null;
        for (int attempt = 0; attempt < OAUTH_ATTEMPTS; attempt++) {
            DataSupport.HttpResp resp = DataSupport.post(OAUTH_URL, headers, body, timeout);
            if (resp.error() != null) {
                lastError = "Product Hunt API Error: " + resp.error();
                if (!sleepBackoff(attempt)) {
                    return TokenOrError.err(fail("Product Hunt API Error: request interrupted", secrets));
                }
                continue;
            }
            if (isCloudflareChallenge(resp)) {
                lastError = "Product Hunt API Error: Cloudflare challenge (HTTP " + resp.status()
                        + "). Consider using a Developer Token instead.";
                if (!sleepBackoff(attempt)) {
                    return TokenOrError.err(fail("Product Hunt API Error: request interrupted", secrets));
                }
                continue;
            }
            if (!resp.ok()) {
                lastError = "Product Hunt API Error: " + DataSupport.httpStatusMessage(resp.status(), resp.body());
                if (!sleepBackoff(attempt)) {
                    return TokenOrError.err(fail("Product Hunt API Error: request interrupted", secrets));
                }
                continue;
            }
            Map<String, Object> data = resp.jsonObject();
            String rawAccess = data == null ? null : str(data.get("access_token"));
            String token = DataSupport.trimToNull(rawAccess);
            if (token == null) {
                String dump = data == null ? "null" : Json.write(data);
                return TokenOrError.err(fail(
                        "Error: ValueError: OAuth response missing access_token: " + dump,
                        concat(secrets, rawAccess)));
            }
            return TokenOrError.ok(token);
        }
        String fallback = lastError == null
                ? "Product Hunt API Error: Failed to obtain Product Hunt bearer token after retries."
                : lastError;
        return TokenOrError.err(fail(fallback, secrets));
    }

    /**
     * Cloudflare challenge: HTML ({@code Content-Type} contains {@code text/html}, inferred when
     * the body starts with {@code <}) OR the body is not a JSON object.
     */
    static boolean isCloudflareChallenge(DataSupport.HttpResp resp) {
        String body = resp == null || resp.body() == null ? "" : resp.body();
        String trimmed = body.trim();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '<') {
            return true;
        }
        Object parsed = DataSupport.parseJson(body);
        return !(parsed instanceof Map<?, ?>);
    }

    static List<Map<String, Object>> mapPosts(Map<String, Object> postsData) {
        List<Map<String, Object>> posts = new ArrayList<>();
        for (Object edgeObj : Json.asList(postsData == null ? null : postsData.get("edges"))) {
            Map<String, Object> node = Json.asMap(Json.asMap(edgeObj).get("node"));
            List<String> makers = new ArrayList<>();
            for (Object m : Json.asList(node.get("makers"))) {
                makers.add(str(Json.asMap(m).get("name")));
            }
            List<Map<String, Object>> topics = new ArrayList<>();
            for (Object te : Json.asList(Json.asMap(node.get("topics")).get("edges"))) {
                Map<String, Object> tn = Json.asMap(Json.asMap(te).get("node"));
                Map<String, Object> topic = new LinkedHashMap<>();
                topic.put("name", str(tn.get("name")));
                topic.put("slug", str(tn.get("slug")));
                topics.add(topic);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", str(node.get("id")));
            row.put("name", str(node.get("name")));
            row.put("tagline", str(node.get("tagline")));
            row.put("description", clip(str(node.get("description")), DESC_CHARS));
            row.put("votes_count", count(node.get("votesCount")));
            row.put("comments_count", count(node.get("commentsCount")));
            row.put("website", str(node.get("website")));
            row.put("url", str(node.get("url")));
            row.put("created_at", str(node.get("createdAt")));
            row.put("makers", makers);
            row.put("topics", topics);
            posts.add(row);
        }
        return posts;
    }

    static Map<String, String> jsonHeaders(String bearer) {
        if (bearer == null) {
            return DataSupport.headers(
                    "Content-Type", "application/json",
                    "Accept", "application/json",
                    "User-Agent", USER_AGENT);
        }
        return DataSupport.headers(
                "Authorization", "Bearer " + bearer,
                "Content-Type", "application/json",
                "Accept", "application/json",
                "User-Agent", USER_AGENT);
    }

    private static void putIfPresent(Map<String, Object> vars, String key, String value) {
        if (value != null) {
            vars.put(key, value);
        }
    }

    private static boolean sleepBackoff(int attempt) {
        try {
            Thread.sleep(2000L * (attempt + 1));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Map<String, Object> fail(String message, String... secrets) {
        return DataSupport.fail(DataSupport.redact(message, secrets));
    }

    private static String[] concat(String[] secrets, String extra) {
        if (extra == null || extra.isBlank()) {
            return secrets == null ? new String[0] : secrets;
        }
        int n = secrets == null ? 0 : secrets.length;
        String[] out = new String[n + 1];
        if (n > 0) {
            System.arraycopy(secrets, 0, out, 0, n);
        }
        out[n] = extra;
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Object count(Object v) {
        return v instanceof Number n ? n : 0;
    }

    private record TokenOrError(String token, Map<String, Object> error) {
        static TokenOrError ok(String token) {
            return new TokenOrError(token, null);
        }

        static TokenOrError err(Map<String, Object> error) {
            return new TokenOrError(null, error);
        }
    }
}
