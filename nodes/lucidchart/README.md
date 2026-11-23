# Lucidchart

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/lucidchart/api_spec.json`.

## Overview

Official Lucid REST. `Lucid-Api-Version` is required; path prefix `/v1` is the documented workaround when custom headers cannot be set (https://developer.lucid.co/reference/headers). Partner program for marketplace. FedRAMP uses a different published base URL — override is not in OfficialApiNode; document the commercial host here.

- **Official API:** Lucid REST API
- **Docs:** https://developer.lucid.co/reference/headers
- **Researched:** 2026-08-22
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/lucidchart/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `document_create` | POST | `https://api.lucid.co/v1/documents` | Official |
| `document_get` | GET | `https://api.lucid.co/v1/documents/{documentId}` | Official |
| `document_search` | GET | `https://api.lucid.co/v1/documents/search` | Official |
| `document_trash` | POST | `https://api.lucid.co/v1/documents/{documentId}/trash` | Official |
| `folder_list` | GET | `https://api.lucid.co/v1/folders` | Official |
| `collaborator_add` | POST | `https://api.lucid.co/v1/documents/{documentId}/collaborators` | Official |
| `collaborator_remove` | DELETE | `https://api.lucid.co/v1/documents/{documentId}/collaborators/{collaboratorId}` | Official |
| `embed_get` | GET | `https://api.lucid.co/v1/documents/{documentId}/embed` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{status,http_status,data}` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://lucid.co/tos
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{status,http_status,data}` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
