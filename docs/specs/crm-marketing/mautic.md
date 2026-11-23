# Mautic

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/mautic/`
- **Researched:** 2026-08-20
- **Official API:** Mautic REST **`/api`** (classic JSON API). Docs pinned to **7.1**. Optional newer `/api/v2` (API Platform) is **out of scope** for these catalog verbs.
- **Docs:** https://devdocs.mautic.org/en/7.1/rest_api/getting_started.html · Auth: https://devdocs.mautic.org/en/7.1/rest_api/authentication.html · Contacts: https://devdocs.mautic.org/en/7.1/rest_api/contacts.html
- **Auth:** oauth2 (preferred) or HTTP Basic **if enabled** — secret bindings: `client_secret` / `password` / refresh token

## Scope

One node against the operator’s Mautic base URL (self-host or Cloud). API is **off by default** (`Configuration → API Settings` or `api_enabled`). Base: `https://{host}/api`. OAuth2 grants: `authorization_code`, `refresh_token`, `client_credentials`. Token URL: `{base}/oauth/v2/token`. Prefer `Authorization: Bearer`. **Do not put access tokens on the query string.** Basic auth is still documented in 7.1 but must be turned on (`api_enable_basic_auth`); Mautic **recommends OAuth2**. Some hosted/Cloud builds disable Basic — treat Basic as fallback, not default. Do not ship the PHP api-library.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `contact.create` | POST | `/contacts/new` | Create contact |
| `contact.get` | GET | `/contacts/{id}` | Fetch one |
| `contact.list` | GET | `/contacts` | Search/page |
| `contact.update` | PATCH | `/contacts/{id}/edit` | Partial update (`PUT` full replace also exists) |
| `contact.delete` | DELETE | `/contacts/{id}/delete` | Delete |
| `contact.points` | POST | `/contacts/{id}/points/plus/{points}` or `/minus/{points}` | Adjust points |
| `contact.dnc` | POST | `/contacts/{id}/dnc/{channel}/add` or `/remove` | Do-not-contact |
| `contact.sendEmail` | POST | `/emails/{emailId}/contact/{contactId}/send` | Send marketing email to contact |
| `company.create` | POST | `/companies/new` | Create company |
| `company.get` | GET | `/companies/{id}` | Fetch company |
| `company.list` | GET | `/companies` | List companies |
| `company.update` | PATCH | `/companies/{id}/edit` | Update |
| `company.delete` | DELETE | `/companies/{id}/delete` | Delete |
| `companyContact.add` | POST | `/companies/{companyId}/contact/{contactId}/add` | Link |
| `companyContact.remove` | POST | `/companies/{companyId}/contact/{contactId}/remove` | Unlink |
| `campaignContact.add` | POST | `/campaigns/{campaignId}/contact/{contactId}/add` | Add to campaign |
| `campaignContact.remove` | POST | `/campaigns/{campaignId}/contact/{contactId}/remove` | Remove |
| `segmentContact.add` | POST | `/segments/{segmentId}/contact/{contactId}/add` | Add to segment |
| `segmentContact.remove` | POST | `/segments/{segmentId}/contact/{contactId}/remove` | Remove |
| `segmentEmail.send` | POST | `/emails/{id}/send` (segment send as documented) | Send email to its segments |
| `trigger.listen` | POST | `/hooks/new` | Create webhook (`name`, `webhookUrl`, `triggers[]`) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.get` | no | Chooses the row above |
| `base_url` | string | yes | empty | no | Origin only, e.g. `https://mautic.example.com` (SSRF-allowlisted by daemon) |
| `auth_mode` | select | yes | `oauth2` | no | `oauth2` \| `basic` |
| `client_id` | string | if oauth2 | empty | no | API Credentials client id |
| `client_secret` | password | if oauth2 | empty | yes | Client secret |
| `oauth_access_token` | password | if oauth2 | empty | yes | Access token (refresh outside or on 401) |
| `username` | string | if basic | empty | no | Mautic user |
| `password` | password | if basic | empty | yes | User password |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s. |

Validate `base_url` is https (except operator-local dev). No open redirects.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include passwords |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact.create` | `contact.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | number | update yes | Path id |
| `email` | string | create yes | Email |
| `firstname` | string | no | First name |
| `lastname` | string | no | Last name |
| `fields` | object | no | Other Mautic fields (`overwriteWithBlank` as documented) |
| `ip` | string | no | IP |
| `overwrite_with_blank` | boolean | no | Clear empty fields |

### `operation = contact.get` | `contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | number | yes | Id |

### `operation = contact.list` | `company.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `search` | string | no | Search / commands |
| `start` | number | no | Offset (default 0) |
| `limit` | number | no | Default ~30 |
| `order_by` | string | no | Column |
| `order_by_dir` | string | no | `asc` / `desc` |
| `published_only` | boolean | no | Filter |
| `minimal` | boolean | no | Smaller payload |

### `operation = contact.points`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | number | yes | Contact |
| `delta` | number | yes | Absolute points; sign via `direction` |
| `direction` | string | yes | `plus` \| `minus` |

### `operation = contact.dnc`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | number | yes | Contact |
| `channel` | string | yes | `email` (default) / `sms` / … |
| `action` | string | yes | `add` \| `remove` |
| `reason` | number | no | 1 unsubscribed, 2 bounced, 3 manual |
| `comments` | string | no | Note |

### `operation = contact.sendEmail`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email_id` | number | yes | Email asset |
| `contact_id` | number | yes | Contact |
| `tokens` | object | no | Token replacements |

### `operation = company.create` | `company.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `company_id` | number | update yes | Id |
| `companyname` | string | create yes | Name |
| `fields` | object | no | Other company fields |

### `operation = company.get` | `company.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `company_id` | number | yes | Id |

### `operation = companyContact.add` | `companyContact.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `company_id` | number | yes | Company |
| `contact_id` | number | yes | Contact |

### `operation = campaignContact.add` | `campaignContact.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | number | yes | Campaign |
| `contact_id` | number | yes | Contact |

### `operation = segmentContact.add` | `segmentContact.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `segment_id` | number | yes | Segment |
| `contact_id` | number | yes | Contact |

### `operation = segmentEmail.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email_id` | number | yes | Email with segments |
| `tokens` | object | no | Replacements |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Webhook name |
| `webhook_url` | string | yes | Maps to `webhookUrl` (StudioQuack message-event ingress) |
| `triggers` | array | yes | Event types e.g. `mautic.lead_post_save_new`, `mautic.lead_post_save_update` |
| `secret` | string | no | HMAC secret; deliveries use `Webhook-Signature` |
| `is_published` | boolean | no | `isPublished` |
| `description` | string | no | Description |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | number | create/get | Contact/company/webhook id |
| `contact` | object | contact ops | `contact` object (capped). Do not log email. |
| `company` | object | company ops | Company object |
| `items` | array | list | Contacts/companies |
| `total` | number | list | Total count when present |
| `error` | string | failure | `error.message` or OAuth `error_description` |
| `http_status` | number | HTTP error | 401/403/404 |
| `retry_after_seconds` | number | 429 / 5xx | If present |

OAuth errors: `{ "error": "invalid_grant", "error_description": "…" }`. System: `{ "error": { "message", "code": 403 } }`.

## Legal

- Auth: https://devdocs.mautic.org/en/7.1/rest_api/authentication.html
- Contacts/DNC/points: https://devdocs.mautic.org/en/7.1/rest_api/contacts.html
- Emails send: https://devdocs.mautic.org/en/7.1/rest_api/emails.html
- Self-host: operator is the processor; they enable API and egress. Cloud: customer’s Mautic Cloud contract.
- Contacts are PII. Caller owns CAN-SPAM / CASL / GDPR before `contact.sendEmail` / `segmentEmail.send`.
- Never log passwords, client secrets, Bearer tokens, or emails.
- Rate-limiter cache config is **gone in Mautic 7+** (getting started). Honor HTTP 429 anyway.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Join `{base_url}/api` + path. SSRF: only the configured host.
- OAuth: Bearer header. Basic: `Authorization: Basic` of `username:password` **only** if `auth_mode=basic`.
- Empty credentials or `api_enabled` off (typical 403) → `{ error }` with “enable API in Mautic”.
- Response cap **1_000_000** bytes. Timeout default 30 s.
- Optional `Mautic-Version` response header (since 2.4.0) may be copied to logs (not PII).
- 429 → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry inside the handler.
- Logs: operation, host (not URL with tokens), http_status, ids, sizes — never emails or secrets.

Verified 7.1: companies `POST /companies/COMPANY_ID/contact/CONTACT_ID/add|remove`; webhooks `POST /hooks/new` with `name`, `webhookUrl`, `triggers` (https://devdocs.mautic.org/en/7.1/rest_api/webhooks.html). Basic is **not removed** in 7.1; catalog “deprecated in some versions” = must-enable + some Cloud hosts deny. `/api/v2` is a separate surface — do not mix paths.
