# Smartsheet

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/smartsheet/api_spec.json`.

## Overview

Official Smartsheet API 2.0. Enterprise admins may block tokens. Developer agreement applies.

- **Official API:** Smartsheet API 2.0
- **Docs:** https://developers.smartsheet.com/api/smartsheet
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/smartsheet/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `row_create` | POST | `https://api.smartsheet.com/2.0/sheets/{sheetId}/rows` | Official |
| `row_update` | PUT | `https://api.smartsheet.com/2.0/sheets/{sheetId}/rows` | Official |
| `row_delete` | DELETE | `https://api.smartsheet.com/2.0/sheets/{sheetId}/rows` | Official |
| `sheet_create` | POST | `https://api.smartsheet.com/2.0/sheets` | Official |
| `sheet_copy` | POST | `https://api.smartsheet.com/2.0/sheets/{sheetId}/copy` | Official |
| `sheet_get` | GET | `https://api.smartsheet.com/2.0/sheets/{sheetId}` | Official |
| `folder_create` | POST | `https://api.smartsheet.com/2.0/folders` | Official |
| `folder_copy` | POST | `https://api.smartsheet.com/2.0/folders/{folderId}/copy` | Official |
| `workspace_copy` | POST | `https://api.smartsheet.com/2.0/workspaces/{workspaceId}/copy` | Official |
| `comment_create` | POST | `https://api.smartsheet.com/2.0/sheets/{sheetId}/rows/{rowId}/comments` | Official |
| `user_add` | POST | `https://api.smartsheet.com/2.0/users` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.| Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://www.smartsheet.com/legal/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
