# DocRaptor

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/docraptor/`
- **Official API:** https://docraptor.com/documentation/api
- **Auth secret:** `api_key`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
`document_get` OUTPUT may be type `file` (platform file ref-id).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `html_to_pdf` | POST | `/docs` |
| `html_to_xls` | POST | `/docs` |
| `document_get` | GET | `/docs/{id_or_token}` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields (`document_content`, `test`, …). No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `api_key` (password), `timeout_seconds`.

## Legal

- Terms: https://docraptor.com/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- Prince XML engine; commercial. Test vs paid docs. Create with POST https://api.docraptor.com/docs. HTML is INPUT `document_content`; `test` is the official body flag. document_get OUTPUT is type file.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
