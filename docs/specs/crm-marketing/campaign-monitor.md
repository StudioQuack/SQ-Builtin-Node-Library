# Campaign Monitor

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/campaign-monitor/`
- **Researched:** 2026-08-20
- **Official API:** Campaign Monitor REST API v3.3 (`https://api.createsend.com/api/v3.3`)
- **Docs:** https://www.campaignmonitor.com/api/v3-3/getting-started/
- **Auth:** `api_key` HTTP Basic (`apiKey` as username, dummy password `x`) **or** OAuth 2.0 access token — secret bindings: `apiKey`, `clientSecret`, `accessToken`

## Scope

One node for Marigold Campaign Monitor: subscribers, lists, draft/send campaigns, and transactional (smart or classic) send. `trigger.listen` creates a **list webhook** (Subscribe / Update / Deactivate) pointing at StudioQuack message-event ingress. JSON only (append `.json`). Do not use XML. Do not scrape the Campaign Monitor UI.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `subscriber.add` | POST | `/api/v3.3/subscribers/{listId}.json` | Add or update one subscriber on a list |
| `subscriber.get` | GET | `/api/v3.3/subscribers/{listId}.json?email=` | Fetch one subscriber |
| `subscriber.update` | PUT | `/api/v3.3/subscribers/{listId}.json` | Update name/fields/consent |
| `subscriber.unsubscribe` | POST | `/api/v3.3/subscribers/{listId}/unsubscribe.json` | Unsubscribe from the list |
| `subscriber.delete` | DELETE | `/api/v3.3/subscribers/{listId}.json?email=` | Delete from the list |
| `list.create` | POST | `/api/v3.3/lists/{clientId}.json` | Create a list under a client |
| `list.get` | GET | `/api/v3.3/lists/{listId}.json` | List summary |
| `campaign.create` | POST | `/api/v3.3/campaigns/{clientId}.json` | Create a draft campaign from HTML/template |
| `campaign.send` | POST | `/api/v3.3/campaigns/{campaignId}/send.json` | Send or schedule a draft |
| `campaign.unsent` | GET | `/api/v3.3/clients/{clientId}/drafts.json` | List unsent (draft) campaigns |
| `transactional.send` | POST | `/api/v3.3/transactional/smartEmail/{smartEmailId}/send` **or** `/api/v3.3/transactional/classicEmail/send` | Transactional send (JSON-only family) |
| `trigger.listen` | POST | `/api/v3.3/lists/{listId}/webhooks.json` | Subscribe / Update / Deactivate callbacks |

v3.2 paths remain on the host but this node always uses **v3.3**.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `subscriber.add` | no | Chooses the row above |
| `auth` | select | yes | `apiKeyBasic` | no | `apiKeyBasic` or `oauth2` |
| `apiKey` | password | if Basic | | yes | Account or client API key (Basic username) |
| `oauthClientId` | string | if oauth2 | | no | OAuth app client id (Campaign Monitor calls this Client ID for the **app**, distinct from the CM **client**/account id) |
| `clientSecret` | password | if oauth2 | | yes | OAuth client secret |
| `accessToken` | password | if oauth2 | | yes | Bearer token |
| `cmClientId` | string | often | | no | Campaign Monitor **client** (tenant) id — required for list.create, campaign.create, campaign.unsent, classic transactional |
| `listId` | string | many ops | | no | Subscriber list id |
| `timeoutMs` | number | no | `30000` | no | HTTP read timeout |
| `maxResponseBytes` | number | no | `1048576` | no | Response cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo Basic/OAuth secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = subscriber.add` / `subscriber.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `emailAddress` | string | yes | Subscriber email |
| `name` | string | no | Display name |
| `mobileNumber` | string | no | E.164; empty string clears |
| `customFields` | object[] | no | `{ key, value }` — 250 char limit per value |
| `resubscribe` | boolean | no | Re-add previously unsubscribed (use with caution) |
| `consentToTrack` | string | recommended | `Yes` / `No` / `Unchanged` |
| `consentToSendSms` | string | no | SMS permission; customer’s legal duty |
| `restartSubscriptionBasedAutoresponders` | boolean | no | Only when resubscribing |

### `operation = subscriber.get` / `subscriber.delete` / `subscriber.unsubscribe`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `emailAddress` | string | yes | Subscriber email |

### `operation = list.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | List name |
| `unsubscribeSetting` | select | yes | `AllClientLists` (recommended) or `OnlyThisList` |
| `confirmedOptIn` | boolean | no | Double opt-in |

### `operation = list.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `listId` | string | yes | If not set in config |

### `operation = campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Campaign name |
| `subject` | string | yes | Subject |
| `fromName` | string | yes | From name |
| `fromEmail` | string | yes | From email |
| `replyTo` | string | yes | Reply-to |
| `htmlUrl` | string | if no template | Public HTML URL |
| `listIds` | string[] | yes | Recipient lists |
| `segmentIds` | string[] | no | Segments |

### `operation = campaign.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignId` | string | yes | Draft campaign id |
| `confirmationEmail` | string | yes | Send confirmation to this address |
| `sendDate` | string | no | `Immediately` or `YYYY-MM-DD HH:MM` |

### `operation = campaign.unsent`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `cmClientId` | string | yes | Client id |

### `operation = transactional.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | select | yes | `smart` or `classic` |
| `smartEmailId` | string | if smart | Smart email id |
| `to` | string[] | yes | Recipients |
| `data` | object | no | Merge variables (smart) |
| `from` | string | if classic | From |
| `subject` | string | if classic | Subject |
| `html` | string | if classic | Full HTML |
| `group` | string | no | Classic grouping |
| `consentToTrack` | string | recommended | Tracking consent |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | HTTPS ingress URL |
| `events` | string[] | yes | Subset of `Subscribe`, `Update`, `Deactivate` |
| `payloadFormat` | select | no | `json` (required for this node) |

## Output fields (by operation)

### `operation = subscriber.add` / `subscriber.update` / `subscriber.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 200 | OK |
| `emailAddress` | string | add | Echo (do not log) |
| `subscriber` | object | get | Record |

### `operation = subscriber.unsubscribe` / `subscriber.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 200 | OK |

### `operation = list.create` / `list.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `listId` | string | create | New list id |
| `list` | object | get | Summary |

### `operation = campaign.create` / `campaign.send`

| Field | Type | When | Purpose |
|---|---|---|---|
| `campaignId` | string | create | New id |
| `status` | number | send | 200 |

### `operation = campaign.unsent`

| Field | Type | When | Purpose |
|---|---|---|---|
| `drafts` | object[] | 200 | Unsent campaigns |

### `operation = transactional.send`

| Field | Type | When | Purpose |
|---|---|---|---|
| `messageId` | string | 202/200 | Provider message id when returned |

### `operation = trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `webhookId` | string | 201 | List webhook id |

Failures: `{ error }` — never API key / token.

## Legal

- API docs: https://www.campaignmonitor.com/api/v3-3/getting-started/
- Lists/webhooks: https://www.campaignmonitor.com/api/lists/ · https://www.campaignmonitor.com/api/webhooks/
- Transactional: https://www.campaignmonitor.com/api/v3-3/transactional/
- Terms: https://www.campaignmonitor.com/legal/terms/
- Rate limiting is documented on getting-started (HTTP 429). Transactional routes have a separate, stricter limit than the general API.
- Subscriber emails, custom fields, and webhook payloads are PII. The **customer** is the controller. StudioQuack does not accept Campaign Monitor / Marigold ToS.
- `ConsentToTrack` / `ConsentToSendSms`, CAN-SPAM, CASL, and GDPR lawful basis are the customer’s duty. `Resubscribe: true` can re-permission previously unsubscribed people — do not default it on.

## Implementation notes

- JDK HTTP only. Host `api.createsend.com`. Basic: `Authorization: Basic base64(apiKey:x)`. OAuth: `Authorization: Bearer`.
- Prefer JSON paths. Transactional family is JSON-only.
- Timeouts, 256 KiB request / `maxResponseBytes` response, retry `429`/`5xx` max 3.
- 4xx/5xx → `{ error }` on OUTPUT. Never log tokens or subscriber emails.
