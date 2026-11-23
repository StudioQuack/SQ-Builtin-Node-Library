# Moonshot Kimi

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/moonshot-kimi/`
- **Researched:** 2026-08-20
- **Official API:** Kimi Open Platform HTTP API (OpenAI-compatible Chat Completions). OpenAPI `info.version` **1.0.0**. International host `https://api.moonshot.ai`; China host `https://api.moonshot.cn`.
- **Docs:** [API overview](https://platform.kimi.ai/docs/api/overview), [Create chat completion](https://platform.kimi.ai/docs/api/chat), [List models](https://platform.kimi.ai/docs/api/list-models), [Vision](https://platform.kimi.ai/docs/guide/use-kimi-vision-model), [Upload file](https://platform.kimi.ai/docs/api/files-upload), [Models](https://platform.kimi.ai/docs/models), [Rate limits](https://platform.kimi.ai/docs/pricing/limits)
- **Auth:** api_key — secret binding: `api_key`

## Scope

One StudioQuack node for Moonshot AI’s Kimi Open Platform. The handler talks to **`https://api.moonshot.ai/v1`** (or the China twin) with JDK `HttpClient` (no vendor SDK). Chat and vision share `POST /v1/chat/completions`. `analyze_image` is **not** a separate RPC — it is the same path with multimodal `image_url` / `video_url` parts. Ports are the v2 agent **model** contract (`llm_input` → `llm_output`).

Do not scrape kimi.com / kimi.ai chat. Do not call Batch, Balance, or Token-estimate in v1. File upload is an implementation step inside `analyze_image` (large video / reuse), not a catalog operation.

International keys (`platform.kimi.ai`) and China keys (`platform.kimi.com`) are **not interchangeable**. Match `base_url` to the console that issued the key.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/chat/completions` | Text / tools / thinking / JSON. Agent `model` port. |
| `analyze_image` | POST | `/v1/chat/completions` | Image or video understanding. Same RPC; multimodal `content` parts. May `POST /v1/files` first (`purpose=image` or `video`) then reference `ms://<file_id>`. |
| `list_models` | GET | `/v1/models` | Discover ids and `supports_image_in` / `supports_video_in` / `supports_reasoning`. |

Catalog verbs kept: `chat`, `analyze_image`. Added `list_models` (documented). No catalog verb dropped.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | Row above |
| `api_key` | password | yes | — | **yes** | `Authorization: Bearer`. Never on INPUT. Never log. |
| `model` | string | runtime yes except `list_models` | `kimi-k3` | no | Current ids: `kimi-k3`, `kimi-k2.7-code`, `kimi-k2.7-code-highspeed`, `kimi-k2.6`, `kimi-k2.5`. Moonshot V1 (`moonshot-v1-*`, `*-vision-preview`) sunsets **2026-08-31** for new users. Do not default deprecated `kimi-k2-*`. |
| `base_url` | string | no | `https://api.moonshot.ai/v1` | no | China: `https://api.moonshot.cn/v1`. HTTPS only. |
| `temperature` | number | no | omit | no | Official range **0–1** (not 0–2). Prefer omit; model cards warn against manual sampling on some families. |
| `top_p` | number | no | omit | no | 0–1. Do not combine with temperature. |
| `max_completion_tokens` | number | no | omit | no | Output cap. `max_tokens` is deprecated on this API. K3 default 131072, max 1048576. |
| `n` | number | no | `1` | no | 1–5. Return first choice only. |
| `reasoning_effort` | select | no | omit | no | `low` \| `high` \| `max`. K3 always thinks; default effort `max` on the platform. |
| `thinking` | string | no | omit | no | JSON `{"type":"enabled"\|"disabled","keep":null\|"all"}`. K2.6/K2.7. K2.7-code thinking is always on. |
| `response_format` | string | no | empty | no | `text`, `json` → `{type:json_object}`, or JSON object (`json_schema`). |
| `stream` | select | no | `true` for `chat` / `analyze_image` | no | SSE. Ignored for `list_models`. |
| `timeout_seconds` | number | no | `300` | no | Per-request. Connect 15s. Platform may 504 after ~2 hours. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | v2 LLM object | Unwrap like OpenAI Models. Bare string → one user message. Images: typed `image` blocks or `file` refs — never inline bytes on the port. |
| `llm_output` | OUTPUT | `{ result: … }` | Always one ResultItem. Failures still HTTP 200 with `result.error`. |

FUNCTION ports: none.

## Input fields (by operation)

Shared envelope:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | `chat` / `analyze_image` | `{role, content, tool_calls?, tool_call_id?, name?, reasoning_content?}`. Roles: `system` \| `user` \| `assistant` \| `tool`. Keep `reasoning_content` on historical assistant turns when thinking is on. |
| `tools` | list | no | Agent `{name, description, request}` → `{type:"function", function:{name, description, parameters}}`. |
| `stream` / `streaming` | boolean | no | Per-call override |

### `operation = chat`

POST JSON required: `model`, `messages`. Optional documented fields: `temperature`, `top_p`, `n`, `max_completion_tokens`, `presence_penalty` (−2–2), `frequency_penalty` (−2–2), `stop`, `stream`, `logprobs`, `top_logprobs` (0–20), `response_format`, `tools`, `tool_choice`, `thinking`, `reasoning_effort`. Content may be a string **or** an array of `{type:text\|image_url\|video_url}`. Public HTTPS image URLs are **not** supported — only `data:` base64 or `ms://<file_id>`.

### `operation = analyze_image`

Same body as `chat`. Require at least one image or video part. Map `file` refs: small images → `data:image/<mime>;base64,…` (jpeg, png, gif, webp, bmp, heic, heif). SVG rejected. Video (mp4, mpeg, mov, avi, webm, …) and large assets: `POST /v1/files` multipart `file` + `purpose=image` or `purpose=video`, then `video_url`/`image_url` `{"url":"ms://<id>"}`. Recommend image ≤ 4K, video ≤ FHD. Request body cap **100 MB**. Use a vision-capable model (`supports_image_in` / `supports_video_in` from `list_models`). Video understanding: `kimi-k3`, `kimi-k2.6`, `kimi-k2.7-code` (+ highspeed).

### `operation = list_models`

No body. Bearer only.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `role` | string | always | `"assistant"` |
| `content` | list | success | `[{type:"text", text}]` |
| `tool_calls` | list | tool use | `{id, name, args, type:"tool_call"}` |
| `thoughts` / `thinking` | list | thinking models | Map `message.reasoning_content` |
| `finish_reason` | string | chat | `stop` \| `length` \| `tool_calls` |
| `usage` | object | when present | `prompt_tokens`, `completion_tokens`, `total_tokens`, `cached_tokens` |
| `models` | list | `list_models` | `{id, object, created, owned_by, context_length, supports_image_in, supports_video_in, supports_reasoning}` |
| `error` | string | failure | Redact Bearer / key |

## Legal

- ToS: [Terms of Service for Kimi OpenPlatform](https://platform.kimi.ai/docs/agreement/modeluse). Privacy: [Kimi OpenPlatform Privacy Policy](https://platform.kimi.ai/docs/agreement/userprivacy).
- Rate limits (recharge tiers, user-level not key-level, shared across models). Platform announced an August update to the table; **verify live values** on the limits page / console. Published at research time (may change): minimum **$1** recharge to call; example Tier0 (~$1): concurrency 1, RPM 3, TPM 500,000, TPD 1,500,000; Tier1 ($10): 50 / 200 / 2,000,000 / unlimited. HTTP **429**. Headers may include `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. Do not retry 429 in the handler.
- PII / residency: prompts, images, and videos leave the daemon for Moonshot (PRC and/or the international cluster depending on `base_url`). GDPR Art. 44+ — customer is controller. Do not send PHI. Files: max 1,000 files / 100 MB each / 10 GB total.
- Official-API-first: `api.moonshot.ai` or `api.moonshot.cn` only.

## Implementation notes

- JDK HTTP only. Connect 15s; request `timeout_seconds`. No redirects. Shared `HttpClient`.
- Headers: `Authorization: Bearer <api_key>`, `Content-Type: application/json`. Multipart for file upload. Stream: `Accept: text/event-stream`; SSE `data:` chunks, `[DONE]` ends. `thinking` is a top-level JSON field (raw HTTP), not an SDK-only extra.
- Map `reasoning_content` → `emitPartial("thinking")`; visible text → `emitPartial("answer")`.
- Retry: same posture as OpenAI Models — strip `Unsupported parameter` only. No 429 backoff.
- Empty `api_key` → `Missing api_key configuration.` Public URL images → explicit error (Moonshot does not fetch arbitrary URLs).
- Egress: `api.moonshot.ai:443` or `api.moonshot.cn:443`.

## Unverified

- Exact post-August-2026 tier numbers (platform said the table would change).
- Whether `GET /v1/models` returns sunset V1 ids for legacy accounts.
- Batch / estimate / balance endpoints exist but are out of this node’s operations.
