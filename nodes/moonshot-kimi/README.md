# Moonshot Kimi

Official Kimi Open Platform node (`templateId` from `node_definition.json` `_id`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Kimi Open Platform HTTP API (OpenAI-compatible Chat Completions). OpenAPI `info.version` 1.0.0
- **Origin (intl):** `https://api.moonshot.ai/v1`
- **Origin (China):** `https://api.moonshot.cn/v1`
- **Docs:** [API overview](https://platform.kimi.ai/docs/api/overview) · [Create chat completion](https://platform.kimi.ai/docs/api/chat) · [List models](https://platform.kimi.ai/docs/api/list-models) · [Vision](https://platform.kimi.ai/docs/guide/use-kimi-vision-model) · [Upload file](https://platform.kimi.ai/docs/api/files-upload) · [Models](https://platform.kimi.ai/docs/models)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One node for Moonshot AI’s Kimi Open Platform. Chat and vision share `POST /v1/chat/completions`. Catalog `analyze_image` is **not** a separate RPC — it is the same path with multimodal `image_url` / `video_url` parts. Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

International keys (`platform.kimi.ai`) and China keys (`platform.kimi.com`) are **not interchangeable**. Match `base_url` to the console that issued the key.

Do not scrape kimi.com / kimi.ai chat. Do not call Batch, Balance, or Token-estimate in this node.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/chat/completions` | Text / tools / thinking / JSON |
| `analyze_image` | POST | `/chat/completions` | Image or video understanding (same RPC) |
| `list_models` | GET | `/models` | Discover ids and `supports_image_in` / `supports_video_in` / `supports_reasoning` |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, file?}`. Public HTTPS image URLs are **not** supported — `data:` or `ms://<file_id>` only. |
| `llm_output` | OUTPUT | `llm_output` | May include `reasoning_content`. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token. |
| `model` | string | no | Default `kimi-k3`. |
| `base_url` | string | no | Intl `https://api.moonshot.ai/v1` or China `https://api.moonshot.cn/v1`. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Terms of Service for Kimi OpenPlatform](https://platform.kimi.ai/docs/agreement/modeluse) · [Privacy](https://platform.kimi.ai/docs/agreement/userprivacy)
- Rate limits are user-level (not key-level), shared across models. HTTP 429. Do not retry 429 in the generic handler.
- Prompts, images, and videos leave the host to Moonshot (PRC and/or the international cluster depending on `base_url`). Customer is controller. Do not send PHI. Files: max 1,000 files / 100 MB each / 10 GB total (platform docs).
- Official-API-first: `api.moonshot.ai` or `api.moonshot.cn` only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `OfficialApiNode` interpolates config `base_url` as `api_spec.json` `baseUrl`. Default intl `https://api.moonshot.ai/v1`; China `https://api.moonshot.cn/v1`.
- Large video / reuse: official `POST /v1/files` (`purpose=image` or `video`) then `ms://<file_id>`. The generic handler does **not** upload first — put a data URI or existing `ms://` id on `messages`. Multipart may JSON-encode `file` until a later multipart path.
- Temperature official range is **0–1** (not 0–2). `max_tokens` is deprecated on this API — prefer `max_completion_tokens`.
- Never log `api_key`.

## Unverified

- Exact post-August-2026 rate-limit tier numbers (platform announced a table update).
- Whether `GET /v1/models` still returns sunset V1 ids for legacy accounts.
