# Cohere

Official Cohere API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_COHERE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Cohere API **v2** (chat, embed, rerank) and **v1** (tokenize). Documented REST as of 2026-08-20. `POST /v1/classify` was retired by Cohere on 2025-09-25 and is not an operation.
- **Origin:** `https://api.cohere.com`
- **Docs:** [Chat](https://docs.cohere.com/reference/chat) · [Embed](https://docs.cohere.com/reference/embed) · [Rerank](https://docs.cohere.com/reference/rerank) · [Tokenize](https://docs.cohere.com/reference/tokenize)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One node for Cohere Chat, Embed, Rerank, and Tokenize. Ports: **IN `llm_input` / OUT `llm_output`**. **No extra FUNCTION ports.** Do not call Bedrock/Azure Marketplace Cohere from this node.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v2/chat` | Agent model |
| `embed` | POST | `/v2/embed` | Vectors (`input_type` required) |
| `rerank` | POST | `/v2/rerank` | Query + documents relevance |
| `tokenize` | POST | `/v1/tokenize` | Token ids for a model |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{operation?, messages?, tools?, texts?, query?, documents?, inputs?, text?, file?}`. Map AI Agent tool `request` fields via DuckTape. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token. |
| `model` | string | no | Default `command-a-plus-05-2026` (chat). |
| `temperature` | number | no | Chat sampling on INPUT body. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Cohere SaaS Agreement](https://cohere.com/saas-agreement)
- Prompts, documents, and images leave the host to Cohere. Customer is controller. Do not send PHI without a covering DPA. Official-API-first: no scrape of the dashboard.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `api_spec.json` sets `operationStrict: true` — wrong casing (`Chat`) is rejected; default `chat` in config is overridden when `llm_input` carries only embed/rerank/tokenize fields.
- Config/UI may store sampling knobs as strings; chat bodies coerce numeric fields before JSON so Cohere gets `float32` (e.g. `temperature`). Blank config temperature is omitted (not sent as `"0"` string).
- Tool payloads routed only to virtual `input` are merged into `llm_input` so `operation` + embed/rerank fields are not dropped.
- Embed requires `input_type` (`search_document` \| `search_query` \| `classification` \| `clustering` \| `image`). When `texts` / `images` / `inputs` are set and `input_type` is omitted, the node sends `search_document`.
- Config `model` for chat (`command-a-*`) is swapped on the wire for `embed-v4.0` / `rerank-v4.0-pro` when the resolved operation is embed/rerank.
- Partial `query` or `documents` alone routes to **rerank** (not chat) so missing fields surface as rerank validation errors.
- Tokenize remains on `/v1`. Classify does not: Cohere retired `/v1/classify` on 2025-09-25.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.
