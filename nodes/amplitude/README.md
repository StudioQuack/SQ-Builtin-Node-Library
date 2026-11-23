# Amplitude

Official Amplitude HTTP V2 (`POST /2/httpapi`) plus Identify, Group Identify, Batch Event Upload, Export API v2, Taxonomy API. Handler: `OfficialApiNode` via `api_spec.json`.

- **Export/Taxonomy:** per-op `baseUrl` `https://amplitude.com` (`/api/2/export`, `/api/2/taxonomy/event`). HTTP Basic is `api_key:secret_key` — config `basic_token` is precomputed Base64 of that pair; extraHeaders send `Authorization: Basic {basic_token}` on those ops. Ingest stays `https://api2.amplitude.com` with query `api_key`. https://amplitude.com/docs/apis/analytics/export
- **Docs:** https://amplitude.com/docs/apis/analytics/http-v2 · Export https://amplitude.com/docs/apis/analytics/export · Taxonomy https://amplitude.com/docs/apis/analytics/taxonomy · Auth https://amplitude.com/docs/apis/authentication
- **Base:** per-op hosts (US ingest `api2.amplitude.com`; US export/taxonomy `amplitude.com`). EU ingest: `https://api.eu.amplitude.com`. EU analytics: `https://analytics.eu.amplitude.com`.
- **Auth:** ingest `api_key` in the JSON or form body, also sent as query `api_key`. Export/taxonomy use HTTP Basic `api_key:secret_key` via config `basic_token`. Customer owns the account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `event.track` | POST | `api2.amplitude.com/2/httpapi` |
| `identify` | POST | `api2.amplitude.com/identify` |
| `group.identify` | POST | `api2.amplitude.com/groupidentify` |
| `event.batch` | POST | `api2.amplitude.com/batch` |
| `export.download` | GET | `amplitude.com/api/2/export` |
| `taxonomy.list` | GET | `amplitude.com/api/2/taxonomy/event` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Ingest query `api_key` |
| `basic_token` | password | no | Precomputed Base64(`api_key:secret_key`) for Export/Taxonomy `Authorization: Basic {basic_token}` |
| `timeout_seconds` | number | no | Default 25 |

**Region:** packaged ingest host is US HTTP V2 `api2.amplitude.com`. EU projects must use `api.eu.amplitude.com` (ingest) and `analytics.eu.amplitude.com` (export/taxonomy) — handler does not switch hosts. Do not send events to `analytics.amplitude.com` (web app). Official HTTP V2 puts `api_key` in JSON/form body; OfficialApiNode also sends it as the query name `api_key`. Export (`GET /api/2/export`) and Taxonomy (`GET /api/2/taxonomy/event`) live on `amplitude.com` / `analytics.eu.amplitude.com`. Do not put `api_key` or `secret_key` on INPUT.

## Ports

`input` / `output` only. Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- Amplitude terms/DPA apply to the customer project. EU residency requires EU hosts. Identifiers in events are quasi-PII.
- Official API only. Do not scrape the vendor dashboard.
