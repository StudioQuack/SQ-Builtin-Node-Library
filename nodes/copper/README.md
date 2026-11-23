# Copper

Official [Copper Developer API v1](https://developer.copper.com/) via `OfficialApiNode` / `api_spec.json`.

- **Docs:** https://developer.copper.com/
- **Base:** `https://api.copper.com/developer_api/v1`
- **Auth:** API key headers (https://developer.copper.com/introduction/requests.html): `X-PW-AccessToken` from `access_token`, extraHeaders `X-PW-UserEmail: {user_email}` and `X-PW-Application: developer_api`.
- **Account:** Customer owns the provider account. StudioQuack does not accept the provider ToS on the customer's behalf.

Inbound provider callbacks are **not** a node socket. `trigger.listen` is `kind: ingress` — use a BPMN message start / catch / boundary event. StudioQuack webhook ingress correlates onto those events.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `company.create` | POST | `/companies` | Official HTTP |
| `company.get` | GET | `/companies/{id}` | Official HTTP |
| `company.list` | POST | `/companies/search` | Official HTTP |
| `company.update` | PUT | `/companies/{id}` | Official HTTP |
| `company.delete` | DELETE | `/companies/{id}` | Official HTTP |
| `person.create` | POST | `/people` | Official HTTP |
| `person.get` | GET | `/people/{id}` | Official HTTP |
| `person.list` | POST | `/people/search` | Official HTTP |
| `person.update` | PUT | `/people/{id}` | Official HTTP |
| `person.delete` | DELETE | `/people/{id}` | Official HTTP |
| `lead.create` | POST | `/leads` | Official HTTP |
| `lead.get` | GET | `/leads/{id}` | Official HTTP |
| `lead.list` | POST | `/leads/search` | Official HTTP |
| `lead.update` | PUT | `/leads/{id}` | Official HTTP |
| `lead.delete` | DELETE | `/leads/{id}` | Official HTTP |
| `opportunity.create` | POST | `/opportunities` | Official HTTP |
| `opportunity.get` | GET | `/opportunities/{id}` | Official HTTP |
| `opportunity.list` | POST | `/opportunities/search` | Official HTTP |
| `opportunity.update` | PUT | `/opportunities/{id}` | Official HTTP |
| `opportunity.delete` | DELETE | `/opportunities/{id}` | Official HTTP |
| `project.create` | POST | `/projects` | Official HTTP |
| `project.get` | GET | `/projects/{id}` | Official HTTP |
| `project.list` | POST | `/projects/search` | Official HTTP |
| `project.update` | PUT | `/projects/{id}` | Official HTTP |
| `project.delete` | DELETE | `/projects/{id}` | Official HTTP |
| `task.create` | POST | `/tasks` | Official HTTP |
| `task.get` | GET | `/tasks/{id}` | Official HTTP |
| `task.list` | POST | `/tasks/search` | Official HTTP |
| `task.update` | PUT | `/tasks/{id}` | Official HTTP |
| `task.delete` | DELETE | `/tasks/{id}` | Official HTTP |
| `customerSource.list` | GET | `/customer_sources` | Official HTTP |
| `user.list` | POST | `/users/search` | Official HTTP |
| `trigger.listen` | ingress | — | BPMN message event (no socket) |

## Config
| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Default verb |
| `access_token` | password | yes | yes | Copper API key sent as `X-PW-AccessToken`. Never log or place on INPUT. |
| `user_email` | string | yes | no | Token-owner email sent as `X-PW-UserEmail`. |
| `timeout_seconds` | number | no | no | Default 25 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Legal

- Minimize PII: send only fields the operation needs. Do not log emails, phones, message bodies, or tokens.
- Consent, unsubscribe, and suppression are the **customer's** duty (CAN-SPAM / CASL / GDPR where they apply).
- Terms: https://www.copper.com/terms
- Official API only. Do not scrape the provider dashboard.
- Professional/Business (or trial) required for API/webhooks per Copper help.
- Rate limit 180 req/min (bulk 3 rps). Honor 429.
- Webhook target must be HTTPS; deliveries may lag (~15 min) and are not always retried.

