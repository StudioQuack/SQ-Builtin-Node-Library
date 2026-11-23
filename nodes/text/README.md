# Text

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/text/`
- **Researched:** 2026-08-20
- **Official API:** Unicode string processing (in-process — no third-party network API)
- **Docs:** https://www.unicode.org/reports/tr15/
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_TEXT`)

## Scope

In-process string tools on provided text. Default operation `replace` supports text-only passthrough, `case`, split+join via `separator`, literal or auto-detected regex replace, and optional post-replace casing. Regex is time-bounded (ReDoS). strip_html sanitizes markup already in the payload.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `replace`…`strip_html` | in-process | Unicode | See ops |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | replace | no | Verb |
| `flags` | string | no | | no | Regex flags |
| `limit` | number | no | 0 | no | Cap |
| `timeout_seconds` | number | no | 25 | no | Budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No crawler. No third-party API.

## Implementation notes

- ReDoS timeout + input size cap.
- template does not eval code.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
