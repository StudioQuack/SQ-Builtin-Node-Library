# Jina AI

Official Jina Reader / Search / DeepSearch / Embeddings (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_JINA_AI`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Reader:** `POST https://r.jina.ai/` JSON `{"url":"https://…"}` — [jina.ai/reader](https://jina.ai/reader/)
- **Search:** `POST https://s.jina.ai/` JSON `{"q":"…"}` — same Reader product page
- **DeepSearch:** `POST https://deepsearch.jina.ai/v1/chat/completions` OpenAI-compat, model `jina-deepsearch-v1` — [jina.ai/deepsearch](https://jina.ai/deepsearch/)
- **Embeddings:** `POST https://api.jina.ai/v1/embeddings` — [jina.ai/embeddings](https://jina.ai/embeddings/) (verified 2026-08-22)
- **Auth:** `Authorization: Bearer` `api_key` (https://jina.ai/?sui=apikey)
- OfficialApiSpec has a single `baseUrl`. Paths are `r.jina.ai/`, `s.jina.ai/`, `deepsearch.jina.ai/v1/chat/completions` concatenated onto `https://`. `embed` sets per-op `baseUrl` `https://api.jina.ai`. EU origins `eu.r.jina.ai` / `eu.s.jina.ai` exist; this package uses the global hosts.

## Scope

| operation | HTTP | URL |
|---|---|---|
| `read` | POST | `https://r.jina.ai/` |
| `search` | POST | `https://s.jina.ai/` |
| `deep_research` | POST | `https://deepsearch.jina.ai/v1/chat/completions` |
| `embed` | POST | `https://api.jina.ai/v1/embeddings` |

Embeddings model ids: `jina-embeddings-v5-text-small`, `jina-embeddings-v5-text-nano`, `jina-embeddings-v5-omni-small`, `jina-embeddings-v5-omni-nano`, `jina-embeddings-v4`, `jina-embeddings-v3`, `jina-clip-v2`. Optional body: `task`, `dimensions`, `embedding_type`, `normalized`, `late_chunking`, `truncate`.

POST JSON is preferred over GET-with-URL-in-path so secrets stay out of URLs.

**Agent tools:** INPUT shortCode `embed_input` (not `input`) maps to the embeddings `input` field so tool JSON schemas stay valid. `text` is an alias. **DeepSearch:** string items in `messages` are coerced to `{role:user, content}`; default model `jina-deepsearch-v1`. **Search:** `num` must be a positive integer when set (`0` is rejected).

## Ports

`input` / `output` only.

## Configuration

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `read` |
| `api_key` | password | **yes** | Bearer |
| `timeout_seconds` | number | no | Default 60 |

## Legal

- Operator accepts [Jina terms](https://jina.ai/reader/). Token billing follows output size (Reader), process tokens (DeepSearch), or input tokens (Embeddings).
- **Honor robots.txt.** Reader fetches URLs the operator supplies. This node does not disable robots, does not authenticate to paywalls, and must not be used to bypass them. Target-site terms still apply. Blocked sites fail honestly.
- Do not fetch `file:`, `localhost`, or link-local unless daemon egress policy already allows it (default deny).
- Page text, queries, and embedding inputs may be PII; they leave the host to Jina.
- Logs: operation, status, sizes — never Bearer, never full page bodies or embedding vectors.
