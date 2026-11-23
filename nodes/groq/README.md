# Groq

Official Groq Cloud API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_GROQ`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** GroqCloud OpenAI-compatible API (`/openai/v1`). Documented REST as of 2026-08-20.
- **Origin:** `https://api.groq.com`
- **Docs:** [API reference](https://console.groq.com/docs/api-reference) · [Speech to text](https://console.groq.com/docs/speech-to-text) · [Text to speech](https://console.groq.com/docs/text-to-speech) · [Models](https://console.groq.com/docs/models)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One LLM provider for GroqCloud chat, STT, TTS, and model listing. Ports: **IN `llm_input` / OUT `llm_output`**. **No extra FUNCTION ports.** Translations (`/audio/translations`) are not a catalog verb and are omitted.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/openai/v1/chat/completions` | Agent model |
| `stt` | POST | `/openai/v1/audio/transcriptions` | Whisper transcription (`file` or `url`) |
| `tts` | POST | `/openai/v1/audio/speech` | Orpheus speech (`input` + `voice`) |
| `list_models` | GET | `/openai/v1/models` | Discover model ids |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, file?, input?, voice?}`. Audio uses type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}` . Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token. |
| `model` | string | no | Default `openai/gpt-oss-120b`. `llama-3.3-70b-versatile` / `llama-3.1-8b-instant` shut down 2026-08-16 for free/developer tiers. Docs: https://console.groq.com/docs/models |
| `temperature` | number | no | Chat/STT sampling on INPUT. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Groq Services Agreement](https://console.groq.com/docs/legal/services-agreement) (governs GroqCloud; [groq.com/terms-of-use](https://groq.com/terms-of-use) is website-only)
- [Your data](https://console.groq.com/docs/your-data)
- Prompts and audio leave the host to Groq. Inference logs may be retained up to 30 days unless ZDR/data-controls apply. Do not send PHI without a covering agreement. Official-API-first: no scrape of GroqCloud Playground.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- `stt` is documented as multipart; the generic handler JSON-encodes `bodyFrom` — binary uploads may need a later multipart path. `url` is a documented alternative.
- TTS returns audio bytes (not JSON); OfficialApiNode still places the body in `data`.
- HTTP 4xx/5xx Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.  Never log `api_key`.
