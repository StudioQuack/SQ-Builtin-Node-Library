# Azure OpenAI

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/azure-openai/`
- **Researched:** 2026-08-20
- **Official API:** Azure OpenAI / Microsoft Foundry Models **data-plane inference**. Default GA query version **`2024-10-21`** on deployment-scoped paths; Foundry also publishes a **v1** data-plane (`api-version=v1` / `preview`) at `/openai/v1/…`.
- **Docs:** [Image & audio REST 2024-10-21](https://learn.microsoft.com/en-us/azure/foundry/openai/reference) · [Azure OpenAI REST (chat & others)](https://learn.microsoft.com/en-us/rest/api/microsoft-foundry/azureopenai/chat?view=rest-microsoft-foundry-2024-10-21) · [v1 preview image/audio/video](https://learn.microsoft.com/en-us/azure/foundry/openai/reference-preview-latest) · [API lifecycle](https://learn.microsoft.com/en-us/azure/foundry/openai/api-version-lifecycle)
- **Auth:** `api_key` — secret binding: `api_key` (header `api-key`). Optional Entra: `entra_token` is **not** a node input; v1 uses API key only unless `use_entra` is added later.

## Scope

Customer’s Azure OpenAI / Foundry **resource endpoint** + **deployment** names (not `api.openai.com`). Chat, embeddings, image generation, speech-to-text, and text-to-speech are `operation` values. Ports: **IN `llm_input` / OUT `llm_output`** only. JDK HTTP, no Azure SDK.

Do not implement Assistants, vector stores, fine-tune, or control-plane ARM from this node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `{endpoint}/openai/deployments/{deployment}/chat/completions?api-version={api_version}` | Agent model. OpenAI-shaped `messages`. |
| `embeddings` | POST | `{endpoint}/openai/deployments/{deployment}/embeddings?api-version={api_version}` | Embedding deployments |
| `image_gen` | POST | `{endpoint}/openai/deployments/{deployment}/images/generations?api-version=2024-10-21` | DALL·E / image deployments (GA body: `prompt`, `n`, `size`, `quality`, `style`, `response_format`) |
| `stt` | POST | `{endpoint}/openai/deployments/{deployment}/audio/transcriptions?api-version=2024-10-21` | Whisper-class STT (`multipart/form-data`) |
| `tts` | POST | `{endpoint}/openai/v1/audio/speech?api-version=preview` | TTS on the **v1 preview** inference spec (not listed on the 2024-10-21 GA image/audio page, which covers transcriptions/translations/images). If the operator’s resource only has deployment-scoped speech, send `POST .../deployments/{deployment}/audio/speech?api-version={preview}` as documented for that version. |

Retired / unofficial verbs are omitted, not listed as "later".

**v1 GA alternative (optional later switch):** `{endpoint}/openai/v1/chat/completions?api-version=v1` — do not mix v1 and 2024-10-21 in one invoke. Config `api_style`: `deployments` (default) | `v1`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | Row above |
| `api_key` | password | yes at runtime | | **yes** | Resource key. Header `api-key`. Never on INPUT. |
| `endpoint` | string | yes | | no | `https://{resource}.openai.azure.com` (no trailing path) |
| `deployment` | string | yes except when `api_style=v1` and body has `model` | | no | Deployment id (path). For v1 style, also sent as `model` if required. |
| `api_version` | string | yes | `2024-10-21` | no | Query `api-version`. Use `preview` for v1 TTS path. |
| `api_style` | select | no | `deployments` | no | `deployments` \| `v1` |
| `temperature` | number | no | omit | no | Chat sampling |
| `top_p` | number | no | omit | no | |
| `max_completion_tokens` | number | no | omit | no | Chat; prefer over `max_tokens` |
| `max_tokens` | number | no | omit | no | Legacy alias |
| `response_format` | string | no | omit | no | Chat `response_format` JSON or `json`/`text` |
| `stream` | select | no | `true` | no | Chat only |
| `timeout_seconds` | number | no | `300` | no | |

File payloads use type `file` (platform ref-id), never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | v2 LLM object + per-operation fields | |
| `llm_output` | OUTPUT | `{ result }` | Content-filter errors still `result.error` / `finish_reason=content_filter` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = chat`

Documented Chat Completions body (Azure OpenAI REST + OpenAI-compatible fields). **Do not send `model` on deployment-scoped GA paths** (deployment is in the URL). On `api_style=v1`, send `model` = deployment or Foundry model name as required.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | yes | `system` / `user` / `assistant` / `tool`. Vision: `image_url` from platform `file` (data URL **on the wire only**). |
| `tools` | list | no | `{type:function, function:{name, description, parameters}}` from v2 `{name, description, request}` |
| `tool_choice` | string or object | no | `none` / `auto` / `required` / named |
| `temperature` | number | no | 0–2 |
| `top_p` | number | no | |
| `n` | number | no | Return first choice only |
| `stream` | boolean | no | SSE |
| `stop` | string or list | no | |
| `max_completion_tokens` | number | no | |
| `max_tokens` | number | no | Older models |
| `presence_penalty` | number | no | |
| `frequency_penalty` | number | no | |
| `logit_bias` | object | no | |
| `user` | string | no | Abuse tracking — **not PHI** |
| `seed` | number | no | |
| `logprobs` | boolean | no | |
| `top_logprobs` | number | no | |
| `response_format` | object | no | `{type:text\|json_object\|json_schema}` |
| `data_sources` | list | no | Azure **On Your Data** (Azure Search, etc.). Optional; omit unless operator configures it. Secrets for those sources stay in Azure, not this node. |
| `parallel_tool_calls` | boolean | no | |

### `operation = embeddings`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `input` | string or list | yes if no `messages` | Embedding input. Else last user text from `messages`. |
| `encoding_format` | string | no | `float` / `base64` |
| `dimensions` | number | no | When the deployment supports reducing dimensions |
| `user` | string | no | |

### `operation = image_gen`

GA 2024-10-21 JSON body:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `prompt` | string | yes if no `messages` | Else last user text. Max length 4,000 characters (GA docs). |
| `n` | integer | no | Default 1 |
| `size` | string | no | `1024x1024` / `1792x1024` / `1024x1792` |
| `quality` | string | no | `standard` / `hd` |
| `style` | string | no | `vivid` / `natural` |
| `response_format` | string | no | `url` (default) or `b64_json`. Handler always materializes a platform `file` ref (download URL or decode b64 in sandbox). |
| `user` | string | no | |

### `operation = stt`

**multipart/form-data** (2024-10-21):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform ref-id (audio). Part name `file`. |
| `prompt` | string | no | Style / continuation; must match audio language |
| `response_format` | string | no | `json` / `text` / `srt` / `verbose_json` / `vtt` |
| `temperature` | number | no | 0–1, default 0 |
| `language` | string | no | ISO-639-1 |

### `operation = tts`

v1 preview JSON body (Create speech):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `input` | string | yes if no `messages` | Else last user text |
| `voice` | string | yes | Deployment-supported voice |
| `response_format` | string | no | e.g. `mp3` / `wav` / `opus` as documented for that API version |
| `speed` | number | no | When documented |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `result.role` | string | chat | `assistant` |
| `result.content` | list | chat / stt | Text / transcript |
| `result.tool_calls` | list | chat | From `choices[0].message.tool_calls` |
| `result.finish_reason` | string | chat | `stop` / `length` / `content_filter` / `tool_calls` |
| `result.usage` | object | chat / embeddings | `prompt_tokens`, `completion_tokens`, `total_tokens` |
| `result.embedding` | list | embeddings | `data[0].embedding` |
| `result.output_files` | list | image_gen / tts | Platform `file` refs (`type` image or audio) |
| `result.content_filter_results` | object | when Azure returns them | Filter categories — do not log prompt |
| `result.error` | string | failure | Redacted. Content filter → `finish_reason=content_filter` |

## Legal

- ToS: [Microsoft Product Terms](https://www.microsoft.com/licensing/terms/) (Azure / Models sold by Azure / Generative AI universal license terms) and [Code of Conduct for Microsoft AI Services](https://learn.microsoft.com/en-us/legal/ai-code-of-conduct). Limited-access / guardrail modification: [Limited access](https://learn.microsoft.com/en-us/azure/foundry/responsible-ai/openai/limited-access). Privacy: [Azure OpenAI data, privacy, and security](https://learn.microsoft.com/en-us/azure/foundry/responsible-ai/openai/data-privacy).
- Abuse monitoring and content filters are on by default; disabling is a Limited Access process.
- Rate limits: per-deployment TPM/RPM in Azure Monitor / Foundry. 429 → `result.error`. No retry storm.
- PII / residency: data stays in the customer’s Azure region **as configured on the resource**. Customer is the Azure subscriber and controller. HIPAA only with a BAA covering that subscription.
- Official-API-first: no scrape of Azure Portal.

## Implementation notes

- JDK HTTP. Header `api-key`. Do not log `endpoint` query strings that accidentally include keys.
- Connect 15s; read `timeout_seconds`. Redirects off.
- Chat stream: OpenAI-style SSE `data:` chunks; `emitPartial`. `stream_options.include_usage` if that version supports it; drop on 400 mentioning the field (same as OpenAI Models).
- Retry: none on 429; one retry on 408/503.
- Image: if `response_format=url`, HTTPS GET the blob into a `file` ref (timeout 60s); do not return the SAS URL downstream if it embeds secrets — prefer copying bytes into the platform file store.
- STT: multipart from resolved `file` bytes; cap e.g. 25 MB unless the live audio docs allow more.
- Error mapping: Azure error JSON `error.message` / `error.code` / inner `ResponsibleAIPolicyViolation` → redacted `result.error`. Never echo `api-key`.
- Egress: customer `endpoint` host `:443` (and image blob host if `url` format).
