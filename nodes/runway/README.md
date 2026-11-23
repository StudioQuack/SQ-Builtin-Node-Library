# Runway

Official Runway Dev Tasks API (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_RUNWAY`). Dispatched by `OfficialApiNode`.

## Official API

- **Origin:** `https://api.dev.runwayml.com`
- **Docs:** [API reference](https://docs.dev.runwayml.com/api/) · [OpenAPI](https://docs.dev.runwayml.com/openapi.json) (`info.version` = `2024-11-06`) · [Versioning](https://docs.dev.runwayml.com/api-details/versioning/)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`
- **Required header:** `X-Runway-Version: 2024-11-06` on every request via spec-level `extraHeaders` (verified 2026-08-22 against the official OpenAPI `info.version` and SDK docs).

## Scope

Video generate is **asynchronous**. Create returns `{ id }`. Poll `get_task` until `SUCCEEDED` or `FAILED`. Output URLs are **temporary** — download them. Ports **`input` / `output`**. **No FUNCTION ports.** No webhook ingress node.

Likeness / public-figure moderation: failed tasks use `failure` / `failureCode`, not always HTTP 4xx. Moderated generations can still consume credits.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `generate_video` | POST | `/v1/text_to_video` |
| `image_to_video` | POST | `/v1/image_to_video` |
| `get_task` | GET | `/v1/tasks/{id}` |

Request bodies are **per-model** discriminated unions. Do not reuse `ratio` / `duration` across models. Do not invent retired model ids (`gen3a_turbo`). Current text/image-to-video examples: `gen4.5`, `seedance2`, `seedance2_5`, `veo3.1`, `veo3.1_fast` ([models](https://docs.dev.runwayml.com/guides/models.md)). `aleph2` is video-to-video (not an operation on this node). Image/video: type **file** or HTTPS URL.

Poll with backoff (Runway: not tighter than ~5s per task). OfficialApiNode does not auto-poll — chain a second node invocation.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `generate_video` |
| `api_key` | password | **yes** | Bearer |
| `model` | string | no | Designer default (e.g. `gen4.5`); send on INPUT |
| `timeout_seconds` | number | no | Default `60` (one HTTP call) |

## Legal

- Runway Developer terms / content policy on docs.dev.runwayml.com. Prompts and media leave the host.
- Official API only. Do not scrape app.runwayml.com.
