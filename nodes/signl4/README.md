# SIGNL4

In-process StudioQuack builtin (`templateId` `a75649ef-5a86-59b4-8c70-bb1f8b5d161e`, override with `SQ_TEMPLATE_ID_SIGNL4`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official SIGNL4 inbound / resolve webhook API** at `https://connect.signl4.com/webhook`. Auth: `none` via `team_secret`.

Provider docs: [https://www.signl4.com/webhook-api/](https://www.signl4.com/webhook-api/)

Terms: [https://www.signl4.com/terms-of-use/](https://www.signl4.com/terms-of-use/)

On-call alerting, not general chat. Team secret is part of the URL — treat as a secret.

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
| `send-alert` | POST | `/{team_secret}` | Official HTTP |
| `resolve-alert` | POST | `/{team_secret}` | Official HTTP |

## Configuration parameters

`operation` (select), `team_secret` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. On-call alerting, not general chat. Team secret is part of the URL — treat as a secret. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
