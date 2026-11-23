# OneNote

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/onenote/api_spec.json`.

## Overview

Official Graph OneNote. Notes.ReadWrite. page_create body is HTML (presentation). file_download returns page content as file ref when the handler stores the body.

- **Official API:** Microsoft Graph OneNote
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/onenote?view=graph-rest-1.0
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/onenote/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `notebook_create` | POST | `https://graph.microsoft.com/v1.0/me/onenote/notebooks` | Official |
| `notebook_get` | GET | `https://graph.microsoft.com/v1.0/me/onenote/notebooks/{notebook_id}` | Official |
| `section_create` | POST | `https://graph.microsoft.com/v1.0/me/onenote/notebooks/{notebook_id}/sections` | Official |
| `section_get` | GET | `https://graph.microsoft.com/v1.0/me/onenote/sections/{section_id}` | Official |
| `page_create` | POST | `https://graph.microsoft.com/v1.0/me/onenote/sections/{section_id}/pages` | Raw `text/html` body (`html`); title lives in `<title>` |
| `page_get` | GET | `https://graph.microsoft.com/v1.0/me/onenote/pages/{page_id}` | Official |
| `page_copy` | POST | `https://graph.microsoft.com/v1.0/me/onenote/pages/{page_id}/copyToSection` | Official |
| `page_delete` | DELETE | `https://graph.microsoft.com/v1.0/me/onenote/pages/{page_id}` | Official |
| `page_content` | GET | `https://graph.microsoft.com/v1.0/me/onenote/pages/{page_id}/content` | Official |
| `file_download` | GET | `https://graph.microsoft.com/v1.0/me/onenote/pages/{page_id}/content` | Official |
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
