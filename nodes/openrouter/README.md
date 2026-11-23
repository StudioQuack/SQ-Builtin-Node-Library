# OpenRouter

Official OpenRouter aggregator node (`templateId` from `node_definition.json` `_id`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** OpenRouter Chat Completions v1. OpenAPI: [openrouter.ai/openapi.yaml](https://openrouter.ai/openapi.yaml)
- **Origin:** `https://openrouter.ai/api/v1`
- **Docs:** [API overview](https://openrouter.ai/docs/api-reference/overview) · [Create chat completion](https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request) · [List models](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties) · [Authentication](https://openrouter.ai/docs/api/reference/authentication) · [App attribution](https://openrouter.ai/docs/app-attribution)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One node for OpenRouter, Inc. — an **LLM aggregator**. Every `chat` call is routed to a third-party model provider. The customer must accept **OpenRouter’s terms and the upstream model’s terms** for the selected `model` id. Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

Do not invent embeddings / images / audio operations (chat already supports image inputs and some image outputs via `modalities`). Do not scrape openrouter.ai rankings HTML.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/chat/completions` | Routed chat / tools / vision (Agent model port) |
| `list_models` | GET | `/models` | Catalog of routed models (pricing, context, modalities) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, stream?, …}` plus list_models query fields. Image use type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` or `{error}`. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token. |
| `model` | string | no | OpenRouter id with org prefix. |
| `http_referer` | string | no | Header `HTTP-Referer` (app URL). Omitted when blank. |
| `title` | string | no | Header `X-OpenRouter-Title`. Omitted when blank. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [OpenRouter Terms of Service](https://openrouter.ai/terms). OpenRouter “operates a large language model aggregator”; models may be added or removed at any time.
- **Upstream ToS:** the customer accepts each model provider’s terms for the routed id. StudioQuack does not accept those terms.
- Prompts leave the host to OpenRouter and the selected upstream. Do not send PHI without a covering DPA. `user` must not contain PHI. Attribution headers are public ranking metadata — do not put secrets there.
- Official-API-first: `openrouter.ai/api` only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- Attribution: extraHeaders send `HTTP-Referer: {http_referer}` and `X-OpenRouter-Title: {title}` when set (blank placeholders are omitted).
- `OfficialApiNode` copies `bodyFrom` keys from INPUT only. Put `model` on `llm_input` as well.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.

## Unverified

- Whether `GET /api/v1/generation?id=` remains useful for cost audit (not a catalog verb; not implemented).
- Live `region=eu` filter semantics on list_models (documented as `eu` only as of 2026-08-20).
