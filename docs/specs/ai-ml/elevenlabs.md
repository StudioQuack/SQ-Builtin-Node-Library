# ElevenLabs

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/elevenlabs/`
- **Researched:** 2026-08-20
- **Official API:** ElevenAPI REST v1 (voices list uses v2), host `https://api.elevenlabs.io`
- **Docs:** [API reference](https://elevenlabs.io/docs/api-reference) · [Create speech](https://elevenlabs.io/docs/api-reference/text-to-speech/convert) · [Create transcript](https://elevenlabs.io/docs/api-reference/speech-to-text/convert) · [List voices](https://elevenlabs.io/docs/api-reference/voices/search) · [Create IVC](https://elevenlabs.io/docs/api-reference/voices/ivc/create) · [Create dub](https://elevenlabs.io/docs/api-reference/dubbing/create)
- **Auth:** api_key — secret binding: `api_key` (header `xi-api-key`)

## Scope

One node for ElevenLabs **TTS, STT, voice library, Instant Voice Cloning (IVC), and dubbing**. Whisper / OpenAI STT stays on the OpenAI Models node. Audio in and out is platform type `file` (ref-id), never inline bytes.

Do not scrape elevenlabs.io. Do not impersonate a real person. IVC requires documented consent from the voice owner. Professional Voice Cloning (PVC) is **not** an operation: PVC requires interactive identity verification on ElevenLabs and cannot be completed from this handler.

`get_transcript`, `get_dub`, and `download_dub` are kept so async STT (webhook) and dubbing jobs are usable. They are not extra catalog rows; they are the poll/download surface for `stt` / `dub`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `tts` | POST | `/v1/text-to-speech/{voice_id}` | Synthesize speech; store audio as `file` |
| `stt` | POST | `/v1/speech-to-text` | Transcribe audio/video (`multipart/form-data`). Sync 200, or 202 when `webhook=true` |
| `get_transcript` | GET | `/v1/speech-to-text/transcripts/{transcription_id}` | Poll async STT when `stt` used `webhook=true` |
| `voices_list` | GET | `/v2/voices` | Paginated voice library (search/filter) |
| `clone_voice` | POST | `/v1/voices/add` | Instant Voice Clone (IVC). Requires `consent_attested=true` |
| `dub` | POST | `/v1/dubbing` | Start a dubbing job (`multipart/form-data`); returns `dubbing_id` |
| `get_dub` | GET | `/v1/dubbing/{dubbing_id}` | Poll dub status (`dubbed` / in-progress / failed) |
| `download_dub` | GET | `/v1/dubbing/{dubbing_id}/audio/{language_code}` | Fetch finished dub as `file` (MP3/MP4) |

**Dropped (not on this node):** music, sound effects, text-to-dialogue, voice changer, voice isolator, voice design, voice remix, forced alignment, ElevenAgents, Studio projects, PVC create, TTS WebSocket (`/v1/text-to-speech/{voice_id}/stream-input`), STT realtime WebSocket. Those are other ElevenAPI products or interactive dashboard flows.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `tts` | no | Chooses the row above |
| `api_key` | password | yes | empty | **yes** | `xi-api-key`. Never on INPUT, never logged |
| `voice_id` | string | `tts` if input omits | empty | no | Default voice id (e.g. library id `JBFqnCBsd6RMkjVDRZzb`) |
| `model_id` | string | no | TTS: `eleven_multilingual_v2`; STT: `scribe_v2` | no | Default model. TTS flagship `eleven_v3` is 5 000 characters; Flash v2.5 is 40 000 |
| `output_format` | string | no | `mp3_44100_128` | no | TTS query `output_format` (codec_sample_rate_bitrate) |
| `base_url` | string | no | `https://api.elevenlabs.io` | no | Origin only. Residency: `https://api.us.elevenlabs.io`, `https://api.eu.residency.elevenlabs.io`, `https://api.in.residency.elevenlabs.io`, `https://api.sg.residency.elevenlabs.io` |
| `timeout_seconds` | number | no | `120` | no | Connect+read. Floor 1. STT/TTS/dub uploads need the higher budget |
| `max_upload_bytes` | number | no | `52428800` (50 MiB) | no | Handler cap for `file` uploads. Provider STT allows up to 5 GB; do not stream that through the sandbox |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Audio/video is `file` |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = tts`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | Body `text`. Trim; empty → `{ error }` no HTTP. Cap to the selected model’s character limit (v3: 5 000; multilingual v2: 10 000; flash v2.5: 40 000) |
| `voice_id` | string | if config blank | Path `{voice_id}` |
| `model_id` | string | no | Overrides config. Default `eleven_multilingual_v2` |
| `language_code` | string | no | ISO 639-1; ignored by multilingual_v2 per docs |
| `output_format` | string | no | Query override |
| `stability` | number | no | `voice_settings.stability` (0–1, default 0.5) |
| `similarity_boost` | number | no | `voice_settings.similarity_boost` (default 0.75) |
| `style` | number | no | `voice_settings.style` (default 0) |
| `speed` | number | no | `voice_settings.speed` (default 1) |
| `use_speaker_boost` | boolean | no | `voice_settings.use_speaker_boost` |
| `seed` | number | no | 0–4294967295; best-effort determinism |

### `operation = stt`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `audio` | file | yes unless `source_url` | Multipart field `file`. Min 100 ms audio. Exactly one of `audio` or `source_url` |
| `source_url` | string | yes unless `audio` | HTTPS URL of audio/video (YouTube/TikTok listed as supported). Prefer `file` for customer media |
| `model_id` | string | no | Required by API; default `scribe_v2` |
| `language_code` | string | no | ISO 639-1 or 639-3; omit to auto-detect |
| `diarize` | boolean | no | Speaker labels |
| `num_speakers` | number | no | Max speakers (API max 32) |
| `timestamps_granularity` | string | no | `word` \| `character` \| `none` |
| `tag_audio_events` | boolean | no | Tag laughter/footsteps etc. |
| `webhook` | boolean | no | `true` → 202 + `transcription_id`; poll `get_transcript`. Default `false` (wait for 200) |
| `keyterms` | array | no | Bias terms (max 1000; extra surcharge) |

### `operation = get_transcript`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `transcription_id` | string | yes | Path id from `stt` when `webhook=true` |

### `operation = voices_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `search` | string | no | Filter name/description/labels |
| `page_size` | number | no | Max 100, default 10 |
| `next_page_token` | string | no | Pagination |
| `voice_type` | string | no | `personal` \| `community` \| `default` \| `workspace` \| … as documented |
| `category` | string | no | `premade` \| `cloned` \| `generated` \| `professional` |

### `operation = clone_voice`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `consent_attested` | boolean | **yes, must be `true`** | Operator attests they have the speaker’s consent (or it is their own voice). If missing/false → `{ error }` **no HTTP** |
| `name` | string | yes | Voice display name |
| `samples` | file or array of file | yes | One or more short recordings (`files` multipart). Prefer < 2 minutes of clean speech |
| `description` | string | no | Voice description |
| `remove_background_noise` | boolean | no | Audio isolation on samples (can hurt clean recordings) |

### `operation = dub`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target_lang` | string | yes | ISO 639-1 or 639-3 |
| `source` | file | yes unless `source_url` | Multipart `file` |
| `source_url` | string | yes unless `source` | Remote media URL |
| `source_lang` | string | no | Source language code |
| `name` | string | no | Project name |
| `num_speakers` | number | no | `0` = auto |
| `watermark` | boolean | no | Watermark output video |
| `drop_background_audio` | boolean | no | Drop bed for speech-only sources |
| `disable_voice_cloning` | boolean | no | Use library voices instead of cloning speakers |

### `operation = get_dub`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dubbing_id` | string | yes | From `dub` |

### `operation = download_dub`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dubbing_id` | string | yes | Job id |
| `language_code` | string | yes | Target language of the finished dub |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `audio` | file | `tts`, `download_dub` | Platform ref-id of stored audio/video |
| `content_type` | string | file outs | e.g. `audio/mpeg`, `video/mp4` |
| `text` | string | `stt` sync, `get_transcript` | Transcript |
| `language_code` | string | STT | Detected language |
| `language_probability` | number | STT | 0–1 |
| `words` | array | STT | Timing / speaker / type |
| `transcription_id` | string | STT | Id for poll / support |
| `audio_duration_secs` | number | STT | Duration billed |
| `voices` | array | `voices_list` | `{ voice_id, name, category, … }` (no sample bytes) |
| `has_more` | boolean | `voices_list` | Pagination |
| `next_page_token` | string | `voices_list` | Next page |
| `voice_id` | string | `clone_voice` | New IVC id |
| `requires_verification` | boolean | `clone_voice` | Provider flag |
| `dubbing_id` | string | `dub`, `get_dub` | Job id |
| `expected_duration_sec` | number | `dub` | Estimate |
| `dub_status` | string | `get_dub` | Provider `status` (`dubbed` when ready) |
| `target_languages` | array | `get_dub` | Dubbed language codes |
| `error` | string | failure | Actionable; key redacted to `***` |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` if present; else 60 on 429, 30 on 5xx |

## Legal

- Terms of Use: https://elevenlabs.io/terms-of-use
- Prohibited Use Policy: https://elevenlabs.io/use-policy — **no unauthorized, deceptive, or harmful impersonation**; no cloning a real identifiable person without consent
- Privacy: https://elevenlabs.io/privacy
- Voice cloning help (PVC is own-voice only): https://elevenlabs.io/docs/eleven-creative/voices/voice-cloning
- Credits and concurrency are **plan-specific** (credits per character for TTS; STT billed on audio duration). Concurrency is per model family. HTTP 429 must not tight-loop.
- **PII / biometric:** voiceprints and recordings are biometric data. Operator is controller. Require a DPIA for EU/UK production use of `clone_voice`, `stt`, and `dub`. Do not log audio bytes, transcripts, or `api_key`.
- HIPAA: only if the customer has an ElevenLabs BAA. This node does not create one.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. Header `xi-api-key`, `Accept` as needed, `User-Agent: StudioQuack/1.0`.
- Empty `api_key` → `{ error: "Missing ElevenLabs API key. Bind api_key in node parameters." }` — no HTTP.
- `tts`: JSON body. Persist response bytes via the daemon file store; put the ref-id on `audio`. Do not base64 the whole clip on the port.
- `stt` / `clone_voice` / `dub`: `multipart/form-data`. Resolve `file` refs to temp bytes inside the sandbox; delete after the request.
- `stt` with `webhook=false`: one blocking POST (timeout default 120 s). Large files should use `webhook=true` + `get_transcript` from a later BPMN step / timer — **do not poll in a tight loop inside the handler**.
- `clone_voice`: refuse unless `consent_attested === true`.
- Response JSON cap **2_000_000** bytes. Audio file write cap = `max_upload_bytes` unless downloading a dub (cap 100 MiB).
- 429 → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry.
- Logs: operation, status, sizes, ids — never `api_key`, never transcript/PII text beyond length.

## Unverified

- Exact per-plan TTS concurrency numbers (published on the models page; change often).
- Whether `source_url` STT still accepts YouTube/TikTok on every plan.
- Dubbing v2 resource-render path for Dubbing Studio edits — this node downloads **automatic** dub audio only (`GET .../audio/{language_code}`).
