# Asana

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/asana/api_spec.json`.

## Overview

Official Asana REST 1.0 (app.asana.com/api/1.0). PAT or OAuth access token as bearer. OAuth app review for production distribution.

- **Official API:** Asana API 1.0
- **Docs:** https://developers.asana.com/reference/rest-api-reference
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `personal_access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/asana/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `project_create` | POST | `https://app.asana.com/api/1.0/projects` | Official |
| `project_get` | GET | `https://app.asana.com/api/1.0/projects/{project_gid}` | Official |
| `project_list` | GET | `https://app.asana.com/api/1.0/projects` | Official |
| `project_update` | PUT | `https://app.asana.com/api/1.0/projects/{project_gid}` | Official |
| `project_delete` | DELETE | `https://app.asana.com/api/1.0/projects/{project_gid}` | Official |
| `task_create` | POST | `https://app.asana.com/api/1.0/tasks` | Official |
| `task_get` | GET | `https://app.asana.com/api/1.0/tasks/{task_gid}` | Official |
| `task_list` | GET | `https://app.asana.com/api/1.0/tasks` | Official |
| `task_search` | GET | `https://app.asana.com/api/1.0/workspaces/{workspace_gid}/tasks/search` | Official |
| `task_update` | PUT | `https://app.asana.com/api/1.0/tasks/{task_gid}` | Official |
| `task_delete` | DELETE | `https://app.asana.com/api/1.0/tasks/{task_gid}` | Official |
| `task_move` | POST | `https://app.asana.com/api/1.0/tasks/{task_gid}/addProject` | Official |
| `subtask_create` | POST | `https://app.asana.com/api/1.0/tasks/{task_gid}/subtasks` | Official |
| `subtask_list` | GET | `https://app.asana.com/api/1.0/tasks/{task_gid}/subtasks` | Official |
| `comment_add` | POST | `https://app.asana.com/api/1.0/tasks/{task_gid}/stories` | Official |
| `comment_remove` | DELETE | `https://app.asana.com/api/1.0/stories/{story_gid}` | Official |
| `tag_add` | POST | `https://app.asana.com/api/1.0/tasks/{task_gid}/addTag` | Official |
| `tag_remove` | POST | `https://app.asana.com/api/1.0/tasks/{task_gid}/removeTag` | Official |
| `project_add_task` | POST | `https://app.asana.com/api/1.0/projects/{project_gid}/addTask` | Official |
| `project_remove_task` | POST | `https://app.asana.com/api/1.0/projects/{project_gid}/removeTask` | Official |
| `user_get` | GET | `https://app.asana.com/api/1.0/users/{user_gid}` | Official |
| `user_list` | GET | `https://app.asana.com/api/1.0/users` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus path/query fields (`project_gid`, `task_gid`, `workspace`, `assignee`, …). Write ops send the official Asana `data` envelope (`name`, `notes`, `due_on`, `assignee`, `tag`, `text`, … inside `data`). Purpose fields bodyMap into `data`; keep `data` for custom/rare keys. | Secrets are not on this port. |
| `output` | OUTPUT | `{ status, http_status, data }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | project_create | no | Default official API call for this instance when input.operation is blank. EXACT accepted values: project_create, project_get, project_list, project_update, project_delete, task... |
| `personal_access_token` | password | yes |  | yes | Provider credential for auth.type=bearer (configKey personal_access_token). REQUIRED secret — StudioQuack secret binding only; never put this on INPUT. Never logged. Example: 'r... |
| `due_on` | string | no |  | no | Due date YYYY-MM-DD for project/task writes. Nested as data.due_on. Do not send with due_at on the same task. Example: 2026-12-01. |
| `notes` | string | no |  | no | Plain-text notes for project_create/update, task_create/update, subtask_create. Nested as data.notes. Example: Kickoff Friday. |
| `archived` | boolean | no |  | no | Archive flag on project_update. Nested as data.archived. Example: false. |
| `start_on` | string | no |  | no | Start date YYYY-MM-DD for project/task writes. Nested as data.start_on. Example: 2026-11-01. |
| `name` | string | no |  | no | Task or project name. Required by Asana on project_create / task_create / subtask_create unless supplied inside data. Nested as data.name. Example: Q4 launch. |
| `color` | string | no |  | no | Project color on project_create/update. EXACT: dark-pink / dark-green / dark-blue / dark-red / dark-teal / dark-brown / dark-orange / dark-purple / dark-warm-gray / light-pink /... |
| `privacy_setting` | string | no |  | no | Project privacy on project_create/update. EXACT: public_to_workspace / private_to_team / private. Nested as data.privacy_setting. Example: private_to_team. |
| `team` | string | no |  | no | Team GID. Query on project_list. Body data.team on project_create when the workspace is an organization. Example: 1234567890. |
| `html_notes` | string | no |  | no | HTML notes (Asana rich text) for project/task writes. Nested as data.html_notes. Example: <body>Kickoff</body>. |
| `public` | boolean | no |  | no | Legacy public flag on project_create/update. Prefer privacy_setting. Nested as data.public. Example: false. |
| `data` | object | no |  | no | Asana `data` envelope escape hatch for write ops. Purpose fields (name, notes, due_on, assignee, tag, text, …) merge into data via bodyMap and win on conflict. Custom/rare keys ... |
| `opt_fields` | string | no |  | no | Comma-separated Asana opt_fields for project_get/list and task_get. Example: name,due_on,assignee.name. |
| `project_gid` | string | no |  | no | Project GID path for project_get/update/delete and project_add_task/remove_task. Example: 1234567890. |
| `limit` | number | no |  | no | Page size 1–100 for project_list, task_list, task_search, user_list. Example: 50. |
| `offset` | string | no |  | no | Pagination offset token from the previous list response (not a number). Example: eyJ0eXAiOiJKV1QifQ. |
| `assignee` | string | no |  | no | User GID, email, or me. Query filter on task_list. Body data.assignee on task_create/update and subtask_create. Example: me. |
| `completed` | boolean | no |  | no | Task completed flag for task_create/update and subtask_create (data.completed). Also query filter for task_search (true/false). Example: false. |
| `parent` | string | no |  | no | Parent task GID on task_create / subtask_create. Nested as data.parent. Example: 1234567890. |
| `due_at` | string | no |  | no | Due date-time ISO-8601 UTC for task_create/update and subtask_create. Nested as data.due_at. Example: 2026-12-01T17:00:00.000Z. |
| `projects` | list | no |  | no | Project GID list on task_create (create-only). Nested as data.projects. Example: ["1234567890"]. |
| `task_gid` | string | no |  | no | Task GID path for task_get/update/delete/move, subtask_*, comment_add, tag_add/remove. Example: 1234567890. |
| `project` | string | no |  | no | Project GID. Query filter on task_list. Body data.project (required) on task_move (addProject). Example: 1234567890. |
| `text` | string | no |  | no | Search text for task_search (query). Comment body for comment_add (data.text). Example: kickoff notes. |
| `section` | string | no |  | no | Section GID on task_move and project_add_task. Nested as data.section. Example: 1234567890. |
| `insert_before` | string | no |  | no | Neighbor task GID on task_move / project_add_task. Nested as data.insert_before. Example: 1234567890. |
| `insert_after` | string | no |  | no | Neighbor task GID on task_move / project_add_task. Nested as data.insert_after. Example: 1234567890. |
| `html_text` | string | no |  | no | HTML comment body on comment_add. Nested as data.html_text. Use instead of text. Example: <body>Looks good</body>. |
| `story_gid` | string | no |  | no | Story/comment GID path for comment_remove. Example: 1234567890. |
| `tag` | string | no |  | no | Tag GID for tag_add / tag_remove. Nested as data.tag. Example: 1234567890. |
| `task` | string | no |  | no | Task GID on project_add_task (required) and project_remove_task (required). Nested as data.task. Example: 1234567890. |
| `user_gid` | string | no |  | no | User GID path for user_get. me is accepted. Example: me. |
| `timeout_seconds` | number | no | 25 | no | Connect+read timeout for the official HTTPS call. Positive integer. Blank/0/negative → 25. Example: 25. |
| `workspace` | string | no |  | no | Default Asana workspace GID when INPUT workspace is empty (project_list / task_list / user_list / project_create / task_create). Example: 1234567890. |
| `workspace_gid` | string | no |  | no | Default workspace GID for task_search when INPUT workspace_gid is empty. |

## Legal

- Terms: https://asana.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx raise a BPMN error (AUTH_FAILED / RATE_LIMITED / HTTP_<status> / REQUEST_FAILED); secrets are redacted.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
