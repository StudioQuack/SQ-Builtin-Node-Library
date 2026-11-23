# Close

Official [Close REST API v1](https://developer.close.com/api/overview) via `OfficialApiNode` / `api_spec.json`.

- **Docs:** https://developer.close.com/api/overview
- **Base:** `https://api.close.com/api/v1`
- **Auth:** HTTP Basic `{api_key}:` (API key as username, empty password — `basicKeyPosition: username`).
- **Account:** Customer owns the provider account. StudioQuack does not accept the provider ToS on the customer's behalf.

Inbound provider callbacks are **not** a node socket. `trigger.listen` is `kind: ingress` — use a BPMN message start / catch / boundary event. StudioQuack webhook ingress correlates onto those events.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `lead.create` | POST | `/lead/` | Official HTTP |
| `lead.get` | GET | `/lead/{id}/` | Official HTTP |
| `lead.list` | GET | `/lead/` | Official HTTP |
| `lead.update` | PUT | `/lead/{id}/` | Official HTTP |
| `lead.delete` | DELETE | `/lead/{id}/` | Official HTTP |
| `contact.create` | POST | `/contact/` | Official HTTP |
| `contact.get` | GET | `/contact/{id}/` | Official HTTP |
| `contact.list` | GET | `/contact/` | Official HTTP |
| `contact.update` | PUT | `/contact/{id}/` | Official HTTP |
| `opportunity.create` | POST | `/opportunity/` | Official HTTP |
| `opportunity.get` | GET | `/opportunity/{id}/` | Official HTTP |
| `opportunity.list` | GET | `/opportunity/` | Official HTTP |
| `opportunity.update` | PUT | `/opportunity/{id}/` | Official HTTP |
| `call.create` | POST | `/activity/call/` | Official HTTP |
| `call.get` | GET | `/activity/call/{id}/` | Official HTTP |
| `email.create` | POST | `/activity/email/` | Official HTTP |
| `sms.create` | POST | `/activity/sms/` | Official HTTP |
| `task.create` | POST | `/task/` | Official HTTP |
| `task.get` | GET | `/task/{id}/` | Official HTTP |
| `activity.list` | GET | `/activity/` | Official HTTP |
| `customActivity.create` | POST | `/activity/custom/` | Official HTTP |
| `customObject.create` | POST | `/custom_object/` | Official HTTP |
| `customObject.update` | PUT | `/custom_object/{id}/` | Official HTTP |
| `membership.add` | POST | `/membership/` | Official HTTP |
| `membership.remove` | DELETE | `/membership/{id}/` | Official HTTP |
| `trigger.listen` | ingress | — | BPMN message event (no socket) |

## Config
| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Default verb |
| `api_key` | password | yes | yes | Close API key (Settings → Developer → API Keys). HTTP Basic username. Never log or place on INPUT. |
| `timeout_seconds` | number | no | no | Default 25 |

Key INPUT fields: `email.create` needs `lead_id` + `status` (to send: `status=outbox`, `sender`, `to`, `subject`, `body_text`/`body_html`); `sms.create` needs `status` (to send: `local_phone`, `remote_phone`, `text`); `customObject.create` needs `custom_object_type_id`, `lead_id`, `name`; `membership.add` needs `email` + `role_id`.

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Legal

- Minimize PII: send only fields the operation needs. Do not log emails, phones, message bodies, or tokens.
- Consent, unsubscribe, and suppression are the **customer's** duty (CAN-SPAM / CASL / GDPR where they apply).
- Terms: https://close.com/tos
- Official API only. Do not scrape the provider dashboard.
- Honor RateLimit / Retry-After. Max 40 webhook subscriptions per org.
- Do not fetch or persist call recording URLs in logs. membership.add may require OAuth, not an API key.
- Verify inbound HMAC (close-sig-hash) on the BPMN message event, not on this node.

