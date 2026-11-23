# Pushover

In-process StudioQuack builtin (`templateId` `c9cb1622-65eb-5a91-806b-0b5e3517dc0f`, override with `SQ_TEMPLATE_ID_PUSHOVER`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Pushover Message API v1** at `https://api.pushover.net/1`. Auth: `query` via `token`.

Provider docs: [https://pushover.net/api](https://pushover.net/api)

Terms: [https://pushover.net/terms](https://pushover.net/terms)

Emergency priority has extra cost / quota. Do not implement Glances or Open Client.

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
| `push` | POST | `/messages.json` | Official HTTP |
| `list-sounds` | GET | `/sounds.json` | Official HTTP |

## Configuration parameters

`operation` (select), `token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Emergency priority has extra cost / quota. Do not implement Glances or Open Client. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
