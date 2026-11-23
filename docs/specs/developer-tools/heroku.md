# Heroku

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/heroku/`
- **Researched:** 2026-08-20
- **Official API:** Heroku Platform API **v3** (`Accept: application/vnd.heroku+json; version=3`)
- **Docs:** https://devcenter.heroku.com/articles/platform-api-reference
- **Auth:** api_key — secret binding: `apiKey`

## Scope

One node for the customer’s Heroku account via the **Platform API** at `https://api.heroku.com`. Apps, one-off dynos, dyno restart, config vars, releases, builds, and app webhooks (`listen`). JDK HTTP.

**Liveness (2026-08-20):** The Platform API v3 reference is live and is the current (only) supported version. Heroku is in a Salesforce **sustaining-engineering** posture (no new Enterprise contracts; dashboard credit-card plans continue). That is **not** a Platform API shutdown. Separately, **Heroku Status API v4** (`status.heroku.com/api/v4`) is being deprecated in favor of Salesforce Trust — this node must **not** call the Status API. Incident UI: https://status.salesforce.com/products/Heroku (dual-post with https://status.heroku.com during migration).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `app_get` | GET | `/apps/{app_id_or_name}` | Get one app |
| `app_list` | GET | `/apps` | List apps |
| `app_create` | POST | `/apps` | Create an app |
| `dyno_create` | POST | `/apps/{app_id_or_name}/dynos` | Run a one-off dyno |
| `dyno_restart` | DELETE | `/apps/{app_id_or_name}/dynos/{dyno_id_or_name}` | Restart one dyno; omit dyno name → `DELETE /apps/{id}/dynos` (all) |
| `config_get` | GET | `/apps/{app_id_or_name}/config-vars` | Read config vars |
| `config_set` | PATCH | `/apps/{app_id_or_name}/config-vars` | Set/unset config vars (`null` deletes) |
| `release_list` | GET | `/apps/{app_id_or_name}/releases` | List releases |
| `build_create` | POST | `/apps/{app_id_or_name}/builds` | Create a build from a source blob URL |
| `listen` | POST | `/apps/{app_id_or_name}/webhooks` | App webhook subscription |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `app_list` | no | Chooses the row above |
| `apiKey` | password | yes | — | **yes** | Platform API token. `Authorization: Bearer` |
| `timeoutSeconds` | number | no | `30` | no | HTTP timeout. Cap `120` |
| `maxResponseBytes` | number | no | `8388608` | no | Body cap 8 MiB |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Config var **values** are secrets in transit — never log |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | `config_get` returns keys; values are customer secrets — still return them to OUTPUT (operator asked) but **never log**. `listen` secret: do not put HMAC secret on OUTPUT |

FUNCTION ports: none.

## Input fields (by operation)

### Shared app-scoped ops

| Field | Type | Required | Purpose |
|---|---|---|---|
| `app` | string | yes (except `app_list` / `app_create`) | App id or name |

### `operation = app_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `range` | string | no | `Range` header for pagination (id lists) |

### `operation = app_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | no | App name; Heroku generates if omitted |
| `region` | string | no | Region name (`us`, `eu`, …) |
| `stack` | string | no | Stack name |
| `team` | string | no | Team name when creating in a team |

### `operation = dyno_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `command` | string | yes | Process command |
| `size` | string | no | Dyno size (e.g. `standard-1X`) |
| `type` | string | no | Process type, default `run` |
| `timeToLive` | number | no | Seconds, max 86400 |
| `env` | object | no | Extra env for this dyno only |

### `operation = dyno_restart`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dyno` | string | no | Dyno id or name. Blank → restart **all** dynos |

### `operation = config_set`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vars` | object | yes | Map of name → string value, or JSON `null` to unset |

### `operation = release_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `range` | string | no | Pagination `Range` header |

### `operation = build_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sourceBlobUrl` | string | yes | HTTPS URL of source tarball (`source_blob.url`) |
| `sourceBlobChecksum` | string | no | `source_blob.checksum` |
| `sourceBlobVersion` | string | no | `source_blob.version` |
| `buildpacks` | array | no | Optional buildpack URLs |

Do not upload tarball bytes through this node; pass a URL Heroku can GET.

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | HTTPS StudioQuack message-event ingress |
| `include` | array of string | yes | Entities, e.g. `api:release`, `api:build`, `api:dyno`, `api:app` |
| `level` | string | yes | `notify` or `sync` |
| `authorization` | string | no | Custom `Authorization` header Heroku will send to the callback (secret — never log) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `app` | object | app_get/create | App resource (id, name, web_url, region, stack) |
| `apps` | array | `app_list` | App summaries |
| `dyno` | object | `dyno_create` | Dyno id, name, state, attach_url |
| `restarted` | boolean | `dyno_restart` | `true` on 202 |
| `config` | object | `config_get` / `config_set` | Config map. Values are secrets — OUTPUT only, never logs |
| `releases` | array | `release_list` | Release summaries (version, status, description) |
| `build` | object | `build_create` | Build id, status, output_stream_url |
| `webhook` | object | `listen` | Webhook `id`, `include`, `level`, `url` — omit `secret` |
| `error` | string | failure | HTTP status + Heroku `id`/`message`. Never apiKey |

## Legal

- Terms: https://www.salesforce.com/company/legal/agreements/
- Platform API: https://devcenter.heroku.com/articles/platform-api-reference
- Compatibility: https://devcenter.heroku.com/articles/api-compatibility-policy — v3 is current; no v4 Platform API.
- Rate limit: `RateLimit-Remaining` (examples use 4500). HTTP 429 → jitter retry, max 3.
- Config vars and dyno env may contain credentials — treat as secrets in logs.
- Official Platform API only. No dashboard scrape. No Status API.

## Implementation notes

- JDK HTTP. Host `api.heroku.com`. TLS verify on.
- Headers: `Authorization: Bearer {apiKey}`, `Accept: application/vnd.heroku+json; version=3`, `Content-Type: application/json` on writes, `User-Agent: StudioQuack/1.0`.
- Pagination: id-range `Range` / `Next-Range` headers (not page numbers).
- `dyno_restart` all: `DELETE /apps/{app}/dynos` with empty body.
- `{ error }` on OUTPUT. Never throw past dispatch. Never log `apiKey`, webhook HMAC, or config values.
