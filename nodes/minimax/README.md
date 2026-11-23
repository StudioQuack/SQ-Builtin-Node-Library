# MiniMax

Official MiniMax Open Platform node (`templateId` from `node_definition.json` `_id`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** MiniMax Open Platform HTTP (chat OpenAI-compat, image, T2A, video V2)
- **Origin:** `https://api.minimax.io`
- **Docs:** [API overview](https://platform.minimax.io/docs/api-reference/api-overview) · [Chat Completions](https://platform.minimax.io/docs/api-reference/text-chat-openai) · [Image generation](https://platform.minimax.io/docs/api-reference/image-generation-t2i) · [HTTP T2A](https://platform.minimax.io/docs/api-reference/speech-t2a-http) · [Video V2 create](https://platform.minimax.io/docs/api-reference/video-generation-v2-create)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One node for MiniMax chat, image, speech, and video. Pay-as-you-go API keys (not Token Plan subscription keys) are required for all modalities. Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

Voice clone, voice design, music, and Files CRUD are official but **not** catalog verbs for this node. Music APIs are closing to new users as of 2026-08-20 per MiniMax docs — do not add them.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/chat/completions` | MiniMax-M3 (+ M2.x) text / VL / tools |
| `generate_image` | POST | `/v1/image_generation` | `image-01` text-to-image / subject reference |
| `tts` | POST | `/v1/t2a_v2` | Synchronous HTTP text-to-audio |
| `generate_video` | POST | `/v2/video_generation` | MiniMax-H3 async task (returns `task_id`) |

Legacy Hailuo `POST /v1/video_generation` still exists; this node uses current V2 H3.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | Chat `messages`; image `prompt`; TTS `text`; video `content` / `model`. Media: type `file`. |
| `llm_output` | OUTPUT | `llm_output` | Video: poll query is a separate official GET — not a catalog verb. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token. |
| `model` | string | no | Per-operation model id. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [MiniMax Terms of Service](https://www.minimax.io/terms-of-service-v2.html) (framework; Open Platform product terms are incorporated).
- Voice clone: consent required; MiniMax ToS bans impersonation. Image/video likeness and content policy apply.
- Prompts and media leave the host to MiniMax. Do not send PHI without a covering DPA. Image URL responses expire (docs: 24 hours). Official-API-first: `api.minimax.io`.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `operation` on **llm_input** (or agent `input` merged into it) selects the verb; default config `chat` is overridden by `prompt` → `generate_image`, `text` → `tts`, `content`/video fields → `generate_video`.
- String-typed `messages` is rejected locally before HTTP (array of `{role, content}` required).
- HTTP **402** (balance code 1008) is a billing/entitlement signal from MiniMax — not a node defect.
- `OfficialApiNode` does not send query `GroupId`. Intl `api.minimax.io` examples omit it.
- Video H3 is async. Query `GET /v2/query/video_generation/{task_id}` is official but not implemented by the generic handler.
- TTS WebSocket T2A is out of scope (HTTP only).
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.

## Unverified

- Whether China host `api.minimaxi.com` still requires `GroupId` as a query param (intl docs do not).
- Exact live H3 query/list/cancel paths vs overview wording (`Query Task`).
