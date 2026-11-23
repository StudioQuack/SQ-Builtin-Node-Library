# ClickUp

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/clickup/api_spec.json`.

## Overview

Official ClickUp API **v2** for every resource this node calls (tasks, lists, folders, comments, checklists, goals, tags, dependencies, time entries). ClickUp v3 exists for Docs, Chat, and a few workspace-scoped extras (https://developer.clickup.com/docs/Getting%20Started) — none of those replace the v2 paths packaged here, so v2 is kept. Personal tokens go in Authorization as the raw token (no Bearer prefix).

- **Official API:** ClickUp API v2
- **Docs:** https://developer.clickup.com/reference/gettasks
- **Researched:** 2026-08-22
- **Auth:** `header` bound to configuration `api_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/clickup/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `task_create` | POST | `https://api.clickup.com/api/v2/list/{list_id}/task` | Official |
| `task_get` | GET | `https://api.clickup.com/api/v2/task/{task_id}` | Official |
| `task_list` | GET | `https://api.clickup.com/api/v2/list/{list_id}/task` | Official |
| `task_update` | PUT | `https://api.clickup.com/api/v2/task/{task_id}` | Official |
| `task_delete` | DELETE | `https://api.clickup.com/api/v2/task/{task_id}` | Official |
| `task_members` | GET | `https://api.clickup.com/api/v2/task/{task_id}/member` | Official |
| `task_custom_field` | POST | `https://api.clickup.com/api/v2/task/{task_id}/field/{field_id}` | Official |
| `list_create` | POST | `https://api.clickup.com/api/v2/folder/{folder_id}/list` | Official |
| `list_get` | GET | `https://api.clickup.com/api/v2/list/{list_id}` | Official |
| `list_list` | GET | `https://api.clickup.com/api/v2/folder/{folder_id}/list` | Official |
| `list_update` | PUT | `https://api.clickup.com/api/v2/list/{list_id}` | Official |
| `list_delete` | DELETE | `https://api.clickup.com/api/v2/list/{list_id}` | Official |
| `folder_create` | POST | `https://api.clickup.com/api/v2/space/{space_id}/folder` | Official |
| `folder_get` | GET | `https://api.clickup.com/api/v2/folder/{folder_id}` | Official |
| `folder_update` | PUT | `https://api.clickup.com/api/v2/folder/{folder_id}` | Official |
| `folder_delete` | DELETE | `https://api.clickup.com/api/v2/folder/{folder_id}` | Official |
| `comment_create` | POST | `https://api.clickup.com/api/v2/task/{task_id}/comment` | Official |
| `comment_get` | GET | `https://api.clickup.com/api/v2/task/{task_id}/comment` | Official |
| `comment_update` | PUT | `https://api.clickup.com/api/v2/comment/{comment_id}` | Official |
| `comment_delete` | DELETE | `https://api.clickup.com/api/v2/comment/{comment_id}` | Official |
| `checklist_create` | POST | `https://api.clickup.com/api/v2/task/{task_id}/checklist` | Official |
| `checklist_delete` | DELETE | `https://api.clickup.com/api/v2/checklist/{checklist_id}` | Official |
| `checklist_item_create` | POST | `https://api.clickup.com/api/v2/checklist/{checklist_id}/checklist_item` | Official |
| `checklist_item_update` | PUT | `https://api.clickup.com/api/v2/checklist/{checklist_id}/checklist_item/{checklist_item_id}` | Official |
| `goal_create` | POST | `https://api.clickup.com/api/v2/team/{team_id}/goal` | Official |
| `goal_get` | GET | `https://api.clickup.com/api/v2/goal/{goal_id}` | Official |
| `goal_update` | PUT | `https://api.clickup.com/api/v2/goal/{goal_id}` | Official |
| `goal_delete` | DELETE | `https://api.clickup.com/api/v2/goal/{goal_id}` | Official |
| `key_result_create` | POST | `https://api.clickup.com/api/v2/goal/{goal_id}/key_result` | Official |
| `key_result_delete` | DELETE | `https://api.clickup.com/api/v2/key_result/{key_result_id}` | Official |
| `tag_add` | POST | `https://api.clickup.com/api/v2/task/{task_id}/tag/{tag_name}` | Official |
| `tag_remove` | DELETE | `https://api.clickup.com/api/v2/task/{task_id}/tag/{tag_name}` | Official |
| `dependency_create` | POST | `https://api.clickup.com/api/v2/task/{task_id}/dependency` | Official |
| `dependency_delete` | DELETE | `https://api.clickup.com/api/v2/task/{task_id}/dependency` | Official |
| `time_entry_create` | POST | `https://api.clickup.com/api/v2/team/{team_id}/time_entries` | Official |
| `time_entry_get` | GET | `https://api.clickup.com/api/v2/team/{team_id}/time_entries/{timer_id}` | Official |
| `time_entry_update` | PUT | `https://api.clickup.com/api/v2/team/{team_id}/time_entries/{timer_id}` | Official |
| `time_entry_delete` | DELETE | `https://api.clickup.com/api/v2/team/{team_id}/time_entries/{timer_id}` | Official |
| `time_start` | POST | `https://api.clickup.com/api/v2/team/{team_id}/time_entries/start` | Official |
| `time_stop` | POST | `https://api.clickup.com/api/v2/team/{team_id}/time_entries/stop` | Official |
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

- Terms: https://clickup.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- extraHeaders are applied after auth. Values support `{configKey}` (input, then node config).

If every call fails with `HTTP transport failed` (status −1, no ClickUp JSON body), the sandbox never reached `api.clickup.com` — fix daemon egress, DNS, and TLS; this is not a ClickUp 401/404.

### Operation-specific required path ids

| Field | Operations (api_spec `required`) |
|---|---|
| `team_id` | `goal_create`, `time_entry_create`, `time_entry_get`, `time_entry_update`, `time_entry_delete`, `time_start`, `time_stop` |
| `space_id` | `folder_create` |
| `task_id` | task, comment, checklist, tag, dependency ops that target a task |
| `list_id` | `task_create`, `task_list`, `list_get`, `list_update`, `list_delete` |
| `folder_id` | `list_create`, `list_list`, folder ops except `folder_create` |

Path ids may be set on INPUT or, for `team_id`, on configurationParameters (merged before HTTP). `listen` is not an HTTP op — use a BPMN message event.
