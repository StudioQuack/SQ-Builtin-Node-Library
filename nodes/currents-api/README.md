# Currents API

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/currents-api` |
| `templateId` | `02361fb2-c359-511d-b357-b3aca17ff961` |
| Env override | `SQ_TEMPLATE_ID_CURRENTS_API` |
| Category | Data & Analytics |
| Official docs | https://currentsapi.services/en/docs/ |
| Auth | `header` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `search` | http GET | `/search` |
| `latest` | http GET | `/latest-news` |
| `available_languages` | http GET | `/available/languages` |
| `available_regions` | http GET | `/available/regions` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

Currents API terms. Authorization header is the API key. Publisher copyright remains.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
