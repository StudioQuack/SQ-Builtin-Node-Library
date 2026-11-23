# ActiveCampaign


Contact / contactList / account writes take purpose fields (`email`, `firstName`, `lastName`, `phone`, `list`, `status`, `name`) merged into the official envelopes via bodyMap. Docs: https://developers.activecampaign.com/reference/create-a-new-contact-new

Official ActiveCampaign REST API v3 (path /api/3; create-contact reference updated 2026-08-15). Legacy v1 is not used.. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developers.activecampaign.com/reference
- **Base:** `https://{api_url}/api/3 (config `api_url` = Settings → Developer host)`
- **Auth:** header `Api-Token` (`api_key`). Customer owns the account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `contact.create` | POST | `{api_url}/api/3/contacts` |
| `contact.get` | GET | `{api_url}/api/3/contacts/{contactId}` |
| `contact.list` | GET | `{api_url}/api/3/contacts` |
| `contact.update` | PUT | `{api_url}/api/3/contacts/{contactId}` |
| `contact.delete` | DELETE | `{api_url}/api/3/contacts/{contactId}` |
| `contactList.add` | POST | `{api_url}/api/3/contactLists` |
| `contactList.remove` | POST | `{api_url}/api/3/contactLists` |
| `contactTag.add` | POST | `{api_url}/api/3/contactTags` |
| `contactTag.remove` | DELETE | `{api_url}/api/3/contactTags/{contactTagId}` |
| `account.create` | POST | `{api_url}/api/3/accounts` |
| `account.get` | GET | `{api_url}/api/3/accounts/{accountId}` |
| `account.list` | GET | `{api_url}/api/3/accounts` |
| `account.update` | PUT | `{api_url}/api/3/accounts/{accountId}` |
| `account.delete` | DELETE | `{api_url}/api/3/accounts/{accountId}` |
| `accountContact.create` | POST | `{api_url}/api/3/accountContacts` |
| `accountContact.update` | PUT | `{api_url}/api/3/accountContacts/{accountContactId}` |
| `accountContact.delete` | DELETE | `{api_url}/api/3/accountContacts/{accountContactId}` |
| `deal.create` | POST | `{api_url}/api/3/deals` |
| `deal.get` | GET | `{api_url}/api/3/deals/{dealId}` |
| `deal.list` | GET | `{api_url}/api/3/deals` |
| `deal.update` | PUT | `{api_url}/api/3/deals/{dealId}` |
| `deal.delete` | DELETE | `{api_url}/api/3/deals/{dealId}` |
| `deal.note.create` | POST | `{api_url}/api/3/notes` |
| `deal.note.update` | PUT | `{api_url}/api/3/notes/{noteId}` |
| `list.list` | GET | `{api_url}/api/3/lists` |
| `tag.create` | POST | `{api_url}/api/3/tags` |
| `tag.get` | GET | `{api_url}/api/3/tags/{tagId}` |
| `tag.list` | GET | `{api_url}/api/3/tags` |
| `tag.update` | PUT | `{api_url}/api/3/tags/{tagId}` |
| `tag.delete` | DELETE | `{api_url}/api/3/tags/{tagId}` |
| `ecommerce.order.create` | POST | `{api_url}/api/3/ecomOrders` |
| `ecommerce.order.get` | GET | `{api_url}/api/3/ecomOrders/{id}` |
| `ecommerce.order.list` | GET | `{api_url}/api/3/ecomOrders` |
| `ecommerce.order.update` | PUT | `{api_url}/api/3/ecomOrders/{id}` |
| `ecommerce.order.delete` | DELETE | `{api_url}/api/3/ecomOrders/{id}` |
| `ecommerce.customer.create` | POST | `{api_url}/api/3/ecomCustomers` |
| `ecommerce.customer.get` | GET | `{api_url}/api/3/ecomCustomers/{id}` |
| `ecommerce.customer.list` | GET | `{api_url}/api/3/ecomCustomers` |
| `ecommerce.customer.update` | PUT | `{api_url}/api/3/ecomCustomers/{id}` |
| `ecommerce.customer.delete` | DELETE | `{api_url}/api/3/ecomCustomers/{id}` |
| `ecommerce.orderProduct.get` | GET | `{api_url}/api/3/ecomOrderProducts/{id}` |
| `ecommerce.orderProduct.list` | GET | `{api_url}/api/3/ecomOrderProducts` |
| `connection.create` | POST | `{api_url}/api/3/connections` |
| `connection.get` | GET | `{api_url}/api/3/connections/{id}` |
| `connection.list` | GET | `{api_url}/api/3/connections` |
| `connection.update` | PUT | `{api_url}/api/3/connections/{id}` |
| `connection.delete` | DELETE | `{api_url}/api/3/connections/{id}` |
| `trigger.listen` | ingress | BPMN message event (no socket) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| auth secret | password | yes | Provider credential |
| `timeout_seconds` | number | no | Default 25 |

**Region:** copy the exact API URL host from Settings → Developer. US production is `*.api-us1.com`; other DCs use other suffixes — do not invent the host. Rate limit 5 rps. `trigger.listen` is ingress (account webhooks).

## Ports

`input` / `output` only. Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- https://www.activecampaign.com/legal — caller holds CAN-SPAM/CASL/GDPR consent for list subscribe.
- Official API only. Do not scrape the vendor dashboard.
