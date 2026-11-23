# Jira

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/jira/api_spec.json`.

## Overview

Canonical Jira Cloud REST v3 node (also listed under Developer Tools — one node). JQL on issue_list. User create/delete are admin-only and may be restricted on Cloud. Attachments use type file.

- **Official API:** Jira Cloud REST v3
- **Docs:** https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/
- **Researched:** 2026-08-20
- **Auth:** `basic` bound to configuration `api_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/jira/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `issue_create` | POST | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue` | Official |
| `issue_get` | GET | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}` | Official |
| `issue_list` | GET | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/search/jql` | Official |
| `issue_update` | PUT | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}` | Official | Same purpose fields as create (`summary`, `project_key`, …) bodyMapped into `fields`; keep `fields` / `update` as escape hatches.
| `issue_delete` | DELETE | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}` | Official |
| `issue_changelog` | GET | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}/changelog` | Official |
| `issue_notify` | POST | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}/notify` | Official |
| `issue_transitions` | GET | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}/transitions` | Official |
| `comment_add` | POST | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}/comment` | Official |
| `comment_get` | GET | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}/comment/{id}` | Official |
| `comment_list` | GET | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}/comment` | Official |
| `comment_update` | PUT | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}/comment/{id}` | Official |
| `comment_delete` | DELETE | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}/comment/{id}` | Official |
| `attachment_add` | POST | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}/attachments` | Official. `bodyFormat` multipart; `X-Atlassian-Token: no-check`. `file` is type file — the platform file list `[{kind, mime_type, source, data\|url, name}]` (one multipart part per item). A single ContentBlock map and legacy `{dataBase64\|url, fileName, contentType}` are still accepted. |
| `attachment_get` | GET | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/attachment/{id}` | Official |
| `attachment_list` | GET | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/issue/{issueIdOrKey}` | Official |
| `attachment_delete` | DELETE | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/attachment/{id}` | Official |
| `user_get` | GET | `https://api.atlassian.com/ex/jira/{cloud_id}/rest/api/3/user` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields. issue_create/update take `summary`, `project_key`, `issuetype`, `description` (ADF), `assignee_account_id`, `priority`, `labels` (nested under `fields`); comments take ADF `body` plus `visibility_type`/`visibility_value`. | File payloads use type `file` |
| `output` | OUTPUT | `{ status, http_status, data }` or `{status,http_status,data}` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. **`api_token`** (secret) and **`email`** (Atlassian account email) stay in configuration only — never on INPUT. **`cloud_id`** (Jira Cloud site UUID) belongs in instance config for one-site workflows; INPUT `cloud_id` overrides per call for multi-site. If both are blank, operations fail with `cloud_id is required … (set on INPUT or in configurationParameters)` before HTTP.

**issue_create:** prefer INPUT `summary` (issue title). Tool/agent alias `subject` maps to `summary`. Top-level purpose fields (`project_key`, `issuetype`, …) merge into `fields` via `bodyMap` and win over duplicate keys inside `fields`. Auth: Basic with config `email` + `api_token`.

## Legal

- Terms: https://www.atlassian.com/legal/developer-terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx raise a BPMN error (AUTH_FAILED / RATE_LIMITED / HTTP_<status> / REQUEST_FAILED).
- issue_create takes `summary`, `project_key`, `issuetype`, ADF `description`, `assignee_account_id` (nested under `fields`). issue_update keeps the `fields` / `update` objects. Comments take ADF `body` plus `visibility_type` / `visibility_value`. `fieldsQuery` is sent as query `fields`.
