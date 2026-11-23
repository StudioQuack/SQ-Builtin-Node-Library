# Mailparser

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/mailparser/`
- **Official API:** https://app.mailparser.io/mailparser-api
- **Auth secret:** `api_key`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded payloads may appear as OUTPUT `file` (platform file ref-id). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `inbox_list` | GET | `/inboxes` |
| `webhook_listen` | ingress | BPMN message event — does not open a socket |
| `parsed_mail_get` | GET | `/inbox/{inbox_id}/parsed_mails/{id}` |
| `parsed_mail_list` | GET | `/inbox/{inbox_id}/parsed_mails` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{status,http_status,data}`. |

FUNCTION ports: none.

## Configuration

`operation` (select), auth secrets (password), `timeout_seconds`.

## Legal

- Terms: https://mailparser.io/terms/
- Official-API-first. No scrape. No unofficial web wrapper.
- Inbound email parsing SaaS. Contents stored on Mailparser. webhook_listen is ingress (BPMN), not a Webhook node.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{status,http_status,data}`. Never throw past dispatch.
