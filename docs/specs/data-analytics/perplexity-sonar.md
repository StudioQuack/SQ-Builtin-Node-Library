# Perplexity Sonar

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/perplexity-sonar/`
- **Researched:** 2026-08-20
- **Official API:** Perplexity Search API (`POST /search`) + Agent API (`POST /v1/agent`). Sonar chat completions still exist but **retire 2026-09-27**.
- **Docs:** https://docs.perplexity.ai/docs/search/quickstart · https://docs.perplexity.ai/api-reference/search-post · https://docs.perplexity.ai/docs/agent-api/quickstart.md · https://docs.perplexity.ai/docs/agent-api/migrate-from-sonar/how-to
- **Auth:** Bearer API key — secret binding: `api_key`

## Scope

One node for Perplexity’s first-party HTTP APIs.

- **`search`** — raw web hits (`title`, `url`, `snippet`, dates). Stable as of 2026-08-20. Prefer this for SERP-like lists.
- **`agent`** — grounded multi-step answer via the **Agent API** (successor to Sonar). Use this for prose answers with citations.
- **`sonar_chat`** — OpenAI-compatible `POST /v1/chat/completions` with Sonar models (`sonar`, `sonar-pro`, `sonar-reasoning-pro`, `sonar-deep-research`). **Marked sunset:** Perplexity announced Sonar endpoints retire **2026-09-27**. Keep the operation only as a compatibility path until that date; after retirement, map it to `{ error }` telling the operator to use `agent`.

Do not route through third-party OpenRouter. Do not scrape perplexity.ai HTML.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | POST | `https://api.perplexity.ai/search` | Structured web results |
| `agent` | POST | `https://api.perplexity.ai/v1/agent` | Grounded research / answer (Agent API) |
| `sonar_chat` | POST | `https://api.perplexity.ai/v1/chat/completions` | Legacy Sonar chat — **do not add after 2026-09-27** |

`/v1/agent` and `/v1/responses` are equivalent; use **`/v1/agent`** for raw JDK HTTP. OpenAI SDK compatibility alias `/v1/responses` is not required.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | `Authorization: Bearer`. Never on INPUT. |
| `timeout_seconds` | number | no | `60` | no | Search can be slow; agent/research slower. Cap at sandbox request budget. |
| `default_model` | string | no | empty | no | Default Agent/Sonar model id when input omits `model` |
| `default_max_results` | number | no | `10` | no | Search `max_results` default (API range 1–20) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo Bearer |

FUNCTION ports: none (the node does not invoke other StudioQuack nodes).

## Input fields (by operation)

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Search query (string or API-documented array if live spec allows) |
| `max_results` | number | no | 1–20, default 10 |
| `search_context_size` | string | no | `low` \| `medium` \| `high` if documented |
| `search_recency_filter` | string | no | `hour` \| `day` \| `week` \| `month` \| `year` |
| `search_after_date_filter` | string | no | Published after (API date format) |
| `search_before_date_filter` | string | no | Published before |
| `country` | string | no | If documented on live `/search` |

### `operation = agent`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `input` | string or array | yes | Agent `input`: a string query **or** role/content items (same shape as Sonar `messages`). Alias `query` → string `input`. |
| `preset` | string | no | e.g. `fast`, `low` (bundles web search). Prefer preset for new workflows. |
| `model` | string | no | Agent slug (e.g. `perplexity/sonar`). Do not send both a conflicting preset and model unless docs allow. |
| `tools` | array | no | Default `[{ "type": "web_search" }]` when neither `preset` nor `tools` is set (Sonar-equivalent grounding). |
| `stream` | boolean | no | **Force `false`**. This node is request/response, not SSE. |

### `operation = sonar_chat` (until 2026-09-27)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | array | yes | OpenAI chat messages |
| `model` | string | no | `sonar` \| `sonar-pro` \| `sonar-reasoning-pro` \| `sonar-deep-research` |
| `stream` | boolean | no | Force `false` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `results` | array | `search` | `{ title, url, snippet, date, last_updated }` |
| `answer` | string | `agent` / `sonar_chat` | Model text |
| `citations` | array | `agent` / `sonar_chat` | URLs / citation objects as returned |
| `error` | string | failure | Include sunset text for `sonar_chat` after retirement |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` |

## Legal

- Search API: https://docs.perplexity.ai/api-reference/search-post
- Search quickstart: https://docs.perplexity.ai/docs/search/quickstart
- Sonar (sunset): https://docs.perplexity.ai/docs/sonar/quickstart
- Community retirement note (2026-08-13): Sonar → Agent API, retire 2026-09-27
- Operator accepts Perplexity API terms. Citations remain third-party content. Do not republish full crawled pages beyond the API licence.
- Prompts leave the host (GDPR disclosure). Minimize PII in `query` / `messages`.

## Implementation notes

- JDK HTTP POST JSON. Header `Authorization: Bearer {api_key}`, `Content-Type: application/json`.
- Search body cap 1 MiB; agent responses can be large — cap **2_000_000** bytes and fail closed if exceeded.
- `sonar_deep_research` / Agent research: default timeout **120 s** unless input/config sets lower. Never exceed daemon `sqs.sandbox.oci.requestTimeoutMs`.
- Empty key → error, no HTTP. `stream=true` → error, no HTTP.
- After 2026-09-27, `sonar_chat` returns `{ error: "Perplexity Sonar chat retired on 2026-09-27. Use operation=agent." }` without calling `/v1/chat/completions`.
- Agent success: map `output_text` (or concatenated `output[]` message text) to `answer`; pass through citations / search_results when present.
- Logs: operation, model id, result counts, status — never prompts, never key.
