# Gladia

Official Gladia Speech-to-Text node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_GLADIA`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Gladia API v2 (pre-recorded REST + live WebSocket). Researched 2026-08-20.
- **Origin:** `https://api.gladia.io`
- **Docs:** [Auth](https://docs.gladia.io/api-reference/authentication) · [Init pre-recorded](https://docs.gladia.io/api-reference/v2/pre-recorded/init) · [Get pre-recorded](https://docs.gladia.io/api-reference/v2/pre-recorded/get) · [Live quickstart](https://docs.gladia.io/chapters/live-stt/quickstart)
- **Auth:** StudioQuack secret `api_key` → header `x-gladia-key`. Never on INPUT. Never logged.

## Scope

One node for Gladia async pre-recorded transcription and a live-stream ingress stub. Submit `POST /v2/pre-recorded` with `audio_url`; poll `GET /v2/pre-recorded/{id}` until `status` is `done`. Live transcription is WebSocket after `POST /v2/live` returns a `url` — operation `live` is `kind: ingress` and does **not** open a socket. Use a BPMN message event.

EU-hosted option: confirm the current Gladia data-residency endpoint in their docs before sending regulated audio; this package uses the documented global `https://api.gladia.io`.

Dropped catalog `transcribe` as a duplicate of `pre_recorded` (same HTTP path).

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `pre_recorded` | POST | `/v2/pre-recorded` | Submit hosted `audio_url` (async job) |
| `get_transcript` | GET | `/v2/pre-recorded/{id}` | Poll job metadata / result |
| `live` | — | ingress | Live WebSocket STT — not opened by this node |

### Operation selection

Set **`operation`** on INPUT (or instance config), or let the node infer it when the tool omits the verb:

| Payload | Inferred operation |
|---|---|
| `audio_url` present (with or without `id`) | `pre_recorded` |
| `id` only (no `audio_url`) | `get_transcript` |
| Neither | config default (`pre_recorded`) → needs `audio_url` |

Agent tools should expose `operation`, `audio_url`, and `id` so create→poll round-trips work.

### Local validation (`pre_recorded`)

- `audio_url`: required; `http`/`https` URL.
- `model`: `solaria-1` or `solaria-3` when set.
- `language_config.languages`: non-empty when the key is present.

### Local validation (`get_transcript`)

- `id`: required UUID.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | `audio_url` / `id`. No file upload; no secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}` or `BPMN error`. Secrets redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `pre_recorded`. |
| `api_key` | password | **yes** | `x-gladia-key`. |
| `timeout_seconds` | number | no | Default `60`. Floor 1. |

## Legal

- [General Terms of Use (Gladia Inc.)](https://www.gladia.io/general-terms-of-use-gladia-inc)
- Operator is the Gladia customer. Audio and transcripts leave the daemon toward Gladia. DPIA if the workflow handles identifiable content. Do not log `api_key`, audio bytes, or transcript text.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `header` / `x-gladia-key`.
- `POST /v2/live` is the official HTTP session init (returns `id` + WebSocket `url`). This node does not call it because `live` is ingress-only.
- Do not poll `get_transcript` in a tight loop inside one invoke; use a later BPMN timer step or Gladia `callback`.
- HTTP 4xx/5xx → `BPMN error` on OUTPUT. Never log `api_key`.

## Unverified

- Whether a dedicated EU control-plane host exists besides app-level residency settings.
- Default model when `model` is omitted (docs: Solaria-1; Solaria-3 is async-only and language-limited).
