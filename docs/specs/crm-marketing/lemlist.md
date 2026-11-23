# Lemlist

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/lemlist/`
- **Researched:** 2026-08-20
- **Official API:** lemlist REST **v2** (`https://api.lemlist.com/api`)
- **Docs:** https://developer.lemlist.com/api-reference/getting-started/overview · Auth: https://developer.lemlist.com/api-reference/getting-started/authentication · OpenAPI: https://developer.lemlist.com/api-reference/openapi/v2.json
- **Auth:** api_key as HTTP **Basic** (empty username, API key as password) — secret binding: `api_key`

## Scope

One node for lemlist outreach. All routes live under `https://api.lemlist.com/api/{endpoint}`. **Do not use Bearer tokens.** Some list endpoints still default to deprecated **v1** unless `version=v2` is on the query string (documented at https://developer.lemlist.com/api-reference/getting-started/version). Enrichment may accept **LinkedIn profile URLs**; that use is subject to LinkedIn’s terms (operator duty) — this node must not scrape LinkedIn. Do not ship MCP/CLI as the runtime.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `lead.create` | POST | `/campaigns/{campaignId}/leads/` | Create lead in a campaign |
| `lead.get` | GET | `/leads/{emailOrId}` (see Get Lead by Email or ID) | Fetch lead |
| `lead.delete` | DELETE | campaign lead delete (Delete or Unsubscribe Lead) | Remove from campaign |
| `lead.unsubscribe` | POST | unsubscribe-lead-from-campaign **or** `/unsubscribes` | Permanent unsubscribe |
| `campaign.list` | GET | `/campaigns` | List campaigns — send **`version=v2`** |
| `campaign.stats` | GET | `/campaigns/{campaignId}/stats` | One campaign’s stats |
| `activity.list` | GET | `/activities` | Activity history (filter by type/campaign) |
| `enrichment.get` | GET | `/enrich/{enrichId}` | Poll a prior enrich job (`202` = in progress) |
| `enrichment.enrichLead` | POST | `/leads/{leadId}/enrich` | Enrich an existing lead (credits; query flags) |
| `enrichment.enrichPerson` | POST | `/enrich` | Find email / phone / LinkedIn from inputs |
| `team.get` | GET | `/team` | Team info |
| `team.credits` | GET | `/team/credits` | Remaining enrichment credits |
| `unsubscribe.add` | POST | `/unsubscribes` (Add Unsubscribe Email or Domain) | Add email or domain |
| `unsubscribe.delete` | DELETE | unsubscribe by email | Remove from list |
| `unsubscribe.list` | GET | `/unsubscribes` | List unsubscribes — `version=v2` if required |
| `trigger.listen` | POST | `/hooks` (Add Webhook) | Register event webhook |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `campaign.list` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Integration key from lemlist settings. Basic `:api_key`. Never on INPUT. |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s. Enrichment may need the full window. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include the key |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = lead.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | Path `campaignId` (`cam_…`) |
| `email` | string | no | Lead email |
| `first_name` | string | no | `firstName` |
| `last_name` | string | no | `lastName` |
| `company_name` | string | no | `companyName` |
| `job_title` | string | no | `jobTitle` |
| `linkedin_url` | string | no | `linkedinUrl` — **LinkedIn ToS** on caller |
| `phone` | string | no | Phone |
| `company_domain` | string | no | Domain |
| `timezone` | string | no | IANA tz |
| `custom_variables` | object | no | Extra keys stored as campaign variables |
| `deduplicate` | boolean | no | Query: skip if email exists in other campaigns |
| `linkedin_enrichment` | boolean | no | Query `linkedinEnrichment` (credits) |
| `find_email` | boolean | no | Query `findEmail` |
| `verify_email` | boolean | no | Query `verifyEmail` |
| `find_phone` | boolean | no | Query `findPhone` |

### `operation = lead.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email_or_id` | string | yes | Email or lead id |

### `operation = lead.delete` | `lead.unsubscribe`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes unless global unsubscribe | Campaign |
| `email_or_id` | string | yes | Lead identity |
| `action` | string | no | Provider delete vs unsubscribe flag as documented |

### `operation = campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `version` | string | no | Always send `v2` |
| `page` | number | no | Pagination as documented |
| `limit` | number | no | Page size |

### `operation = campaign.stats`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | Campaign id |

### `operation = activity.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | no | Filter |
| `type` | string | no | Activity type |
| `limit` | number | no | Page size |
| `offset` | number | no | Offset if documented |

### `operation = enrichment.enrichPerson`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `linkedin_url` | string | no | Profile URL (LinkedIn ToS) |
| `first_name` | string | no | Name |
| `last_name` | string | no | Name |
| `company_domain` | string | no | Domain |
| `email` | string | no | Starting email |
| `find_email` | boolean | no | Feature flag |
| `find_phone` | boolean | no | Feature flag |
| `find_linkedin` | boolean | no | Feature flag |

### `operation = enrichment.enrichLead`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `lead_id` | string | yes | Existing lead |
| `find_email` | boolean | no | Credits |
| `find_phone` | boolean | no | Credits |
| `linkedin_enrichment` | boolean | no | Credits |

### `operation = enrichment.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `enrichment_id` | string | yes | Job id from enrich response |

### `operation = unsubscribe.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes unless `domain` | Address |
| `domain` | string | yes unless `email` | Domain |

### `operation = unsubscribe.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Address to remove |

### `operation = unsubscribe.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `version` | string | no | Send `v2` |
| `limit` | number | no | Page size |
| `offset` | number | no | Offset |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target_url` | string | yes | StudioQuack message-event ingress |
| `events` | array | yes | lemlist webhook event names as documented |

`team.get` / `team.credits`: no input fields.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/get | Lead `_id`, campaign id, enrichment id, webhook id |
| `resource` | object | 2xx | Provider JSON (capped). Do not log emails or LinkedIn URLs. |
| `items` | array | list | Campaigns, activities, unsubscribes |
| `credits` | object | `team.credits` | Remaining credit balance |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 | Header `Retry-After` (often 2) |

## Legal

- Docs: https://developer.lemlist.com/
- Rate limit (documented): **20 requests per 2 seconds** per API key. Headers: `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
- Enrichment consumes **credits**. LinkedIn URL inputs must comply with LinkedIn’s user agreement / developer terms — this node does not scrape LinkedIn.
- Cold email: caller owns CAN-SPAM / CASL / GDPR consent and sender reputation. Customer owns the lemlist account.
- Never log API keys, emails, phones, or LinkedIn URLs.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Auth: `Authorization: Basic` of UTF-8 `: + api_key` Base64. Empty key → `{ error }` no HTTP.
- Always add `version=v2` on endpoints the version page flags (campaigns list at minimum).
- Response cap **1_000_000** bytes. Timeout default 30 s.
- 401/403/404 often return **plain text**, not JSON — map body (240 chars) to `{ error }`.
- 429 → `{ error, http_status: 429, retry_after_seconds }` from `Retry-After`. Do not auto-retry inside the handler.
- Logs: operation, http_status, campaign id, sizes — never emails, URLs, or keys.

Verified: `POST /leads/{leadId}/enrich`, `GET /enrich/{enrichId}`, `GET /team/credits`, `POST /hooks`. **UNVERIFIED:** which list GETs still require `version=v2` besides campaigns — check the version page per endpoint at implement time. Do not put competitor webhook examples from lemlist docs into this node.
