# Action Network


Person writes take OSDI purpose fields `given_name`, `family_name`, `email` (email_addresses[0].address), `postal_code` (postal_addresses[0].postal_code) merged into the `person` envelope via bodyMap. Docs: https://actionnetwork.org/docs/v2/people

`event.create` and `petition.create` send `origin_system` (required by Action Network on POST); blank input defaults to `StudioQuack`.

Official Action Network REST API v2 (OSDI 1.1.1 / HAL+JSON; changelog includes 2026-07-08). Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://actionnetwork.org/docs/v2
- **Base:** `https://actionnetwork.org`
- **Auth:** header `OSDI-API-Token` (`api_key`). Customer owns the account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `person.create` | POST | `/api/v2/people/` |
| `person.get` | GET | `/api/v2/people/{personId}` |
| `person.list` | GET | `/api/v2/people` |
| `person.update` | PUT | `/api/v2/people/{personId}` |
| `event.create` | POST | `/api/v2/events` |
| `event.get` | GET | `/api/v2/events/{eventId}` |
| `event.list` | GET | `/api/v2/events` |
| `attendance.create` | POST | `/api/v2/events/{eventId}/attendances` |
| `attendance.get` | GET | `/api/v2/events/{eventId}/attendances/{attendanceId}` |
| `attendance.list` | GET | `/api/v2/events/{eventId}/attendances` |
| `petition.create` | POST | `/api/v2/petitions` |
| `petition.get` | GET | `/api/v2/petitions/{petitionId}` |
| `petition.list` | GET | `/api/v2/petitions` |
| `petition.update` | PUT | `/api/v2/petitions/{petitionId}` |
| `signature.create` | POST | `/api/v2/petitions/{petitionId}/signatures` |
| `signature.get` | GET | `/api/v2/petitions/{petitionId}/signatures/{signatureId}` |
| `signature.list` | GET | `/api/v2/petitions/{petitionId}/signatures` |
| `signature.update` | PUT | `/api/v2/petitions/{petitionId}/signatures/{signatureId}` |
| `tag.create` | POST | `/api/v2/tags` |
| `tag.get` | GET | `/api/v2/tags/{tagId}` |
| `tag.list` | GET | `/api/v2/tags` |
| `personTag.add` | POST | `/api/v2/tags/{tagId}/taggings` |
| `personTag.remove` | DELETE | `/api/v2/people/{personId}/taggings/{taggingId}` |
| `trigger.listen` | ingress | BPMN message event (no socket) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| auth secret | password | yes | Provider credential |
| `timeout_seconds` | number | no | Default 25 |

Shared production host: `actionnetwork.org` (no regional DC). `trigger.listen` is ingress — paste the StudioQuack webhook URL into API & Sync. Rate limit: 4 rps. v1 `api-key` header is retired.

## Ports

`input` / `output` only. Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- Partner-feature keys. Political/advocacy data is sensitive. ToS: https://actionnetwork.org/docs
- Official API only. Do not scrape the vendor dashboard.
