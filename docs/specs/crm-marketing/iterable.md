# Iterable

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/iterable/`
- **Researched:** 2026-08-20
- **Official API:** Iterable REST (`/api/...`). No calendar version string; project is USDC or EDC.
- **Docs:** https://api.iterable.com/api/docs (US) · https://api.eu.iterable.com/api/docs (EU) · endpoint guide https://support.iterable.com/hc/en-us/articles/204780579-Iterable-API-Endpoints-and-Sample-Payloads
- **Auth:** server-side API key header `Api-Key` — secret binding: `api_key`. Do not use client-side / JWT browser keys for these ops.

## Scope

Server-side user upsert/get/delete, event track, list subscribe/unsubscribe, catalog item upsert, campaign trigger, webhook registration. Keys and base URL are **data-center bound** (`region` = `api.iterable.com` \| `api.eu.iterable.com`). Default published throughput **30 req/s**; bulk event endpoints allow up to 1 000 events/request (out of catalog scope).

Email/SMS consent lives on the user profile (`emailListIds`, `unsubscribedChannelIds`, `subscribedMessageTypeIds`, etc.). The operator must set lawful consent fields before `campaign.trigger`. StudioQuack does not infer consent.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `user.upsert` | POST | `/api/users/update` | Create/update profile (`email` and/or `userId` + `dataFields`) |
| `user.get` | GET | `/api/users/{email}` or `/api/users/byUserId/{userId}` | Fetch profile |
| `user.delete` | DELETE | `/api/users/{email}` or `/api/users/byUserId/{userId}` | GDPR-style delete |
| `event.track` | POST | `/api/events/track` | Custom event (triggers journeys) |
| `list.add` | POST | `/api/lists/subscribe` | Subscribe emails/userIds to a static list |
| `list.remove` | POST | `/api/lists/unsubscribe` | Unsubscribe from a static list |
| `catalog.upsert` | PUT | `/api/catalogs/{catalogName}/items/{itemId}` | Replace catalog item |
| `campaign.trigger` | POST | `/api/campaigns/trigger` | Send/trigger campaign to listed users |
| `trigger.listen` | POST | `/api/webhooks` | Register/update project webhook URL (also GET to list) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `user.upsert` | no | |
| `api_key` | password | yes | empty | yes | Server-side project key (`Api-Key`) |
| `region` | select | yes | `api.iterable.com` | no | Host without scheme: `api.iterable.com` (USDC) / `api.eu.iterable.com` (EDC). `baseUrl` `https://{region}`. |
| `timeout_seconds` | number | no | `25` | no | Allow ≥60 s between user update and campaign send (Iterable guidance). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Emails and `dataFields` are PII |
| `output` | OUTPUT | `{ status, code, msg, … }` or `{ error }` | Iterable `{ code, msg }` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = user.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | one of email/`user_id` | Profile email |
| `user_id` | string | one of email/`user_id` | Maps to `userId` |
| `data_fields` | object | no | Profile fields. Include marketing consent fields the project expects. |
| `prefer_user_id` | boolean | no | `preferUserId` as documented |

### `operation = user.get` / `user.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | one of | Path-encoded |
| `user_id` | string | one of | Uses `byUserId` path |

### `operation = event.track`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_name` | string | yes | `eventName` |
| `email` | string | one of email/`user_id` | |
| `user_id` | string | one of | |
| `data_fields` | object | no | Event properties |
| `created_at` | number | no | Unix ms if documented |
| `campaign_id` | number | no | Optional attribution |

### `operation = list.add` / `list.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | number | yes | Static list id |
| `subscribers` | array | yes | `{ email }` and/or `{ userId }` objects as documented |

### `operation = catalog.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `catalog_name` | string | yes | Path |
| `item_id` | string | yes | Path |
| `item` | object | yes | Catalog item JSON (`update` / replace per PUT semantics) |

### `operation = campaign.trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | number | yes | |
| `recipients` | array | yes | Users to send (`email` / `userId` / dataFields) |
| `allow_repeat_marketing_sends` | boolean | no | Override 18-hour marketing send cap |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `endpoint` | string | yes | SQ ingress HTTPS URL |
| `auth` | string | no | Iterable webhook auth header value they document |
| `events` | array | no | Event types if the POST body supports a filter (confirm in API Explorer) |

## Output fields (by operation)

### Upsert / track / list / catalog / campaign

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `code` | string | 2xx | Iterable `code` (`Success`, …) |
| `msg` | string | 2xx | |
| `params` | object | when present | e.g. catalog URLs, campaign `messageId` |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx | 30 |

### `user.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `user` | object | 2xx | Profile including `dataFields` (PII) |

### `user.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `webhook` | object | 2xx | Provider echo |

## Legal

- Explorer: https://api.iterable.com/api/docs
- Getting started (US/EU hosts): https://support.iterable.com/hc/en-us/articles/41044692130196-Getting-Started-with-Iterable-s-API
- API keys: https://support.iterable.com/hc/en-us/articles/360043464871-API-Keys
- Terms: https://iterable.com/legal/terms/
- Trust ToS (linked from API catalog): https://iterable.com/trust/terms-of-service/
- CAN-SPAM/CASL/GDPR consent: caller duty. Do not trigger campaigns without stored consent flags.
- Never log `Api-Key`, emails, or `dataFields`.

## Implementation notes

- JDK HTTP. Header `Api-Key`. JSON. Path-encode emails.
- Timeout 25 s. Body cap 512 KiB (single event/user). TLS verified. `Redirect.NEVER`.
- Wrong `region` for the key → 401; do not retry the other DC automatically.
- 4xx/5xx → `{ error }` on OUTPUT. No inner retry. Iterable recommends backoff on concurrent profile writes.
