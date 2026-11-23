# Google Vertex AI

Official Vertex AI node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_GOOGLE_VERTEX_AI`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Vertex AI / Gemini Enterprise Agent Platform REST **v1** (current documented REST as of 2026-08-20).
- **Origin:** `https://aiplatform.googleapis.com` (global host; regional `{location}-aiplatform.googleapis.com` is also documented).
- **Docs:** [publishers.models generateContent](https://cloud.google.com/vertex-ai/docs/reference/rest/v1/projects.locations.publishers.models/generateContent) · [Generative AI docs](https://cloud.google.com/vertex-ai/generative-ai/docs) · [Vertex AI REST](https://cloud.google.com/vertex-ai/docs/reference/rest)
- **Auth:** StudioQuack secret `access_token` → `Authorization: Bearer`. Operator-minted OAuth token (`https://www.googleapis.com/auth/cloud-platform`). **OfficialApiNode cannot sign service-account JSON.** Never on INPUT. Never logged.

## Scope

Enterprise Gemini (and Model Garden publishers) on **Google Cloud**, distinct from AI Studio Gemini. Ports are **IN `llm_input` / OUT `llm_output`** only. **No extra FUNCTION ports.**

Dropped catalog verb: `tune_job`. `tuningJobs.create` is real REST, but the body is a nested TuningJob resource OfficialApiNode cannot spec as flat `bodyFrom`. Do not call `generativelanguage.googleapis.com` from this node.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/projects/{project}/locations/{location}/publishers/{publisher}/models/{model}:generateContent` | Agent model / multimodal / tools |
| `embeddings` | POST | `…/models/{model}:embedContent` | Gemini embedding models on Vertex |
| `predict` | POST | `…/models/{model}:predict` | Model Garden publishers that still document Predict |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, contents?, tools?, instances?, file?}`. File/image use type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` . Secrets redacted. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | chat | no | Official Vertex verb. EXACT values: chat / embeddings / predict. Default chat = POST …/models/{model}:generateContent. |
| `access_token` | password | yes |  | yes | GCP OAuth 2.0 access token (cloud-platform). Sent as Authorization Bearer. Mint with gcloud or your SA outside this node — OfficialApiNode cannot sign service-account JSON. Neve... |
| `project` | string | yes |  | no | GCP project id (path). Required for every operation. |
| `location` | string | yes | us-central1 | no | Region in the resource path (e.g. us-central1). Must match model availability. |
| `publisher` | string | no | google | no | Path publishers/{publisher}. Default google. |
| `model` | string | no | gemini-3.7-flash | no | Model id without publishers/ prefix. Default gemini-3.7-flash (current Vertex / Agent Platform Flash). Same Gemini 3.x ids as the Gemini API: gemini-3.7-flash, gemini-3.6-flash,... |
| `generationConfig` | string | no |  | no | Request-body parameter `generationConfig` for operation: chat. |
| `messages` | object | no |  | no | Conversation for operation=chat. Vertex REST field is contents (role user/model). OfficialApiNode does not remap. Example: [{"role":"user","content":"Hello"}]. |
| `cachedContent` | string | no |  | no | Request-body parameter `cachedContent` for operation: chat. |
| `contents` | object | no |  | no | GenerateContentRequest.contents for a faithful REST body. |
| `systemInstruction` | string | no |  | no | Request-body parameter `systemInstruction` for operation: chat. |
| `tools` | list | no |  | no | Optional function declarations. Omit or [] sends no tools. |
| `safetySettings` | string | no |  | no | Request-body parameter `safetySettings` for operation: chat. |
| `toolConfig` | string | no |  | no | Request-body parameter `toolConfig` for operation: chat. |
| `labels` | string | no |  | no | Request-body parameter `labels` for operation: chat. |
| `embedContentConfig` | object | no |  | no | Embedding options object (taskType, title, outputDimensionality, autoTruncate) for operation: embeddings. Replaces the deprecated top-level fields. |
| `content` | string | no |  | no | Request-body parameter `content` for operation: embeddings. |
| `instances` | list | no |  | no | PredictRequest.instances for operation=predict. No raw file bytes — text or gs:// URIs. |
| `parameters` | object | no |  | no | Request-body parameter `parameters` for operation: predict. |
| `timeout_seconds` | number | no | 300 | no | HTTP timeout. Default 300. Floor 1. |
| `stream` | select | no | true | no | Purpose: controls whether Google Vertex AI requests a streamed completion and fans live deltas through helper emitPartial. Canonical flag. When true the node POSTs stream:true, ... |

## Legal

- [Google Cloud Terms](https://cloud.google.com/terms)
- [Service Specific Terms](https://cloud.google.com/terms/service-terms) (Generative AI Services)
- Data is processed in the configured location. Customer is GCP project owner/controller. PHI requires a BAA covering that project. Official-API-first: no scrape of Cloud Console.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer` / `access_token`.
- Vertex REST uses `contents`; OfficialApiNode does not remap v2 `messages`.
- Regional hostname `{location}-aiplatform.googleapis.com` is not interpolated (single `baseUrl`). Global `aiplatform.googleapis.com` is the documented generateContent host in current REST examples.
- HTTP 4xx/5xx Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.  Never log `access_token`.
