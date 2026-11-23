# Autopilot

Official Autopilot REST v1 (legacy). Host `https://api2.autopilothq.com/v1` only. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** http://developers.autopilothq.com/getting-started/ · Apiary https://github.com/autopilotdev/autopilotdev.github.io/blob/master/_api_docs/apiary.md
- **Base:** `https://api2.autopilothq.com/v1`
- **Auth:** header `autopilotapikey` (`api_key`). Customer owns the account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `contact.upsert` | POST | `/contact` |
| `contact.get` | GET | `/contact/{contact_id_or_email}` |
| `contact.list` | GET | `/contacts` |
| `contact.delete` | DELETE | `/contact/{contact_id_or_email}` |
| `list.create` | POST | `/list` |
| `list.list` | GET | `/lists` |
| `list.add` | POST | `/list/{list_id}/contact/{contact_id_or_email}` |
| `list.remove` | DELETE | `/list/{list_id}/contact/{contact_id_or_email}` |
| `list.contains` | GET | `/list/{list_id}/contact/{contact_id_or_email}` |
| `list.contacts` | GET | `/list/{list_id}` |
| `journey.add` | POST | `/trigger/{trigger_id}/contact/{contact_id_or_email}` |
| `trigger.listen` | ingress | BPMN message event (no socket) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| auth secret | password | yes | Provider credential |
| `timeout_seconds` | number | no | Default 25 |

**Do not point this node at Ortto.** Autopilot rebranded; the Ortto API is a different surface. Shared production host: `api2.autopilothq.com`. Rate limit 5 rps (Apiary). `journey.add` uses the official trigger path `POST /trigger/{trigger_id}/contact/{contact_id_or_email}`. `trigger.listen` is ingress (REST Hooks `POST /hook` is configured in Autopilot; inbound delivery uses a BPMN message event).

## Ports

`input` / `output` only. Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- Autopilot API ToS as published on developers.autopilothq.com. Deleting a contact is permanent.
- Official API only. Do not scrape the vendor dashboard.
