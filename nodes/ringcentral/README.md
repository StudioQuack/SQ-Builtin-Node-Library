# RingCentral

In-process StudioQuack builtin (`templateId` `4fad78d8-b58e-5c00-9884-6dcc3de400f8`, override with `SQ_TEMPLATE_ID_RINGCENTRAL`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official RingEX REST API v1.0** at `https://platform.ringcentral.com/restapi/v1.0`, plus Team Messaging on the platform host. Auth: `bearer` via `access_token`.

Provider docs:

- [API reference](https://developers.ringcentral.com/api-reference)
- [Team Messaging — posting](https://developers.ringcentral.com/guide/team-messaging/posting)
- [Programmable Meetings API (discontinued)](https://developers.ringcentral.com/meetings-api)

Terms: [https://www.ringcentral.com/legal/tos.html](https://www.ringcentral.com/legal/tos.html)

OAuth JWT or 3-legged. Sandbox host is `platform.devtest.ringcentral.com`. SMS 10DLC.

## Removed: `create-meeting`

The **Programmable Meetings API is discontinued**. `create-meeting` (`POST /account/~/extension/~/meeting`) was removed from this node. RingCentral Video (`/rcvideo/v2/...`, still beta) is a different product and is not wired here.

## `create-post` path

`create-post` now calls Team Messaging, not legacy Glip:

- Host override: `https://platform.ringcentral.com` (outside `/restapi/v1.0`)
- Path: `POST /team-messaging/v1/chats/{chatId}/posts`

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-sms` | POST | `/account/~/extension/~/sms` | Official HTTP |
| `send-fax` | POST | `/account/~/extension/~/fax` | Official HTTP |
| `ringout` | POST | `/account/~/extension/~/ring-out` | Official HTTP |
| `create-post` | POST | `/team-messaging/v1/chats/{chatId}/posts` | Per-op baseUrl `https://platform.ringcentral.com` |
| `create-contact` | POST | `/account/~/extension/~/address-book/contact` | Official HTTP |
| `update-contact` | PUT | `/account/~/extension/~/address-book/contact/{contactId}` | Official HTTP |
| `find-contact` | GET | `/account/~/extension/~/address-book/contact` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

Removed: `create-meeting`.

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. SMS 10DLC. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
