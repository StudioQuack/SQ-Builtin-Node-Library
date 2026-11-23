# ntfy

In-process StudioQuack builtin (`templateId` `cbbe45b7-b502-557d-ab3e-d04ce86dd459`, override with `SQ_TEMPLATE_ID_NTFY`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official ntfy HTTP API** at `https://ntfy.sh`. Auth: `bearer` via `access_token`.

Provider docs: [https://docs.ntfy.sh/publish/](https://docs.ntfy.sh/publish/)

Terms: [https://ntfy.sh/docs/faq/](https://ntfy.sh/docs/faq/)

Self-host: OfficialApiNode host is ntfy.sh. Public topics are secrets. listen = JSON/SSE subscribe is not a long-poll on this node — BPMN message events.

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
| `publish` | POST | `/{topic}` | `topic` required on INPUT or instance config; body is JSON (`title`, `message`, `priority`, …) per `contentType: application/json`. |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

**Default operation:** Omitted INPUT `operation` uses instance config (`publish`). `topic` is validated before HTTP (`api_spec` `required`).

**AI Agent tool schema:** Exposed `request` properties come from DuckTape `tools` → `input` mappings only. Map at least `operation`, `topic`, `message` (and optional fields) or the LLM sees `properties: {}` while runtime still enforces `topic`.

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Self-host: OfficialApiNode host is ntfy.sh. Public topics are secrets. listen = JSON/SSE subscribe is not a long-poll on this node — BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
