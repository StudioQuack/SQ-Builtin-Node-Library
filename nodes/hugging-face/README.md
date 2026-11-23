# Hugging Face

Official Hugging Face Inference Providers + Hub node (`templateId` from `node_definition.json` `_id`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Hugging Face Inference Providers (router) + Hub REST. The legacy host `api-inference.huggingface.co` is **replaced** — do not call it.
- **Origin (inference):** `https://router.huggingface.co`
- **Origin (Hub, official):** `https://huggingface.co`
- **Docs:** [Inference Providers](https://huggingface.co/docs/inference-providers/en/index) · [Chat completion](https://huggingface.co/docs/inference-providers/tasks/chat-completion) · [Feature extraction](https://huggingface.co/docs/inference-providers/tasks/feature-extraction) · [Hub API](https://huggingface.co/docs/hub/en/api) · [Gated models](https://huggingface.co/docs/hub/en/models-gated)
- **Auth:** StudioQuack secret `api_token` (`hf_…`) → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

Two official surfaces:

1. **Inference Providers** — OpenAI-compatible **chat only** at `POST /v1/chat/completions`. Other tasks (embeddings, classification) use `POST /{provider}/models/{model}`.
2. **Hub REST** — `GET https://huggingface.co/api/models` and `/api/datasets` (metadata, not weights).

Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.** Respect **gated** model cards: HTTP 403 until the token’s user has accepted the license **in the Hub UI**. Do not scrape or auto-accept licenses.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/chat/completions` | Conversational LLMs / VLMs |
| `embeddings` | POST | `/{provider}/models/{model}` | Feature-extraction vectors |
| `inference` | POST | `/{provider}/models/{model}` | Generic task `inputs` JSON |
| `models_search` | GET | `/api/models` | Hub model search (see dual-host note) |
| `datasets_search` | GET | `/api/datasets` | Hub dataset search (see dual-host note) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | Chat `messages`; embeddings/inference `inputs`; Hub `search`. Media: type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` . Secrets redacted. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | chat | no | Official Hugging Face verb. EXACT values: chat / embeddings / inference / models_search / datasets_search. Default chat = POST router /v1/chat/completions. |
| `api_token` | password | yes |  | yes | Hugging Face user access token (hf_…) with Inference Providers permission. Sent as Authorization: Bearer. StudioQuack secret binding only. Never logged. Empty → error 'api_token... |
| `model` | string | no |  | no | Hub id org/name. Chat may append :fastest / :cheapest / :providerName (official routing suffix). Options are curated examples only — the Hub catalog is unbounded. Verify at http... |
| `provider` | string | no | hf-inference | no | Inference Providers id for embeddings/inference path /{provider}/models/{model}. Default hf-inference. Chat: prefer model suffix over this field. Verify live providers page befo... |
| `temperature` | string | no |  | no | Request-body parameter `temperature` for operation: chat. |
| `response_format` | string | no |  | no | Request-body parameter `response_format` for operation: chat. |
| `messages` | object | no |  | no | Conversation for operation=chat. List of {role, content}. VLMs may include image_url parts. Image sources use type file. Example: [{"role":"user","content":"Hello"}]. |
| `seed` | string | no |  | no | Request-body parameter `seed` for operation: chat. |
| `stop` | number | no |  | no | Request-body parameter `stop` for operation: chat. |
| `logprobs` | string | no |  | no | Request-body parameter `logprobs` for operation: chat. |
| `max_tokens` | string | no |  | no | Request-body parameter `max_tokens` for operation: chat. |
| `tool_choice` | string | no |  | no | Request-body parameter `tool_choice` for operation: chat. |
| `frequency_penalty` | string | no |  | no | Request-body parameter `frequency_penalty` for operation: chat. |
| `tools` | list | no |  | no | Request-body parameter `tools` for operation: chat. |
| `presence_penalty` | string | no |  | no | Request-body parameter `presence_penalty` for operation: chat. |
| `top_p` | string | no |  | no | Request-body parameter `top_p` for operation: chat. |
| `top_logprobs` | string | no |  | no | Request-body parameter `top_logprobs` for operation: chat. |
| `input` | string | no |  | no | Request-body parameter `input` for operation: embeddings. |
| `prompt_name` | string | no |  | no | Request-body parameter `prompt_name` for operation: embeddings. |
| `truncate` | number | no |  | no | Request-body parameter `truncate` for operation: embeddings. |
| `normalize` | string | no |  | no | Request-body parameter `normalize` for operation: embeddings. |
| `inputs` | object | no |  | no | Feature-extraction / generic inference payload (string, string[], or task JSON). Used by embeddings and inference. |
| `truncation_direction` | string | no |  | no | Request-body parameter `truncation_direction` for operation: embeddings. |
| `parameters` | object | no |  | no | Request-body parameter `parameters` for operation: inference. |
| `direction` | string | no |  | no | Query-string parameter `direction` for operations: models_search, datasets_search. |
| `full` | string | no |  | no | Query-string parameter `full` for operations: models_search, datasets_search. |
| `filter` | string | no |  | no | Query-string parameter `filter` for operations: models_search, datasets_search. |
| `sort` | string | no |  | no | Query-string parameter `sort` for operations: models_search, datasets_search. |
| `limit` | number | no |  | no | Query-string parameter `limit` for operations: models_search, datasets_search. |
| `author` | string | no |  | no | Query-string parameter `author` for operations: models_search, datasets_search. |
| `config` | object | no |  | no | Query-string flag `config` for operation models_search only. true adds each model's config to the results. |
| `search` | string | no |  | no | Hub substring for models_search / datasets_search. |
| `timeout_seconds` | number | no | 300 | no | HTTP timeout. Default 300. Floor 1. Cold hf-inference models can 503/504 while loading. |
| `stream` | select | no | true | no | Purpose: controls whether Hugging Face requests a streamed completion and fans live deltas through helper emitPartial. Canonical flag. When true the node POSTs stream:true, pars... |

## Legal

- [Hugging Face Terms of Service](https://huggingface.co/terms-of-service) (Inference Providers is a listed service).
- **Model cards / licenses:** customer must comply with each model’s license. Gated access is a human accept on the model page.
- **Upstream providers:** routed calls (Together, Fireworks, Groq, …) are billed per [Inference Providers pricing](https://huggingface.co/docs/inference-providers/en/pricing). Customer accepts that provider’s terms too.
- Prompts leave the host to HF and the selected provider. Do not send PHI without a covering DPA. Official-API-first: `router.huggingface.co` and `huggingface.co/api`.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`, config key `api_token`.
- **Dual host:** `OfficialApiNode` uses a single `baseUrl` (`https://router.huggingface.co`). Hub search is officially `GET https://huggingface.co/api/models` and `GET https://huggingface.co/api/datasets`. The generic handler will prefix the router origin for those ops until a later revision honors Hub origin (do not invent a second node). Router `GET /v1/models` lists chat-routable models only.
- `hf-inference` embeddings sometimes document `/pipeline/feature-extraction` on the path; this spec uses `/{provider}/models/{model}` as the generic Inference Providers URL.
- `X-HF-Bill-To` is not sent by `OfficialApiNode`.
- HTTP 403 gated: do not retry; open the model card. Never log `hf_` tokens.

## Unverified

- Live Inference Providers dropdown (together, fireworks-ai, groq, …) — verify against the providers page before hard-coding.
- Whether `POST /hf-inference/models/{model}/pipeline/feature-extraction` is still required for some embedding models vs the generic provider path.
