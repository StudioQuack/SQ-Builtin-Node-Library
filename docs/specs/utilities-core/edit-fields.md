# Edit Fields

- **Status:** planned
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder when built:** `nodes/edit-fields/`
- **Researched:** 2026-08-20
- **Official API:** RFC 8259 / STD 90 — The JavaScript Object Notation (JSON) Data Interchange Format — **in-process / no third-party network API**
- **Docs:** https://www.rfc-editor.org/rfc/rfc8259
- **Auth:** none — secret binding: n/a

## Scope

Rewrites a JSON object (RFC 8259) in the daemon node-library process: set or overlay fields, keep only listed fields, or emit a constructed JSON document. No HTTP, no vendor SDK. File payloads that pass through are platform `file` ref-ids (never inline bytes), gated by `includeBinary`. This is not a Filter / IF / Wait node; branching stays in BPMN.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `set` | — | in-process overlay | Write listed fields onto the incoming object; unlisted keys are kept |
| `keep_set` | — | in-process keep+set | Output contains **only** the listed fields (values set / copied); all other keys dropped |
| `json_output` | — | in-process construct | Replace the item with a constructed JSON object from `fields[]` (and optional `json` template) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `set` | no | Chooses the row above. Exact values: `set` \| `keep_set` \| `json_output`. Unknown → `{ error }` |
| `fields` | array | yes for all ops | `[]` | no | Field specs (see below). Empty `fields` on `set` is a no-op copy (still subject to `includeBinary`). Empty `fields` on `keep_set` / `json_output` → `{ error: "fields is required and must be non-empty" }` |
| `includeBinary` | boolean | no | `false` | no | When `true`, properties whose values are platform **file** refs are copied through as type `file`. When `false`, those keys are **omitted** from the output (never expanded to bytes) |
| `dotNotation` | boolean | no | `true` | no | When `true`, `name` / `from` use `.` paths (`user.email` → nested object). When `false`, the key is a literal including dots |
| `timeoutMs` | number | no | `5000` | no | Wall-clock budget for the whole handle. Floor 1, cap 30_000. Exceeded → `{ error }` |
| `maxItems` | number | no | `10000` | no | Max objects processed when input is an array / `items`. Floor 1, cap 50_000 |
| `maxJsonBytes` | number | no | `2000000` | no | Max UTF-8 bytes of the serialized input. Over cap → `{ error }` before mutation |
| `maxFields` | number | no | `200` | no | Max length of `fields[]`. Over cap → `{ error }` |
| `maxDepth` | number | no | `32` | no | Max object/array depth walked for dot paths. Deeper → `{ error }` |

### `fields[]` element

| Name | Type | Required | Default | Purpose |
|---|---|---|---|---|
| `name` | string | yes | — | Destination key or dotted path. Trimmed. Empty → skip that element with `{ error }` for the whole call (fail closed) |
| `value` | any (JSON) | no | — | Literal RFC 8259 value to write. Ignored when `from` is a non-empty string |
| `from` | string | no | empty | Source path on the **incoming** item (same `dotNotation` rules). Missing path → write JSON `null` unless `skipMissing=true` |
| `type` | select | no | inferred | `string` \| `number` \| `boolean` \| `json` \| `file`. `file` means a platform ref-id string; the output schema marks that property as type `file` |
| `skipMissing` | boolean | no | `false` | If `true` and `from` does not resolve, do not write `name` |

Type coercion (`type` set):

| `type` | Accepted `value` / resolved `from` | On mismatch |
|---|---|---|
| `string` | JSON string, or `String.valueOf` for number/boolean | object/array/`file` ref → `{ error }` |
| `number` | JSON number, or decimal string parsable as `BigDecimal` | non-numeric → `{ error }` |
| `boolean` | JSON boolean, or strings `true`/`false` (any case, trimmed) | else `{ error }` |
| `json` | any RFC 8259 value (object/array/scalar) | — |
| `file` | non-empty string ref-id (platform file handle). **Not** raw bytes, **not** a host path | missing/`includeBinary=false` → key omitted, no error |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — item, or `{ items: object[] }`, or a JSON array of objects | No secrets. File properties are type `file` (ref-id) |
| `output` | OUTPUT | object — `{ status, item }` or `{ status, items }` or `{ error }` | Failures never include file bytes or secrets |

FUNCTION ports: none.

## Input fields (by operation)

Shared envelope (all operations):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `item` | object | one of `item` / `items` / root object / root array | Single JSON object to rewrite. If the root **is** an object and has neither `item` nor `items`, the root itself is the item |
| `items` | array | no | Batch of objects. Length > `maxItems` → `{ error }` (do not silently truncate; use the Limit node to cut) |
| *(root array)* | array | no | Same as `items` |

Per-field writes always read config `fields[]`. Input must not carry secrets; secret refs in values are left as opaque strings (the daemon resolves secrets **before** this node, only for this node’s config — not for arbitrary JSON values on the edge).

### `operation = set`

Incoming keys not listed in `fields[]` remain. Listed `name`s are created or overwritten. Nested parents are created as objects when `dotNotation=true`. Intermediate path collisions (scalar where an object is required) → `{ error }`.

### `operation = keep_set`

Start from `{}`. Apply `fields[]` only. Unlisted incoming keys are dropped, including unlisted file refs even when `includeBinary=true`. To keep a file ref, list it with `from` and `type=file`.

### `operation = json_output`

Same as `keep_set` (output is exactly the constructed object). Optional input `json` (object) is a base document that `fields[]` then overlay; if omitted, base is `{}`. `json` must be an object; array/scalar → `{ error }`.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `json` | object | no | Base document for `json_output` only |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `item` | object | success, single-item input | Rewritten object. File-bearing keys are type `file` (ref-id strings) |
| `items` | array | success, batch input | One output object per input object, same order |
| `count` | number | success | Number of objects emitted |
| `error` | string | failure | Actionable; no stack, no bytes, no secret material |

## Legal

- IETF RFC 8259 (STD 90): https://www.rfc-editor.org/rfc/rfc8259 — IETF Trust Simplified BSD for code components; the node implements the format, it does not ship a third-party SaaS client.
- No provider ToS. No network, no scrape.
- Output may contain personal data that was already on the edge. Minimize in the workflow. Logs: counts and sizes only.
- File refs stay env-scoped platform handles. This node must not dereference them to host paths or bytes.

## Implementation notes

- Java in-process (Jackson or equivalent RFC 8259 parser). No `eval`, no `javax.script`.
- Timeouts: `timeoutMs` default 5 s. Size: `maxJsonBytes` 2_000_000; `maxItems` 10_000; `maxFields` 200; `maxDepth` 32.
- Dot paths: split on `.` when `dotNotation=true`; reject empty segments (`a..b`), reject `/` JSON Pointer mix-in (that is the JSON node). Array indexes in a path are decimal digits (`items.0.id`).
- `includeBinary=false` (default): strip keys whose runtime type is a file ref (detect via existing platform file-ref envelope, not by sniffing bytes).
- Error mapping: validation / timeout / depth / cap → `{ error }` on OUTPUT. Never throw past dispatch.
- Dispatch budget: CPU-only; must finish well under the 150 ms *overhead* budget for tiny objects; large batches are bounded by `timeoutMs` and caps, not by calling the control plane.
