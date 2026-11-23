# seven

In-process StudioQuack builtin (`templateId` `a85a8e5b-dd8c-519d-91fd-3f131e1a6af9`, override with `SQ_TEMPLATE_ID_SEVEN`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official seven.io Gateway API** at `https://gateway.seven.io/api`. Auth: `header` via `api_key`.

Provider docs: [https://docs.seven.io/en/](https://docs.seven.io/en/)

Terms: [https://www.seven.io/en/company/terms/](https://www.seven.io/en/company/terms/)

Formerly sms77. Local telecom rules apply. Official gateway.seven.io.

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
| `send-sms` | POST | `/sms` | Official HTTP |
| `voice-call` | POST | `/voice` | Official HTTP |

## Configuration parameters

`operation` (select, default `send-sms`), `api_key` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

**AI Agent tool schema:** Map DuckTape `request` → INPUT (`operation`, `to`, `text`, optional `from`, …). Unmapped tools expose `properties: {}` while `api_spec` still enforces `to` + `text` before HTTP.

## Security & legal

Official API only. Formerly sms77. Local telecom rules apply. Official gateway.seven.io. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
