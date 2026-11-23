# OpenAI Models

## Overview

Official OpenAI HTTPS provider on the v2 agent **model** contract (`llm_input` → `llm_output`). `templateId` `4f682e07-d874-4d10-abb3-0174ca65ec19` (env `SQ_TEMPLATE_ID_OPENAI_MODELS`).

The handler talks to **`https://api.openai.com/v1`** with JDK `HttpClient` (HTTP/1.1, connect 10s on the shared client, request 300s). Streaming bodies add a 600s idle watchdog after headers (`HttpRequest.timeout` only waits for headers). Optional instance overrides (not in the designer): `read_timeout_seconds` [30, 600], `stream_idle_timeout_seconds` [5, 600]. `connect_timeout_seconds` is accepted but unused (one process-wide client). Timeouts name model + endpoint + elapsed. Non-2xx bodies use the provider `error.message`. Bind `api_key` to a StudioQuack secret. Never log it.

| Family | Detection | Endpoint | Docs |
| --- | --- | --- | --- |
| Chat / reasoning | default (`gpt-5*`, `gpt-4*`, `o3*`, `o4*`, …) | `POST /v1/chat/completions` | [Chat Completions](https://platform.openai.com/docs/api-reference/chat) |
| Images | `gpt-image-*`, `dall-e-*` | `POST /v1/images/generations` | [Images](https://platform.openai.com/docs/api-reference/images) |
| TTS | `tts-1`, `tts-1-hd`, `gpt-4o-mini-tts`, ids ending `-tts` | `POST /v1/audio/speech` | [Speech](https://platform.openai.com/docs/api-reference/audio/createSpeech) |
| STT | `whisper-1`, `*transcribe*`, `whisper*` | `POST /v1/audio/transcriptions` (multipart) | [Transcriptions](https://platform.openai.com/docs/api-reference/audio/createTranscription) |

SDK reference: [OpenAI API](https://platform.openai.com/docs/api-reference). Keys: [API keys](https://platform.openai.com/api-keys).

Handler: `OpenAiModelsNode`. Shared process-wide `HttpClient` (thread-safe). Dispatch by `templateId` in `NodeLibraryApp`.

## Ports

| Port | Direction | Type | Schema | How the node uses incoming data |
| --- | --- | --- | --- | --- |
| `llm_input` | INPUT | `llm_input` | `{messages, tools?, inputFiles?, stream?, streaming?, options?}` plus media kwargs | Read `getInputValuesByPortValue("llm_input")`, then `inputVariables.llm_input`, then any input row whose value contains `messages`. Bare string → one user message. JSON string is parsed. Schema-default stubs with null/empty `messages` are skipped. `inputFiles` fold onto the last user message as OpenAI parts (FULL). Missing usable input → `PROVIDER_ERROR` `No input received on 'llm_input' port.` |
| `llm_output` | OUTPUT | `llm_output` | `{result: <assistant>, outputFiles?: [...]}` | Always one ResultItem on success. Failures raise `PROVIDER_ERROR`. `outputFiles` mirrors `result.output_files` when that list is non-empty (omitted when empty). During streaming, thinking/answer deltas already went out via `emitPartial`; this object is the assembled end state. |

### `llm_input` fields

| Field | Type | Required | Purpose |
| --- | --- | --- | --- |
| `messages` | list | for chat; optional for image/TTS/STT (prompt/audio derived from text/blocks) | Conversation `{role, content, tool_calls?, thinking?, thoughts?, tool_call_id?, name?}`. `role`: `system` \| `user` \| `assistant` \| `tool`. `content`: string **or** typed blocks `text` / `image` / `audio` / `video` / `document` / `tool_use` / `tool_result`. `data:` base64 prefixes are stripped before send. |
| `tools` | list | no | `{name, description, request:<JSON Schema>}` → OpenAI `{type:"function", function:{name, description, parameters}}`. Schema types normalised (`list`→`array`, …). Empty → omitted. Ignored for image/TTS/STT. |
| `inputFiles` | file list | no | User-uploaded files / voice clips `{kind, mime_type, source, data, name}`. Folded onto the last user message via `OpenAiContentParts` (FULL) before Chat Completions or Images/TTS/STT. Empty/omitted → no-op. Never logged. |
| `stream` | boolean / string | no | Per-call canonical override (top-level or `options.stream`). Wins over config. |
| `streaming` | boolean / string | no | Alias used only when `stream` is omitted/unparseable. |
| other top-level keys | mixed | no | Merged into `options` (`messages`/`tools`/`options`/`result`/`inputFiles`/`outputFiles` excluded). Image/TTS/STT read `n`, `size`, `quality`, `voice`, `format`, `language`, `prompt`, … from `options`. |

Boolish tokens: `true`/`1`/`yes`/`on`/`stream`/`streaming` → true; `false`/`0`/`no`/`off`/`nonstream`/`non-stream`/`sync` → false (case-insensitive). Invalid → fall through.

`response_format` and `modalities` are **instance config**, not per-call fields on this port.

### `llm_output.result`

Success:

`{role:"assistant", content:[{type:"text", text}], tool_calls?:[{id, name, args, type:"tool_call"}], thoughts?:[{type, text, signature?}], thinking?:[{type:"thinking", thinking}] (legacy mirror), output_files?:[{type, mime_type, source, data, name?}], finish_reason?, usage?:{prompt_tokens, completion_tokens, total_tokens, reasoning_tokens?}}`

Error (still `role:"assistant"` so the agent loop can ingest it):

`{role:"assistant", content:[{type:"text", text:"[OpenAI provider error] …"}], finish_reason:"error"|"length"|"content_filter", error:"<redacted message>"}`

`error` never contains the raw `api_key`. `Bearer` and `sk-…` substrings are redacted.

## Configuration parameters

| shortCode | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `model` | select | runtime (empty → `gpt-5.6-sol`) | `gpt-5.6-sol` | See options list in `node_definition.json` (gpt-5.6-sol/terra/luna, gpt-5.5*, gpt-5.4*, gpt-5.2*, gpt-4.x, o3/o4, `gpt-image-2` + previous gpt-image-*, retired `dall-e-2/3`, `whisper-1`, `gpt-4o-*-transcribe`, `tts-1*`, `gpt-4o-mini-tts`) | Selects API family and parameter whitelist. Docs: https://developers.openai.com/api/docs/models |
| `api_key` | password | **runtime yes** | `""` | Secret binding; non-empty after resolution | Authenticates `Authorization: Bearer`. Empty → `Missing api_key configuration.` |
| `temperature` | number | no | `1.0` | `0.0–2.0` | Chat sampling. **Stripped** on reasoning models. Ignored for image/TTS/STT. Non-numeric skipped. |
| `top_p` | number | no | `1.0` | `0.0–1.0` | Nucleus sampling. Stripped on reasoning models. |
| `max_completion_tokens` | number | no | empty (omit) | Positive int | Output cap. The deprecated `max_tokens` is never sent. |
| `frequency_penalty` | number | no | empty | `-2.0–2.0` | Stripped on reasoning models. |
| `presence_penalty` | number | no | empty | `-2.0–2.0` | Stripped on reasoning models. |
| `stop` | string | no | empty | Comma-separated, max 4, or a single string | Halt sequences. **Stripped** for reasoning models and ids starting `gpt-5` / `computer-use`. |
| `n` | number | no | `1` | Positive int. Image: 1–10; `dall-e-3` forced to 1 | Completions count. Handler returns **first choice only**. |
| `reasoning_effort` | select | no | `medium` | `none` \| `minimal` \| `low` \| `medium` \| `high` \| `xhigh` \| `max` | Sent only for reasoning models. GPT-5.6: none/low/medium/high/xhigh/max. |
| `verbosity` | string | no | empty | `low` \| `medium` \| `high` | Reasoning verbosity; omitted when empty. |
| `response_format` | string | no | `text` | JSON object string, `json` → `{type:json_object}`, `text` → `{type:text}` | Chat Completions `response_format`. Invalid JSON that is not json/text is skipped. Not used on `gpt-image-1`. |
| `modalities` | string | no | `text` | Comma list or JSON array; tokens filtered to `text` / `audio` | Chat output modalities (`gpt-4o-audio-*`). Empty after filter → omitted. |
| `audio` | string | no | `{"voice":"alloy","format":"wav"}` | JSON object | Audio-out voice/container. Invalid JSON → omitted. |
| `parallel_tool_calls` | boolean | no | `false` | boolean / true\|1\|yes | Sent `true` only when true **and** tools are present. |
| `prompt_cache_key` | string | no | empty | Non-empty string | OpenAI prompt-prefix cache bucket. |
| `seed` | number | no | empty | Integer | Best-effort determinism. Non-numeric omitted. |
| `service_tier` | select | no | `auto` | `auto` \| `default` \| `flex` \| `priority` \| `fast` | Capacity class. `fast` is Fast mode (alias of `priority` on GPT-5.6 and earlier). Empty not sent. |
| `safety_identifier` | string | no | empty | Opaque id | OpenAI safety classifier id. **Not PHI / not secrets.** |
| `stream` | select | no | `true` | `true` \| `false` | Canonical streaming flag. Input-port override wins. Invalid/blank → true. |

Resolution: instance `configurationParameters` first, then `helper.getParameterValueByShortCode`. Blank strings are treated as unset.

**Reasoning detection** (`OpenAiConfig.isReasoning`): id starts with `o1` / `o3` / `o4` / `o-`, **or** contains `reasoning`, **or** starts with `gpt-5`. Sampling (`temperature`, `top_p`, penalties, `logprobs`, `stop`) is stripped; `reasoning_effort` (default `medium`) and optional `verbosity` are injected.

Code also forwards `logprobs` / `top_logprobs` when present on the instance even though they are not designer fields.

## How it works

### Dispatch

`AiNodeHandlers` registers `OpenAiModelsNode` for this templateId. Failures raise `PROVIDER_ERROR` (`OpenAiChat.errorResponse` / `LlmAssistant.errorResponse`). Success reply `{llm_output: {result: …}}`.

### Family branch

1. Require non-blank `api_key`.
2. Read/coerce `llm_input`.
3. Fold `inputFiles` and convert every message through `OpenAiContentParts` (FULL). Office (xlsx/ods) is always extracted to text. PDF stays a native `file` part only on vision-capable chat models.
4. If image / TTS / STT model → `OpenAiMedia` (non-stream APIs; `stream` flag ignored; chat gate skipped).
5. Else chat: `OpenAiCapabilities` rejects leftover image parts on models without vision (`o3-mini*`, `o1-mini*`, `o1-preview*`, `gpt-3.5*`, and any other non-vision id) with an actionable message naming gpt-5.5 / gpt-5 / gpt-4o.
6. `OpenAiChat.buildOpenaiMessages` converts remaining blocks to OpenAI parts (already-native `image_url` / `file` / `input_audio` pass through).
7. Reasoning models → Responses API (`/v1/responses`); else Chat Completions. Stream → idle-watched SSE; else unary JSON.

Image prompt = last user text. TTS input = last user text. STT audio = last user `audio` block/`files` entry (base64; `data:` prefix stripped).

### Chat streaming (`OpenAiSse`)

`POST /v1/chat/completions` with `stream:true` and `stream_options:{include_usage:true}`. HTTP 400 mentioning `stream_options` retries **once** without that field.

SSE `data:` lines are JSON chunks (`[DONE]` ends). Collector:

- `emitPartial("thinking", delta)` for `reasoning` / `reasoning_content` / `thinking` / `thoughts` and content parts typed thinking/reasoning
- `emitPartial("answer", delta)` for visible text
- tool-call argument fragments assembled by index
- audio deltas → `output_files` (base64)
- `finish_reason` + `usage` (including `reasoning_tokens`) at the end

If the provider ignores `stream=true` and returns JSON (`Content-Type` not `event-stream`), the envelope is ingested as a single chunk.

`emitPartial` is `POST /v1/helper/emit-partial` over UDS — best-effort, never fails the node.

### Retry / backoff

Up to **4** chat attempts. Retry **only** when the error matches `Unsupported parameter: '<name>'` and that key is stripped from the body. **No** 429 exponential backoff. 4xx/5xx become `OpenAI API error: <redacted message>`.

Connect timeout 10s (shared client); per-request 300s; stream idle after headers 60s. Redirects are not followed.

### Error mapping (raises `PROVIDER_ERROR`)

| Condition | Message / notes |
| --- | --- |
| Blank `api_key` | `Missing api_key configuration.` |
| Unusable `llm_input` | `No input received on 'llm_input' port.` |
| Vision on gpt-3.5 / o1-mini / o1-preview | `Model '…' does not accept multimodal input. …` |
| Message normalisation throw | `Message normalisation failed: …` |
| HTTP ≥ 400 | `OpenAI API error: …` (or `OpenAI streaming failed: …`) |
| `finish_reason=content_filter` with empty body | `Response blocked by content filter.` |
| `finish_reason=length` with empty body | System text asking to raise `max_completion_tokens` |
| Image without user text | IllegalArgumentException → redacted `PROVIDER_ERROR` |
| TTS without text / STT without audio | same |

## Security & legal

- **Secrets:** `api_key` from `configurationParameters` (secret binding) only. Resolved just-in-time into the sandbox invoke. Added to the daemon redaction set. Never in graph JSON as a literal, never in `result`, never in logs (`OpenAiChat.redact` strips `Bearer` / `sk-`).
- **Data leaving the host:** prompts, tool schemas, images, audio, and PDFs are POSTed to `api.openai.com`. That is a transfer to OpenAI (US) — GDPR Art. 44+; the workflow owner is controller and must have a lawful basis / DPA with OpenAI. Do not put PHI in `safety_identifier` or `prompt_cache_key`.
- **Sandbox:** Podman; needs **egress** to `api.openai.com:443`. Helper UDS is used only for `emitPartial` / parameter reads, not for the OpenAI call.
- **Rate limits:** OpenAI account TPM/RPM. This node does not retry 429. Bound `max_completion_tokens` and avoid unbounded `n`.
- **ToS:** [OpenAI Terms of Use](https://openai.com/policies/terms-of-use/) and [Usage policies](https://openai.com/policies/usage-policies/). Customer bears provider billing and ToS compliance.
- **Liability:** customer owns the endpoint and the OpenAI account. StudioQuack isolates the secret to this env’s sandbox; a compromised daemon must not expose another env’s key.

## Examples

### 1. Chat + tools (stream)

`llm_input`:

```json
{
  "messages": [
    {"role": "system", "content": "You are a warehouse copilot. Answer in one sentence."},
    {"role": "user", "content": "What bin is order 42 in?"}
  ],
  "tools": [
    {
      "name": "lookup_order",
      "description": "Fetch an order by id.",
      "request": {
        "type": "object",
        "properties": {"order_id": {"type": "string"}},
        "required": ["order_id"]
      }
    }
  ],
  "stream": true
}
```

`llm_output`:

```json
{
  "result": {
    "role": "assistant",
    "content": [{"type": "text", "text": "I am calling a tool to answer your request."}],
    "tool_calls": [
      {"id": "call_1", "name": "lookup_order", "args": {"order_id": "42"}, "type": "tool_call"}
    ],
    "thoughts": [{"type": "thinking", "text": "Need the warehouse record."}],
    "thinking": [{"type": "thinking", "thinking": "Need the warehouse record."}],
    "finish_reason": "tool_calls",
    "usage": {"prompt_tokens": 80, "completion_tokens": 24, "total_tokens": 104}
  }
}
```

### 2. Image generation

Instance `model` = `gpt-image-1`. `llm_input`:

```json
{
  "messages": [
    {"role": "user", "content": "Warehouse bin map, isometric, labeled A-12."}
  ],
  "n": 1,
  "size": "1024x1024"
}
```

`result.content` is a short summary; `result.output_files` holds `{type:"image", mime_type:"image/png", source:"base64", data:"…", name:"generated_….png"}`. Streaming flags are ignored.
