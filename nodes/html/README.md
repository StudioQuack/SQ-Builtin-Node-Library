# HTML

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/html/`
- **Researched:** 2026-08-20
- **Official API:** WHATWG HTML + CSS Selectors Level 3
- **Docs:** https://html.spec.whatwg.org/
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_HTML`)

## Scope

Operates only on HTML the workflow already holds. CSS selectors do not fetch URLs. Template output must be treated as untrusted (XSS).

When source=file, `file` is type `file`.

## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `template` | in-process | WHATWG HTML | Render |
| `extract` | in-process | CSS Selectors | Query nodes |
| `to_table` | in-process | HTML tables | Rows |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | template | no | Which capability to run. EXACT accepted values: template / extract / to_table. Required. Example: 'template'. |
| `css_selector` | string | no |  | no | CSS selector for extract/to_table. Example: 'table tbody tr'. |
| `source` | select | no | json | no | json / file. file means the HTML is a file ref. |
| `template` | string | no |  | no | HTML template. {{name}} escapes, {{{name}}} is raw. INPUT wins. |
| `item` | object | no |  | no | JSON object bound into the template. INPUT wins. |
| `html` | string | no |  | no | HTML document when source is json. INPUT wins. |
| `file` | file | no |  | no | File ref when source is file. INPUT wins. |
| `timeout_seconds` | number | no | 25 | no | Connect+read / run budget in seconds. Positive integer. Blank/0/negative → 25. Keep at or below the sandbox request budget. Example: 25. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- Not a crawler. No third-party fetch.
- Do not template untrusted HTML into an admin UI without encoding.

## Implementation notes

- source=binary → type file.
- Encode output when used as HTML.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
