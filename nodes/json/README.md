# JSON

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/json/`
- **Researched:** 2026-08-20
- **Official API:** RFC 8259, RFC 6902 JSON Patch, RFC 7386 JSON Merge Patch, jq language
- **Docs:** https://www.rfc-editor.org/rfc/rfc8259
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_JSON`)

## Scope

In-process JSON tools. `jq` is a jq-compatible evaluator, never `eval` of JavaScript.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `parse` | in-process | RFC 8259 | Text → value |
| `stringify` | in-process | RFC 8259 | Value → text |
| `jq` | in-process | jq | Query |
| `merge` | in-process | RFC 7386 | Merge |
| `patch` | in-process | RFC 6902 | Patch |
| `flatten`/`unflatten` | in-process | dotted keys | Shape |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | parse | no | Which capability to run. EXACT accepted values: parse / stringify / jq / merge / patch / flatten / unflatten. Required. Example: 'parse'. |
| `path` | string | no |  | no | jq expression or flatten prefix. Example: '.items[] / .id'. |
| `pretty` | select | no | false | no | Pretty-print stringify. Default false. |
| `value` | object | no |  | no | JSON value or JSON text. INPUT wins. |
| `expression` | string | no |  | no | jq program. INPUT wins. path is the fallback when this is empty. |
| `patch` | list | no |  | no | RFC 6902 operation array. INPUT wins. |
| `other` | string | no |  | no | Second object to merge into value. INPUT wins. |
| `timeout_seconds` | number | no | 25 | no | Connect+read / run budget in seconds. Positive integer. Blank/0/negative → 25. Keep at or below the sandbox request budget. Example: 25. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party API. jq programs can be expensive — time-bound.

## Implementation notes

- No JS eval. JSON Patch = RFC 6902. Merge = RFC 7386 unless documented as deep-merge.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
