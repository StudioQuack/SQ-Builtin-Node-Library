# Matrix

In-process StudioQuack builtin (`templateId` `ff0ce84f-748a-564a-87a7-fcd6ddc8f1d9`, override with `SQ_TEMPLATE_ID_MATRIX`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Matrix Client-Server API v1.19** at `https://`. Auth: `bearer` via `access_token`.

Provider docs: [https://spec.matrix.org/v1.19/client-server-api/](https://spec.matrix.org/v1.19/client-server-api/)

Terms: [https://matrix.org/legal/terms-and-conditions/](https://matrix.org/legal/terms-and-conditions/)

E2EE rooms are out of v1 (plaintext only). homeserver is interpolated into the path (baseUrl https://). listen = /sync is not opened here — use a BPMN message event for appservice/webhook bridges.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` . |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `get-account` | GET | `{homeserver}/_matrix/client/v3/account/whoami` | Official HTTP |
| `get-event` | GET | `{homeserver}/_matrix/client/v3/rooms/{roomId}/event/{eventId}` | Official HTTP |
| `send-message` | PUT | `{homeserver}/_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}` | Official HTTP |
| `list-messages` | GET | `{homeserver}/_matrix/client/v3/rooms/{roomId}/messages` | Official HTTP |
| `send-media` | POST | `{homeserver}/_matrix/media/v3/upload` | Official HTTP |
| `create-room` | POST | `{homeserver}/_matrix/client/v3/createRoom` | Official HTTP |
| `invite-room` | POST | `{homeserver}/_matrix/client/v3/rooms/{roomId}/invite` | Official HTTP |
| `join-room` | POST | `{homeserver}/_matrix/client/v3/join/{roomIdOrAlias}` | Official HTTP |
| `kick-room` | POST | `{homeserver}/_matrix/client/v3/rooms/{roomId}/kick` | Official HTTP |
| `leave-room` | POST | `{homeserver}/_matrix/client/v3/rooms/{roomId}/leave` | Official HTTP |
| `list-members` | GET | `{homeserver}/_matrix/client/v3/rooms/{roomId}/members` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. E2EE rooms are out of v1 (plaintext only). homeserver is interpolated into the path (baseUrl https://). listen = /sync is not opened here — use a BPMN message event for appservice/webhook bridges. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
