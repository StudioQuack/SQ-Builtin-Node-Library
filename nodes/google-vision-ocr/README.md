# Google Vision OCR

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/google-vision-ocr/`
- **Official API:** https://cloud.google.com/vision/docs/ocr
- **Auth secret:** `access_token`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Purpose fields: `content` → `requests[0].image.content`, `imageUri` → `requests[0].image.source.imageUri`, `type` → `requests[0].features[0].type` (`TEXT_DETECTION` / `DOCUMENT_TEXT_DETECTION` / `LABEL_DETECTION`). `requests` remains a one-request object escape hatch. OUTPUT may set `file` (platform ref-id). Never inline bytes as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `text_detection` | POST | `/images:annotate` |
| `document_text_detection` | POST | `/images:annotate` |
| `label_detection` | POST | `/images:annotate` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields (`operation`, `requests`). No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }`. Errors raise a BPMN error. |

FUNCTION ports: none.

## Configuration

`operation` (select), `access_token` (password; OAuth token minted outside StudioQuack), `timeout_seconds`.

## Legal

- Terms: https://cloud.google.com/terms/data-processing-terms
- Official-API-first. No scrape. No unofficial web wrapper.
- Images leave the daemon to Google. DPA/BAA if PHI. StudioQuack stored type is file; Vision's JSON content field is wire encoding only. Multi-page PDF/TIFF uses files:asyncBatchAnnotate + GCS — not a silent scrape.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.  Never throw past dispatch.
