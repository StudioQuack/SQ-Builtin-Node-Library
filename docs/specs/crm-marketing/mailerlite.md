# MailerLite

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/mailerlite/`
- **Researched:** 2026-08-20
- **Official API:** MailerLite current REST (`https://connect.mailerlite.com/api`, date versioning via `X-Version`) — **not** Classic v2
- **Docs:** https://developers.mailerlite.com/docs/ · Subscribers: https://developers.mailerlite.com/api/subscribers · Classic: https://developers-classic.mailerlite.com/docs/request
- **Auth:** apiToken Bearer — secret binding: `api_token`

## Scope

One node. **New MailerLite accounts** must use `https://connect.mailerlite.com/api` (released 2022-03-22 with the current product). **MailerLite Classic** accounts use `https://api.mailerlite.com/api/v2/` (API v1/v2) and will be deprecated — Classic keys **do not** work on connect.mailerlite.com and vice versa. Catalog row says “REST v2”; implementer default is **current** API; set `api_edition=classic` only for remaining Classic accounts. JDK HTTP only (ignore official language SDKs).

## Operations (`operation` config values)

Paths below are **current** API. Classic v2 uses different paths (`/subscribers`, `/groups/{id}/subscribers`, …) — map 1:1 in the handler when `api_edition=classic`.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `subscriber.create` | POST | `/subscribers` | Upsert by email (201 created / 200 updated) |
| `subscriber.get` | GET | `/subscribers/{id or email}` | Fetch one |
| `subscriber.list` | GET | `/subscribers` | Cursor page; `filter[status]` |
| `subscriber.update` | PUT | `/subscribers/{id}` | Update; cannot reactivate unsubscribed/bounced/junk via API |
| `subscriber.delete` | DELETE | `/subscribers/{id}` | Soft-remove (re-subscribe keeps history). GDPR wipe = `POST /subscribers/{id}/forget` — not a separate catalog op; call from delete when `forget=true`. |
| `group.add` | POST | `/subscribers/{subscriber_id}/groups/{group_id}` | Assign to group |
| `group.remove` | DELETE | `/subscribers/{subscriber_id}/groups/{group_id}` | Unassign |
| `campaign.send` | POST | `/campaigns/{campaign_id}/schedule` | Schedule **or send immediately** (`delivery: instant`) |
| `trigger.listen` | POST | `/webhooks` | Register webhook |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `subscriber.get` | no | Chooses the row above |
| `api_edition` | select | yes | `current` | no | `current` (connect.mailerlite.com) \| `classic` (api.mailerlite.com/api/v2) |
| `api_token` | password | yes | empty | yes | Bearer token (current) or Classic API key |
| `x_version` | string | no | empty | no | Current API only: `X-Version: YYYY-MM-DD` to pin. Empty = latest. |
| `timeout_seconds` | number | no | `25` | no | Floor 1 s. |

Classic header (verified): `X-MailerLite-ApiKey: {key}` — https://developers-classic.mailerlite.com/docs/authentication. Not Bearer.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include token |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = subscriber.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | RFC email |
| `fields` | object | no | `name`, `last_name`, custom fields — omission does not clear |
| `groups` | array | no | Group ids to add (not remove) |
| `status` | string | no | `active` \| `unsubscribed` \| `unconfirmed` \| `bounced` \| `junk` |
| `resubscribe` | boolean | no | Resubscribe previously unsubscribed |
| `opted_in_at` | string | no | `yyyy-MM-dd HH:mm:ss` consent evidence |
| `optin_ip` | string | no | Consent IP |

### `operation = subscriber.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscriber_id_or_email` | string | yes | Id or email |

### `operation = subscriber.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `filter_status` | string | no | `active` / `unsubscribed` / `unconfirmed` / `bounced` / `junk` |
| `limit` | number | no | Default 25 |
| `cursor` | string | no | `meta.next_cursor` |
| `include` | string | no | `groups` |

### `operation = subscriber.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscriber_id` | string | yes | Id |
| `fields` | object | no | Field map |
| `groups` | array | no | **Replaces** group membership (unlisted groups removed) |
| `status` | string | no | Cannot reactivate bounced/junk/unsubscribed via API |

### `operation = subscriber.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscriber_id` | string | yes | Id |
| `forget` | boolean | no | `true` → `POST /subscribers/{id}/forget` (GDPR, 30-day wipe) |

### `operation = group.add` | `group.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscriber_id` | string | yes | Subscriber |
| `group_id` | string | yes | Group |

### `operation = campaign.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | Ready campaign |
| `delivery` | string | yes | `instant` to send now; else `scheduled` / `timezone_based` / `smart_sending` |
| `schedule` | object | if scheduled | `{ date, hours, minutes, timezone_id }` as documented |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | no | Webhook name |
| `events` | array | yes | Event names from webhook docs |
| `url` | string | yes | StudioQuack message-event ingress |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/get | Subscriber / webhook id |
| `resource` | object | 2xx | `data` object (capped). Do not log email. |
| `items` | array | list | `data` array |
| `meta` | object | list | `next_cursor` / `prev_cursor` |
| `error` | string | failure | `message` + truncated `errors` |
| `http_status` | number | HTTP error | Including 422, 429 |
| `retry_after_seconds` | number | 429 | Header `Retry-After` (example 119) |

## Legal

- Current ToS: https://www.mailerlite.com/legal/terms-of-service
- If the operator collects subscribers **without** MailerLite forms, MailerLite ToS puts **abuse prevention** on that API key holder (anti-spam policy). Caller owns CAN-SPAM / CASL / GDPR.
- Rate limit: **120 requests / minute** global; import endpoints **5 / minute**. Headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After`.
- Customer owns the MailerLite account. Keys die if the creating user is deleted.
- Never log tokens or emails.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Current: `Authorization: Bearer {token}`, `Content-Type: application/json`, `Accept: application/json`, optional `X-Version`.
- Classic: different host + key header — reject mixed edition/host.
- 401 `{ "message": "Unauthenticated." }`. 422 validation. 204 on delete.
- Response cap **1_000_000** bytes. Timeout default 25 s.
- 429 → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry inside the handler.
- Logs: operation, edition, http_status, sizes — never emails or tokens.

Classic v2: accounts created **before March 2022** only (Classic getting-started). Current vs Classic field names differ (`groups` vs Classic group ids). Do not send Bearer to Classic or `X-MailerLite-ApiKey` to connect.mailerlite.com.
