# Fireworks

Official Fireworks AI Inference node (`templateId` from `node_definition.json` `_id`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Fireworks Inference REST (OpenAI-compatible). Chat OpenAPI published as Gateway REST 5.10.0; embeddings OpenAPI 0.0.1 (as of 2026-08-20).
- **Origin:** `https://api.fireworks.ai/inference/v1`
- **Docs:** [Docs home](https://docs.fireworks.ai/) · [Create chat completion](https://docs.fireworks.ai/api-reference/post-chatcompletions) · [Create completion](https://docs.fireworks.ai/api-reference/post-completions) · [Embeddings](https://docs.fireworks.ai/api-reference/creates-an-embedding-vector-representing-the-input-text) · [FLUX image FAQ](https://docs.fireworks.ai/faq-new/models-inference/flux-image-generation)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One LLM provider for Fireworks.ai, Inc. serverless (and dedicated) inference. `operation` selects chat, completions, embeddings, or FLUX `image_gen`. Image generation is **not** `POST /v1/images/generations` — it is the workflow path `POST /inference/v1/workflows/accounts/fireworks/models/{model}/text_to_image`. Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

Do not add `list_models` (Gateway account control plane) or `rerank` in this node. Do not scrape app.fireworks.ai.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/chat/completions` | Agent model / VL / tools |
| `completions` | POST | `/completions` | Raw prompt; caller owns chat template |
| `embeddings` | POST | `/embeddings` | Vectors |
| `image_gen` | POST | `/workflows/accounts/fireworks/models/{model}/text_to_image` | FLUX text-to-image (one image per call) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, prompt?, input?, stream?}`. Image use type `file` (platform ref-id). |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` or `BPMN error`. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token. |
| `model` | string | no | Chat: `accounts/fireworks/models/<id>`. Image: workflow slug (path `{model}`). |
| `base_url` | string | no | Origin override (HTTPS). Default `https://api.fireworks.ai/inference/v1`. |
| `temperature` | number | no | Chat/completions `0–2`. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Fireworks Terms of Service](https://fireworks.ai/terms-of-service) (page last updated 2026-07-10 as of research). Third-party **Model EULAs** bind the customer (Llama, FLUX, etc.). Fireworks is not the model owner.
- Prompts and media leave the host to Fireworks. Do not send PHI without a covering DPA. Official-API-first: `api.fireworks.ai` only.
- HTTP 422 validation, 429, 5xx. No 429 retry in the generic handler.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `OfficialApiNode` does not merge config `model` into JSON `bodyFrom` (INPUT keys only). Put `model` on `llm_input` as well, or a later handler revision must merge config.
- `api_spec.json` `baseUrl` is `{base_url}` (config default `https://api.fireworks.ai/inference/v1`).
- FLUX schnell may return a binary JPEG/PNG body; the generic handler JSON-parses the response. Kontext async (`get_result` poll) is **not** implemented by `OfficialApiNode` — do not invent a second node.
- HTTP 4xx/5xx → `BPMN error` on OUTPUT. Never log `api_key`.

## Unverified

- Whether live FLUX schnell still honors `Accept: image/jpeg` vs JSON `{image}` as of 2026-08-20.
- Exact Kontext poll cadence (`get_result`) — out of this generic handler.
