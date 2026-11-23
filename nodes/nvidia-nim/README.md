# NVIDIA NIM

Official NVIDIA API Catalog / NIM node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_NVIDIA_NIM`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** NVIDIA NIM OpenAI-compatible inference (hosted catalog + self-hosted NIM containers). Researched 2026-08-20.
- **Origin (hosted):** `https://integrate.api.nvidia.com`
- **Docs:** [NIM LLM API reference](https://docs.nvidia.com/nim/large-language-models/latest/reference/api-reference.html) · [LLM APIs](https://docs.api.nvidia.com/nim/reference/llm-apis) · [build.nvidia.com](https://build.nvidia.com) · [Embeddings NIM](https://docs.nvidia.com/nim/nemo-retriever/text-embedding/2.3.0/reference.html)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Keys from [build.nvidia.com](https://build.nvidia.com) typically start with `nvapi-`. Never on INPUT. Never logged.

## Scope

One node for NVIDIA-hosted API Catalog NIMs (default) and, if the operator later overrides origin, a self-hosted NIM on port 8000. `operation` selects Chat Completions (`chat`) or Embeddings (`embeddings`). Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

Dropped: `POST /v1/completions`, Responses, Anthropic Messages, tokenize/detokenize, health. Self-hosted management endpoints are not catalog verbs.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/chat/completions` | Agent model / Nemotron and partner LLMs |
| `embeddings` | POST | `/v1/embeddings` | Vector embeddings (embedding NIM only) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, …}` for chat; `{input}` for embeddings. Images use type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` or `{error}`. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer. Required for hosted catalog. |
| `model` | string | no | Default `nvidia/nemotron-3-nano-30b-a3b`. Switch to an embedding id for `embeddings`. Docs: https://docs.api.nvidia.com/nim/reference/llm-apis |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [NVIDIA NGC Terms of Use](https://ngc.nvidia.com/legal/terms)
- [Product Specific Terms for AI Products](https://www.nvidia.com/en-us/agreements/enterprise-software/product-specific-terms-for-ai-products/) (NIM / NGC AI catalog)
- [NVIDIA Software License Agreement](https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-software-license-agreement/)
- Partner model cards on [build.nvidia.com](https://build.nvidia.com) apply per model (Llama, Mistral, etc.).
- Hosted inference leaves the daemon for NVIDIA. Do not send PHI without a covering agreement. Official-API-first: `integrate.api.nvidia.com` (or the operator’s own NIM origin).

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `bodyFrom` is taken from INPUT only — also put `model` (and embeddings `input`) on `llm_input`.
- NGC personal keys for `nvcr.io` image pulls are **not** interchangeable with `NVIDIA_API_KEY` / `nvapi-` hosted keys.
- Embeddings `input_type` `query` \| `passage` is model-specific (NV-EmbedQA). Send on INPUT.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.

## Unverified

- Live default chat id `nvidia/nemotron-3-nano-30b-a3b`. Previous `nvidia/llama-3.1-nemotron-nano-8b-v1` remains listed. `nvidia/llama-3.3-nemotron-super-49b-v1.5` API deprecates 2026-08-25. Verify `GET /v1/models`.
- Whether hosted `GET /v1/models` returns the full catalog or only models the key can invoke.
- Exact hosted RPM/TPM (account/model/traffic; not a published static table as of this research).
