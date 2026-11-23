# Braze

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/braze/`
- **Researched:** 2026-08-20
- **Official API:** Braze **REST API** (unversioned paths on a **cluster-specific** host). Default documented rate limit **250,000 requests/hour** for most endpoints; several collections have tighter limits. Auth: `Authorization: Bearer` REST API key with **endpoint-scoped permissions**.
- **Docs:** API overview https://www.braze.com/docs/api/basics · `/users/track` https://www.braze.com/docs/api/endpoints/user_data/post_user_track/ · Campaign trigger https://www.braze.com/docs/api/endpoints/messaging/send_messages/post_send_triggered_campaigns · Canvas trigger https://www.braze.com/docs/api/endpoints/messaging/send_messages/post_send_triggered_canvases · Catalog item upsert https://www.braze.com/docs/api/endpoints/catalogs/catalog_items/synchronous/put_update_catalog_item/ · Subscriptions https://www.braze.com/docs/api/endpoints/subscription_groups/post_update_user_subscription_group_status · Messaging index https://www.braze.com/docs/api/endpoints/messaging
- **Auth:** `api_key` — secret binding: `restApiKey`. Nested `app_id` on events/purchases/message objects belongs in INPUT payloads, not config.

## Scope

One node for Braze user data, messaging, catalogs, and subscription groups. The operator **must** set `restEndpoint` to their cluster REST host from **Settings → APIs and Identifiers → API Keys** (e.g. `https://rest.iad-01.braze.com`, `https://rest.fra-01.braze.eu`, `https://rest.us-10.braze.com`, AU/ID/JP/KR hosts in the overview table). Do not call SDK hosts (`sdk.*`). Do not hard-code US-01.

`user.track` consumes **data points** per custom attribute; send **deltas only**. `/users/track` allows up to **75 combined** `attributes`/`events`/`purchases` objects per request (legacy accounts: up to 75 of each). Burst limit commonly **3,000 requests / 3 seconds** for data-point contracts.

`trigger.listen` is inbound: Braze **Webhook campaigns** or a Currents HTTP connector POST to StudioQuack ingress. There is no REST “create webhook subscription” on the user-data API; Currents is configured in the dashboard.

## Operations (`operation` config values)

| operation | HTTP | Path | Permission | When to use |
|---|---|---|---|---|
| `user.track` | POST | `/users/track` | `users.track` | Attributes, custom events, purchases |
| `user.delete` | POST | `/users/delete` | `users.delete` | GDPR/CCPA deletion by `external_ids` / `braze_ids` / aliases |
| `user.export` | POST | `/users/export/ids` | `users.export.ids` | Export profiles by ids (segment export is a different permission — out of catalog) |
| `campaign.trigger` | POST | `/campaigns/trigger/send` | `campaigns.trigger.send` | API-triggered campaign |
| `canvas.trigger` | POST | `/canvas/trigger/send` | `canvas.trigger.send` | API-triggered Canvas |
| `subscription.set` | POST | `/subscription/status/set` | `subscription.status.set` | Email/SMS/RCS group subscribe/unsubscribe (v1; v2 `/v2/subscription/status/set` optional) |
| `catalog.upsert` | PUT | `/catalogs/{catalog_name}/items/{item_id}` | `catalogs.replace_item` | Synchronous replace-or-create **one** item |
| `message.send` | POST | `/messages/send` | `messages.send` | Immediate message; body defined in the request |
| `trigger.listen` | inbound POST | StudioQuack ingress | n/a | Braze webhook / Currents HTTP payload |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `user.track` | no | Chooses the row above |
| `restEndpoint` | string | yes | | no | Cluster REST origin, no path (must match dashboard) |
| `restApiKey` | password | yes | | **yes** | Bearer REST key; permissions must include the verb |

Allowlisted IPs on the key are operator-side; the daemon egress IP must be allowlisted if the key requires it.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, resource }` or `{ error }` | Never echo the REST key |

FUNCTION ports: none. Catalog item values are JSON, not `file`. Email attachments on campaign trigger reference **URLs**, not platform `file` (Braze fetches the URL).

## Input fields (by operation)

### `operation = user.track`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `attributes` | object[] | one of attributes/events/purchases | User attribute objects; each needs one identifier |
| `events` | object[] | one of | Custom events (`name`, `time`, identifier) |
| `purchases` | object[] | one of | Purchases (`product_id`, `currency`, `price`, identifier) |
| `externalId` / `brazeId` / `email` / `phone` / `userAlias` | mixed | if not inside arrays | Convenience wrapper into one attributes object |

Identifier rules: at most one **primary** (`external_id`, `user_alias`, `braze_id`) per object; `email`/`phone` are secondary. Mixing a new primary with an existing email can **duplicate** profiles.

### `operation = user.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `externalIds` | string[] | one identifier set | Users to delete |
| `brazeIds` | string[] | one identifier set | Braze ids |
| `email` / `phone` aliases | mixed | as documented | Alias-only users |

### `operation = user.export`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `externalIds` / `brazeIds` / `emailAddress` | mixed | yes | Export selector |
| `fieldsToExport` | string[] | no | Minimize fields (PII) |

### `operation = campaign.trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignId` | string | yes | API-triggered campaign id |
| `recipients` | object[] | unless `broadcast` | Up to documented recipient cap; each one identifier |
| `broadcast` | boolean | if no recipients | Must be `true` to send the dashboard segment; defaults false |
| `triggerProperties` | object | no | Canvas/campaign personalization |
| `sendId` | string | no | Send identifier |
| `attachments` | object[] | no | `{ file_name, url }` — Braze pulls URL; max 2 MB file |

### `operation = canvas.trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `canvasId` | string | yes | Canvas id |
| `recipients` | object[] | unless broadcast | Max 50 objects |
| `broadcast` | boolean | if no recipients | Entire Canvas audience |
| `context` | object | no | Canvas context; per-recipient context overrides |
| `audience` | object | no | Additional connected-audience filters |

### `operation = subscription.set`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscriptionGroupId` | string | yes | Group id |
| `subscriptionState` | string | yes | `subscribed` or `unsubscribed` |
| `externalId` | string[] | one of | Up to 50 |
| `email` | string[] | one of | Up to 50 |
| `phone` | string[] | one of | E.164, up to 50 |
| `useDoubleOptInLogic` | boolean | no | SMS groups only |

Prefer `/v2/subscription/status/set` with `subscription_groups[]` when updating multiple groups.

### `operation = catalog.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `catalogName` | string | yes | Catalog name |
| `itemId` | string | yes | Item id (letters, numbers, hyphen, underscore; ≤ 250 chars) |
| `item` | object | yes | Fields **already defined** on the catalog; do **not** send `id` in the body |
| Nested objects cannot use keys with `.` or `$`; max 50 nesting levels; item ≤ 5,000 characters. One item per request. Shared sync catalog-item limit **50 rpm**.

### `operation = message.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `externalUserIds` / `segmentId` / `audience` | mixed | yes | Target |
| `messages` | object | yes | Channel payloads (`email`, `android_push`, …) as documented on `/messages/send` |
| `campaignId` | string | no | Optional tracking campaign |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none on INPUT)* | | | Verify operator-configured shared secret if used |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP 200/201; Braze body often `{ "message": "success" }` |
| `resource` | object | success | Provider JSON (`message`, `errors` empty, export job ids) |
| `message` | string | success | Typically `success` |
| `event` | object | listen | Webhook/Currents payload |
| `error` | object | 4xx/5xx | `{ code, message, status, errors[] }` — no REST key, no emails |

## Legal

- Braze REST keys are created with **fixed** permission + IP allowlist (cannot be edited later). Messaging and deletion require matching permissions.
- Rate limits: default 250k/hour; `/users/track` burst and object caps above; catalog sync items 50 rpm shared. See https://www.braze.com/docs/api/api_limits (linked from basics).
- Users, emails, phones, purchases, and exports are PII. Deletion (`user.delete`) is the GDPR path — confirm the operator intends irreversible erase. **(a)** Minimize attributes and `fieldsToExport`. **(b)** Customer owns the Braze workspace and is the controller. **(c)** StudioQuack does not accept Braze ToS on their behalf. **(d)** Never log `restApiKey` or profile field values.
- Official-API-first: cluster `rest.*` hosts only. No dashboard scrape. Currents partner files (Avro to S3) are out of this node except HTTP connectors hitting `listen`.

## Implementation notes

- JDK `HttpClient` only. `Authorization: Bearer {restApiKey}`, `Content-Type: application/json`.
- Validate `restEndpoint` against the official cluster table (https origin, `rest.` host, no userinfo, no path).
- Timeouts: connect 10s, read 30s (export 60s). Body cap 1 MiB (`/users/track` well under 75 objects).
- Retry: 429/5xx, max 3. No retry on 400 (invalid fields / duplicate-profile mistakes).
- Errors: 4xx/5xx → `{ error }` on OUTPUT. Braze 201 with `"message": "success"` is success; `"message": "queued"` treat as success with `resource`.
- `user.export` may be asynchronous — return the export job/url fields Braze sends; if Braze returns a file URL, download only if the operator asked and store as `file` with a size cap.
