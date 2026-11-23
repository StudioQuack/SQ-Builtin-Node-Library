# GitLab

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/gitlab/`
- **Researched:** 2026-08-20
- **Official API:** GitLab REST API v4 (major version `4`; GraphQL exists but is **not** this node’s surface — every catalog verb has a REST equivalent)
- **Docs:** https://docs.gitlab.com/api/rest/
- **Auth:** `oauth2` | `pat` — secret binding: `accessToken`

## Scope

One node for GitLab.com or a self-managed GitLab host. Calls REST under `{baseUrl}/api/v4` with JDK HTTP. Covers projects, repository files, issues, merge requests, CI pipelines/jobs, releases, branches, tags, and webhook `listen`. Do not call GraphQL unless a future verb has no REST equivalent. Do not scrape the GitLab UI. Distinct from the shipped GitHub node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `project_get` | GET | `/api/v4/projects/{id}` | Load one project (`id` numeric or URL-encoded `namespace/path`) |
| `project_list` | GET | `/api/v4/projects` | List projects visible to the token (`membership`, `search`, pagination) |
| `file_get` | GET | `/api/v4/projects/{id}/repository/files/{file_path}` | Read a file at `ref`; store content as type `file` |
| `file_create` | POST | `/api/v4/projects/{id}/repository/files/{file_path}` | Commit a new file |
| `file_update` | PUT | `/api/v4/projects/{id}/repository/files/{file_path}` | Commit an update to an existing file |
| `file_delete` | DELETE | `/api/v4/projects/{id}/repository/files/{file_path}` | Commit a file deletion |
| `issue_create` | POST | `/api/v4/projects/{id}/issues` | Create an issue |
| `issue_get` | GET | `/api/v4/projects/{id}/issues/{iid}` | Get issue by **project `iid`** (not global `id`) |
| `issue_update` | PUT | `/api/v4/projects/{id}/issues/{iid}` | Update title/description/labels/state |
| `issue_note` | POST | `/api/v4/projects/{id}/issues/{iid}/notes` | Add a comment on an issue |
| `mr_create` | POST | `/api/v4/projects/{id}/merge_requests` | Open a merge request |
| `mr_get` | GET | `/api/v4/projects/{id}/merge_requests/{iid}` | Get MR by project `iid` |
| `mr_update` | PUT | `/api/v4/projects/{id}/merge_requests/{iid}` | Update MR fields |
| `mr_accept` | PUT | `/api/v4/projects/{id}/merge_requests/{iid}/merge` | Merge (accept) an MR |
| `pipeline_create` | POST | `/api/v4/projects/{id}/pipeline` | Create a pipeline on `ref` |
| `pipeline_get` | GET | `/api/v4/projects/{id}/pipelines/{pipeline_id}` | Get one pipeline |
| `job_cancel` | POST | `/api/v4/projects/{id}/jobs/{job_id}/cancel` | Cancel a job |
| `job_retry` | POST | `/api/v4/projects/{id}/jobs/{job_id}/retry` | Retry a job |
| `release_create` | POST | `/api/v4/projects/{id}/releases` | Create a release for a tag |
| `branch_create` | POST | `/api/v4/projects/{id}/repository/branches` | Create a branch |
| `tag_create` | POST | `/api/v4/projects/{id}/repository/tags` | Create a tag |
| `listen` | POST | `/api/v4/projects/{id}/hooks` | Register a project webhook; inbound maps onto `output` |

Path `{file_path}` is URL-encoded (including `/` → `%2F`). `{id}` is numeric project id or URL-encoded path.

Retired / unofficial verbs are omitted. Do not use query-string `private_token` (token in URL).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `project_get` | no | Chooses the row above |
| `baseUrl` | string | no | `https://gitlab.com` | no | Origin only (no path). Self-host allowed. Trailing slash stripped |
| `authType` | select | yes | `pat` | no | `pat` → `PRIVATE-TOKEN`; `oauth2` → `Authorization: Bearer` |
| `accessToken` | password | yes | | yes | PAT / project / group token, or OAuth access token. Never logged |
| `timeoutSeconds` | number | no | `30` | no | Connect + request timeout |
| `insecureTls` | boolean | no | `false` | no | Lab-only; skip TLS verify on self-host. Production must stay `false` |
| `webhookSecret` | password | no | | yes | Signing token (`whsec_…`) and/or secret token for `listen` verification |
| `defaultProjectId` | string | no | | no | Default `{id}` when input omits `projectId` |

File payloads use type `file` (platform ref-id), never inline bytes.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Do not put tokens here |
| `output` | OUTPUT | object — `{ status: "ok", … }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Input fields (by operation)

Shared: `projectId` (string, required unless `defaultProjectId`) — numeric id or `group/project`.

### `operation = project_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Path `{id}` |

### `operation = project_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `search` | string | no | Filter by name/path |
| `membership` | boolean | no | Only projects the user is a member of |
| `owned` | boolean | no | Only owned projects |
| `simple` | boolean | no | Compact representation |
| `page` | number | no | 1-based page |
| `perPage` | number | no | Cap 100 |

### `operation = file_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `filePath` | string | yes | Repository path (encoded on the wire) |
| `ref` | string | yes | Branch, tag, or SHA |

### `operation = file_create` / `file_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `filePath` | string | yes | Path in repo |
| `branch` | string | yes | Target branch |
| `contentFile` | file | yes | Platform file ref to commit (UTF-8 or binary) |
| `commitMessage` | string | yes | Commit message |
| `startBranch` | string | no | Create branch from this ref (`file_create`) |
| `encoding` | string | no | `text` (default) or `base64` if the file is binary |

### `operation = file_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `filePath` | string | yes | Path to delete |
| `branch` | string | yes | Branch |
| `commitMessage` | string | yes | Commit message |

### `operation = issue_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `title` | string | yes | Issue title |
| `description` | string | no | Markdown body |
| `labels` | string | no | Comma-separated labels |
| `assigneeIds` | array | no | User ids |
| `milestoneId` | number | no | Milestone |

### `operation = issue_get` / `issue_update` / `issue_note`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `issueIid` | number | yes | Project-scoped iid |
| `title` | string | no | `issue_update` |
| `description` | string | no | `issue_update` |
| `stateEvent` | string | no | `close` / `reopen` (`issue_update`) |
| `labels` | string | no | `issue_update` |
| `body` | string | yes | `issue_note` comment text |

### `operation = mr_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `sourceBranch` | string | yes | Source |
| `targetBranch` | string | yes | Target |
| `title` | string | yes | Title |
| `description` | string | no | Description |
| `removeSourceBranch` | boolean | no | Delete source after merge |

### `operation = mr_get` / `mr_update` / `mr_accept`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `mergeRequestIid` | number | yes | Project-scoped iid |
| `title` | string | no | `mr_update` |
| `description` | string | no | `mr_update` |
| `stateEvent` | string | no | `close` / `reopen` (`mr_update`) |
| `mergeCommitMessage` | string | no | `mr_accept` |
| `squash` | boolean | no | `mr_accept` |
| `shouldRemoveSourceBranch` | boolean | no | `mr_accept` |
| `sha` | string | no | `mr_accept` — HEAD SHA to prevent races |

### `operation = pipeline_create` / `pipeline_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `ref` | string | yes | `pipeline_create` — branch or tag |
| `variables` | array | no | `{ key, value }` CI variables |
| `pipelineId` | number | yes | `pipeline_get` |

### `operation = job_cancel` / `job_retry`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `jobId` | number | yes | Job id |

### `operation = release_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `tagName` | string | yes | Existing or new tag |
| `name` | string | no | Release title |
| `description` | string | no | Release notes |
| `ref` | string | no | Create tag from this commit if tag is new |

### `operation = branch_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `branch` | string | yes | New branch name |
| `ref` | string | yes | Source SHA/branch/tag |

### `operation = tag_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | yes* | Project |
| `tagName` | string | yes | Tag name |
| `ref` | string | yes | Target commit |
| `message` | string | no | Annotated tag message |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `register` (default) or `map` (inbound already at ingress) |
| `projectId` | string | yes* | Project for hook registration |
| `ingressUrl` | string | yes when `register` | StudioQuack message-event ingress HTTPS URL |
| `events` | array | no | GitLab hook flags; default `push_events`, `merge_requests_events`, `pipeline_events`, `issues_events`, `job_events` |
| `enableSslVerification` | boolean | no | Default `true` (GitLab → ingress TLS) |
| `hookId` | number | no | Update existing hook instead of create |

`listen` maps inbound verified payloads onto `output` (see Implementation notes). Signature verification is mandatory before mapping.

## Output fields (by operation)

### Shared success / failure

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok` |
| `error` | string | failure | Human message; never tokens |
| `httpStatus` | number | HTTP error | Provider status |
| `rateLimitRemaining` | string | when header present | `RateLimit-Remaining` or `RateLimit-Observed` |

### `project_get` / `project_list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `project` | object | `project_get` | `id`, `path_with_namespace`, `web_url`, `default_branch`, `visibility`, `description` |
| `projects` | array | `project_list` | Same shape, page |
| `page` | number | `project_list` | Echo page |

### `file_get` / `file_create` / `file_update` / `file_delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `file` | file | `file_get` | Decoded blob stored as platform file ref (never raw base64 on the port) |
| `filePath` | string | success | Path |
| `branch` | string | mutate | Branch |
| `commitId` | string | mutate | New commit SHA (`file_create`/`update`/`delete`) |
| `contentSha256` | string | `file_get` | GitLab `content_sha256` |

### `issue_*`

| Field | Type | When | Purpose |
|---|---|---|---|
| `issue` | object | get/create/update | `id`, `iid`, `title`, `state`, `web_url`, `labels` |
| `note` | object | `issue_note` | `id`, `body` (truncated 4 KiB in logs only; full on port), `author.id` |

### `mr_*`

| Field | Type | When | Purpose |
|---|---|---|---|
| `mergeRequest` | object | success | `id`, `iid`, `title`, `state`, `web_url`, `source_branch`, `target_branch`, `merge_status` |
| `merged` | boolean | `mr_accept` | Whether merge succeeded |

### `pipeline_*` / `job_*`

| Field | Type | When | Purpose |
|---|---|---|---|
| `pipeline` | object | pipeline ops | `id`, `status`, `ref`, `sha`, `web_url` |
| `job` | object | job ops | `id`, `status`, `name`, `stage`, `web_url` |

### `release_create` / `branch_create` / `tag_create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `release` | object | release | `tag_name`, `name`, `_links.self` |
| `branch` | object | branch | `name`, `commit.id` |
| `tag` | object | tag | `name`, `commit.id`, `message` |

### `listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `hook` | object | `register` | `id`, `url` (no secret), `push_events`, … |
| `event` | string | inbound | `X-Gitlab-Event` (e.g. `Push Hook`) |
| `eventUuid` | string | inbound | `X-Gitlab-Event-UUID` / `Idempotency-Key` |
| `objectKind` | string | inbound | Payload `object_kind` |
| `projectId` | number | inbound | Payload project id |
| `payload` | object | inbound | Sanitized JSON (no tokens). Size-capped |

## Legal

- GitLab [Terms of Service](https://about.gitlab.com/terms/) and [Privacy Statement](https://about.gitlab.com/privacy/)
- API docs: https://docs.gitlab.com/api/rest/ — authentication: https://docs.gitlab.com/api/rest/authentication/
- Webhooks: https://docs.gitlab.com/user/project/integrations/webhooks/ and https://docs.gitlab.com/api/project_webhooks/
- Rate limits (GitLab.com, published; self-host may differ): authenticated REST typically **2,000 requests per minute** per user; unauthenticated **500/min**. Headers `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`. HTTP **429** → retry with jitter. Project webhook test/resend endpoints: **5 req/min** per user/project.
- PII: issues/MRs/files may contain personal data. Operator is the GitLab account holder. Minimize what workflows persist.
- Official-API-first: no HTML scrape, no undocumented private endpoints.

## Implementation notes

- JDK `java.net.http.HttpClient` only. No GitLab Java/JS SDK.
- Headers: `Accept: application/json`, `User-Agent: StudioQuack/1.0`. Auth: `PRIVATE-TOKEN: <token>` when `authType=pat`; `Authorization: Bearer <token>` when `oauth2`. Never put the token in the query string.
- Timeouts: `timeoutSeconds` (default 30) for connect and request. Stay under daemon stateful invoke budget (~300s).
- Size cap: **8 MiB** response body. `file_get` writes decoded content to the file store; if GitLab JSON (base64 `content`) would exceed cap, fail `{ error }` — do not stream unbounded into heap.
- Retry: HTTP 429 and 5xx with exponential backoff + jitter; honor `Retry-After` / `RateLimit-Reset`. **No retry** on other 4xx.
- TLS verify on. `insecureTls=true` is lab-only for self-signed GitLab.
- Success `{ status: "ok", … }`. HTTP 4xx/5xx (after retries) → `{ error }` on OUTPUT. Never throw past dispatch. Never echo `accessToken` / `webhookSecret`.
- Issues and MRs: always use **`iid`** in paths, not global `id`.
- `listen` register: `POST /api/v4/projects/{id}/hooks` with `url=ingressUrl`, event booleans, `enable_ssl_verification=true`, `signing_token` (`whsec_` + 32-byte key) **preferred** and/or `token`. Point `ingressUrl` at StudioQuack message-event ingress (self-hosted: backend `POST /be/v1/environments/{envId}/runtime/message-events`; Pro: daemon `/sqs/v1/ingress/message-events`). This is **not** a second catalog node.
- Inbound verification (required): prefer HMAC over raw body using the signing token; headers `webhook-signature` / `webhook-id` (format `v1={signature}`). Fallback only if the hook still uses secret token: compare `X-Gitlab-Token` with constant-time equality. Reject unsigned or mismatched requests (do not correlate). Then map `X-Gitlab-Event` + JSON body onto `output` as above and correlate the same payload as a BPMN message event.
- Logs: `gitlab {operation} http={status}` plus ids. Never log tokens, file bytes, or webhook secrets.
