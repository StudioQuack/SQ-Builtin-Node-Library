# Cal.com

In-process StudioQuack builtin (`templateId` `ba1d542d-81cd-5174-9b7a-14e2e924d307`, override with `SQ_TEMPLATE_ID_CAL_COM`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Cal.com API v2** at `https://api.cal.com/v2`. Auth: `header` via `api_key` (`Authorization: Bearer`).

Provider docs:

- [API v2 introduction](https://cal.com/docs/api-reference/v2/introduction)
- [v1 → v2 differences](https://cal.com/docs/api-reference/v2/v1-v2-differences) (still shows `cal-api-version: 2024-08-13` as the migration baseline)
- [Create a booking](https://cal.com/docs/api-reference/v2/bookings/create-a-booking) (`2026-02-25`)
- [Get all bookings](https://cal.com/docs/api-reference/v2/bookings/get-all-bookings) (`2026-05-01`)
- [Get all event types](https://cal.com/docs/api-reference/v2/event-types/get-all-event-types) (`2024-06-14`)

Terms: [https://cal.com/terms](https://cal.com/terms)

API v1 shut down 2026-04-08. Self-host uses a different host; OfficialApiNode is pinned to `api.cal.com`.

## `cal-api-version` header

Every HTTP call sends `cal-api-version` via `extraHeaders`. The spec-level default is `2024-08-13`. Current OpenAPI **requires a different date per endpoint**; op-level headers override:

| operation | `cal-api-version` |
|---|---|
| `create-booking`, `get-booking`, `reschedule-booking`, `cancel-booking` | `2026-02-25` |
| `list-bookings` | `2026-05-01` |
| `list-event-types`, `get-event-type` | `2024-06-14` |

Omitting the header, or sending a value the endpoint does not list, falls back to an older schema.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. Attachments are `file`. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `create-booking` | POST | `/bookings` | Official HTTP |
| `get-booking` | GET | `/bookings/{uid}` | Official HTTP |
| `list-bookings` | GET | `/bookings` | Official HTTP |
| `reschedule-booking` | POST | `/bookings/{uid}/reschedule` | Official HTTP |
| `cancel-booking` | POST | `/bookings/{uid}/cancel` | Official HTTP |
| `list-event-types` | GET | `/event-types` | Official HTTP |
| `get-event-type` | GET | `/event-types/{id}` | Official HTTP |

### `create-booking` and custom questions

Many event types require **custom booking fields** (`notes`, `title`, phone fields, etc.). The Cal.com API expects answers in **`bookingFieldsResponses`** (slug → value). If a required field is missing, Cal.com often returns an opaque `responses - {slug}error_required_field` message ([upstream issue](https://github.com/calcom/cal.com/issues/24851)); OfficialApiNode rewrites that to name `bookingFieldsResponses.<slug>`.

**Flow for agents:** `list-event-types` or `get-event-type` → read `bookingFields` / required slugs → `create-booking` with `start`, `attendee`, `eventTypeId` (or `eventTypeSlug` + `username`/`teamSlug`), and **`bookingFieldsResponses`** for every required slug.

Inbound Cal.com booking webhooks (created/cancelled/rescheduled) are **BPMN message events** only — this node does not define an HTTP `listen` operation; do not pass `operation: listen`.

## Configuration parameters

`operation` (select), `api_key` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
