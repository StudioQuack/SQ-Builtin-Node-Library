# Runway

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/runway/`
- **Researched:** 2026-08-20
- **Official API:** Runway Dev Tasks API (`https://api.dev.runwayml.com`)
- **Docs:** [ai-context](https://docs.dev.runwayml.com/ai-context.md) · [api.md](https://docs.dev.runwayml.com/api.md) · [OpenAPI](https://docs.dev.runwayml.com/openapi.json)
- **Auth:** `api_key` — secret binding: `api_key` (`Authorization: Bearer`). Every request must include `X-Runway-Version: 2024-11-06` (OfficialApiNode does not send extra headers today).

## Scope

Video generate is **asynchronous**. POST create → `{ id }` → poll `GET /v1/tasks/{id}` until `SUCCEEDED` / `FAILED` / `CANCELLED`. `THROTTLED` is queued, not an error. Output URLs are temporary. Ports **`input` / `output`**. Image/video type **file** (or HTTPS URL). No FUNCTION ports. No webhook listen.

Bodies are **per-model** unions keyed on `model`. Do not reuse `ratio`/`duration` across models. Do not invent retired ids (`gen3a_turbo`, `gen4_aleph`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `generate_video` | POST | `/v1/text_to_video` | Text → video task |
| `image_to_video` | POST | `/v1/image_to_video` | Image (+ optional text) → video task |
| `get_task` | GET | `/v1/tasks/{id}` | Poll status and `output[]` URLs |

Dropped: Characters/avatars, recipes, model routers, `POST /v1/uploads` handshake as a separate catalog verb (document HTTPS URL / later upload). OfficialApiNode does **not** auto-poll.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `generate_video` | no | Row above |
| `api_key` | password | yes | empty | **yes** | Bearer (`RUNWAYML_API_SECRET`) |
| `model` | string | create if not on INPUT | `gen4.5` | no | Must match live [models](https://docs.dev.runwayml.com/guides/models.md) |
| `timeout_seconds` | number | no | `60` | no | One HTTP call. Floor 1. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | `promptText`, `promptImage` type file, `id` for poll |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Create ≠ finished video |

FUNCTION ports: none.

## Legal

Runway developer / content-moderation policy as published on docs.dev.runwayml.com. Prompts and media leave the host. Moderated tasks may still consume credits. Official-API-first: no scrape of the consumer app.
