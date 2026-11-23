# PDF.co

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/pdfco/`
- **Official API:** https://docs.pdf.co/api-reference
- **Auth secret:** `api_key`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Operations send `url`/`html` JSON bodies. OUTPUT `file` is a platform file
ref-id (`sq-file:` / `sq-ref:gridfs:`). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `pdf_merge` | POST | `/pdf/merge` |
| `pdf_split` | POST | `/pdf/split` |
| `pdf_convert` | POST | `/pdf/convert/to/jpg` |
| `pdf_add_text` | POST | `/pdf/edit/add` |
| `pdf_extract_text` | POST | `/pdf/convert/to/text` |
| `pdf_barcode` | POST | `/barcode/generate` |
| `html_to_pdf` | POST | `/pdf/convert/from/html` |
| `url_to_pdf` | POST | `/pdf/convert/from/url` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), auth secrets (password), `timeout_seconds`.

## Legal

- Terms: https://pdf.co/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- Files are uploaded to PDF.co — DPA if PHI. Do not store PDF.co outputDataFormat=base64 as the StudioQuack type.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
