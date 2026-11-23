# Zoom

In-process StudioQuack builtin (`templateId` `2ab9bdcf-69bd-5fa8-8661-f413e8c182a7`, override with `SQ_TEMPLATE_ID_ZOOM`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Zoom Meeting API v2** at `https://api.zoom.us/v2`. Auth: `bearer` via `access_token`.

Provider docs: [https://developers.zoom.us/docs/api/meetings/](https://developers.zoom.us/docs/api/meetings/)

Terms: [https://developers.zoom.us/docs/api/marketplace-app-review/](https://developers.zoom.us/docs/api/marketplace-app-review/)

OAuth user or Server-to-Server account token (JWT apps are retired). Server-to-Server cannot use the `me` keyword — pass a real userId or email (https://developers.zoom.us/docs/api/using-zoom-apis/). Recordings are personal data. listen = Marketplace event subscriptions as BPMN message events.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `create-meeting` | POST | `/users/{userId}/meetings` | Official HTTP |
| `get-meeting` | GET | `/meetings/{meetingId}` | Official HTTP |
| `list-meetings` | GET | `/users/{userId}/meetings` | Official HTTP |
| `update-meeting` | PATCH | `/meetings/{meetingId}` | Official HTTP |
| `delete-meeting` | DELETE | `/meetings/{meetingId}` | Official HTTP |
| `list-registrants` | GET | `/meetings/{meetingId}/registrants` | Official HTTP |
| `add-registrant` | POST | `/meetings/{meetingId}/registrants` | Official HTTP |
| `list-recordings` | GET | `/meetings/{meetingId}/recordings` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password; OAuth / Server-to-Server token minted outside StudioQuack), `timeout_seconds` (default 25).

## Security & legal

Official API only. OAuth user or Server-to-Server account token (JWT apps are retired). Server-to-Server cannot use the `me` keyword — pass a real userId or email (https://developers.zoom.us/docs/api/using-zoom-apis/). Recordings are personal data. listen = Marketplace event subscriptions as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
