# Gotify

In-process StudioQuack builtin (`templateId` `8c60aed9-6882-5621-b91d-36a6e362361f`, override with `SQ_TEMPLATE_ID_GOTIFY`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Gotify REST API** at `https://`. Auth: `header` via `app_token`.

Provider docs: [https://gotify.net/api-docs](https://gotify.net/api-docs)

Terms: [https://gotify.net/](https://gotify.net/)

Self-hosted. app_token sends; listing may require a client token (same header). Customer owns the server.

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
| `create-message` | POST | `{host}/message` | Official HTTP |
| `list-messages` | GET | `{host}/message` | Official HTTP |
| `delete-message` | DELETE | `{host}/message/{id}` | Official HTTP |

## Configuration parameters

`operation` (select), `app_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Self-hosted. app_token sends; listing may require a client token (same header). Customer owns the server. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
