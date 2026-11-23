# Insightly

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/insightly/`
- **Researched:** 2026-08-20
- **Official API:** Insightly REST **v3.1**
- **Docs:** https://api.insightly.com/v3.1/Help and pod Help `https://api.{pod}.insightly.com/v3.1/`
- **Auth:** HTTP Basic, API key as username, **empty password** — secret binding: `api_key`.

## Scope

CRM objects on the operator’s Insightly instance: contacts, organisations, leads, opportunities, projects, tasks, notes, events, custom field metadata. Base URL is **pod-specific**: `https://api.{pod}.insightly.com/v3.1/` (example pod `na1`). Pod is shown under User Settings next to the API key.

v3.1: POST/PUT accept subcollections; PUT is partial (unset with JSON `null`). Permissions follow the key owner’s UI rights. JSON only. HTTPS required.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `contact.create` | POST | `/Contacts` | Create contact |
| `contact.get` | GET | `/Contacts/{id}` | One contact |
| `contact.list` | GET | `/Contacts` | Page with `top`/`skip` (default 100, max **500**) |
| `contact.update` | PUT | `/Contacts/{id}` | Partial update; optional `If-Match` ETag |
| `contact.delete` | DELETE | `/Contacts/{id}` | Delete |
| `organisation.create` | POST | `/Organisations` | |
| `organisation.get` | GET | `/Organisations/{id}` | |
| `organisation.list` | GET | `/Organisations` | |
| `organisation.update` | PUT | `/Organisations/{id}` | |
| `organisation.delete` | DELETE | `/Organisations/{id}` | |
| `lead.create` | POST | `/Leads` | |
| `lead.get` | GET | `/Leads/{id}` | |
| `lead.list` | GET | `/Leads` | |
| `lead.update` | PUT | `/Leads/{id}` | |
| `lead.delete` | DELETE | `/Leads/{id}` | |
| `opportunity.create` | POST | `/Opportunities` | |
| `opportunity.get` | GET | `/Opportunities/{id}` | |
| `opportunity.list` | GET | `/Opportunities` | |
| `opportunity.update` | PUT | `/Opportunities/{id}` | |
| `opportunity.delete` | DELETE | `/Opportunities/{id}` | |
| `project.create` | POST | `/Projects` | |
| `project.get` | GET | `/Projects/{id}` | |
| `task.create` | POST | `/Tasks` | |
| `task.get` | GET | `/Tasks/{id}` | |
| `task.list` | GET | `/Tasks` | |
| `note.create` | POST | `/Notes` | |
| `event.create` | POST | `/Events` | Calendar events |
| `customField.list` | GET | `/CustomFields` | FIELD_NAME lookup before writes |
| `trigger.listen` | n/a | Insightly System Settings → Integrations → Webhooks (UI) | Record create/update push to SQ ingress |

Search: `GET /{Resource}/Search?field_name=&field_value=` is available; **not** a separate catalog verb — `*.list` may accept `field_name`/`field_value`/`updated_after_utc` as optional filters.

**UNVERIFIED (2026-08-20):** a documented `POST /Webhooks` resource on v3.1 Help overview. Do not invent REST webhook CRUD. Use the product webhook UI.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.list` | no | |
| `api_key` | password | yes | empty | yes | Basic username |
| `pod` | string | yes | `na1` | no | Host label only (`^[a-z0-9]+$`) |
| `timeout_seconds` | number | no | `25` | no | |
| `default_top` | number | no | `100` | no | List page size, max 500 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Record body or id |
| `output` | OUTPUT | object or `{ error }` | CRM PII |

FUNCTION ports: none.

## Input fields (by operation)

### Create (`*.create`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `record` | object | yes | Insightly JSON (e.g. contact `FIRST_NAME`, `LAST_NAME`, `EMAIL_ADDRESS`, `CUSTOMFIELDS[]` with `FIELD_NAME`). Dates in body: `yyyy-MM-dd HH:mm:ss` UTC. |

### Get / delete (`*.get`, `*.delete`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Record integer id |

### List (`*.list`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `top` | number | no | 1–500 |
| `skip` | number | no | Offset |
| `count_total` | boolean | no | Adds `X-Total-Count` |
| `field_name` | string | no | Switches to `/Search` |
| `field_value` | string | no | With `field_name` |
| `updated_after_utc` | string | no | ISO-8601 `yyyy-mm-ddThh:mm:ssZ` |

### Update (`*.update`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | |
| `record` | object | yes | Fields to set; `null` clears |
| `etag` | string | no | Sent as `If-Match`; 412 if stale |

### `customField.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Optional future object-type query if Help documents it |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payload` | object | yes from ingress | Insightly webhook JSON |
| `shared_secret` | string | no | Query token the operator embedded in the webhook URL (Insightly HMAC **UNVERIFIED** on official Help; do not assume `x-insightly-signature` unless pod Help documents it) |

## Output fields (by operation)

### Get / create / update

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `record` | object | 2xx | Full object including `ETag` |
| `error` | string | failure | 401 encoding, 412 ETag, 429 |
| `http_status` | number | HTTP error | |
| `rate_limit_remaining` | number | when header present | `X-RateLimit-Remaining` |
| `retry_after_seconds` | number | 429 / 5xx | Until next UTC day if daily quota; else 30 |

### List

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `records` | array | 2xx | |
| `total_count` | number | if `count_total` | From `X-Total-Count` |

### Delete

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | accepted | `ok` |
| `record` | object | | Passthrough body |
| `error` | string | secret mismatch | |

## Legal

- Help: https://api.insightly.com/v3.1/Help
- Overview (na1): https://api.na1.insightly.com/v3.1/Help/_OverviewResource
- Support: https://support.insight.ly/en-us/Knowledge/article/1447/Working_with_the_Insightly_Web_API
- Rate: **10 req/s**; daily: Gratis 1 000; Plus 40 000; Professional 60 000; Enterprise 100 000; Legacy 20 000 (rolling 24 h). Headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`.
- Contacts/leads/emails are PII. Customer owns the instance. Never log Basic credentials or record bodies.

## Implementation notes

- Host: `https://api.` + pod + `.insightly.com` (allowlist charset). Basic `Base64(api_key + ":")`.
- `Accept: application/json`. Optional `Accept-Encoding: gzip`.
- Timeout 25 s. Response cap 2 MiB. TLS verified. `Redirect.NEVER`.
- JDK HTTP only. 4xx/5xx → `{ error }` on OUTPUT. No inner retry.
