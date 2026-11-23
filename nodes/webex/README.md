# Webex

In-process StudioQuack builtin (`templateId` `f9235fc9-b202-5cd8-af08-0ef4b9d16015`, override with `SQ_TEMPLATE_ID_WEBEX`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Webex REST API v1** at `https://webexapis.com/v1`. Auth: `bearer` via `access_token`.

Provider docs: [https://developer.webex.com/docs/api/v1/messages](https://developer.webex.com/docs/api/v1/messages)

Terms: [https://www.cisco.com/c/en/us/about/legal/cloud-and-software/end_user_license_agreement.html](https://www.cisco.com/c/en/us/about/legal/cloud-and-software/end_user_license_agreement.html)

Bot vs user tokens differ (bots cannot list all rooms). listen = Webex webhooks as BPMN message events.

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
| `create-message` | POST | `/messages` | Official HTTP |
| `get-message` | GET | `/messages/{messageId}` | Official HTTP |
| `list-messages` | GET | `/messages` | Official HTTP |
| `update-message` | PUT | `/messages/{messageId}` | Official HTTP |
| `delete-message` | DELETE | `/messages/{messageId}` | Official HTTP |
| `create-meeting` | POST | `/meetings` | Official HTTP |
| `get-meeting` | GET | `/meetings/{meetingId}` | Official HTTP |
| `list-meetings` | GET | `/meetings` | Official HTTP |
| `update-meeting` | PUT | `/meetings/{meetingId}` | Official HTTP |
| `delete-meeting` | DELETE | `/meetings/{meetingId}` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Bot vs user tokens differ (bots cannot list all rooms). listen = Webex webhooks as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
