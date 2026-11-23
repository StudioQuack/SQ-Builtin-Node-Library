# PDFMonkey

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/pdfmonkey/`
- **Official API:** https://pdfmonkey.io/docs/api/
- **Auth secret:** `api_key`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
document_download OUTPUT is type `file` (platform file ref-id; never inline bytes or base64).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `document_generate` | POST | `/documents` | Body nested as `document.{document_template_id,payload,status,meta}` |
| `document_get` | GET | `/documents/{id}` |
| `document_list` | GET | `/documents` |
| `document_download` | GET | `/documents/{id}/download` |
| `template_list` | GET | `/document_template_cards` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }`. Errors raise a BPMN error. |

FUNCTION ports: none.

## Configuration

`operation` (select), auth secrets (password), `timeout_seconds`.

## Legal

- Terms: https://www.pdfmonkey.io/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- Template-based HTML/PDF. Generated files stay on PDFMonkey until deleted. Official list-templates path is GET /document_template_cards. Download uses the signed `download_url` on the document/card (1 hour). document_download OUTPUT is type file.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
