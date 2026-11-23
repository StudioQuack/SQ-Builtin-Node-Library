# Cortex

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/cortex/`
- **Researched:** 2026-08-20
- **Official API:** Cortex **2+** REST API (`/api/…`, default listen **9001**)
- **Docs:** https://docs.strangebee.com/cortex/api/api-guide/ (live 2026-08-20)
- **Auth:** `api_key` — secret binding: `api_key` (header `Authorization: Bearer {key}`)

## Scope

Calls a **customer-hosted** Cortex instance to run **analyzers** on observables, poll jobs, fetch reports, and run **responders**. Cortex is the analysis engine (often paired with TheHive); this node talks to Cortex directly, not to TheHive’s Cortex connector.

Official guide applies to **Cortex 2 and newer** (not Cortex 1). Prefer API keys; HTTP Basic exists but is **disabled by default** — do not implement Basic in v1 of this node.

**Policy warning (must surface in the node description):** submitting observables to analyzers may **share IOCs and file bytes with third-party scanners** configured on that Cortex (quota’d external services). Operators need a lawful basis and org policy before `execute-analyzer` / `execute-responder`.

**DO NOT BUILD:** Cortex 1; org/user admin APIs (`/api/organization`, `/api/user`); enabling/disabling analyzers; session-cookie auth; Basic auth.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute-analyzer` | POST | `/api/analyzer/{analyzerId}/run` | Submit an observable. Query `force=1` bypasses job cache |
| `get-job` | GET | `/api/job/{jobId}` | Job metadata **without** report |
| `get-job-report` | GET | `/api/job/{jobId}/report` | Job + `report`. Optional wait: `/api/job/{jobId}/waitreport?atMost=` |
| `execute-responder` | POST | `/api/responder/{responderId}/run` | Run a responder on JSON input (`data` + `dataType`) |

Analyzer run / job paths: https://docs.strangebee.com/cortex/api/api-guide/  
Responder run is **not** in that guide’s TOC; it is on the Cortex HTTP router (`POST /api/responder/:id/run` in TheHive-Project/Cortex `conf/routes`) and Cortex4py `responders.run_by_id`. Mark responder **payload examples** as **UNVERIFIED** against a live instance if the operator’s Cortex build differs.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute-analyzer` | no | Chooses the row above |
| `base_url` | string | yes | empty | no | Cortex origin, no trailing slash (example `https://cortex.example:9001`) |
| `api_key` | password | yes | empty | yes | Bearer API key. Never on INPUT, never logged |
| `timeout_seconds` | number | no | `60` | no | HTTP timeout. Analyzer jobs can be slow; waitreport uses this as ceiling |
| `verify_tls` | boolean | no | `true` | no | TLS verify. Default on |
| `default_tlp` | number | no | `2` | no | Default TLP 0–3 when input omits `tlp` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. File observables use type `file` |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never include `api_key`. Do not log observable `data` or file bytes |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = execute-analyzer`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `analyzer_id` | string | yes | Enabled analyzer **instance** id (`ANALYZER_ID` in docs) |
| `data_type` | string | yes | Observable type (`ip`, `domain`, `hash`, `file`, …) as the analyzer allows |
| `data` | string | yes | Observable value (JSON body). Official file runs are multipart `attachment` + `_json` — this node JSON-posts `bodyFrom` and has no INPUT `attachment`. |
| `message` | string | no | Free-text context for the analyzer |
| `parameters` | object | no | Analyzer-specific JSON object |
| `force` | boolean | no | If true, `?force=1` (skip cache) |

Non-file: JSON POST `{ data, dataType, tlp, message, parameters }`. File: multipart as in the official guide.

### `operation = get-job`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `job_id` | string | yes | Job id |

### `operation = get-job-report`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `job_id` | string | yes | Job id |
| `wait` | boolean | no | If true, call `/waitreport` instead of `/report` |
| `at_most` | string | if `wait` | Duration: `Xsecond`, `Xminute`, or `Xhour` (official). Cap so total ≤ `timeout_seconds` |

If the job is not finished, `report` may be a **string** status (official). Return it as `job_status` / `report` without spinning in the handler beyond `waitreport`.

### `operation = execute-responder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `responder_id` | string | yes | Enabled responder instance id |
| `data_type` | string | yes | Responder datatype (e.g. `thehive:case`, `thehive:alert`, or analyzer-style types) |
| `data` | object or string | yes | JSON the responder expects (often a case/alert document). **UNVERIFIED** exact schema per responder |
| `tlp` | number | no | 0–3 |
| `pap` | number | no | PAP if the responder honors it |
| `message` | string | no | Optional message |
| `parameters` | object | no | Optional parameters |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `job` | object | execute-* / get-job / get-job-report 2xx | `id`, `analyzerId` / `analyzerName`, `status` (`Waiting` \| `InProgress` \| `Success` \| `Failure` \| `Deleted`), `dataType`, `data` (omit or redact on logs), `tlp`, `startDate`, `endDate`, `organization` |
| `report` | object or string | get-job-report | Full report (`summary.taxonomies`, `full`, `success`, `artifacts`) or pending status string |
| `error` | string | failure | Includes Cortex `{ type, message }` when present. No API key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 30 |

Job statuses: `Waiting`, `InProgress`, `Success`, `Failure`, `Deleted`.

## Legal

- API guide: https://docs.strangebee.com/cortex/api/api-guide/
- Cortex product: https://strangebee.com/cortex/ — self-hosted; operator holds the Cortex license/support contract
- Analyzers/responders may be AGPL components plus **third-party ToS** (each enabled worker). Sharing indicators/files with those vendors is the operator’s responsibility
- Observables can be personal data or law-enforcement sensitive. Minimize. Never log API keys, file bytes, or raw IOC strings in handler logs (log job id + HTTP status only)

## Implementation notes

- JDK HTTP only. Do not bundle Cortex4py or any vendor SDK.
- Header: `Authorization: Bearer {api_key}`. **Never log it.** JSON: `Content-Type: application/json`. File run: multipart; do not set JSON content-type on the outer request.
- TLS verified by default. Timeout from config (default 60 s). Body cap 8 MiB (file observables). `Redirect.NEVER`.
- Missing `data` for execute-analyzer → `{ error }` without HTTP. Official multipart `attachment` is not an INPUT on this node.
- 401 `{ type: AuthenticationError }` → OUTPUT `{ error }`. 4xx/5xx never throw past dispatch.
- Do not follow job artifacts download in v1 unless catalog grows a verb.
