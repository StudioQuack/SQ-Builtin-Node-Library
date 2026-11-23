# Perplexity Sonar

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/perplexity-sonar` |
| `templateId` | `acc5db46-9761-546a-bde1-9ca9e92da1cb` |
| Env override | `SQ_TEMPLATE_ID_PERPLEXITY_SONAR` |
| Category | Data & Analytics |
| Official docs | https://docs.perplexity.ai/docs/agent-api/quickstart |
| Auth | `bearer` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `search` | http POST | `/search` |
| `agent` | http POST | `/v1/agent` |

Agent API (verified 2026-08-22): [quickstart](https://docs.perplexity.ai/docs/agent-api/quickstart) · [POST /v1/agent](https://docs.perplexity.ai/api-reference/agent-post) · [presets](https://docs.perplexity.ai/docs/agent-api/presets) · [models](https://docs.perplexity.ai/docs/agent-api/models) · [migrate from Sonar](https://docs.perplexity.ai/docs/agent-api/migrate-from-sonar). Search: [POST /search](https://docs.perplexity.ai/api-reference/search-post).

Presets: `fast`, `low`, `medium`, `high`, `xhigh`. Sonar Chat Completions `POST /v1/sonar` retires **2026-09-27** and is **not** called here. Map sonar→fast, sonar-pro→low, sonar-reasoning-pro→medium, sonar-deep-research→high. Agent `model` ids are `provider/model` (e.g. `perplexity/sonar`).

Agent streaming exists (`stream: true`, Responses SSE). This node does **not** declare a stream block so `output.data` stays the full Agent JSON (citations / `output[]`), not a v2 assistant envelope. Engine `openai-sse` does not parse `response.output_text.delta`.

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`. `agent` requires body field `input`.

## Legal

Perplexity API terms. Search POST /search is current. Agent API POST /v1/agent is the default for new work. Prompts leave the host.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
