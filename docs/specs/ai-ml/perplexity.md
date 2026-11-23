# Perplexity

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/perplexity/`
- **Researched:** 2026-08-20
- **Official API:** Perplexity AI API **1.0.0** (OpenAPI `info.version`); host `https://api.perplexity.ai`
- **Docs:** [Sonar quickstart](https://docs.perplexity.ai/docs/sonar/quickstart), [Sonar POST /v1/sonar](https://docs.perplexity.ai/api-reference/sonar-post), [OpenAI compatibility](https://docs.perplexity.ai/docs/sonar/openai-compatibility), [Search API](https://docs.perplexity.ai/docs/search/quickstart), [Search POST /search](https://docs.perplexity.ai/api-reference/search-post), [Agent API](https://docs.perplexity.ai/docs/agent-api/quickstart.md), [Agent POST /v1/agent](https://docs.perplexity.ai/api-reference/agent-post.md)
- **Auth:** api_key — secret binding: `api_key`

## Scope

One node for Perplexity AI, Inc. Two **different** first-party HTTP APIs:

1. **Chat (grounded answer)** — OpenAI-compatible Sonar chat, canonical `POST /v1/sonar` (alias `POST /chat/completions`). Returns prose + citations, not a `results[]` array.
2. **Search** — `POST /search`. Returns ranked `{title, url, snippet, date, last_updated}`. This is **not** invented: it is a separate Search API.

Ports are `llm_input` / `llm_output` so `chat` can sit on the Agent `model` port.

**Sonar deprecation (official, 2026-08-20):** docs state “Sonar Chat Completions is now Agent API. Sonar will be supported until **September 27, 2026**.” New StudioQuack work should default `chat_surface=agent` (`POST /v1/agent`, alias `POST /v1/responses`). Keep `chat_surface=sonar` until that sunset so existing OpenAI-shaped `messages` map 1:1. After 2026-09-27, drop Sonar.

Do not scrape perplexity.ai web search. Do not call OpenRouter for Perplexity.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/agent` (default) or `/v1/sonar` | Grounded answer / Agent model port |
| `search` | POST | `/search` | Raw web results for the workflow to rank |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | `chat` \| `search` |
| `api_key` | password | yes | — | **yes** | `Authorization: Bearer`. Never on INPUT. Never log. |
| `model` | string | `chat` | `sonar-pro` (sonar) / required for agent | no | Sonar enum: `sonar`, `sonar-pro`, `sonar-deep-research`, `sonar-reasoning-pro`. Agent: `provider/model` e.g. `openai/gpt-5.6-sol`. |
| `chat_surface` | select | no | `agent` | no | `agent` \| `sonar`. Sonar sunset 2026-09-27. |
| `return_images` | boolean | no | false | no | Sonar |
| `return_related_questions` | boolean | no | false | no | Sonar |
| `disable_search` | boolean | no | false | no | Sonar: answer from weights only |
| `temperature` | number | no | omit | no | Sonar 0–2 |
| `max_tokens` | number | no | omit | no | Sonar max 128000 |
| `stream` | select | no | `true` for `chat` | no | SSE. Search is non-stream. |
| `timeout_seconds` | number | no | `300` | no | Deep research needs the full budget. Connect 15s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | object | Chat: messages/tools. Search: `query` or last user text. |
| `llm_output` | OUTPUT | `{ result: … }` | Include `citations` / `search_results` on chat; `results` on search |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = chat` + `chat_surface=sonar`

`POST /v1/sonar` body (`ApiChatCompletionsRequest`):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `model` | string | yes | `sonar` \| `sonar-pro` \| `sonar-deep-research` \| `sonar-reasoning-pro` |
| `messages` | list | yes | Conversation; OpenAI chat roles |
| `max_tokens` | integer | no | >0, ≤128000 |
| `stream` | boolean | no | default false on API |
| `stop` | string \| string[] | no | |
| `temperature` | number | no | 0–2 |
| `top_p` | number | no | 0–1 |
| `response_format` | object | no | `text` or `json_schema` |
| `web_search_options` | object | no | Search knobs object |
| `search_mode` | string | no | `web` \| `academic` \| `sec` |
| `return_images` | boolean | no | Image hits in response |
| `return_related_questions` | boolean | no | Follow-ups |
| `enable_search_classifier` | boolean | no | Model decides whether to search |
| `disable_search` | boolean | no | No web search |
| `search_domain_filter` | string[] | no | Allowlist domains |
| `search_language_filter` | string[] | no | ISO 639-1 |
| `search_recency_filter` | string | no | `hour`\|`day`\|`week`\|`month`\|`year` |
| `search_after_date_filter` / `search_before_date_filter` | string | no | `MM/DD/YYYY` |
| `last_updated_after_filter` / `last_updated_before_filter` | string | no | `MM/DD/YYYY` |
| `image_format_filter` / `image_domain_filter` | string[] | no | Image filters |
| `stream_mode` | string | no | `full` \| `concise` |

Map Agent `tools` to OpenAI function tools only if Sonar documents tool calling for that model; otherwise ignore tools and rely on built-in search. ⚠️ verify: tool calling on Sonar vs Agent API.

### `operation = chat` + `chat_surface=agent`

`POST /v1/agent` body (`ResponsesRequest`): `input` (required; string or input items — convert `messages` to Agent `input`), `instructions` (system text), `model` or `models[]` (max 5) or `preset` (`fast`\|`low`\|`medium`\|`high`\|`xhigh`), `max_output_tokens` (**required** for `anthropic/*`), `max_steps` (1–100), `tools` (including `{type:"web_search", search_context_size?}`), `stream`, `background` (async + poll `GET /v1/responses/{id}` — v1 should keep `background=false` and wait).

### `operation = search`

`POST /search` body (`ApiSearchRequest`):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string \| string[] | **yes** | From `options.query` or last user message |
| `max_results` | integer | no | 1–20, default 10 |
| `search_context_size` | string | no | `low` \| `medium` \| `high` (default high). Omit if using token caps. |
| `max_tokens` | integer | no | 1–1_000_000 total extract |
| `max_tokens_per_page` | integer | no | 1–1_000_000 |
| `country` | string | no | ISO 3166-1 alpha-2 |
| `search_language_filter` | string[] | no | max 20, 2-char codes |
| `search_domain_filter` | string[] | no | max 20 |
| `search_recency_filter` | string | no | hour/day/week/month/year |
| date filters | string | no | `MM/DD/YYYY` as documented |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `role` | string | always | `"assistant"` |
| `content` | list | chat success | Answer text. Search: compact summary of hits. |
| `citations` | list | sonar chat | Source URLs / citation objects Perplexity returns |
| `search_results` | list | sonar when present | Structured hits alongside the answer |
| `related_questions` | list | if requested | Follow-ups |
| `images` | list | if `return_images` | Image URLs — do not fetch bytes unless the workflow asks; store as `file` if downloaded |
| `results` | list | **search** | `{title, url, snippet, date?, last_updated?}` |
| `id` | string | search / chat | Search `id`; chat completion id |
| `finish_reason` | string | chat | |
| `usage` | object | chat | Token usage when returned |
| `error` | string | failure | Redacted. HTTP 422 → validation `detail[]` |

Cite sources in `content` when citations exist (catalog: grounded-search ToS; cite sources).

## Legal

- ToS: [Perplexity API Terms of Service](https://www.perplexity.ai/hub/legal/perplexity-api-terms-of-service) (Sonar and Agentic Research API customers). Third-party models on Agent API have additional terms listed by Perplexity.
- Search vs Sonar: different products and **pricing**. Search is metered per request/context size; Sonar/Agent add tokens + search. See [docs.perplexity.ai](https://docs.perplexity.ai/) pricing pages.
- PII: queries and conversation leave the host to Perplexity (US). Customer is controller. Grounded answers include third-party web content — copyright stays with sources; cite; no paywall bypass.
- Official-API-first: `api.perplexity.ai` only.

## Implementation notes

- JDK HTTP only. Connect 15s; 300s request (deep research may need 600s — expose `timeout_seconds`).
- Headers: `Authorization: Bearer`, `Content-Type: application/json`.
- Streaming: Sonar SSE; Agent SSE when `stream:true`. `emitPartial` for answer tokens.
- No 429 retry. 422 is validation (do not retry).
- `search` does not need an LLM model; ignore `model` or allow it unused.
- Empty key → `Missing api_key configuration.` Chat without messages → `No input received on 'llm_input' port.` Search without query → `Missing query.`
- Egress: `api.perplexity.ai:443`.
- After 2026-09-27, fail `chat_surface=sonar` with a clear migration error pointing at Agent API.
