# LangChain utilities

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/langchain-utilities/`
- **Researched:** 2026-08-20
- **Official API:** **none** (local transforms; no vendor HTTP)
- **Docs:** this spec (algorithm contract). Not a third-party REST surface.
- **Auth:** none — **no secrets**

## Scope

Local, in-process text transforms on the daemon: split long strings into overlapping chunks, parse structured / list output from model text, apply one JSON repair pass, label text with operator-supplied rules, and load **platform files** into `{text, metadata}` documents.

This is **not** a hosted orchestration product, **not** a second AI Agent, and **not** a Python library wrapper. Implement the semantics below in **JDK** (no Python runtime, no vendor SDK, no outbound HTTP). GitHub repository loading stays on the **GitHub** node. Document conversion of PDFs/Office stays on **Docling**. Vector upsert/query stays on the **databases** store node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `split_text` | *(local)* | — | Fixed-size character windows with overlap |
| `split_recursive` | *(local)* | — | Hierarchical split: paragraphs → lines → sentences → words → characters |
| `split_tokens` | *(local)* | — | Budget chunks by an approximate token count (character heuristic) |
| `parse_structured` | *(local)* | — | Extract a JSON object matching `schema` |
| `parse_list` | *(local)* | — | Extract a JSON array or a list of strings |
| `parse_autofix` | *(local)* | — | Parse JSON; on failure apply **one** documented repair pass |
| `classify_text` | *(local)* | — | Rule/keyword labels only (no model call) |
| `load_documents` | *(local)* | — | Read platform `file` refs into documents |

Dropped: any HTTP to a cloud “utilities” host; loading remote URLs (use HTTP Request); GitHub clone/blob fetch (GitHub node); embedding or chat; invoking other nodes (no FUNCTION ports).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `split_text` | no | Row above |
| `chunk_size` | number | no | `1000` | no | Max characters per chunk (`split_text`, `split_recursive`). Floor 1, cap `100_000`. |
| `overlap` | number | no | `200` | no | Character overlap. Must be `< chunk_size`. Floor 0. |
| `max_tokens` | number | no | `512` | no | Token budget for `split_tokens`. Floor 1, cap `100_000`. |
| `chars_per_token` | number | no | `4` | no | Approximate tokens = `ceil(utf16_length / chars_per_token)` (min 1). No bundled tokenizer tables. |
| `separators` | string | no | empty | no | `split_recursive`: JSON array of separator strings; empty → built-in hierarchy. |
| `schema` | string | `parse_structured` | empty | no | JSON Schema (draft 2020-12 subset: `type`, `properties`, `required`, `items`, `enum`). |
| `timeout_seconds` | number | no | `25` | no | CPU budget for this invoke (watchdog). Floor 1. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Files are `file` ref-ids. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include file bytes |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = split_text` | `split_recursive` | `split_tokens`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | Source. Trim; empty → `{ error }` no work. Cap **2_000_000** characters. |
| `chunk_size` | number | no | Overrides config (`split_text` / `split_recursive`). |
| `overlap` | number | no | Overrides config. |
| `max_tokens` | number | no | Overrides config (`split_tokens` only). |
| `metadata` | object | no | Copied onto each chunk’s `metadata` (plus `chunk_index`). |

**`split_text` algorithm:** walk `text` with window `chunk_size` and step `chunk_size - overlap`. Last window may be shorter. Do not split inside a UTF-16 surrogate pair; if a window would, move the start forward one code unit.

**`split_recursive` algorithm:** separator list default `["\n\n", "\n", ". ", "? ", "! ", " ", ""]`. Split on the first separator that yields more than one piece; recurse on pieces longer than `chunk_size`; merge adjacent pieces until adding the next would exceed `chunk_size`, keeping `overlap` characters from the previous chunk when emitting. If a single unsplittable run exceeds `chunk_size`, hard-cut with `split_text` semantics.

**`split_tokens` algorithm:** same recursive split, but the size test is `token_estimate(s) <= max_tokens` where `token_estimate = max(1, ceil(utf16Length / chars_per_token))`. Overlap is converted as `ceil(overlap_chars / chars_per_token)` tokens, implemented by keeping the last `overlap` **characters** of the previous chunk (same as character overlap; do not invent a second tokenizer).

### `operation = parse_structured`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | Model or user text that should contain JSON. Cap 1_000_000 chars. |
| `schema` | object/string | if config blank | JSON Schema. Config `schema` used if omitted. |

Extract the first `{...}` object: strip optional markdown fence (` ```json ` / ` ``` `); then parse with JDK `Json`. Validate required keys / types against schema (`type` `object`/`array`/`string`/`number`/`boolean`/`null`, `properties`, `required`, `items`, `enum` only). Extra properties allowed unless schema `additionalProperties: false`. Failure → `{ error }` no HTTP.

### `operation = parse_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | Source. Cap 1_000_000 chars. |

Order: (1) if fenced/plain JSON array parses, use it; (2) else lines matching `^\s*([-*•]|\d+[.)])\s+` → strip marker; (3) else split on commas if there is no newline. Result is always `items: string[]` (stringify non-strings from JSON). Empty list is success with `items: []`.

### `operation = parse_autofix`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | JSON-ish text. Cap 1_000_000 chars. |

Parse JSON. On failure, **once**: strip fences; remove trailing commas before `}`/`]`; if the result is a single-quoted object-looking string, replace `'` with `"` only when the text has no unescaped double quotes. Parse again. Still failing → `{ error }`. Do not loop, do not eval, do not call a model.

### `operation = classify_text`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | Text to label. Cap 100_000 chars. |
| `labels` | array | yes | Non-empty list of `{ "id": string, "keywords": string[] }`. `keywords` default `[id]`. Max 64 labels, 32 keywords each, keyword length ≤ 64. |

Score each label: case-insensitive count of keyword occurrences in `text` (literal substring, not regex). Winner = highest score; tie → first in list. Score 0 for all → `label` omitted, `matched: false`. This is **not** an LLM classifier; wire an Agent graph for that.

### `operation = load_documents`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `files` | file[] | yes | Platform file ref-ids. Max **20** files, **5_000_000** bytes each. |
| `split` | boolean | no | If true, run `split_recursive` on each document’s `text` using config chunk/overlap. |

Supported types by filename suffix / MIME: `.txt`, `.md`, `.csv`, `.html`/`.htm`, `.json` (stringified). HTML: extract visible text (JDK HTML strip of `script`/`style`, decode entities). CSV: one document per row after header, `text` = `header: value` lines, `metadata.row` = index. JSON object/array: one document, `text` = compact JSON. Unknown type → that file contributes `{ error }` in `documents[].error` and does not fail the whole invoke unless **all** files fail.

GitHub blobs, HTTP URLs, and host paths are rejected.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `chunks` | array | split ops | `{ text, metadata, token_estimate? }` |
| `chunk_count` | number | split ops | `len(chunks)` |
| `value` | object | `parse_structured`, `parse_autofix` | Parsed JSON |
| `items` | array | `parse_list` | Strings |
| `label` | string | `classify_text` and `matched` | Winning `id` |
| `matched` | boolean | `classify_text` | Whether any keyword scored > 0 |
| `scores` | object | `classify_text` | `id → count` |
| `documents` | array | `load_documents` | `{ text, metadata, source_file, error? }` |
| `error` | string | failure | Actionable; no file bytes |

## Legal

- **Not a third-party API.** No vendor Terms of Service apply to this node. Algorithms are described here for implementers (Apache-style description of local behavior only).
- Operator remains responsible for the **content** of `text` / files (copyright, personal data). Bytes never leave the host.
- No secrets. Do not add an API key field.

## Implementation notes

- Pure JDK. No outbound `HttpClient`. Watchdog = `timeout_seconds`.
- Cap total output JSON at **2_000_000** bytes; if chunks would exceed, return `{ error: "Output too large; lower chunk_size or input size." }`.
- Logs: operation, counts, sizes — never full `text` bodies.
- Handler never throws past dispatch.
