# Sort

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/sort/`
- **Researched:** 2026-08-20
- **Official API:** RFC 8259 JSON (in-process — no third-party network API)
- **Docs:** https://www.rfc-editor.org/rfc/rfc8259
- **Auth:** none — secret binding: `(none)`
- **Handler:** `SortNode` (`TransformHandlers`, template `SQ_TEMPLATE_ID_SORT`)

## Scope

Sorts `items` by `keys` (simple), CSPRNG shuffle (random), or a sandbox compare expression (code). Not a Filter/IF node.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `sort` | in-process | RFC 8259 | Order items |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | sort | no | Verb |
| `keys` | string | no | | no | Sort keys |
| `type` | select | no | simple | no | simple/random/code |
| `timeout_seconds` | number | no | 25 | no | Run budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party API. `code` compare is entitlement-gated sandbox-only.

## Implementation notes

- **AI Agent tools:** Map DuckTape `request` → INPUT (`items`, optional `keys`). Nested `args`/`request` envelopes are flattened. If the agent sends `items` as a one-element list holding a JSON array **string**, it is parsed before sort.
- INPUT `items` must be a JSON array (or `[...]` string). Plain strings are rejected with `INVALID_INPUT`.
- Cap item count. random = SecureRandom shuffle. No host eval.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
