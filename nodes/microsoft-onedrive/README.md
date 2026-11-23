# Microsoft OneDrive

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/microsoft-onedrive/api_spec.json`.

## Overview

Canonical OneDrive node (also listed under Files & Documents — one node). Official Graph driveItem. Chunked upload >4 MiB uses createUploadSession (upload session URL is returned in data).

- **Official API:** Microsoft Graph Files (OneDrive)
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/drive?view=graph-rest-1.0
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/microsoft-onedrive/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `copy` | POST | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/copy` | Official |
| `delete` | DELETE | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}` | Official |
| `download` | GET | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/content` | Official |
| `get` | GET | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}` | Official |
| `rename` | PATCH | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}` | Official |
| `search` | GET | `https://graph.microsoft.com/v1.0/me/drive/root/search(q='{q}')` | Official |
| `share` | POST | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/createLink` | Official |
| `upload` | PUT | `https://graph.microsoft.com/v1.0/me/drive/root:/{filename}:/content` | Official |
| `create_folder` | POST | `https://graph.microsoft.com/v1.0/me/drive/items/{parent_id}/children` | Official |
| `list_children` | GET | `https://graph.microsoft.com/v1.0/me/drive/items/{item_id}/children` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

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
