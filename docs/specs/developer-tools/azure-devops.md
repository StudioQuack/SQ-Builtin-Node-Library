# Azure DevOps

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/azure-devops/`
- **Researched:** 2026-08-20
- **Official API:** Azure DevOps Services REST API — default **api-version 7.1** (stable); **7.2** is the current vNext / Azure DevOps Server vNext line. Query parameter `api-version` is **required** on every call.
- **Docs:** https://learn.microsoft.com/en-us/rest/api/azure/devops/?view=azure-devops-rest-7.1
- **Auth:** `pat` | `oauth2` — secret binding: `accessToken`

## Scope

One node for **Azure DevOps** (Boards work items, Git pull requests, Builds, classic Releases, service-hook `listen`). Host `https://dev.azure.com/{organization}` (and `https://vsrm.dev.azure.com/{organization}` for Release Management). **Do not include Azure Resource Manager**, Storage, Functions, Key Vault, or any ARM resource — those belong to the separate planned `azure` node. Optional on-prem Azure DevOps Server via `baseUrl` (`{server}/tfs/{collection}`).

`git_push_listen` is **not** a second node: it is an alias of `listen` with event filter `git.push`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `workitem_create` | POST | `/{project}/_apis/wit/workitems/${type}?api-version=7.1` | Create a work item (JSON Patch body) |
| `workitem_get` | GET | `/{project}/_apis/wit/workitems/{id}?api-version=7.1` | Get one work item |
| `workitem_update` | PATCH | `/{project}/_apis/wit/workitems/{id}?api-version=7.1` | Update fields / relations (JSON Patch) |
| `workitem_query` | POST | `/{project}/_apis/wit/wiql?api-version=7.1` | WIQL query (then optional batch get) |
| `workitem_comment` | POST | `/{project}/_apis/wit/workItems/{id}/comments?api-version=7.1-preview.4` | Add a comment (Comments API) |
| `workitem_link` | PATCH | `/{project}/_apis/wit/workitems/{id}?api-version=7.1` | Add a relation (`System.LinkTypes.*`) |
| `pr_create` | POST | `/{project}/_apis/git/repositories/{repositoryId}/pullrequests?api-version=7.1` | Create a pull request |
| `pr_get` | GET | `/{project}/_apis/git/repositories/{repositoryId}/pullrequests/{pullRequestId}?api-version=7.1` | Get a pull request |
| `build_queue` | POST | `/{project}/_apis/build/builds?api-version=7.1` | Queue a build |
| `build_get` | GET | `/{project}/_apis/build/builds/{buildId}?api-version=7.1` | Get a build |
| `release_get` | GET | `https://vsrm.dev.azure.com/{org}/{project}/_apis/release/releases/{releaseId}?api-version=7.1` | Get a classic Release |
| `listen` | POST | `/_apis/hooks/subscriptions?api-version=7.1` | Register a service hook; inbound maps onto `output` |
| `git_push_listen` | POST | same as `listen` | Alias: `listen` with `eventType=git.push` only |

Dropped: ARM (`Microsoft.Resources`, VM power, AKS, Key Vault). Drop undocumented dashboard scrape.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `workitem_get` | no | Chooses the row above |
| `organization` | string | yes | | no | Azure DevOps org name (`dev.azure.com/{organization}`) |
| `project` | string | yes | | no | Default project name or GUID |
| `authType` | select | yes | `pat` | no | `pat` Basic empty-user+PAT; `oauth2` Bearer (Entra / ADO OAuth) |
| `accessToken` | password | yes | | yes | PAT or OAuth access token |
| `apiVersion` | string | no | `7.1` | no | REST `api-version`. Allow `7.1` or `7.2`. Preview suffixes only when a verb documents them (comments) |
| `baseUrl` | string | no | | no | Override host for Azure DevOps Server, e.g. `https://tfs.example.com/tfs/DefaultCollection`. Empty → `https://dev.azure.com/{organization}` |
| `timeoutSeconds` | number | no | `30` | no | Connect + request timeout |
| `insecureTls` | boolean | no | `false` | no | Lab-only for Server TLS |
| `webhookSecret` | password | no | | yes | Service-hook HTTP headers / Basic secret used to verify inbound |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No PAT |
| `output` | OUTPUT | object — `{ status: "ok", … }` or `{ error }` | Never secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = workitem_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `project` | string | no | Override config project |
| `type` | string | yes | Work item type (`Bug`, `Task`, `User Story`, …) — path uses `$` + type |
| `fields` | object | yes | Map of reference names → values (`System.Title` required by most processes) |
| `bypassRules` | boolean | no | Query `bypassRules` (needs elevated permission) |

Body: JSON Patch array `[{ "op":"add","path":"/fields/System.Title","value":"…" }, …]`. `Content-Type: application/json-patch+json`.

### `operation = workitem_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Work item id |
| `$expand` | string | no | `all`, `relations`, `fields`, … |

### `operation = workitem_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Work item id |
| `patch` | array | yes | JSON Patch operations (`add`/`replace`/`remove` on `/fields/…`) |

### `operation = workitem_query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `wiql` | string | yes | WIQL string |
| `expandWorkItems` | boolean | no | If true, follow with `workitemsbatch` (max 200 ids) |

### `operation = workitem_comment`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Work item id |
| `text` | string | yes | Comment text (HTML allowed per Comments API) |

### `operation = workitem_link`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Source work item |
| `rel` | string | yes | e.g. `System.LinkTypes.Hierarchy-Forward` |
| `url` | string | yes | Target work item URL (`…/_apis/wit/workItems/{id}`) |
| `comment` | string | no | Relation attributes comment |

### `operation = pr_create` / `pr_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repositoryId` | string | yes | Repo GUID or name |
| `title` | string | yes | `pr_create` |
| `sourceRefName` | string | yes | `pr_create` e.g. `refs/heads/feature` |
| `targetRefName` | string | yes | `pr_create` e.g. `refs/heads/main` |
| `description` | string | no | `pr_create` |
| `pullRequestId` | number | yes | `pr_get` |
| `includeCommits` | boolean | no | `pr_get` |
| `includeWorkItemRefs` | boolean | no | `pr_get` |

### `operation = build_queue` / `build_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `definitionId` | number | yes | `build_queue` pipeline/definition id |
| `sourceBranch` | string | no | `build_queue` |
| `parameters` | string | no | `build_queue` JSON string of queue params |
| `buildId` | number | yes | `build_get` |

### `operation = release_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `releaseId` | number | yes | Classic Release id |

Uses **vsrm** host, not ARM. YAML Pipelines are `build_*`, not this verb.

### `operation = listen` / `git_push_listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `register` or `map` |
| `ingressUrl` | string | yes when `register` | Message-event ingress URL |
| `publisherId` | string | no | Default `tfs` |
| `eventType` | string | no | Default `workitem.created`; `git_push_listen` forces `git.push` |
| `resourceVersion` | string | no | e.g. `1.0` |
| `repositoryId` | string | when git events | Filter repo |
| `projectId` | string | no | Project GUID for the subscription filter |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `error` | string | failure | Never PAT |
| `httpStatus` | number | HTTP error | Status |
| `workItem` | object | WI create/get/update/link | `id`, `rev`, `fields` (subset: Title, State, AssignedTo, WorkItemType), `url` |
| `workItemIds` | array | `workitem_query` | Ids from WIQL |
| `workItems` | array | query + expand | Batch-get summaries (≤200) |
| `comment` | object | `workitem_comment` | `id`, `text`, `createdBy` |
| `pullRequest` | object | PR ops | `pullRequestId`, `title`, `status`, `url` |
| `build` | object | build ops | `id`, `status`, `result`, `sourceBranch`, `_links.web.href` |
| `release` | object | `release_get` | `id`, `name`, `status` |
| `subscription` | object | `listen` register | `id`, `eventType`, `consumerInputs.url` (no secret) |
| `eventType` | string | inbound | Service-hook event type |
| `resource` | object | inbound | Publisher resource (push/work item/build) sanitized |
| `payload` | object | inbound | Full JSON cap-truncated |

## Legal

- [Microsoft Services Agreement](https://www.microsoft.com/en-us/servicesagreement/) / Azure DevOps [usage](https://azure.microsoft.com/support/legal/)
- REST: https://learn.microsoft.com/en-us/rest/api/azure/devops/
- Auth (PAT / OAuth / MSAL): https://learn.microsoft.com/en-us/azure/devops/integrate/get-started/authentication/authentication-guidance
- Service hooks: https://learn.microsoft.com/en-us/azure/devops/service-hooks/services/webhooks
- Rate limiting: Azure DevOps returns **HTTP 429** with `Retry-After` when usage is excessive (documented as delayed responses / 429 under consumption limits). Retry only 429/5xx. WIQL/batch: max **200** work items per batch get.
- Do not call Azure Resource Manager from this node.
- PII: work item fields and comments may contain personal data. Operator owns the org.

## Implementation notes

- JDK HTTP only. **No** Azure DevOps .NET client libraries / `Microsoft.VisualStudio.Services.*` SDK.
- Every URL includes `?api-version={apiVersion}` (`7.1` default). Do not omit it.
- PAT: `Authorization: Basic` of `":" + pat` (empty username) UTF-8 Base64. OAuth: `Authorization: Bearer`. `Accept: application/json`. Work item mutate: `Content-Type: application/json-patch+json`.
- Cloud org URL: `https://dev.azure.com/{organization}`. Server: `baseUrl` with no extra `/_apis` duplication. Release API: `https://vsrm.dev.azure.com/{organization}` (or Server vsrm equivalent).
- Timeouts 30s default. Body cap **8 MiB**. Retry 429/5xx + jitter; no retry other 4xx. TLS verify on; `insecureTls` lab-only for Server.
- Success `{ status: "ok", … }`; `{ error }` on failure. Never throw past dispatch. Never log PAT.
- `listen` register: `POST /_apis/hooks/subscriptions` consumer `webHooks` / `httpRequest`, `consumerInputs.url` = StudioQuack message-event ingress, optional Basic or HTTP header secret from `webhookSecret`. Map inbound to BPMN message events — not a second node.
- Inbound verification (required): require the configured shared secret (HTTP Basic on the hook, or custom header). Reject missing/invalid. Azure also sends `X-VSS-SubscriptionId`. After verify, map `eventType` + `resource` onto `output`.
- `git_push_listen` = `listen` with `eventType=git.push` (publisher Git).
