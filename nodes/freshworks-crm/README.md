# Freshworks CRM

Official Freshsales / Freshworks CRM REST (unversioned live reference). Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developers.freshworks.com/crm/api/
- **Base:** `https://{bundleAlias}.{hostSuffix}/crm/sales/api` (`hostSuffix` default `myfreshworks.com`)
- **Auth:** `Authorization: Token token=<apiKey>`. Customer owns the CRM tenant. Domain-scoped key.

List endpoints require a **saved view id** (`/view/{view_id}`), not a bare collection GET. There is **no REST webhook-subscribe**; `trigger.listen` documents Workflow → Trigger webhook into SQ ingress.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `contact.create` | POST | `/contacts` |
| `contact.get` | GET | `/contacts/{id}` |
| `contact.list` | GET | `/contacts/view/{view_id}` |
| `contact.update` | PUT | `/contacts/{id}` |
| `contact.delete` | DELETE | `/contacts/{id}` |
| `account.*` | … | `/sales_accounts` (same get/list/update/delete pattern) |
| `deal.*` | … | `/deals` |
| `appointment.*` | … | `/appointments` |
| `task.*` | … | `/tasks` |
| `note.create` / `note.update` / `note.delete` | POST/PUT/DELETE | `/notes` |
| `salesActivity.get` | GET | `/sales_activities/{id}` |
| `salesActivity.list` | GET | `/sales_activities` |
| `trigger.listen` | — | ingress — BPMN message event; configure Freshsales Workflow “Trigger webhook” |

JSON wrappers required on writes: `{ "contact": {…} }`, `{ "sales_account": {…} }`, `{ "deal": {…} }`, `{ "appointment": {…} }`, `{ "task": {…} }`, `{ "note": {…} }`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `apiKey` | password | yes | Token header |
| `bundleAlias` | string | no | Subdomain / CRM bundle alias |
| `hostSuffix` | select | no | `myfreshworks.com` or `freshworks.com` |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes `apiKey`.

## Legal

- Customer is the controller of names, emails, phones, and notes.
- Terms: https://www.freshworks.com/terms/ · Privacy: https://www.freshworks.com/privacy/
- Rate limit: 1000 API requests per hour per account; HTTP `429`.
- Official API only. Do not call Freshdesk/Freshservice from this node.
- `trigger.listen` is not a StudioQuack Webhook node — use a BPMN message event. Workflow setup: https://crmsupport.freshworks.com/support/solutions/articles/50000002418
