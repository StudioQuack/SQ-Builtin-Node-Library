# Pika

Official Pika customer HTTP API (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_PIKA`). Dispatched by `OfficialApiNode`.

## Official API

- **Origin:** `https://api.dev.pika.art`
- **Docs:** [pika.art/api](https://pika.art/api) · [dev.pika.art](https://dev.pika.art) · catalog `GET https://api.dev.pika.art/catalog/apis`
- **Auth:** StudioQuack secret `api_key` → header `X-API-Key` (Bearer is also documented; this node uses `X-API-Key`). Never on INPUT. Never logged.
- **Terms:** [Terms of Service](https://dev.pika.art/terms-of-service) · [Privacy](https://dev.pika.art/privacy-policy)

This is Pika’s **first-party** public API (researched 2026-08-20). It is not a Discord bot and not an unofficial scraper. The same catalog also lists non-Pika vendors; this node’s default path is first-party `pika/pika-2.5/text-to-video`. Always confirm `api_id` is **active** in the live catalog before a paid submit.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `generate_video` | POST | `/v1/media/{vendor}/{model}/{function}` → job `{ id, status }` |
| `get_job` | GET | `/v1/media/jobs/{request_id}` until `completed` \| `failed` |
| `get_content` | GET | `/v1/media/jobs/{request_id}/content` → `{ "url": "..." }` (only after completed) |

Local files: official flow is `POST /v1/media/uploads` then PUT bytes to the signed URL, then pass the hosted `url` in the model body. Image/video port fields are type **file**. OfficialApiNode does not perform the upload handshake — pass an HTTPS URL the API can fetch, or wait for a later multipart/upload path.

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `api_key`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `generate_video` |
| `api_key` | password | **yes** | `X-API-Key` |
| `vendor` | string | no | Default `pika` (path) |
| `model` | string | no | Default `pika-2.5` |
| `function` | string | no | Default `text-to-video` |
| `timeout_seconds` | number | no | Default `60` |

## Legal

Prompts and media leave the host. Check balance (`GET /billing/balance`) before paid submits. Official API only — no Discord automation.
