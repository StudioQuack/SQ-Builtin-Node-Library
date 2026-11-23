# LM Studio

Official LM Studio OpenAI-compatible local server node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_LM_STUDIO`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

This dedicated node is for **native extras** (`list_models` / discovery). It is **not a replacement** for the shipped **Local LLM** node, which already covers OpenAI-compat chat through the daemon helper. Use Local LLM when you only need chat on an Engine Settings allow-listed engine. Use this node when you need LM Studio’s `/v1/models` list (and a direct chat call to that server).

## Official API

- **Name / version:** LM Studio OpenAI Compatibility Endpoints. Researched 2026-08-20.
- **Origin (default):** `http://127.0.0.1:1234/v1`
- **Docs:** [OpenAI compat](https://lmstudio.ai/docs/developer/openai-compat) · [Chat Completions](https://lmstudio.ai/docs/developer/openai-compat/chat-completions) · [List Models](https://lmstudio.ai/docs/developer/openai-compat/models) · [Authentication](https://lmstudio.ai/docs/developer/core/authentication)
- **Auth:** optional bearer. StudioQuack secret `api_key` (`Authorization: Bearer`) when Developer → Server Settings requires a token. Default server requires none. Never on INPUT. Never logged.

## Scope

One node for a **customer-hosted** LM Studio server (`lms server start --port 1234`). `operation` selects Chat Completions (`chat`) or model list (`list_models`). Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output` for `chat`. **No extra FUNCTION ports.**

Dropped: native REST `/api/v1/chat` (non-OpenAI), Responses, Completions (legacy), Embeddings, model download jobs. Those are not catalog verbs.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/chat/completions` | Chat against a loaded / JIT model |
| `list_models` | GET | `/models` | Discover ids (may include unloaded models when JIT is on) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `operation` (`chat` \| `list_models`) overrides the instance select when set. `{messages, model?, stream?, file?}` for chat. Images use type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` . Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | `chat` \| `list_models`. Default `chat`. INPUT `operation` overrides this when set. |
| `base_url` | string | no | Default `http://127.0.0.1:1234/v1`. |
| `api_key` | password | optional | Token when server auth is on. Sent as `Authorization: Bearer`. |
| `model` | string | no | Id from `list_models`. Also on INPUT. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [LM Studio App Terms of Service](https://lmstudio.ai/app-terms): personal and / or **internal business** use; not a service bureau / SaaS redistribution of the app.
- Each loaded GGUF/MLX **weight license** still applies.
- Customer owns the host. Prompts stay on that host. Official-API-first: the configured loopback origin only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Spec `baseUrl` is `{base_url}` (config, default `http://127.0.0.1:1234/v1`); paths are `/chat/completions` and `/models`.
- Config `base_url` is substituted into every operation baseUrl.
- `bodyFrom` is taken from INPUT only — put `model` on `llm_input`. `logprobs` must be a real boolean if you add it later (string `"true"` 400s).
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.

## Unverified

- Whether OpenAI-compat `/v1/*` honors the same API token toggle as `/api/v1/chat` (auth docs show REST examples).
- Exact JIT-loading contents of `GET /v1/models` vs loaded-only (docs: list “may include all downloaded models when Just-In-Time loading is enabled”).
