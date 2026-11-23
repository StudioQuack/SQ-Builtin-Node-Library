# Google Gemini

Official Gemini API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_GOOGLE_GEMINI`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Gemini API / Generative Language API **v1beta** (current documented REST as of 2026-08-20).
- **Origin:** `https://generativelanguage.googleapis.com`
- **Docs:** [Gemini API docs](https://ai.google.dev/gemini-api/docs) · [API reference](https://ai.google.dev/api) · [generateContent](https://ai.google.dev/api/generate-content) · [embedContent](https://ai.google.dev/api/embeddings) · [Video](https://ai.google.dev/gemini-api/docs/video)
- **Auth:** StudioQuack secret `api_key` → header `x-goog-api-key`. Never on INPUT. Never logged. Query `key` is also documented; this node uses the header.

## Scope

First-party **Gemini API** (AI Studio keys), not Vertex. `operation` selects generateContent, Veo long-running predict, LRO poll, or embeddings. Audio/image/video **understanding** is `chat` with multimodal `parts` — not separate RPCs. Image **generation** is `generate_image` (same `:generateContent`, image model id). Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

Dropped catalog verbs (not distinct public RPCs): `analyze_audio`, `transcribe`, `analyze_image`, `analyze_video`. Do not call Imagen `:predict` (shut down). Do not use Interactions API in v1 of this node. Do not call Vertex from this node.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1beta/models/{model}:generateContent` | Agent model / multimodal understanding |
| `generate_image` | POST | `/v1beta/models/{model}:generateContent` | Native image models (not Imagen) |
| `generate_video` | POST | `/v1beta/models/{model}:predictLongRunning` | Veo LRO (`instances` / `parameters`) |
| `download_video` | GET | `/v1beta/{operation_name}` | Poll LRO until `done` |
| `embeddings` | POST | `/v1beta/models/{model}:embedContent` | Vectors |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, contents?, tools?, file?, …}`. Image/audio/video/PDF use type `file` (platform ref-id). |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` . Secrets redacted. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | chat | no | Official Gemini verb. EXACT values: chat / generate_image / generate_video / download_video / embeddings. Default chat = POST /v1beta/models/{model}:generateContent. |
| `api_key` | password | yes |  | yes | Google AI Studio API key. Sent as header x-goog-api-key. StudioQuack secret binding only. Never logged. Empty → error 'api_key is required'. |
| `model` | string | no | gemini-3.7-flash | no | Model id in the URL path (models/{model}). Default gemini-3.7-flash (latest stable Flash). Chat: gemini-3.7-flash, gemini-3.6-flash, gemini-3.5-flash, gemini-3.5-flash-lite, gem... |
| `generationConfig` | string | no |  | no | Request-body parameter `generationConfig` for operations: chat, generate_image. |
| `messages` | object | no |  | no | Conversation for chat / generate_image. Gemini REST field is contents (role user/model). OfficialApiNode does not remap messages→contents; send contents as well when the engine ... |
| `cachedContent` | string | no |  | no | Request-body parameter `cachedContent` for operation: chat. |
| `contents` | object | no |  | no | GenerateContentRequest.contents. Prefer this for a faithful REST body. |
| `systemInstruction` | string | no |  | no | Request-body parameter `systemInstruction` for operation: chat. |
| `tools` | list | no |  | no | Optional functionDeclarations / Google tools. Omit or [] sends no tools. Do not enable googleSearch unless the operator opts in (separate terms). |
| `safetySettings` | string | no |  | no | Request-body parameter `safetySettings` for operations: chat, generate_image. |
| `toolConfig` | string | no |  | no | Request-body parameter `toolConfig` for operation: chat. |
| `parameters` | object | no |  | no | Request-body parameter `parameters` for operation: generate_video. |
| `instances` | list | no |  | no | Request-body parameter `instances` for operation: generate_video. |
| `prompt` | string | no |  | no | Request-body parameter `prompt` for operation: generate_video. |
| `operation_name` | string | no |  | no | LRO name from generate_video for download_video (GET /v1beta/{operation_name}). |
| `content` | string | no |  | no | Request-body parameter `content` for operation: embeddings. |
| `embedContentConfig` | object | no |  | no | Embedding options object (taskType, title, outputDimensionality, autoTruncate) for operation: embeddings. Replaces the deprecated top-level fields. |
| `timeout_seconds` | number | no | 300 | no | HTTP timeout. Default 300. Floor 1. Video LRO may need 600. |
| `stream` | select | no | true | no | Purpose: controls whether Google Gemini requests a streamed completion and fans live deltas through helper emitPartial. Canonical flag. When true the node POSTs stream:true, par... |

## Legal

- [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms)
- [Google APIs Terms](https://developers.google.com/terms)
- Prompts and files leave the host to Google Generative Language. Paid vs unpaid data-use rules differ — read the live Additional Terms. Do not send PHI without a covering agreement. Official-API-first: no scrape of AI Studio.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `header` / `x-goog-api-key`.
- Gemini REST uses `contents`, not v2 `messages`. OfficialApiNode forwards `bodyFrom` keys as-is (no remap).
- `download_video` path-encodes `operation_name`; slashes in LRO names may become `%2F` (engine gap).
- HTTP 4xx/5xx Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.  Never log `api_key`.
