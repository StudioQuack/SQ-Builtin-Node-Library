# MessageBird

In-process StudioQuack builtin (`templateId` `0fc83b95-4bfe-5ab4-9df2-917efbaeba20`, override with `SQ_TEMPLATE_ID_MESSAGEBIRD`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **current Bird SMS API** at `https://{platform_host}` (default `us1.platform.bird.com`). Auth: `bearer` via `access_key` (`Authorization: Bearer bk_{region}_…`).

Provider docs:

- [Sending SMS](https://bird.com/en-us/docs/guides/sms/sending-sms)
- [Create an SMS message](https://bird.com/en-us/docs/api/reference/create-sms-message)
- [Authentication & API keys](https://bird.com/en-us/docs/guides/authentication)
- [Bird docs home](https://bird.com/en-us/docs)

Terms: [https://bird.com/legal/terms](https://bird.com/legal/terms)

## Migration from rest.messagebird.com

The Connectivity Platform host `https://rest.messagebird.com` with `Authorization: AccessKey` is **legacy**. This node now calls the regional platform:

| Was (legacy) | Now (2026-08-22) |
|---|---|
| Host `rest.messagebird.com` | `us1.platform.bird.com` or `eu1.platform.bird.com` (`platform_host`; must match `bk_us1_` / `bk_eu1_`) |
| Header `Authorization: AccessKey …` | `Authorization: Bearer bk_…` (config shortCode **`access_key` unchanged**) |
| POST `/messages` | POST `/v1/sms/messages` |
| Body `originator`, `body`, `recipients[]`, `reference` | Body `from`, `text`, `to` (one E.164), `category` |
| GET `/balance` | **Removed.** Wallet balance is not readable with an SMS-scoped `bk_` key (dashboard / org billing). A send with no funds returns HTTP 402. |

Map stored values when migrating: `originator` → `from`, `body` → `text`, one `recipients` entry → `to`. Those legacy keys are not on INPUT and are not sent.

`category` is required on a free-text send (`transactional`, `marketing`, or `authentication`). Default config value is `transactional`.

Sender-ID and destination-country rules vary by country. listen = status/inbound as BPMN message events.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the regional platform host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` . |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-sms` | POST | `/v1/sms/messages` | Current Bird SMS send. 202 Accepted. |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

Removed: `get-balance`.

## Configuration parameters

`operation` (select), `access_key` (password secret, Bearer `bk_` key), `platform_host` (default `us1.platform.bird.com`), `from` (default sender), `category` (default `transactional`), `timeout_seconds` (default 25).

## Security & legal

Official API only. PHI/PII in messages leaves the sandbox toward Bird; the operator is the data controller. Never log tokens, message bodies, or file bytes. Authentication OTP bodies with `category: authentication` are redacted on later reads (`**REDACTED**`).

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
