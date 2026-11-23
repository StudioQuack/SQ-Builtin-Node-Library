# Luma

Official Luma Dream Machine API (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_LUMA`). Dispatched by `OfficialApiNode`.

## Official API

- **Origin:** `https://api.lumalabs.ai/dream-machine/v1`
- **Docs:** [API](https://docs.lumalabs.ai/docs/api) · [Video generation](https://docs.lumalabs.ai/docs/video-generation) · [Create generation](https://docs.lumalabs.ai/reference/creategeneration)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.
- **Keys:** [Luma API Platform](https://platform.lumalabs.ai/)

Luma also points newer “Agents API” docs at docs.agents.lumalabs.ai. This node is the **Dream Machine** REST surface still documented for video generate + poll (2026-08-20).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `generate_video` | POST | `/generations` (often HTTP 201; `state` may be `dreaming`) |
| `get_generation` | GET | `/generations/{id}` until `completed` \| `failed` |

When completed, video URL is `assets.video`. Image-to-video uses `keyframes` with `{ "type": "image", "url": "https://..." }` — Luma fetches HTTPS URLs; type **file** refs must be published to a URL first. Optional `callback_url` is an operator-owned HTTPS POST; StudioQuack must not register a global webhook. Prefer poll.

Models documented: `ray-2`, `ray-flash-2`. Put `model` on INPUT so it is sent in the JSON body.

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `api_key`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `generate_video` |
| `api_key` | password | **yes** | Bearer |
| `model` | string | no | Designer default `ray-2`; send on INPUT |
| `timeout_seconds` | number | no | Default `60` |

## Legal

- [Terms of Service](https://lumalabs.ai/legal/tos)
- Prompts, keyframe images, and videos leave the host. Official API only.
