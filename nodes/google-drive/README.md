# Google Drive

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/google-drive/api_spec.json`.

## Overview

Canonical Drive node (also listed under Files & Documents — one node). Official Drive API v3. Restricted scopes for broad Drive. Binary upload host is googleapis.com/upload — metadata create uses /files; download uses alt=media.

- **Official API:** Google Drive API v3
- **Docs:** https://developers.google.com/workspace/drive/api/reference/rest/v3
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/google-drive/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `copy` | POST | `https://www.googleapis.com/drive/v3/files/{fileId}/copy` | Official |
| `create_from_text` | POST | `https://www.googleapis.com/drive/v3/files` | Official |
| `upload` | POST | `https://www.googleapis.com/drive/v3/files` | Official |
| `download` | GET | `https://www.googleapis.com/drive/v3/files/{fileId}` | Official |
| `update` | PATCH | `https://www.googleapis.com/drive/v3/files/{fileId}` | Official |
| `delete` | DELETE | `https://www.googleapis.com/drive/v3/files/{fileId}` | Official |
| `move` | PATCH | `https://www.googleapis.com/drive/v3/files/{fileId}` | Official |
| `share` | POST | `https://www.googleapis.com/drive/v3/files/{fileId}/permissions` | Official |
| `search` | GET | `https://www.googleapis.com/drive/v3/files` | Official |
| `create_folder` | POST | `https://www.googleapis.com/drive/v3/files` | Official |
| `delete_folder` | DELETE | `https://www.googleapis.com/drive/v3/files/{fileId}` | Official |
| `share_folder` | POST | `https://www.googleapis.com/drive/v3/files/{fileId}/permissions` | Official |
| `create_shared_drive` | POST | `https://www.googleapis.com/drive/v3/drives` | Official |
| `get_shared_drive` | GET | `https://www.googleapis.com/drive/v3/drives/{driveId}` | Official |
| `list_shared_drives` | GET | `https://www.googleapis.com/drive/v3/drives` | Official |
| `update_shared_drive` | PATCH | `https://www.googleapis.com/drive/v3/drives/{driveId}` | Official |
| `delete_shared_drive` | DELETE | `https://www.googleapis.com/drive/v3/drives/{driveId}` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` | Errors raise a BPMN error; secrets are redacted |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | copy | no | Default official API call for this instance when input.operation is blank. EXACT accepted values: copy, create_from_text, upload, download, update, delete, move, share, search, ... |
| `access_token` | password | yes |  | yes | Provider credential for auth.type=bearer (configKey access_token). REQUIRED secret — StudioQuack secret binding only; never put this on INPUT. Never logged. Example: 'redacted-t... |
| `fileId` | string | no |  | no | Request field for operations that declare `fileId` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `description` | string | no |  | no | Request field for operations that declare `description` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `supportsAllDrives` | string | no |  | no | Request field for operations that declare `supportsAllDrives` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `name` | string | no |  | no | Request field for operations that declare `name` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `parents` | string | no |  | no | Request field for operations that declare `parents` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `mimeType` | string | no |  | no | Request field for operations that declare `mimeType` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `uploadType` | string | no |  | no | Request field for operations that declare `uploadType` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `file` | file | no |  | no | Platform file ref-id (never inline bytes) for operations that declare `file` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'file-ref-abc'. |
| `alt` | string | no |  | no | Request field for operations that declare `alt` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `addParents` | string | no |  | no | Request field for operations that declare `addParents` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `removeParents` | string | no |  | no | Request field for operations that declare `removeParents` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `emailMessage` | string | no |  | no | permissions.create emailMessage. share / share_folder. |
| `role` | string | no |  | no | Request field for operations that declare `role` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `type` | string | no |  | no | Request field for operations that declare `type` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `emailAddress` | string | no |  | no | Request field for operations that declare `emailAddress` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `sendNotificationEmail` | boolean | no |  | no | permissions.create sendNotificationEmail. share / share_folder. |
| `q` | string | no |  | no | Request field for operations that declare `q` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `corpora` | string | no |  | no | files.list corpora. EXACT: user / domain / drive / allDrives. search. |
| `pageSize` | string | no |  | no | Request field for operations that declare `pageSize` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `orderBy` | string | no |  | no | files.list orderBy (createdTime, folder, modifiedTime, name, …). search. Example: modifiedTime desc. |
| `includeItemsFromAllDrives` | string | no |  | no | Request field for operations that declare `includeItemsFromAllDrives` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `driveId` | string | no |  | no | Request field for operations that declare `driveId` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `fields` | string | no |  | no | files.list fields partial response. search. Example: files(id,name,mimeType). |
| `pageToken` | string | no |  | no | Request field for operations that declare `pageToken` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `requestId` | string | no |  | no | Request field for operations that declare `requestId` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `timeout_seconds` | number | no | 25 | no | Connect+read timeout for the official HTTPS call. Positive integer. Blank/0/negative → 25. Example: 25. |

## Legal

- Terms: https://developers.google.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx raise a BPMN error (`HTTP_<status>` / `AUTH_FAILED` / `RATE_LIMITED`).
- `upload` sends metadata JSON on `/drive/v3/files`. Drive `multipart/related` media+metadata is not expressible (engine multipart is form-data). Use `file` plus `uploadType` when the operator supplies a prebuilt flow; otherwise create metadata then upload in a later step.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
