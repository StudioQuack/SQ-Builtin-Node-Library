# Recraft

Official Recraft image/vector REST (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_RECRAFT`). Dispatched by `OfficialApiNode`.

## Official API

- **Origin:** `https://external.api.recraft.ai`
- **Docs:** [Getting started](https://www.recraft.ai/docs/api-reference/getting-started) · [Endpoints](https://www.recraft.ai/docs/api-reference/endpoints) · [Swagger](https://external.api.recraft.ai/doc/#/)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

Catalog verbs **generate**, **vectorize**, **style**. Generate is **synchronous** (`POST /v1/images/generations` returns `data[].url`). Official endpoints.md (2026-08-20) has **no** get-task / jobs poll path — do not invent one. Marketing “batch/async” is not a documented REST poll resource.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `generate` | POST | `/v1/images/generations` |
| `vectorize` | POST | `/v1/images/vectorize` (`file` type file or `image_url`) |
| `style` | POST | `/v1/styles` |

Default generate model: `recraftv4_1`. Also current: `recraftv4_1_vector`, `recraftv4_1_pro`, `recraftv4_1_pro_vector`, `recraftv4_1_utility`, `recraftv4_1_utility_vector`, `recraftv4_1_utility_pro`, `recraftv4_1_utility_pro_vector`, `recraftv4`, `recraftv4_vector`, `recraftv4_pro`, `recraftv4_pro_vector`, `recraftv3`, `recraftv3_vector`, `recraftv2`, `recraftv2_vector`. Put `model` on INPUT so it is included in the JSON body. Docs: https://www.recraft.ai/docs/api-reference/endpoints

## Ports

`input` / `output` only. Raster inputs are type **file**. Failures: `{ error }` — never includes `api_key`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `generate` |
| `api_key` | password | **yes** | Bearer (`https://app.recraft.ai/profile/api`) |
| `model` | string | no | Designer default; send on INPUT |
| `timeout_seconds` | number | no | Default `120` |

## Legal

- [Terms of Service](https://www.recraft.ai/terms) · [Privacy](https://www.recraft.ai/privacy)
- Prompts and images leave the host. Official API only.
