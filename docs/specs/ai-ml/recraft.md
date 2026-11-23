# Recraft

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/recraft/`
- **Researched:** 2026-08-20
- **Official API:** Recraft REST (`https://external.api.recraft.ai`)
- **Docs:** [Getting started](https://www.recraft.ai/docs/api-reference/getting-started) · [Endpoints](https://www.recraft.ai/docs/api-reference/endpoints) · [Swagger UI](https://external.api.recraft.ai/doc/#/)
- **Auth:** `api_key` — secret binding: `api_key` (`Authorization: Bearer`)

## Scope

One node for Recraft, Inc. Catalog: **generate**, **vectorize**, **style**. Generate is **synchronous** (`POST /v1/images/generations`). Official endpoints.md lists no GET job/task poll resource as of 2026-08-20 — **do not invent `get_task`**. Marketing copy about batch/async is not a documented poll path.

Ports **`input` / `output`**. Image inputs type **file**. No FUNCTION ports.

Default generate model: `recraftv4_1` (also `_vector`, `_pro`, v4/v3/v2 families as listed in endpoints).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `generate` | POST | `/v1/images/generations` | Raster or vector from `prompt` (JSON) |
| `vectorize` | POST | `/v1/images/vectorize` | Raster → SVG (`file` or `image_url`) |
| `style` | POST | `/v1/styles` | Create style id from reference images |

Dropped: undocumented job poll; scraping app.recraft.ai.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `generate` | no | Row above |
| `api_key` | password | yes | empty | **yes** | Bearer from https://app.recraft.ai/profile/api |
| `model` | string | no | `recraftv4_1` | no | Designer default; body field must be on INPUT |
| `timeout_seconds` | number | no | `120` | no | Floor 1 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | `prompt`; `file` type file; `image_url`; `style_id` |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | generate: `data[].url` or `b64_json` |

FUNCTION ports: none.

## Legal

- [Terms](https://www.recraft.ai/terms) · [Privacy](https://www.recraft.ai/privacy)
- Prompts and images leave the host. Official-API-first.
