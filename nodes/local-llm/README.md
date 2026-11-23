# Local LLM

## Overview

Daemon-mediated OpenAI-compatible provider on the same v2 contract as OpenAI Models (`llm_input` → `llm_output`). `templateId` `9462f700-00cc-4e9c-872d-97a97aba984c` (env `SQ_TEMPLATE_ID_LOCAL_LLM`).

Inference goes through `helper.llm()` — the StudioQuack engine daemon proxies to the engine configured in **Engine Settings** (Ollama, vLLM, llama.cpp, TGI, or any BYO OpenAI-compatible `/v1/chat/completions` endpoint). **Base URL, API keys, and TLS never enter this sandbox.** Only the bare model id, sampling knobs, tools, and messages are forwarded.

Do not use this node for OpenAI cloud keys — use OpenAI Models.

| Helper method | Daemon path | When |
| --- | --- | --- |
| `llm().chatStream(body)` | `POST /v1/helper/llm/chat` with `stream:true` (SSE, 600s) | Default streaming path |
| `llm().completion(messages, model, prompt, env)` | `POST /v1/helper/llm/completion` (300s) | `stream=false`, or SSE transport failure |

The daemon then calls the configured provider’s chat-completions API. Official shape: [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat/create) (compatible engines). Ollama: [OpenAI compatibility](https://github.com/ollama/ollama/blob/main/docs/openai.md). vLLM: [OpenAI-compatible server](https://docs.vllm.ai/en/latest/serving/openai_compatible_server.html).

Handler: `LocalLlmNode`. Dispatch by `templateId` in `NodeLibraryApp`.

## Ports

| Port | Direction | Type | Required | Schema | How the node uses incoming data |
| --- | --- | --- | --- | --- | --- |
| `llm_input` | INPUT | `llm_input` | **yes** | Object `{messages?, prompt?, tools?, stream?, streaming?, options?, …}` or a UTF-8 string | `getInputValuesByPortValue("llm_input")` first, then `inputVariables.llm_input`. Bare string: JSON-parse if possible, else wrap `{messages:[{role:"user", content:<string>}]}`. One-element list of a map is unwrapped. Empty → `{error, type:"ValueError"}`. |
| `llm_output` | OUTPUT | `llm_output` | — | `{result: …}` | Always HTTP 200. Success = assistant payload. Failure = `{error, type}` with **no** `role`/`content`. Check `result.error`. |

### `llm_input` fields

| Field | Type | Required | Purpose |
| --- | --- | --- | --- |
| `messages` | list | one of `messages` / `prompt` | OpenAI-style `{role, content, tool_calls?, …}`. `role`: `system` \| `user` \| `assistant` \| `tool`. `content`: string or typed blocks (`text` / `image` / `audio` / `video` / `document` / `tool_use` / `tool_result`). |
| `prompt` | string / object | alternative to `messages` | Folded into the completion call. If the payload is not a map, the whole payload is treated as `prompt`. |
| `tools` | list | no | `{name, description, request}` forwarded on the **streaming** body. `[]` / omit → no `tools` field. |
| `stream` | boolean / string | no | Per-call canonical override (also `options.stream`). Wins over config. |
| `streaming` | boolean / string | no | Alias when `stream` is omitted/unparseable. |

Stream resolution (`LocalLlmConfig.resolveStream`): input `stream` → input `streaming` → config `stream` → **default `true`**. Unparseable values fall through. Default true means Java streaming is the default even if config `stream` is unset.

**Java vs definition:** the definition says extra top-level keys that are not config shortCodes are forwarded to `completion()`. The Java stream body is **only** coerced `PARAM_KEYS` plus `messages` / `prompt` / `tools` / `envId` / `stream`. Atomic `completion()` accepts **only** messages, model, prompt, env — sampling knobs and tools are **dropped** on that path.

### `llm_output.result`

Success (stream collector, Python `_make_response` shape):

`{role:"assistant", content:[{type:"text", text}], tool_calls?, thoughts?, thinking?, output_files?, finish_reason?, usage?}`

Atomic `completion()` extracts `choices[0].message` when present; otherwise returns the envelope as-is (may be a raw chat-completions object).

Failure:

```json
{"error": "No input received on llm_input", "type": "ValueError"}
```

`type` is the Java exception simple name (`ValueError` is used for empty input to match the Python node). Never contains daemon credentials or the `envId::` prefix.

## Configuration parameters

| shortCode | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `model` | select | **yes** | `""` | Designer picker: `envId::modelId` (from `GET /be/v1/llm/models/picker`) or a bare id | Split on first `::`: left = overlay `envId` for the daemon LLM allow-list; right = bare model id sent to the provider. No `::` → this engine (`envId=null`). Empty → completion without `model` (provider error packed as `{error, type}`). |
| `temperature` | number | no | omit | `0.0–2.0` | Sampling. Non-numeric omitted. |
| `top_p` | number | no | omit | `0.0–1.0` | Nucleus sampling. |
| `max_completion_tokens` | number | no | omit | Positive int | Preferred cap; wins over `max_tokens`. |
| `max_tokens` | number | no | omit | Positive int | Deprecated alias. |
| `frequency_penalty` | number | no | omit | `-2.0–2.0` | Engine-dependent (Ollama may ignore). |
| `presence_penalty` | number | no | omit | `-2.0–2.0` | Engine-dependent. |
| `stop` | string | no | omit | JSON array of strings, or a raw string | Stop sequences. Parsed as JSON when possible. |
| `n` | number | no | omit | Positive int | Completions count; handler still returns `choices[0]` / first stream choice. |
| `logprobs` | boolean | no | `false` | boolean or true\|1\|yes\|on / false\|0\|no\|off | Forwarded as a **real boolean** (string `"true"` 400s on LM Studio). Unparseable → omitted. |
| `top_logprobs` | number | no | omit | Non-negative int (typical 0–20) | With `logprobs=true`. |
| `reasoning_effort` | select | no | omit | `low` \| `medium` \| `high` | Hint for o-series / gpt-5-compatible local endpoints. Empty omitted. Unknown strings still forwarded on the stream body. |
| `verbosity` | string | no | omit | `low` \| `medium` \| `high` | Reasoning verbosity. |
| `response_format` | string | no | omit | JSON object, or `json` / `text` | Structured output. Invalid JSON that is not json/text is omitted. |
| `modalities` | string | no | omit | JSON array or comma list (`text`, `audio`) | Multimodal output hint. |
| `audio` | string | no | omit | JSON object e.g. `{"voice":"alloy","format":"wav"}` | Audio-out config. |
| `parallel_tool_calls` | boolean | no | `false` | boolean / true\|1\|yes\|on | Real boolean on the stream body. |
| `prompt_cache_key` | string | no | omit | Non-empty string | Prefix-cache bucket. Not a secret; avoid unique PHI. |
| `seed` | number | no | omit | Integer | Best-effort determinism. |
| `stream` | select | no | `true` | `true` \| `false` | Canonical. Input-port override wins. Invalid/blank → true. |

Booleans and numbers are typed before the daemon call. Instance `configurationParameters` win over helper shortCode lookups.

## How it works

### Dispatch

`AiNodeHandlers` registers `LocalLlmNode`. `handle` always returns `{llm_output:{result}}`. Failures are packed, never thrown.

### Streaming path

1. `LocalLlmConfig.load` splits `model`, coerces PARAM_KEYS, resolves `stream`.
2. `helper.llm().chatStream(cfg.streamBody(messages, prompt, tools))` — body always has `stream:true`, optional `envId`.
3. `LocalLlmStream.collect` iterates SSE frames (provider-verbatim chat chunks, same shape as OpenAI Models).
4. `emitPartial("thinking", …)` for `reasoning` / `reasoning_content` / `thinking` / `thoughts`; `emitPartial("answer", …)` for content. Tool-call fragments assembled by index. Audio deltas → `output_files`.
5. If `SseStream.transportFailed()`, collect returns `null` → **atomic fallback** `completion()`.
6. Missing `helper` / `llm()` also falls back to `completion()`, or `{error:"LLM helper channel is unavailable", type:"RuntimeError"}`.

`SseStream` is `curl --unix-socket` over `SQ_HELPER_SOCKET`. A down channel is an already-failed empty stream (`transportFailed()`), never a throw. Close the stream to destroy curl.

### Atomic `completion()` limitation

`LlmHelper.completion` POSTs only `messages`, `prompt`, `model`, `envId`. Temperature, tools, stop, penalties, etc. are **not** on that signature. Setting `stream=false` (or hitting transport fallback) therefore drops those extras — same gap as the Java helper vs Python `**params`. Prefer streaming when tools or sampling knobs matter.

### Retry / backoff

No HTTP retry loop. The only fallback is stream → `completion()` when SSE never delivered frames. Engine 4xx/5xx surface as `{error, type}` or as a provider envelope inside `result`. No 429 backoff here; the daemon/provider owns that.

### Error mapping

| Condition | `result` |
| --- | --- |
| Empty `llm_input` | `{error:"No input received on llm_input", type:"ValueError"}` |
| Any `handle` exception | `{error:<message>, type:<SimpleName>}` |
| Helper LLM unavailable | `{error:"LLM helper channel is unavailable", type:"RuntimeError"}` on the complete path |

Logs must not include prompts, deltas, `helperToken`, or credentials.

## Security & legal

- **Secrets:** none on this node. Provider API keys, TLS material, and base URL stay on the daemon (Engine Settings / OS secret manager). The sandbox never sees them.
- **Data residency:** prompts and attachments leave the **sandbox** over UDS to **this env’s daemon**, then to the configured local/BYO engine. They do **not** go to OpenAI unless the operator pointed Engine Settings at OpenAI. For GDPR, treat the daemon host + that engine as the processing location (customer controller).
- **Cross-env:** `envId::modelId` routes via the overlay only when this engine holds an LLM allow-list grant on the target. Reachability ≠ authorization; the daemon re-checks the grant.
- **Sandbox:** Podman; helper UDS only; no provider URL from this container. Egress policy for Local LLM is daemon-side, not this image.
- **PHI / logs:** do not log `llm_input` or SSE deltas. `prompt_cache_key` must be opaque. OpenAI-platform-only fields (`service_tier`, `safety_identifier`) are deliberately not exposed: compatible local servers do not define them.
- **Rate limits:** whatever the local engine enforces (Ollama queue, vLLM max concurrency). Bound `max_completion_tokens`. The helper `chatStream` timeout is 600s; `completion` is 300s.
- **ToS:** the operator’s engine license (Ollama, vLLM, weights EULA). If Engine Settings points at a cloud-compatible endpoint, that vendor’s ToS applies (e.g. [OpenAI Terms](https://openai.com/policies/terms-of-use/) only in that misconfiguration).
- **Liability:** customer owns the daemon host and the chosen engine. StudioQuack’s duty is env-scoped proxying so one compromised sandbox cannot read another env’s LLM credentials.

## Examples

### 1. Streamed chat (loopback model)

Instance `model` = `llama3.1` (no `::`). `llm_input`:

```json
{
  "messages": [
    {"role": "user", "content": "Summarize the last podman error in one sentence."}
  ],
  "stream": true
}
```

Handler sends `model=llama3.1` with no `envId`. Success `llm_output`:

```json
{
  "result": {
    "role": "assistant",
    "content": [{"type": "text", "text": "The container OOM-killed at the test step."}],
    "thoughts": [{"type": "thinking", "text": "Looking at the last stderr line."}],
    "thinking": [{"type": "thinking", "thinking": "Looking at the last stderr line."}],
    "finish_reason": "stop",
    "usage": {"prompt_tokens": 40, "completion_tokens": 12, "total_tokens": 52}
  }
}
```

### 2. Picker value + empty input

Instance `model` = `3f2a9c10-1111-2222-3333-444455556666::llama3.1` → `envId=<uuid>`, `model=llama3.1`.

Empty `llm_input`:

```json
{
  "result": {
    "error": "No input received on llm_input",
    "type": "ValueError"
  }
}
```
