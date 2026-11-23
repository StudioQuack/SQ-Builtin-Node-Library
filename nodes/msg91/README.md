# MSG91

In-process StudioQuack builtin (`templateId` `2377dbb2-24e6-5e7b-88d3-ae33780dd58d`, override with `SQ_TEMPLATE_ID_MSG91`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official MSG91 Flow / SMS API** at `https://control.msg91.com/api/v5`. Auth: `header` via `authkey`.

Provider docs: [https://docs.msg91.com/reference/send-sms](https://docs.msg91.com/reference/send-sms)

Terms: [https://msg91.com/terms-of-use](https://msg91.com/terms-of-use)

India DLT / sender-ID rules. Use Flow API (v5), not retired query-string sendhttp.php.

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
| `send-sms` | POST | `/flow` | Official HTTP |

## Configuration parameters

`operation` (select), `authkey` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. India DLT / sender-ID rules. Use Flow API (v5), not retired query-string sendhttp.php. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
