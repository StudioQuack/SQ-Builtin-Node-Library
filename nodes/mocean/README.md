# Mocean

In-process StudioQuack builtin (`templateId` `f48809f9-5c1b-551a-a453-0289b6837d77`, override with `SQ_TEMPLATE_ID_MOCEAN`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Mocean REST API** at `https://rest.moceanapi.com/rest/2`. Auth: `query` via `mocean-api-secret`.

Provider docs: [https://moceanapi.com/docs/](https://moceanapi.com/docs/)

Terms: [https://moceanapi.com/legal/](https://moceanapi.com/legal/)

APAC sender-ID rules. Query auth uses mocean-api-secret (auth.configKey) plus mocean-api-key config. Official rest.moceanapi.com.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. Attachments are `file`. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-sms` | POST | `/sms` | Official HTTP |
| `send-voice` | POST | `/voice/dial` | Official HTTP |

## Configuration parameters

`operation` (select), `mocean-api-secret` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. APAC sender-ID rules. Query auth uses mocean-api-secret (auth.configKey) plus mocean-api-key config. Official rest.moceanapi.com. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
