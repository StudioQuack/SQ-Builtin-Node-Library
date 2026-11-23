# DeepSeek

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/deepseek/`
- **Researched:** 2026-08-20
- **Official API:** DeepSeek API **1.0.0** (docs OpenAPI). OpenAI-compatible Chat Completions at `https://api.deepseek.com` (HTTP path `POST /chat/completions`, **no** `/v1` prefix on the official curl). Anthropic-compatible twin at `https://api.deepseek.com/anthropic` is out of scope.
- **Docs:** [Your first API call](https://api-docs.deepseek.com/), [Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion), [List models](https://api-docs.deepseek.com/api/list-models), [Thinking mode](https://api-docs.deepseek.com/guides/thinking_mode), [Rate limit](https://api-docs.deepseek.com/quick_start/rate_limit), [Pricing](https://api-docs.deepseek.com/quick_start/pricing), [Change log](https://api-docs.deepseek.com/updates/)
- **Auth:** api_key — secret binding: `api_key`

## Scope

One StudioQuack node for Hangzhou DeepSeek Artificial Intelligence Co., Ltd. cloud inference. JDK `HttpClient` to **`https://api.deepseek.com`**. Ports: v2 `llm_input` → `llm_output`.

**There is no `reason` RPC.** Catalog verb `reason` is **dropped**. Reasoning is thinking mode on `POST /chat/completions` (`thinking` + `reasoning_effort`) on the current models `deepseek-v4-flash` and `deepseek-v4-pro`. Legacy ids `deepseek-chat` and `deepseek-reasoner` were aliases and **discontinued 2026-07-24**. Do not invent a second operation.

Do not ship FIM `POST /completions`, Responses API, or Anthropic Messages on this node (not catalog verbs). Do not scrape chat.deepseek.com.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `/chat/completions` | All chat, tools, JSON, and thinking. Agent `model` port. |
| `list_models` | GET | `/models` | Discover current ids (`deepseek-v4-flash`, `deepseek-v4-pro`). |

Dropped catalog verb: **`reason`** — not a distinct endpoint; thinking is a request body on `chat`. Added `list_models` (documented).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | Row above |
| `api_key` | password | yes | — | **yes** | `Authorization: Bearer`. Never on INPUT. Never log. |
| `model` | string | runtime yes except `list_models` | `deepseek-v4-pro` | no | Official enum: `deepseek-v4-flash` (DeepSeek-V4-Flash-0731), `deepseek-v4-pro` (DeepSeek-V4-Pro-0813). Reject `deepseek-reasoner` / `deepseek-chat` with a migration error. |
| `base_url` | string | no | `https://api.deepseek.com` | no | OfficialApiNode `baseUrl` (`{base_url}`). HTTPS. Prefer official no-`v1` path. Do not default Anthropic base. |
| `thinking` | select | no | `enabled` | no | `{type: enabled\|disabled}`. Platform default is **enabled**. |
| `reasoning_effort` | select | no | `high` | no | `low` \| `high` \| `max`. Compatibility: `medium` and `xhigh` map to `high`. |
| `temperature` | number | no | omit | no | 0–2, default 1 when sent. **No effect in thinking mode** (accepted, ignored). |
| `top_p` | number | no | omit | no | 0–1. Ignored in thinking mode. |
| `max_tokens` | number | no | omit | no | Output cap. Context 1M; max output 384K (pricing page). |
| `response_format` | string | no | empty | no | `{type: text\|json_object}`. JSON mode requires instructing JSON in the prompt. |
| `user_id` | string | no | empty | no | `[a-zA-Z0-9_-]+`, max 512. Isolation / safety. **Not PII.** |
| `stream` | select | no | `true` for `chat` | no | SSE. Keep-alives are `: keep-alive` comments — ignore them. |
| `timeout_seconds` | number | no | `300` | no | Connect 15s. Server may keep the socket open up to **10 minutes** before inference starts. |

`frequency_penalty` / `presence_penalty` are **deprecated** on this API (no effect). Do not send them.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | v2 LLM object | Unwrap like OpenAI Models. |
| `llm_output` | OUTPUT | `{ result: … }` | HTTP 200. `result.error` on failure. |

FUNCTION ports: none. File payloads: none on this API (text chat only as of this research).

## Input fields (by operation)

### `operation = chat`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | yes | `system` / `user` / `assistant` / `tool`. Assistant turns in a tool loop **must** echo `reasoning_content` or the API returns **400**. |
| `tools` | list | no | Max **128** functions. `{type:function, function:{name, description, parameters, strict?}}`. `strict` is Beta. |
| `tool_choice` | string/object | no | `none` \| `auto` \| `required` \| named function |
| `stream` | boolean | no | Per-call override |
| `thinking` | object | no | `{type: enabled\|disabled}` if not using config |
| `reasoning_effort` | string | no | Per-call override |
| `user_id` | string | no | Per-call override of config |
| `logprobs` / `top_logprobs` | bool / int | no | `top_logprobs` 0–20 |

POST JSON required: `model`, `messages`. Official curl also sends `thinking` and `reasoning_effort` at the top level (raw HTTP). OpenAI SDKs stuff `thinking` in `extra_body`; this handler sends it as a JSON field.

### `operation = list_models`

No body.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `role` | string | always | `"assistant"` |
| `content` | list | success | Final answer text |
| `thoughts` / `thinking` | list | thinking mode | Map `message.reasoning_content` |
| `tool_calls` | list | tool use | `{id, name, args, type:"tool_call"}` |
| `finish_reason` | string | chat | `stop` \| `length` \| `content_filter` \| `tool_calls` \| `insufficient_system_resource` |
| `usage` | object | when present | `prompt_tokens`, `completion_tokens`, `total_tokens`, `prompt_cache_hit_tokens`, `prompt_cache_miss_tokens`, `completion_tokens_details.reasoning_tokens` |
| `models` | list | `list_models` | `{id, object, owned_by}` |
| `error` | string | failure | Redact secrets |

Between two user turns **without** tools, `reasoning_content` need not be resent (API ignores it). **With** `tools`, it must be resent.

## Legal

- ToS: [DeepSeek Open Platform Terms of Service](https://cdn.deepseek.com/policies/en-US/deepseek-open-platform-terms-of-service.html) (developer-specific; supplements [Terms of Use](https://cdn.deepseek.com/policies/en-US/deepseek-terms-of-use.html)). Privacy: [DeepSeek Privacy Policy](https://cdn.deepseek.com/policies/en-US/deepseek-privacy-policy.html).
- Rate limits: account concurrency — **500** (`deepseek-v4-pro`), **2500** (`deepseek-v4-flash`). HTTP **429** when exceeded. Optional per-`user_id` caps at the same numbers for expanded-quota accounts. No published RPM/TPM table. Do not retry 429.
- PII / residency: prompts leave the host to DeepSeek (PRC operator). Customer is controller for end-user data (Open Platform §3.3 / §5.5). Terms allow de-identified use of Inputs/Outputs to improve the service unless the account opts out of **“Improve the model for everyone”**. Do not put PHI in `user_id` or prompts.
- Official-API-first: `api.deepseek.com` only.

## Implementation notes

- JDK HTTP only. Full URL default: `POST https://api.deepseek.com/chat/completions`. If `base_url` already ends with `/v1`, POST `{base_url}/chat/completions` (some clients use that alias — if 404, retry without `/v1` once).
- Non-stream keep-alives: empty lines. Stream: ignore SSE comments. `stream_options.include_usage` is documented.
- Thinking on → strip temperature/top_p from the body or leave them (API ignores). Map `reasoning_content` deltas to `emitPartial("thinking")`.
- Empty `api_key` → `Missing api_key configuration.`
- Egress: `api.deepseek.com:443`.

## Unverified

- Whether `https://api.deepseek.com/v1/chat/completions` is a guaranteed alias (official examples omit `/v1`).
- Exact peak/off-peak pricing hours (pricing page; not needed to call).
- FIM `/completions` exists (Beta, non-thinking only) — not an operation here.
