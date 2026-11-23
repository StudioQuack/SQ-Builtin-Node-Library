# Azure Blob

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/azure-blob/`
- **Official API:** https://learn.microsoft.com/en-us/rest/api/storageservices/blob-service-rest-api
- **Auth secret:** `sas_token`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Host is `https://{account_name}.blob.core.windows.net`. Download bytes land on OUTPUT `file` (platform ref-id). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `blob_create` | PUT | `/{container}/{blob}` |
| `blob_delete` | DELETE | `/{container}/{blob}` |
| `blob_get` | GET | `/{container}/{blob}` |
| `blob_list` | GET | `/{container}` |
| `container_create` | PUT | `/{container}` |
| `container_delete` | DELETE | `/{container}` |
| `container_get` | GET | `/{container}` |
| `container_list` | GET | `/` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields (`container`, `blob`, query). No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `sas_token` (password), `account_name` (host), `timeout_seconds`.

## Legal

- Terms: https://www.microsoft.com/licensing/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- Default x-ms-version 2026-06-06. Block blobs for upload. OfficialApiNode does not compute SharedKey; prefer SAS on the request URL.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
