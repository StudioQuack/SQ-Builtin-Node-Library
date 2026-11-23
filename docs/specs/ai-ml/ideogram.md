# Ideogram

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/ideogram/`
- **Researched:** 2026-08-20
- **Official API:** Ideogram Image API (`https://api.ideogram.ai`)
- **Docs:** [API reference](https://developer.ideogram.ai/api-reference/api-reference) · [Generate v4](https://developer.ideogram.ai/api-reference/api-reference/generate-v4.md) · [Async](https://developer.ideogram.ai/api-reference/api-reference/generate-v4-async.md) · [Poll](https://developer.ideogram.ai/api-reference/api-reference/get-generation.md)
- **Auth:** `api_key` — secret binding: `api_key` (header `Api-Key`)

## Scope

One image node: sync generate, async generate + **get_task** poll, remix, upscale. Ports **`input` / `output`**. Result URLs are ephemeral — download to keep. Image ports type **file**. No FUNCTION ports. No webhook *listen* operation (BPMN ingress). Optional `webhook_url` on async generate is operator-owned HTTPS only.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `generate` | POST | `/v1/ideogram-v4/generate` | Sync Ideogram 4.0. `text_prompt` **xor** `json_prompt` |
| `generate_async` | POST | `/v1/ideogram-v4/async/generate` | Returns `generation_id` immediately |
| `get_task` | GET | `/v1/generations/{generation_id}` | Poll: `pending` \| `completed` \| `failed` |
| `remix` | POST | `/v1/ideogram-v4/remix` | `image` (file) + `text_prompt` |
| `upscale` | POST | `/upscale` | `image_file` (file) + `image_request` object |

Official generate/remix/upscale bodies are multipart. OfficialApiNode JSON-encodes `bodyFrom`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `generate` | no | Row above |
| `api_key` | password | yes | empty | **yes** | `Api-Key`. Never on INPUT. |
| `timeout_seconds` | number | no | `120` | no | Floor 1. Prefer async+poll for long jobs. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Prompts, `image`/`image_file` type file, `generation_id` |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | `is_image_safe=false` → empty url |

FUNCTION ports: none.

## Legal

Content policy and optional copyright detection (`enable_copyright_detection`) as documented. Prompts and images leave the host. Official-API-first: no scrape of ideogram.ai.
