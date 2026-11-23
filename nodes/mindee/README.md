# Mindee

Official Mindee API v2 inference (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_MINDEE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Host:** `https://api-v2.mindee.net` ([OpenAPI](https://api-v2.mindee.net/openapi.json))
- **Enqueue:** `POST /v2/products/extraction/enqueue` ([extraction models](https://docs.mindee.com/integrations/api-reference/extraction-models.md))
- **Auth:** header `Authorization: {api_key}` — **raw key only**. Not `Bearer`, not V1 `Token `. V1 keys (`api.mindee.net/v1`) are a different product and are not used here.
- JSON body is official when not using multipart: `model_id` plus `url` or `file_base64`. Multipart `file` is also official; OfficialApiNode JSON-encodes type `file` refs until a later multipart path.

## Scope

V2 extraction models are identified by a **per-account `model_id` UUID**. Invoice / Receipt / Passport are catalog **templates** you clone; they are not global path slugs like `/invoices/v4`.

| operation | HTTP | Path |
|---|---|---|
| `predict` | POST | `/v2/products/extraction/enqueue` |
| `invoice` | POST | same — set `model_id` to your Invoice catalog model UUID |
| `receipt` | POST | same — Receipt catalog model UUID |
| `passport` | POST | same — Passport catalog model UUID |
| `get_job` | GET | `/v2/jobs/{job_id}` |
| `get_result` | GET | `/v2/products/extraction/results/{inference_id}` |

Enqueue is **async** (HTTP 202, `job.id` / `job.polling_url`). OfficialApiNode does not poll by itself: loop `get_job` with `job_id` until `job.status` is `Processed` (the server then redirects to the result, which the node returns), or call `get_result` with `inference_id`. Enable “Delete extracted data when fetched” on the model when you must not retain ID fields.

## Ports

`input` / `output` only. `file` is type `file`. Prefer `url` for JSON-only invokes.

## Configuration

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Enqueue intent (`predict` / `invoice` / `receipt` / `passport`) or polling (`get_job` / `get_result`) |
| `api_key` | password | **yes** | V2 key (`md_…`) |
| `timeout_seconds` | number | no | Default 60 |

## Legal

- [Mindee terms](https://www.mindee.com/legal/terms-of-service) · DPA on the Mindee account
- Documents leave the host to Mindee. **Passport / ID models are PII.** Minimize retention; delete inferences when the run is done.
- Logs: operation, model_id, job id, status — never file bytes, never extracted identity fields, never `api_key`.
