# Google Docs

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/google-docs/api_spec.json`.

## Overview

Official Docs API v1 (docs.googleapis.com). Copy/export live on Drive API v3 — use the Google Drive node. Supply a minted OAuth access token (OfficialApiNode is bearer-only). Restricted user-data policy if public OAuth.

- **Official API:** Google Docs API v1
- **Docs:** https://developers.google.com/workspace/docs/api/reference/rest
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/google-docs/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `create` | POST | `https://docs.googleapis.com/v1/documents` | Official |
| `get` | GET | `https://docs.googleapis.com/v1/documents/{documentId}` | Official |
| `batch_update` | POST | `https://docs.googleapis.com/v1/documents/{documentId}:batchUpdate` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | create | no | Default official API call for this instance when input.operation is blank. EXACT accepted values: create, get, batch_update, listen. Default: create. Example: create. |
| `access_token` | password | yes |  | yes | Provider credential for auth.type=bearer (configKey access_token). REQUIRED secret — StudioQuack secret binding only; never put this on INPUT. Never logged. Example: 'redacted-t... |
| `title` | string | no |  | no | Request field for operations that declare `title` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `suggestionsViewMode` | string | no |  | no | documents.get suggestionsViewMode. EXACT: DEFAULT_FOR_CURRENT_ACCESS / SUGGESTIONS_INLINE / PREVIEW_SUGGESTIONS_ACCEPTED / PREVIEW_WITHOUT_SUGGESTIONS. |
| `documentId` | string | no |  | no | Request field for operations that declare `documentId` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `includeTabsContent` | boolean | no |  | no | documents.get includeTabsContent. get. true returns all tab contents. |
| `requests` | string | no |  | no | Request field for operations that declare `requests` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `timeout_seconds` | number | no | 25 | no | Connect+read timeout for the official HTTPS call. Positive integer. Blank/0/negative → 25. Example: 25. |

## Legal

- Terms: https://developers.google.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
