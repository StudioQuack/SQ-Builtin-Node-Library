# OpenRouter

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/openrouter/`
- **Researched:** 2026-08-20
- **Official API:** OpenRouter Chat Completions **v1** (`https://openrouter.ai/api/v1`); OpenAPI [openrouter.ai/openapi.yaml](https://openrouter.ai/openapi.yaml)
- **Docs:** [API reference overview](https://openrouter.ai/docs/api-reference/overview), [Create chat completion](https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request), [List models](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties), [Authentication](https://openrouter.ai/docs/api/reference/authentication), [App attribution](https://openrouter.ai/docs/app-attribution)
- **Auth:** api_key — secret binding: `api_key`

## Scope

One node for OpenRouter, Inc. — an **LLM aggregator**. Every `chat` call is routed to a third-party model provider. The customer (workflow owner) must accept **OpenRouter’s terms and the upstream model’s terms** for the selected `model` id. JDK `HttpClient` only.

Catalog verbs `chat` and `list_models` both exist. Do not invent embeddings / images / audio operations unless they appear as first-class OpenRouter HTTP paths in a later research pass (chat already supports image **inputs** and some image **outputs** via `modalities`). Do not scrape openrouter.ai rankings HTML.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `/api/v1/chat/completions` | Routed chat / tools / vision (Agent model port) |
| `list_models` | GET | `/api/v1/models` | Catalog of routed models (pricing, context, modalities) |

Optional (not a catalog verb; implement internally only if needed for cost audit): `GET /api/v1/generation?id=` after a completion.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | `chat` \| `list_models` |
| `api_key` | password | yes | — | **yes** | `Authorization: Bearer`. Never on INPUT. Never log. |
| `model` | string | runtime for `chat` | empty | no | OpenRouter id with org prefix, e.g. `openai/gpt-5.2`, `anthropic/claude-sonnet-4.6`. Omit only if the OpenRouter account has a default model. |
| `http_referer` | string | no | empty | no | extraHeaders `HTTP-Referer`. Omitted when blank. |
| `title` | string | no | empty | no | Header `X-OpenRouter-Title` (legacy `X-Title` also accepted by OpenRouter). Display name in rankings. |
| `base_url` | string | no | `https://openrouter.ai/api/v1` | no | HTTPS only |
| `temperature` | number | no | omit | no | 0–2 |
| `top_p` | number | no | omit | no | (0, 1] |
| `top_k` | number | no | omit | no | ≥1; ignored on OpenAI upstreams |
| `max_tokens` | number | no | omit | no | `[1, context_length)` |
| `frequency_penalty` | number | no | omit | no | −2–2 |
| `presence_penalty` | number | no | omit | no | −2–2 |
| `repetition_penalty` | number | no | omit | no | (0, 2] |
| `seed` | number | no | omit | no | Integer |
| `response_format` | string | no | empty | no | `json_object` or `json_schema` object |
| `route` | string | no | empty | no | OpenRouter `route: "fallback"` when using `models[]` |
| `stream` | select | no | `true` for `chat` | no | SSE for all routed models |
| `timeout_seconds` | number | no | `300` | no | Connect 15s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | object — v2 LLM contract | Messages / tools / stream |
| `llm_output` | OUTPUT | `{ result: … }` | Secrets redacted |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = chat`

Body fields from OpenRouter request schema ([overview](https://openrouter.ai/docs/api-reference/overview)):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | yes (or `prompt`) | `system`/`user`/`assistant`/`tool`. User content may be string or parts `{type:text}` / `{type:image_url, image_url:{url, detail?}}`. `url` = HTTPS or data URI. Resolve `file` refs to URL/data URI; never send platform ref-ids upstream. |
| `prompt` | string | if no messages | Non-chat completion shape; prefer `messages` for Agent |
| `model` | string | no | Overrides instance `model` |
| `models` | string[] | no | Fallback chain; with `route: "fallback"` |
| `provider` | object | no | OpenRouter provider preferences (order, ignore, quantizations, data collection, etc.) — pass through if present on `options.provider` |
| `tools` | list | no | OpenAI function tools; OpenRouter maps to upstreams that lack native tools |
| `tool_choice` | string/object | no | `none` \| `auto` \| `{type:function, function:{name}}` |
| `temperature` | number | no | 0–2 |
| `top_p` / `top_k` / `min_p` / `top_a` | number | no | Sampling; unsupported keys are **ignored** per model |
| `max_tokens` | number | no | |
| `stop` | string \| string[] | no | |
| `stream` | boolean | no | |
| `seed` | integer | no | |
| `frequency_penalty` / `presence_penalty` / `repetition_penalty` | number | no | |
| `logit_bias` | object | no | Ignored on non-OpenAI upstreams |
| `top_logprobs` | integer | no | |
| `response_format` | object | no | `json_object` or `json_schema` `{name, strict?, schema}` |
| `plugins` | list | no | `{id: "web"\|"file-parser"\|"response-healing"\|"context-compression", ...}` |
| `modalities` | string[] | no | `"text"`, `"image"`, `"audio"` output modalities |
| `prediction` | object | no | `{type:"content", content}` predicted outputs |
| `user` | string | no | Stable end-user id for abuse detection — **not PHI**, not email |
| `debug.echo_upstream_body` | boolean | no | Streaming only; do not enable in production (leaks transformed body to logs if mishandled) |

Assistant prefill: last message `role: assistant` is supported.

### `operation = list_models`

Query params (official list endpoint): `offset`, `limit` (max 1000; omit both for full list), `q`, `sort` (`pricing-low-to-high`, `pricing-high-to-low`, `context-high-to-low`, `throughput-high-to-low`, `latency-low-to-high`, `most-popular`, `top-weekly`, `newest`, `intelligence-high-to-low`, `coding-high-to-low`, `agentic-high-to-low`, `design-arena-elo-high-to-low`), `output_modalities`, `input_modalities`, `supported_parameters`, `model_authors`, `region` (`eu` only as of research date). Cap `limit` at 200 in the node unless input asks otherwise.

## Output fields (by operation)

OpenRouter normalizes to OpenAI chat shape. `choices` is always an array. `finish_reason` normalized: `tool_calls` \| `stop` \| `length` \| `content_filter` \| `error`. Raw value in `native_finish_reason`.

| Field | Type | When | Purpose |
|---|---|---|---|
| `role` | string | always | `"assistant"` |
| `content` | list | success | Text (and image/audio output via `output_files` when modalities request them) |
| `tool_calls` | list | tools | `{id, type, function:{name, arguments}}` → Agent `{id, name, args}` |
| `finish_reason` | string | always on choice | Normalized |
| `native_finish_reason` | string | when present | Upstream raw |
| `usage` | object | non-stream always; stream on last chunk | `prompt_tokens`, `completion_tokens`, `total_tokens`, `prompt_tokens_details.cached_tokens`, `completion_tokens_details.reasoning_tokens`, `cost`, `cost_details`, `is_byok`, `server_tool_use.web_search_requests` |
| `id` / `model` | string | success | `gen-…` id; **actual** routed model (may differ from request) |
| `models` | list | `list_models` | `{id, canonical_slug, name, created, description, context_length, architecture, pricing, top_provider, per_request_limits, supported_parameters, default_parameters, expiration_date, …}` |
| `output_files` | list | image/audio out | `file` refs |
| `error` | string | failure | Choice-level `error.message` or HTTP error; redact keys |

## Legal

- ToS: [OpenRouter Terms of Service](https://openrouter.ai/terms). OpenRouter “operates a large language model aggregator”; models may be added or removed at any time.
- **Upstream ToS:** the customer accepts each model provider’s terms (OpenAI, Anthropic, Google, Meta, etc.) for the routed id. StudioQuack does not accept those terms.
- Data residency: OpenRouter may send prompts to the selected provider’s region. `provider` preferences / `region=eu` on list are hints, not a DPA. GDPR: owner is controller.
- Rate limits: credit balance, per-key limits, upstream 429 (OpenRouter may fallback if `models`/`route` set). Node does not retry 429 against a single model unless fallback list is configured.
- `user` must not contain PHI. Attribution headers are public ranking metadata — do not put secrets there.
- Official-API-first: `openrouter.ai/api` only.

## Implementation notes

- JDK HTTP only. Connect 15s; request 300s. No redirects.
- Headers: `Authorization: Bearer`, `Content-Type: application/json`, optional `HTTP-Referer`, `X-OpenRouter-Title`, `X-OpenRouter-Categories`.
- Streaming: SSE; ignore comment payloads (OpenRouter sends SSE comments). Usage arrives on the final chunk with empty `choices` before `[DONE]`.
- Unsupported parameters are ignored by OpenRouter (not always 400). Still strip known-unsupported keys if a 400 names them.
- No 429 exponential backoff. If `models` + `route=fallback` is set, OpenRouter handles upstream 5xx/429 itself.
- Empty `api_key` → `Missing api_key configuration.` Chat without messages/prompt → `No input received on 'llm_input' port.`
- Egress: `openrouter.ai:443`. Upstream hosts are OpenRouter’s, not called directly.
- Never log API keys, `HTTP-Referer` is OK to log as a host string; do not log full prompts.
