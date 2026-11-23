# Calendly

- **Status:** planned
- **Catalog:** `docs/catalog/productivity-collaboration.md`
- **Folder when built:** `nodes/calendly/`
- **Researched:** 2026-08-20
- **Official API:** Calendly Scheduling API v2 (`https://api.calendly.com`)
- **Docs:** https://developer.calendly.com/api-docs
- **Auth:** oauth2 | pat — secret binding: `personal_access_token` or `oauth_client_secret` + `refresh_token`

## Scope

One node for Calendly scheduled events, invitees, event types, single-use booking links, organization membership, and **official** webhook subscriptions. The designer picks `operation`; the handler calls the matching REST path on `https://api.calendly.com` with `Authorization: Bearer`.

`listen` registers a webhook subscription. The callback URL must be **public HTTPS** (StudioQuack message-event ingress). Calendly delivers `invitee.created`, `invitee.canceled`, and optionally `routing_form_submission.created`. There is **no** first-class reschedule event: a reschedule inside Calendly fires **both** `invitee.canceled` and `invitee.created`.

**Calendar edits outside Calendly do not fire webhooks — still true as of 2026-08-20.** Official webhook types are only invitee created/canceled and routing-form submission ([webhooks overview](https://calendly.com/help/webhooks-overview), [developer webhook guide](https://developer.calendly.com/receive-data-from-scheduled-events-in-real-time-with-webhook-subscriptions)). There is no documented event for moves, deletes, or time changes made only in a connected calendar. Do not poll calendars to fake that signal.

Do not scrape calendly.com HTML. Do not call API v1 (retired). No unofficial SDK.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `listen` | POST | `/webhook_subscriptions` | Subscribe a public HTTPS URL to `invitee.created` / `invitee.canceled` (and optional routing-form event). Paid Calendly plan required for webhooks. |
| `get_event` | GET | `/scheduled_events/{uuid}` | Fetch one scheduled event by UUID (from URI path). |
| `list_events` | GET | `/scheduled_events` | Paginated events for a `user` or `organization` URI. |
| `cancel_event` | POST | `/scheduled_events/{uuid}/cancellation` | Cancel a booked event (not a single invitee on a group event). |
| `get_invitee` | GET | `/scheduled_events/{event_uuid}/invitees/{invitee_uuid}` | Invitee name, email, answers, cancel/reschedule URLs. |
| `list_invitees` | GET | `/scheduled_events/{event_uuid}/invitees` | Paginated invitees for one event. |
| `get_event_type` | GET | `/event_types/{uuid}` | One event type. |
| `list_event_types` | GET | `/event_types` | Event types for `user` and/or `organization`. |
| `create_once_link` | POST | `/scheduling_links` | Single-use booking URL (`max_event_count` must be `1`). Unused links expire after **90 days**. |
| `invite_org_user` | POST | `/organizations/{uuid}/invitations` | Email an organization invitation (admin). |
| `get_org_membership` | GET | `/organization_memberships/{uuid}` | One membership (role, user, organization). |

Base origin: `https://api.calendly.com`. Resource UUIDs may be passed as full URIs (`https://api.calendly.com/scheduled_events/{uuid}`); the handler strips to the trailing UUID.

**Dropped (not in current official surface for this node):** API v1 paths; a dedicated reschedule RPC (official FAQ: no reschedule endpoint — use invitee `reschedule_url`); `invitee.rescheduled` webhook (not in the documented `events` enum).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `list_events` | no | Chooses the row above |
| `auth_type` | select | yes | `pat` | no | `pat` \| `oauth2` |
| `personal_access_token` | password | if `pat` | empty | yes | PAT from Calendly → Integrations → API & Webhooks. Header `Authorization: Bearer`. Never on INPUT. |
| `oauth_client_id` | string | if `oauth2` | empty | no | OAuth application client id |
| `oauth_client_secret` | password | if `oauth2` | empty | yes | OAuth client secret |
| `refresh_token` | password | if `oauth2` | empty | yes | Refresh token; handler mints access tokens just-in-time. Never logged. |
| `organization_uri` | string | no | empty | no | Default `https://api.calendly.com/organizations/{uuid}` for list/listen/invite when input omits it |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens or webhook signing keys |

FUNCTION ports: none. No `file` ports.

## Input fields (by operation)

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callback_url` | string | yes | Public **HTTPS** URL (StudioQuack message-event ingress). HTTP is rejected by Calendly. |
| `events` | array | no | Subset of `invitee.created`, `invitee.canceled`, `routing_form_submission.created`. Default: created + canceled. |
| `scope` | string | yes | `user` or `organization` |
| `organization` | string | yes | Organization URI (required even for user scope — a user can belong to multiple orgs) |
| `user` | string | if `scope=user` | User URI |

### `operation = get_event`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_uuid` | string | yes | Scheduled-event UUID or full URI |

### `operation = list_events`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user` | string | one of user/organization | User URI |
| `organization` | string | one of user/organization | Organization URI; else config `organization_uri` |
| `min_start_time` | string | no | ISO-8601 lower bound on start |
| `max_start_time` | string | no | ISO-8601 upper bound on start |
| `status` | string | no | `active` \| `canceled` |
| `count` | number | no | Page size, max **100** (Calendly default 20) |
| `page_token` | string | no | Cursor from previous `pagination.next_page_token` |

### `operation = cancel_event`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_uuid` | string | yes | Scheduled-event UUID or URI |
| `reason` | string | no | Cancellation reason, max 10000 chars |

### `operation = get_invitee`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_uuid` | string | yes | Scheduled-event UUID |
| `invitee_uuid` | string | yes | Invitee UUID |

### `operation = list_invitees`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_uuid` | string | yes | Scheduled-event UUID |
| `status` | string | no | `active` \| `canceled` |
| `count` | number | no | Page size ≤ 100 |
| `page_token` | string | no | Pagination cursor |
| `email` | string | no | Filter by invitee email when documented |

### `operation = get_event_type`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_type_uuid` | string | yes | Event-type UUID or URI |

### `operation = list_event_types`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user` | string | no | User URI |
| `organization` | string | no | Organization URI; else config |
| `active` | boolean | no | Filter active types |
| `count` | number | no | Page size ≤ 100 |
| `page_token` | string | no | Pagination cursor |

### `operation = create_once_link`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `owner` | string | yes | Event-type URI (`https://api.calendly.com/event_types/{uuid}`) |
| `owner_type` | string | no | Must be `EventType` (default) |
| `max_event_count` | number | no | Must be **1** (only allowed value). Default 1 |

### `operation = invite_org_user`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `organization_uuid` | string | if config blank | Organization UUID or URI |
| `email` | string | yes | Invitee email |

### `operation = get_org_membership`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `membership_uuid` | string | yes | Membership UUID or URI |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `resource` | object | get / cancel / create_once_link / invite / membership | Provider `resource` object (event, cancellation, link, invitation, membership) |
| `booking_url` | string | `create_once_link` | `resource.booking_url` |
| `collection` | array | list ops | Provider collection |
| `pagination` | object | list ops | `count`, `next_page`, `next_page_token`, `previous_page`, `previous_page_token` |
| `webhook_uri` | string | `listen` | Created subscription URI |
| `signing_key` | string | `listen` | Returned once — treat as secret; **do not log**; operator stores it to verify `Calendly-Webhook-Signature` on ingress |
| `error` | string | failure | Actionable, tokens redacted to `***` |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` if present; else 60 on 429, 30 on 5xx/timeout |

## Legal

- Developer portal: https://developer.calendly.com/api-docs
- Webhooks: https://developer.calendly.com/receive-data-from-scheduled-events-in-real-time-with-webhook-subscriptions
- Help: https://calendly.com/help/webhooks-overview, https://calendly.com/help/calendly-api-overview
- FAQ (cancel vs reschedule, PAT vs OAuth, 90-day unused single-use links): https://developer.calendly.com/frequently-asked-questions
- Terms: https://calendly.com/pages/terms (also referenced as API ToS in the OpenAPI `termsOfService` field)
- Privacy: operator is controller of invitee name/email/timezone/custom-question answers leaving the sandbox toward Calendly.
- **Webhooks:** Calendly user must be on a **paid** plan (Standard / Teams / Enterprise per FAQ). GET/POST on many v2 endpoints work on Free except Enterprise-only activity-log and delete-event-data endpoints (out of this node).
- **Quota:** HTTP 429 with rate-limit headers. Exact per-minute numbers are served from the Stoplight viewer and were **not extractable as static HTML on 2026-08-20**. Implementer must read live docs; third-party summaries (60/min most paid tiers, 120/min Enterprise) are **unverified** against official HTML. Honor `Retry-After` / `X-RateLimit-*`. Do not tight-loop.
- PHI: do not put health data in event descriptions or custom questions unless the customer’s Calendly + BAA posture covers it.
- Official-API-first: no scrape.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `Authorization: Bearer {token}`, `Content-Type: application/json` on POST, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Empty token → `{ error: "Missing Calendly credentials. Bind personal_access_token or OAuth refresh." }` — no HTTP.
- OAuth: refresh against Calendly’s published token URL; access token stack-local only.
- UUID extraction: if input looks like `https://api.calendly.com/.../{uuid}`, use the last path segment.
- Response body cap **1_000_000** bytes. Timeout default 25 s (floor 1).
- Pagination: one page per invoke. Return `pagination.next_page_token`; do not auto-walk pages.
- `listen`: POST body `{ url, events, organization, scope, user? }`. Persist `signing_key` only in the secret store. Ingress verifies `Calendly-Webhook-Signature` (`t=` timestamp, `v1=` HMAC) over the **raw** body.
- 429 → `{ error, http_status: 429, retry_after_seconds }` (header or 60). 5xx / timeout → 30. Other 4xx → truncated body (240 chars), secrets `***`.
- Do not auto-retry inside the handler except OAuth token refresh once on 401.
- Logs: operation, HTTP status, collection length, UUID **suffix** — never PAT, refresh token, signing key, invitee email, or webhook payload PII.
