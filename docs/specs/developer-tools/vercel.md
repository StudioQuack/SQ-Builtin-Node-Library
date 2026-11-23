# Vercel

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/vercel/`
- **Researched:** 2026-08-20
- **Official API:** Vercel REST API (versioned per path; deployments v13/v12/v7 as of 2026-08-20)
- **Docs:** https://vercel.com/docs/rest-api
- **Auth:** api_key — secret binding: `token`

## Scope

One node for the customer’s Vercel account (personal or team). Create/list/get/cancel/delete deployments, get/create projects, set project env vars, get a domain, and register REST webhooks (`listen`). Team-scoped calls append `teamId` (config or input). JDK HTTP. File uploads for `deploy_create` use type `file` (Vercel `POST /v2/files` then `POST /v13/deployments`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `deploy_create` | POST | `/v13/deployments` | Create a deployment (optional prior `POST /v2/files`) |
| `deploy_get` | GET | `/v13/deployments/{idOrUrl}` | Get deployment by id or URL |
| `deploy_list` | GET | `/v7/deployments` | List deployments |
| `deploy_cancel` | PATCH | `/v12/deployments/{id}/cancel` | Cancel an in-flight deployment |
| `deploy_delete` | DELETE | `/v13/deployments/{id}` | Delete a deployment |
| `project_get` | GET | `/v9/projects/{idOrName}` | Get a project |
| `project_create` | POST | `/v11/projects` | Create a project |
| `env_set` | POST | `/v10/projects/{idOrName}/env` | Create project environment variable(s) |
| `domain_get` | GET | `/v5/domains/{domain}` | Apex/domain metadata |
| `listen` | POST | `/v1/webhooks` | Create a webhook toward StudioQuack ingress |

Base: `https://api.vercel.com`. Append `?teamId=` when `teamId` is set.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `deploy_list` | no | Chooses the row above |
| `token` | password | yes | — | **yes** | Vercel access token (`Authorization: Bearer`) |
| `teamId` | string | no | — | no | Team id for team-owned resources. Input may override |
| `timeoutSeconds` | number | no | `30` | no | HTTP timeout. `deploy_create` with files: default `120` if unset |
| `maxResponseBytes` | number | no | `8388608` | no | JSON cap 8 MiB |
| `maxFileBytes` | number | no | `33554432` | no | Per-file upload cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Files are type `file` |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never include `token` or webhook `secret` after first persist-to-secret-store handoff — return `webhookId` only plus `secretPresent: true` |

FUNCTION ports: none.

## Input fields (by operation)

Shared: `teamId` overrides config.

### `operation = deploy_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Deployment / project name |
| `project` | string | no | Existing project id or name |
| `files` | array of file | no | Files to upload via `POST /v2/files` (SHA digest flow). Type `file` |
| `gitSource` | object | no | `{ type, repo, ref, sha }` when deploying from connected git |
| `target` | string | no | `production` \| `staging` |
| `meta` | object | no | String map of metadata |

If `files` is empty and `gitSource` is empty, fail before HTTP: `files or gitSource is required for deploy_create.`

### `operation = deploy_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `idOrUrl` | string | yes | Deployment id (`dpl_…`) or hostname |

### `operation = deploy_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectId` | string | no | Filter |
| `limit` | number | no | Page size; handler cap 100 |
| `state` | string | no | `BUILDING` \| `ERROR` \| `INITIALIZING` \| `QUEUED` \| `READY` \| `CANCELED` \| `BLOCKED` |
| `since` | number | no | Created after (ms epoch) |
| `until` | number | no | Created before (ms epoch) |
| `target` | string | no | Environment filter |

### `operation = deploy_cancel` / `deploy_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Deployment id |

### `operation = project_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `idOrName` | string | yes | Project id or name |

### `operation = project_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Project name |
| `framework` | string | no | Framework slug |
| `gitRepository` | object | no | Connected repo `{ type, repo }` |

### `operation = env_set`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `idOrName` | string | yes | Project |
| `key` | string | yes | Env var name |
| `value` | string | yes | Env var value — never log |
| `target` | array of string | no | `production`, `preview`, `development` |
| `type` | string | no | `plain` \| `encrypted` \| `sensitive` |

### `operation = domain_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `domain` | string | yes | Apex domain name |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | HTTPS StudioQuack message-event ingress |
| `events` | array of string | yes | Official event names, e.g. `deployment.ready`, `deployment.error`, `project.created` |
| `projectIds` | array of string | no | Limit to projects |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `deployment` | object | deploy_* (except list) | Id, url, readyState, createdAt — no tokens |
| `deployments` | array | `deploy_list` | Page of summaries |
| `pagination` | object | `deploy_list` | Vercel pagination cursor if present |
| `project` | object | `project_get`, `project_create` | Project metadata |
| `env` | object | `env_set` | Created env var metadata **without** `value` |
| `domain` | object | `domain_get` | Domain record |
| `webhookId` | string | `listen` | Webhook id |
| `webhookSecretSet` | boolean | `listen` | `true` if API returned `secret` — handler stores via daemon secret path if available, **never** copies secret onto OUTPUT |
| `error` | string | failure | HTTP status + Vercel `error.message`. Never token |

## Legal

- Terms: https://vercel.com/legal/terms
- REST: https://vercel.com/docs/rest-api
- OpenAPI: https://openapi.vercel.sh/
- Tokens are customer-owned. Team resources require `teamId` or requests 403.
- Rate limits: honor `X-RateLimit-*` / 429. Retry jitter, max 3.
- Official REST only. Deploy hooks (bare POST URLs) are out of catalog — use REST `deploy_create` / `listen`.

## Implementation notes

- JDK HTTP. `Authorization: Bearer {token}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- TLS verify on. Host: `api.vercel.com` only.
- `deploy_create` file flow: for each `file` ref, SHA-1 digest as Vercel expects, `POST /v2/files`, then create deployment referencing uploaded hashes. Cap total files at 100.
- Timeouts + size caps. 429 retry with full jitter.
- `{ error }` on OUTPUT. Never throw past dispatch. Never log token, env values, or webhook secret.
