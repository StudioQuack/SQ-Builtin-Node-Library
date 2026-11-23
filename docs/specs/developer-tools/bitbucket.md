# Bitbucket

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/bitbucket/`
- **Researched:** 2026-08-20
- **Official API:** Bitbucket Cloud REST API 2.0
- **Docs:** https://developer.atlassian.com/cloud/bitbucket/rest/intro/
- **Auth:** `oauth2` | `api_token` — secret binding: `accessToken` (and `email` for Basic). App passwords are **retired** (fully disabled **2026-07-28**) and must not be the primary auth.

## Scope

One node for **Bitbucket Cloud** only (`https://api.bitbucket.org/2.0`). Repository metadata, source files, pull requests, issues, Pipelines, commits, and webhook `listen`. Not Bitbucket Data Center / Server (different product and REST). Not HTML scrape. Distinct from GitHub and GitLab nodes.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `repo_get` | GET | `/2.0/repositories/{workspace}/{repo_slug}` | Load one repository |
| `repo_list` | GET | `/2.0/repositories/{workspace}` | List workspace repositories |
| `file_get` | GET | `/2.0/repositories/{workspace}/{repo_slug}/src/{commit}/{path}` | Read file bytes at a commit; store as type `file` |
| `pr_create` | POST | `/2.0/repositories/{workspace}/{repo_slug}/pullrequests` | Open a pull request |
| `pr_get` | GET | `/2.0/repositories/{workspace}/{repo_slug}/pullrequests/{id}` | Get one PR |
| `pr_merge` | POST | `/2.0/repositories/{workspace}/{repo_slug}/pullrequests/{id}/merge` | Merge a PR |
| `issue_create` | POST | `/2.0/repositories/{workspace}/{repo_slug}/issues` | Create a repository issue |
| `issue_get` | GET | `/2.0/repositories/{workspace}/{repo_slug}/issues/{id}` | Get one issue |
| `pipeline_run` | POST | `/2.0/repositories/{workspace}/{repo_slug}/pipelines` | Start a Pipeline |
| `pipeline_get` | GET | `/2.0/repositories/{workspace}/{repo_slug}/pipelines/{uuid}` | Get pipeline status (`{uuid}` includes braces, URL-encoded) |
| `commit_list` | GET | `/2.0/repositories/{workspace}/{repo_slug}/commits` | List commits (`include`/`exclude` refs) |
| `listen` | POST | `/2.0/repositories/{workspace}/{repo_slug}/hooks` | Register repository webhook; inbound maps onto `output` |

Dropped: app-password auth as a first-class `authType`. Workspace/team webhook subject types that Bitbucket marks deprecated — register on the **repository** (or current workspace resource), not legacy team URLs.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `repo_get` | no | Chooses the row above |
| `workspace` | string | yes | | no | Workspace slug (also overridable on input) |
| `authType` | select | yes | `api_token` | no | `api_token` (Atlassian email + token Basic) or `oauth2` (Bearer) |
| `email` | string | when `api_token` | | no | Atlassian account email (username for Basic). **Not** Bitbucket username |
| `accessToken` | password | yes | | yes | API token or OAuth access token. Never logged |
| `timeoutSeconds` | number | no | `30` | no | Connect + request timeout |
| `webhookSecret` | password | no | | yes | HMAC secret for `listen` (`X-Hub-Signature`) |
| `defaultRepoSlug` | string | no | | no | Default repository slug |

Do **not** add `appPassword` as a recommended config. If a legacy graph still names it, treat as `accessToken` only after documenting that Bitbucket returns **401** for app passwords after 2026-07-28.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status: "ok", … }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Input fields (by operation)

Shared: `workspace` (optional override), `repoSlug` (required except `repo_list`).

### `operation = repo_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repoSlug` | string | yes* | Repository slug |

### `operation = repo_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `role` | string | no | `member` / `admin` / `contributor` |
| `q` | string | no | Query filter (Bitbucket `q` syntax) |
| `page` | number | no | Page number |
| `pagelen` | number | no | Page length (Cloud max typically 100) |

### `operation = file_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repoSlug` | string | yes* | Repository |
| `commit` | string | yes | SHA, branch, or tag |
| `path` | string | yes | File path in the tree |

### `operation = pr_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repoSlug` | string | yes* | Repository |
| `title` | string | yes | PR title |
| `sourceBranch` | string | yes | Source branch name |
| `destinationBranch` | string | yes | Destination branch name |
| `description` | string | no | Markdown description |
| `closeSourceBranch` | boolean | no | Close source after merge |

### `operation = pr_get` / `pr_merge`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repoSlug` | string | yes* | Repository |
| `pullRequestId` | number | yes | PR id |
| `message` | string | no | `pr_merge` commit message |
| `mergeStrategy` | string | no | `merge_commit`, `squash`, `fast_forward` |
| `closeSourceBranch` | boolean | no | `pr_merge` |

### `operation = issue_create` / `issue_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repoSlug` | string | yes* | Repository |
| `title` | string | yes | `issue_create` |
| `content` | string | no | `issue_create` markup |
| `kind` | string | no | `bug` / `enhancement` / `proposal` / `task` |
| `priority` | string | no | `trivial` … `critical` |
| `issueId` | number | yes | `issue_get` |

### `operation = pipeline_run` / `pipeline_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repoSlug` | string | yes* | Repository |
| `targetRefType` | string | no | `branch`, `tag`, `commit` (default `branch`) |
| `refName` | string | yes | `pipeline_run` — branch/tag/SHA |
| `variables` | array | no | `{ key, value, secured }` |
| `pipelineUuid` | string | yes | `pipeline_get` — UUID with or without `{}` |

### `operation = commit_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repoSlug` | string | yes* | Repository |
| `include` | string | no | Ref to include |
| `exclude` | string | no | Ref to exclude |
| `pagelen` | number | no | Page size |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `register` (default) or `map` |
| `repoSlug` | string | yes* | Repository |
| `ingressUrl` | string | yes when `register` | StudioQuack message-event ingress URL |
| `events` | array | no | Default `repo:push`, `pullrequest:created`, `pullrequest:updated`, `pullrequest:fulfilled`, `issue:created` |
| `description` | string | no | Hook description |
| `secret` | password | no | Prefer config `webhookSecret`; HMAC required |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `error` | string | failure | Never secrets |
| `httpStatus` | number | HTTP error | Status |
| `repository` | object | `repo_get` | `uuid`, `full_name`, `links.html.href`, `mainbranch.name` |
| `repositories` | array | `repo_list` | Page of repo summaries + `next` URL stripped of credentials |
| `file` | file | `file_get` | File store ref |
| `path` | string | `file_get` | Path |
| `pullRequest` | object | PR ops | `id`, `title`, `state`, `links.html.href` |
| `issue` | object | issue ops | `id`, `title`, `state`, `priority` |
| `pipeline` | object | pipeline ops | `uuid`, `state.name`, `target.ref_name` |
| `commits` | array | `commit_list` | `hash`, `message` (truncated 500), `date`, `author.raw` |
| `hook` | object | `listen` register | `uuid`, `url` (no secret), `events` |
| `event` | string | inbound | `X-Event-Key` |
| `payload` | object | inbound | Sanitized JSON |

## Legal

- [Atlassian Cloud Terms of Service](https://www.atlassian.com/legal/cloud-terms-of-service) and [Bitbucket Cloud documentation](https://support.atlassian.com/bitbucket-cloud/)
- REST intro / auth: https://developer.atlassian.com/cloud/bitbucket/rest/intro/
- API tokens: https://support.atlassian.com/bitbucket-cloud/docs/using-app-passwords/ (page now documents **API tokens**; app passwords deprecated)
- App password EOL: brownouts through 2026-07-27; **fully removed 2026-07-28** — https://community.atlassian.com/forums/Bitbucket-articles/Deprecation-notice-Bitbucket-Cloud-app-password-brownout/ba-p/3237429
- Webhooks + HMAC: https://support.atlassian.com/bitbucket-cloud/docs/manage-webhooks/
- Rate limits: Bitbucket Cloud applies per-user / per-integration throttling and returns **HTTP 429** with `Retry-After`. Paginated resources use `pagelen` (max 100). Treat 429 as retryable; other 4xx are not.
- PII: repository issues, PRs, and file contents may include personal data. Operator owns the Atlassian account.
- Official-API-first. Not Data Center scrape.

## Implementation notes

- JDK HTTP only. Base `https://api.bitbucket.org/2.0`. `Accept: application/json`.
- `authType=api_token`: `Authorization: Basic base64(email + ":" + token)`. Email **must** be the Atlassian account email, not the Bitbucket username. `authType=oauth2`: `Authorization: Bearer <accessToken>`.
- Timeouts default 30s. Response cap **8 MiB**. `file_get` streams into the file store up to that cap.
- Retry 429/5xx with jitter; honor `Retry-After`. No retry on other 4xx.
- TLS verify on. Cloud only — no `insecureTls`.
- Success `{ status: "ok", … }`; failures `{ error }`. Never throw past dispatch. Never log `accessToken` / `webhookSecret`.
- Pipeline UUID in path: if the operator omits braces, wrap as `{uuid}` then URL-encode.
- `listen` register: `POST .../hooks` with `url`, `active=true`, `events`, `secret`. Ingress URL is StudioQuack message-event ingress (not a second node).
- Inbound: require `X-Hub-Signature` `sha256={hex}` HMAC of **raw** body with `webhookSecret`. Constant-time compare. Reject if missing/mismatch. Map `X-Event-Key` + body onto `output` and correlate as a BPMN message event.
- Pagination: follow `next` only when the operator asks for another page via input; do not silently walk unbounded lists.
