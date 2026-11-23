# Segment

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/segment/`
- **Researched:** 2026-08-20
- **Official API:** Twilio Segment HTTP Tracking API (`https://api.segment.io/v1`). Docs last modified 2026-02-24 on Twilio.
- **Docs:** https://www.twilio.com/docs/segment/connections/sources/catalog/libraries/server/http-api · Spec: https://segment.com/docs/connections/spec/
- **Auth:** write key — secret binding: `write_key`

## Scope

One node for **server-side** Segment Tracking API calls: `identify`, `track`, `page`, `screen`, `group`, `alias`. Optional `batch`. This is the HTTP Source, not the Public API (`api.segmentapis.com`) and not Engage audience APIs. **No `trigger.listen`:** the Tracking API does not push events to callers. Outbound “webhook destinations” are a different Segment product surface and are out of scope for this node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `identify` | POST | `{data_plane}/v1/identify` | Tie `userId`/`anonymousId` to traits |
| `track` | POST | `{data_plane}/v1/track` | Record an event |
| `page` | POST | `{data_plane}/v1/page` | Web page view |
| `screen` | POST | `{data_plane}/v1/screen` | Mobile screen view |
| `group` | POST | `{data_plane}/v1/group` | Associate user with a group |
| `alias` | POST | `{data_plane}/v1/alias` | Merge identities |

`batch` is not a catalog verb: if input `batch` is a non-empty array, POST `{data_plane}/v1/batch` (max **500 KB** request, **32 KB** per event).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `track` | no | |
| `write_key` | password | yes | empty | yes | Source write key. Prefer HTTP Basic `write_key:` (empty password) **or** JSON `writeKey`. Never on INPUT |
| `data_plane_url` | string | no | `https://api.segment.io` | no | Tracking API origin used as `{data_plane_url}/v1`. Oregon default. EU Business regional: `https://events.eu1.segmentapis.com` (official). No trailing slash |
| `timeout_seconds` | number | no | `15` | no | |

Do not hardcode resolved IPs (Segment IPs change; refresh DNS).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo write key |

FUNCTION ports: none.

## Input fields (by operation)

Shared optional fields on every call (official spec): `anonymousId` or `userId` (at least one), `timestamp` (ISO 8601), `context` (object), `integrations` (object), `messageId` (string, idempotency).

### `operation = identify`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `userId` | string | one of userId/anonymousId | Known user |
| `anonymousId` | string | one of | Anonymous identity |
| `traits` | object | no | Reserved: `email`, `name`, … — PII |

### `operation = track`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `userId` / `anonymousId` | string | one of | |
| `event` | string | yes | Event name |
| `properties` | object | no | Event props |

### `operation = page`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `userId` / `anonymousId` | string | one of | |
| `name` | string | no | Page name |
| `properties` | object | no | Often `path`, `url`, `title`, `referrer` |

### `operation = screen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `userId` / `anonymousId` | string | one of | |
| `name` | string | no | Screen name |
| `properties` | object | no | |

### `operation = group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `userId` / `anonymousId` | string | one of | |
| `groupId` | string | yes | Company/account id |
| `traits` | object | no | Group traits |

### `operation = alias`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `userId` | string | yes | New id |
| `previousId` | string | yes | Old id |

## Output fields (by operation)

All operations share the same success/error shape (Tracking API typically returns `200` with `{ success: true }` or empty body).

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `success` | boolean | 2xx | From body when present |
| `error` | string | 4xx/5xx/timeout | Include 400 size-limit text when applicable |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | From `Retry-After` or `X-RateLimit-Reset` |

## Legal

- HTTP API: https://www.twilio.com/docs/segment/connections/sources/catalog/libraries/server/http-api
- Twilio Segment DPA: https://www.twilio.com/legal/data-protection-addendum (fetched 2026-08-20)
- Operator is the Segment workspace owner. Tracking payloads often contain **email and user identifiers**. Minimize traits/properties. Customer supplies the lawful basis.
- Never log write keys, `traits`, `properties`, emails, or `userId` values. Log operation, byte size, HTTP status.
- Recommended cap **1,000 requests/second per workspace**; excess may 429. Batch endpoint 500 KB. Single request **32 KB**.
- EU: using `https://api.segment.io/v1/` from the EU can **silently drop** events — set `data_plane_url` to the Dublin host when the workspace is regional EU.

## Implementation notes

- JDK HTTP POST JSON. `Content-Type: application/json`.
- Auth: `Authorization: Basic {base64(write_key + ":")}` **and** omit key from body, **or** body `writeKey` without Basic. Prefer Basic so the key is not duplicated. Never log the Authorization header.
- Validate: missing write key → `{ error }`, no HTTP. Missing both `userId` and `anonymousId` → `{ error }`. `track` without `event` → `{ error }`. `group` without `groupId` → `{ error }`. `alias` without `previousId` → `{ error }`.
- Size: reject body &gt; 32 KB (or 500 KB for batch) before send.
- Timeout default **15 s**. Retry 429 up to 2 times using `Retry-After`. Do not retry 400.
- `data_plane_url` must be https. Allowlist default hosts: `api.segment.io`, `events.eu1.segmentapis.com`. Custom data planes only if the operator’s Segment-managed custom domain is set — still https + egress policy.
- No listen/webhook registration on this node.
