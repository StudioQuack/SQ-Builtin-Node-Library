# WeCom

In-process StudioQuack builtin (`templateId` `5ff96ad8-05b1-533d-95cb-f9fafae91b81`, override with `SQ_TEMPLATE_ID_WECOM`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official WeChat Work (企业微信) API** at `https://qyapi.weixin.qq.com/cgi-bin`. Auth: `query` via `access_token`.

Provider docs: [https://developer.work.weixin.qq.com/document/path/90664](https://developer.work.weixin.qq.com/document/path/90664)

Terms: [https://work.weixin.qq.com/nl/eula](https://work.weixin.qq.com/nl/eula)

Not WeChat Official Account / personal WeChat. China-region endpoints. Operator supplies a minted access_token (query). Corp secrets are high privilege. listen = callback as BPMN message events.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-app-message` | POST | `/message/send` | Official HTTP |
| `send-group-robot` | POST | `/webhook/send` | Official HTTP |
| `get-user` | GET | `/user/get` | Official HTTP |
| `list-users` | GET | `/user/simplelist` | Official HTTP |
| `create-user` | POST | `/user/create` | Official HTTP |
| `update-user` | POST | `/user/update` | Official HTTP |
| `delete-user` | GET | `/user/delete` | Official HTTP |
| `list-departments` | GET | `/department/list` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Not WeChat Official Account / personal WeChat. China-region endpoints. Operator supplies a minted access_token (query). Corp secrets are high privilege. listen = callback as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
