# Folk

Official Folk External API **REST v1** (OpenAPI `2025-06-09`). Handler: `OfficialApiNode` via `api_spec.json`. **Not GraphQL.**

- **Docs:** https://developer.folk.app/api-reference/overview · versioning https://developer.folk.app/api-reference/versioning · OpenAPI https://developer.folk.app/schemas/2025-06-09.json
- **Base:** `https://api.folk.app`
- **Auth:** Bearer `apiKey` (secret). Customer owns the Folk workspace. Paid-plan API access.
- **Header pin:** `X-API-Version: {apiVersion}` (default `2025-06-09`). Paths stay `/v1/…`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `person.create` | POST | `/v1/people` |
| `person.get` | GET | `/v1/people/{personId}` |
| `person.update` | PATCH | `/v1/people/{personId}` |
| `person.search` | GET | `/v1/people` |
| `company.create` | POST | `/v1/companies` |
| `company.get` | GET | `/v1/companies/{companyId}` |
| `company.update` | PATCH | `/v1/companies/{companyId}` |
| `group.list` | GET | `/v1/groups` |
| `note.create` | POST | `/v1/notes` |
| `customField.set` | PATCH | `/v1/{entityCollection}/{entityId}` (`people` or `companies`) |
| `trigger.listen` | — | ingress — BPMN message event; this node does not open a socket |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `apiKey` | password | yes | Bearer token |
| `apiVersion` | string | no | `X-API-Version` pin (default `2025-06-09`) |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes `apiKey`.

## Legal

- Customer is the controller of Folk contacts, emails, notes, and custom fields. Minimize INPUT.
- Terms: https://www.folk.app/terms-of-use · Privacy: https://www.folk.app/privacy-policy
- Rate limit: 600 requests/minute per user; `429` + `Retry-After`.
- Official API only (`api.folk.app`). Do not scrape `app.folk.app`.
- `trigger.listen` is not a StudioQuack Webhook node — use a BPMN message event.
