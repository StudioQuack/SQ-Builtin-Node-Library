# Google Gemini

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/google-gemini/`
- **Researched:** 2026-08-20
- **Official API:** Gemini API (Google AI Studio / Generative Language API) **v1beta** REST at `https://generativelanguage.googleapis.com/v1beta`
- **Docs:** [Gemini API docs](https://ai.google.dev/gemini-api/docs) · [API reference](https://ai.google.dev/api) · [All methods](https://ai.google.dev/api/all-methods) · [Embeddings](https://ai.google.dev/gemini-api/docs/embeddings) · [Image generation](https://ai.google.dev/gemini-api/docs/image-generation) · [Video](https://ai.google.dev/gemini-api/docs/video)
- **Auth:** `api_key` — secret binding: `api_key` (header `x-goog-api-key`)

## Scope

First-party **Gemini API** (AI Studio keys), **not** Vertex / Agent Platform. One node; `operation` selects the HTTP method. Chat, audio, image, and video **understanding** all use `generateContent` (multimodal `contents.parts`). Native image/video **generation** also uses `generateContent` with image/video models as of 2026-08-20. The node exposes **IN `llm_input` / OUT `llm_output`** only.

**Interactions API** (`POST /v1beta/interactions`) is GA and Google-recommended for new apps; **do not use it in v1 of this node** — `generateContent` maps 1:1 onto the Agent `model` port without server-side conversation state.

**Imagen `:predict`:** official Imagen docs state models are **deprecated and shut down on 2026-08-17**. Do **not** call `imagen-*:predict`. Use Nano Banana / Gemini image models via `generate_image` → `:generateContent`.

**Dropped catalog verbs:** `analyze_audio`, `transcribe`, `analyze_image`, `analyze_video` — not distinct RPCs; they are `chat` with audio/image/video `parts` (Files API or inline, resolved from platform `file` refs).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `/v1beta/models/{model}:generateContent` (stream: `:streamGenerateContent`) | Text + multimodal understanding + tools. Default Agent model op. |
| `generate_image` | POST | `/v1beta/models/{model}:generateContent` | Image models (e.g. `gemini-3.1-flash-image`, `gemini-3-pro-image`). Not Imagen. |
| `generate_video` | POST | `/v1beta/models/{model}:generateContent` | Veo / Gemini Omni video models per [Video generation](https://ai.google.dev/gemini-api/docs/video). If the chosen model still documents `:predictLongRunning`, use that RPC instead (same operation). |
| `download_video` | GET | `/v1beta/{name}` (Operations) then fetch the generated file URI / Files resource | Poll a long-running video job; store result as platform `file`. |
| `embeddings` | POST | `/v1beta/models/{model}:embedContent` | Vectors (`gemini-embedding-2` / `gemini-embedding-001`). |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | Row above |
| `api_key` | password | yes at runtime | | **yes** | AI Studio key. Header `x-goog-api-key`. Never on INPUT. |
| `model` | string | yes for generate/embed | `gemini-3.5-flash` | no | Model id in the URL path (`models/{model}`) |
| `base_url` | string | no | `https://generativelanguage.googleapis.com` | no | Host only |
| `api_version` | string | no | `v1beta` | no | Path prefix. `v1` is valid for some generateContent image examples; default **v1beta** matches the live reference curl. |
| `top_p` | number | no | omit | no | `generationConfig.topP` |
| `top_k` | number | no | omit | no | `generationConfig.topK` |
| `max_output_tokens` | number | no | omit | no | `generationConfig.maxOutputTokens` |
| `response_mime_type` | string | no | omit | no | e.g. `application/json` for structured output |
| `safety_settings` | string | no | omit | no | JSON array of `{category, threshold}` as in GenerateContentRequest |
| `stream` | select | no | `true` | no | `chat` only. Port override wins. |
| `timeout_seconds` | number | no | `300` | no | Read timeout; video generate/poll may need 600 |

File payloads use type `file` (platform ref-id), never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | v2 LLM object + per-operation fields | Same unwrap rules as OpenAI Models |
| `llm_output` | OUTPUT | `{ result }` | Failures in `result.error`. No secrets. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = chat`

**POST** `{base}/{api_version}/models/{model}:generateContent`  
Stream: **POST** `...:streamGenerateContent` (`alt=sse` or JSON stream as documented).

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | yes | Map roles: `user`/`model` (Gemini uses `model` not `assistant`). `system` → `systemInstruction`. `tool` → `functionResponse` parts. Content blocks: `text`; `inline_data` from platform `file` (image/audio/video/PDF) with MIME; or `file_data.file_uri` after Files upload. |
| `tools` | list | no | `{name, description, request}` → `functionDeclarations`. Optional Google tools (`googleSearch`, etc.) only if explicitly present in `options.google_tools` — do not enable Search by default (separate ToS). |
| `tool_config` | object | no | `functionCallingConfig.mode`: `AUTO` / `ANY` / `NONE` |
| `system_instruction` | string | no | Overrides system messages |
| `generation_config` | object | no | Merged over instance knobs: `temperature`, `topP`, `topK`, `maxOutputTokens`, `stopSequences`, `candidateCount` (use 1), `responseMimeType`, `responseSchema`, `thinkingConfig` |
| `safety_settings` | list | no | Overrides config JSON |
| `cached_content` | string | no | Resource name of a context cache |
| `stream` | boolean | no | |

Do not put `data:` base64 in graph JSON. Inline only on the HTTPS body after resolving a `file` ref. Files API: resumable **POST** `/upload/{api_version}/files` then reference `files/{id}` (documented max ~2 GB / 48h persistence).

### `operation = generate_image`

Same RPC as `chat`. Default model if config still points at a text Flash id: require an image-capable id (operator sets `model`).

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | yes | Last user text = prompt. Optional image `file` parts for edit/compose. |
| `generation_config` | object | no | Image size / aspect via current `imageConfig` / `generationConfig` fields documented for that model (e.g. aspect ratio, resolution 512/1K/2K/4K where supported). |
| `n` | number | no | Prefer `candidateCount` 1; extra candidates only if the model documents it. |

### `operation = generate_video`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | yes | Text prompt; optional image `file` for image-to-video. |
| `model` | string | no | Veo / Omni video model id |
| `generation_config` | object | no | Duration, aspect, audio (Veo native audio) as documented for that model |
| `operation_name` | string | no | Unused on create |

If the live model card still requires **POST** `/v1beta/models/{model}:predictLongRunning` with `{instances:[{prompt}], parameters}`, send that body instead of GenerateContentRequest. Record the returned Operation `name` on `result`.

### `operation = download_video`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `operation_name` | string | yes | e.g. `operations/…` from `generate_video` |
| `poll_timeout_seconds` | number | no | Cap wait inside this invoke (default 120; remaining polls are a new invoke) |

Handler: GET `{base}/{api_version}/{operation_name}` until `done`. On success, download the video URI / Files resource into a platform `file` ref (never return raw bytes on the port). If `generateContent` already returned inline video, this op is unnecessary — return `error` explaining the result is already on `generate_video`.

### `operation = embeddings`

**POST** `/v1beta/models/{model}:embedContent`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` or `text` | list / string | one required | `content.parts[].text` (and optional image/audio/PDF parts for `gemini-embedding-2`) |
| `task_type` | string | no | e.g. `RETRIEVAL_QUERY`, `RETRIEVAL_DOCUMENT`, `SEMANTIC_SIMILARITY`, `CLASSIFICATION`, `CLUSTERING` |
| `title` | string | no | Document title when task is retrieval document |
| `output_dimensionality` | number | no | `gemini-embedding-2`: 128–3072 (docs recommend 768 / 1536 / 3072) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `result.role` | string | chat / image / video text | `assistant` |
| `result.content` | list | success | Concatenated `candidates[0].content.parts` text |
| `result.tool_calls` | list | functionCall parts | `{id, name, args, type:"tool_call"}` |
| `result.thoughts` | list | thinking parts | |
| `result.output_files` | list | image/video | `{type, mime_type, source:"file", data:<ref-id>, name}` — **not** inline base64 on the port |
| `result.finish_reason` | string | chat | Map `finishReason`: `STOP`→`stop`, `MAX_TOKENS`→`length`, `SAFETY`→`content_filter`, `RECITATION`, etc. |
| `result.usage` | object | when `usageMetadata` present | `promptTokenCount`→`prompt_tokens`, `candidatesTokenCount`→`completion_tokens`, `totalTokenCount` |
| `result.safety_ratings` | list | when present | Pass through categories/probabilities (no extra logging of prompt text) |
| `result.embedding` | list of number | embeddings | First `embedding.values` |
| `result.embeddings` | list | embeddings batch if used | |
| `result.operation_name` | string | generate_video LRO | For `download_video` |
| `result.error` | string | failure | Redacted |

## Legal

- ToS: [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms) **and** [Google APIs Terms](https://developers.google.com/terms). Paid vs unpaid data-use rules differ (unpaid / AI Studio may allow Google to use prompts to improve products; paid Cloud-billed quota generally does not — operator must read the live Additional Terms).
- Grounding with Google Search is a **separate** Service with extra terms; do not turn it on unless the operator opts in.
- Rate limits: per-project RPM/TPM published in AI Studio / [rate-limits](https://ai.google.dev/gemini-api/docs/rate-limits) (page fetch timed out on 2026-08-20 — implementer must copy current free/paid tables from that URL at build time). HTTP 429 → `result.error`, no automatic backoff storm.
- PII / residency: traffic goes to Google Generative Language. Not the same DPA as Vertex. PHI only with a covering agreement. Minimize.
- Official-API-first: no scrape of AI Studio UI.

## Implementation notes

- JDK HTTP only. Header `x-goog-api-key` (do not put the key in query strings in logs/URLs that get logged).
- Connect 15s; read `timeout_seconds`. Video LRO: poll every 5s, jitter, stop at `poll_timeout_seconds`.
- Stream: SSE `data:` JSON chunks of `GenerateContentResponse`; `emitPartial` for text/thinking.
- Retry: none on 429; one retry on 503/500.
- Inline payload cap: prefer Files API above **20 MB**; documented inline ceiling up to **100 MB** (50 MB PDFs) — still resolve from `file` refs, never graph JSON.
- Error mapping: JSON `error.message` / `error.status` (`INVALID_ARGUMENT`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `RESOURCE_EXHAUSTED`, `UNAVAILABLE`) → `result.error`. Redact API keys.
- Egress: `generativelanguage.googleapis.com:443`.
