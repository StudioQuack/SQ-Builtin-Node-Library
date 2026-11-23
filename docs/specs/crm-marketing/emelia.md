# Emelia

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/emelia/`
- **Researched:** 2026-08-20
- **Official API:** Emelia REST API **1.0.0** (`https://api.emelia.io`)
- **Docs:** https://docs.emelia.io/docs/api-reference · intro/auth: https://docs.emelia.io/docs/emelia/emelia · lists: https://docs.emelia.io/docs/emelia/lists
- **Auth:** API key in header `Authorization: {apiKey}` (scheme name `ApiKey`) — secret binding: `apiKey`. GraphQL at `graphql.emelia.io` is **legacy** — do not call it.

## Scope

One node for Emelia B2B outreach: **advanced campaigns**, **legacy email campaign** start/pause (documented paths), contact lists, and webhooks. Catalog intent is cold-email campaign control, not LinkedIn scraping.

**Do not implement** `LinkedIn Scrapper` / Sales Navigator endpoints even if they appear in the REST index. Those conflict with LinkedIn’s terms; this node must not scrape LinkedIn. If a contact payload includes `linkedinUrlProfile`, pass it through only when the operator supplied it — do not fetch or enrich from LinkedIn.

`trigger.listen` = `POST /webhook` with the StudioQuack ingress URL.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `campaign.create` | POST | `/advanced/campaigns` | Create an advanced campaign |
| `campaign.get` | GET | `/advanced/campaigns/{campaignId}` | Full config including `recipients.lists` |
| `campaign.list` | GET | `/advanced/campaigns` | List advanced campaigns |
| `campaign.start` | POST | `/emails/campaigns/{campaignId}/start` | Start a **legacy email** campaign (verified path) |
| `campaign.pause` | POST | `/emails/campaigns/{campaignId}/pause` | Pause a **legacy email** campaign |
| `campaign.addContact` | POST | `/advanced/campaign/contacts` **or** `/emails/campaign/contacts` | Add one contact to advanced vs legacy email campaign |
| `contactList.add` | POST | `/lists/list/{listId}/contacts` | Add one contact to a list (auto-propagates to campaigns using the list) |
| `contactList.list` | GET | `/lists/list` | List lists (`page`/`limit` optional envelope). Live probe 2026-08-20: `GET /lists` is Express 404; `GET /lists/list` returns JSON 401 without a key. |
| `trigger.listen` | POST | `/webhook` | Watch campaign activity |

If `campaign.start` / `campaign.pause` return 400 for an **advanced** campaign id, surface `{ error }` stating start/pause are documented on `/emails/campaigns/{id}/…` (legacy email). Do **not** guess an `/advanced/campaigns/{id}/start` path.

Create-empty-list (`POST` under `/lists`) is not a catalog verb; operators create lists in Emelia or via list docs, then `contactList.add`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `campaign.list` | no | Chooses the row above |
| `apiKey` | password | yes | | yes | Raw key in `Authorization` (no `Bearer` prefix unless Emelia’s current docs show one — 2026-08-20 docs: pass the key as the header value) |
| `campaignKind` | select | no | `advanced` | no | `advanced` vs `email` for addContact |
| `timeoutMs` | number | no | `30000` | no | Read timeout |
| `maxResponseBytes` | number | no | `1048576` | no | Response cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo API key |

FUNCTION ports: none. CSV export exists (`file`) but is **not** in the catalog row — omit.

## Input fields (by operation)

### `operation = campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Campaign name (confirm required keys against live OpenAPI if the page is sparse) |
| `body` | object | yes | Remaining official create body — **do not invent** sequence/LinkedIn steps. If the published schema is incomplete, send only documented properties and fail on 400. |

### `operation = campaign.get` / `campaign.start` / `campaign.pause`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignId` | string | yes | Campaign id |

### `operation = campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Pagination if supported |
| `limit` | number | no | Page size |

### `operation = campaign.addContact`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignId` | string | yes | Target campaign (field name as in current POST body) |
| `email` | string | yes | Contact email |
| `firstName` | string | no | Name |
| `custom` | object | no | Extra keys per Emelia field detection — minimize |

### `operation = contactList.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `listId` | string | yes | List id |
| `email` | string | usual | Email (duplicate detection) |
| `firstName` | string | no | Also accepts human labels like `First Name` |
| `updateIfExists` | boolean | no | Upsert vs skip duplicate |
| `phone` | string | no | Phone (PII) |

Do not require `linkedinUrlProfile`. If the operator sends it, store it as they provided.

Bulk add (`POST` up to 100) is not a catalog verb; loop in BPMN or omit.

### `operation = contactList.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | If set with `limit`, paginated envelope |
| `limit` | number | no | Page size |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | HTTPS ingress |
| `campaignId` | string | no | Scope if the create body supports it |

## Output fields (by operation)

### `operation = campaign.create` / `campaign.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `campaignId` | string | 200 | Id |
| `campaign` | object | get | Config; log id only |

### `operation = campaign.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `campaigns` | object[] | 200 | Summaries |

### `operation = campaign.start` / `campaign.pause`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 200 | Started/paused |

### `operation = campaign.addContact` / `contactList.add`

| Field | Type | When | Purpose |
|---|---|---|---|
| `leadId` | string | 200 | Contact id |
| `duplicate` | boolean | if dup | Existing |
| `updated` | boolean | if upsert | Updated |

### `operation = contactList.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `lists` | object[] | 200 | Lists |

### `operation = trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `webhook` | object | 200 | Created webhook |

Failures: `{ error }`.

## Legal

- REST docs: https://docs.emelia.io/docs/emelia/emelia
- Product: https://emelia.io/ (cold email / LinkedIn outreach marketing).
- **Cold email:** CAN-SPAM, CASL, GDPR consent, and sender reputation are the **customer’s** duty. StudioQuack does not accept Emelia ToS and does not operate as the sender.
- Do not build LinkedIn Sales Navigator scrape tools. LinkedIn URLs in contact records remain the customer’s compliance problem.
- Contacts are PII. Never log API keys, emails, or phones.
- Marketing site claims “Unlimited API Calls”; still apply local timeout/size caps and back off on 429 if returned.

## Implementation notes

- JDK HTTP only. Base `https://api.emelia.io`. Header `Authorization` = API key (no GraphQL).
- Timeouts 10s/`timeoutMs`; request ≤ 256 KiB; response ≤ `maxResponseBytes`.
- Retry `429`/`5xx` max 3. Other 4xx → `{ error }`.
- Create-campaign request bodies on the public pages are thin; implement against the live OpenAPI/Docusaurus schema at build time and keep this spec’s field list as the **minimum** contract.

**UNVERIFIED:** JSON property names on `POST /advanced/campaigns` and `POST /advanced/campaign/contacts` (Docusaurus pages omit the body schema). `campaign.start` / `pause` are verified only for `/emails/campaigns/{id}/…`.
