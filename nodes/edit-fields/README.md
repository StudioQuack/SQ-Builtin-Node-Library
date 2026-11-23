# Edit Fields

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/edit-fields/`
- **Researched:** 2026-08-20
- **Official API:** RFC 8259 JSON (in-process — no third-party network API)
- **Docs:** https://www.rfc-editor.org/rfc/rfc8259
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_EDIT_FIELDS`)

## Scope

Mutates the input JSON object: set listed fields, keep only listed fields, or emit JSON (`json_output`). Each `fields` entry may be a `{path,value}` map, a JSON-encoded string of that shape, or `path=value`. A bare path under `set` removes that leaf. Malformed entries fail with `INVALID_INPUT`. Dot-notation optional. No network.

File refs use type `file` (platform ref-id), never inline bytes.

## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `set` | in-process | RFC 8259 | Write listed paths |
| `keep_set` | in-process | RFC 8259 | Keep listed paths only |
| `json_output` | in-process | RFC 8259 | Emit JSON text |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | set | no | Verb |
| `fields` | string | no | | no | Default mappings |
| `include_binary` | select | no | false | no | Pass file refs |
| `dot_notation` | select | no | true | no | Dotted paths |
| `timeout_seconds` | number | no | 25 | no | Run budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party API. Operator is the data controller for values written into the graph.
- Do not copy secrets onto OUTPUT.

## Implementation notes

- Item-count cap. Dot-notation does not evaluate expressions.
- `include_binary` passes `file` refs through; never inline bytes.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
