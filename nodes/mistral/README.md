# Mistral

Official Mistral Cloud API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_MISTRAL`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Mistral AI API **v1** (chat, embeddings, FIM, OCR, files). Documented REST as of 2026-08-20.
- **Origin:** `https://api.mistral.ai`
- **Docs:** [Chat](https://docs.mistral.ai/api) · [Embeddings](https://docs.mistral.ai/api/endpoint/embeddings) · [FIM](https://docs.mistral.ai/api/endpoint/fim) · [OCR](https://docs.mistral.ai/api/endpoint/ocr) · [Files](https://docs.mistral.ai/api/endpoint/files)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One node for Mistral chat, embeddings, fill-in-the-middle, OCR, and file upload. Ports: **IN `llm_input` / OUT `llm_output`**. **No extra FUNCTION ports.** Catalog `extract_text` is `ocr`.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/chat/completions` | Agent model |
| `embeddings` | POST | `/v1/embeddings` | Vectors (`input`) |
| `fim` | POST | `/v1/fim/completions` | Code fill-in-the-middle |
| `ocr` | POST | `/v1/ocr` | PDF/image document understanding |
| `files` | POST | `/v1/files` | Multipart upload (`purpose` ocr\|fine-tune\|batch) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, prompt?, document?, file?}`. PDF/image use type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` or `{error}`. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token. |
| `model` | string | no | Default `mistral-large-latest`. |
| `temperature` | number | no | Chat/FIM sampling on INPUT. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Commercial Terms of Service](https://legal.mistral.ai/terms/commercial-terms-of-service)
- [Additional terms](https://legal.mistral.ai/terms)
- Prompts and documents leave the host to Mistral (France). Customer is controller. Do not send PHI without a covering DPA. Official-API-first: no scrape of Le Chat or Studio.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `files` is documented as multipart; the generic handler JSON-encodes `bodyFrom` — large uploads may need a later multipart path.
- OCR typically needs a `document_url` / `image_url` or a prior `files` `file_id`.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.
