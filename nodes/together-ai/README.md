# Together AI

Official Together Inference API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_TOGETHER_AI`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Together APIs **2.0.0** (OpenAPI `info.version`); OpenAI-compatible inference. Documented REST as of 2026-08-20.
- **Origin:** `https://api.together.ai`
- **Docs:** [OpenAI compatibility](https://docs.together.ai/docs/inference/openai-compatibility) · [Chat completions](https://docs.together.ai/reference/chat-completions) · [Completions](https://docs.together.ai/reference/completions) · [Embeddings](https://docs.together.ai/reference/embeddings) · [Images](https://docs.together.ai/reference/post-images-generations) · [Models](https://docs.together.ai/reference/models)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One StudioQuack node for Together serverless inference. Ports: **IN `llm_input` / OUT `llm_output`**. **No extra FUNCTION ports.** TTS/STT/video exist on Together but are not catalog verbs for this node — omitted.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/chat/completions` | Agent model / vision / tools |
| `completions` | POST | `/v1/completions` | Legacy prompt completion |
| `embeddings` | POST | `/v1/embeddings` | Dense vectors |
| `image_gen` | POST | `/v1/images/generations` | Text-to-image |
| `list_models` | GET | `/v1/models` | Discover namespaced model ids |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, prompt?, input?, file?}`. Vision/image use type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token. |
| `model` | string | no | Namespaced id (default `meta-llama/Llama-3.3-70B-Instruct-Turbo`). |
| `temperature` | number | no | Chat/completions sampling on INPUT. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Together AI Terms of Service](https://www.together.ai/terms-of-service)
- Prompts and images leave the host to Together. Open-weight model licenses still bind the customer. Do not send PHI without a covering DPA. Official-API-first: no scrape of the Together playground.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- Model ids are namespaced (`org/name`). Flat ids 404.
- HTTP 4xx/5xx raise a BPMN error (AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED). Never log `api_key`.
