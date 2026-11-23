# Microsoft Excel

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/microsoft-excel/api_spec.json`.

## Overview

Official Graph Excel (workbook) APIs on v1.0. One node for OneDrive and SharePoint workbooks; site_id/drive_id/item_id select the store. Admin consent for Files/Sites.ReadWrite.All.

- **Official API:** Microsoft Graph Excel API
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/excel?view=graph-rest-1.0
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/microsoft-excel/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `table_append` | POST | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/tables/{table_id}/rows/add` | Official |
| `table_get_columns` | GET | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/tables/{table_id}/columns` | Official |
| `table_get_rows` | GET | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/tables/{table_id}/rows` | Official |
| `table_lookup` | GET | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/tables/{table_id}/range` | Official |
| `workbook_add_sheet` | POST | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/worksheets` | Official |
| `workbook_list` | GET | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/worksheets` | Official |
| `workbook_delete` | DELETE | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}` | Official |
| `worksheet_list` | GET | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/worksheets` | Official |
| `worksheet_get` | GET | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/worksheets/{worksheet_id}` | Official |
| `worksheet_append` | POST | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/worksheets/{worksheet_id}/range(address='{address}')/insert` | Official |
| `worksheet_update` | PATCH | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/worksheets/{worksheet_id}/range(address='{address}')` | Official |
| `worksheet_clear` | POST | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/worksheets/{worksheet_id}/range(address='{address}')/clear` | Official |
| `worksheet_delete` | DELETE | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/worksheets/{worksheet_id}` | Official |
| `table_create` | POST | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/tables/add` | Official |
| `table_delete` | DELETE | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/workbook/tables/{table_id}` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://learn.microsoft.com/en-us/legal/microsoft-apis/terms-of-use
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
