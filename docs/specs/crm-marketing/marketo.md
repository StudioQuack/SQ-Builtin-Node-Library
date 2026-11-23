# Marketo

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/marketo/`
- **Researched:** 2026-08-20
- **Official API:** Adobe Marketo Engage REST (Lead Database ` /rest/v1/` + Asset ` /rest/asset/v1/`) + Identity OAuth
- **Docs:** REST: https://experienceleague.adobe.com/en/docs/marketo-developer/marketo/rest/rest-api · Auth: https://experienceleague.adobe.com/en/docs/marketo-developer/marketo/rest/authentication (updated 2026-08-04)
- **Auth:** oauth2 client credentials (2-legged) — secret bindings: `client_id`, `client_secret`

## Scope

One node for a single Marketo instance. Identity and REST bases are **per-subscription** (Admin → Web Services). Token grant:

`GET|POST {identityUrl}/oauth/token?grant_type=client_credentials&client_id=…&client_secret=…`

Every REST call must send **`Authorization: Bearer {access_token}`**. Passing `access_token` as a **query or form parameter is removed on 2026-08-31** — do not implement query-string tokens. SOAP is deprecated as of 2026-07-31. Custom service belongs to an **API-only user**; roles gate verbs.

## Operations (`operation` config values)

REST host: `{restEndpoint}` from Web Services (typically `https://{munchkinId}.mktorest.com`).

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `lead.create` | POST | `/rest/v1/leads.json` | `action=createOnly` |
| `lead.upsert` | POST | `/rest/v1/leads.json` | `action=createOrUpdate`, `lookupField=email` (or `id`) |
| `lead.get` | GET | `/rest/v1/leads.json` | `filterType=email` or `id` + `filterValues` |
| `lead.describe` | GET | `/rest/v1/leads/describe.json` | Field metadata |
| `list.add` | POST | `/rest/v1/lists/{listId}/leads.json` | Add leads to static list |
| `list.remove` | DELETE | `/rest/v1/lists/{listId}/leads.json` | Remove from list |
| `campaign.request` | POST | `/rest/v1/campaigns/{id}/trigger.json` | Request Campaign (must have “Campaign is Requested” / Web Service API). Max 100 leads. |
| `activity.get` | GET | `/rest/v1/activities.json` | Activities; needs `nextPageToken` from `/rest/v1/activities/pagingtoken.json` |
| `form.submit` | POST | `/rest/v1/leads/submitForm.json` | Upsert by email + Fill out Form activity |
| `email.sendSample` | POST | `/rest/asset/v1/email/{id}/sendSample.json` | Send sample (form-urlencoded) |
| `asset.email.get` | GET | `/rest/asset/v1/email/{id}.json` | Fetch email asset |
| `trigger.listen` | — | none first-class | Marketo has no general REST webhook catalog like list-webhooks. Use **Webhook** flow steps inside Marketo pointing at StudioQuack ingress, or poll `activity.get`. This op **registers nothing** unless the instance exposes a documented hook API — return `{ error }` with that guidance if no URL to call. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `lead.get` | no | Chooses the row above |
| `identity_url` | string | yes | empty | no | Identity base from Web Services (no trailing slash) |
| `rest_endpoint` | string | yes | empty | no | REST base including `/rest` or as copied from Admin |
| `client_id` | password | yes | empty | yes | LaunchPoint Client Id |
| `client_secret` | password | yes | empty | yes | LaunchPoint Client Secret |
| `munchkin_id` | string | no | empty | no | Display / validation only |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s. |

Token lifetime is **3600 s**. Cache per `client_id`. On Marketo error **601** (invalid) or **602** (expired), refresh then retry **once**.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Marketo often HTTP 200 + `success: false` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = lead.create` | `lead.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Lookup / create key |
| `first_name` | string | no | REST field `firstName` |
| `last_name` | string | no | `lastName` |
| `fields` | object | no | Additional REST API names from `lead.describe` |
| `lookup_field` | string | no | Default `email` |

### `operation = lead.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `filter_type` | string | yes | `email` or `id` |
| `filter_values` | string | yes | Comma-separated values |

### `operation = list.add` | `list.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | number | yes | Static list id |
| `lead_ids` | array | yes | Lead ids |

### `operation = campaign.request`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | number | yes | Smart campaign id |
| `lead_ids` | array | yes | Max 100 `{ "id": n }` |
| `tokens` | array | no | `{ name: "{{my.x}}", value }` max 100 |

### `operation = activity.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `activity_type_ids` | string | yes | Comma-separated, max 10 |
| `since_datetime` | string | yes unless `next_page_token` | For paging-token call |
| `next_page_token` | string | no | From prior response |
| `lead_ids` | string | no | Up to 30 ids |
| `list_id` | number | no | Restrict to list |

### `operation = form.submit`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | number | yes | Form id |
| `lead_form_fields` | object | yes | Must include `email`; names = REST API names on the form |
| `program_id` | number | no | Program attribution |
| `cookie` | string | no | Munchkin cookie |
| `visitor_data` | object | no | `pageURL`, `queryString`, `leadClientIpAddress`, `userAgentString` |

### `operation = email.sendSample`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email_id` | number | yes | Asset id |
| `email_address` | string | yes | Sample recipient |
| `text_only` | boolean | no | As documented |

### `operation = asset.email.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email_id` | number | yes | Asset id |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callback_url` | string | yes | Documented for the operator; handler does not POST to Marketo unless a verified hook API exists |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | `success: true` | `ok` |
| `request_id` | string | always | Marketo `requestId` |
| `result` | array | success | Lead/activity/asset rows |
| `next_page_token` | string | activities | Continue token |
| `more_result` | boolean | activities | More pages |
| `error` | string | `success: false` or HTTP error | Concatenate `errors[].message` (no token) |
| `http_status` | number | HTTP error | Transport status |
| `retry_after_seconds` | number | 429 / timeout | 20 s minimum (rate window is 20 s) |

Treat `success: false` with HTTP 200 as `{ error }` on OUTPUT (do not throw).

## Legal

- Auth (query-token sunset **2026-08-31**): https://experienceleague.adobe.com/en/docs/marketo-developer/marketo/rest/authentication
- Quotas (REST getting started, updated 2026-08-07): **50,000 calls/day** (reset 00:00 CST); **100 calls / 20 seconds**; **10 concurrent**; URI max 8 KB; body 1 MB (bulk 10 MB).
- Leads are PII. Caller owns consent for email send-sample and form capture. Customer owns the Marketo subscription.
- Never log client secret, access token, or emails.
- Do not use double-slash REST gateway URLs (deprecated).

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- **Never** append `access_token=` to the query string.
- Identity 4xx → `{ error }` no REST call. 601/602 → refresh token, retry REST **once**.
- Response cap **1_000_000** bytes. Timeout default 30 s.
- Daily quota / 100-per-20s: 429 or error codes → `{ error, retry_after_seconds }` without tight loop.
- Logs: operation, requestId, success flag, result count — never tokens or emails.

**UNVERIFIED:** `trigger.listen` has **no** equivalent of Mailchimp list webhooks on the REST surface fetched 2026-08-20. Do not invent a Marketo subscribe URL. Identity URL path is `/oauth/token` on the Identity host (verified). REST getting-started still mentions an older query-token date (2025-06-30); follow the Authentication page (**2026-08-31**) and this spec.
