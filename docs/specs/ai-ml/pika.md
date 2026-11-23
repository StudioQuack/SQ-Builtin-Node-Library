# Pika

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/pika/`
- **Researched:** 2026-08-20
- **Official API:** Pika customer HTTP API (`https://api.dev.pika.art`) — first-party public API documented at [pika.art/api](https://pika.art/api)
- **Docs:** [pika.art/api](https://pika.art/api) · catalog `GET https://api.dev.pika.art/catalog/apis` · [Terms](https://dev.pika.art/terms-of-service)
- **Auth:** `api_key` — secret binding: `api_key` (header `X-API-Key`; Bearer also documented)

## Scope

**Build.** Pika publishes a public developer API (not Discord, not a third-party proxy). Submit is async: `POST /v1/media/{vendor}/{model}/{function}` → job id → poll `GET /v1/media/jobs/{request_id}` → `GET .../content` for `{ url }`.

Default first-party triple (confirm live catalog): `pika` / `pika-2.5` / `text-to-video`. The same catalog lists other vendors; only submit `api_id`s that are **active**. Image/video ports type **file**. Local files: official `POST /v1/media/uploads` then PUT to the signed URL (OfficialApiNode does not perform that handshake).

Do not automate pika.art via Discord or unofficial “Pika API” wrappers.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `generate_video` | POST | `/v1/media/{vendor}/{model}/{function}` | Submit; returns job `id` |
| `get_job` | GET | `/v1/media/jobs/{request_id}` | Poll until `completed` \| `failed` |
| `get_content` | GET | `/v1/media/jobs/{request_id}/content` | After completed; `{ url }` |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `generate_video` | no | Row above |
| `api_key` | password | yes | empty | **yes** | `X-API-Key` |
| `vendor` | string | path | `pika` | no | Path `{vendor}` |
| `model` | string | path | `pika-2.5` | no | Path `{model}` |
| `function` | string | path | `text-to-video` | no | Path `{function}` |
| `timeout_seconds` | number | no | `60` | no | One HTTP call |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Catalog JSON body + path fields; `image`/`video` type file; `request_id` |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Create ≠ finished video |

FUNCTION ports: none.

## Legal

- [Terms of Service](https://dev.pika.art/terms-of-service) · [Privacy](https://dev.pika.art/privacy-policy)
- Prompts and media leave the host. Check `GET /billing/balance` before paid submits. Official-API-first.
