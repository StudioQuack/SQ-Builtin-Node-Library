# Onfleet

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/onfleet/`
- **Researched:** 2026-08-20
- **Official API:** Onfleet REST **v2** (`https://onfleet.com/api/v2`)
- **Docs:** https://docs.onfleet.com/reference/introduction · auth: https://docs.onfleet.com/reference/authentication · tasks: https://docs.onfleet.com/reference/create-task · destinations: https://docs.onfleet.com/reference/create-destination · webhooks: https://docs.onfleet.com/reference/webhooks · throttle: https://docs.onfleet.com/reference/throttling
- **Auth:** HTTP Basic — API key as **username**, **empty password** — secret binding: `api_key`

## Scope

One node for Onfleet last-mile tasks, destinations, and workers. The customer owns the Onfleet organization. **Driver and recipient PII** (name, phone, address notes) must not appear in daemon logs.

Webhooks are `trigger` on **this same node**. Creating a webhook: Onfleet **GET-handshake** — respond with the `check` query value.

Do not use official language SDKs; JDK HTTP only.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `task_create` | POST | `/tasks` | Create a task (inline destination/recipient objects allowed) |
| `task_get` | GET | `/tasks/{taskId}` | Fetch one task (shortId variant: documented `GET /tasks/shortId/{shortId}`) |
| `task_update` | PUT | `/tasks/{taskId}` | Update a task |
| `destination_create` | POST | `/destinations` | Create a geocoded destination for reuse |
| `worker_list` | GET | `/workers` | List workers/drivers (`states`, `teams`, `phones`, `filter`) |
| `trigger` | inbound POST | Onfleet webhook | Trigger ids 0–30 (taskStarted, taskCompleted, workerDuty, …) |

Catalog “CRUD-ish”: no separate `task_delete` in starting ops (DELETE `/tasks/{id}` exists — omit until catalog adds it).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `task_create` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Organization API key (Basic username, blank password) |
| `webhook_secret` | password | no | empty | yes | Dashboard webhook HMAC secret (docs: HMAC-SHA512) for `trigger` |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s. List queries can 502 at 70 s Onfleet timeout — keep windows narrow |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Recipient/driver PII in payloads |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = task_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `destination` | object or string | yes | Destination object (`address.unparsed` or structured) **or** existing destination id |
| `recipients` | array | no | 0–1 recipient objects (`name`, `phone`, `notes`) — PII |
| `notes` | string | no | Task notes (max 10_000 chars) |
| `complete_after` | number | no | Unix **ms** |
| `complete_before` | number | no | Unix **ms** |
| `pickup_task` | boolean | no | Pickup vs dropoff |
| `auto_assign` | object | no | `{ mode: distance \| … }` |
| `container` | object | no | Assign container (mutually exclusive with auto_assign) |

### `operation = task_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `task_id` | string | conditional | Onfleet task id |
| `short_id` | string | conditional | If set, GET by shortId |

### `operation = task_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `task_id` | string | yes | Path id |
| `task` | object | yes | Fields to update (same shapes as create, as documented) |

### `operation = destination_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `address` | object | yes | `unparsed` or structured address |
| `notes` | string | no | Access notes — PII |

### `operation = worker_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `states` | string | no | Comma-separated `0,1,2` (off-duty / idle / active) |
| `teams` | string | no | Comma-separated team ids |
| `phones` | string | no | Filter — PII |
| `filter` | string | no | Sparse field list |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `method` | string | yes | `GET` = handshake (`check` query → body echo); `POST` = event |
| `check` | string | no | Handshake token |
| `headers` | object | no | POST signature headers |
| `raw_body` | string | no | POST JSON |
| `trigger_id` | number | no | Filter Onfleet trigger id |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `task` | object | task ops | Task id, shortId, state, trackingURL — omit recipient phone from logs |
| `destination` | object | `destination_create` | Destination id + geocode |
| `workers` | array | `worker_list` | Worker ids + state — omit phones from logs |
| `handshake` | string | trigger GET | Echo of `check` (this is the HTTP response body Onfleet requires) |
| `event` | object | trigger POST | `triggerId`, `triggerName`, `taskId`, `workerId` |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `X-RateLimit-*`; 429 → 60 |

## Legal

- Onfleet API terms / organization contract. Driver and recipient data are PII (phones, names, GPS).
- **20 requests/second** across all org API keys. 429 then possible temporary ban. Prefer webhooks over polling.
- Official-API-first: no scrape of dashboard.onfleet.com.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. `Content-Type: application/json`.
- Auth: `Authorization: Basic base64(api_key + ":")`. Empty key → `{ error }` — no HTTP.
- Times: milliseconds Unix except `etaSeconds`.
- `trigger` GET handshake: return **plain** `check` value with 200 (ingress must support this). POST: verify HMAC-SHA512 per https://docs.onfleet.com/reference/secrets if `webhook_secret` set.
- Body cap **1_000_000** bytes. Timeout default 30 s.
- Logs: operation, task/worker ids, HTTP status — never API key, phones, addresses, or notes.
