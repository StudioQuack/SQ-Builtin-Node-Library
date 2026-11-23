# Intercom

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/intercom/`
- **Researched:** 2026-08-20
- **Official API:** Intercom REST API **2.15** (`Intercom-Version: 2.15`)
- **Docs:** [developers.intercom.com/docs/references/2.15/introduction](https://developers.intercom.com/docs/references/2.15/introduction) · [Contacts](https://developers.intercom.com/docs/references/rest-api/api.intercom.io/contacts) · [Companies](https://developers.intercom.com/docs/references/rest-api/api.intercom.io/companies) · [Conversations](https://developers.intercom.com/docs/references/rest-api/api.intercom.io/conversations) · [Webhooks](https://developers.intercom.com/docs/references/webhooks/webhook-models) · [REST overview](https://developers.intercom.com/docs/build-an-integration/learn-more/rest-apis)
- **Auth:** bearer access token (private app) or OAuth 2.0 (public app) — secret binding: `access_token`. Webhook HMAC uses `client_secret` (password).

## Scope

One node for Intercom Contacts, Companies, and Conversations. Calls `https://api.intercom.io` (US), `https://api.eu.intercom.io` (Europe), or `https://api.au.intercom.io` (Australia) over HTTPS with JDK HTTP only. `send-message` and `listen` use the **Conversations** API and Developer Hub webhooks — not a sibling node.

**Leads are not a REST resource.** Intercom merged Users and Leads into **Contacts** years ago. A contact’s `role` is `user` or `lead`. There is no `/leads` collection on API 2.15. Seed verbs `create-lead`, `get-lead`, `list-leads`, `update-lead`, and `delete-lead` are **dropped**. Create a lead by `create-contact` with `role=lead`. Merge lead→user with Intercom’s `POST /contacts/merge` is **out of v1** (optional later op on this same node).

This is not a ticketing/Fin-agent node. Tickets, Help Center, and Fin Agent API stay off this node.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `create-contact` | POST | `/contacts` | Create a contact (`role` `user` or `lead`) |
| `get-contact` | GET | `/contacts/{contact_id}` | Fetch one contact by Intercom id |
| `list-contacts` | GET | `/contacts` | Page all contacts (users and leads) |
| `update-contact` | PUT | `/contacts/{contact_id}` | Patch name, email, custom attributes, `role` |
| `delete-contact` | DELETE | `/contacts/{contact_id}` | Archive/delete the contact (Intercom archives) |
| `create-company` | POST | `/companies` | Create a company (`company_id` or `name`) |
| `get-company` | GET | `/companies/{company_id}` | Fetch one company by Intercom id |
| `list-companies` | GET | `/companies` | List companies (cursor/`per_page`) |
| `update-company` | PUT | `/companies/{company_id}` | Update company name, plan, size, custom attributes |
| `list-company-contacts` | GET | `/companies/{company_id}/contacts` | Contacts attached to a company (replaces seed `list-company-users`) |
| `send-message` | POST | `/conversations` **or** `/conversations/{conversation_id}/reply` | Start an in-app conversation from a contact, or reply as admin/contact |
| `listen` | inbound POST | Developer Hub webhook URL | Verify `X-Hub-Signature` and emit conversation/contact topics |

Dropped (not listed as later): `create-lead`, `get-lead`, `list-leads`, `update-lead`, `delete-lead`. Seed `list-company-users` → `list-company-contacts`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `create-contact` | no | Row above |
| `access_token` | password | yes (except inbound `listen` verify) | — | **yes** | `Authorization: Bearer`. Developer Hub → your app → Access token (private) or OAuth access token (public). Never log. |
| `client_secret` | password | `listen` | — | **yes** | HMAC-SHA1 key for `X-Hub-Signature`. Developer Hub → Basic Information → Client secret. Not the access token. |
| `region` | select | no | `api.intercom.io` | no | Host without scheme: `api.intercom.io` / `api.eu.intercom.io` / `api.au.intercom.io` (`https://{region}`) |
| `api_version` | string | no | `2.15` | no | Sent as `Intercom-Version`. Pin 2.15 unless the operator has verified a newer versioned reference. |
| `admin_id` | string | `send-message` reply as admin | — | no | Intercom teammate id for admin comments/notes |
| `listen_topics` | string | `listen` | `conversation.user.created,conversation.user.replied` | no | Comma-separated Intercom topics to accept; others → `{ error }` without forwarding |
| `timeout_seconds` | number | no | `25` | no | Connect+read timeout. `<= 0` → 25 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secrets never on this port |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens or `client_secret` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = create-contact`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `role` | string | no | `user` (default) or `lead` |
| `email` | string | one of email / `external_id` / `user_id` | Contact email |
| `external_id` | string | one of | Your system id (`external_id`) |
| `user_id` | string | one of | Alias of `external_id` if Intercom still accepts it on 2.15 — send as `external_id` |
| `name` | string | no | Display name |
| `phone` | string | no | Phone |
| `custom_attributes` | object | no | Workspace-defined attributes |
| `signed_up_at` | number | no | Unix seconds |

### `operation = get-contact`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | Intercom contact id |

### `operation = list-contacts`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `per_page` | number | no | Page size (Intercom default/max as published; cap at 150) |
| `starting_after` | string | no | Cursor from previous `pages.next.starting_after` |

### `operation = update-contact`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | Intercom id |
| `role` | string | no | `user` or `lead` |
| `email` | string | no | New email |
| `name` | string | no | New name |
| `phone` | string | no | New phone |
| `custom_attributes` | object | no | Merged custom attributes |
| `unsubscribed_from_emails` | boolean | no | Marketing opt-out |

### `operation = delete-contact`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | Intercom id to archive |

### `operation = create-company`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `company_id` | string | one of `company_id` / `name` | Your company id (Intercom `company_id`) |
| `name` | string | one of | Company name |
| `plan` | string | no | Plan name |
| `size` | number | no | Employee count |
| `website` | string | no | Website |
| `industry` | string | no | Industry |
| `custom_attributes` | object | no | Custom company attributes |

### `operation = get-company`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `company_id` | string | yes | Intercom **internal** company id (path), not `company_id` attribute unless documented as accepted on GET |

### `operation = list-companies`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `per_page` | number | no | Page size |
| `starting_after` | string | no | Cursor |

### `operation = update-company`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `company_id` | string | yes | Intercom internal id (path) |
| `name` | string | no | Name |
| `plan` | string | no | Plan |
| `size` | number | no | Size |
| `website` | string | no | Website |
| `industry` | string | no | Industry |
| `custom_attributes` | object | no | Custom attributes |

### `operation = list-company-contacts`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `company_id` | string | yes | Intercom company id |
| `per_page` | number | no | Page size |
| `starting_after` | string | no | Cursor |

### `operation = send-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `conversation_id` | string | no | If set → **reply** (`POST /conversations/{id}/reply`). If omitted → **create** (`POST /conversations`) |
| `contact_id` | string | create, or contact-authored reply | Intercom contact id (`from` / contact reply identity) |
| `body` | string | comment/note | HTML/plain body. Required for `message_type` `comment` and `note` |
| `message_type` | string | reply | `comment` (default) \| `note` \| `quick_reply` |
| `type` | string | reply as admin | `admin` when posting as teammate |
| `attachment_urls` | array | no | Up to 10 image URLs (Intercom limit) |
| `attachment_files` | file | no | Platform `file` ref-ids (max 10). Handler reads bytes from the file store and maps to Intercom `attachment_files` (`content_type`, `name`, base64 `data`) — never put raw bytes on INPUT |

Create-conversation body: `from: { type: "user" or "lead" / contact, id: contact_id }`, `body`. In-app only per Intercom create-conversation docs.

### `operation = listen`

Inbound webhook only. INPUT may be empty when the engine delivers the raw body to the node as the message-event payload. If the handler is invoked with a parsed envelope:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | yes (receive) | Exact JSON bytes Intercom signed |
| `x_hub_signature` | string | yes (receive) | Header `X-Hub-Signature` (`sha1=<hex>`) |

Subscribe (Developer Hub): operator sets the webhook HTTPS URL to the StudioQuack message-event ingress. Intercom sends a signed `ping` on save — return HTTP 2xx. `listen` does **not** create a second node.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `contact` | object | contact ops | `{ id, role, email, name, phone, external_id, custom_attributes, created_at }` |
| `contacts` | array | list-contacts, list-company-contacts | Page of contact summaries |
| `company` | object | company ops | `{ id, company_id, name, plan, size, website }` |
| `companies` | array | list-companies | Page of companies |
| `conversation` | object | send-message | Conversation or message model Intercom returned |
| `conversation_id` | string | send-message | Id for later reply |
| `starting_after` | string | lists | Next cursor, if any |
| `has_more` | boolean | lists | Whether another page exists |
| `topic` | string | listen | e.g. `conversation.user.created` |
| `notification_id` | string | listen | Dedupe key (`id` on the notification envelope) |
| `item` | object | listen | `data.item` (conversation or contact). **Do not log message bodies** |
| `error` | string | failure | Validation or `Intercom API Error: HTTP {status}`. Never tokens |

## Legal

- [Intercom Developer Terms](https://developers.intercom.com/docs/publish-to-the-app-store/intercom-developer-terms)
- [Intercom Customer Terms](https://www.intercom.com/legal/terms-and-policies) (confirm live URL)
- Rate limits ([official](https://developers.intercom.com/docs/references/rest-api/errors/rate-limiting)): default **10,000 calls/minute per app** and **25,000/minute per workspace**, spread across 10-second windows. HTTP 429 + `X-RateLimit-Limit` / `Remaining` / `Reset`. Do not retry-storm; map 429 to `{ error }` (engine may retry once with backoff).
- Contacts, companies, and conversation bodies are **personal data** (names, emails, message text, possibly health or financial content the operator stored). Customer is controller; Intercom is the processor under the customer’s Intercom DPA. Minimize fields. Never log `body`, tokens, or `client_secret`.
- Regional hosting: pick `region` (`api.intercom.io` / `api.eu.intercom.io` / `api.au.intercom.io`) so traffic stays in the workspace’s residency.
- Official-API-first: no Messenger scrape, no undocumented admin UI.

## Implementation notes

- JDK `HttpClient` only. Headers: `Authorization: Bearer`, `Accept: application/json`, `Content-Type: application/json`, `Intercom-Version: {api_version}`.
- Timeouts: `timeout_seconds` (default 25). Body cap: 8 MiB. No vendor SDK.
- Errors: HTTP 4xx/5xx → `{ error: "Intercom API Error: HTTP {status}" }` plus Intercom `errors[].message` when present. Never throw past dispatch.
- `listen`: HMAC-SHA1 over **raw** body with `client_secret`; compare to `X-Hub-Signature` after `sha1=` using a constant-time compare. SHA-256 will never match. Reject missing/invalid signature. Respond 2xx quickly (Intercom: ~5s or delivery fails).
- Access tokens and client secrets are `configurationParameters` only.

### Examples

Create a lead-role contact:

```json
{ "role": "lead", "email": "ada@example.com", "name": "Ada" }
```

Reply to a conversation:

```json
{
  "conversation_id": "123",
  "message_type": "comment",
  "type": "admin",
  "body": "Thanks — we are looking into this."
}
```

Success (create-contact): `{ "status": "ok", "contact": { "id": "…", "role": "lead", "email": "ada@example.com" } }`.
