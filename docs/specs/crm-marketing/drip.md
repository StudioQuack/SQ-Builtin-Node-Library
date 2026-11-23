# Drip

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/drip/`
- **Researched:** 2026-08-20
- **Official API:** Drip REST API v2 (`https://api.getdrip.com/v2`)
- **Docs:** https://developer.drip.com/ · auth: https://developer.drip.com/#authentication
- **Auth:** HTTP Basic (API token as username, empty password) **or** OAuth 2.0 Bearer — secret bindings: `apiToken`, `clientSecret`, `accessToken`

## Scope

One node for Drip ecommerce email automation: subscribers, tags, events, email-series campaign subscribe/unsubscribe, workflow activate, and list webhooks. JSON over HTTPS with SNI. Every resource URL includes `{accountId}`. A `User-Agent` identifying the StudioQuack integration is required. `trigger.listen` POSTs `/v2/{accountId}/webhooks`. Do not scrape getdrip.com.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `subscriber.upsert` | POST | `/v2/{accountId}/subscribers` | Create or update (`subscribers: [{ email, … }]`) |
| `subscriber.get` | GET | `/v2/{accountId}/subscribers/{idOrEmail}` | Fetch one |
| `subscriber.unsubscribe` | POST | `/v2/{accountId}/subscribers/{idOrEmail}/unsubscribe_all` | Global unsubscribe |
| `subscriber.delete` | DELETE | `/v2/{accountId}/subscribers/{idOrEmail}` | Delete |
| `tag.apply` | POST | `/v2/{accountId}/tags` | Apply one tag (`email` + `tag`) |
| `tag.remove` | DELETE | `/v2/{accountId}/subscribers/{idOrEmail}/tags/{tag}` | Remove tag |
| `event.record` | POST | `/v2/{accountId}/events` | Record a custom event |
| `campaign.subscribe` | POST | `/v2/{accountId}/campaigns/{campaignId}/subscribers` | Subscribe to an Email Series Campaign |
| `campaign.unsubscribe` | POST | `/v2/{accountId}/subscribers/{idOrEmail}/remove?campaign_id=` | Remove from one series (omit `campaign_id` = all series) |
| `workflow.activate` | POST | `/v2/{accountId}/workflows/{workflowId}/activate` | Activate a workflow (204) |
| `trigger.listen` | POST | `/v2/{accountId}/webhooks` | Event callbacks to ingress |

Workflow pause exists on the API (`POST …/workflows/{id}/pause`) but is **not** a catalog verb — omit.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `subscriber.upsert` | no | Chooses the row above |
| `auth` | select | yes | `apiToken` | no | `apiToken` or `oauth2` |
| `apiToken` | password | if apiToken | | yes | Personal API token (Basic username) |
| `accountId` | string | yes | | no | Drip account id (path) |
| `clientId` | string | if oauth2 | | no | OAuth app |
| `clientSecret` | password | if oauth2 | | yes | OAuth secret |
| `accessToken` | password | if oauth2 | | yes | Bearer |
| `userAgent` | string | no | `StudioQuack-Drip (+https://studioquack.com)` | no | Required by Drip |
| `timeoutMs` | number | no | `30000` | no | Read timeout |
| `maxResponseBytes` | number | no | `1048576` | no | Response cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo token |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = subscriber.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Subscriber email |
| `userId` | string | no | Your user id |
| `firstName` / `lastName` | string | no | Name fields if still accepted on current body |
| `customFields` | object | no | Minimize PII |
| `tags` | string[] | no | Tags to apply |
| `status` | string | no | e.g. `active` |
| `timeZone` | string | no | Olson TZ |
| `euConsent` | string | EU | `granted` / `denied` |
| `euConsentMessage` | string | with consent | Copy the person saw |

### `operation = subscriber.get` / `subscriber.unsubscribe` / `subscriber.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `idOrEmail` | string | yes | Id, email, or visitor UUID |

### `operation = tag.apply`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Subscriber email |
| `tag` | string | yes | Tag string |

### `operation = tag.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `idOrEmail` | string | yes | Subscriber |
| `tag` | string | yes | Path-encoded tag |

### `operation = event.record`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Subscriber email |
| `action` | string | yes | Event name |
| `properties` | object | no | Event properties |
| `occurredAt` | string | no | ISO timestamp |

### `operation = campaign.subscribe`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignId` | string | yes | Email Series Campaign id |
| `email` | string | yes | Subscriber |
| `doubleOptin` | boolean | no | Override campaign default |
| `euConsent` | string | EU | GDPR |
| `reactivateIfRemoved` | boolean | no | Default true on API — do not silently re-permission without operator intent |

### `operation = campaign.unsubscribe`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `idOrEmail` | string | yes | Subscriber |
| `campaignId` | string | no | If omitted, remove from **all** series |

### `operation = workflow.activate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `workflowId` | string | yes | Workflow id |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `postUrl` | string | yes | HTTPS ingress |
| `events` | string[] | no | e.g. `subscriber.created`, `subscriber.subscribed_to_campaign`; default = all except received_email |
| `includeReceivedEmail` | boolean | no | Default false |

## Output fields (by operation)

### `operation = subscriber.upsert` / `subscriber.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 200/201 | OK |
| `subscribers` | object[] | success | Log ids only |

### `operation = subscriber.unsubscribe` / `subscriber.delete` / `tag.remove` / `workflow.activate`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 200/204 | Empty or 204 |

### `operation = tag.apply`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 201 | Empty `{}` |

### `operation = event.record` / `campaign.subscribe` / `campaign.unsubscribe`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 201/200 | OK |
| `subscribers` | object[] | subscribe | If returned |

### `operation = trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `webhookId` | string | 201 | Webhook id |

Failures: `{ error }`.

## Legal

- Docs: https://developer.drip.com/
- Rate limit: **3,600 requests/hour** (~1 rps sustained). HTTP 429 + `X-RateLimit-*`. Batch endpoints have extra limits.
- Subscribers and events are PII. The **customer** is the controller. StudioQuack does not accept Drip ToS.
- `eu_consent` / CAN-SPAM / CASL / GDPR are the customer’s duty. Do not default `reactivate_if_removed` on in the UI if the catalog default would re-opt people.
- Official API only.

## Implementation notes

- JDK HTTP only. Basic `apiToken:` or Bearer. JSON body (not query) for POST/PUT.
- Always send `User-Agent` and `Content-Type: application/json`.
- Timeouts 10s/`timeoutMs`; request ≤ 256 KiB; response ≤ `maxResponseBytes`.
- Retry `429`/`5xx` max 3. Other 4xx → `{ error }`.
- Never log tokens or emails.
