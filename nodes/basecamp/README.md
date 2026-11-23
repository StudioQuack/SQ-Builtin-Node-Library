# Basecamp

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/basecamp/api_spec.json`.

## Overview

Official Basecamp 3/4 API (not BC2). extraHeaders send required `User-Agent: {user_agent}` (app name + contact email or URL). Missing User-Agent → HTTP 400 (https://github.com/basecamp/bc3-api).

- **Official API:** Basecamp 3 API
- **Docs:** https://github.com/basecamp/bc3-api
- **Researched:** 2026-08-22
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/basecamp/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `project_list` | GET | `https://3.basecampapi.com/{account_id}/projects.json` | Official |
| `project_get` | GET | `https://3.basecampapi.com/{account_id}/projects/{project_id}.json` | Official |
| `todo_create` | POST | `https://3.basecampapi.com/{account_id}/buckets/{project_id}/todolists/{todolist_id}/todos.json` | Official |
| `todo_get` | GET | `https://3.basecampapi.com/{account_id}/buckets/{project_id}/todos/{todo_id}.json` | Official |
| `todo_complete` | POST | `https://3.basecampapi.com/{account_id}/buckets/{project_id}/todos/{todo_id}/completion.json` | Official |
| `todo_list_create` | POST | `https://3.basecampapi.com/{account_id}/buckets/{project_id}/todolists.json` | Official |
| `message_create` | POST | `https://3.basecampapi.com/{account_id}/buckets/{project_id}/messages.json` | Official |
| `document_create` | POST | `https://3.basecampapi.com/{account_id}/buckets/{project_id}/documents.json` | Official |
| `comment_create` | POST | `https://3.basecampapi.com/{account_id}/buckets/{project_id}/recordings/{recording_id}/comments.json` | Official |
| `person_list` | GET | `https://3.basecampapi.com/{account_id}/people.json` | Official |
| `campfire_line` | POST | `https://3.basecampapi.com/{account_id}/chats/{chat_id}/lines.json` | Official |
| `schedule_entry` | POST | `https://3.basecampapi.com/{account_id}/buckets/{project_id}/schedules/{schedule_id}/entries.json` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |


## Canonical flat paths (non-bucket-scoped)

Flat routes are the canonical form for new integrations (https://github.com/basecamp/bc3-api). The project (bucket) is derived server-side. Packaged ops still include some legacy `/buckets/{project_id}/...` create-under-project collections; prefer flats when you have the resource id:

| Resource | Canonical flat | Legacy bucket-scoped |
|---|---|---|
| Get to-do | `GET /{account_id}/todos/{todo_id}.json` | `GET /{account_id}/buckets/{project_id}/todos/{todo_id}.json` |
| Complete to-do | `POST /{account_id}/todos/{todo_id}/completion.json` | `POST /{account_id}/buckets/{project_id}/todos/{todo_id}/completion.json` |
| Create to-do | `POST /{account_id}/todolists/{todolist_id}/todos.json` | `POST /{account_id}/buckets/{project_id}/todolists/{todolist_id}/todos.json` |
| Comment | `POST /{account_id}/recordings/{recording_id}/comments.json` | `POST /{account_id}/buckets/{project_id}/recordings/{recording_id}/comments.json` |
| Message | `POST /{account_id}/message_boards/{board_id}/messages.json` | `POST /{account_id}/buckets/{project_id}/messages.json` |

A few project-level collections (message types, webhooks index/create, tools) still require the project-scoped form.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://basecamp.com/about/policies
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx raise a BPMN error (AUTH_FAILED / RATE_LIMITED / HTTP_<status> / REQUEST_FAILED); secrets are redacted.
- extraHeaders are applied after auth. Values support `{configKey}` (input, then node config).
