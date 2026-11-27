package com.studioquack.nodelibrary.nodes.officialapi;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.OfficialApiIngressHandler;
import com.studioquack.nodelibrary.core.OfficialApiSpec;
import com.studioquack.nodelibrary.core.SelectOptionSyntax;
import com.studioquack.nodelibrary.nodes.testemulator.HelperClient;
import studioquack.StudioQuackHelper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub Search ({@code cc8bd8a8-4294-4b53-9ae5-a7a1923e87ec}): repository search, repo metadata,
 * and latest release via GitHub REST API v3.
 *
 * <p>Optional PAT is sent as {@code Authorization: Bearer} only — never logged or
 * returned. Thread-safe: the only instance field is the immutable template id.
 */
public final class GithubNode implements NodeHandler {

    static final String DEFAULT_ID = "cc8bd8a8-4294-4b53-9ae5-a7a1923e87ec";

    private static final String API_BASE = "https://api.github.com";
    private static final String ACCEPT = "application/vnd.github+json";
    private static final String API_VERSION = "2026-03-10";
    /** Config parameter keys referenced by {@code nodes/github/node_definition.json}. */
    private static final String CFG_OPERATION = "operation";
    private static final String CFG_WEBHOOK_KEY = "webhook_key";
    private static final String CFG_WEBHOOK_SECRET = "webhook_secret";

    /** Library-internal GitHub webhook ingress contract ({@code X-Hub-Signature-256} + ping handshake). */
    static final OfficialApiSpec.IngressSpec INGRESS_SPEC = OfficialApiSpec.IngressSpec.from(Map.of(
            "handshake", Map.of(
                    "type", "header_value_challenge",
                    "method", "POST",
                    "headerName", "x-github-event",
                    "headerValue", "ping",
                    "staticResponseBody", "{\"ok\":true,\"event\":\"ping\"}",
                    "responseContentType", "application/json",
                    "verifyBeforeHandshake", true),
            "verify", Map.of(
                    "type", "hmac_sha256_hex",
                    "header", "x-hub-signature-256",
                    "prefix", "sha256=",
                    "secretConfigKey", CFG_WEBHOOK_SECRET),
            "extract", Map.of(
                    "idempotencyHeader", "x-github-delivery",
                    "eventTypeHeader", "x-github-event",
                    "correlationKeyPath", "repository.full_name",
                    "fallbackCorrelationKeyPaths", List.of("pull_request.id", "issue.id", "head_commit.id"),
                    "fields", Map.of(
                            "action", "action",
                            "repository", "repository.full_name",
                            "sender", "sender.login",
                            "ref", "ref"))));

    private final String templateId;

    public GithubNode() {
        this(DataNodeSupport.templateId("github", DEFAULT_ID));
    }

    public GithubNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * One invoke: {@code search_repositories} / {@code get_repo} / {@code get_latest_release}.
     * Reply is keyed only by OUTPUT port {@code output}. Failures set {@code output.error};
     * this definition has no error port. Never logs {@code helperToken} or the PAT.
     *
     * @param request daemon POST body (null treated as empty input/config)
     * @param _helper unused; helper logs go through {@link DataNodeSupport#logs}
     * @return port map {@code {output: …}}; never {@code null}
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper _helper) {
        HelperClient logs = DataNodeSupport.logs(request);
        String action = "search_repositories";
        int timeoutSec = 25;
        try {
            Map<String, Object> cfg = DataNodeSupport.configParams(request);
            if (OfficialApiIngressHandler.isIngressInvocation(request, _helper)) {
                // webhook_key binding is validated by the daemon; verify provider HMAC using webhook_secret.
                Object ignoredKeyRef = cfg.get(CFG_WEBHOOK_KEY);
                if (ignoredKeyRef != null) {
                    logs.log(Map.of("message", "github ingress", "mode", "webhook"));
                }
                return OfficialApiIngressHandler.handle(
                        request, _helper, INGRESS_SPEC, "github", cfg, Clock.systemUTC());
            }
            Map<String, Object> in = DataNodeSupport.inputPort(request);
            timeoutSec = DataNodeSupport.positiveInt(cfg.get("timeout_seconds"), 25);
            int defaultPerPage = DataNodeSupport.positiveInt(cfg.get("default_per_page"), 20);

            String rawAction = DataNodeSupport.str(in.get("action")).trim();
            if (rawAction.isEmpty()) {
                rawAction = SelectOptionSyntax.extractValue(DataNodeSupport.str(cfg.get(CFG_OPERATION)));
            }
            if (!rawAction.isEmpty()) {
                action = rawAction;
            }
            logs.log(Map.of("message", "github start", "action", action));

            if ("listen".equals(action)) {
                return DataSupport.invalidInput(
                        "Inbound webhook operation 'listen' requires webhook ingress mode "
                                + "(BPMN message start / catch / boundary event).");
            }

            String pat = DataNodeSupport.str(cfg.get("personal_access_token")).trim();
            Map<String, String> headers = githubHeaders(pat);

            Map<String, Object> ports;
            if ("search_repositories".equals(action)) {
                ports = searchRepositories(in, headers, timeoutSec, defaultPerPage);
            } else if ("get_repo".equals(action)) {
                ports = getRepo(in, headers, timeoutSec);
            } else if ("get_latest_release".equals(action)) {
                ports = getLatestRelease(in, headers, timeoutSec);
            } else {
                ports = DataSupport.invalidInput(
                        "Unknown action: " + action
                                + ". Use search_repositories, get_repo, get_latest_release, or listen.");
            }
            logs.log(Map.of("message", "github done", "action", action));
            return ports;
        } catch (DataNodeSupport.DataHttpException e) {
            logs.log(Map.of("message", "github done", "action", action));
            if (e.timeout) {
                return DataSupport.fail("GitHub API Error: timed out after " + timeoutSec + "s");
            }
            String msg = e.getMessage();
            return DataSupport.fail("GitHub API Error: "
                    + (msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            logs.log(Map.of("message", "github done", "action", action));
            return DataSupport.fail(
                    "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Required GitHub REST headers; PAT only when non-blank. */
    private static Map<String, String> githubHeaders(String pat) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", ACCEPT);
        headers.put("User-Agent", DataNodeSupport.USER_AGENT);
        headers.put("X-GitHub-Api-Version", API_VERSION);
        if (pat != null && !pat.isBlank()) {
            headers.put("Authorization", "Bearer " + pat);
        }
        return headers;
    }

    /** GET /search/repositories. {@code query} or alias {@code q} is required. */
    private static Map<String, Object> searchRepositories(Map<String, Object> in,
                                                          Map<String, String> headers,
                                                          int timeoutSec, int defaultPerPage) {
        String query = DataNodeSupport.firstNonBlank(in, "query", "q");
        if (query.isEmpty()) {
            return DataSupport.invalidInput("query is required for search_repositories.");
        }
        String sort = DataNodeSupport.str(in.get("sort")).trim();
        if (sort.isEmpty()) {
            sort = "stars";
        }
        String order = DataNodeSupport.str(in.get("order")).trim();
        if (order.isEmpty()) {
            order = "desc";
        }
        int perPage = Math.min(DataNodeSupport.positiveInt(in.get("per_page"), defaultPerPage), 100);
        int page = DataNodeSupport.toInt(in.get("page"), 1);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("sort", sort);
        params.put("order", order);
        params.put("per_page", perPage);
        params.put("page", page);

        DataNodeSupport.HttpResult http = DataNodeSupport.get(
                API_BASE + "/search/repositories", headers, params, timeoutSec);
        if (!http.ok()) {
            return httpError(http);
        }
        Map<String, Object> data = DataNodeSupport.asObject(DataNodeSupport.parseJson(http.body));
        List<Object> repositories = new ArrayList<>();
        for (Object raw : DataNodeSupport.asArray(data.get("items"))) {
            repositories.add(mapSearchItem(DataNodeSupport.asObject(raw)));
        }
        String remaining = http.header("X-RateLimit-Remaining");
        if (remaining.isBlank()) {
            remaining = "unknown";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("total_count", DataNodeSupport.toInt(data.get("total_count"), 0));
        out.put("repos_returned", repositories.size());
        out.put("rate_limit_remaining", remaining);
        out.put("repositories", repositories);
        return DataNodeSupport.outputPorts(out);
    }

    /** GET /repos/{owner}/{repo}. Owner and repo are path-encoded. */
    private static Map<String, Object> getRepo(Map<String, Object> in, Map<String, String> headers,
                                               int timeoutSec) {
        String owner = DataNodeSupport.str(in.get("owner")).trim();
        String repo = DataNodeSupport.str(in.get("repo")).trim();
        if (owner.isEmpty() || repo.isEmpty()) {
            return DataSupport.invalidInput("owner and repo are required for get_repo.");
        }
        String url = API_BASE + "/repos/" + DataNodeSupport.pathSegment(owner)
                + "/" + DataNodeSupport.pathSegment(repo);
        DataNodeSupport.HttpResult http = DataNodeSupport.get(url, headers, null, timeoutSec);
        if (!http.ok()) {
            return httpError(http);
        }
        Map<String, Object> item = DataNodeSupport.asObject(DataNodeSupport.parseJson(http.body));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("repository", mapRepo(item));
        return DataNodeSupport.outputPorts(out);
    }

    /** GET .../releases/latest. HTTP 404 is success with {@code release: null}. */
    private static Map<String, Object> getLatestRelease(Map<String, Object> in,
                                                        Map<String, String> headers,
                                                        int timeoutSec) {
        String owner = DataNodeSupport.str(in.get("owner")).trim();
        String repo = DataNodeSupport.str(in.get("repo")).trim();
        if (owner.isEmpty() || repo.isEmpty()) {
            return DataSupport.invalidInput("owner and repo are required for get_latest_release.");
        }
        String url = API_BASE + "/repos/" + DataNodeSupport.pathSegment(owner)
                + "/" + DataNodeSupport.pathSegment(repo) + "/releases/latest";
        DataNodeSupport.HttpResult http = DataNodeSupport.get(url, headers, null, timeoutSec);
        if (http.status == 404) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "ok");
            out.put("release", null);
            out.put("message", "No releases found.");
            return DataNodeSupport.outputPorts(out);
        }
        if (!http.ok()) {
            return httpError(http);
        }
        Map<String, Object> rel = DataNodeSupport.asObject(DataNodeSupport.parseJson(http.body));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("release", mapRelease(rel));
        return DataNodeSupport.outputPorts(out);
    }

    /** 4xx/5xx without response body (body may echo request context). */
    private static Map<String, Object> httpError(DataNodeSupport.HttpResult http) {
        return DataSupport.fail(
                "GitHub API Error: HTTP " + http.status + " for url: " + http.url);
    }

    private static Map<String, Object> mapSearchItem(Map<String, Object> item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("full_name", DataNodeSupport.str(item.get("full_name")));
        row.put("description", DataNodeSupport.truncate(DataNodeSupport.str(item.get("description")), 300));
        row.put("html_url", DataNodeSupport.str(item.get("html_url")));
        row.put("stargazers_count", DataNodeSupport.toInt(item.get("stargazers_count"), 0));
        row.put("forks_count", DataNodeSupport.toInt(item.get("forks_count"), 0));
        row.put("open_issues_count", DataNodeSupport.toInt(item.get("open_issues_count"), 0));
        row.put("language", item.get("language"));
        row.put("topics", new ArrayList<>(DataNodeSupport.asArray(item.get("topics"))));
        row.put("license", licenseSpdx(item.get("license")));
        row.put("pushed_at", DataNodeSupport.str(item.get("pushed_at")));
        row.put("created_at", DataNodeSupport.str(item.get("created_at")));
        row.put("updated_at", DataNodeSupport.str(item.get("updated_at")));
        return row;
    }

    private static Map<String, Object> mapRepo(Map<String, Object> item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("full_name", DataNodeSupport.str(item.get("full_name")));
        row.put("description", DataNodeSupport.str(item.get("description")));
        row.put("html_url", DataNodeSupport.str(item.get("html_url")));
        row.put("stargazers_count", DataNodeSupport.toInt(item.get("stargazers_count"), 0));
        row.put("forks_count", DataNodeSupport.toInt(item.get("forks_count"), 0));
        row.put("open_issues_count", DataNodeSupport.toInt(item.get("open_issues_count"), 0));
        row.put("subscribers_count", DataNodeSupport.toInt(item.get("subscribers_count"), 0));
        row.put("language", item.get("language"));
        row.put("topics", new ArrayList<>(DataNodeSupport.asArray(item.get("topics"))));
        row.put("license", licenseSpdx(item.get("license")));
        row.put("pushed_at", DataNodeSupport.str(item.get("pushed_at")));
        row.put("created_at", DataNodeSupport.str(item.get("created_at")));
        row.put("default_branch", DataNodeSupport.str(item.get("default_branch")));
        return row;
    }

    private static Map<String, Object> mapRelease(Map<String, Object> rel) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tag_name", DataNodeSupport.str(rel.get("tag_name")));
        row.put("name", DataNodeSupport.str(rel.get("name")));
        row.put("body", DataNodeSupport.truncate(DataNodeSupport.str(rel.get("body")), 1000));
        row.put("published_at", DataNodeSupport.str(rel.get("published_at")));
        row.put("html_url", DataNodeSupport.str(rel.get("html_url")));
        row.put("prerelease", Boolean.TRUE.equals(rel.get("prerelease")));
        Map<String, Object> author = DataNodeSupport.asObject(rel.get("author"));
        row.put("author", DataNodeSupport.str(author.get("login")));
        return row;
    }

    /** {@code license.spdx_id}, or {@code null} when license is missing/non-object. */
    private static Object licenseSpdx(Object license) {
        if (!(license instanceof Map<?, ?>)) {
            return null;
        }
        return DataNodeSupport.asObject(license).get("spdx_id");
    }
}
