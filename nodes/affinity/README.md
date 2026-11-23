# Affinity

Official Affinity API v2 dated revision `2026-07-15` plus legacy v1 writes at the same host (v1 is not deprecated). Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developer.affinity.co/pages/external-api-v2/introduction · [Versioning](https://developer.affinity.co/pages/external-api-v2/versioning) · [Changelog 2026-07-15](https://developer.affinity.co/pages/changelog/previous-changes) · v1 https://api-docs.affinity.co/
- **Base:** `https://api.affinity.co`
- **Auth:** Bearer `api_key`. Customer owns the account.
- **Version header:** `X-Affinity-Api-Version` from config `api_version` (default `2026-07-15`) via spec `extraHeaders` `{api_version}`. Verified current stable version 2026-08-22.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `person.create` | POST | `/persons` |
| `person.get` | GET | `/v2/persons/{personId}` |
| `person.list` | GET | `/v2/persons` |
| `person.update` | PUT | `/persons/{personId}` |
| `person.delete` | DELETE | `/persons/{personId}` |
| `organization.create` | POST | `/organizations` |
| `organization.get` | GET | `/v2/companies/{organizationId}` |
| `organization.list` | GET | `/v2/companies` |
| `organization.update` | PUT | `/organizations/{organizationId}` |
| `organization.delete` | DELETE | `/organizations/{organizationId}` |
| `list.get` | GET | `/v2/lists/{listId}` |
| `list.list` | GET | `/v2/lists` |
| `listEntry.create` | POST | `/lists/{listId}/list-entries` |
| `listEntry.get` | GET | `/v2/lists/{listId}/list-entries/{listEntryId}` |
| `listEntry.list` | GET | `/v2/lists/{listId}/list-entries` |
| `listEntry.delete` | DELETE | `/lists/{listId}/list-entries/{listEntryId}` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_version` | string | no | Sent as `X-Affinity-Api-Version` (default `2026-07-15`) |
| `api_key` | password | yes | Provider credential |
| `timeout_seconds` | number | no | Default 25 |

Single shared host `api.affinity.co` (no DC subdomain). License-gated API. v1+v2 share monthly/per-minute/concurrent limits. v2 `2026-07-15` redacts restricted opportunity list-entry fields (`type: hidden`).

## Ports

`input` / `output` only. Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- https://support.affinity.co — do not republish relationship-intelligence outside the customer contract.
- Official API only. Do not scrape the vendor dashboard.
