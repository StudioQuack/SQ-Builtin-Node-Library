# Markdown

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/markdown/`
- **Researched:** 2026-08-22
- **Official API:** CommonMark 0.31.2 + GitHub Flavored Markdown spec
- **Docs:** https://spec.commonmark.org/0.31.2/
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_MARKDOWN`)

## Scope

In-process Markdown ↔ HTML. GFM tables optional. Not a crawler.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `md_to_html` | in-process | CommonMark | MD → HTML |
| `html_to_md` | in-process | CommonMark | HTML → MD |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | md_to_html | no | Which capability to run. EXACT accepted values: md_to_html / html_to_md. Required. Example: 'md_to_html'. |
| `gfm` | select | no | true | no | Enable GFM extensions. Default true. |
| `tables` | select | no | true | no | Enable GFM tables. Default true. |
| `markdown` | string | no |  | no | Markdown source. INPUT wins. |
| `html` | string | no |  | no | HTML source. INPUT wins. |
| `timeout_seconds` | number | no | 25 | no | Connect+read / run budget in seconds. Positive integer. Blank/0/negative → 25. Keep at or below the sandbox request budget. Example: 25. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- CommonMark / GFM specs. No scrape.

## Implementation notes

- Official CommonMark parser. Raw HTML in Markdown is sanitized on md_to_html.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
