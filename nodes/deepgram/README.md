# Deepgram

Official Deepgram API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_DEEPGRAM`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Deepgram Listen v1 (pre-recorded REST) + Speak v1 (TTS REST) + Listen streaming WebSocket. Researched 2026-08-22.
- **Origin:** `https://api.deepgram.com`
- **Docs:** [Listen pre-recorded](https://developers.deepgram.com/reference/speech-to-text/listen-pre-recorded) · [Listen streaming](https://developers.deepgram.com/reference/speech-to-text/listen-streaming) · [Speak](https://developers.deepgram.com/reference/text-to-speech/speak)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Token {api_key}` (Bearer JWTs are also documented; this node ships Token prefix). Never on INPUT. Never logged.

## Scope

One node for Deepgram pre-recorded STT, TTS, and a live-stream ingress stub. Pre-recorded HTTP is `POST /v1/listen`. OfficialApiNode treats the operation **name** `listen` as ingress (no socket), so the HTTP verb is `transcribe` → `POST /v1/listen`. Live WebSocket (`wss://api.deepgram.com/v1/listen`, Flux `wss://api.deepgram.com/v2/listen`) is operation `listen` with `kind: ingress`. Use a BPMN message event; this node does not open a WebSocket.

Dropped catalog `analyze`: Audio Intelligence (summarize, sentiment, topics, intents, diarize) is query flags on `POST /v1/listen`, not a separate path.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `transcribe` | POST | `/v1/listen` | Pre-recorded STT. JSON body `{url}` (hosted media). Intelligence via query flags |
| `speak` | POST | `/v1/speak` | TTS; response audio is type `file` |
| `listen` | — | ingress | Live WebSocket STT — not opened by this node |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | `operation` (`transcribe` \| `speak` \| `listen`) overrides the instance select when set. `url` for transcribe; `text` for speak. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. Secrets redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `transcribe`. |
| `api_key` | password | **yes** | `Authorization: Token {api_key}`. |
| `model` | string | no | Query `model`. STT: `nova-3` / `nova-3-medical` / `nova-2` ([models](https://developers.deepgram.com/docs/models-languages-overview)). Speak: `aura-2-thalia-en` ([TTS models](https://developers.deepgram.com/docs/tts-models)). |
| `timeout_seconds` | number | no | Default `60`. Floor 1. |

## Legal

- [Terms of Service](https://deepgram.com/terms)
- [Privacy](https://deepgram.com/privacy)
- Audio and transcripts leave the daemon toward Deepgram. Operator is controller. Do not log `api_key`, audio bytes, or transcript text. Review Deepgram’s Model Improvement Program (`mip_opt_out`) before sending regulated audio.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `header` / `Authorization` / prefix `Token `.
- Pre-recorded binary audio body (raw bytes, not `{url}`) is documented; OfficialApiNode JSON-encodes `bodyFrom` — send a hosted `url` or wait for a later raw-body path.
- `speak` returns binary audio; OfficialApiNode may place a non-JSON body in `data`. Persist as platform `file` in a later handler revision.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.

## Unverified

- Default live model vs Nova-3 vs Flux v2 listen for new accounts.
- Whether Token vs Bearer JWT is required on some enterprise projects.
