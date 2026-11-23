# Streak

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/streak/`
- **Researched:** 2026-08-20
- **Official API:** Streak REST **v1** (`https://api.streak.com/api/v1`) for pipelines/boxes/files; **v2** (`https://api.streak.com/api/v2`) for contacts, tasks, comments, webhook create. Auth docs 2025-06-09; webhooks events **2026-01-06**.
- **Docs:** https://streak.readme.io/docs/overview · https://streak.readme.io/docs/authentication · https://streak.readme.io/reference/create-a-box · https://streak.readme.io/reference/list-all-pipelines · https://streak.readme.io/reference/get-files-for-box · https://streak.readme.io/reference/create-a-contact · https://streak.readme.io/reference/create-a-task · https://streak.readme.io/reference/create-a-comment · https://streak.readme.io/reference/create-a-webhook · https://streak.readme.io/reference/webhooks
- **Auth:** API key via HTTP Basic (username = key, empty password) — secret binding: `api_key`

## Scope

One node for Streak CRM in Gmail. **Boxes = deals** (pipeline records). Pipelines, boxes, contacts, fields, tasks, comments, files, and webhooks are official REST. Email tracking and mail merge are **unavailable** via API (official exception). User-key auth has the **same privileges as the Gmail user** who created the key.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `pipeline.list` | GET | v1 `/pipelines` | All pipelines |
| `pipeline.get` | GET | v1 `/pipelines/{pipelineKey}` | One pipeline |
| `box.create` | POST | v1 `/pipelines/{pipelineKey}/boxes` | Create deal |
| `box.get` | GET | v1 `/boxes/{boxKey}` | Get deal |
| `box.list` | GET | v1 `/pipelines/{pipelineKey}/boxes` | List deals |
| `box.update` | POST | v1 `/boxes/{boxKey}` | Edit a box (https://streak.readme.io/reference/edit-a-box) |
| `box.delete` | DELETE | v1 `/boxes/{boxKey}` | Delete deal |
| `contact.create` | POST | v2 `/teams/{teamKey}/contacts/` | Team contact (`emailAddresses` and/or name) |
| `contact.get` | GET | v2 `/contacts/{contactKey}` | https://streak.readme.io/reference/read-a-contact-1 |
| `contact.update` | POST | v2 update contact | https://streak.readme.io/reference/update-a-contact-1 |
| `field.update` | POST | v1 box fields | https://streak.readme.io/reference/updating-multiple-fields-on-a-box |
| `task.create` | POST | v2 `/boxes/{boxKey}/tasks` | JSON `text`, optional `dueDate` |
| `comment.create` | POST | v2 `/boxes/{boxKey}/comments` | JSON `{ "message" }` |
| `file.get` | GET | v1 `/boxes/{key}/files` | Contents only if `fileType=GMAIL_API` |
| `trigger.listen` | POST | v2 `/webhooks?pipelineKey=` | Body `event` + `targetUrl` |

v1 base `https://api.streak.com/api/v1`. v2 base `https://api.streak.com/api/v2`. Same Basic auth.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `box.list` | no | |
| `api_key` | password | yes | empty | yes | Basic username. Rotate in Streak Integrations if leaked |
| `team_key` | string | no | empty | no | Required for `contact.*` (v2 team-scoped contacts) |
| `timeout_seconds` | number | no | `30` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | `file.get` download uses type `file` on OUTPUT |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo API key |

FUNCTION ports: none.

## Input fields (by operation)

### `pipeline.list`

No required fields.

### `pipeline.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pipeline_key` | string | yes | |

### `box.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pipeline_key` | string | yes | |
| `name` | string | yes | Box name |
| `stage_key` | string | no | |
| `assigned_to` | array | no | `[{ "email": "…" }]` JSON as official |

### `box.get` / `box.update` / `box.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `box_key` | string | yes | |
| `name` | string | update | |
| `stage_key` | string | update | |
| `notes` | string | update | PII |
| `fields` | object | update | Field key → value |

### `box.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pipeline_key` | string | yes | |
| `page` | number | no | 0-indexed |
| `limit` | number | no | |
| `stage_key` | string | no | Filter |
| `sort_by` | string | no | `creationTimestamp` \| `lastUpdatedTimestamp` |

### `contact.create` / `contact.get` / `contact.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `team_key` | string | create | Path `teamKey` (or config `team_key`) |
| `key` | string | get/update | Contact key |
| `email_addresses` | array | create | Official `emailAddresses` — at least one of name or emails |
| `given_name` | string | no | `givenName` |
| `family_name` | string | no | `familyName` |
| `get_if_existing` | boolean | no | Query `getIfExisting=true` — then **only** `emailAddresses` is allowed |

### `field.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `box_key` | string | yes | |
| `field_key` | string | yes | |
| `value` | any | yes | |

### `task.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `box_key` | string | yes | |
| `text` | string | yes | |
| `due_date` | number | no | Official `dueDate` epoch ms |
| `assigned_to` | array | no | `assignedToSharingEntries`: `[{ "email": "…" }]` |

### `comment.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `box_key` | string | yes | |
| `message` | string | yes | PII |

### `file.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `box_key` | string | yes | List files in box |
| `file_key` | string | no | If set, download that file (GMAIL_API only) |
| `download` | boolean | no | Default false (metadata only) |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pipeline_key` | string | one of pipeline/team | Query `pipelineKey` (pipeline webhook) |
| `team_key` | string | one of | Query `teamKey` (team webhook: contacts/orgs) |
| `event` | string | yes | Official: `BOX_CREATE`, `BOX_DELETE`, `BOX_EDIT`, `BOX_CHANGE_STAGE`, `BOX_CHANGE_PIPELINE`, `BOX_NEW_EMAIL_ADDRESS`, `STAGE_CREATE`, `COMMENT_CREATE`, `TASK_CREATE`, `TASK_COMPLETE`, `TASK_DUE`, `TASK_UPDATED`, `MEETING_CREATE`, `MEETING_UPDATE`, `CONTACT_CREATE`, `CONTACT_UPDATE`, `CONTACT_DELETE`, `ORG_CREATE`, `ORG_UPDATE`, `ORG_DELETE` |
| `target_url` | string | yes | Body `targetUrl` — StudioQuack ingress |
| `token` | password | no | Optional `X-Streak-Webhook-Token` shared secret |

Webhook POST gets the updated object(s) as an **array**. HTTP **410** deletes the webhook. Non-200 retries with backoff (official).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `pipeline` / `pipelines` | object/array | pipeline | Includes `pipelineKey`, stages, fields |
| `box` / `boxes` | object/array | box | `boxKey`, `stageKey`, `fields` |
| `has_next_page` | boolean | box.list | Official `hasNextPage` |
| `contact` | object | contact | |
| `task` | object | task | |
| `comment` | object | comment | |
| `files` | array | file.get list | Metadata; `fileType`, `size`, `mimeType` |
| `file` | file | file.get download | Platform ref-id; only `GMAIL_API` |
| `webhook` | object | listen | Created webhook ids |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | |

## Legal

- Streak API key = full user access to that Gmail/Streak account. Customer owns the mailbox.
- Gmail / Google API user data policy still applies to data that originated in Gmail (threads, attachments).
- Boxes, contacts, comments, files are **PII**. Never log API keys, emails, comment bodies, or file bytes.
- Webhook `target_url` must be the operator’s ingress, not an arbitrary open relay.

## Implementation notes

- JDK HTTP. Pipelines/boxes/files: `https://api.streak.com/api/v1`. Contacts/tasks/comments/webhook create: `https://api.streak.com/api/v2`. HTTPS only (HTTP ignored officially).
- Auth: `Authorization: Basic {base64(api_key + ":")}`. Empty key → `{ error }`.
- SNI TLS required (official). JVM 25 is fine.
- Timeout **30 s**. Download cap **20_000_000** bytes.
- Retry 429/5xx twice. 410 on webhook delivery is provider-side.
- `file.get`: if `fileType` is not `GMAIL_API`, return `{ error: "Streak only serves GMAIL_API file contents via API" }` without guessing Drive URLs.
- Logs: operation, keys (pipeline/box **ids only**), status — never the Basic header.
