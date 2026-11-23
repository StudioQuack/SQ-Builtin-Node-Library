# Luma

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/luma/`
- **Researched:** 2026-08-20
- **Official API:** Dream Machine REST (`https://api.lumalabs.ai/dream-machine/v1`)
- **Docs:** [API](https://docs.lumalabs.ai/docs/api) · [Video generation](https://docs.lumalabs.ai/docs/video-generation) · [Create](https://docs.lumalabs.ai/reference/creategeneration)
- **Auth:** `api_key` — secret binding: `api_key` (`Authorization: Bearer`)

## Scope

Video generate + poll. Create `POST /generations` returns an id (often `state: dreaming`). Poll `GET /generations/{id}` until `completed` (`assets.video`) or `failed`. Ports **`input` / `output`**. Image keyframes are HTTPS URLs Luma can fetch (type **file** must be published first). No FUNCTION ports.

Optional `callback_url` is operator-owned HTTPS; StudioQuack must not register a global webhook. Prefer poll.

Models documented: `ray-2`, `ray-flash-2`. Resolution examples: `540p`, `720p`, `1080`, `4k`. Duration example: `5s`.

Luma also advertises a newer Agents API at docs.agents.lumalabs.ai / platform.lumalabs.ai. This node is the **Dream Machine** surface still documented for generate + get.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `generate_video` | POST | `/generations` | Text-to-video or image-to-video via `keyframes` |
| `get_generation` | GET | `/generations/{id}` | Poll until completed/failed |

Dropped: list/delete generations as catalog verbs; scraping lumalabs.ai.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `generate_video` | no | Row above |
| `api_key` | password | yes | empty | **yes** | Bearer from https://platform.lumalabs.ai/ |
| `model` | string | no | `ray-2` | no | Send on INPUT to include in body |
| `timeout_seconds` | number | no | `60` | no | One HTTP call |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | `prompt`, `keyframes`, `id` for poll |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Create ≠ finished video |

FUNCTION ports: none.

## Legal

- [Terms of Service](https://lumalabs.ai/legal/tos)
- Prompts, keyframe images, and videos leave the host. Official-API-first.
