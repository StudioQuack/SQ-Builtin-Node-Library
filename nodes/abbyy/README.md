# ABBYY

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/abbyy/`
- **Official API:** https://support.abbyy.com/hc/en-us/articles/360017269420-API-reference
- **Auth secret:** `password`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded result bytes use type `file` on OUTPUT (Platform file ref-id (sq-file: / sq-ref:gridfs:). Never inline bytes or base64 as the stored type.).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `process_image` | POST | `/v2/processImage` |
| `process_document` | POST | `/v2/processDocument` |
| `get_task` | GET | `/v2/getTaskStatus` |
| `get_result` | GET | `/v2/getTaskStatus` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), auth secrets (password), `endpoint` (Cloud OCR host, default `cloud-eu.ocrsdk.com`), `timeout_seconds`.

## Legal

- Terms: https://www.abbyy.com/legal/
- Official-API-first. No scrape. No unofficial web wrapper.
- Commercial Cloud OCR. Partner/contract required. Public HTTP surface is Cloud OCR SDK API v2 JSON (`/v2/…`); v1 XML is legacy. Vantage is tenant-specific REST. Files go to ABBYY cloud unless on-prem Vantage. Re-verify if Cloud OCR SDK is sunset for the customer's contract.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
