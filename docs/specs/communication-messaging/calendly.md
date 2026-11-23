# Calendly

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/calendly/`
- **Researched:** 2026-08-20
- **Official API:** Calendly API v2 (`https://api.calendly.com`)
- **Docs:** https://developer.calendly.com/api-docs · webhooks https://developer.calendly.com/receive-data-from-scheduled-events-in-real-time-with-webhook-subscriptions · auth https://developer.calendly.com/when-to-choose-between-personal-access-tokens-and-oauth · scopes https://developer.calendly.com/scopes
- **Auth:** oauth2 **or** personal access token — secret bindings: `access_token` (Bearer) and, for listen, `webhook_signing_key`

## Scope

One node for **Calendly API v2**. API v1 and its webhooks were discontinued (migration target May 2025; treat v1 as dead). Authenticate with `Authorization: Bearer` using either:

- a **personal access token** (Integrations → API & Webhooks) for internal/private automation, or
- **OAuth 2.0** (`https://auth.calendly.com/oauth/authorize` + token endpoint) for multi-customer apps.

New PATs and OAuth apps are **scoped**. Needed here: `users:read`, `event_types:read`, `scheduled_events:read`, `scheduled_events:write`, `webhooks:read`, `webhooks:write` (exact names per https://developer.calendly.com/scopes). Legacy unscoped tokens may still work until refreshed.

**Webhooks fire only for Calendly-managed bookings** (Calendly booking pages / official embed). They do **not** cover meetings created only on an external calendar. Reschedule = `invitee.canceled` **plus** `invitee.created` (no dedicated rescheduled event).

`listen` is **this node**: `POST /webhook_subscriptions` pointing at the operator’s StudioQuack message-event ingress URL. Incoming Calendly POSTs are verified with HMAC-SHA256 over `timestamp.payload` using the subscription `signing_key` (`Calendly-Webhook-Signature: t=…,v1=…`). There is no sibling trigger node.

Do not implement the paid **Scheduling API** `POST /invitees` (create booking) in v1 of this node (not in the catalog seed).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `list-event-types` | GET | `/event_types` | Event types for `user` or `organization` URI |
| `get-event` | GET | `/scheduled_events/{uuid}` | One scheduled event |
| `list-events` | GET | `/scheduled_events` | Events for user or organization |
| `cancel-event` | POST | `/scheduled_events/{uuid}/cancellation` | Cancel; optional `reason` (max 10 000) |
| `list-invitees` | GET | `/scheduled_events/{uuid}/invitees` | Invitees (names, emails, answers — PII) |
| `create-invitee-no-show` | POST | `/invitee_no_shows` | Mark invitee no-show. Body `{ "invitee": "<invitee URI>" }` |
| `listen` | POST/GET/DELETE | `/webhook_subscriptions` | Subscribe / list / delete webhooks |

Base: `https://api.calendly.com`. UUIDs may be passed as raw UUID or as a full `https://api.calendly.com/...` URI; strip to UUID for path segments.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `list-event-types` | no | Chooses the row above |
| `auth` | select | yes | `personal_access_token` | no | `personal_access_token` \| `oauth2` |
| `access_token` | password | yes | empty | yes | PAT or OAuth access token. Header `Authorization: Bearer`. Never on INPUT |
| `client_id` | string | if oauth2 refresh | empty | no | OAuth app id (refresh path) |
| `client_secret` | password | if oauth2 refresh | empty | yes | OAuth client secret |
| `refresh_token` | password | if oauth2 refresh | empty | yes | Stored refresh token |
| `organization_uri` | string | no | empty | no | Default `https://api.calendly.com/organizations/{id}` |
| `user_uri` | string | no | empty | no | Default user URI; if empty, `GET /users/me` once per invoke that needs it |
| `webhook_signing_key` | password | `listen` verify | empty | yes | HMAC key for inbound `Calendly-Webhook-Signature`. Prefer supplying on create so the operator already holds it |
| `timeout_seconds` | number | no | `25` | no | REST timeout |

OAuth token refresh (when `auth=oauth2` and the access token is expired) uses the official token URL; persist the new access token via the daemon secret store — do not write tokens to OUTPUT.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo Bearer or signing key. Invitee PII on success only |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = list-event-types`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `scope` | string | no | `user` (default) \| `organization` |
| `user` | string | user scope | User URI; else config / `/users/me` |
| `organization` | string | org scope | Organization URI |
| `active` | boolean | no | Filter active event types if documented |
| `count` | number | no | Page size |
| `page_token` | string | no | Pagination |

### `operation = get-event`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_uuid` | string | yes | Scheduled event UUID or URI |

### `operation = list-events`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `scope` | string | no | `user` \| `organization` |
| `user` / `organization` | string | as scope | Same as list-event-types |
| `status` | string | no | e.g. `active` \| `canceled` |
| `min_start_time` | string | no | ISO-8601 |
| `max_start_time` | string | no | ISO-8601 |
| `invitee_email` | string | no | Filter if documented on live GET |
| `count` | number | no | Page size |
| `page_token` | string | no | Pagination |

### `operation = cancel-event`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_uuid` | string | yes | UUID or URI |
| `reason` | string | no | Max 10 000 characters |

### `operation = list-invitees`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_uuid` | string | yes | |
| `status` | string | no | `active` \| `canceled` |
| `email` | string | no | Filter |
| `count` | number | no | |
| `page_token` | string | no | |

### `operation = create-invitee-no-show`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `invitee` | string | yes | Canonical invitee URI `https://api.calendly.com/scheduled_events/{event}/invitees/{invitee}` |

Scopes page also lists `POST /invitees/{uuid}/no_show`. Implement **`POST /invitee_no_shows`** (create Invitee No Show resource) as documented by the create-invitee-no-show reference and clients. If that path 404s at ship time, try the scoped alternate and record the live path in the node README.

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `listen_mode` | string | no | `subscribe` (default) \| `unsubscribe` \| `list` |
| `subscriber_url` | string | subscribe | HTTPS StudioQuack message-event ingress URL |
| `events` | array | subscribe | Default `["invitee.created","invitee.canceled"]`. Allowed: those plus `routing_form_submission.created`. **Do not** invent a rescheduled event name |
| `scope` | string | subscribe | `user` \| `organization` |
| `user` / `organization` | string | as scope | Required URIs |
| `signing_key` | string | no | Optional; if omitted Calendly returns one — copy to secret store, never to logs. Config `webhook_signing_key` used to **verify** inbound |
| `webhook_uuid` | string | unsubscribe | Subscription UUID or URI |

Inbound verification (engine ingress, not the REST handler): parse `Calendly-Webhook-Signature`; HMAC-SHA256(`signing_key`, `timestamp + "." + raw_body`); constant-time compare to `v1`. Reject stale timestamps.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `resource` | object | get / cancel / no-show / subscribe | Provider `resource` |
| `collection` | array | list ops | Provider `collection` |
| `pagination` | object | list | `count`, `next_page`, `next_page_token`, `previous_page_token` |
| `uri` | string | when present | Canonical URI |
| `error` | string | failure | No token. 401/403 scope or plan (API is paid-tier) |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After`. Exact RPM is **unverified** on the JS Stoplight page (third-party reports cite ~60/min) |

## Legal

- Customer Terms: https://calendly.com/legal/customer-terms-conditions
- Developer Policy: https://calendly.com/legal/developer-policy (no bypass of access controls; disclose data use; breach notice to support+developer@calendly.com)
- DPA / privacy: https://calendly.com/legal
- **Invitees are personal data** (name, email, timezone, custom questions, location, payment). GDPR/CCPA: operator is controller; minimize fields; do not log invitee bodies.
- Webhooks: only Calendly-managed bookings. Do not claim coverage of all calendar events.
- PHI: Calendly is not a healthcare API; do not put ePHI in event questions unless a BAA exists with Calendly.

## Implementation notes

- JDK `HttpClient` only. `Authorization: Bearer {access_token}`, `Content-Type: application/json`, `User-Agent: StudioQuack/1.0`. TLS verified. `Redirect.NEVER`.
- Resolve `/users/me` when `user_uri` empty (`resource.uri` + `current_organization`).
- Pagination: pass `page_token`; do not silently walk all pages in one invoke (cap one page).
- Response cap **1_000_000**. Timeout 25 s.
- Redact Bearer, signing key, refresh token. Logs: operation, HTTP status, collection length — not emails.
- **Do not build:** API v1, `POST /invitees` (Scheduling API create), routing-form CRUD (listen may still subscribe to `routing_form_submission.created`), availability, shares, data compliance export APIs, a separate webhook node.
