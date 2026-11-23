# Streak

Official Streak REST **v1**. Handler: `OfficialApiNode` via `api_spec.json`. Boxes = deals. Customer owns the Gmail/Streak account. User-key auth has the same privileges as the Gmail user who created the key.

- **Docs:** https://streak.readme.io/docs/overview · https://streak.readme.io/docs/authentication · https://streak.readme.io/reference/create-a-box · https://streak.readme.io/reference/webhooks
- **API (2026-08-20):** `https://api.streak.com/api/v1`. Overview/auth updated 2025-06-09; webhooks reference updated 2026-01-06.
- **Base:** `https://api.streak.com/api/v1`
- **Auth:** HTTP Basic, API key as username and empty password. `OfficialApiNode` Basic encodes `{api_key}:{api_key}` (non-empty password required). Never on INPUT.

`trigger.listen` is `kind: ingress`. Pipeline webhooks (`POST /webhooks`) must target the StudioQuack message-event ingress URL. The node does not open a socket. Use a BPMN message start / catch / boundary event.

Email tracking and mail merge are unavailable via API (official exception). File contents via API only for `fileType=GMAIL_API`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `pipeline.list` | GET | `/pipelines` |
| `pipeline.get` | GET | `/pipelines/{pipeline_key}` |
| `box.create` | POST | `/pipelines/{pipeline_key}/boxes` |
| `box.get` | GET | `/boxes/{box_key}` |
| `box.list` | GET | `/pipelines/{pipeline_key}/boxes` |
| `box.update` | POST | `/boxes/{box_key}` |
| `box.delete` | DELETE | `/boxes/{box_key}` |
| `contact.create` | POST | `/contacts` |
| `contact.get` | GET | `/contacts/{key}` |
| `contact.update` | POST | `/contacts/{key}` |
| `field.update` | POST | `/boxes/{box_key}` |
| `task.create` | POST | `/boxes/{box_key}/tasks` |
| `comment.create` | POST | `/boxes/{box_key}/comments` |
| `file.get` | GET | `/boxes/{box_key}/files` |
| `trigger.listen` | ingress | Pipeline webhook → BPMN message event |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Streak user API key (Basic username) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer is the controller of mailbox CRM data. Gmail user-data policy still applies to data that originated in Gmail.
- Official REST only. HTTPS required (HTTP ignored officially).
