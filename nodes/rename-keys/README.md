# Rename Keys

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/rename-keys/`
- **Researched:** 2026-08-20
- **Official API:** RFC 8259 JSON (in-process — no third-party network API)
- **Docs:** https://www.rfc-editor.org/rfc/rfc8259
- **Auth:** none — secret binding: `(none)`
- **Handler:** `RenameKeysNode` (`TransformHandlers`)

## Scope

Renames object keys. `mappings` is a JSON array of `from->to` strings (agent tools) and/or `{from,to}` objects (studio config). Optional regex mode. ReDoS timeout on regex.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `rename` | in-process | RFC 8259 | Rename keys |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | rename | no | Verb |
| `mappings` | string | no | | no | from/to |
| `regex` | select | no | false | no | Regex from |
| `timeout_seconds` | number | no | 25 | no | Run budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party API.

## Implementation notes

- Regex time-budget. Do not evaluate replacement as code.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
