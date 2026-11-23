# Ideogram

Official Ideogram Image API (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_IDEOGRAM`). Dispatched by `OfficialApiNode`.

## Official API

- **Origin:** `https://api.ideogram.ai`
- **Docs:** [API reference](https://developer.ideogram.ai/api-reference/api-reference) · [Generate v4](https://developer.ideogram.ai/api-reference/api-reference/generate-v4) · [Async generate](https://developer.ideogram.ai/api-reference/api-reference/generate-v4-async) · [Poll](https://developer.ideogram.ai/api-reference/api-reference/get-generation)
- **Auth:** StudioQuack secret `api_key` → header `Api-Key`. Never on INPUT. Never logged.

## Scope

Image generate (sync + async poll), remix, upscale. Result URLs are **ephemeral** — download if you need to keep them. Ports **`input` / `output`**. **No FUNCTION ports.** No webhook listen (ingress is BPMN). Optional `webhook_url` on async generate is an operator-owned HTTPS callback only.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `generate` | POST | `/v1/ideogram-v4/generate` (sync) |
| `generate_async` | POST | `/v1/ideogram-v4/async/generate` → `generation_id` |
| `get_task` | GET | `/v1/generations/{generation_id}` (`pending` \| `completed` \| `failed`) |
| `remix` | POST | `/v1/ideogram-v4/remix` |
| `upscale` | POST | `/upscale` |

Set **`operation`** on INPUT (or rely on inference): `generation_id` only → `get_task`; `image_file` → `upscale`; `image` + `text_prompt` → `remix`; `webhook_url` + prompt → `generate_async`.

`text_prompt` and `json_prompt` are mutually exclusive on generate. **upscale** needs `image_file` (file ref) and **`image_request`** (JSON string, e.g. `{"resemblance":50,"detail":90}`). remix/upscale images are type **file**. Official bodies are multipart.

**Billing:** HTTP **402** means the API key has no payment method/credits — configure billing in the Ideogram developer console before positive generation tests.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `generate` |
| `api_key` | password | **yes** | `Api-Key` |
| `timeout_seconds` | number | no | Default `120` |

## Legal

- Content policy / copyright detection as documented on generate (`enable_copyright_detection`). No public-figure abuse.
- Prompts and images leave the host. Official API only — do not scrape ideogram.ai.
