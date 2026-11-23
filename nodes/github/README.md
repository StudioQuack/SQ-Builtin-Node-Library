# GitHub Search

## Overview

In-process StudioQuack builtin (`templateId` `cc8bd8a8-4294-4b53-9ae5-a7a1923e87ec`, override `SQ_TEMPLATE_ID_GITHUB`). Handler: `GithubNode`. It calls the official [GitHub REST API](https://docs.github.com/en/rest) v3 over HTTPS and maps the result onto a single OUTPUT port `output`.

This node is **read-only**. It searches public (and, with a PAT, authorized private) repositories, loads one repository’s metadata, or reads the latest GitHub Release. It does not create issues, stars, releases, or webhooks. No helper-channel calls. Tokens are read per invoke from `configurationParameters` and are never cached, logged, or returned.

Provider docs: [REST overview](https://docs.github.com/en/rest), [API versions](https://docs.github.com/en/rest/about-the-rest-api/api-versions) (`X-GitHub-Api-Version`), [search repositories](https://docs.github.com/en/rest/search/search#search-repositories), [get a repository](https://docs.github.com/en/rest/repos/repos#get-a-repository), [get the latest release](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Daemon delivers `inputVariables.input`, or `currentNode.inputList[]` with `targetPortValue=input` (a one-element list unwraps). Unknown keys are ignored. Do **not** put the PAT here. |
| `output` | OUTPUT | object | yes | Success: `status=ok` plus action-specific fields. Failure: `{error}` only — there is no error OUTPUT port. The PAT is never present. |

### Input fields

Incoming data selects the REST operation (`action`) and supplies that operation’s arguments. Search fields are ignored by `get_repo` / `get_latest_release`; `owner`/`repo` are ignored by `search_repositories`.

| Field | Type | Required | Default | Accepted values | How it is used |
|---|---|---|---|---|---|
| `action` | string | no | `search_repositories` | `search_repositories` \| `get_repo` \| `get_latest_release` (case-sensitive) | Dispatches the GitHub path. Any other string fails with no HTTP call. |
| `query` | string | when searching | — | GitHub search syntax (`language:`, `stars:`, `topic:`, `org:`, …) | Sent as `q` on `GET /search/repositories`. Alias `q` when `query` is blank. |
| `sort` | string | no | `stars` | `stars` \| `forks` \| `updated` \| `help-wanted-issues` | Search sort. Other strings are forwarded and typically yield HTTP 422. |
| `order` | string | no | `desc` | `desc` \| `asc` | Search order. Other strings are forwarded and typically yield HTTP 422. |
| `per_page` | number | no | config `default_per_page` (20) | positive int; sent as `min(n, 100)` | Search page size. Values `<= 0` use the config default. |
| `page` | number | no | `1` | 1-based integer | Search page. GitHub Search only returns the first 1,000 matches (`page * per_page` must stay in that window). |
| `owner` | string | with `repo` for get actions | — | GitHub login | Path segment on `/repos/{owner}/{repo}` (URL-encoded). Not `owner/name`. |
| `repo` | string | with `owner` for get actions | — | repository name only | Path segment. |

### Output fields

| Field | Type | When present | Meaning |
|---|---|---|---|
| `status` | string | success | Exact `ok`. Absent when `error` is set. HTTP 404 on latest-release is success (`release=null`). |
| `total_count` | number | `search_repositories` | GitHub `total_count` (index size, not page length). |
| `repos_returned` | number | `search_repositories` | `repositories.length` on this page. |
| `rate_limit_remaining` | string | `search_repositories` | `X-RateLimit-Remaining`, or `unknown` if GitHub omitted the header (Search quota, not core). |
| `repositories` | array | `search_repositories` | Page of summaries. Each: `full_name`, `description` (truncated to 300 chars), `html_url`, `stargazers_count`, `forks_count`, `open_issues_count`, `language` (nullable), `topics` (array), `license` (SPDX id or null), `pushed_at`, `created_at`, `updated_at`. |
| `repository` | object | `get_repo` | Full description (not truncated) plus `subscribers_count` and `default_branch` (no `updated_at`). |
| `release` | object or null | `get_latest_release` | `tag_name`, `name`, `body` (truncated to 1000 chars), `published_at`, `html_url`, `prerelease`, `author` (login). `null` when GitHub returns 404. |
| `message` | string | latest-release 404 | Exact `No releases found.` |
| `error` | string | failure | See error mapping below. Never contains the PAT. |

## Configuration parameters

Instance `configurationParameters` (shortCode → value). Secrets stay here, not on the input port.

| Name | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `personal_access_token` | password | no | `""` | classic `ghp_…` or fine-grained `github_pat_…` | Blank → unauthenticated public API. Non-blank → every request adds `Authorization: Bearer <token>`. Fine-grained public-repo metadata is enough for public search; private/org data needs those repositories selected. |
| `timeout_seconds` | number | no | `25` | positive number; `<= 0` → 25 | Per-request HTTP timeout (connect + read). Keep below the daemon stateful invoke budget (typically 300s). |
| `default_per_page` | number | no | `20` | positive number; `<= 0` → 20 | Search page size when input `per_page` is omitted or not positive. Still capped at 100 on the wire. Unused by get actions. |

## How it works

1. The catalog registers `GithubNode` under `cc8bd8a8-4294-4b53-9ae5-a7a1923e87ec` (or `SQ_TEMPLATE_ID_GITHUB`). The dispatcher selects this handler by `templateId`.
2. `handle` reads `configurationParameters` and the `input` port via `DataNodeSupport`. It never throws past `handle()`.
3. `action` (default `search_repositories`) selects `GET https://api.github.com/search/repositories`, `GET /repos/{owner}/{repo}`, or `GET /repos/{owner}/{repo}/releases/latest`.
4. Every call uses the shared JDK `HttpClient` (HTTP/1.1, normal redirects, request timeout, 8 MiB body cap) through `DataNodeSupport.get`.
5. Headers on every request: `Accept: application/vnd.github+json`, `User-Agent: StudioQuack/1.0`, `X-GitHub-Api-Version: 2026-03-10` (current REST calendar version; https://docs.github.com/en/rest/about-the-rest-api/api-versions), and `Authorization: Bearer …` only when the PAT is non-blank.
6. Pagination is offset-style: 1-based `page` + `per_page` (max 100). There is no cursor. Search is also capped at 1,000 matches by GitHub.
7. Failures land on `output.error` (no error port):
   - `query is required for search_repositories.`
   - `owner and repo are required for get_repo.` / `…get_latest_release.`
   - `Unknown action: {action}. Use search_repositories, get_repo, or get_latest_release.`
   - `GitHub API Error: HTTP {status} for url: {url}` (4xx/5xx except latest-release 404)
   - `GitHub API Error: timed out after {n}s`
   - `GitHub API Error: {message}` (other transport)
   - `Error: {simpleName}: {message}` (unexpected)
8. Helper logs record only `github start` / `github done` plus `action` — never the PAT, never `helperToken`.

## Security & legal

- The PAT is read only from `configurationParameters.personal_access_token`. It is never logged, never copied onto `output`, and never interpolated into error strings. Do not place it on the input port.
- GitHub [Terms of Service](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service) and [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies) apply to every call this node makes. The customer owns the token and the GitHub account it represents.
- Typical quotas (GitHub may change these): Search ~10 req/min unauthenticated / ~30 req/min with a PAT; core REST ~60 req/hour unauthenticated / ~5,000 req/hour with a PAT. `search_repositories` returns `rate_limit_remaining` from `X-RateLimit-Remaining`. HTTP 403 with remaining quota 0 surfaces as a generic HTTP 403 error.
- This node does not scrape HTML; it uses the official REST API with an explicit API version header.

## Examples

### Search repositories

Input:

```json
{
  "action": "search_repositories",
  "query": "language:python stars:>100 topic:automation",
  "sort": "stars",
  "order": "desc",
  "per_page": 2,
  "page": 1
}
```

Output (shape):

```json
{
  "status": "ok",
  "total_count": 1842,
  "repos_returned": 2,
  "rate_limit_remaining": "29",
  "repositories": [
    {
      "full_name": "microsoft/autogen",
      "description": "A programming framework for agentic AI",
      "html_url": "https://github.com/microsoft/autogen",
      "stargazers_count": 40000,
      "forks_count": 5800,
      "open_issues_count": 210,
      "language": "Python",
      "topics": ["agents", "llm"],
      "license": "MIT",
      "pushed_at": "2026-08-01T12:00:00Z",
      "created_at": "2023-08-18T00:00:00Z",
      "updated_at": "2026-08-01T12:00:00Z"
    }
  ]
}
```

### Get one repository and latest release

`get_repo` input: `{"action":"get_repo","owner":"microsoft","repo":"autogen"}` → `output.repository` with `default_branch` and `subscribers_count`.

`get_latest_release` input: `{"action":"get_latest_release","owner":"microsoft","repo":"autogen"}` → `output.release` with `tag_name` / `html_url`, or `release: null` and `message: "No releases found."` when the repo has no releases.
