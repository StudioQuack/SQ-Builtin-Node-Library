# Aggregate

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/aggregate/`
- **Researched:** 2026-08-20
- **Official API:** RFC 8259 JSON (in-process — no third-party network API)
- **Docs:** https://www.rfc-editor.org/rfc/rfc8259
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_AGGREGATE`)

## Scope

Groups `items` by `field` (`fields`) or wraps the whole array (`all_items`). Optional rename, list merge, keep-null.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `fields` | in-process | RFC 8259 | Group by key |
| `all_items` | in-process | RFC 8259 | One bucket |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | fields | no | Which capability to run. EXACT accepted values: fields / all_items. Required. Example: 'fields'. |
| `field` | string | no |  | no | Group-by key for operation=fields. Example: 'status'. |
| `rename` | string | no |  | no | Optional output key name. |
| `merge_lists` | select | no | false | no | Concat array values in a group. Default false. |
| `keep_null` | select | no | false | no | Keep groups whose key is null. Default false. |
| `items` | list | no |  | no | JSON array to group. INPUT items wins when set. |
| `timeout_seconds` | number | no | 25 | no | Connect+read / run budget in seconds. Positive integer. Blank/0/negative → 25. Keep at or below the sandbox request budget. Example: 25. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party API.

## Implementation notes

- Cap group count. No eval.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
