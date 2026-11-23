# AssemblyAI

Official AssemblyAI Speech-to-Text node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_ASSEMBLYAI`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** AssemblyAI REST v2 transcripts + upload; Streaming v3 WebSocket. Researched 2026-08-22.
- **Origin:** `https://api.assemblyai.com` (EU transcription: `https://api.eu.assemblyai.com`)
- **Docs:** [Submit transcript](https://www.assemblyai.com/docs/api-reference/transcripts/submit) · [Get transcript](https://www.assemblyai.com/docs/api-reference/transcripts/get) · [Select the speech model](https://www.assemblyai.com/docs/pre-recorded-audio/select-the-speech-model) · [Transcribe a file](https://www.assemblyai.com/docs/pre-recorded-audio/getting-started/transcribe-an-audio-file) · [Streaming WebSocket](https://www.assemblyai.com/docs/streaming/api-spec/streaming-websocket)
- **Auth:** StudioQuack secret `api_key` → header `authorization` with the raw key (**no** `Bearer` prefix). Never on INPUT. Never logged.

## Scope

One node for AssemblyAI pre-recorded transcription. `transcribe` submits `POST /v2/transcript`; `get_transcript` polls `GET /v2/transcript/{transcript_id}`; `upload` is `POST /v2/upload` for local `file` bytes (returns `upload_url` to pass as `audio_url`). Streaming is WebSocket (`wss://streaming.assemblyai.com/v3/ws`) — operation `realtime` is `kind: ingress` and does **not** open a socket. Use a BPMN message start / catch event.

**Dropped `lemur`:** `POST /lemur/v3/generate/task` is no longer the current official LLM surface. AssemblyAI now documents [LLM Gateway](https://www.assemblyai.com/docs/api-reference/lemur/task) at `https://llm-gateway.assemblyai.com/v1/chat/completions` (different host). Do not call LLM Gateway from this STT node.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `transcribe` | POST | `/v2/transcript` | Submit a publicly reachable `audio_url` (or an `upload_url`) |
| `get_transcript` | GET | `/v2/transcript/{transcript_id}` | Poll until `status` is `completed` or `error` |
| `upload` | POST | `/v2/upload` | Raw-byte upload of type `file`; then transcribe the returned URL |
| `realtime` | — | ingress | Streaming WebSocket — not opened by this node |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | `audio_url` / `speech_models` / `transcript_id` / type `file` for upload. No secrets. Async model select is `speech_models` (array: `universal-3-5-pro`, `universal-2`). The deprecated singular `speech_model` is not accepted. `redact_pii: true` also requires `redact_pii_policies` (policy-name list). The node does not invent a default policy. |
| `output` | OUTPUT | object | `{status, http_status, data}`. Secrets redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `transcribe`. |
| `api_key` | password | **yes** | Raw `authorization` header. |
| `timeout_seconds` | number | no | Default `60`. Floor 1. |

## Legal

- [Terms of Service](https://www.assemblyai.com/legal/terms-of-service)
- [Acceptable Use Policy](https://www.assemblyai.com/legal/acceptable-use-policy)
- [Privacy Policy](https://www.assemblyai.com/legal/privacy-policy)
- Audio and transcripts leave the daemon toward AssemblyAI. Operator is controller. HIPAA only with an AssemblyAI BAA. Do not log `api_key`, audio bytes, or transcript text.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `header` / `authorization` / empty prefix.
- `upload` is `POST /v2/upload` with `bodyFormat: raw` and `fileBytes: true`. The body is the platform file's bytes (`Content-Type: application/octet-stream`). A plain string that is not a path under `/sandbox/files` is rejected before HTTP. A ContentBlock is sent as its decoded bytes.
- Do not poll `get_transcript` in a tight loop inside one invoke; use a later BPMN timer step.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.

## Unverified

- Whether any remaining production accounts still have LeMUR `/lemur/v3/generate/task` enabled (docs now redirect that URL to LLM Gateway).
- Exact streaming (WebSocket) model enum churn (`universal-3-5-pro` vs `universal-streaming-*`). Async HTTP uses `speech_models` only.
