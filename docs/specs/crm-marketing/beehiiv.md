# Beehiiv

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/beehiiv/`
- **Researched:** 2026-08-20
- **Official API:** beehiiv **REST API v2** (`https://api.beehiiv.com/v2`, OpenAPI 3.1 `public_v2` / `info.version` 1.0.0 on subscription create). OAuth2 authorization-code is available for multi-publication apps; API keys remain valid as Bearer tokens.
- **Docs:** https://developers.beehiiv.com/ · Index https://developers.beehiiv.com/llms.txt · Create subscription https://developers.beehiiv.com/api-reference/subscriptions/create · Webhooks https://developers.beehiiv.com/webhooks · Create webhook https://developers.beehiiv.com/api-reference/webhooks/create · Rate limits https://developers.beehiiv.com/welcome/rate-limiting.md · OpenAPI https://developers.beehiiv.com/openapi.json
- **Auth:** Bearer `apiToken` (secret) and/or OAuth (`clientId` / `clientSecret` secrets). Header `Authorization: Bearer`.

## Scope

One node for beehiiv publications: subscriptions, posts, publication metadata, and webhooks. Publication ids are prefixed `pub_…`. Subscription ids are prefixed. Webhooks are **Scale plan and above**. Delivery is via **Svix** (`svix-id`, `svix-timestamp`, `svix-signature`). Offset pagination is deprecated (max 100 pages); use **cursor** pagination.

Do not scrape the beehiiv editor. Do not call unpublished v1 hosts if the OpenAPI only lists `/v2`.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `subscription.create` | POST | `/v2/publications/{publicationId}/subscriptions` | Add a subscriber (`subscriptions:write`) |
| `subscription.get` | GET | `/v2/publications/{publicationId}/subscriptions/{id}` or `.../subscriptions/by_email/{email}` | Fetch by id or email |
| `subscription.update` | PATCH/PUT | `/v2/publications/{publicationId}/subscriptions/{id}` (and by-email update) | Update status/fields |
| `subscription.delete` | DELETE | `/v2/publications/{publicationId}/subscriptions/{id}` | Delete / GDPR-style removal |
| `post.list` | GET | `/v2/publications/{publicationId}/posts` | List posts (`posts:read`) |
| `post.get` | GET | `/v2/publications/{publicationId}/posts/{id}` | One post |
| `publication.get` | GET | `/v2/publications/{publicationId}` | Publication metadata (`publications:read`) |
| `webhook.create` | POST | `/v2/publications/{publicationId}/webhooks` | Register endpoint + `event_types` (`webhooks:write`) |
| `trigger.listen` | inbound POST | StudioQuack ingress | Receive Svix-signed events |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `subscription.create` | no | Chooses the row above |
| `apiToken` | password | yes unless OAuth token stored | | **yes** | Bearer API key from beehiiv developer settings |
| `publicationId` | string | yes | | no | `pub_` prefixed id (config default; INPUT may override) |
| `clientId` | string | OAuth apps | | no | OAuth client |
| `clientSecret` | password | OAuth apps | | **yes** | OAuth secret |
| `webhookSecret` | password | `trigger.listen` | | **yes** | Svix signing secret from Settings → Webhooks |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, resource }` or `{ error }` | Never echo Bearer or Svix secret |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = subscription.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Subscriber email |
| `reactivateExisting` | boolean | no | Only if the person is **knowingly** resubscribing |
| `sendWelcomeEmail` | boolean | no | Welcome email |
| `utmSource` / `utmMedium` / `utmCampaign` | string | no | Attribution |
| `referringSite` | string | no | Referrer |
| `customFields` | object[] | no | `{ name, value }` for **existing** custom fields |
| `tier` | string | no | `free` or `premium` |
| `doubleOptOverride` | string | no | Override publication double-opt |

### `operation = subscription.get | subscription.update | subscription.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscriptionId` | string | get/update/delete unless email | Prefixed subscription id |
| `email` | string | get/update-by-email | Alternate key |
| `status` | string | update | Subscription status when changing state |
| `customFields` | object[] | update | Field patches |

### `operation = post.list | post.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `postId` | string | get | Post id |
| `limit` | number | list | Page size |
| `cursor` | string | list | Cursor (do not use deep offset pages) |
| `status` | string | list | Filter (draft/confirmed/archived as documented) |

### `operation = publication.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none beyond config `publicationId`)* | | | Optional INPUT override `publicationId` |

### `operation = webhook.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | HTTPS ingress URL |
| `eventTypes` | string[] | yes | Official enums: `subscription.created`, `subscription.confirmed`, `subscription.deleted`, `post.sent`, `post.updated`, `post.scheduled`, newsletter-list and podcast events, … |
| `description` | string | no | Label |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none on INPUT)* | | | Verify Svix headers, then emit `event` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP 200 |
| `resource` | object | CRUD | `{ data: { id, email, status, ... } }` |
| `data` | array | list | Posts or subscriptions |
| `id` | string | create | Prefixed resource id |
| `event` | object | listen | `{ uid, event_type, event_timestamp, data }` |
| `error` | object | 4xx/5xx/429 | `{ code, message, status }` — no token, no email |

## Legal

- beehiiv developer terms via the beehiiv account. Webhooks: Scale+ only (https://developers.beehiiv.com/webhooks).
- Rate limit: **180 requests per minute per organization**. Headers `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`. HTTP 429 → backoff.
- Newsletter emails are PII; premium/Stripe customer ids may appear. Double opt-in: honor publication settings unless `doubleOptOverride` is explicitly set **and** the operator has a lawful basis. **(a)** Minimize fields. **(b)** Customer owns the beehiiv publication and is the controller. **(c)** StudioQuack does not accept beehiiv ToS on their behalf. **(d)** Never log Bearer tokens, Svix secrets, or email addresses.
- Official-API-first: `api.beehiiv.com/v2` only.

## Implementation notes

- JDK `HttpClient` only. No beehiiv SDK. `Authorization: Bearer`.
- Timeouts: connect 10s, read 30s. Body cap 1 MiB.
- Retry: 429/5xx, max 3, honor `RateLimit-Reset`. Cap at 180 rpm.
- Pagination: cursor only.
- `trigger.listen`: verify Svix signatures with `webhookSecret` before accepting. Return 2xx quickly; process asynchronously if needed.
- Errors: 4xx/5xx → `{ error }` on OUTPUT. 401 = missing/invalid/expired token (official copy).
