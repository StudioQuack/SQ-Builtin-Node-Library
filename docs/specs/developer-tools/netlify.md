# Netlify

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/netlify/`
- **Researched:** 2026-08-20
- **Official API:** Netlify REST API v1 (`https://api.netlify.com/api/v1`)
- **Docs:** https://docs.netlify.com/api-and-cli-guides/api-guides/get-started-with-api/ — OpenAPI: https://open-api.netlify.com/
- **Auth:** api_key (personal access token or OAuth access token) — secret binding: `token`

## Scope

One node for the customer’s Netlify account: create/get/list/cancel deploys, get/list/delete sites (UI “Project ID” = API `site_id`), and register outgoing hooks (`listen`). Deploy file payloads use type `file` (digest + `PUT /deploys/{id}/files/...` or ZIP). SSL only. JDK HTTP.

Netlify UI renamed “site” → “project”; the API path remains `/sites/{site_id}`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `deploy_create` | POST | `/sites/{site_id}/deploys` | Create a deploy (file digest or ZIP `file`) |
| `deploy_get` | GET | `/deploys/{deploy_id}` | Get a deploy |
| `deploy_list` | GET | `/sites/{site_id}/deploys` | List deploys for a site |
| `deploy_cancel` | POST | `/deploys/{deploy_id}/cancel` | Cancel a deploy (`postcancelSiteDeploy`) |
| `site_get` | GET | `/sites/{site_id}` | Get site / project |
| `site_list` | GET | `/sites` | List sites |
| `site_delete` | DELETE | `/sites/{site_id}` | Delete a site |
| `listen` | POST | `/hooks` | Create an outgoing webhook / notification hook |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `site_list` | no | Chooses the row above |
| `token` | password | yes | — | **yes** | PAT or OAuth token. `Authorization: Bearer` |
| `timeoutSeconds` | number | no | `30` | no | HTTP timeout. File deploy default `120` if unset |
| `maxResponseBytes` | number | no | `8388608` | no | JSON cap 8 MiB |
| `maxFileBytes` | number | no | `33554432` | no | Per-file / ZIP cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Deploy files type `file` |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never include token |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = deploy_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `siteId` | string | yes | Site / project id (or `mysite.netlify.app`) |
| `files` | array of file | one-of | Files for digest deploy. Type `file`. SHA-1 of bytes → POST body `files` map then PUT missing hashes |
| `zip` | file | one-of | Entire site ZIP (`Content-Type: application/zip`). 25,000 file extraction limit |
| `async` | boolean | no | Digest deploy: set `async: true` for large manifests |
| `draft` | boolean | no | Draft deploy (not published) |
| `title` | string | no | Deploy title |
| `branch` | string | no | Branch name when applicable |

Exactly one of `files` or `zip`. Functions digest is out of this catalog row (do not add a functions SHA map unless a later catalog expansion).

### `operation = deploy_get` / `deploy_cancel`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `deployId` | string | yes | Deploy id |

### `operation = deploy_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `siteId` | string | yes | Site id |
| `page` | number | no | 1-based |
| `perPage` | number | no | Max 100 |

### `operation = site_get` / `site_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `siteId` | string | yes | Site id or default domain |

### `operation = site_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | 1-based |
| `perPage` | number | no | Max 100 |
| `filter` | string | no | Optional `all` per Netlify list filters if documented on the request |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `siteId` | string | yes | Site the hook belongs to |
| `type` | string | yes | Hook type from `GET /hooks/types` (e.g. deploy succeeded / failed). Pass the official `type` string |
| `url` | string | yes | HTTPS StudioQuack message-event ingress |
| `event` | string | no | Event name when the type requires it |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `deploy` | object | deploy create/get/cancel | `id`, `state`, `ssl_url`, `deploy_ssl_url`, `error_message` — no tokens |
| `required` | array | digest `deploy_create` | SHA-1 list still needing PUT (empty when complete) |
| `deploys` | array | `deploy_list` | Page of deploys |
| `site` | object | `site_get` | Site metadata |
| `sites` | array | `site_list` | Page of sites |
| `deleted` | boolean | `site_delete` | `true` on 204/200 |
| `hook` | object | `listen` | Hook `id`, `type`, `site_id` — no secrets |
| `error` | string | failure | HTTP status + body message. Never token |

## Legal

- Terms: https://www.netlify.com/legal/terms/
- API get-started: https://docs.netlify.com/api-and-cli-guides/api-guides/get-started-with-api/
- Rate limits (published): 500 requests/minute typical; deploys **3/minute** and **100/day**. Headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
- Password reset invalidates PATs.
- SAML teams: token must be created with team access granted.
- Official REST only. User-Agent should identify the integration (`StudioQuack/1.0 (ops@…)`).

## Implementation notes

- JDK HTTP. `Authorization: Bearer {token}`, HTTPS only, TLS verify on. Host: `api.netlify.com`.
- Digest deploy: compute SHA-1 per file, POST manifest, PUT each `required` hash to `/deploys/{id}/files/{path}` with `Content-Type: application/octet-stream`. Escape paths; reject `#` and `?` in paths.
- ZIP deploy: POST with zip body per get-started docs. Cap files extracted is Netlify-side (25k).
- API requests > 30s are terminated by Netlify — use `async: true` for large deploys and poll `deploy_get`.
- Retry 429 with jitter, max 3. Honor deploy-specific stricter limits (do not retry deploy_create in a tight loop).
- `{ error }` on OUTPUT. Never throw past dispatch. Never log token or file bytes.
