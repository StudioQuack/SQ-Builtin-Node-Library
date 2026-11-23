# DeepSeek

Official DeepSeek Open Platform node (`templateId` from `node_definition.json` `_id`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** DeepSeek API 1.0.0 (docs OpenAPI). OpenAI-compatible Chat Completions.
- **Origin:** `https://api.deepseek.com` (official curl path `POST /chat/completions` — **no** `/v1` prefix)
- **Docs:** [Your first API call](https://api-docs.deepseek.com/) · [Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion) · [List models](https://api-docs.deepseek.com/api/list-models) · [Thinking mode](https://api-docs.deepseek.com/guides/thinking_mode) · [Rate limit](https://api-docs.deepseek.com/quick_start/rate_limit)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One node for Hangzhou DeepSeek Artificial Intelligence Co., Ltd. cloud inference. Ports: v2 `llm_input` → `llm_output`. **No extra FUNCTION ports.** Text on `deepseek-v4-flash` / `deepseek-v4-pro`; image input on `deepseek-v4-flash-vision-exp` (released 2026-08-21). The node does not expose DeepSeek's Files API (`file_id` uploads) — send images as content parts on chat.

**There is no `reason` RPC.** Catalog verb `reason` is **dropped**. Reasoning is thinking mode on `POST /chat/completions` (`thinking` + `reasoning_effort`) on `deepseek-v4-flash` / `deepseek-v4-pro` / `deepseek-v4-flash-vision-exp`. Legacy ids `deepseek-chat` and `deepseek-reasoner` were discontinued **2026-07-24**. Do not invent a second operation. Do not default `deepseek-reasoner`.

Do not ship FIM `POST /completions`, Responses API, or Anthropic Messages (`https://api.deepseek.com/anthropic`) on this node. Do not scrape chat.deepseek.com.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/chat/completions` | All chat, tools, JSON, and thinking. Agent `model` port. |
| `list_models` | GET | `/models` | Discover current ids (`deepseek-v4-pro`, `deepseek-v4-flash`, `deepseek-v4-flash-vision-exp`). |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, thinking?, reasoning_effort?}`. |
| `llm_output` | OUTPUT | `llm_output` | `reasoning_content` on thinking turns. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | chat | no | Official DeepSeek verb. EXACT values: chat / list_models. Default chat = POST /chat/completions. There is no reason operation. |
| `api_key` | password | yes |  | yes | DeepSeek Open Platform API key. Sent as Authorization: Bearer. StudioQuack secret binding only. Never logged. Empty → error 'api_key is required'. |
| `model` | string | no | deepseek-v4-pro | no | Official ids: deepseek-v4-pro (DeepSeek-V4-Pro-0813, default), deepseek-v4-flash (DeepSeek-V4-Flash-0731), deepseek-v4-flash-vision-exp (experimental vision, 2026-08-21). Do not... |
| `base_url` | string | no | https://api.deepseek.com | no | HTTPS origin substituted as OfficialApiNode baseUrl. Default https://api.deepseek.com (official curl has no /v1 prefix). Do not default the Anthropic twin https://api.deepseek.c... |
| `temperature` | number | no |  | no | chat sampling temperature 0–2. Official temperature. Example: 0.7. |
| `response_format` | string | no |  | no | Request-body parameter `response_format` for operation: chat. |
| `user_id` | string | no |  | no | Request-body parameter `user_id` for operation: chat. |
| `messages` | list | no |  | no | chat messages [{role, content}]. Required for chat. Official messages. |
| `reasoning_effort` | string | no |  | no | Request-body parameter `reasoning_effort` for operation: chat. |
| `stop` | list | no |  | no | chat stop sequences, strings. Official stop. |
| `logprobs` | boolean | no |  | no | Request-body parameter `logprobs` for operation: chat. |
| `thinking` | object | no |  | no | Thinking mode object {type: enabled/disabled}. Platform default is enabled. Not a separate HTTP operation. |
| `max_tokens` | number | no |  | no | chat max_tokens (completion budget). |
| `tool_choice` | string | no |  | no | Request-body parameter `tool_choice` for operation: chat. |
| `top_logprobs` | number | no |  | no | Request-body parameter `top_logprobs` for operation: chat. |
| `tools` | list | no |  | no | Optional function tools (max 128). Omit or [] sends no tools. |
| `top_p` | number | no |  | no | chat nucleus sampling 0–1. Official top_p. |
| `timeout_seconds` | number | no | 300 | no | HTTP timeout. Default 300. Floor 1. Server may keep the socket open up to 10 minutes before inference starts. |
| `stream` | select | no | true | no | Purpose: controls whether DeepSeek requests a streamed completion and fans live deltas through helper emitPartial. Canonical flag. When true the node POSTs stream:true, parses S... |

## Legal

- [DeepSeek Open Platform Terms of Service](https://cdn.deepseek.com/policies/en-US/deepseek-open-platform-terms-of-service.html) (supplements [Terms of Use](https://cdn.deepseek.com/policies/en-US/deepseek-terms-of-use.html)). Privacy: [DeepSeek Privacy Policy](https://cdn.deepseek.com/policies/en-US/deepseek-privacy-policy.html).
- Rate limits: account concurrency — **500** (`deepseek-v4-pro`), **2500** (`deepseek-v4-flash`). HTTP **429**. Do not retry 429.
- Prompts leave the host to DeepSeek (PRC operator). Customer is controller. Terms allow de-identified use of Inputs/Outputs to improve the service unless the account opts out of “Improve the model for everyone”. Do not put PHI in `user_id` or prompts.
- Official-API-first: `api.deepseek.com` only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- Official raw HTTP sends `thinking` and `reasoning_effort` as top-level JSON fields (OpenAI SDKs stuff `thinking` in `extra_body`).
- `frequency_penalty` / `presence_penalty` are **deprecated** on this API (no effect). Do not send them.
- With `tools`, `reasoning_content` must be resent on later assistant turns or the API returns 400.
- `OfficialApiNode` substitutes config `base_url` as `api_spec.json` `baseUrl` (no `/v1`). If an operator pastes `…/v1`, a later revision may accept that alias.
- Never log `api_key`.

## Unverified

- Whether `https://api.deepseek.com/v1/chat/completions` is a guaranteed alias (official examples omit `/v1`).
- Exact peak/off-peak pricing hours (pricing page; not needed to call).
- FIM `/completions` exists (Beta, non-thinking only) — not an operation here.
