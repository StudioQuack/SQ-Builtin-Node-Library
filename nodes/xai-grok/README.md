# xAI Grok

Official xAI Inference API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_XAI_GROK`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** xAI Inference REST (OpenAI-compatible Chat Completions + Imagine images). Researched 2026-08-20.
- **Origin:** `https://api.x.ai`
- **Docs:** [Quickstart](https://docs.x.ai/developers/quickstart) · [Chat](https://docs.x.ai/developers/rest-api-reference/inference/chat) · [Images](https://docs.x.ai/developers/rest-api-reference/inference/images) · [Models](https://docs.x.ai/developers/models) · [Rate limits](https://docs.x.ai/developers/rate-limits)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One LLM provider for first-party Grok. `operation` selects Chat Completions (`chat`) or Imagine image generation (`image_gen`). Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

Dropped (not catalog verbs): Responses API `POST /v1/responses`, image **edits** `POST /v1/images/edits`, video, voice, Files, Batches. Do not scrape grok.x.ai.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/chat/completions` | Agent model / multimodal chat |
| `image_gen` | POST | `/v1/images/generations` | Text → image (`grok-imagine-image-2.0`) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, stream?, prompt?, …}`. Images use type `file` (platform ref-id). |
| `llm_output` | OUTPUT | `llm_output` | `{status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer. Required. |
| `model` | string | no | Chat default `grok-4.6`. Imagine: `grok-imagine-image-2.0`. |
| `max_tokens` | number | no | Chat; prefer `max_completion_tokens` on INPUT. |
| `temperature` | number | no | Chat `0–2`. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Enterprise Terms of Service](https://x.ai/legal/terms-of-service-enterprise) (API / business). Consumer Grok: [Terms of Service — Consumer](https://x.ai/legal/terms-of-service).
- [Acceptable Use Policy](https://x.ai/legal/acceptable-use-policy) (effective 2026-08-14).
- [Data Processing Addendum](https://x.ai/legal/data-processing-addendum) when xAI processes personal data on the customer’s behalf.
- [Rate limits](https://docs.x.ai/developers/rate-limits): per-model RPS/TPM by spend tier; HTTP **429**. Do not retry-storm.
- Prompts and images leave the host to xAI. Do not send PHI without a covering DPA. Official-API-first: `api.x.ai` only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `bodyFrom` is taken from INPUT only — also put `model` on `llm_input` (config `model` is not merged into the JSON body by the generic handler).
- Chat image input: jpg/jpeg/png, max **20 MiB** (models page). Resolve `file` refs before send; the generic handler does not.
- Image URLs are temporary — download promptly. `response_format` `url` (default) or `b64_json`.
- HTTP 4xx/5xx raise a BPMN error (AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED). Never log `api_key`.

## Unverified

- Whether `GET /v1/models` should be added as a catalog verb later (documented, not in this node).
- Exact Imagine `quality` enum beyond `low`/`medium` on `grok-imagine-image-2.0`.
- Whether Responses `POST /v1/responses` will replace Chat Completions as the Agent default (quickstart currently demos Responses; this node keeps catalog `chat` on Chat Completions).
