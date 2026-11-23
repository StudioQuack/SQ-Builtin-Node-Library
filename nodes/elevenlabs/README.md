# ElevenLabs

Official ElevenLabs REST node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_ELEVENLABS`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** ElevenAPI REST (TTS v1, STT v1, Voices v2, IVC, dubbing). Researched 2026-08-22.
- **Origin:** `https://api.elevenlabs.io` (residency: `https://api.us.elevenlabs.io`, `https://api.eu.residency.elevenlabs.io`, `https://api.in.residency.elevenlabs.io`, `https://api.sg.residency.elevenlabs.io`)
- **Docs:** [API reference](https://elevenlabs.io/docs/api-reference) · [Create speech](https://elevenlabs.io/docs/api-reference/text-to-speech/convert) · [Create transcript](https://elevenlabs.io/docs/api-reference/speech-to-text/convert) · [Get transcript](https://elevenlabs.io/docs/api-reference/speech-to-text/get) · [List voices](https://elevenlabs.io/docs/api-reference/voices/search) · [Create IVC](https://elevenlabs.io/docs/api-reference/voices/ivc/create) · [Create dub](https://elevenlabs.io/docs/api-reference/dubbing/create)
- **Auth:** StudioQuack secret `api_key` → header `xi-api-key`. Never on INPUT. Never logged.

## Scope

One node for ElevenLabs TTS, STT, voice library, Instant Voice Cloning (IVC), and dubbing. Audio in/out is platform type `file` (ref-id), never inline bytes. Whisper / OpenAI STT stays on the OpenAI Models node.

Do not scrape elevenlabs.io. Do not impersonate a real person. IVC requires documented consent from the voice owner. Professional Voice Cloning (PVC) is not an operation: PVC requires interactive identity verification on ElevenLabs.

Dropped (other ElevenAPI products or interactive dashboard flows): music, sound effects, text-to-dialogue, voice changer, voice isolator, voice design, voice remix, forced alignment, Agents, Studio projects, PVC create, TTS WebSocket (`/v1/text-to-speech/{voice_id}/stream-input`), STT realtime WebSocket. Dubbing v2 (`POST /v1/dubbing/project`, announced 2026-08-06) is a separate project API — this node uses the still-documented automatic dub path `POST /v1/dubbing`.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `tts` | POST | `/v1/text-to-speech/{voice_id}` | Synthesize speech; response audio is type `file` |
| `stt` | POST | `/v1/speech-to-text` | Transcribe audio/video (`multipart/form-data`). Sync 200, or 202 when `webhook=true` |
| `get_transcript` | GET | `/v1/speech-to-text/transcripts/{transcription_id}` | Poll async STT |
| `voices_list` | GET | `/v2/voices` | Paginated voice library |
| `clone_voice` | POST | `/v1/voices/add` | Instant Voice Clone (IVC). Operator must attest consent |
| `dub` | POST | `/v1/dubbing` | Start automatic dubbing; returns `dubbing_id` |
| `get_dub` | GET | `/v1/dubbing/{dubbing_id}` | Poll dub status |
| `download_dub` | GET | `/v1/dubbing/{dubbing_id}/audio/{language_code}` | Fetch finished automatic dub as `file` |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. Audio/video is type `file`. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}` or `BPMN error`. Secrets redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | tts | no | Official ElevenLabs verb. EXACT: tts / stt / get_transcript / voices_list / clone_voice / dub / get_dub / download_dub. Default tts = POST /v1/text-to-speech/{voice_id}. |
| `api_key` | password | yes |  | yes | ElevenLabs xi-api-key. StudioQuack secret binding only. Never logged. Empty → error 'api_key is required'. |
| `voice_id` | string | no |  | no | Default TTS voice id when input.voice_id is blank. Example library id JBFqnCBsd6RMkjVDRZzb. |
| `model_id` | string | no | eleven_multilingual_v2 | no | Default model_id. Official TTS convert default is eleven_multilingual_v2. Current TTS ids also include eleven_v3 (expressive, 70+ languages) and eleven_flash_v2_5 (low latency).... |
| `voice_settings` | object | no |  | no | Request-body parameter `voice_settings` for operation: tts. |
| `previous_text` | string | no |  | no | Request-body parameter `previous_text` for operation: tts. |
| `next_text` | string | no |  | no | Request-body parameter `next_text` for operation: tts. |
| `text` | string | no |  | no | TTS body text. Empty → error, no HTTP. Model character caps apply (e.g. multilingual_v2 10 000). |
| `seed` | number | no |  | no | Request-body parameter `seed` for operation: tts. |
| `language_code` | string | no |  | no | ISO code for tts/stt or download_dub path {language_code}. |
| `enable_logging` | boolean | no |  | no | Query-string parameter `enable_logging` for operation: tts. |
| `output_format` | string | no |  | no | Query-string parameter `output_format` for operation: tts. |
| `timestamps_granularity` | string | no |  | no | Request-body parameter `timestamps_granularity` for operation: stt. |
| `source_url` | string | no |  | no | HTTPS URL of audio/video for stt or dub when file is omitted. |
| `tag_audio_events` | boolean | no |  | no | Request-body parameter `tag_audio_events` for operation: stt. |
| `diarize` | boolean | no |  | no | Request-body parameter `diarize` for operation: stt. |
| `file` | file | no |  | no | Platform file ref-id sent as multipart field file for stt (audio/video to transcribe, min ~100 ms) and dub (source audio/video). Never inline bytes. |
| `webhook` | string | no |  | no | Request-body parameter `webhook` for operation: stt. |
| `keyterms` | string | no |  | no | Request-body parameter `keyterms` for operation: stt. |
| `num_speakers` | number | no |  | no | Request-body parameter `num_speakers` for operations: stt, dub. |
| `transcription_id` | string | no |  | no | Path id for get_transcript. |
| `category` | string | no |  | no | Query-string parameter `category` for operation: voices_list. |
| `page_size` | number | no |  | no | Query-string parameter `page_size` for operation: voices_list. |
| `next_page_token` | string | no |  | no | Query-string parameter `next_page_token` for operation: voices_list. |
| `voice_type` | string | no |  | no | Query-string parameter `voice_type` for operation: voices_list. |
| `search` | string | no |  | no | Query-string parameter `search` for operation: voices_list. |
| `remove_background_noise` | boolean | no |  | no | Request-body parameter `remove_background_noise` for operation: clone_voice. |
| `description` | string | no |  | no | Request-body parameter `description` for operation: clone_voice. |
| `name` | string | no |  | no | Display name for clone_voice or dub project. |
| `files` | file | no |  | no | Voice sample recording(s) for clone_voice, sent as multipart field files (one part per file). Platform file ref-id(s); prefer under 2 minutes of clean speech. Example: [ref-id of sample1.mp3, ref-id of sample2.mp3]. |
| `labels` | object | no |  | no | Request-body parameter `labels` for operation: clone_voice. |
| `watermark` | boolean | no |  | no | Request-body parameter `watermark` for operation: dub. |
| `target_lang` | string | no |  | no | Dub target language. ISO 639-1 or 639-3. Required for dub. Also used as language_code on download_dub. |
| `drop_background_audio` | boolean | no |  | no | Request-body parameter `drop_background_audio` for operation: dub. |
| `source_lang` | string | no |  | no | Request-body parameter `source_lang` for operation: dub. |
| `disable_voice_cloning` | boolean | no |  | no | Request-body parameter `disable_voice_cloning` for operation: dub. |
| `dubbing_id` | string | no |  | no | Path id for get_dub and download_dub. |
| `timeout_seconds` | number | no | 120 | no | HTTP timeout. Default 120. Floor 1. |

## Legal

- [Terms of Use](https://elevenlabs.io/terms-of-use)
- [Prohibited Use Policy](https://elevenlabs.io/terms-of-use/use-policy) — no unauthorized, deceptive, or harmful impersonation; no cloning a real identifiable person without consent
- [Privacy](https://elevenlabs.io/privacy)
- Voiceprints and recordings are biometric data. Operator is controller. Require a DPIA for EU/UK production use of `clone_voice`, `stt`, and `dub`. Do not log audio bytes, transcripts, or `api_key`.
- HIPAA: only if the customer has an ElevenLabs BAA. This node does not create one.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `header` / `xi-api-key`.
- `stt` / `clone_voice` / `dub` are documented as multipart; the generic handler JSON-encodes `bodyFrom` — large uploads may need a later multipart path.
- `tts` and `download_dub` return binary audio; OfficialApiNode parses JSON when possible and otherwise returns the body string in `data`. Persist as platform `file` in a later handler revision.
- HTTP 4xx/5xx → `BPMN error` on OUTPUT. Never log `api_key`.

## Unverified

- Exact per-plan TTS concurrency numbers (published on the models page; change often).
- Whether `source_url` STT still accepts YouTube/TikTok on every plan.
- Whether operators should migrate automatic dubs to Dubbing v2 `POST /v1/dubbing/project` (legacy `POST /v1/dubbing` still documented 2026-08-20).
