# Elastic Security (Cases)

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/elastic-security/`
- **Researched:** 2026-08-20
- **Official API:** Kibana **Cases** REST API (current Kibana API docs, 2026). Not the Elasticsearch REST API.
- **Docs:** https://www.elastic.co/docs/api/doc/kibana/group/endpoint-cases
- **Auth:** `api_key` or `basic` — secret bindings: `api_key`, `password`

## Scope

Manages **Kibana Cases** (Elastic Security / Observability / Stack Management owners) over the **Kibana HTTP API**. Cases do **not** live on the Elasticsearch REST API. Catalog `baseUrl` must be the **Kibana origin** (often `https://kibana.example:5601`), never the Elasticsearch `:9200` URL.

Spaces: if `space_id` is set, prefix paths with `/s/{space_id}` (e.g. `GET /s/{space_id}/api/cases/_find`). Default space uses unprefixed `/api/cases…`.

Every mutating call requires header `kbn-xsrf` (any non-empty string; use `true`).

**DO NOT BUILD `listen`.** There is no official Cases **inbound** event-subscription API. The [Webhook - Case Management connector](https://www.elastic.co/docs/reference/kibana/connectors-kibana/cases-webhook-action-type) is **outbound** (Kibana pushes a case to a third-party REST API). Operators who need ingress should configure that connector (or a Kibana webhook action) themselves against StudioQuack message-event ingress — not this node.

**DO NOT BUILD:** Elasticsearch `_search` against case indices; undocumented Kibana internals; `GET /api/cases/status` (removed in Kibana **9.0.0**).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `create-case` | POST | `/api/cases` | Open a case (`owner` typically `securitySolution`) |
| `get-case` | GET | `/api/cases/{caseId}` | Case metadata. Comments are **not** inlined — use `list-comments` |
| `list-cases` | GET | `/api/cases/_find` | Search/filter/page cases |
| `update-case` | PATCH | `/api/cases` | Body `{ "cases": [ { id, version, …fields } ] }`. Optimistic concurrency via `version` |
| `delete-case` | DELETE | `/api/cases?ids=…` | Query `ids` JSON array of case ids (URL-encoded) |
| `get-case-status` | GET | `/api/cases/_find` | **Changed.** Retired `GET /api/cases/status` (deprecated 8.1, **deleted 9.0**). Use `_find` `count_open_cases` / `count_in_progress_cases` / `count_closed_cases` |
| `add-comment` | POST | `/api/cases/{caseId}/comments` | User comment or alert attachment (`type`: `user` \| `alert`) |
| `get-comment` | GET | `/api/cases/{caseId}/comments/{commentId}` | One comment |
| `list-comments` | GET | `/api/cases/{caseId}/comments/_find` | Paginated comments (replaces retired `GET /api/cases/{id}/comments`) |
| `update-comment` | PATCH | `/api/cases/{caseId}/comments` | Update user comment; cannot change `type` or `owner` |
| `remove-comment` | DELETE | `/api/cases/{caseId}/comments/{commentId}` | Delete one comment |
| `add-tag` | PATCH | `/api/cases` | **No dedicated tag-add API.** GET case, append tag, PATCH with `version` + full `tags` array |
| `remove-tag` | PATCH | `/api/cases` | **No dedicated tag-remove API.** GET case, drop tag, PATCH with `version` + remaining `tags` |
| `create-connector` | POST | `/api/actions/connector/{id}` | Official **Kibana Actions** API (not under `/api/cases`). Case-push uses this connector id |

Create case: https://www.elastic.co/docs/api/doc/kibana/operation/operation-createcasedefaultspace  
Find cases: https://www.elastic.co/docs/api/doc/kibana/operation/operation-findcasesdefaultspace  
Get case: https://www.elastic.co/docs/api/doc/kibana/operation/operation-getcasedefaultspace  
Update: https://www.elastic.co/docs/api/doc/kibana/operation/operation-updatecasedefaultspace  
Delete: https://www.elastic.co/docs/api/doc/kibana/operation/operation-deletecasedefaultspace  
Add comment: https://www.elastic.co/docs/api/doc/kibana/operation/operation-addcasecommentdefaultspace  
Get comment: https://www.elastic.co/docs/api/doc/kibana/operation/operation-getcasecommentdefaultspace  
Find comments: https://www.elastic.co/docs/api/doc/kibana/operation/operation-findcasecommentsdefaultspace  
Update comment: https://www.elastic.co/docs/api/doc/kibana/operation/operation-updatecasecommentdefaultspace  
Delete comment: https://www.elastic.co/docs/api/doc/kibana/operation/operation-deletecasecommentdefaultspace  
Create connector: https://www.elastic.co/docs/api/doc/kibana/operation/operation-post-actions-connector-id

Kibana 9 breaking notes: `GET /api/cases/status` deleted with **no same-path replacement**; use find-cases counts. https://www.elastic.co/guide/en/kibana/8.19/breaking-changes-summary.html

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `list-cases` | no | Chooses the row above |
| `auth_type` | select | yes | `api_key` | no | `api_key` or `basic` |
| `base_url` | string | yes | empty | no | **Kibana** origin, no trailing slash (not Elasticsearch) |
| `space_id` | string | no | empty | no | Kibana space. Empty = default space |
| `api_key` | password | if `api_key` | empty | yes | Kibana/Elastic API key. Header `Authorization: ApiKey {value}` or the key string Elastic documents for Kibana. Never on INPUT, never logged |
| `username` | string | if `basic` | empty | no | Basic user |
| `password` | password | if `basic` | empty | yes | Basic password. Never logged |
| `default_owner` | string | no | `securitySolution` | no | Cases owner: `securitySolution` \| `observability` \| `cases` |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s |
| `verify_tls` | boolean | no | `true` | no | TLS verify. Lab-only if false |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No credentials. Case text may be PII / threat intel — do not log |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = create-case`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Case title |
| `description` | string | yes | Description |
| `owner` | string | no | Default instance `default_owner` |
| `tags` | array | no | String tags (may be empty) |
| `severity` | string | no | `low` \| `medium` \| `high` \| `critical` |
| `connector` | object | yes | `{ id, name, type, fields }`. None-connector: `id=none`, `name=none`, `type=.none`, `fields=null` |
| `settings` | object | yes | At least `syncAlerts` boolean |
| `assignees` | array | no | `{ uid }` objects |
| `category` | string | no | Category |
| `custom_fields` | array | no | `{ key, type, value }` as documented |

### `operation = get-case`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `case_id` | string | yes | Case id |

### `operation = list-cases`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page (1-based) |
| `per_page` | number | no | Page size. Clamp 1–100 |
| `search` | string | no | Elasticsearch `simple_query_string` |
| `status` | string | no | `open` \| `in-progress` \| `closed` |
| `severity` | string | no | `low` \| `medium` \| `high` \| `critical` |
| `tags` | string or array | no | Tag filter |
| `owner` | string or array | no | Owner filter |
| `sort_field` | string | no | Sort field as documented |
| `sort_order` | string | no | `asc` \| `desc` |

### `operation = update-case`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `case_id` | string | yes | Case id (`id` in PATCH body) |
| `version` | string | yes | Optimistic lock from GET |
| `title` | string | no | New title |
| `description` | string | no | New description |
| `status` | string | no | `open` \| `in-progress` \| `closed` |
| `severity` | string | no | Severity |
| `tags` | array | no | **Full** replacement tag list |
| `connector` | object | no | Connector + fields |
| `settings` | object | no | Settings |
| `assignees` | array | no | Assignees |
| `custom_fields` | array | no | Custom fields |

### `operation = delete-case`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `case_ids` | array | yes | One or more case ids. Encoded as `ids=[…]` query |

### `operation = get-case-status`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `owner` | string or array | no | Filter counts to an owner |

Handler: `GET /api/cases/_find?per_page=1` (+ `owner`). Map `count_open_cases`, `count_in_progress_cases`, `count_closed_cases`. Do not call `/api/cases/status`.

### `operation = add-comment`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `case_id` | string | yes | Case id |
| `type` | string | yes | `user` or `alert` |
| `comment` | string | if `user` | Comment text |
| `owner` | string | yes | Same owner family as the case |
| `alert_id` | string | if `alert` | Alert document id |
| `index` | string | if `alert` | Alert index |
| `rule` | object | if `alert` | `{ id, name }` as documented |

### `operation = get-comment`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `case_id` | string | yes | Case id |
| `comment_id` | string | yes | Comment id |

### `operation = list-comments`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `case_id` | string | yes | Case id |
| `page` | number | no | Page |
| `per_page` | number | no | Page size. Clamp 1–100 |
| `sort_order` | string | no | `asc` \| `desc` |

### `operation = update-comment`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `case_id` | string | yes | Case id |
| `comment_id` | string | yes | Comment id (`id` in body) |
| `version` | string | yes | Comment version |
| `comment` | string | yes | New text (user comments) |
| `type` | string | yes | Must remain `user` (API forbids type change) |
| `owner` | string | yes | Must remain unchanged |

### `operation = remove-comment`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `case_id` | string | yes | Case id |
| `comment_id` | string | yes | Comment id |

### `operation = add-tag` | `remove-tag`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `case_id` | string | yes | Case id |
| `tag` | string | yes | Tag to add or remove |

Handler: GET case → mutate `tags` (add: append unique; remove: filter) → PATCH `/api/cases` with `id`, `version`, `tags`. If `version` conflict (409), return `{ error }` with `http_status=409` (no silent retry loop).

### `operation = create-connector`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Connector id (path). Length 1–36 as documented |
| `name` | string | yes | Display name |
| `connector_type_id` | string | yes | e.g. `.cases-webhook`, `.jira`, `.servicenow`, `.none` |
| `config` | object | yes | Type-specific config. Never log |
| `secrets` | object | no | Type-specific secrets. **Secret-bearing.** Never log, never echo on OUTPUT |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `case` | object | create/get/update/add-tag/remove-tag/add-comment/update-comment 2xx | Case payload (`id`, `version`, `title`, `status`, `tags`, `owner`, `severity`, counts, `connector`, …). Update/comment APIs often return the **full case** |
| `cases` | array | list-cases 2xx | `cases` array from `_find` |
| `page` | number | list-cases / list-comments | Page |
| `per_page` | number | list-cases / list-comments | Page size |
| `total` | number | list-cases / list-comments | Total hits |
| `count_open_cases` | number | list-cases / get-case-status | Open count |
| `count_in_progress_cases` | number | list-cases / get-case-status | In-progress count |
| `count_closed_cases` | number | list-cases / get-case-status | Closed count |
| `comment` | object | get-comment 2xx | Single comment |
| `comments` | array | list-comments 2xx | Comment page |
| `connector` | object | create-connector 2xx | Created connector **without** `secrets` |
| `error` | string | failure | No credentials |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 / 30 |

## Legal

- Kibana Cases API: https://www.elastic.co/docs/api/doc/kibana/group/endpoint-cases
- Elastic license / subscription (connectors and Security solution features may require a paid license): https://www.elastic.co/pricing and the operator’s Elastic agreement / EULA
- Case bodies often contain **threat intel, alert context, and personal data**. Minimize fields. Never log descriptions, comments, or connector secrets.
- Kibana feature privileges: Cases `read` vs `all` (+ `delete` sub-feature for delete).

## Implementation notes

- JDK HTTP only. No Elastic client SDK.
- `base_url` must not look like Elasticsearch (`/_cluster`, port `9200` alone is not proof — document Kibana). If the operator points at ES, 404s are expected.
- Headers: `Content-Type: application/json`, `kbn-xsrf: true`, `Authorization` as above. **Never log Authorization.**
- TLS verified by default. Timeout 25 s. Body cap 2 MiB. `Redirect.NEVER`.
- URL-encode path ids. DELETE `ids` query: JSON array percent-encoded.
- 401/403/404/409/429/5xx → OUTPUT `{ error }` + `http_status`. No throw past dispatch. No inner retry loop.
