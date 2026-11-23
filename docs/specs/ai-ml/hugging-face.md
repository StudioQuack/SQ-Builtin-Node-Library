# Hugging Face

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/hugging-face/`
- **Researched:** 2026-08-20
- **Official API:** Hugging Face **Inference Providers** (router) + **Hub API**. The legacy Serverless “Inference API” host `api-inference.huggingface.co` is **replaced** — do not call it.
- **Docs:** [Inference Providers](https://huggingface.co/docs/inference-providers/en/index), [Chat completion task](https://huggingface.co/docs/inference-providers/tasks/chat-completion), [Feature extraction](https://huggingface.co/docs/inference-providers/tasks/feature-extraction), [Hub API](https://huggingface.co/docs/hub/en/api) (OpenAPI: [huggingface.co/.well-known/openapi.md](https://huggingface.co/.well-known/openapi.md)), [Gated models](https://huggingface.co/docs/hub/en/models-gated), [Pricing](https://huggingface.co/docs/inference-providers/en/pricing)
- **Auth:** api_key — secret binding: `api_token`

## Scope

One node for Hugging Face, Inc. Two official surfaces:

1. **Inference Providers** — routed inference. OpenAI-compatible **chat only** at `https://router.huggingface.co/v1/chat/completions`. Other tasks (embeddings, classification, …) use the router + `provider`, not the OpenAI `/v1` chat path.
2. **Hub REST** — `https://huggingface.co/api/models` and `/api/datasets` for search. This is metadata, not weights download.

`chat` uses `llm_input` / `llm_output` (Agent model port). Hub search uses the same ports (results in `result`). No FUNCTION ports.

Respect **gated** model cards: HTTP 403 until the token’s user has accepted the license **in the Hub UI**. The node must not scrape or auto-accept licenses.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `https://router.huggingface.co/v1/chat/completions` | Conversational LLMs / VLMs |
| `embeddings` | POST | `https://router.huggingface.co/{provider}/models/{model}` (feature-extraction) or `https://router.huggingface.co/hf-inference/models/{model}/pipeline/feature-extraction` when `provider=hf-inference` | Vectors |
| `inference` | POST | `https://router.huggingface.co/{provider}/models/{model}` | Generic task (`inputs` JSON) when not chat/embeddings |
| `models_search` | GET | `https://huggingface.co/api/models` | Hub model search |
| `datasets_search` | GET | `https://huggingface.co/api/datasets` | Hub dataset search |

`GET https://router.huggingface.co/v1/models` lists chat-routable models (pricing/context). Not a catalog verb — may be used internally for picker, not required in v1.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | Row above |
| `api_token` | password | yes | — | **yes** | User Access Token (`hf_…`) with **Inference Providers** permission for router calls. Hub search works with the same token (or anonymously with lower rate limits). Never on INPUT. Never log. |
| `model` | string | inference ops | empty | no | Hub id `org/name`. Chat may append routing suffix `:fastest` / `:cheapest` / `:providerName` (official). |
| `provider` | select | no | `auto` | no | Inference Providers id: `auto` (router fastest/cheapest policy) or explicit: `hf-inference`, `together`, `fireworks-ai`, `groq`, `replicate`, `fal-ai`, `cerebras`, `cohere`, `novita`, `nscale`, `openai`, `sambanova`, `scaleway`, `nebius`, `hyperbolic`, `deepinfra`, `featherless-ai`, `publicai`, `ovhcloud`, `baseten`, `wavespeed`, `zai-org` (list from InferenceClient docs as of research — **verify** against live providers page before hard-coding the dropdown). For chat, prefer model suffix over a separate header when both exist. |
| `task` | string | `inference` | empty | no | Hub pipeline tag, e.g. `text-classification`, `summarization`, `automatic-speech-recognition`. |
| `bill_to` | string | no | empty | no | Header `X-HF-Bill-To` (org name) for org billing |
| `temperature` / `top_p` / `max_tokens` / `frequency_penalty` / `presence_penalty` | number | no | omit | no | Chat body |
| `stream` | select | no | `true` for `chat` | no | SSE |
| `timeout_seconds` | number | no | `300` | no | Cold models on `hf-inference` can 503/504 while loading |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | object | Chat messages; embeddings `inputs`; search query params |
| `llm_output` | OUTPUT | `{ result: … }` | |

FUNCTION ports: none. Media: type `file`.

## Input fields (by operation)

### `operation = chat`

OpenAI-compatible body ([task spec](https://huggingface.co/docs/inference-providers/tasks/chat-completion)):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `model` | string | yes | `org/name` or `org/name:fastest` |
| `messages` | list | **yes** | Chat + optional `image_url` parts for VLMs |
| `tools` / `tool_choice` | list / any | no | Function tools when the provider supports them |
| `temperature` | number | no | −2–2 on some providers; typically 0–2 |
| `frequency_penalty` / `presence_penalty` | number | no | −2–2 |
| `max_tokens` | integer | no | |
| `top_p` / `top_k` | number | no | |
| `stream` | boolean | no | |
| `stream_options.include_usage` | boolean | no | |
| `logprobs` / `top_logprobs` | bool / int | no | |
| `response_format` | object | no | JSON / schema when supported |
| `seed` | integer | no | |
| `stop` | string[] | no | |

Header: `Authorization: Bearer hf_…`. Optional `X-HF-Bill-To`.

### `operation = embeddings`

Feature-extraction payload:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `inputs` | string \| string[] | **yes** | Last user text if omitted |
| `normalize` | boolean | no | |
| `prompt_name` | string | no | sentence-transformers prompt key |
| `truncate` | boolean | no | |
| `truncation_direction` | string | no | `left` \| `right` |

Path uses `provider` (default `hf-inference` for classic embedding models). Response: array of floats or array of arrays.

### `operation = inference`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `inputs` | any | **yes** | Task-specific: string, string[], or `{image: url}` etc. Files via `file` ref → URL the provider accepts. |
| `parameters` | object | no | Task parameters (max length, top_k, …) as on the model card |

Do not use this for chat (use `chat`).

### `operation = models_search`

GET query ([Hub API](https://huggingface.co/docs/hub/en/api)):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `search` | string | no | Substring on repo / username |
| `author` | string | no | Org or user |
| `filter` | string | no | Tag, e.g. `text-classification` |
| `sort` | string | no | `downloads`, `author`, … |
| `direction` | string | no | `-1` descending |
| `limit` | integer | no | Cap at 50 default, max 100 |
| `full` | boolean | no | Heavy payload — default false |
| `config` | boolean | no | Include config.json |

Paginate via `Link` header if present; v1 returns first page only.

### `operation = datasets_search`

Same query params against `/api/datasets`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `role` | string | always | `"assistant"` |
| `content` | list | chat / inference text | |
| `tool_calls` | list | chat | |
| `finish_reason` | string | chat | |
| `usage` | object | chat | When provider returns it |
| `embeddings` | list | embeddings | Vectors |
| `raw` | any | `inference` | Task JSON (classification scores, etc.) — size-capped |
| `models` / `datasets` | list | search | Hub cards: `id`, `sha`, `downloads`, `likes`, `tags`, `pipeline_tag`, `gated`, `private`, … |
| `gated` | boolean | search/inference error | Hint that the card needs UI accept |
| `error` | string | failure | Map 403 gated to `Gated model: accept the license on huggingface.co/<id> then retry.` Never include the token. |

## Legal

- ToS: [Hugging Face Terms of Service](https://huggingface.co/terms-of-service) (Inference Providers is a listed service). Privacy policy on huggingface.co.
- **Model cards / licenses:** customer must comply with each model’s license (Apache, Llama, Gemma gated, etc.). Gated access is a human accept on the model page; the token must belong to that account.
- **Upstream providers:** routed calls (Together, Fireworks, Groq, …) are billed per [Inference Providers pricing](https://huggingface.co/docs/inference-providers/en/pricing) (HF billed, no extra HF markup as of docs) **or** BYO provider key. Customer accepts that provider’s terms too.
- Rate limits: Hub anonymous vs authenticated; Inference Providers plan. HTTP 429 / 503 “model loading”. Retry **once** after 10s on 503 loading only; no 429 loop.
- PII: prompts leave the host to HF and the selected provider. Customer is controller.
- Official-API-first: `router.huggingface.co` and `huggingface.co/api`. No scrape of model pages.

## Implementation notes

- JDK HTTP only. Do **not** ship `huggingface_hub`. Connect 15s; request 300s.
- Chat path is OpenAI-compat; embeddings/inference are **not** `/v1/chat/completions`.
- Provider routing: if `provider` is `auto` and operation is chat, send model id only (server `:fastest` policy). If explicit, append `:{provider}` to `model` **or** use the provider path — pick one scheme and stick to it (prefer official chat: `model` field with `:provider` suffix).
- Gated 403: do not retry; tell the operator to open the model card.
- Empty token on inference ops → `Missing api_token configuration.` Hub search may proceed without a token but should still send it when present (higher limits).
- Egress: `router.huggingface.co:443`, `huggingface.co:443`.
- Never log `hf_` tokens or prompt bodies. `X-HF-Bill-To` is an org slug, not a secret, but do not put API keys there.
