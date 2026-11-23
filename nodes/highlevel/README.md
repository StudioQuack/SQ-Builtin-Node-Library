# HighLevel

Official HighLevel Public API on `https://services.leadconnectorhq.com` (docs: marketplace.gohighlevel.com). Handler: `OfficialApiNode` via `api_spec.json`. Contacts, opportunities, tasks, calendar free-slots/book. **API v1 reached end-of-support 2025-12-31** — not called. SMS/email Conversations sends are out of this node.

- **Docs:** https://marketplace.gohighlevel.com/docs/
- **Base:** `https://services.leadconnectorhq.com`
- **API:** extraHeaders send `Version: {api_version}` (default **`v3`**, current named version on Get Contact; https://marketplace.gohighlevel.com/docs/ghl/contacts/get-contact/). Date version `2021-07-28` remains documented as legacy.
- **Auth:** Bearer `access_token` (location-scoped PIT `pit-…` or OAuth location token). Do not write CRM with a raw agency token.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `contact.upsert` | POST | `/contacts/upsert` |
| `contact.get` | GET | `/contacts/{contactId}` |
| `contact.list` | POST | `/contacts/search` |
| `contact.update` | PUT | `/contacts/{contactId}` |
| `contact.delete` | DELETE | `/contacts/{contactId}` |
| `opportunity.create` | POST | `/opportunities/` |
| `opportunity.get` | GET | `/opportunities/{id}` |
| `opportunity.list` | GET | `/opportunities/search` |
| `opportunity.update` | PUT | `/opportunities/{id}` |
| `opportunity.delete` | DELETE | `/opportunities/{id}` |
| `task.create` | POST | `/contacts/{contactId}/tasks` |
| `task.get` | GET | `/contacts/{contactId}/tasks/{taskId}` |
| `task.list` | GET | `/contacts/{contactId}/tasks` |
| `task.update` | PUT | `/contacts/{contactId}/tasks/{taskId}` |
| `task.delete` | DELETE | `/contacts/{contactId}/tasks/{taskId}` |
| `calendar.freeSlots` | GET | `/calendars/{calendarId}/free-slots` |
| `calendar.book` | POST | `/calendars/events/appointments` |
| `trigger.listen` | n/a | **ingress** — Marketplace app webhook URL → BPMN message event. Does not open a socket. |

Config `locationId` is sent as JSON/query `locationId` when INPUT `locationId` is blank.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer location token |
| `locationId` | string | no | Default sub-account; body/query fallback when INPUT is blank |
| `api_version` | string | no | Documented `Version` value (`2021-07-28`) |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Marketplace / product ToS: operator accepts HighLevel Marketplace terms.
- Contacts, phone, and email are PII. Customer owns the sub-account.
- Official API only. Do not scrape the agency dashboard.
