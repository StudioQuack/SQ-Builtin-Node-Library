# Wufoo

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/wufoo/`
- **Researched:** 2026-08-20
- **Official API:** Wufoo REST API **v3** (`https://{subdomain}.wufoo.com/api/v3/`)
- **Docs:** https://wufoo.github.io/docs/ — help https://help.surveymonkey.com/en/wufoo/integrations/wufoo-api/ — terms https://www.wufoo.com/terms-of-use/
- **Auth:** api_key — HTTP Basic (API key as username, any non-empty password) — secret binding: `api_key`

## Scope

One node for Wufoo (SurveyMonkey-owned form product). Per-account host **`https://{subdomain}.wufoo.com/api/v3/`**. JSON via `.json` suffix (do not use `.xml` in this node). Lists forms, lists/submits entries, and registers webhooks. Webhook PUT accepts **`handshakeKey`**; Wufoo echoes it on every POST as **`HandshakeKey`**. Entries are respondent PII. **Customer owns the Wufoo account.** Do not scrape the form builder.

`webhook.register` and `trigger.listen` share `PUT /forms/{hash}/webhooks.json`. `trigger.listen` requires the URL to be StudioQuack message-event ingress and **requires** a handshake key.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `form.list` | GET | `/forms.json` | All forms |
| `form.get` | GET | `/forms/{identifier}.json` | One form (hash preferred over title) |
| `entry.list` | GET | `/forms/{identifier}/entries.json` | Entries (`pageSize` max 100) |
| `entry.submit` | POST | `/forms/{identifier}/entries.json` | Submit `Field{N}=value` form-encoded |
| `webhook.register` | PUT | `/forms/{identifier}/webhooks.json` | Subscribe URL (max 10 webhooks per form); optional handshake |
| `trigger.listen` | PUT | `/forms/{identifier}/webhooks.json` | Same PUT; HTTPS SQ ingress + required `handshakeKey` |

DELETE webhook (`/webhooks/{identifier}/{hash}.json`) is not a catalog verb.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `form.list` | no | Chooses the row above |
| `subdomain` | string | yes | | no | Account subdomain (`fishbowl` in docs). Host only — SSRF-safe: `^[a-z0-9-]+$` |
| `api_key` | password | yes | | yes | 16-character key from Form Manager → API Information |
| `identifier` | string | no | | no | Default form hash |
| `timeout_seconds` | number | no | `25` | no | Connect + read. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | File fields: type `file` |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo `apiKey` or `handshakeKey` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = form.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pretty` | boolean | no | Pretty JSON (debug only; default false) |

### `operation = form.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identifier` | string | yes* | Form hash (preferred) or title. Else `formHash` |

### `operation = entry.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identifier` | string | yes* | Form hash |
| `pageStart` | number | no | Default 0 |
| `pageSize` | number | no | Default 25; **max 100** |
| `sort` | string | no | Field id to sort by |
| `sortDirection` | string | no | `ASC` \| `DESC` |
| `system` | boolean | no | Include system fields |
| `FilterId` / `Filter1` … | string | no | Wufoo filter query params as documented |
| `match` | string | no | `AND` \| `OR` combining filters |

### `operation = entry.submit`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identifier` | string | yes* | Form hash |
| `fields` | object | yes | Map `Field1` → value (API ids from form API Information). File fields: `file` ref-ids |
| `comment` | string | no | Entry comment if the form allows |

POST body `application/x-www-form-urlencoded`, not JSON.

### `operation = webhook.register`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identifier` | string | yes* | Form hash |
| `url` | string | yes | Subscriber HTTPS URL |
| `handshakeKey` | string | no | Webhook handshake. Echoed as `HandshakeKey` |
| `metadata` | boolean | no | Include `FormStructure` / `FieldStructure` |

PUT is idempotent per URL (updates handshake if URL matches).

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identifier` | string | yes* | Form hash |
| `url` | string | yes | SQ message-event HTTPS URL |
| `handshakeKey` | string | yes | Required for listen |
| `metadata` | boolean | no | Default `false` (smaller PII surface) |

Inbound: reject POST unless `HandshakeKey` matches. Then correlate BPMN message.

## Output fields (by operation)

### `form.list` | `form.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `forms` / `form` | object / object[] | success | `Hash`, `Name`, `Url`, `DateCreated`, `EntryCount`, link hrefs |
| `error` | string | failure | |

### `entry.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `Entries` | object[] | success | Field columns (**PII**) |
| `error` | string | failure | |

### `entry.submit`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `Success` | number | success | 1 on success |
| `EntryId` | string | success | New entry id |
| `ErrorText` | string | validation | Field errors (no secrets) |
| `error` | string | failure | Including submit **rate-limit** payload |

### `webhook.register` | `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `Hash` | string | success | Webhook hash (`WebHookPutResult.Hash`) |
| `url` | string | success | Subscriber URL |
| `error` | string | failure | Never return handshake |

## Legal

- REST docs: https://wufoo.github.io/docs/
- SurveyMonkey Wufoo help: https://help.surveymonkey.com/en/wufoo/integrations/wufoo-api/
- Terms: https://www.wufoo.com/terms-of-use/
- Entries and webhook payloads are PII. Customer owns the Wufoo account and consent. Never log API key, handshake, or field values.
- Handshake key is a shared secret: store as daemon secret; compare constant-time on ingress; never log.
- Official-API-first: `{subdomain}.wufoo.com/api/v3/` only. TLS 1.0+ required by Wufoo; use modern TLS.
- Submit rate limit: Wufoo returns a dedicated error object when entry POST quota is exceeded (see docs “submit entry rate limit”); map to `{ error, http_status }` — do not retry immediately.

## Implementation notes

- JDK `HttpClient` only. Host `{subdomain}.wufoo.com` after subdomain allowlist. TLS verified. `Redirect.NEVER`.
- Auth: `Authorization: Basic base64(apiKey + ":" + dummy)` where dummy is any non-empty string (docs use `footastic`); do not log it.
- JSON only (`.json`). `Content-Type` for submit/webhook: `application/x-www-form-urlencoded`.
- Body cap **1_000_000** bytes. Timeout default 25 s.
- 429 / submit limit → `{ error, retry_after_seconds: 60 }`. 5xx/timeout → 30. Do not tight-loop.
- Logs: operation, form hash, status, counts — never keys or entry fields.
- **DO NOT BUILD** in this task.
