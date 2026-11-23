# Mandrill

In-process StudioQuack builtin (`templateId` `dfa4cc19-263e-5a09-84b4-bb8061cedae5`, override with `SQ_TEMPLATE_ID_MANDRILL`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Mailchimp Transactional (Mandrill) API 1.0** at `https://mandrillapp.com/api/1.0`. Auth: `none` via `api_key`.

Provider docs: [https://mailchimp.com/developer/transactional/api/messages/](https://mailchimp.com/developer/transactional/api/messages/)

Terms: [https://mailchimp.com/legal/terms/](https://mailchimp.com/legal/terms/)

Mailchimp Transactional — not the Marketing API (other category). Auth type none because Mandrill expects key inside the JSON body; copy api_key into input.key or rely on bodyFrom. listen = webhooks as BPMN message events.

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
| `send-html` | POST | `/messages/send.json` | Official HTTP |
| `send-template` | POST | `/messages/send-template.json` | Official HTTP | Same `message.*` purpose fields as send-html; keep `message` as the escape hatch.
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `api_key` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Mailchimp Transactional — not the Marketing API (other category). Auth type none because Mandrill expects key inside the JSON body; copy api_key into input.key or rely on bodyFrom. listen = webhooks as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
