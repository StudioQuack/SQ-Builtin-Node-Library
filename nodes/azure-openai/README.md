# Azure OpenAI

Official Azure OpenAI node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AZURE_OPENAI`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Azure OpenAI / Microsoft Foundry Models data-plane inference. Default GA query **`api-version=2024-10-21`**. TTS uses the deployment-scoped speech route, which is only documented on preview api-versions (e.g. `2025-04-01-preview`).
- **Origin:** `https://{endpoint}/` where `endpoint` is the resource hostname (no scheme).
- **Docs:** [Image & audio REST 2024-10-21](https://learn.microsoft.com/en-us/azure/foundry/openai/reference) · [Chat Completions REST](https://learn.microsoft.com/en-us/rest/api/microsoft-foundry/azureopenai/chat) · [API lifecycle](https://learn.microsoft.com/en-us/azure/foundry/openai/api-version-lifecycle)
- **Auth:** StudioQuack secret `api_key` → header `api-key`. Never on INPUT. Never logged.

## Scope

Customer’s Azure OpenAI / Foundry **resource** + **deployment** names (not a third-party public chat API). Ports: **IN `llm_input` / OUT `llm_output`** only. **No extra FUNCTION ports.** Do not implement Assistants, vector stores, fine-tune, or ARM control-plane from this node.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `{endpoint}/openai/deployments/{deployment}/chat/completions?api-version={api_version}` | Agent model |
| `embeddings` | POST | `{endpoint}/openai/deployments/{deployment}/embeddings?api-version={api_version}` | Embedding deployments |
| `image_gen` | POST | `{endpoint}/openai/deployments/{deployment}/images/generations?api-version={api_version}` | Image deployments |
| `stt` | POST | `{endpoint}/openai/deployments/{deployment}/audio/transcriptions?api-version={api_version}` | Whisper-class STT (multipart) |
| `tts` | POST | `{endpoint}/openai/deployments/{deployment}/audio/speech?api-version={api_version}` | Speech (preview api-version) |

`api-version` is baked into `path` because OfficialApiNode query keys cannot use a hyphenated name.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, file?, prompt?, input?}`. Image/audio use type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}`. Secrets redacted. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | chat | no | Official Azure OpenAI verb. EXACT values: chat / embeddings / image_gen / stt / tts. Default chat = POST …/chat/completions. |
| `api_key` | password | yes |  | yes | Azure OpenAI resource key. Sent as header api-key. StudioQuack secret binding only. Never logged. Empty → error 'api_key is required'. |
| `endpoint` | string | yes |  | no | Hostname only, no scheme (e.g. myres.openai.azure.com). Interpolated into https://{endpoint}/…. Do not include https://. |
| `deployment` | string | yes |  | no | Deployment id in the path. Required for every operation (chat, embeddings, image_gen, stt, tts). This is the Azure deployment name, not the underlying OpenAI model slug (https://learn.microsoft.com/en... |
| `api_version` | string | yes | 2024-10-21 | no | Query api-version for the deployment-scoped paths every operation uses (tts needs a preview version). Default 2024-10-21 remains the documented GA date for those deployment routes (https://lea... |
| `temperature` | number | no | 0 | no | Chat sampling 0–2. Empty → omitted unless also on INPUT. |
| `data_sources` | list | no |  | no | Request-body parameter `data_sources` for operation: chat. |
| `seed` | number | no |  | no | Request-body parameter `seed` for operation: chat. |
| `max_completion_tokens` | number | no |  | no | Request-body parameter `max_completion_tokens` for operation: chat. |
| `logit_bias` | object | no |  | no | Request-body parameter `logit_bias` for operation: chat. |
| `top_p` | number | no |  | no | Request-body parameter `top_p` for operation: chat. |
| `max_tokens` | number | no |  | no | Request-body parameter `max_tokens` for operation: chat. |
| `response_format` | object | no |  | no | Request-body parameter `response_format` for operations: chat, image_gen, stt, tts. |
| `parallel_tool_calls` | boolean | no |  | no | Request-body parameter `parallel_tool_calls` for operation: chat. |
| `stop` | list | no |  | no | Request-body parameter `stop` for operation: chat. |
| `tool_choice` | string | no |  | no | Request-body parameter `tool_choice` for operation: chat. |
| `messages` | object | no |  | no | Chat Completions messages for operation=chat. Vision image_url from type file. Example: [{"role":"user","content":"Hello"}]. Do not send model on deployment-scoped paths. |
| `presence_penalty` | number | no |  | no | Request-body parameter `presence_penalty` for operation: chat. |
| `n` | number | no |  | no | Request-body parameter `n` for operations: chat, image_gen. |
| `user` | string | no |  | no | Request-body parameter `user` for operations: chat, embeddings, image_gen. |
| `frequency_penalty` | number | no |  | no | Request-body parameter `frequency_penalty` for operation: chat. |
| `tools` | list | no |  | no | Optional tools {name, description, request} mapped to Chat Completions functions. Omit or [] sends no tools. |
| `input` | string | no |  | no | embeddings input or tts input text when messages is omitted. |
| `encoding_format` | string | no |  | no | Request-body parameter `encoding_format` for operation: embeddings. |
| `dimensions` | number | no |  | no | Request-body parameter `dimensions` for operation: embeddings. |
| `style` | string | no |  | no | Request-body parameter `style` for operation: image_gen. |
| `size` | string | no |  | no | Request-body parameter `size` for operation: image_gen. |
| `quality` | string | no |  | no | Request-body parameter `quality` for operation: image_gen. |
| `prompt` | string | no |  | no | image_gen prompt (or last user text). GA max 4000 characters. |
| `language` | string | no |  | no | Request-body parameter `language` for operation: stt. |
| `file` | file | no |  | no | Platform file ref-id for stt (multipart field file). Never inline bytes. |
| `speed` | number | no |  | no | Request-body parameter `speed` for operation: tts. |
| `voice` | string | no |  | no | Request-body parameter `voice` for operation: tts. |
| `timeout_seconds` | number | no | 300 | no | HTTP timeout. Default 300. Floor 1. |
| `stream` | select | no | true | no | Purpose: controls whether Azure OpenAI requests a streamed completion and fans live deltas through helper emitPartial. Canonical flag. When true the node POSTs stream:true, pars... |

## Legal

- [Microsoft Product Terms](https://www.microsoft.com/licensing/terms/)
- [Code of Conduct for Microsoft AI Services](https://learn.microsoft.com/en-us/legal/ai-code-of-conduct)
- [Azure OpenAI data, privacy, and security](https://learn.microsoft.com/en-us/azure/foundry/responsible-ai/openai/data-privacy)
- Data residency follows the Azure resource region. Customer is the Azure subscriber/controller. HIPAA only with a BAA covering that subscription. Official-API-first: no scrape of Azure Portal.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `header` / `api-key`.
- `baseUrl` is `https://` so `{endpoint}` can be interpolated (OfficialApiNode does not template `baseUrl`).
- `stt` is documented as multipart; the generic handler JSON-encodes `bodyFrom` — binary uploads may need a later multipart path.
- Do not send `model` on the deployment-scoped paths (all operations, including tts).
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.
