# Keap

Official Keap REST **v2** (resources) at `https://api.infusionsoft.com`. Handler: `OfficialApiNode` via `api_spec.json`. REST Hooks stay on v1 in Keap’s docs; this package’s `trigger.listen` is **ingress** (BPMN message event), not an outbound subscribe call.

- **Docs:** https://developer.infusionsoft.com/docs/restv2/ · OAuth: https://developer.keap.com/getting-started-oauth-keys/
- **Base:** `https://api.infusionsoft.com`
- **Auth:** Bearer `access_token` (secret). OfficialApiNode does not refresh OAuth. Obtain a token with `POST https://api.infusionsoft.com/token` (`grant_type=refresh_token` or authorization-code). Refresh tokens rotate — persist the new refresh token outside this node. Do not use XML-RPC.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `contact.upsert` | POST | `/crm/rest/v2/contacts` |
| `contact.get` | GET | `/crm/rest/v2/contacts/{contact_id}` |
| `contact.list` | GET | `/crm/rest/v2/contacts` |
| `contact.delete` | DELETE | `/crm/rest/v2/contacts/{contact_id}` |
| `company.create` | POST | `/crm/rest/v2/companies` |
| `company.list` | GET | `/crm/rest/v2/companies` |
| `contactNote.*` | POST/GET/PATCH/DELETE | `/crm/rest/v2/contacts/{contact_id}/notes` |
| `contactTag.add` / `remove` / `list` | POST/GET | `/crm/rest/v2/tags/{tag_id}/contacts…` |
| `ecommerce.order.*` | POST/GET/DELETE | `/crm/rest/v2/orders` |
| `ecommerce.product.*` | POST/GET/DELETE | `/crm/rest/v2/products` |
| `email.send` | POST | `/crm/rest/v2/emails:send` |
| `email.listSent` | GET | `/crm/rest/v2/emails` |
| `email.record` | POST | `/crm/rest/v2/emails` |
| `file.upload` / `list` / `delete` | POST/GET/DELETE | `/crm/rest/v2/files` |
| `trigger.listen` | ingress | BPMN message start / catch / boundary |

`contact.upsert` maps `email` → `email_addresses[0].email` and `phone` → `phone_numbers[0].number`. Config defaults `email_field=EMAIL1` and `phone_field=PHONE1` (Keap requires `field`). `email_addresses` / `phone_numbers` remain one-object escape hatches.

`ecommerce.order.create` sends `order_type` (`Online` | `Offline`); `email.send` sends the sender `user_id` alongside `contact_ids`, `subject` and the body.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer token |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `access_token`. `file.upload` uses type `file` (ref-id).

## Legal

- Contacts, notes, emails, files, and orders are PII. Customer owns the Keap account.
- Caller is responsible for CAN-SPAM / CASL / GDPR consent before `email.send`.
- Provider terms: https://developer.keap.com/
- Official API only. Do not scrape the Keap UI.
