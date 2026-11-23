# Replicate

Official Replicate HTTP API v1 node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_REPLICATE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

This is a **prediction runner**, not an Agent chat model. Ports are `input` / `output`. Do **not** attach this node to the AI Agent `model` FUNCTION port.

## Official API

- **Name / version:** Replicate HTTP API **v1** (`https://api.replicate.com/v1`). Researched 2026-08-22.
- **Docs:** [HTTP API reference](https://replicate.com/docs/reference/http) · [OpenAPI](https://replicate.com/docs/reference/openapi) · [Create a prediction](https://replicate.com/docs/topics/predictions/create-a-prediction) · [Prediction lifecycle](https://replicate.com/docs/topics/predictions/lifecycle) · [API tokens](https://replicate.com/docs/topics/security/api-tokens)
- **Auth:** StudioQuack secret `api_token` → `Authorization: Bearer` (tokens start with `r8_`). Never on INPUT. Never logged.

## Scope

One node for Replicate, Inc. Every run is a **prediction**: create → (operator poll via `get_prediction`) → optional `cancel`. Official models use `POST /v1/models/{owner}/{name}/predictions` (`run`). `POST /v1/predictions` (`predict`) now accepts official `{owner}/{name}` as well as community `{version_id}` / `{owner}/{name}:{version_id}` ([changelog](https://replicate.com/changelog/2025-08-05-run-all-models-with-the-same-api-endpoint)).

`list_models` is `GET /v1/models` (paginated **public** models), not “list my predictions”. Do not implement trainings, deployments, or model create/delete. Do not call beta `QUERY /v1/models`. Webhooks: optional operator-owned HTTPS URL on create only; StudioQuack does not register a global webhook.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `run` | POST | `/v1/models/{owner}/{name}/predictions` | Official model create. Generic handler **does not wait/poll** — returns the create body (`starting` is common). Poll with `get_prediction`. |
| `predict` | POST | `/v1/predictions` | Create; body `version` + `input`. `version` may be official `owner/name` or a community version id. |
| `get_prediction` | GET | `/v1/predictions/{prediction_id}` | Fetch / poll until terminal |
| `cancel` | POST | `/v1/predictions/{prediction_id}/cancel` | Cancel pending/running |
| `list_models` | GET | `/v1/models` | Paginated public catalog (`cursor`, `sort_by`, `sort_direction`) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | `input` | `owner`/`name` for `run`; `version`+nested `input` for `predict`; `prediction_id`. **No token.** |
| `output` | OUTPUT | `output` | `{status,http_status,data}` or `{error}`. Secrets redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `run`. |
| `api_token` | password | **yes** | Bearer `r8_…`. Required. |
| `version` | string | no | Community version; also on INPUT for `predict`. |
| `timeout_seconds` | number | no | Default `60` (single HTTP call). |

## Legal

- [Replicate Terms of Service](https://replicate.com/terms)
- Model licenses: each model page / weights license applies (some **non-commercial**, some extra API terms). Customer must read the model card before production use.
- Rate limits: account credits; HTTP 401/402 (payment)/429. No 429 retry storm. Cold boots can make `starting` last seconds to minutes.
- PII: `input` is stored and visible on the Replicate dashboard; API predictions delete input/output/logs after about **one hour** by default (`data_removed`). Still a transfer to Replicate. Customer is controller. Do not send PHI.
- Official-API-first: `api.replicate.com` only. No Discord bots.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth: `Authorization: Bearer` (docs use Bearer, not `Token`).
- Nested prediction payload must be the JSON field `input` on the port (not a flat `{prompt}` unless a later handler wraps it).
- Replicate wants HTTP URLs (>256 KB) or data URLs (≤256 KB) inside the nested `input` object. The generic handler does not resolve platform refs or send `Prefer: wait`.
- `run` catalog meaning is “create and wait”; this package maps `run` to **create only**. Use `get_prediction` until `succeeded`/`failed`/`canceled`/`aborted`.
- `cancel` `bodyFrom` is `_omit` so the generic handler POSTs `{}` instead of echoing the whole INPUT map.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_token` or prompt bodies.

## Unverified

- Exact pagination query name `cursor` vs parsing the `next` URL (use the `next` URL’s query from the previous response).
