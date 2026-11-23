# Azure Document Intelligence

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/azure-document-intelligence/`
- **Official API:** https://learn.microsoft.com/en-us/azure/ai-services/document-intelligence/
- **Auth secret:** `api_key`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Auth is `Ocp-Apim-Subscription-Key` from config `api_key`. Download/result bytes land on OUTPUT `file` (platform ref-id). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `prebuilt_invoice` | POST | `/documentModels/prebuilt-invoice:analyze` |
| `prebuilt_receipt` | POST | `/documentModels/prebuilt-receipt:analyze` |
| `prebuilt_id` | POST | `/documentModels/prebuilt-idDocument:analyze` |
| `prebuilt_layout` | POST | `/documentModels/prebuilt-layout:analyze` |
| `custom_analyze` | POST | `/documentModels/{modelId}:analyze` |
| `get_result` | GET | `/documentModels/{modelId}/analyzeResults/{resultId}` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields (`modelId`, `resultId`, `api-version`). No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `api_key` (password), `endpoint`, `timeout_seconds`.

## Legal

- Terms: https://www.microsoft.com/licensing/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- GA api-version 2024-11-30. Regional endpoint required. DPA/BAA via Azure. Wire may use base64Source; StudioQuack field type remains file. Poll Operation-Location for get_result.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
