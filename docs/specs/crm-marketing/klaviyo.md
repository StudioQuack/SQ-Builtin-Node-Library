# Klaviyo

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/klaviyo/`
- **Researched:** 2026-08-20
- **Official API:** Klaviyo REST JSON:API, dated revision **`2026-07-15`** (GA)
- **Docs:** https://developers.klaviyo.com/en/docs/ · Versioning: https://developers.klaviyo.com/en/docs/api_versioning_and_deprecation_policy · Auth: https://developers.klaviyo.com/en/docs/authenticate_
- **Auth:** privateKey (`Authorization: Klaviyo-API-Key …`) or oauth2 — secret bindings: `private_api_key` or OAuth client secret + refresh token

## Scope

One server-side node for Klaviyo `/api` (not `/client`). Every request **must** send header `revision: 2026-07-15` (ISO date; invalid revision → 404). There is **no builtin** Klaviyo SDK in this library — JDK HTTP only. Partner marketplace apps should use OAuth; account automation may use a private key (`pk_…`). Do not put private keys in browser/client code. Do not use beta `2026-07-15.pre` in production.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `profile.create` | POST | `/api/profiles/` | Create a profile (JSON:API `type: profile`) |
| `profile.upsert` | POST | `/api/profile-import/` | Create or update by attributes / id (max body 100 KB) |
| `profile.get` | GET | `/api/profiles/{id}/` | Fetch one profile |
| `profile.update` | PATCH | `/api/profiles/{id}/` | Patch attributes (`null` clears a field) |
| `profile.search` | GET | `/api/profiles/` | Filter e.g. `equals(email,"…")` |
| `list.addProfiles` | POST | `/api/lists/{id}/relationships/profiles/` | Attach existing profiles (max 1000). For **consent**, prefer Subscribe Profiles. |
| `list.removeProfiles` | DELETE | `/api/lists/{id}/relationships/profiles/` | Detach profiles |
| `list.get` | GET | `/api/lists/{id}/` | Fetch list |
| `event.create` | POST | `/api/events/` | Record an event. Optional `backfill: true` (revision 2026-07-15) skips flow triggers. |
| `campaign.get` | GET | `/api/campaigns/{id}/` | Fetch campaign |
| `campaign.list` | GET | `/api/campaigns/` | List campaigns |
| `campaign.send` | POST | `/api/campaign-send-jobs/` | Async send job for an existing campaign |
| `segment.get` | GET | `/api/segments/{id}/` | Fetch segment |
| `template.list` | GET | `/api/templates/` | List templates |
| `catalog.item.upsert` | POST | `/api/catalog-item-bulk-create-jobs/` | Bulk catalog items (job). Single-item create is `/api/catalog-items/` when the payload is one item. |
| `trigger.listen` | POST | `/api/webhooks/` | Register webhook (`webhooks:write`). OAuth partner / Advanced KDP as documented. |

Base origin: `https://a.klaviyo.com`. Content-Type: `application/vnd.api+json`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `profile.get` | no | Chooses the row above |
| `auth_mode` | select | yes | `private_key` | no | `private_key` \| `oauth2` |
| `private_api_key` | password | if private_key | empty | yes | `pk_…` private key |
| `oauth_access_token` | password | if oauth2 | empty | yes | Bearer access token |
| `revision` | string | no | `2026-07-15` | no | Must be a documented GA revision. Do not default to `.pre`. |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s. Cap 120 s. |

Public/`company_id` client APIs are out of scope (browser tracking).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include keys |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = profile.create` | `profile.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes unless `phone_number` or `id` | Profile email |
| `phone_number` | string | no | E.164 |
| `external_id` | string | no | Your id |
| `first_name` | string | no | Attribute |
| `last_name` | string | no | Attribute |
| `properties` | object | no | Custom properties |
| `profile_id` | string | no | Upsert: existing Klaviyo id |

### `operation = profile.get` | `profile.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `profile_id` | string | yes | Path id |
| `email` | string | no | Update |
| `properties` | object | no | Update attributes; `null` clears |

### `operation = profile.search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes unless `filter` | Builds `filter=equals(email,"…")` |
| `filter` | string | no | Raw Klaviyo filter string |
| `page_cursor` | string | no | Cursor pagination |
| `page_size` | number | no | Page size as documented |

### `operation = list.addProfiles` | `list.removeProfiles`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | List id |
| `profile_ids` | array | yes | Profile ids (max 1000). Adding to a list does **not** grant marketing consent — use Subscribe Profiles in a later op if needed. |

### `operation = list.get` | `segment.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` / `segment_id` | string | yes | Path id |

### `operation = event.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `metric_name` | string | yes | Metric name |
| `profile_email` | string | yes unless `profile_id` | Identify profile |
| `profile_id` | string | no | Profile id |
| `properties` | object | no | Event properties |
| `time` | string | no | ISO timestamp |
| `unique_id` | string | no | Dedup key |
| `backfill` | boolean | no | `true` = historical; do not trigger flows (GA 2026-07-15) |

### `operation = campaign.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | Path id |

### `operation = campaign.list` | `template.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_cursor` | string | no | Cursor |
| `filter` | string | no | Provider filter |

### `operation = campaign.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | Campaign to send (JSON:API relationship on the send-job) |

### `operation = catalog.item.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `items` | array | yes | Catalog items (`external_id`, `title`, `description`, …) as JSON:API catalog-item attributes. Cap payload well under 5 MB. |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Webhook name |
| `endpoint_url` | string | yes | StudioQuack message-event ingress |
| `topics` | array | yes | Webhook topic ids from Get Webhook Topics |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/get/send | Resource or job id |
| `data` | object/array | 2xx | JSON:API `data` (capped). Do not log profile emails. |
| `included` | array | when present | Related resources |
| `links` | object | paginated | `next` cursor URL — parse cursor only, do not log query PII |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` / rate-limit headers |

## Legal

- API Terms: https://www.klaviyo.com/legal/api-terms
- Auth / scopes: https://developers.klaviyo.com/en/docs/authenticate_
- Revisions are supported ~2 years. Pin `2026-07-15`; do not rely on fall-forward.
- Rate limits are **per-endpoint** (burst + steady). Example: profile-import burst 75/s steady 750/m; list relationship writes 10/s 150/m; webhooks GET 1/s 15/m. 429 must not tight-loop.
- Email/SMS: double opt-in / GDPR consent is the **caller’s** duty. `list.addProfiles` does not subscribe. Customer owns the Klaviyo account.
- Never log private keys, OAuth tokens, or profile PII.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `Authorization: Klaviyo-API-Key {key}` **or** `Authorization: Bearer {oauth}`, `revision: {revision}`, `accept: application/vnd.api+json`, `content-type: application/vnd.api+json`.
- Missing `revision` or key → `{ error }` no HTTP.
- Response cap **1_000_000** bytes. `profile.upsert` request cap **100_000** bytes.
- HTTP 410 on retired revision → `{ error }` instructing operator to bump `revision`.
- 429 → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry inside the handler.
- Logs: operation, revision, http_status, resource type, sizes — never emails or tokens.

JSON:API body `{ "data": { "type": "campaign-send-job", "id": "{campaign_id}" } }` (operation `send_campaign`, revision `2026-07-15`, scope `campaigns:write`, burst 10/s steady 150/m). 202 accepted. Poll GET `/api/campaign-send-jobs/{id}/`. Omnichannel campaign **create** remains beta until planned GA 2026-10-15 — this node does not require it.
