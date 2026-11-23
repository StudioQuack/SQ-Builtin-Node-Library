# Adobe Acrobat Sign

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/adobe-acrobat-sign/`
- **Official API:** https://developer.adobe.com/acrobat-sign/docs/overview/developer_guide/apiusage
- **Auth secret:** `access_token`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded result bytes use type `file` on OUTPUT (Platform file ref-id (sq-file: / sq-ref:gridfs:). Never inline bytes or base64 as the stored type.).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `agreement_create` | POST | `/agreements` |
| `agreement_get` | GET | `/agreements/{agreementId}` |
| `agreement_cancel` | PUT | `/agreements/{agreementId}/state` |
| `agreement_documents` | GET | `/agreements/{agreementId}/combinedDocument` |
| `webhook_listen` | ingress | BPMN message event — does not open a socket |
| `group_get` | GET | `/groups/{groupId}` |
| `workflow_get` | GET | `/workflows/{workflowId}` |
| `webform_get` | GET | `/widgets/{widgetId}` |
| `megasign_get` | GET | `/megaSigns/{megaSignId}` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `access_token` (OAuth access token minted outside StudioQuack), `shard` (api.{shard}.adobesign.com, default na1), `timeout_seconds`.

## Legal

- Terms: https://www.adobe.com/legal/terms.html
- Official-API-first. No scrape. No unofficial web wrapper.
- SOAP is retired. Discover api_access_point via GET /base_uris. Enterprise provisioned; regional shards. Customer is the sender. Transient document upload then POST /agreements. webhook_listen is ingress (BPMN).
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
