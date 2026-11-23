# Brevo

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/brevo/`
- **Researched:** 2026-08-20
- **Official API:** Brevo REST API v3 (`https://api.brevo.com/v3`)
- **Docs:** https://developers.brevo.com/docs/getting-started
- **Auth:** `api_key` — secret binding: `apiKey` (header `api-key`). OAuth 2.0 exists for partner apps; this node uses a server API key.

## Scope

One node for the Brevo (formerly Sendinblue) marketing + transactional REST surface. Contacts, list membership, contact attributes, verified senders, transactional SMTP send, and marketing campaign send-now. Inbound events use `trigger.listen` on this same node (Brevo webhooks POST to StudioQuack message-event ingress). Do not scrape the Brevo UI. SMS/WhatsApp/Conversations/CRM deals are out of this node’s first cut (catalog is email-primary).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `contact.create` | POST | `/v3/contacts` | Create a contact (`updateEnabled` false) |
| `contact.upsert` | POST | `/v3/contacts` | Same path with `updateEnabled: true` |
| `contact.get` | GET | `/v3/contacts/{identifier}` | Fetch by email or contact id |
| `contact.list` | GET | `/v3/contacts` | Page contacts (`limit`, `offset`, `modifiedSince`) |
| `contact.update` | PUT | `/v3/contacts/{identifier}` | Patch attributes / list membership |
| `contact.delete` | DELETE | `/v3/contacts/{identifier}` | Remove a contact |
| `contactAttribute.create` | POST | `/v3/contacts/attributes/{attributeCategory}/{attributeName}` | Define a contact attribute |
| `contactAttribute.getAll` | GET | `/v3/contacts/attributes` | List attribute definitions |
| `contactAttribute.update` | PUT | `/v3/contacts/attributes/{attributeCategory}/{attributeName}` | Rename/type-safe update of definition |
| `contactAttribute.delete` | DELETE | `/v3/contacts/attributes/{attributeCategory}/{attributeName}` | Drop a definition |
| `email.send` | POST | `/v3/smtp/email` | Transactional email (inline HTML/text) |
| `email.sendTemplate` | POST | `/v3/smtp/email` | Same path with `templateId` + `params` |
| `sender.create` | POST | `/v3/senders` | Add a sender identity |
| `sender.list` | GET | `/v3/senders` | List senders |
| `sender.delete` | DELETE | `/v3/senders/{senderId}` | Remove a sender |
| `list.add` | POST | `/v3/contacts/lists/{listId}/contacts/add` | Add existing contacts to a list |
| `campaign.send` | POST | `/v3/emailCampaigns/{campaignId}/sendNow` | Send an existing marketing campaign now |
| `trigger.listen` | POST | `/v3/webhooks` | Register marketing and/or transactional webhook URL |

Retired / unofficial verbs are omitted. Do not call v2 Sendinblue hosts.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.upsert` | no | Chooses the row above |
| `apiKey` | password | yes | | yes | Brevo API key (`api-key` header) |
| `attributeCategory` | select | no | `normal` | no | Attribute ops: `normal` / `transactional` / `category` / `calculated` / `global` |
| `webhookType` | select | no | `transactional` | no | `trigger.listen`: `transactional` or `marketing` |
| `webhookEvents` | string | no | | no | Comma-separated Brevo event names (e.g. `delivered,hardBounce,unsubscribed`) |
| `timeoutMs` | number | no | `30000` | no | HTTP read timeout |
| `maxResponseBytes` | number | no | `1048576` | no | Hard cap on response body |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `apiKey` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact.create` / `contact.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | if no `extId` / SMS | Contact email |
| `extId` | string | no | External id (`ext_id`) |
| `attributes` | object | no | Capitalized attribute map (`FNAME`, `SMS`, …) already defined in Brevo |
| `listIds` | number[] | no | Lists to join |
| `emailBlacklisted` | boolean | no | Email suppression |
| `smsBlacklisted` | boolean | no | SMS suppression |

### `operation = contact.get` / `contact.update` / `contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identifier` | string | yes | Email or numeric contact id |
| `attributes` | object | update only | Fields to change |
| `listIds` | number[] | update only | Replace/add list membership per API body |
| `unlinkListIds` | number[] | update only | Remove from lists |

### `operation = contact.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Page size (Brevo max 1000) |
| `offset` | number | no | Offset |
| `modifiedSince` | string | no | ISO-8601 filter |
| `sort` | string | no | `asc` / `desc` |

### `operation = contactAttribute.create` / `update` / `delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `attributeName` | string | yes | Name in the path |
| `type` | string | create | e.g. `text`, `date`, `float`, `boolean`, `category` |
| `enumeration` | object[] | category type | Value list |
| `value` | string | update (calculated) | Formula when applicable |

### `operation = email.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `senderEmail` | string | yes | From address (must be a verified sender) |
| `senderName` | string | no | From name |
| `to` | object[] | yes | `[{ email, name? }]` — minimize PII |
| `subject` | string | yes | Subject |
| `htmlContent` | string | if no `textContent` | HTML body |
| `textContent` | string | if no `htmlContent` | Text body |
| `replyTo` | object | no | Reply-to |
| `tags` | string[] | no | Correlation tags for webhooks |

### `operation = email.sendTemplate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `templateId` | number | yes | Brevo template id |
| `to` | object[] | yes | Recipients |
| `params` | object | no | Template variables |
| `senderEmail` | string | no | Override sender if allowed |

### `operation = sender.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Sender display name |
| `email` | string | yes | Sender email |

### `operation = sender.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `senderId` | number | yes | Sender id |

### `operation = list.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `listId` | number | yes | Target list |
| `emails` | string[] | if no `ids` | Existing contact emails |
| `ids` | number[] | if no `emails` | Existing contact ids |

### `operation = campaign.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignId` | number | yes | Existing email campaign |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `notifyUrl` | string | yes | HTTPS StudioQuack message-event ingress URL |
| `description` | string | no | Webhook label |

## Output fields (by operation)

### `operation = contact.create` / `contact.upsert`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 201 | Created |
| `id` | number | success | Contact id |

### `operation = contact.get` / `contact.update`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 200 | OK |
| `id` | number | success | Contact id |
| `email` | string | success | Email (do not log) |
| `attributes` | object | success | Attribute map |

### `operation = contact.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `contacts` | object[] | 200 | Page |
| `count` | number | if returned | Total |

### `operation = contact.delete` / `sender.delete` / `contactAttribute.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 204 | No body |

### `operation = contactAttribute.getAll`

| Field | Type | When | Purpose |
|---|---|---|---|
| `attributes` | object | 200 | Definitions |

### `operation = email.send` / `email.sendTemplate`

| Field | Type | When | Purpose |
|---|---|---|---|
| `messageId` | string | 201 | SMTP message id |

### `operation = sender.create` / `sender.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | number | create | Sender id |
| `senders` | object[] | list | Senders |

### `operation = list.add`

| Field | Type | When | Purpose |
|---|---|---|---|
| `contacts` | object | 201 | `{ success, failure }` counts if returned |

### `operation = campaign.send`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 204 | Scheduled now; 402 if credits insufficient |

### `operation = trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `webhookId` | number | 201 | Webhook id |

All failures: `{ error }` only — never `apiKey`.

## Legal

- Developer terms / API: https://www.brevo.com/legal/termsofuse/ and https://developers.brevo.com/docs/api-limits
- Rate limits (general tier, 2026-08-20): contacts ~10 RPS / 36,000 RPH; `POST /v3/smtp/email` up to 1,000 RPS / 3,600,000 RPH on general; other routes as low as 100 RPH. Honor `429` and rate-limit headers. Max 40 webhooks (marketing + transactional combined).
- Contacts, emails, and webhook payloads are PII. The **customer** is the controller of their Brevo account. StudioQuack does not accept Brevo’s ToS on the customer’s behalf.
- CAN-SPAM / CASL / GDPR consent, unsubscribe, and suppression lists are the customer’s duty. Do not send to purchased lists.
- Official API only. No unofficial wrappers.

## Implementation notes

- JDK `HttpClient` only (no Brevo SDK). Base `https://api.brevo.com`. Header `api-key`.
- Connect timeout 10s; read `timeoutMs`; request body ≤ 256 KiB; response ≤ `maxResponseBytes`.
- Retry: `429` and `5xx` with exponential backoff, max 3, honor `Retry-After`. Do not retry other `4xx`.
- Map HTTP 4xx/5xx to `{ error }` on OUTPUT. Never throw past dispatch. Never log `apiKey`, bodies, or email lists.
