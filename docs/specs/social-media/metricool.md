# Metricool

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/metricool/`
- **Researched:** 2026-08-20
- **Official API:** Metricool REST **API 2.0.0** (OpenAPI 3.0.1) — base `https://app.metricool.com/api`
- **Docs:** https://app.metricool.com/resources/apidocs/index.html — Swagger https://app.metricool.com/api/swagger.json (also `/api/swagger.yaml`) — Help https://help.metricool.com/api-access-export-your-metricool-data-to-other-tools-and-automate-tasks-x8ln5 — PDF https://static.metricool.com/API+DOC/API+English.pdf
- **Auth:** api_key — secret binding: `api_token` (header **`X-Mc-Auth`** = Account Settings → API `userToken`). Every call also sends query **`userId`** and **`blogId`**.

## Scope

Official REST for **Advanced and Custom** plans (token UI under Account Settings → API). Catalog ops map to swagger: schedule `POST /v2/scheduler/posts`, analytics `GET /v2/analytics/aggregation` (and network post lists), inbox `GET /v2/inbox/conversations`. Media: PDF + swagger `GET /actions/normalize/image/url` (public URL copy into Metricool storage) and/or `PUT /v2/media/s3/upload-transactions` (presigned S3). Do not scrape the Metricool SPA; do not copy third-party connector path lists. Help-center articles that name other automation products are not reproduced here.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `post_schedule` | POST | `/v2/scheduler/posts` | Create a scheduled (or draft) planner post |
| `analytics_get` | GET | `/v2/analytics/aggregation` | Metric series: required `network`, `metric`, `from`, `to` |
| `inbox_list` | GET | `/v2/inbox/conversations` | Inbox conversations; required query `provider` |

Supporting (same node, not extra catalog rows): `GET /admin/simpleProfiles` to list brands; `GET /v2/scheduler/posts` to list by date (optional later).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `analytics_get` | no | Chooses the row above |
| `api_token` | password | yes | empty | yes | `X-Mc-Auth`. Never on INPUT, never logged. |
| `user_id` | string | yes | empty | no | Query `userId` |
| `blog_id` | string | yes | empty | no | Query `blogId` (brand) |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `media` type `file` when uploading via S3 transaction |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | No token |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = post_schedule`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | Body (`ScheduledPost.text`) |
| `publication_date_time` | string | yes | Local datetime for `publicationDate.dateTime` |
| `timezone` | string | no | `publicationDate.timezone` (e.g. `Europe/Madrid`) |
| `providers` | array | yes | Min 1. Swagger `ProviderStatus`: `network` (required in practice), optional `id`, `status` (`PUBLISHED` \| `PUBLISHING` \| `PENDING` \| `AWAITING_CONFIRMATION` \| `ERROR` \| `DRAFT`), `publicUrl` |
| `draft` | boolean | no | Planner draft |
| `auto_publish` | boolean | no | Auto-publish flag |
| `media_url` | string | no | Public HTTPS URL; handler may `GET /actions/normalize/image/url?url=` then set `media[].url` |
| `media` | file | no | If set, `PUT /v2/media/s3/upload-transactions` with `resourceType=planner`, then use returned URLs. Cap **20_000_000** bytes |
| `facebook_data` / `instagram_data` / … | object | no | Optional per-network blocks from `ScheduledPost` (only fields the operator supplies) |

Required swagger fields: `providers`, `publicationDate`, `text`.

### `operation = analytics_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `network` | string | yes | Query `network` |
| `metric` | string | yes | Query `metric` |
| `from` | string | yes | Range start |
| `to` | string | yes | Range end |
| `timezone` | string | no | Query `timezone` |
| `subject` | string | no | Query `subject` |
| `scope` | string | no | Query `scope` |

### `operation = inbox_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `provider` | string | yes | Query `provider` (required in swagger) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `post` | object | `post_schedule` | Created `ScheduledPost` (id/uuid) |
| `analytics` | object | `analytics_get` | Aggregation JSON (cap body) |
| `conversations` | array | `inbox_list` | Inbox payload |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx |

## Legal

- Help (Advanced/Custom; token + usage monitor): https://help.metricool.com/api-access-export-your-metricool-data-to-other-tools-and-automate-tasks-x8ln5
- How to find an endpoint from the product UI (inspector): https://help.metricool.com/how-to-get-an-endpoint-in-metricool-to-make-api-calls-eclkf — implementers should still **prefer swagger paths**, not ad-hoc Network-tab guesses.
- Basic integration: https://help.metricool.com/basic-guide-for-api-integration-r97af
- OpenAPI: https://app.metricool.com/api/swagger.json
- Operator must hold an eligible plan and accept Metricool’s product terms. Inbox and analytics include social-network PII and message content.
- **Numeric HTTP rate limits were not published** in the help/PDF/swagger text fetched 2026-08-20; the product UI “API usage” monitor is authoritative. Still map 429 to `{ error }` and do not tight-loop.
- Destination-network ToS still apply to scheduled posts. No unofficial hosts.

## Implementation notes

- JDK `HttpClient` only. Base `https://app.metricool.com/api`. Header `X-Mc-Auth: {api_token}`. Append `userId` and `blogId` on **every** URL.
- Empty token / user / blog → `{ error }` no HTTP. Optional brand discovery: `GET /admin/simpleProfiles`.
- `publicationDate` is swagger `DateTimeInfo`: required `dateTime`, optional `timezone`.
- `post_schedule` with `media_url`: `GET /actions/normalize/image/url?url=` **before** POST scheduler (PDF). Put the returned URL on `media[].url` (`ScheduledMediaDto`).
- S3 upload: `PUT /v2/media/s3/upload-transactions` with `S3PresignedUploadRequest` (`resourceType=planner`, `contentType`, `parts` with size/hash/byte range). PUT file bytes only to returned presigned URLs.
- `analytics_get` 200 schema is `JsonOkResponseDouble` (aggregation). Inbox 200 is `JsonOkListResponseConversation`.
- Response cap **1_000_000** bytes. Timeout 30 s (60 s if uploading).
- 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. Truncate 240 chars; redact `X-Mc-Auth`.
- Do not auto-retry inside the handler.
- Optional list of scheduled posts: `GET /v2/scheduler/posts` with `start` / `end` / `timezone` (not a catalog verb; useful when implementing).
- Logs: operation, userId, blogId, status, sizes — never token, inbox message text, or file bytes.
