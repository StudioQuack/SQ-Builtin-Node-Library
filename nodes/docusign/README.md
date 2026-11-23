# DocuSign

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/docusign/`
- **Official API:** https://developers.docusign.com/docs/esign-rest-api/
- **Auth secret:** `access_token`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
`document_download` OUTPUT may be type `file` (platform file ref-id). Paths use `/accounts/{account_id}/…`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `envelope_create_from_template` | POST | `/accounts/{account_id}/envelopes` |
| `envelope_send` | PUT | `/accounts/{account_id}/envelopes/{envelopeId}` |
| `envelope_get` | GET | `/accounts/{account_id}/envelopes/{envelopeId}` |
| `envelope_void` | PUT | `/accounts/{account_id}/envelopes/{envelopeId}` |
| `document_download` | GET | `/accounts/{account_id}/envelopes/{envelopeId}/documents/{documentId}` |
| `recipient_list` | GET | `/accounts/{account_id}/envelopes/{envelopeId}/recipients` |
| `bulk_send` | POST | `/accounts/{account_id}/bulk_send_lists` |
| `listen` | ingress | BPMN message event — does not open a socket |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields (`account_id`, envelope ids, …). No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `access_token` (password; minted outside StudioQuack), `account_id` (REST path fallback), `timeout_seconds`.

## Legal

- Terms: https://www.docusign.com/legal/terms-and-conditions
- Official-API-first. No scrape. No unofficial web wrapper.
- Customer is the e-sign sender (ESIGN/eIDAS). Demo vs prod hosts. Access token minted outside StudioQuack. document_download OUTPUT is type file.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
