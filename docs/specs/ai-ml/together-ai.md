# Together AI

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/together-ai/`
- **Researched:** 2026-08-20
- **Official API:** Together APIs **2.0.0** (OpenAPI `info.version`); OpenAI-compatible inference on `https://api.together.ai/v1`
- **Docs:** [OpenAI compatibility](https://docs.together.ai/docs/inference/openai-compatibility), [Chat completions](https://docs.together.ai/reference/chat-completions), [Completions](https://docs.together.ai/reference/completions), [Embeddings](https://docs.together.ai/reference/embeddings), [Images](https://docs.together.ai/reference/post-images-generations), [Models](https://docs.together.ai/reference/models), OpenAPI [docs.together.ai/openapi.yaml](https://docs.together.ai/openapi.yaml)
- **Auth:** api_key — secret binding: `api_key`

## Scope

One StudioQuack node for Together Computer, Inc. serverless inference. The handler talks to **`https://api.together.ai/v1`** with JDK `HttpClient` (no Together SDK). `operation` selects the HTTP verb. Chat uses the v2 agent **model** contract (`llm_input` → `llm_output`) so this node can sit on the AI Agent `model` FUNCTION port.

Together also documents TTS (`POST /v1/audio/speech`), STT (`POST /v1/audio/transcriptions`), translations, and a Together-native **video** API. Those are not catalog verbs for this node — do not ship them in v1. Do not call Assistants/Threads/Runs, OpenAI-shaped Batch, or `moderations.create` (not implemented). Fine-tuning and Together Files are out of scope.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/chat/completions` | Instruct / vision / tools / structured output (Agent model port) |
| `completions` | POST | `/v1/completions` | Legacy prompt completion (custom templates) |
| `embeddings` | POST | `/v1/embeddings` | Dense vectors for RAG / search |
| `image_gen` | POST | `/v1/images/generations` | FLUX / SD-family text-to-image |
| `list_models` | GET | `/v1/models` | Discover serverless model ids |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | Row in the table above |
| `api_key` | password | yes | — | **yes** | `Authorization: Bearer`. Project-scoped Together key. Never on INPUT. Never log. |
| `model` | string | runtime yes except `list_models` | empty | no | Together id, namespaced (`org/name`), e.g. `meta-llama/Llama-3.3-70B-Instruct-Turbo`, `BAAI/bge-large-en-v1.5`, `black-forest-labs/FLUX.1-schnell`. OpenAI flat ids 404. |
| `base_url` | string | no | `https://api.together.ai/v1` | no | Override host. Must be HTTPS. No trailing path besides `/v1`. |
| `temperature` | number | no | omit | no | Chat/completions sampling (Together docs: typically 0–1; some engines accept higher). |
| `top_p` | number | no | omit | no | Nucleus sampling |
| `top_k` | number | no | omit | no | Top-k cap |
| `max_tokens` | number | no | omit | no | Output token cap (`max_tokens` on Together; not `max_completion_tokens`) |
| `frequency_penalty` | number | no | omit | no | −2.0–2.0 |
| `presence_penalty` | number | no | omit | no | −2.0–2.0 |
| `repetition_penalty` | number | no | omit | no | >1.0 discourages loops |
| `stop` | string | no | empty | no | Comma-separated stop strings |
| `seed` | number | no | omit | no | Best-effort determinism |
| `n` | number | no | `1` | no | Completions / images count. Return first choice only on chat. Image `n` default 1. |
| `response_format` | string | no | empty | no | Chat: `text`, `json`, or JSON object string (`json_object` / `json_schema`) |
| `stream` | select | no | `true` for `chat` | no | SSE for chat/completions. Ignored for embeddings / images / list. |
| `timeout_seconds` | number | no | `300` | no | Per-request timeout. Connect 15s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | object — v2 LLM contract + per-operation fields | Same unwrap as OpenAI Models: `getInputValuesByPortValue("llm_input")`, then `inputVariables.llm_input`. Bare string → one user message. |
| `llm_output` | OUTPUT | `{ result: … }` | Always one ResultItem. Failures still HTTP 200 on this port with `result.error`. |

FUNCTION ports: none.

File payloads (image refs, generated images) use type `file` (platform ref-id). Never put Together `api_key` on this port.

## Input fields (by operation)

Shared `llm_input` envelope (all operations):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | `chat`; optional elsewhere | `{role, content, tool_calls?, tool_call_id?, name?}`. Roles: `system` \| `user` \| `assistant` \| `tool`. `content`: string or typed blocks `text` / `image`. Vision: Together accepts `image_url` (HTTPS URL or data URI). `detail` is accepted and ignored. |
| `tools` | list | no | Agent `{name, description, request:<JSON Schema>}` → Together `{type:"function", function:{name, description, parameters}}`. Ignored unless `chat`. |
| `stream` | boolean | no | Per-call override; wins over config |
| `streaming` | boolean | no | Alias if `stream` unset |
| other keys | mixed | no | Merged into `options` (exclude `messages` / `tools` / `options` / `result`) |

### `operation = chat`

POST JSON required: `model`, `messages`. Optional (Together ChatCompletionRequest): `max_tokens`, `stop[]`, `temperature`, `top_p`, `top_k`, `context_length_exceeded_behavior` (`truncate`\|`error`, default `error`), `repetition_penalty`, `stream`, `logprobs` (0–20), `echo`, `n` (1–128), `min_p`, `presence_penalty`, `frequency_penalty`, `logit_bias` (unsupported on most models — omit unless the model accepts it), `seed`, `function_call` (legacy), `response_format`, `tools`, `tool_choice`, `reasoning_effort` (`low`\|`medium`\|`high`, GPT-OSS), `reasoning.enabled`, `safety_model`, `chat_template_kwargs`. Do not send OpenAI-only `service_tier` / `store` / `metadata` / `prediction` as if they do work — Together accepts and ignores several of these.

### `operation = completions`

Required: `model`, `prompt` (string; derive from last user text if `messages` present and `prompt` omitted). Optional: same sampling fields as chat plus `echo`, `n`, `safety_model`, `min_p`, `logit_bias`, `seed`.

### `operation = embeddings`

Required: `model`, `input` (string or string[]). Derive `input` from last user text when omitted. Together EmbeddingsRequest has **only** `model` and `input`.

### `operation = image_gen`

Required: `model`, `prompt` (derive from last user text). Optional: `steps` (default 20), `image_url`, `seed`, `n` (default 1), `height` (default 1024), `width` (default 1024), `negative_prompt`, `response_format` (`base64`\|`url`), `guidance_scale` (default 3.5), `output_format` (`jpeg`\|`png`, default `jpeg`), `image_loras[]` `{path, scale}`, `reference_images[]` (HTTPS URLs), `disable_safety_checker`. Do not send inline bytes; if the workflow has a `file` ref, resolve to a Together-accepted URL or omit.

### `operation = list_models`

No body. Optional query (Together DMI list, if using that surface): `limit`, `after`. Default serverless list is `GET /v1/models` with Bearer only.

## Output fields (by operation)

Shared `llm_output.result` (chat / completions / embeddings / image_gen):

| Field | Type | When | Purpose |
|---|---|---|---|
| `role` | string | always | `"assistant"` so the Agent loop can ingest errors |
| `content` | list | success | `[{type:"text", text}]`. Embeddings: short summary; vectors live in `embeddings`. |
| `tool_calls` | list | chat tool use | `{id, name, args, type:"tool_call"}` |
| `thoughts` / `thinking` | list | reasoning models | Together puts chain-of-thought on message `reasoning` (also still accepts `reasoning_content` on input). Map to Agent `thoughts`. |
| `output_files` | list | `image_gen` | `{type:"image", mime_type, source:"url"\|"base64", data, name?}`. Prefer storing URL/bytes as platform `file` ref, not logging data. |
| `finish_reason` | string | chat/completions | Together: `stop` \| `eos` \| `length` \| `tool_calls` \| `function_call` |
| `usage` | object | when provider returns it | `prompt_tokens`, `completion_tokens`, `total_tokens`; also read `prompt_tokens_details.cached_tokens` **or** flat `usage.cached_tokens` |
| `id` / `model` | string | success | Together request id and model string |
| `embeddings` | list | `embeddings` | `{index, embedding: number[]}` |
| `models` | list | `list_models` | Provider model objects (`id`, plus fields Together returns) |
| `error` | string | failure | Redacted. Never contains `api_key` or `Bearer`. |

`list_models` success still wraps in `result` with `models` (and empty text content).

## Legal

- ToS: [Together AI Terms of Service](https://www.together.ai/terms-of-service) (also `info.termsOfService` on the OpenAPI spec). Privacy: together.ai privacy policy as linked from that page.
- Rate limits / quota: account TPM/RPM; HTTP **429** RateLimit, **503** Overloaded, **504** Timeout. This node does **not** retry 429. Cap `max_tokens` and `n`.
- PII / residency: prompts, images, and tool schemas leave the daemon host to Together (US). GDPR Art. 44+ — the workflow owner is controller and must have a lawful basis / DPA. Do not put PHI in prompts. Open-weight model licenses (Llama, FLUX, etc.) still bind the customer.
- Official-API-first: `api.together.ai` only. No scrape of together.ai playground.

## Implementation notes

- JDK HTTP only (HTTP/1.1). Connect 15s; request `timeout_seconds` (default 300). No redirects. Shared process-wide `HttpClient`.
- Headers: `Authorization: Bearer <api_key>`, `Content-Type: application/json`, `Accept: application/json`. Stream: `Accept: text/event-stream`.
- Streaming: `stream:true`; SSE `data:` JSON chunks, terminate on `data: [DONE]`. `emitPartial("thinking"|"answer")` over helper UDS; never fail the node if emit fails. If Together returns JSON instead of SSE, ingest as one chunk.
- Retry: at most 4 chat attempts **only** when the error is `Unsupported parameter: '<name>'` and that key is stripped. No 429 backoff.
- Errors: HTTP 400/401/404/429/503/504 → Together-shaped `{error:{message,type,code}}` → `result.error` = `Together API error: …` (redact Bearer / key-like tokens). Handler never throws past dispatch.
- Size caps: reject `messages` JSON > 4 MiB; embeddings `input` array length unbounded by Together except practical timeout; images `n` small.
- Egress: `api.together.ai:443` (and `api-inference.together.ai` only if `base_url` is that host).
- Model ids are namespaced. Empty `api_key` → `Missing api_key configuration.` Empty usable input → `No input received on 'llm_input' port.`
