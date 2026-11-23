# Calendly

In-process StudioQuack builtin (`templateId` `24bb1620-0697-5b78-9a7a-4ab5e59ea159`, override with `SQ_TEMPLATE_ID_CALENDLY`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Calendly API v2** at `https://api.calendly.com`. Auth: `bearer` via `access_token`.

**`list-event-types` and `list-events`:** the API requires a `user` **or** `organization` query parameter (see OpenAPI). If you omit both, the node resolves your user URI with **GET `/users/me`** once per access token and sends `user=` automatically. Override by passing `user` or `organization` on INPUT.

Provider docs: [https://developer.calendly.com/api-docs](https://developer.calendly.com/api-docs)

Terms: [https://calendly.com/legal/api-terms](https://calendly.com/legal/api-terms)

Webhooks fire only for Calendly-managed bookings. Invitees are personal data. listen = webhook signing as BPMN message events.

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
| `list-event-types` | GET | `/event_types` | Official HTTP |
| `get-event` | GET | `/scheduled_events/{uuid}` | Official HTTP |
| `list-events` | GET | `/scheduled_events` | Official HTTP |
| `cancel-event` | POST | `/scheduled_events/{uuid}/cancellation` | Official HTTP |
| `list-invitees` | GET | `/scheduled_events/{uuid}/invitees` | Official HTTP |
| `create-invitee-no-show` | POST | `/invitee_no_shows` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Webhooks fire only for Calendly-managed bookings. Invitees are personal data. listen = webhook signing as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
