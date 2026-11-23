# Fireworks

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/fireworks/`
- **Researched:** 2026-08-20
- **Official API:** Fireworks Inference REST (OpenAI-compatible) on `https://api.fireworks.ai/inference/v1`; chat OpenAPI published as Gateway REST **5.10.0**; embeddings OpenAPI **0.0.1**
- **Docs:** [Docs home](https://docs.fireworks.ai/), [Create chat completion](https://docs.fireworks.ai/api-reference/post-chatcompletions), [Create completion](https://docs.fireworks.ai/api-reference/post-completions), [Create embeddings](https://docs.fireworks.ai/api-reference/creates-an-embedding-vector-representing-the-input-text), [Text models guide](https://docs.fireworks.ai/guides/querying-text-models), [Embeddings guide](https://docs.fireworks.ai/guides/querying-embeddings-models), [FLUX image FAQ](https://docs.fireworks.ai/faq-new/models-inference/flux-image-generation)
- **Auth:** api_key — secret binding: `api_key`

## Scope

One node for Fireworks.ai, Inc. serverless (and dedicated) inference. JDK `HttpClient` only — no Fireworks / OpenAI SDK. Chat, completions, and embeddings use the OpenAI-shaped inference host **`https://api.fireworks.ai/inference/v1`**. Image generation is **not** `POST /v1/images/generations`; it is the Fireworks **workflow** path `POST /inference/v1/workflows/accounts/fireworks/models/{model}/text_to_image` (FLUX schnell) or model-specific workflow URLs (FLUX Kontext is async + `get_result`).

Catalog verbs `chat`, `completions`, `embeddings`, `image_gen` all exist. Do **not** add `list_models` (Gateway `GET /v1/accounts/{account_id}/models` is a different control-plane API) or `rerank` (`POST /inference/v1/rerank`) in v1. Do not scrape app.fireworks.ai.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `https://api.fireworks.ai/inference/v1/chat/completions` | Instruct / VL / tools (Agent model port) |
| `completions` | POST | `https://api.fireworks.ai/inference/v1/completions` | Raw prompt; caller owns chat template |
| `embeddings` | POST | `https://api.fireworks.ai/inference/v1/embeddings` | Vectors / optional logits rerank helper |
| `image_gen` | POST | `https://api.fireworks.ai/inference/v1/workflows/accounts/fireworks/models/{model}/text_to_image` | FLUX text-to-image (one image per call) |

Kontext-style models use `POST .../workflows/accounts/fireworks/models/{model}` then `POST .../{model}/get_result` with `{id}`. Same `image_gen` operation: detect by model id (`flux-kontext-*`) and poll internally. Do not expose `get_result` as a catalog verb.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | Row above |
| `api_key` | password | yes | — | **yes** | `Authorization: Bearer`. Never on INPUT. Never log. |
| `model` | string | runtime yes | empty | no | Chat/completions: `accounts/fireworks/models/<id>` (e.g. `accounts/fireworks/models/deepseek-v3p1`). Embeddings: e.g. `nomic-ai/nomic-embed-text-v1.5` or `fireworks/qwen3-embedding-8b`. Image: workflow slug `flux-1-schnell-fp8`, `flux-kontext-pro`, `flux-kontext-max`. |
| `base_url` | string | no | `https://api.fireworks.ai/inference/v1` | no | Inference host override (HTTPS). Image workflows stay on `api.fireworks.ai`. |
| `temperature` | number | no | omit | no | Chat/completions 0–2 |
| `top_p` | number | no | omit | no | Nucleus |
| `top_k` | number | no | omit | no | 0–100 |
| `max_tokens` | number | no | omit | no | Fireworks does **not** support `max_completion_tokens` / `seed` / `stream_options` on many models. Send `max_tokens` only. |
| `frequency_penalty` | number | no | omit | no | Chat |
| `presence_penalty` | number | no | omit | no | Chat |
| `stop` | string | no | empty | no | Up to 4 sequences |
| `response_format` | string | no | empty | no | `{type:json_object}` or `json_schema` |
| `stream` | select | no | `true` for `chat` | no | SSE. Ignored for embeddings; images are non-stream. |
| `timeout_seconds` | number | no | `300` | no | Per-request. Connect 15s. Image/Kontext poll budget included. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | object — v2 LLM contract + per-op fields | Unwrap like OpenAI Models |
| `llm_output` | OUTPUT | `{ result: … }` | Failures never include secrets |

FUNCTION ports: none. Image/audio/video parts: type `file` (ref-id). Fireworks VL also accepts `image_url` / `video_url` and `mm_file://{file_id}` for Files API uploads — v1 should send HTTPS URLs or data URIs from resolved refs, not invent Fireworks Files CRUD.

## Input fields (by operation)

Shared `llm_input`: `messages`, `tools`, `stream` / `streaming`, plus `options` merge. Bare string → one user message.

### `operation = chat`

Required: `model`, `messages`. Optional (ChatCompletionRequest): `tools[]`, `tool_choice` (`auto`\|`none`\|`any`\|`required` or function / `allowed_tools` object), `stream` (default false on API; node default true), `response_format`, `temperature` (0–2), `top_k` (0–100), `user`, `prompt_cache_key`, `prompt_cache_isolation_key`, `raw_output`, `perf_metrics_in_response`, `stop`, `max_tokens`, `max_completion_tokens` (alias — **do not send both**), `top_p`. VL content parts: `text`, `image_url` `{url, detail?, max_long_side_pixel?}`, `video_url`. `url` may be link, data URI, or `mm_file://{file_id}`.

Do not send `seed` or `stream_options` (commonly rejected).

### `operation = completions`

Required: `model`, `prompt` (string; from last user text if omitted). Optional: sampling fields as documented on [Create Completion](https://docs.fireworks.ai/api-reference/post-completions). Vision completions use image placeholder tokens in `prompt` plus image list — prefer `chat` for VL.

### `operation = embeddings`

Required: `model`, `input` (string \| string[] max 2048 items \| object \| object[]). Optional: `prompt_template` (Jinja2), `dimensions` (nomic-embed-text-v1.5+), `return_logits` (int[]), `normalize` (default false). Qwen3 embeddings are **not** unit-length unless you normalize. Empty string is invalid.

### `operation = image_gen`

Required: `prompt` (from last user text). Path `{model}` from config. Optional documented FLUX fields: `seed`, `aspect_ratio` (Kontext: between 21:9 and 9:21), `output_format` (`jpeg`\|`png`), `input_image` (Kontext; URL or base64 — prefer URL from `file` ref), `prompt_upsampling`, `safety_tolerance`. Header `Accept: image/jpeg` or `image/png` for schnell (binary body + `Seed` / `Finish-Reason` headers). JSON accept returns `{request_id?, image base64?, finish reason SUCCESS\|CONTENT_FILTERED}`. Serverless FLUX: **one image per call**.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `role` | string | always | `"assistant"` |
| `content` | list | success | Text blocks |
| `tool_calls` | list | chat tools | Agent shape |
| `output_files` | list | `image_gen` | `{type:"image", mime_type, source, data, name?}`. Persist as `file` ref. Binary JPEG/PNG from schnell. |
| `finish_reason` | string | chat/completions | Provider value; map `CONTENT_FILTERED` → `content_filter` |
| `usage` | object | when present | `prompt_tokens`, `completion_tokens` / embeddings `total_tokens` |
| `perf_metrics` | object | if requested | Fireworks timing headers mirrored |
| `embeddings` | list | embeddings | `{index, embedding}` |
| `id` / `model` | string | success | Completion id / model |
| `error` | string | failure | Redacted |

## Legal

- ToS: [Fireworks Terms of Service](https://fireworks.ai/terms-of-service) (last updated 2026-07-10 on that page). Third-party **Model EULAs** bind the customer (Llama, FLUX, etc.). Fireworks is not the model owner.
- Rate limits: plan RPM/TPM; HTTP 422 validation, 429, 5xx. No 429 retry in the handler.
- PII: prompts and media leave the host to Fireworks. Customer is controller. FLUX safety checker / `CONTENT_FILTERED` is not a substitute for lawful use.
- Official-API-first: `api.fireworks.ai` only.

## Implementation notes

- JDK HTTP only. Connect 15s; request 300s default. No redirects.
- Headers: `Authorization: Bearer`, `Content-Type: application/json`. Image schnell: `Accept: image/jpeg` (or png).
- Streaming: SSE `data:` + `[DONE]`. Fireworks may omit `stream_options`; do not send it.
- Retry: strip unsupported parameter names (same pattern as OpenAI Models). No 429 backoff.
- Kontext: poll `get_result` until Ready / Error / Moderated; bound polls (e.g. 60 × 2s). Map moderated → `content_filter`.
- Empty `api_key` → `Missing api_key configuration.` Unusable input → `No input received on 'llm_input' port.`
- Egress: `api.fireworks.ai:443`.
- Never log `api_key`, `webhook_secret`, or image bytes.
