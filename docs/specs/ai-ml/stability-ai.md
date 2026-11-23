# Stability AI

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/stability-ai/`
- **Researched:** 2026-08-20
- **Official API:** Stable Image REST **v2beta** (`https://api.stability.ai`)
- **Docs:** [API reference](https://platform.stability.ai/docs/api-reference/) · [Stable Image](https://platform.stability.ai/docs/getting-started/stable-image)
- **Auth:** `api_key` — secret binding: `api_key` (`Authorization: Bearer`)

## Scope

One node for Stability AI Ltd. Stable Image services in four documented categories: **Generate**, **Upscale**, **Edit**, **Control**. Ports are **`input` / `output`**. Not an Agent model node. No FUNCTION ports.

REST v1 and gRPC are **legacy** (maintenance). This node uses v2beta only. Stable Audio / 3D / video are out of scope.

Async: Creative Upscale and some edit jobs return a generation id; poll `GET /v2beta/results/{id}` (`get_result`). HTTP **202** = in progress; **200** = complete.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `generate` | POST | `/v2beta/stable-image/generate/{engine}` | Text-to-image. `{engine}`: `core` \| `ultra` \| `sd3` |
| `upscale` | POST | `/v2beta/stable-image/upscale/{upscale_mode}` | `{upscale_mode}`: `conservative` \| `creative` \| `fast` |
| `edit` | POST | `/v2beta/stable-image/edit/{edit_mode}` | inpaint, outpaint, erase, search-and-replace, search-and-recolor, remove-background, replace-background-and-relight |
| `control` | POST | `/v2beta/stable-image/control/{control_mode}` | sketch, structure, style, style-transfer |
| `get_result` | GET | `/v2beta/results/{id}` | Poll async id |

Official body: `multipart/form-data`. `prompt` 1–10000 chars on generate. Images: jpeg/png/webp. Image/mask ports: type **file**.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `generate` | no | Row above |
| `api_key` | password | yes | empty | **yes** | Bearer. Never on INPUT. Never logged. |
| `engine` | select | generate | `core` | no | Path `{engine}` |
| `upscale_mode` | select | upscale | `conservative` | no | Path `{upscale_mode}` |
| `edit_mode` | select | edit | `inpaint` | no | Path `{edit_mode}` |
| `control_mode` | select | control | `structure` | no | Path `{control_mode}` |
| `timeout_seconds` | number | no | `120` | no | Per HTTP call. Floor 1. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | `prompt`, `image`/`mask` type file, `id` for poll. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Never echo `api_key` |

FUNCTION ports: none.

## Legal

- [Terms of Service](https://stability.ai/terms-of-service)
- Content moderation may return HTTP 403. Output license follows the customer plan. Prompts and images **leave the host**. Official-API-first: no scrape of DreamStudio.
