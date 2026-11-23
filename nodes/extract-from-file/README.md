# Extract From File

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/extract-from-file/`
- **Researched:** 2026-08-20
- **Official API:** RFC 4180 CSV, RFC 5545 iCalendar, PDF text (ISO 32000 extract), OOXML via Apache POI
- **Docs:** https://www.rfc-editor.org/rfc/rfc4180
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_EXTRACT_FROM_FILE`)

## Scope

Parses an existing `file` ref. PDF extraction is plain text only (not a renderer, not a website scrape).

The `file` param and INPUT field are type `file`.

## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `from_csv`…`from_ics` | local | RFC 4180 / POI / PDF text | file → JSON |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | from_csv | no | Parser |
| `header_row` | select | no | true | no | Header |
| `timeout_seconds` | number | no | 25 | no | Budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No Office/PDF website scrape. Customer owns the file.
- PDF text may include personal data.

## Implementation notes

- Type file on the input `file` param.
- Size cap. XXE off if XML-in-file.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
