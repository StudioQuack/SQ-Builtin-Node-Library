# Microsoft SharePoint

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/microsoft-sharepoint/api_spec.json`.

## Overview

Microsoft Graph SharePoint (sites, lists, drive files). Prefer Graph `Sites.Selected` over tenant-wide `Sites.ReadWrite.All`.

- **Official API:** Microsoft Graph v1.0 SharePoint
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/sharepoint?view=graph-rest-1.0
- **List items:** https://learn.microsoft.com/en-us/graph/api/listitem-create?view=graph-rest-1.0
- **Auth:** bearer `access_token`. Never on INPUT.
- **Folder:** `nodes/microsoft-sharepoint/`

## Operations

| operation | HTTP | Path | Body |
|---|---|---|---|
| `file_download` | GET | `/sites/{site_id}/drive/items/{item_id}/content` | — |
| `file_upload` | PUT | `/sites/{site_id}/drive/root:/{filename}:/content` | raw `file` bytes |
| `file_update` | PATCH | `/sites/{site_id}/drive/items/{item_id}` | `name` |
| `item_create` | POST | `/sites/{site_id}/lists/{list_id}/items` | official `{fields}` column map (include `Title` inside `fields`) |
| `item_get` | GET | `/sites/{site_id}/lists/{list_id}/items/{item_id}` | query `$expand`, `$select` |
| `item_list` | GET | `/sites/{site_id}/lists/{list_id}/items` | query `$top`, `$skiptoken`, `$expand`, `$select`, `$filter` |
| `item_update` | PATCH | `/sites/{site_id}/lists/{list_id}/items/{item_id}` | official `{fields}` column map |
| `item_delete` | DELETE | `/sites/{site_id}/lists/{list_id}/items/{item_id}` | — |
| `list_get` | GET | `/sites/{site_id}/lists/{list_id}` | — |
| `list_list` | GET | `/sites/{site_id}/lists` | — |

`fields` is the official Graph `fieldValueSet` (internal column names, including `Title`).

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | `operation` plus path/query/body fields. `file` is type file. |
| `output` | OUTPUT | `{status, http_status, data}`. Failures raise a BPMN error. |

## Configuration

`operation`, `access_token` (password), `site_id`, `timeout_seconds`.
