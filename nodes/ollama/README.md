# Ollama

Official Ollama **native** HTTP API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_OLLAMA`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

This dedicated node is for **native extras** (`list_models`, `pull`, `embed`, `generate`, native `/api/chat`). It is **not a replacement** for the shipped **Local LLM** node, which already covers OpenAI-compat chat through the daemon helper (`helper.llm()`). Use Local LLM when you only need chat on an Engine Settings allow-listed engine. Use this node when you need Ollama’s native `/api` surface on the **customer host**.

## Official API

- **Name / version:** Ollama HTTP API (not strictly versioned; OpenAPI 0.1.0). Researched 2026-08-20.
- **Origin (default):** `http://127.0.0.1:11434` — **customer-host only**. Do not use `https://ollama.com`.
- **Docs:** [Introduction](https://docs.ollama.com/api/introduction) · [Chat](https://docs.ollama.com/api/chat) · [Generate](https://docs.ollama.com/api/generate) · [Embed](https://docs.ollama.com/api/embed) · [Authentication](https://docs.ollama.com/api/authentication) · [Pull](https://docs.ollama.com/api/pull) · [OpenAPI](https://github.com/ollama/ollama/blob/main/docs/openapi.yaml)
- **Auth:** none on loopback (official). Optional StudioQuack secret `api_key` if the operator enabled API auth. Never on INPUT. Never logged.

## Scope

One node for a **customer-hosted** Ollama daemon. Paths are native `/api/*` (not `/v1/chat/completions`). Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output` for `chat`. **No extra FUNCTION ports.**

Dropped: `/api/create`, copy, delete, push, show, ps, version. Cloud `ollama.com` is out of scope.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/api/chat` | Native chat (Agent model) |
| `generate` | POST | `/api/generate` | Completion-style prompt |
| `embed` | POST | `/api/embed` | Embeddings (`model` + `input`) |
| `list_models` | GET | `/api/tags` | Local tags / sizes |
| `pull` | POST | `/api/pull` | Download a library model |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages}` chat; `{prompt}` generate; `{input}` embed; `{model}` pull. Images use type `file`. Send **`stream`: false** so the generic handler gets one JSON object (native default is streaming NDJSON). |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` or `{error}`. Secrets redacted. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | chat | no | Official Ollama native verb. EXACT values: chat / generate / embed / list_models / pull. Default chat = POST /api/chat. |
| `base_url` | string | no | http://127.0.0.1:11434 | no | Customer-host Ollama origin, no path suffix. Default http://127.0.0.1:11434. Interpolated as api_spec baseUrl. Do not use https://ollama.com (cloud is out of scope). |
| `api_key` | password | no |  | yes | Optional Bearer token if the operator enabled Ollama API auth. Local 127.0.0.1:11434 needs none. Sent as Authorization: Bearer when set; omitted when blank. StudioQuack secret. ... |
| `model` | string | no |  | no | Local model name already pulled (or to pull). Example llama3.1. Also send on llm_input so OfficialApiNode includes it in the JSON body. |
| `messages` | object | no |  | no | Chat messages for operation=chat. List of {role, content}. Example: [{"role":"user","content":"Hello"}]. |
| `format` | string | no |  | no | Request-body parameter `format` for operations: chat, generate. |
| `think` | string | no |  | no | Request-body parameter `think` for operation: chat. |
| `options` | object | no |  | no | Request-body parameter `options` for operations: chat, generate, embed. |
| `tools` | list | no |  | no | Request-body parameter `tools` for operation: chat. |
| `keep_alive` | string | no |  | no | Request-body parameter `keep_alive` for operations: chat, generate, embed. |
| `raw` | string | no |  | no | Request-body parameter `raw` for operation: generate. |
| `suffix` | string | no |  | no | Request-body parameter `suffix` for operation: generate. |
| `system` | list | no |  | no | Request-body parameter `system` for operation: generate. |
| `template` | string | no |  | no | Request-body parameter `template` for operation: generate. |
| `images` | string | no |  | no | Request-body parameter `images` for operation: generate. |
| `prompt` | string | no |  | no | Prompt for operation=generate (POST /api/generate). Required for generate. |
| `input` | string | no |  | no | String or string[] for operation=embed (POST /api/embed). Required for embed. |
| `truncate` | number | no |  | no | Request-body parameter `truncate` for operation: embed. |
| `insecure` | string | no |  | no | Request-body parameter `insecure` for operation: pull. |
| `timeout_seconds` | number | no | 300 | no | HTTP timeout. Default 300. Raise for pull of large weights. Floor 1. |
| `stream` | select | no | true | no | Purpose: controls whether Ollama requests a streamed completion and fans live deltas through helper emitPartial. Canonical flag. When true the node POSTs stream:true, parses SSE... |

## Legal

- Ollama software: [MIT License](https://github.com/ollama/ollama/blob/main/LICENSE)
- [Ollama Terms of Service](https://ollama.com/terms) (platform / accounts; last updated May 2026). Local inference is still subject to **each model’s license** (Llama Community License, Gemma, etc.).
- Customer owns the host and the weights. Prompts stay on that host unless the operator pulled a cloud model (`*-cloud`) which then leaves via Ollama’s signed-in path — do not use cloud models with this node.
- Official-API-first: the configured loopback origin only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Config `base_url` is interpolated as `api_spec.json` `baseUrl` (default `http://127.0.0.1:11434`).
- `bodyFrom` is taken from INPUT only — put `model` / `stream:false` on `llm_input`.
- `pull` with `stream: true` (API default) returns NDJSON; set `stream: false` on INPUT.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.

## Unverified

- Whether a local `OLLAMA_API_KEY` / reverse-proxy Bearer is honored on `127.0.0.1:11434` (docs say no auth on localhost; keys are documented for `ollama.com`).
- Default example model names in docs (`gemma4`) vs what the operator has pulled.
