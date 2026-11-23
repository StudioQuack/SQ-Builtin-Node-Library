# Cal.com

- **Status:** planned
- **Catalog:** `docs/catalog/productivity-collaboration.md`
- **Folder when built:** `nodes/cal-com/`
- **Researched:** 2026-08-20
- **Official API:** Cal.com Platform API v2 (`cal-api-version: 2024-08-13`)
- **Docs:** https://cal.com/docs/api-reference/v2/introduction
- **Auth:** api_key — secret binding: `api_key` (header `Authorization: Bearer`)

## Scope

One node for Cal.com **bookings**, **event types**, and **official webhooks**. The designer picks `operation`. Hosted traffic goes to `https://api.cal.com/v2`. Self-hosted instances set `api_base_url` to that instance’s API v2 root (see config).

Every request **must** send `cal-api-version: 2024-08-13`. Omitting it yields 404 / an older contract.

`listen` creates a webhook (`POST …/webhooks`) with official `triggers` such as `BOOKING_CREATED`, `BOOKING_CANCELLED`, `BOOKING_RESCHEDULED`, `BOOKING_COMPLETED` (meeting ended). Callback URL must be reachable HTTPS (StudioQuack message-event ingress).

**Open-source server vs hosted ToS:** self-hosted Cal.com is the AGPL-licensed application the operator runs; their instance’s terms are theirs. Hosted cal.com is a Cal.com, Inc. service under [cal.com/terms](https://cal.com/terms) and [cal.com/privacy](https://cal.com/privacy). The node does not embed the Cal.com server; it is an HTTP client. Platform (managed-user) OAuth is **deprecated for new signups** as of 15 December 2025 — this node uses **API keys** (`cal_` / `cal_live_`), not Platform client headers.

Do not scrape app.cal.com HTML. Do not call API v1 (`/v1/` + query `apiKey=`).

## Operations (`operation` config values)

Paths are relative to `api_base_url` (default `https://api.cal.com/v2`).

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `listen` | POST | `/webhooks` | Subscribe `subscriberUrl` to booking triggers |
| `list_bookings` | GET | `/bookings` | Filter/paginate bookings |
| `get_booking` | GET | `/bookings/{bookingUid}` | One booking by UID |
| `cancel_booking` | POST | `/bookings/{bookingUid}/cancel` | Cancel; optional reason |
| `reschedule_booking` | POST | `/bookings/{bookingUid}/reschedule` | New UTC `start` |
| `list_event_types` | GET | `/event-types` | Event types (often `username` query on hosted) |

**Dropped:** `POST /bookings` (create) — not in the catalog seed. Platform managed-user and `x-cal-client-id` / `x-cal-secret-key` flows. v1 webhooks (`eventTriggers`).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `list_bookings` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | `cal_` or `cal_live_` key from Settings → Security. Header `Authorization: Bearer`. Never on INPUT. |
| `api_base_url` | string | no | `https://api.cal.com/v2` | no | **Self-host:** API v2 root with no trailing slash. Hosted: leave default. Self-host examples from Cal.com config: `{WEBAPP}/api/v2` or the API v2 service (`NEXT_PUBLIC_API_V2_URL`, often port **5555**, e.g. `https://cal.example.com/api/v2`). Handler must not append a second `/v2`. |
| `cal_api_version` | string | no | `2024-08-13` | no | `cal-api-version` header. Do not change unless Cal.com publishes a new required date. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |

Reject `api_base_url` that is not `https:` (or `http:` **only** when the operator’s egress policy allows a private self-host). SSRF: no loopback/link-local/metadata IPs unless the daemon egress policy already permits that host.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none. No `file` ports.

## Input fields (by operation)

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscriber_url` | string | yes | Public HTTPS callback (ingress). Alias `callback_url`. |
| `triggers` | array | no | Official enums, default `BOOKING_CREATED`, `BOOKING_CANCELLED`, `BOOKING_RESCHEDULED`, `BOOKING_COMPLETED`. Optional: `BOOKING_CONFIRMED`, `BOOKING_REJECTED`, `BOOKING_NO_SHOW`, `BOOKING_REOPENED`, `BOOKING_PAID`. |
| `active` | boolean | no | Default `true` |
| `secret` | password | no | Webhook signing secret if the operator supplies one (else Cal.com may return one — treat as secret, never log) |
| `payload_template` | string | no | Optional Cal.com `payloadTemplate` |
| `version` | string | no | Payload version `2021-10-20` (default) or `2026-07-27` (adds ICS fields on some events) |

### `operation = list_bookings`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `status` | string | no | e.g. `accepted`, `pending`, `cancelled`, `rejected` as documented |
| `attendee_email` | string | no | Filter |
| `event_type_id` | number | no | Filter |
| `after_start` | string | no | ISO-8601 |
| `before_end` | string | no | ISO-8601 |
| `take` | number | no | Page size; clamp to Cal.com max (typically ≤ 100) |
| `skip` | number | no | Offset |
| `sort_start` | string | no | Sort key if documented |

### `operation = get_booking`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `booking_uid` | string | yes | Booking UID |

### `operation = cancel_booking`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `booking_uid` | string | yes | Booking UID |
| `cancellation_reason` | string | no | Reason text |
| `seat_uid` | string | no | Seated booking: which seat to cancel |

### `operation = reschedule_booking`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `booking_uid` | string | yes | Booking UID |
| `start` | string | yes | New start UTC ISO-8601 |
| `rescheduling_reason` | string | no | Reason |

### `operation = list_event_types`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `username` | string | no | Hosted username filter (common on GET `/event-types`) |
| `event_type_slug` | string | no | If documented for the instance |

## Output fields (by operation)

Cal.com v2 wraps payloads as `{ status, data }`. Flatten for the workflow:

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object or array | success | Provider `data` (booking, list, webhook, event types) |
| `booking_uid` | string | get / cancel / reschedule | UID when present |
| `webhook_id` | string | `listen` | Created webhook id |
| `error` | string | failure | Actionable, key redacted |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429 (or `Retry-After`); 30 on 5xx/timeout |

## Legal

- Introduction: https://cal.com/docs/api-reference/v2/introduction
- Bookings: https://cal.com/docs/api-reference/v2/bookings (list/get/cancel/reschedule)
- v1→v2: https://cal.com/docs/api-reference/v2/v1-v2-differences
- Webhooks: https://cal.com/docs/api-reference/v2/event-types-webhooks/create-a-webhook and `POST /v2/webhooks`
- Hosted ToS: https://cal.com/terms — Privacy: https://cal.com/privacy
- Self-host license: [calcom/cal.com](https://github.com/calcom/cal.com) **AGPL-3.0**. Operators who run the server accept AGPL; this node only calls HTTP.
- **Quota (hosted):** API key **120 requests/minute** (increase on request; higher rates may be billed). Default unauthenticated 120/min is irrelevant — this node always authenticates.
- Bookings contain attendee name, email, timezone, notes — personal data. Minimize fields forwarded downstream. Never log attendee emails or meeting URLs.
- Official-API-first: no scrape.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `Authorization: Bearer {api_key}`, `cal-api-version: {cal_api_version}`, `Content-Type: application/json` on POST, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Empty `api_key` → `{ error: "Missing Cal.com API key. Bind api_key in node parameters." }` — no HTTP.
- Normalize `api_base_url`: trim trailing slash. Paths in the table already include `/bookings` not `/v2/bookings` when the base ends with `/v2` or `/api/v2`.
- Response body cap **1_000_000** bytes. Timeout default 25 s (floor 1).
- `list_*`: one page per invoke (`take`/`skip`). Do not auto-walk.
- 429 → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry in the handler.
- Logs: operation, HTTP status, list length, booking UID — never `api_key`, attendee PII, or webhook `secret`.
