# Azure DevOps

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AZURE_DEVOPS`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Azure DevOps REST (api-version 7.2)](https://learn.microsoft.com/en-us/rest/api/azure/devops/) at `https://dev.azure.com`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `personal_access_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `workitem_create` | POST | `/{organization}/{project}/_apis/wit/workitems/${type}` | Whole-body JSON Patch array (`fields`), `application/json-patch+json` |
| `workitem_get` | GET | `/{organization}/{project}/_apis/wit/workitems/{id}` | Official HTTP |
| `workitem_update` | PATCH | `/{organization}/{project}/_apis/wit/workitems/{id}` | Whole-body JSON Patch array (`operations`) |
| `workitem_query` | POST | `/{organization}/{project}/_apis/wit/wiql` | Official HTTP |
| `workitem_comment` | POST | `/{organization}/{project}/_apis/wit/workItems/{id}/comments` | Official HTTP |
| `workitem_link` | PATCH | `/{organization}/{project}/_apis/wit/workitems/{id}` | Whole-body JSON Patch array (`operations`) |
| `pr_create` | POST | `/{organization}/{project}/_apis/git/repositories/{repositoryId}/pullrequests` | Official HTTP |
| `pr_get` | GET | `/{organization}/{project}/_apis/git/repositories/{repositoryId}/pullrequests/{pullRequestId}` | Official HTTP |
| `build_queue` | POST | `/{organization}/{project}/_apis/build/builds` | Official HTTP |
| `build_get` | GET | `/{organization}/{project}/_apis/build/builds/{buildId}` | Official HTTP |
| `release_get` | GET | `/{organization}/{project}/_apis/release/releases/{releaseId}` | per-op baseUrl https://vsrm.dev.azure.com |
| `git_push_listen` | ingress | — | BPMN message event (no socket) |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | workitem_create | no | Official API operation. EXACT: workitem_create, workitem_get, workitem_update, workitem_query, workitem_comment, workitem_link, pr_create, pr_get, build_queue, build_get, releas... |
| `personal_access_token` | password | yes |  | yes | Azure DevOps PAT. Sent as HTTP Basic with an empty username and the PAT as the password (:PAT). Never logged. |
| `organization` | string | yes |  | no | Azure DevOps organization. |
| `project` | string | yes |  | no | Project name or id. |
| `$expand` | string | no |  | no | Work item expand for workitem_get (and optional create). EXACT: None / Relations / Fields / Links / All. |
| `suppressNotifications` | boolean | no |  | no | Query suppressNotifications for workitem_create/update. Example: false. |
| `validateOnly` | boolean | no |  | no | Query validateOnly for workitem_create/update: validate without saving. Example: false. |
| `bypassRules` | boolean | no |  | no | Query bypassRules for workitem_create/update: skip work-item type rules. Example: false. |
| `fields` | list | no |  | no | JSON Patch array sent as the whole workitem_create body (Content-Type application/json-patch+json). Each item is {op, path, value}. System.Title is required: [{"op":"add","path"... |
| `type` | string | no |  | no | Work item type path ${type} for workitem_create. Example: Bug / Task / User Story / Feature / Epic. |
| `id` | string | no |  | no | Work item id path for workitem_get/update/comment/link. Example: 131489. |
| `operations` | list | no |  | no | JSON Patch array sent as the whole workitem_update / workitem_link body. Example replace title: [{"op":"replace","path":"/fields/System.Title","value":"Fixed"}]. Link example: [... |
| `query` | string | no |  | no | WIQL string body for workitem_query. Example: SELECT [System.Id] FROM WorkItems WHERE [System.WorkItemType] = 'Bug'. |
| `text` | string | no |  | no | Comment HTML/text for workitem_comment (body text). Example: Please review. |
| `reviewers` | list | no |  | no | Optional reviewers for pr_create: [{id or descriptor}]. Example: [{"id":"d291b0c4-…"}]. |
| `targetRefName` | string | no |  | no | Target branch ref for pr_create. Example: refs/heads/main. |
| `title` | string | no |  | no | Pull request title for pr_create. Example: Add login. |
| `sourceRefName` | string | no |  | no | Source branch ref for pr_create. Example: refs/heads/feature/login. |
| `description` | string | no |  | no | Pull request description for pr_create. |
| `repositoryId` | string | no |  | no | Git repository id for pr_create / pr_get. |
| `isDraft` | boolean | no |  | no | Create the PR as draft (pr_create). Example: false. |
| `pullRequestId` | string | no |  | no | Pull request id for pr_get. |
| `sourceBranch` | string | no |  | no | Branch to build for build_queue. Example: refs/heads/main. |
| `definition` | object | no |  | no | Build definition for build_queue. Official shape {id: <definitionId>}. Example: {"id":12}. |
| `buildId` | string | no |  | no | Build id for build_get. |
| `releaseId` | string | no |  | no | Release id for release_get (vsrm.dev.azure.com). |
| `timeout_seconds` | number | no | 25 | no | Connect + read timeout. Default 25. Keep at or below the daemon stateful budget (~300s). |
| `api-version` | string | no | 7.2 | no | Default REST api-version when INPUT api-version is empty. Example: 7.2. |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.personal_access_token`. Never logged, never returned.
- [Azure DevOps REST (api-version 7.2) docs](https://learn.microsoft.com/en-us/rest/api/azure/devops/). Terms: https://www.microsoft.com/licensing/terms/.
- Microsoft product terms. This is Azure DevOps Boards/Repos/Pipelines — not Azure Resource Manager (see the Azure node).
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
