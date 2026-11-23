# Summarize

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/summarize/`
- **Researched:** 2026-08-20
- **Official API:** IEEE 754 + RFC 8259 (in-process — no third-party network API)
- **Docs:** https://www.rfc-editor.org/rfc/rfc8259
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_SUMMARIZE`)

## Scope

Computes aggregations (sum, count, avg, min, max, concat) on `items` for `fields`.
Each `items` element may be a JSON object, a JSON array (positional columns), or a string
row: CSV (`a,b` header then `2,2`), `field=value`, `field:value`, tab/newline-separated
values, or a scalar when exactly one field is configured.
Without `fields`, only whole-batch `count` is supported.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `summarize` | in-process | IEEE 754 | Stats over fields |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | summarize | no | Verb |
| `fields` | string | no | | no | Columns |
| `aggregations` | string | no | ["count"] | no | Fns |
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

- Division by zero → error object. Concat size-capped.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
