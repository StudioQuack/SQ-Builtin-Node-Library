# Zendesk Sell

Official [Zendesk Sell (Sales CRM) API v2](https://developer.zendesk.com/api-reference/sales-crm/introduction/). Handler: `OfficialApiNode` via `api_spec.json`. **Sell only — not Zendesk Support.**

- **Docs:** https://developer.zendesk.com/api-reference/sales-crm/introduction/ · first call: https://developer.zendesk.com/documentation/sales-crm/first-call/
- **Base:** `https://api.getbase.com/v2`
- **Auth:** Bearer `access_token` (Sell OAuth app or dashboard personal token). Customer owns the Sell account.

Register a separate OAuth application in **Sell** (Integrations → OAuth), not Support. Access tokens from the token endpoint expire (~1 hour) unless they are dashboard personal tokens (non-expiring until revoked).

Create/update take purpose-oriented fields nested as `{data:{...}}` via bodyMap.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `lead.create` | POST | `/leads` |
| `lead.get` | GET | `/leads/{id}` |
| `lead.list` | GET | `/leads` |
| `lead.update` | PUT | `/leads/{id}` |
| `contact.create` | POST | `/contacts` |
| `contact.get` | GET | `/contacts/{id}` |
| `contact.list` | GET | `/contacts` |
| `contact.update` | PUT | `/contacts/{id}` |
| `deal.create` | POST | `/deals` |
| `deal.get` | GET | `/deals/{id}` |
| `deal.list` | GET | `/deals` |
| `deal.update` | PUT | `/deals/{id}` |
| `deal.delete` | DELETE | `/deals/{id}` |
| `note.create` | POST | `/notes` |
| `task.create` | POST | `/tasks` |
| `task.get` | GET | `/tasks/{id}` |
| `pipeline.list` | GET | `/pipelines` |
| `stage.list` | GET | `/stages` |
| `trigger.listen` | ingress | BPMN message event |

`trigger.listen` does not open a socket. Use Sell webhooks / stream subscriptions in the product, then a BPMN message start/catch/boundary event.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer token |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer owns the Sell account and is the controller of lead/contact/deal PII. Minimize. Never log tokens, emails, or phones.
- Official Sell API only: `api.getbase.com/v2`. Do not call Support (`*.zendesk.com/api/v2`) from this node.

## Create / update fields

Write operations take purpose-oriented Sell fields (`first_name`, `last_name`, `organization_name`, `name`, `email`, `phone`, `contact_id`, `value`, `resource_type`, `content`, …) nested under the official `{data:{...}}` envelope via bodyMap. Docs: https://developer.zendesk.com/api-reference/sales-crm/resources/leads/
