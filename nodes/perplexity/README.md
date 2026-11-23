# Perplexity

Official Perplexity AI node (`templateId` from `node_definition.json` `_id`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Perplexity Agent API + Search API (verified 2026-08-22)
- **Origin:** `https://api.perplexity.ai`
- **Docs:** [Agent quickstart](https://docs.perplexity.ai/docs/agent-api/quickstart) · [POST /v1/agent](https://docs.perplexity.ai/api-reference/agent-post) · [Migrate from Sonar](https://docs.perplexity.ai/docs/agent-api/migrate-from-sonar) · [How-to](https://docs.perplexity.ai/docs/agent-api/migrate-from-sonar/how-to) · [Presets](https://docs.perplexity.ai/docs/agent-api/presets) · [Models](https://docs.perplexity.ai/docs/agent-api/models) · [Streaming](https://docs.perplexity.ai/docs/agent-api/output-control) · [Search API](https://docs.perplexity.ai/docs/search/quickstart) · [POST /search](https://docs.perplexity.ai/api-reference/search-post) · [Sunset announcement](https://community.perplexity.ai/t/sonar-is-moving-to-the-agent-api/5802)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

Two **different** first-party HTTP APIs (verified 2026-08-22):

1. **Chat (grounded / agentic answer)** — `POST /v1/agent` (OpenAI-compat alias `POST /v1/responses`). Required body field is `input` (string or `{role,content}` items). `preset` or `model` (provider/model id, e.g. `perplexity/sonar`) is required by the API.
2. **Search** — `POST /search`. Returns ranked `{title, url, snippet, date, last_updated}`. This is **not** invented.

**Hard cutover:** Sonar Chat Completions `POST /v1/sonar` (and `/chat/completions`) **retires 2026-09-27**. This node no longer calls `/v1/sonar`.

Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.** Do not scrape perplexity.ai. Do not call OpenRouter for Perplexity.

Port schema keeps `messages` for v2 stability. Chat accepts Agent `input` directly, or v2 `messages` — `responses-sse` maps `{role, content}` strings onto Responses `input` items (`{type:message, role, content:[{type:input_text, text}]}`) and system/developer text onto `instructions`.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/agent` | Grounded / agentic answer / Agent model port |
| `search` | POST | `/search` | Raw web results for the workflow to rank |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | Chat: `input` (Agent) or v2 `messages` (mapped to `input`). Search: `query`. |
| `llm_output` | OUTPUT | `llm_output` | Chat: Agent JSON (`output_text` / `output[]` / citations). Search: `results[]`. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token. |
| `model` | string | no | Agent model id, default `perplexity/sonar`. Put on `llm_input` to send. Unused for `search`. |
| `preset` | select | no | `fast` \| `low` \| `medium` \| `high` \| `xhigh`. Sonar map: sonar→fast, sonar-pro→low, sonar-reasoning-pro→medium, sonar-deep-research→high. Put on `llm_input` to send. |
| `timeout_seconds` | number | no | Default `300`. |
| `stream` | select | no | Default `true`. Agent SSE uses engine `responses-sse` (OpenAI Responses events); live `thinking`/`answer` deltas fan on `llm_output`. |

## Legal

- [Perplexity API Terms of Service](https://www.perplexity.ai/hub/legal/perplexity-api-terms-of-service) (Agent API and Search API customers).
- Search vs Agent: different products and **pricing**. Grounded answers include third-party web content — copyright stays with sources; cite; no paywall bypass.
- Prompts and queries leave the host to Perplexity. Do not send PHI without a covering DPA. Official-API-first: `api.perplexity.ai` only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `OfficialApiNode` copies `bodyFrom` from INPUT only. Put `input` or v2 `messages`, plus `preset` and/or `model`, on `llm_input`.
- HTTP 422 is validation (do not retry). Never log `api_key`.

## Unverified / flagged

- None.
