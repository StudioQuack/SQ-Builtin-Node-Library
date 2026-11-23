# ngrok

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/ngrok/`
- **Researched:** 2026-08-20
- **Official API:** ngrok REST API, `ngrok-version: 2` (all other versions deprecated)
- **Docs:** https://ngrok.com/docs/api/
- **Auth:** api_key — secret binding: `apiKey`

> **Dropped verbs (retired as of 2026-08-20):** `tunnel_list` / `tunnel_get` — `GET /tunnels` is deprecated in favor of Endpoints (`https://ngrok.com/docs/api-reference/tunnels/list`). `edge_get` — Edges (HTTPS/TLS/TCP), Routes, Modules, Backends, and Labeled Tunnels sunset 2025-12-31 (`https://ngrok.com/docs/gateway/edges`). Replacements: `endpoint_list`, `endpoint_get`.

> **Unverified / limited:** ngrok Event Destinations have **no generic HTTPS webhook URL** in `EventTarget` (targets are Kinesis, Firehose, CloudWatch Logs, Datadog, Azure Logs Ingestion — `https://ngrok.com/docs/api-reference/eventdestinations/list`). `listen` creates an Event Subscription against an existing destination id. It cannot point ngrok directly at StudioQuack message-event ingress.

## Scope

One node for the customer’s ngrok account. It lists and reads **Endpoints** (cloud, ephemeral/agent, and leftover edge-typed records) and **reserved domains**, and registers **event subscriptions**. Tunnel public URLs are customer ingress; this node never opens a local agent. Base URL defaults to `https://api.ngrok.com` (TLS 443 only). Do not call the Agent API (`localhost:4040`). JDK HTTP only.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `endpoint_list` | GET | `/endpoints` | List active endpoints (`before_id`, `limit`, optional CEL `filter`) |
| `endpoint_get` | GET | `/endpoints/{id}` | One endpoint by id |
| `reserved_domain_list` | GET | `/reserved_domains` | List reserved domains on the account |
| `listen` | POST | `/event_subscriptions` | Register an Event Subscription (`destination_ids` + `sources`) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `endpoint_list` | no | Chooses the row above |
| `apiKey` | password | yes | — | **yes** | Bearer token from https://dashboard.ngrok.com/api-keys |
| `apiUrl` | string | no | `https://api.ngrok.com` | no | API origin. Must be `https`. TLS verify **on**. No agent loopback. |
| `timeoutSeconds` | number | no | `30` | no | Per-request timeout. Cap at `120`. |
| `maxResponseBytes` | number | no | `8388608` | no | Response body cap (8 MiB). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Never put `apiKey` here |
| `output` | OUTPUT | object — `{ status: "ok", … }` or `{ error }` | Failures never include the API key |

FUNCTION ports: none.

## Input fields (by operation)

Shared: unknown keys ignored.

### `operation = endpoint_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Page size, max 100 (API default 100) |
| `before_id` | string | no | Cursor: resources older than this id |
| `filter` | string | no | CEL filter (`https://ngrok.com/docs/api/api-filtering`) |

### `operation = endpoint_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Endpoint resource id |

### `operation = reserved_domain_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Page size, max 100 |
| `before_id` | string | no | Cursor |
| `filter` | string | no | CEL filter |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `destinationIds` | array of string | yes | Existing Event Destination ids (not a StudioQuack URL) |
| `sources` | array of string | yes | Event type strings, e.g. `ip_policy_created.v0` — sent as `{ "type": "…" }` |
| `description` | string | no | Max 255 chars |
| `metadata` | string | no | Opaque string, max 4096 bytes |

## Output fields (by operation)

Shared success: `status` = `ok`. Shared failure: `error` string only.

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok` |
| `endpoints` | array | `endpoint_list` | Page of endpoint objects: `id`, `url`, `type`, `proto`, `region`, `created_at`, `bindings` |
| `next_page_uri` | string or null | list ops | API pagination URI |
| `endpoint` | object | `endpoint_get` | Full endpoint resource (omit `public_url` if `url` is present — `public_url` is deprecated) |
| `reserved_domains` | array | `reserved_domain_list` | `id`, `domain`, `region`, `cname_target`, `created_at` |
| `subscription` | object | `listen` | Created subscription `id`, `sources`, `destinations` |
| `error` | string | failure | HTTP status + provider `error_code`/`msg`. Never the API key |

## Legal

- Terms: https://ngrok.com/tos
- Privacy: https://ngrok.com/privacy
- Rate limit (published): 120 requests per rolling 60 seconds; HTTP 429 + `ERR_NGROK_226`. Retry with jitter; do not exceed.
- IP Restrictions (`type=api`) may block the daemon egress IP — operator must allowlist.
- Official REST only. No dashboard scrape. No Agent API.
- Tunnel URLs and reserved domains are **customer ingress**. StudioQuack does not own that traffic.

## Implementation notes

- JDK `HttpClient` only. Headers on every call: `Authorization: Bearer {apiKey}`, `ngrok-version: 2`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`. POST bodies `Content-Type: application/json`.
- TLS verify on. Reject non-https `apiUrl`.
- Timeouts: `timeoutSeconds`. Size cap: `maxResponseBytes`; truncate is an error, not a partial success.
- Retry: HTTP 429 only, exponential backoff + full jitter, max 3 retries. No retry on 4xx except 429.
- Map 4xx/5xx → `{ error }` on OUTPUT. Never throw past dispatch. Never log `apiKey`.
- Do not implement `/tunnels` or `/edges/*`.
