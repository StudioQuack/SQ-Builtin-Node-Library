# Anthropic Claude

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/anthropic-claude/`
- **Researched:** 2026-08-20
- **Official API:** Claude API (Messages + Files), REST at `https://api.anthropic.com`, header `anthropic-version: 2023-06-01`
- **Docs:** [API overview](https://platform.claude.com/docs/en/api/overview) · [Create a Message](https://platform.claude.com/docs/en/api/messages/create) · [Files API](https://platform.claude.com/docs/en/build-with-claude/files) · [Upload File](https://platform.claude.com/docs/en/api/files/upload)
- **Auth:** `api_key` — secret binding: `api_key` (header `x-api-key`)

## Scope

One LLM provider node for Anthropic’s first-party Claude API. `operation` selects Messages (`chat`) or Files CRUD. Document and image analysis are **not** separate HTTP APIs: they are `chat` with `document` / `image` content blocks (or a Files `file_id` source). The node sits on the AI Agent `model` FUNCTION port via the v2 `llm_input` → `llm_output` contract. **No extra FUNCTION ports.** JDK `HttpClient` only (no Anthropic SDK).

Do not scrape claude.ai. Do not call Bedrock / Vertex / Foundry Claude from this node (those are other providers). Do not ship Message Batches, Skills, Managed Agents, Sessions, or the legacy Completions API (`POST /v1/complete`).

**Dropped catalog verbs (not public endpoints today):** `analyze_document`, `analyze_image` (Messages content types, not paths); `prompt_generate`, `prompt_improve`, `prompt_templatize` (prompt-engineering guides only — no `/v1/prompts*` API).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/messages` | Agent model / multimodal chat (text, image, PDF/document, tools, thinking). Stream with `stream: true`. |
| `files_upload` | POST | `/v1/files` | Multipart upload; returns Anthropic `file_id` for later Messages `source.type=file`. |
| `files_get` | GET | `/v1/files/{file_id}` | File metadata (not file bytes for user uploads). |
| `files_list` | GET | `/v1/files` | Paginated metadata list (`limit`, `page`, optional `ids`). |
| `files_delete` | DELETE | `/v1/files/{file_id}` | Delete a stored file. |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | Row in the table above |
| `api_key` | password | yes at runtime | | **yes** | Console API key. Header `x-api-key`. Never on INPUT. Never logged. |
| `model` | string | `chat`: yes if not in `llm_input.options` | `claude-sonnet-5` | no | Messages `model` id (verify live list via Console / `GET /v1/models`) |
| `max_tokens` | number | `chat`: yes (API required) | `1024` | no | Messages `max_tokens` |
| `temperature` | number | no | omit | no | `0–1`. Omit when unset. |
| `top_p` | number | no | omit | no | Nucleus sampling |
| `top_k` | number | no | omit | no | Top-k |
| `base_url` | string | no | `https://api.anthropic.com` | no | Override host only (HTTPS). No path suffix required. |
| `anthropic_version` | string | no | `2023-06-01` | no | `anthropic-version` header. Current documented version as of 2026-08-20. |
| `stream` | select | no | `true` | no | Messages SSE. Port `llm_input.stream` wins. |
| `thinking_enabled` | boolean | no | `false` | no | When true, send Messages `thinking` (extended thinking) per current model support |
| `thinking_budget_tokens` | number | no | omit | no | `thinking.budget_tokens` when thinking is on |
| `timeout_seconds` | number | no | `300` | no | Per-request read timeout (connect 15s) |

File payloads use type `file` (platform ref-id), never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | v2 LLM object — see OpenAI Models contract, plus per-operation fields below | Bare string → one user message. Schema stubs with empty `messages` are skipped. |
| `llm_output` | OUTPUT | `{ result: <assistant or error> }` | Always one ResultItem. Failures: `result.error` (redacted). Never throw past dispatch. |

FUNCTION ports: none.

### Shared `llm_input` (all operations)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | `chat` | `{role, content, tool_calls?, tool_call_id?, name?}`. Anthropic input roles are `user` and `assistant` only; map `system` → top-level `system`; map `tool` → `user` with `tool_result` blocks. |
| `tools` | list | no | `{name, description, request:<JSON Schema>}` → Messages `{name, description, input_schema}`. |
| `stream` | boolean | no | Overrides config `stream` for `chat`. |
| `streaming` | boolean | no | Alias if `stream` omitted. |
| other keys | mixed | no | Merged into `options` (except `messages` / `tools` / `options` / `result`). |

### Shared `llm_output.result`

Success (chat): `{role:"assistant", content:[{type:"text", text}], tool_calls?:[{id, name, args, type:"tool_call"}], thoughts?:[{type, text, signature?}], output_files?:[{type, mime_type, source:"file", data:<ref-id>, name?}], finish_reason?, usage?:{prompt_tokens, completion_tokens, total_tokens, cache_read_input_tokens?, cache_creation_input_tokens?}}`.

Map Anthropic `stop_reason`: `end_turn`→`stop`, `tool_use`→`tool_calls`, `max_tokens`→`length`, `refusal`/`content_filter` as documented → `content_filter`.

Error (still `role:"assistant"`): `{role:"assistant", content:[{type:"text", text:"[Anthropic provider error] …"}], finish_reason:"error", error:"<redacted>"}`. Strip `x-api-key`, `Bearer`, `sk-ant-`.

## Input fields (by operation)

### `operation = chat`

Maps to **POST `/v1/messages`**. Required headers: `x-api-key`, `anthropic-version`, `content-type: application/json`.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | yes | Alternating user/assistant turns. `content` is a string **or** blocks: `text`; `image` (`source.type` `base64` resolved from platform `file` — never persist base64 in graph JSON); `document` (PDF); `tool_use` / `tool_result`; `thinking` (assistant). Prefer `source.type=file` + Anthropic `file_id` after `files_upload`. |
| `system` | string or list | no | Top-level `system` (not a message role). Taken from the first `role=system` message if this field is omitted. |
| `model` | string | no | Overrides config `model`. |
| `max_tokens` | number | no | Overrides config. API requires a positive int. |
| `temperature` | number | no | Overrides config. |
| `top_p` | number | no | |
| `top_k` | number | no | |
| `stop_sequences` | list of string | no | Messages `stop_sequences`. |
| `tools` | list | no | Client tools. Server tools (web search, code execution, …) are out of v1 scope unless passed through as Anthropic tool unions already shaped. |
| `tool_choice` | object or string | no | `auto` / `any` / `none` / `{type:"tool", name}`. |
| `metadata` | object | no | `user_id` only if the operator needs abuse tracking — **not PHI**. |
| `thinking` | object | no | `{type:"enabled", budget_tokens}` when using extended thinking. |
| `service_tier` | string | no | As documented on Create Message (e.g. auto vs standard). |
| `stream` | boolean | no | SSE named events (`message_start`, `content_block_delta`, `message_delta`, `message_stop`). |

Image/document bytes: resolve platform `file` ref-id in the sandbox; if size &lt; ~20 MB prefer Messages `source.type=base64` **only on the wire**, never in the graph. Larger / reused files: `files_upload` then `source: {type:"file", file_id}`.

### `operation = files_upload`

**POST `/v1/files`**, `multipart/form-data`. Official example uses `file=@path`. Overview lists Files as GA; if a host returns 404 without beta, send `anthropic-beta: files-api-2025-04-14` (still documented on retrieve-metadata).

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform ref-id. Max **500 MB** (overview request-size table). |
| `filename` | string | no | Multipart filename; default from the ref. |
| `expires_in_seconds` | number | no | If the live upload schema still accepts it; omit when unsupported. |

### `operation = files_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | Anthropic file id (`file_…`). |

### `operation = files_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Default 20; range 1–1000. |
| `page` | string | no | Opaque `next_page` cursor (`page_…`). |
| `ids` | list of string | no | Filter; mutually exclusive with `page`/`limit`; max 100. |

### `operation = files_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | Path parameter. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `result.role` | string | always on chat/error | `assistant` |
| `result.content` | list | chat success / error | Text blocks |
| `result.tool_calls` | list | `stop_reason=tool_use` | Mapped from `tool_use` blocks (`id`, `name`, `input`→`args`) |
| `result.thoughts` | list | thinking models | From `thinking` blocks + optional `signature` |
| `result.finish_reason` | string | chat | Mapped stop reason |
| `result.usage` | object | chat | `input_tokens`→`prompt_tokens`, `output_tokens`→`completion_tokens` |
| `result.id` | string | files_* success | Anthropic file id |
| `result.filename` | string | files_upload / files_get | |
| `result.mime_type` | string | files metadata | |
| `result.size_bytes` | number | files metadata | |
| `result.created_at` | string | files metadata | RFC 3339 |
| `result.downloadable` | boolean | files metadata | User uploads are typically not downloadable |
| `result.data` | list | files_list | Array of FileMetadata |
| `result.next_page` | string or null | files_list | Cursor |
| `result.type` | string | files_delete | `file_deleted` |
| `result.error` | string | failure | Redacted provider or validation error |

## Legal

- ToS: [Anthropic Consumer Terms](https://www.anthropic.com/legal/terms). API customers also accept Console / commercial terms shown at key creation. Usage: [Usage Policy](https://www.anthropic.com/legal/aup).
- Rate / spend: organization **usage tiers** (Start / Build / Scale / Custom). Spend caps (Start $500, Build $1,000, Scale $200,000 USD / month as published 2026-08-20). RPM/TPM are per-tier and per-model — read [Rate limits](https://platform.claude.com/docs/en/api/rate-limits) and Console Limits. Hitting the monthly cap returns HTTP **429** `rate_limit_error` with `error.details.error_code = enforced_spend_limit_reached` (no `retry-after`). Files API has a **separate** org RPM (see Files guide). Request size: Messages **32 MB**, Files **500 MB**.
- PII / residency: prompts, files, and tool payloads **leave the customer host** to Anthropic. Controller = workflow operator. Do not send PHI without a BAA / DPA covering Anthropic. No training-data exfil from this node; do not log prompts or keys.
- Official-API-first: no scrape, no unofficial Discord/web wrapper.

## Implementation notes

- JDK HTTP only (no vendor SDK). Shared process-wide `HttpClient` (HTTP/1.1). Connect **15s**, request **`timeout_seconds` (default 300)**. Do not follow redirects. Do not send `Authorization: Bearer` unless implementing Workload Identity Federation (out of v1).
- Streaming: parse Anthropic **named** SSE events (version `2023-06-01`). `emitPartial("thinking"|"answer", delta)` best-effort over helper UDS. Assemble `tool_use` JSON by index.
- Retry: do **not** retry 429 (including spend-cap 429). Retry once on transient **529** / **500** only if `retry-after` is absent and the body is not a spend cap. Cap attempts at 2.
- Size: reject resolved files &gt; 500 MB before upload. Cap JSON body at 32 MB for Messages.
- Error mapping: HTTP 4xx/5xx → `llm_output.result.error`. Typical types: `invalid_request_error`, `authentication_error` (401), `permission_error` (403), `not_found_error` (404), `request_too_large` (413), `rate_limit_error` (429), `overloaded_error` (529), `api_error` (500). Never include `api_key` in `error`.
- Egress: `api.anthropic.com:443` (or configured `base_url` host).
- Secrets: resolve `api_key` just-in-time; add to daemon redaction set.
