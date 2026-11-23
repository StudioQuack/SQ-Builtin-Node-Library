# Vercel AI Gateway

Official Vercel AI Gateway node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_VERCEL_AI_GATEWAY`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Vercel AI Gateway OpenAI Chat Completions. Docs last_updated 2026-07-28; researched 2026-08-20.
- **Origin:** `https://ai-gateway.vercel.sh/v1`
- **Docs:** [Chat Completions](https://vercel.com/docs/ai-gateway/sdks-and-apis/openai-chat-completions) · [Authentication](https://vercel.com/docs/ai-gateway/authentication-and-byok/authentication) · [SDKs and APIs](https://vercel.com/docs/ai-gateway/sdks-and-apis)
- **Auth:** StudioQuack secret `api_key` (AI Gateway API key) → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One LLM **aggregator** node. Every `chat` call is routed to a third-party model provider. The workflow owner must accept **Vercel’s AI Product Terms and the upstream model’s terms** for the selected `model` id. Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

Catalog verb is `chat` only. Documented but **not** operations here: `GET /models`, `GET /models/{model}`, `POST /embeddings`, OpenAI Responses, Anthropic Messages. Distinct from the separate Vercel **platform** node (`nodes/vercel/`).

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/chat/completions` | Routed chat / tools / vision (Agent model port) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, stream?, model?, file?}`. Image/PDF use type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `api_key` | password | **yes** | Bearer AI Gateway key. Required. |
| `model` | string | no | Gateway id with org prefix, e.g. `anthropic/claude-opus-5`. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Vercel Terms of Service](https://vercel.com/legal/terms)
- [AI Product Terms](https://vercel.com/legal/ai-product-terms) (AI Gateway is an AI Product; customer content to Non-Vercel Services is subject to that provider’s terms)
- [Notices and License Information](https://vercel.com/legal/notices-and-license-information)
- **Upstream ToS:** the customer accepts each model provider’s terms for the routed id. StudioQuack does not accept those terms.
- Keys are tied to the creating team member and are deactivated if they leave (auth docs). Use a dedicated key.
- Prompts leave the host to Vercel then the upstream. Do not send PHI without covering DPAs (Vercel + upstream). Official-API-first: `ai-gateway.vercel.sh` only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `bodyFrom` is taken from INPUT only — also put `model` on `llm_input`.
- OIDC (`VERCEL_OIDC_TOKEN`) is for apps **deployed on Vercel**; this node uses an API key secret.
- BYOK provider credentials are configured in the Vercel dashboard; they do not replace gateway `api_key`.
- HTTP 4xx/5xx raise a BPMN error (AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED). Never log `api_key`.

## Unverified

- Whether `providerOptions` in the JSON body is still the documented advanced-routing field name (docs also describe dashboard fallbacks).
- Exact 429 budgets (per-key spend caps exist in the dashboard; not a static RPM table).
