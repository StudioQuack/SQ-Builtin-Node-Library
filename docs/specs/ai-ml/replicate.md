# Replicate

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/replicate/`
- **Researched:** 2026-08-20
- **Official API:** Replicate HTTP API **v1** (`https://api.replicate.com/v1`)
- **Docs:** [HTTP API reference](https://replicate.com/docs/reference/http), [OpenAPI](https://replicate.com/docs/reference/openapi), [Create a prediction](https://replicate.com/docs/topics/predictions/create-a-prediction), [Prediction lifecycle](https://replicate.com/docs/topics/predictions/lifecycle), [API tokens](https://replicate.com/docs/topics/security/api-tokens)
- **Auth:** api_key — secret binding: `api_token`

## Scope

One node for Replicate, Inc. This is a **prediction runner**, not an Agent chat model. Ports are **`input` / `output`** (catalog). Do not attach this node to the AI Agent `model` FUNCTION port. No FUNCTION ports.

Every run is a **prediction**: create → (optional wait) → get → optional cancel. Official models use `POST /v1/models/{owner}/{name}/predictions`; community models use `POST /v1/predictions` with a version id.

Catalog `run` and `predict` both map to create: `run` waits (sync/`Prefer: wait` + poll); `predict` returns the in-flight prediction. `list_models` is `GET /v1/models` (paginated public models), not “list my predictions”.

Do not implement trainings, deployments, or model create/delete in v1. Webhooks: optional URL on create only if the operator supplies an **HTTPS URL they control**; StudioQuack must not register a global webhook. Prefer poll.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `run` | POST (+ GET poll) | Official: `/v1/models/{owner}/{name}/predictions`. Community: `/v1/predictions` | Create and wait until `succeeded`/`failed`/`canceled`/`aborted` |
| `predict` | POST | same as `run` | Create only; return `id` + `status` (often `starting`) |
| `get_prediction` | GET | `/v1/predictions/{prediction_id}` | Poll / fetch output |
| `cancel` | POST | `/v1/predictions/{prediction_id}/cancel` | Cancel pending/running |
| `list_models` | GET | `/v1/models` | Paginated public model list |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `run` | no | Row above |
| `api_token` | password | yes | — | **yes** | `Authorization: Bearer`. Tokens start with `r8_`. Never on INPUT. Never log. |
| `version` | string | community `run`/`predict` | empty | no | 64-char version id or `owner/name:version` |
| `wait` | boolean | no | true for `run` | no | Send `Prefer: wait` (Replicate waits up to **60s**; then poll) |
| `cancel_after` | string | no | empty | no | Header `Cancel-After` (`30s`, `1m`, `1h`; min 5s, max 24h) |
| `poll_timeout_seconds` | number | no | `300` | no | Extra wait after the 60s sync window |
| `timeout_seconds` | number | no | `60` | no | Single HTTP call timeout (create/get/cancel) |
| `webhook` | string | no | empty | no | Optional prediction webhook URL (operator-owned HTTPS) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Model `input` object + ids. **No token.** |
| `output` | OUTPUT | object `{ status, … }` or `{ error }` | JSON object (not a string). Secrets redacted. |

FUNCTION ports: none.

Replicate model inputs often take URI strings; put HTTPS URLs Replicate can fetch in the nested `input` object, or follow that model’s file upload docs. Never inline multi-MB base64 unless the model schema requires it and size is capped.

## Input fields (by operation)

### `operation = run` | `predict`

Create body ([predictions.create](https://replicate.com/docs/reference/http#predictions.create)):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `version` | string | community yes | Version id formats: `{owner}/{name}` (official), `{owner}/{name}:{64hex}`, or `{64hex}` |
| `input` | object | **yes** | Model-specific JSON. Schema is the version `openapi_schema`. Pass through from port field `input` (the prediction payload), not the port name. |
| `stream` | boolean | no | If the version supports streaming, response may include `urls.stream` |
| `webhook` | string | no | HTTPS callback |
| `webhook_events_filter` | string[] | no | e.g. `["completed"]` |

Headers: `Prefer: wait` on `run`; `Cancel-After` from config. Official path does not need `version` in the body if the URL has owner/name.

If the port sends a flat `{prompt: "..."}` and no nested `input`, wrap as `{input: <object>}`.

### `operation = get_prediction`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `prediction_id` | string | yes | Id from create (`gm3qorzd…`) |

### `operation = cancel`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `prediction_id` | string | yes | |

### `operation = list_models`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `cursor` | string | no | Pagination cursor from previous `next` URL |
| `query` | string | no | If set, use documented search (`QUERY /v1/models` is listed as beta — **do not call QUERY in v1** unless the HTTP reference still marks it stable). Stick to `GET /v1/models`. |

## Output fields (by operation)

Prediction object (create/get/cancel/`run`):

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success path | Node `ok` plus prediction `status`: `starting` \| `processing` \| `succeeded` \| `failed` \| `canceled` \| `aborted` |
| `id` | string | always on prediction | |
| `model` / `version` | string | | |
| `input` | object | | Echo of sent input (may be **stripped after ~1 hour** — `data_removed`) |
| `output` | any | succeeded | Model-specific (string, URL list, object). Download URL assets to `file` refs when they are media URLs. |
| `error` | string | failed **or** node failure | Prediction error **or** `{error}` only object on handler failure |
| `logs` | string | | Truncate to 32 KiB on output; do not log full logs |
| `metrics` | object | | `predict_time`, `total_time` |
| `urls` | object | | `get`, `cancel`, `web`, optional `stream` |
| `created_at` / `started_at` / `completed_at` | string | | ISO timestamps |
| `data_removed` | boolean | | True after retention window |

`run` must poll `urls.get` until terminal or `poll_timeout_seconds`. `predict` returns immediately (after optional 60s wait if `Prefer: wait` still used — **do not** send Prefer on `predict`).

`list_models`: `{ status:"ok", results: [...], next? }` with model summaries (`url`, `owner`, `name`, `description`, `visibility`, `run_count`, `cover_image_url`, `latest_version?`).

## Legal

- ToS: [Replicate Terms of Service](https://replicate.com/terms).
- Model licenses: each model page / weights license applies (some **non-commercial**, some FLUX/API extra terms). Customer must read the model card before production use.
- Rate limits: account credits; HTTP 401/402 (payment)/429. No 429 retry storm. Cold boots can make `starting` last seconds to minutes.
- PII: `input` is stored and visible on the Replicate dashboard; **API predictions delete input/output/logs after about one hour** by default (`data_removed`). Still a transfer to Replicate. Customer is controller. Do not send PHI.
- Official-API-first: `api.replicate.com` only. No Discord bots.

## Implementation notes

- JDK HTTP only. Auth: `Authorization: Bearer r8_…` (docs now say Bearer, not `Token`).
- Create: if still `starting`/`processing` after Prefer wait, poll GET every 1s then backoff to 5s. Bound by `poll_timeout_seconds`.
- File outputs: if `output` is a URL or list of URLs, fetch with size cap (e.g. 50 MiB) into platform `file` refs; do not put megabytes on the edge.
- Errors: never throw past dispatch. `{error:"Replicate API error: HTTP …"}` with redacted token (`r8_` substrings).
- Empty `api_token` → `Missing api_token configuration.`
- Egress: `api.replicate.com:443` plus `replicate.delivery` / model output CDNs when downloading files.
- Do not log `input` prompt bodies or `api_token`.
