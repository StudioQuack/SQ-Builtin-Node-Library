# Anthropic Claude

Official Anthropic Claude API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_ANTHROPIC_CLAUDE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Claude API (Messages + Files). Required header `anthropic-version: 2023-06-01` (current documented version as of 2026-08-20).
- **Origin:** `https://api.anthropic.com`
- **Docs:** [API overview](https://platform.claude.com/docs/en/api/overview) · [Create a Message](https://platform.claude.com/docs/en/api/messages/create) · [Files](https://platform.claude.com/docs/en/build-with-claude/files)
- **Auth:** StudioQuack secret `api_key` → header `x-api-key`. Never on INPUT. Never logged.

## Scope

One LLM provider for first-party Claude. `operation` selects Messages (`chat`) or Files CRUD. Document and image analysis are **not** separate HTTP APIs — they are `chat` with `document` / `image` content blocks (or a Files `file_id`). Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

Dropped catalog verbs (not public endpoints): `analyze_document`, `analyze_image`, `prompt_generate`, `prompt_improve`, `prompt_templatize`. Do not scrape claude.ai. Do not call Bedrock / Vertex Claude from this node.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/messages` | Agent model / multimodal chat |
| `files_upload` | POST | `/v1/files` | Multipart upload; returns `file_id` |
| `files_get` | GET | `/v1/files/{file_id}` | File metadata |
| `files_list` | GET | `/v1/files` | Paginated metadata |
| `files_delete` | DELETE | `/v1/files/{file_id}` | Delete a stored file |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, stream?, …}` plus per-operation fields. Image/PDF use type `file` (platform ref-id). |
| `llm_output` | OUTPUT | `llm_output` | `{result: …}`. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | `x-api-key`. |
| `model` | string | no | Messages `model` (default `claude-sonnet-5`). Docs: https://platform.claude.com/docs/en/about-claude/models/overview |
| `max_tokens` | number | no | Required by Messages API. Default `1024`. |
| `temperature` | number | no | `0–1`. |
| `base_url` | string | no | Origin override (HTTPS). Default `https://api.anthropic.com`. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Anthropic Consumer Terms](https://www.anthropic.com/legal/terms)
- [Usage Policy](https://www.anthropic.com/legal/aup)
- [Rate limits](https://platform.claude.com/docs/en/api/rate-limits)
- Prompts and files leave the host to Anthropic. Do not send PHI without a covering DPA/BAA. Official-API-first: no scrape.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `header` / `x-api-key`.
- Streamed `chat` sends `anthropic-version: 2023-06-01` via `api_spec.json` `stream.extraHeaders`. Non-stream `chat` still omits it (`OfficialApiNode` applies extraHeaders only on the stream path). Docs: https://platform.claude.com/docs/en/api/versioning
- `files_upload` is documented as multipart; the generic handler JSON-encodes `bodyFrom` — large uploads may need a later multipart path.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.
