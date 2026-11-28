# AI Agent

## Overview

Multimodal orchestrator (`templateId` `a72d6175-f0d5-48d7-94b4-cb50fa9acaf7`, env override `SQ_TEMPLATE_ID_AI_AGENT`). It merges `main_input`, calls a connected v2 LLM on FUNCTION `model`, fans out tools / data / sub-agents, and emits `{final_answer, output_files, voices}` on `main_output`. Live reasoning goes to FUNCTION `thoughts` and helper `emitPartial("thinking"|"answer", delta)`.

The node does **not** call a provider URL. All LLM and tool traffic uses the sandbox helper channel (Unix socket `SQ_HELPER_SOCKET` + per-request `helperToken`): `POST /v1/helper/execute`, `POST /v1/helper/execute/stream`, `POST /v1/helper/emit-partial`. The connected provider (OpenAI Models, Local LLM, or any node on the same `{messages, tools, stream}` contract) performs the actual inference.

Handler: `AiAgentNode` → `AgentLoop`. Dispatch: `NodeLibraryApp` routes the invoke JSON by `templateId`.

## Ports

| Port | Direction | Type | maxConnections | Schema | How the node uses it |
| --- | --- | --- | --- | --- | --- |
| `model` | FUNCTION | `model` | 1 | Request `{messages, tools, stream:true}`. Response: assistant payload (possibly wrapped). | **Required at runtime.** Zero connections fail with `Missing required LLM connection on \`model\` FUNCTION port.` The agent may call this port **N times per run** (first round + one call per tool-loop iteration). Prefer `executeStream`; fall back to atomic `execute`. |
| `memory` | FUNCTION | `memory` | 1 | Request `{operation, key, value?, start?, stop?}`. Response `{status, value}` or `{data:{value}}`. | Optional. Persists full execution history (including tool calls and tool outputs) across runs on a **generic cache** schema: `RPUSH` / `LRANGE 0 -1` / `LTRIM` / `DEL` at `sq:memory:<workflowId:nodeId:conversationId>` (falls back to a per-invoke UUID when no conversation id is present). On follow-up turns, reads existing memory and diffs against the incoming chat thread so only new messages are appended without duplicating prior turns. Wire Redis Cache (or any SET/GET/DEL/RPUSH/LRANGE/LTRIM node). Unconnected → in-process cache. Remote failures fall back to in-memory. |
| `data` | FUNCTION | `data` | unbounded (`0`) | Request `{operation:QUERY, query, embedding?, topK, filter}`. Response `matches` / `data.value`. | On-demand RAG search tools. Pre-RAG at turn start is **not** run. Every connected **generic vector-DB** is exposed to the model as a callable tool with `{query, topK?}` (with `DATA_SOURCE_RAG_RULES` injected into the system prompt) so the agent searches relevant keywords when needed before answering. The agent maps onto the peer schema (`vector.query` / `collection.query` / `QUERY`, `topK`/`n_results`/`limit`, `filter`/`where`). |
| `tools` | FUNCTION | `tools` | unbounded | Request `{request, name, description}` (DuckTape wrapper). Response `{response}` or a bare value. | DuckTape tools. Name + description live on the **connection edge**. The agent may invoke this port **N times per run** (every model `tool_calls` entry, parallel cap 8). Duplicate `(name, args)` in one run returns a cached-style `tool_result`. |
| `sub_agent` | FUNCTION | `sub_agent` | unbounded | Request `{role?, instructions?, prompt, files?}`. Response `{final_answer, output_files?}`. | Synthesises tool `delegate_to_agent`. Sub-agent instance config wins over master overrides. `files` are forwarded. **No** `conversation_id` passthrough. |
| `thoughts` | FUNCTION | `thoughts` | (observability) | Payload `{thoughts}` — a single string, the human-readable reasoning text. | Live side-channel. One `execute("thoughts", {thoughts})` **per** thinking block, **per** tool-dispatch notice, **per** summary — not a dump at the end — plus live deltas via `emitPartial(port='thoughts', channel='thinking')`. Unconnected → drop. Failures never fail the agent. |
| `main_input` | INPUT | `main_input` | unbounded | `{role?, instructions?, prompt?, files?}`. A bare string is a prompt. | Merged across connections (see below). Files become multimodal content blocks on the latest user message. |
| `main_output` | OUTPUT | `main_output` | — | `{final_answer, output_files, voices}` | Final text + generated media. `voices` is the audio subset of `output_files` (still kept there). Thoughts are not here. Uncaught exceptions still emit this port (`final_answer` = `Agent v2 error: <Type>: <message>`, `voices` = `[]`). |

### FUNCTION `model` (LLM)

Wire request (every call):

```json
{
  "messages": [{"role": "system", "content": "…"}, {"role": "user", "content": "…"}],
  "tools": [{"name": "lookup_order", "description": "…", "request": {"type": "object", "properties": {}}}],
  "stream": true
}
```

`stream` is always `true` on the stream path; the atomic fallback omits it. The agent does not honor a caller override.

Response unwrap (recursive, depth ≤ 10): peel one-element lists; open dict keys in order `result`, `response`, `llm_output`, `output`, `data`, `value`; accept a dict with `role=="assistant"` or keys `tool_calls` | `thinking` | `thoughts` | `output_files` | `finish_reason` | `error`. Canonical payload:

`{role, content, tool_calls?, thoughts?, thinking?, output_files?, finish_reason?, usage?, error?}`

Empty transport (`[]`, `null`, `""`, `[{}]`) is a **terminal** provider-return-path error. The agent does not retry — the provider already ran (image generation may already have billed).

### FUNCTION `tools` / `data` catalog

A connection appears in the tool catalog only when DuckTape supplies a non-empty **name**, **description**, and at least one mapped field (`has_config`). Resolver for a model-emitted name: `exact` → `alias_exact` → `alias_contains` → `alias_tokens`.

JSON Schema `request.properties` come from mappings whose `sourcePath` is `request` (`targetPath` = the target node's input field). Types are normalised (`list`→`array`, `dict`→`object`, …).

`data` tools are **never** invoked automatically at turn start (pre-RAG is removed). Every connected `data` node is exposed to the model as a callable RAG search tool (`{query, topK?}`) and `DATA_SOURCE_RAG_RULES` instruct the model to search relevant keywords when needed before answering. When called, `AgentPortMapping` projects `{operation:"QUERY", query, topK, filter}` (plus optional `embedding`) onto the peer INPUT schema and returns the retrieved matches as a `tool` message.

### FUNCTION `sub_agent`

When any sub-agent is connected, the catalog includes synthetic `delegate_to_agent` with `agent_id.enum` = connected node ids. Ambiguous/missing `agent_id` returns a `tool_result` error listing available ids. Sub-agent `role` / `instructions` / `prompt` on **its** `configurationParameters` win. Prior master context must be embedded in `prompt` (or `files`).

### INPUT `main_input` merge

| Field | Merge rule |
| --- | --- |
| `prompt` | **Always** concatenates config `prompt` + every connection (newline-joined). Bare string input = this field. |
| `role` | Config wins when non-blank; else joined connection roles; else `You are a helpful AI assistant.` |
| `instructions` | Config wins when non-blank; else joined connection instructions; else `""`. |
| `files` | Concatenated across connections. Each item `{kind: image\|audio\|video\|document, mime_type, source: base64\|url\|file_id, data, name?}`. |

Empty prompt + empty files still runs: user content becomes `"Proceed with your task."`.

`STRICT_TOOL_CALL_RULES` are appended to the system message **only when instructions are non-blank** (and not identical to the user prompt).

### OUTPUT `main_output`

| Field | Type | Meaning |
| --- | --- | --- |
| `final_answer` | string | Text extracted from the last assistant `content` blocks. On handler failure: the exception string. |
| `output_files` | list | Last payload's `output_files` (image/audio/video/document blocks), or `[]`. Audio blocks stay here. |
| `voices` | list | Audio subset of `output_files` (`kind`/`type` = `audio` or `mime_type` starts with `audio/*`, case-insensitive). Same block objects; empty list when none. Always present. |

`conversation_id` is intentionally absent.

### FUNCTION `thoughts` payload

The port publishes exactly one string parameter, `thoughts` — the reasoning text itself, with no event envelope. Emitted once per thinking block (provider `thoughts` / `thinking` blocks after a round), once per tool-dispatch notice (human-readable, e.g. `Calling tools: lookup_order`), and once per context-window compaction. Live deltas for the same text ride `emitPartial(port='thoughts', channel='thinking')` so wired targets render token-by-token.

## Configuration parameters

| shortCode | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `role` | string | no | `Agent Maygent` | Free text | System-message name/role. Wins over port `role` when non-blank after strip. Empty → port merge, else `You are a helpful AI assistant.` |
| `instructions` | longtext | no | `""` | Free text / markdown | System instructions. Wins over port `instructions` when non-blank. Handler appends `STRICT_TOOL_CALL_RULES` only when this (or the merged port value) is non-blank. |
| `prompt` | longtext | no | `""` | Free text | Static prefix concatenated **in front of** every port prompt. Not a substitute for `instructions`. |
| `maxIterations` | number | no | `10` | Integer. Blank / non-numeric / **≤ 0 → 10** | Tool-loop rounds **after** the first LLM call. Designer copy that says `0` skips tool dispatch is **not** what Java does: `AgentConfig.parsePositiveInt` and `AgentLoop` both treat ≤ 0 as 10. |
| `contextWindow` | number | no | `200000` | Integer. Blank / non-numeric → `200000`. **`0` disables summarization** | Local token budget (`chars/4`). Over budget → compact history into `Summarized((…))` and emit a `summary` thoughts event. Provider overflow can trigger a force-summarize + one extra LLM call. |

Instance-editable. No secrets on this node — API keys live on the connected provider.

## How it works

### Dispatch

`NodeLibraryApp.route` looks up `request.templateId` in `NodeRegistry`. `AiNodeHandlers` registers `AiAgentNode` under catalog `ai-agent` / env `SQ_TEMPLATE_ID_AI_AGENT` / fallback `a72d6175-f0d5-48d7-94b4-cb50fa9acaf7`. Reply keys match OUTPUT ports (`main_output`). HTTP 200 always; the dispatcher never sees a thrown exception.

### Per-invoke session

`AgentConfig.setup` allocates `AgentSession`: `llmNodeId` from the `model` edge, optional `memoryId` / `thoughtsNodeId`, and a deterministic `conversationId` scoped from `workflowId:nodeId:conversationId` when the execution context carries a conversation id (falling back to a fresh UUID for non-conversational runs). Concurrent dispatches do not share mutable state. `AgentMemory.release` drops only the in-process JVM fallback bucket in `finally` — remote memory stays persisted across runs.

### Helper channel (UDS)

`StudioQuackHelper` POSTs JSON over `curl --unix-socket $SQ_HELPER_SOCKET` with `Authorization: Bearer <helperToken>`. Missing socket/token is a silent no-op / empty result — never log the bearer.

| Call | Path | Timeout | Used for |
| --- | --- | --- | --- |
| `execute(port, payload, nodeId)` | `/v1/helper/execute` | 300s | tools, data, memory, thoughts, atomic LLM fallback |
| `executeStream("model", payload, llmNodeId)` | `/v1/helper/execute/stream` | 600s | streamed LLM (SSE `partial` / `complete` / `error` / `done`) |
| `emitPartial("thinking"\|"answer", delta)` | `/v1/helper/emit-partial` | 15s | live UI deltas; best-effort, never fails the loop |

### Agent loop (`AgentLoop.run`)

1. Merge config + `main_input`. Build tool catalog (`tools` + on-demand RAG `data` tools + optional `delegate_to_agent`). Pre-RAG is **not** run at turn start.
2. `prepareInitialHistory`: build core system (`role` + `instructions` + optional `STRICT_TOOL_CALL_RULES` + `DATA_SOURCE_RAG_RULES` when any `data` node is wired). Read existing history from `memory`, diff against incoming full chat (`threadMessages`), trim/clear on prompt-edit divergence, and persist only the new diff messages. Old stored `system` rows from memory are demoted to `user`.
3. Round 0: optional context compaction (persisted back to remote memory when connected) → `safeCallLlm` → persist assistant message.
4. If `tool_calls` is empty, stop and return that payload.
5. Else for `it = 1..maxIterations`: emit `tool_call` thoughts → dispatch tools (including any on-demand `data` RAG searches the model requested) in parallel (max 8) → persist tool results → another LLM round. Stop when the model returns no `tool_calls`.

Every `tool_use` gets a `tool_result` (Anthropic-strict), including errors.

### LLM stream then atomic fallback (`AgentLlm`)

1. Build `{messages, tools, stream:true}` and call `executeStream("model", …)`.
2. Fallback to atomic `execute("model", {messages, tools})` **only if the stream never started**: missing endpoint, `transportFailed()`, 404/503 before first `partial`/`complete`, null stream, or throw. Mid-stream errors are **not** retried (provider side effects may already have run).
3. SSE `partial` / `delta` / `thinking` frames fan `emitPartial("thinking"|"answer")`. Event `complete`/`result` is the assembled envelope. Unknown events are treated as partials if they carry deltas.
4. Unwrap to the canonical assistant payload.

Thinking text also republishes on FUNCTION `thoughts` after the round (`publishThinkingBlocks`).

### Memory

- Remote: `execute("memory", {operation:"RPUSH"|"LRANGE"|"LTRIM"|"DEL", key:"sq:memory:<scoped-conversation-id>", value?, start?, stop?})` after `AgentPortMapping` projects onto the peer cache schema. On subsequent turns of the same conversation, stored history is loaded and compared against the incoming chat transcript so only new messages are appended — preserving prior tool calls and tool outputs without duplicating chat turns.
- Local: `ConcurrentHashMap<conversationId, CopyOnWriteArrayList>` — released at end of invoke, so it does **not** persist across runs unless a memory node is wired.
- `add`/`get` failures log a warning (shape/ids only) and fall back to the local bucket.

### Retry / backoff

| Situation | Policy |
| --- | --- |
| Empty assistant payload (no text, tools, files) | **Never** re-called — one round = one LLM call; a slow long-thinking answer must not re-arm a second stream (it painted duplicate answers into the chat) |
| Provider context-overflow text | Force-summarize; retry LLM **once** if the estimate dropped |
| Empty model transport | Do **not** retry |
| Duplicate tool `(name, args)` in the same run | Skip invoke; `tool_result` `{info: "…cached result."}` |
| Tool 401/403/404/429/5xx | Not retried by the runtime; `STRICT_TOOL_CALL_RULES` instruct the **model** how to retry |

No HTTP backoff on this node (it never talks HTTP to a provider).

### Error mapping

| Failure | `main_output.final_answer` |
| --- | --- |
| Missing helper | `Agent v2 error: IllegalArgumentException: helper is required` |
| Missing `model` edge | `Agent v2 error: IllegalStateException: Missing required LLM connection on \`model\` FUNCTION port.` |
| Any other throw | `Agent v2 error: <SimpleName>: <message>` |
| Provider throw inside `safeCallLlm` | Assistant payload `{error, finish_reason:"error", content: "[Provider error] …"}` — loop may still terminate with that text |
| Empty model transport | `[Provider return-path error] …` (no retry) |

Logs: `ai-agent start/done/error` with `requestId`, tool counts, text lengths, `voices_n` — **never** prompt bodies, files, or `helperToken`.

## Security & legal

- **Secrets:** this node has no `api_key`. Provider credentials stay on the connected node / daemon. Helper bearer is request-scoped; never logged.
- **Data leaving the host:** `messages`, tools, and file bytes are sent through the helper to the connected LLM. If that node is OpenAI Models, prompts and attachments leave the customer host to OpenAI (third-country transfer — GDPR Art. 44+; customer is controller). If it is Local LLM, payloads stay on the daemon’s configured provider proxy.
- **Sandbox:** Podman job; no host paths; helper UDS only. Tool nodes run as their own sandboxes when the agent `execute`s them.
- **PHI / logs:** log shapes and sizes, not values. `emitPartial` deltas are live UI, not audit logs.
- **Rate limits:** inherit the connected provider’s limits. The agent can issue many `model`/`tools` calls per run (`maxIterations` × parallel tools). Bound `maxIterations` and tool fan-out in production graphs.
- **Liability:** customer owns endpoint data and the chosen provider ToS. StudioQuack’s boundary is sandbox + helper isolation (one env). A compromised daemon must not reach another owner’s LLM credentials.

## Examples

### 1. Text + tool loop

`main_input`:

```json
{
  "prompt": "Where is order 42? Use lookup_order if you have it."
}
```

Instance: `role` = `Warehouse copilot`, `instructions` = `Never invent bins.`, `maxIterations` = `10`. `model` wired to OpenAI Models; `tools` wired to a lookup node named `lookup_order`.

The agent calls `model` with `stream:true` and the `lookup_order` catalog. The model returns `tool_calls`; the agent `execute`s `tools` (possibly more than once), then calls `model` again. `thoughts` receives `{thoughts:"Calling tools: lookup_order"}` then thinking texts.

`main_output`:

```json
{
  "final_answer": "Order 42 is in bin A-12.",
  "output_files": [],
  "voices": []
}
```

### 2. Multimodal files + thoughts

`main_input`:

```json
{
  "prompt": "Read the label and say the bin.",
  "files": [
    {
      "kind": "image",
      "mime_type": "image/jpeg",
      "source": "base64",
      "data": "/9j/4AAQ…",
      "name": "label.jpg"
    }
  ]
}
```

User message `content` is a list of `{type:"text"}` + `{type:"image", …}` blocks. A live thoughts payload:

```json
{
  "thoughts": "I should look up order 42."
}
```
