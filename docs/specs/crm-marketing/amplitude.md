# Amplitude

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/amplitude/`
- **Researched:** 2026-08-20
- **Official API:** Amplitude **HTTP V2** (`POST /2/httpapi`) plus Identify, Group Identify, Batch Event Upload, **Export API v2** (`GET /api/2/export`), and **Taxonomy API** (`/api/2/taxonomy/...`). Hosts differ by product: ingestion `api2.amplitude.com` / `api.eu.amplitude.com`; export/taxonomy `amplitude.com` / `analytics.eu.amplitude.com`.
- **Docs:** HTTP V2 https://amplitude.com/docs/apis/analytics/http-v2 · Quickstart https://amplitude.com/docs/apis/analytics/http-api-quickstart · Identify https://amplitude.com/docs/apis/analytics/identify · Export https://amplitude.com/docs/apis/analytics/export · Taxonomy https://amplitude.com/docs/apis/analytics/taxonomy · Auth https://amplitude.com/docs/apis/authentication
- **Auth:** project `api_key` (ingest query). Export/taxonomy use precomputed config `basic_token` = Base64(`api_key:secret_key`). Engine does not collect `secret_key`.

## Scope

One node for Amplitude Analytics ingestion, identity, export, and taxonomy. `serverZone=eu` switches hosts. HTTP V2 is for near-real-time, low-volume batches; `event.batch` uses the Batch Event Upload API for higher volume. Export returns a **zip of JSON files** as platform type `file`. Do not send events to `analytics.amplitude.com` (that hostname is the web app).

## Operations (`operation` config values)

| operation | HTTP | Path / host | When to use |
|---|---|---|---|
| `event.track` | POST | `{ingest}/2/httpapi` | Real-time events (JSON `{ api_key, events }`) |
| `event.batch` | POST | `{ingest}/batch` | Batch Event Upload (higher volume; not HTTP V2) |
| `identify` | POST | `{ingest}/identify` | User properties without an event (`x-www-form-urlencoded`) |
| `group.identify` | POST | `{ingest}/groupidentify` | Group properties (`x-www-form-urlencoded`) |
| `export.download` | GET | `{analytics}/api/2/export` | Hourly zip export (`start`/`end` `YYYYMMDDTHH`) |
| `taxonomy.list` | GET | `{analytics}/api/2/taxonomy/event` (or `/event-property`, `/user-property`, `/category`, `/group-property`) | List taxonomy resources |

Ingest host: US `https://api2.amplitude.com`, EU `https://api.eu.amplitude.com`. Analytics host: US `https://amplitude.com`, EU `https://analytics.eu.amplitude.com`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `event.track` | no | Chooses the row above |
| `apiKey` | password | yes | | **yes** | Project API key (HTTP V2 body `api_key`, Identify form `api_key`, Basic user for export/taxonomy) |
| `basic_token` | password | export + taxonomy | | **yes** | Precomputed Base64(`api_key:secret_key`) for Export/Taxonomy Basic |
| `server_zone` | select | no | `us` | no | `us` or `eu` (not wired — US/EU hosts do not share a URL template) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, resource }` or `{ file }` or `{ error }` | Export uses `file`. Never echo keys |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = event.track | event.batch`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `events` | object[] | yes | Amplitude event objects. Each needs `event_type` and `user_id` and/or `device_id` |
| `userId` / `deviceId` / `eventType` | string | if `events` omitted | Convenience: build a one-event array |
| `eventProperties` / `userProperties` | object | no | Nested on the event |
| `time` | number | no | Client timestamp (ms) |
| `insertId` | string | no | Dedup id |

HTTP V2 official limits: keep payload **under 1 MB**, **fewer than 2000 events per request**, Amplitude recommends **≤ 10 events per batch** for HTTP V2; **100 batches/s** and **1000 events/s** upload guidance; project ceiling **50,000 events/s** for HTTP API/V2. `413` if oversized.

### `operation = identify`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `userId` | string | yes unless `deviceId` | `user_id` |
| `deviceId` | string | yes unless `userId` | `device_id` |
| `userProperties` | object | no | Including `$set` / `$unset` / `$append` operators |
| `groups` | object | no | Group association |

Body: `application/x-www-form-urlencoded` with `api_key` + `identification` JSON array. A single identification object is sent as a one-element array. Do **not** use GET (URL length → 414).

### `operation = group.identify`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `groupType` | string | yes | Group type |
| `groupValue` | string | yes | Group value |
| `groupProperties` | object | no | Properties to set |

### `operation = export.download`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `start` | string | yes | First hour `YYYYMMDDTHH` (UTC upload time) |
| `end` | string | yes | Last hour `YYYYMMDDTHH`; max span **365 days**; whole day `T00`–`T23` |

Data available ~2 hours after ingest. Size cap **4 GB** → HTTP 400; then shorten the window or use Amplitude’s S3 export (out of scope for this node). HTTP 404 = no data in range.

### `operation = taxonomy.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `resource` | select | no | Maps to path: `event` (default), `event-property`, `user-property`, `category`, `group-property` |
| `eventType` | string | some GETs | URL-encoded name when fetching one type |

Taxonomy costs: GET = 1 cost; concurrent cost 4; 7200 cost/hour per API key.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status (`200` track; export `200`) |
| `code` / `payloadSize` / `eventsIngested` | mixed | track/batch | Amplitude JSON success fields when present |
| `file` | file | `export.download` | Zip archive ref-id (not inline bytes) |
| `resource` | object | identify/taxonomy | Provider JSON |
| `error` | object | 4xx/5xx | `{ code, message, status }` — no api_key, no user_id values |

## Legal

- Amplitude terms and DPA apply to the customer’s project. EU projects **must** use EU hosts (residency).
- HTTP V2 throttles hot `user_id`/`device_id` (~30 eps averaged); user-property updates ~1800/hour/Amplitude id. `429`: wait (docs mention ~60s on some ingest limits) then retry.
- Analytics identifiers and event properties are PII or quasi-identifiers. **(a)** Minimize properties. **(b)** Customer owns the Amplitude project and is the controller. **(c)** StudioQuack does not accept Amplitude ToS on their behalf. **(d)** Never log `apiKey`, `secretKey`, `user_id`, or property values.
- Official-API-first: documented Amplitude API hosts only. No UI scrape.

## Implementation notes

- JDK `HttpClient` only. No Amplitude SDK.
- HTTP V2: JSON POST, `Content-Type: application/json`, `api_key` in **body** (not a header). Identify/group: form-urlencoded. Export/taxonomy: `Authorization: Basic base64(apiKey:secretKey)`.
- Timeouts: ingest 15s; export read **120s**. Ingest body cap 1 MiB. Export: stream to a `file` ref; do not buffer 4 GB in heap — if Content-Length exceeds a **32 MiB** handler cap, `{ error }` instructing a shorter window.
- Retry: 429/5xx, max 3. No retry on 400/413 (fix payload). 404 on export is `{ error: no data }`, not a secret leak.
- Errors: 4xx/5xx → `{ error }` on OUTPUT. Never throw past dispatch.
- `serverZone=eu` must apply to **both** ingest and export hosts; mixing US ingest with EU export is a data-residency bug.
