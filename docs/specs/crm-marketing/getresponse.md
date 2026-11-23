# GetResponse

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/getresponse/`
- **Researched:** 2026-08-20
- **Official API:** GetResponse **API 3.0** (path `/v3`)
- **Docs:** https://apidocs.getresponse.com/v3 — auth https://apidocs.getresponse.com/v3/authentication — limits https://apidocs.getresponse.com/v3/limits — contacts https://apidocs.getresponse.com/v3/case-study/adding-contacts — webhooks https://apidocs.getresponse.com/v3/webhooks — callbacks https://apidocs.getresponse.com/v3/callbacks
- **Auth:** `api_key` (`X-Auth-Token: api-key <key>`) or OAuth2 Bearer — secret binding: `apiKey` / `clientSecret`

## Scope

One node for GetResponse email marketing. Retail host **`https://api.getresponse.com/v3`**. GetResponse MAX uses `https://api3.getresponse360.com/v3` or `https://api3.getresponse360.pl/v3` plus header `X-Domain: example.com` (domain only, no scheme). In GetResponse a **campaign is a list**. Contacts are PII; newsletters are marketing messages (CAN-SPAM/CASL/GDPR consent is the operator’s duty). **Customer owns the GetResponse account.** Unused API keys expire after **90 days**. Do not scrape the app UI.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `contact.create` | POST | `/contacts` | Add contact to a list (`202` = queued) |
| `contact.get` | GET | `/contacts/{contactId}` | Fetch one contact |
| `contact.list` | GET | `/contacts` | List/query contacts |
| `contact.update` | POST | `/contacts/{contactId}` | Modify contact (GR uses POST, not PUT) |
| `contact.delete` | DELETE | `/contacts/{contactId}` | Remove contact |
| `campaign.list` | GET | `/campaigns` | List lists (`campaignId`) |
| `newsletter.send` | POST | `/newsletters` | Create/send a newsletter resource |
| `tag.add` | POST | `/contacts/{contactId}` | Attach tag ids on the contact (`tags`) |
| `trigger.listen` | POST | `/accounts/callbacks` | Official callback URL registration; see also UI webhooks |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.get` | no | Chooses the row above |
| `auth` | select | yes | `apiKey` | no | `apiKey` or `oauth2` |
| `apiKey` | password | if apiKey | | yes | `X-Auth-Token: api-key …` |
| `accessToken` | password | if oauth2 | | yes | `Authorization: Bearer` |
| `clientId` / `clientSecret` | string / password | if oauth2 refresh | | secret on secret | OAuth app |
| `baseUrl` | select | yes | `https://api.getresponse.com/v3` | no | Retail vs MAX hosts |
| `parentLogin` | string | no | | no | `X-Parent-Login` for multi-account MAX |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Contact email |
| `campaignId` | string | yes | List token (`campaign.campaignId`) — **not** the 8-digit UI number |
| `name` | string | no | Display name |
| `dayOfCycle` | string | no | Autoresponder day |
| `tags` | string[] | no | Tag ids |
| `customFieldValues` | object[] | no | `{ customFieldId, value[] }` |
| `ipAddress` | string | no | Geo (operator must have a lawful basis) |

### `operation = contact.get` / `contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | yes | Contact id |

### `operation = contact.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | object | no | GR querystring filters (campaignId, email) |
| `page` / `perPage` | number | no | Pagination |
| `fields` | string | no | Sparse field list |

### `operation = contact.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | yes | Contact id |
| `name` / `campaign` / `tags` / `customFieldValues` | mixed | no | Fields to change |

### `operation = campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` / `perPage` | number | no | Pagination |
| `query` | object | no | Name filter if documented |

### `operation = newsletter.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Internal newsletter name |
| `subject` | string | yes | Subject line |
| `campaignId` | string | yes | Target list → body `campaign.campaignId` |
| `html` | string | yes | Maps to `content.html` (cap size) |
| `plain` | string | no | Maps to `content.plain` |
| `fromFieldId` | string | no | Maps to `fromField.fromFieldId` |
| `sendOn` | string | no | ISO datetime to queue (omit = send per account defaults / draft) |

Official PHP client: `POST newsletters` (`sendNewsletter`). `202` means queued. Fail closed on `400` validation `error` resource rather than guessing extra fields.

### `operation = tag.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | yes | Contact id |
| `tagIds` | string[] | yes | Existing tag ids (`GET /tags` to resolve) |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | HTTPS callback (SQ ingress) |
| `actions` | string[] | no | Callback `action` names for the older callbacks API |

Newer **Webhooks** (subscribe/unsubscribe/click/open) are configured in the GetResponse **Webhooks UI** per official docs; the node may return those setup notes plus register `/accounts/callbacks` when the operator uses the callbacks API. Ingress must return **2xx and JSON `{"status":"OK"}`** for the new webhook product.

## Output fields (by operation)

### `contact.create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | **202** means queued, not yet on the list |
| `contactId` | string | when body includes it | Id if returned |
| `error` | object | failure | `{ status, code, message }` |

### `contact.get` / `contact.update`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `contact` | object | success | Contact JSON (PII) |
| `error` | object | failure | `{ status, code, message }` |

### `contact.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `contacts` | object[] | success | Page |
| `error` | object | failure | `{ status, code, message }` |

### `contact.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `deleted` | boolean | success | `true` |
| `error` | object | failure | `{ status, code, message }` |

### `campaign.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `campaigns` | object[] | success | `{ campaignId, name, href }` |
| `error` | object | failure | `{ status, code, message }` |

### `newsletter.send`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `newsletterId` | string | success | Resource id |
| `error` | object | failure | `{ status, code, message }` |

### `tag.add`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `contact` | object | success | Updated contact |
| `error` | object | failure | `{ status, code, message }` |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `callback` | object | success | Registered callback (no secrets) |
| `webhookUiNote` | string | always | Point operator at in-app Webhooks + payload spec |
| `error` | object | failure | `{ status, code, message }` |

## Legal

- GetResponse legal: https://www.getresponse.com/legal
- Privacy: https://www.getresponse.com/legal/privacy
- Rate limits: **30,000 calls / 10 minutes** and **80 calls/second**; **10** parallel requests. Headers `X-RateLimit-Limit|Remaining|Reset`. HTTP `429` with `currentLimit` / `timeToReset`. Batch contact API: **80 calls / 10 minutes**, **1/s**, max 1000 contacts (out of scope for this node).
- Marketing consent: operator must have GDPR/CCPA/CAN-SPAM/CASL basis before `contact.create` / `newsletter.send`.
- Official-API-first: configured `baseUrl` hosts only.

## Implementation notes

- JDK `HttpClient` only. Allowlist `api.getresponse.com`, `api3.getresponse360.com`, `api3.getresponse360.pl`.
- POST requires `Content-Type: application/json`.
- Timeouts: connect 10s, read 30s. Body cap 2 MiB (`html` for newsletters included).
- Retry: `429` wait `X-RateLimit-Reset`; `5xx` backoff + jitter, max 3. Do not retry contact.create blindly (POST is not idempotent).
- Errors → `{ error }` on OUTPUT.
- **DO NOT BUILD** in this task.
