# Postmark

In-process StudioQuack builtin (`templateId` `950733b2-20ad-5b58-b521-b628c7247405`, override with `SQ_TEMPLATE_ID_POSTMARK`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Postmark API** at `https://api.postmarkapp.com`. Auth: `header` via `server_token`.

Provider docs: [https://postmarkapp.com/developer/api/email-api](https://postmarkapp.com/developer/api/email-api)

Terms: [https://postmarkapp.com/terms-of-service](https://postmarkapp.com/terms-of-service)

Transactional only — no bulk marketing. listen = webhooks as BPMN message events.

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
| `send-email` | POST | `/email` | Official HTTP |
| `send-template` | POST | `/email/withTemplate` | Official HTTP; TemplateId or TemplateAlias (https://postmarkapp.com/developer/api/templates-api) |
| `send-batch` | POST | `/email/batch` | Official HTTP |
| `get-bounce` | GET | `/bounces/{id}` | Official HTTP |
| `list-bounces` | GET | `/bounces` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

**Agent / tool testing notes:** `list-bounces` always sends `count` and `offset` (defaults 50 / 0). `get-bounce` accepts `id` or `bounceID`. `send-batch` expects `Messages` as a JSON **array** root body (alias `messages`). Positive `send-email` / `send-template` still need a verified Postmark Sender Signature on the account.

## Configuration parameters

`operation` (select), `server_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Transactional only — no bulk marketing. listen = webhooks as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
