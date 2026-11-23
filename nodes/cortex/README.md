# Cortex

Official [Cortex API](https://docs.strangebee.com/cortex/api/api-guide/) for a **customer-hosted** Cortex 2+ instance. Submitting observables to analyzers may **share IOCs and file bytes with third-party scanners** enabled on that instance. Operators need a lawful basis and org policy before `execute-analyzer` / `execute-responder`.

- **Docs:** https://docs.strangebee.com/cortex/api/api-guide/
- **Base:** `https://{cortexHost}`
- **Auth:** header `Authorization` prefix empty (raw key), configKey `api_key`
- **Handler:** `OfficialApiNode` via `api_spec.json`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `execute-analyzer` | POST | `{cortexHost}/api/analyzer/{analyzerId}/run` |
| `get-job` | GET | `{cortexHost}/api/job/{jobId}` |
| `get-job-report` | GET | `{cortexHost}/api/job/{jobId}/report` |
| `execute-responder` | POST | `{cortexHost}/api/responder/{responderId}/run` |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `cortexHost` | string | no | Host without scheme |
| `api_key` | password | yes | Authorization: {api_key} |
| `timeout_seconds` | number | no | Default 60 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Auth format

This package sends `Authorization: {api_key}` (empty prefix) as specified for Cortex-style raw keys. The StrangeBee API guide fetched 2026-08-20 shows `Authorization: Bearer {API_KEY}` in curl examples. **UNVERIFIED** which format a given instance requires. Do not implement HTTP Basic (disabled by default).

## Engine gaps / unverified

- File observables: official run is multipart (`attachment` + `_json`). OfficialApiNode JSON-posts `bodyFrom`.
- `get-job-report` maps to `/report` only. `/waitreport?atMost=` is not a separate catalog op.
- Responder path is on the Cortex HTTP router (`POST /api/responder/:id/run`); payload examples per responder are **UNVERIFIED**.
- Org/user admin APIs are out of scope.
