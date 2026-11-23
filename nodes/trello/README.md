# Trello

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/trello/api_spec.json`.

## Overview

Official Trello REST. Auth is query key+token (token is the secret; key is a config string on every op via queryFrom). Atlassian developer terms.

- **Official API:** Trello REST API
- **Docs:** https://developer.atlassian.com/cloud/trello/rest/
- **Researched:** 2026-08-20
- **Auth:** `query` bound to configuration `token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/trello/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `board_create` | POST | `https://api.trello.com/1/boards` | Official |
| `board_get` | GET | `https://api.trello.com/1/boards/{id}` | Official |
| `board_update` | PUT | `https://api.trello.com/1/boards/{id}` | Official |
| `board_delete` | DELETE | `https://api.trello.com/1/boards/{id}` | Official |
| `list_create` | POST | `https://api.trello.com/1/lists` | Official |
| `list_get` | GET | `https://api.trello.com/1/lists/{id}` | Official |
| `list_cards` | GET | `https://api.trello.com/1/lists/{id}/cards` | Official |
| `list_update` | PUT | `https://api.trello.com/1/lists/{id}` | Official |
| `list_archive` | PUT | `https://api.trello.com/1/lists/{id}/closed` | Official |
| `card_create` | POST | `https://api.trello.com/1/cards` | Official |
| `card_get` | GET | `https://api.trello.com/1/cards/{id}` | Official |
| `card_update` | PUT | `https://api.trello.com/1/cards/{id}` | Official |
| `card_delete` | DELETE | `https://api.trello.com/1/cards/{id}` | Official |
| `comment_create` | POST | `https://api.trello.com/1/cards/{id}/actions/comments` | Official |
| `comment_update` | PUT | `https://api.trello.com/1/cards/{id}/actions/{idAction}/comments` | Official |
| `comment_delete` | DELETE | `https://api.trello.com/1/cards/{id}/actions/{idAction}/comments` | Official |
| `checklist_create` | POST | `https://api.trello.com/1/cards/{id}/checklists` | Official |
| `checklist_get` | GET | `https://api.trello.com/1/checklists/{id}` | Official |
| `checklist_delete` | DELETE | `https://api.trello.com/1/checklists/{id}` | Official |
| `label_create` | POST | `https://api.trello.com/1/boards/{id}/labels` | Official |
| `label_add` | POST | `https://api.trello.com/1/cards/{id}/idLabels` | Official |
| `label_remove` | DELETE | `https://api.trello.com/1/cards/{id}/idLabels/{idLabel}` | Official |
| `attachment_add` | POST | `https://api.trello.com/1/cards/{id}/attachments` | Official |
| `attachment_list` | GET | `https://api.trello.com/1/cards/{id}/attachments` | Official |
| `attachment_delete` | DELETE | `https://api.trello.com/1/cards/{id}/attachments/{idAttachment}` | Official |
| `member_add` | PUT | `https://api.trello.com/1/boards/{id}/members` | Official (invite by email query) |
| `member_remove` | DELETE | `https://api.trello.com/1/boards/{id}/members/{idMember}` | Official |
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

- Terms: https://www.atlassian.com/legal/developer-terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- `member_add` uses **PUT** `/boards/{id}/members` (not POST). `label_add` requires `idLabel` (sent as query `value`). Local validation covers `board_create.name`, `label_add.idLabel`, `comment_create.text`, and `member_add.email`.

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
