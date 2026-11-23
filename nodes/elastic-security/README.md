# Elastic Security

Official [Kibana Cases API](https://www.elastic.co/docs/api/doc/kibana/group/endpoint-cases). **Not** the Elasticsearch REST API. Point `kibanaHost` at Kibana (often port 5601), never `:9200`.

- **Docs:** https://www.elastic.co/docs/api/doc/kibana/group/endpoint-cases
- **Base:** `https://{kibanaHost}`
- **Auth:** header `Authorization` prefix `ApiKey `, configKey `api_key`
- **Handler:** `OfficialApiNode` via `api_spec.json`. `listen` is `kind: ingress` (BPMN message start/catch/boundary — no socket).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `create-case` | POST | `{kibanaHost}/api/cases` |
| `get-case` | GET | `{kibanaHost}/api/cases/{caseId}` |
| `list-cases` | GET | `{kibanaHost}/api/cases/_find` |
| `update-case` | PATCH | `{kibanaHost}/api/cases` |
| `delete-case` | DELETE | `{kibanaHost}/api/cases` |
| `get-case-status` | GET | `{kibanaHost}/api/cases/_find` |
| `add-comment` | POST | `{kibanaHost}/api/cases/{caseId}/comments` |
| `get-comment` | GET | `{kibanaHost}/api/cases/{caseId}/comments/{commentId}` |
| `list-comments` | GET | `{kibanaHost}/api/cases/{caseId}/comments/_find` |
| `update-comment` | PATCH | `{kibanaHost}/api/cases/{caseId}/comments` |
| `remove-comment` | DELETE | `{kibanaHost}/api/cases/{caseId}/comments/{commentId}` |
| `add-tag` | PATCH | `{kibanaHost}/api/cases` |
| `remove-tag` | PATCH | `{kibanaHost}/api/cases` |
| `create-connector` | POST | `{kibanaHost}/api/actions/connector/{id}` |
| `listen` | ingress | — |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | list-cases | no | Default verb. listen is BPMN ingress. get-case-status is _find counts, not retired /status. |
| `kibanaHost` | string | yes |  | no | Kibana host without scheme (not Elasticsearch). Example: kibana.example.com or kibana.example.com:5601. For a non-default space, include /s/{spaceId} after the host. OfficialApi... |
| `api_key` | password | yes |  | yes | Kibana/Elastic API key. Authorization: ApiKey {value}. StudioQuack secret. Never on INPUT or in logs. |
| `tags` | list | no |  | no | Query-string/request-body parameter `tags` for operations: create-case, list-cases. |
| `assignees` | list | no |  | no | Request-body parameter `assignees` for operation: create-case. |
| `category` | string | no |  | no | Request-body parameter `category` for operation: create-case. |
| `settings` | object | no |  | no | create-case settings {syncAlerts, extractObservables}. |
| `description` | string | no |  | no | create-case description markdown. Required. |
| `title` | string | no |  | no | create-case title. Required. Example: Phishing report. |
| `customFields` | object | no |  | no | Request-body parameter `customFields` for operation: create-case. |
| `connector` | object | no |  | no | create-case connector {id, name, type, fields}. Use {"id":"none","name":"none","type":".none","fields":null} for no external connector. |
| `owner` | string | no |  | no | Cases owner: securitySolution / observability / cases. |
| `severity` | string | no |  | no | Query-string/request-body parameter `severity` for operations: create-case, list-cases. |
| `caseId` | string | no |  | no | Case id for get/comment ops. |
| `page` | number | no |  | no | Query-string parameter `page` for operations: list-cases, list-comments. |
| `sortField` | string | no |  | no | Query-string parameter `sortField` for operation: list-cases. |
| `status` | string | no |  | no | Query-string parameter `status` for operation: list-cases. |
| `perPage` | number | no |  | no | Query-string parameter `perPage` for operations: list-cases, get-case-status, list-comments. |
| `sortOrder` | string | no |  | no | Query-string parameter `sortOrder` for operations: list-cases, list-comments. |
| `search` | string | no |  | no | Query-string parameter `search` for operation: list-cases. |
| `cases` | object | no |  | no | update-case / add-tag / remove-tag PATCH body { cases: [...] } with id+version. |
| `ids` | string | no |  | no | delete-case query ids (JSON array as documented). |
| `alertId` | string | no |  | no | Request-body parameter `alertId` for operation: add-comment. |
| `comment` | string | no |  | no | Request-body parameter `comment` for operations: add-comment, update-comment. |
| `index` | string | no |  | no | Request-body parameter `index` for operation: add-comment. |
| `rule` | object | no |  | no | Request-body parameter `rule` for operation: add-comment. |
| `type` | string | no |  | no | Request-body parameter `type` for operations: add-comment, update-comment. |
| `commentId` | string | no |  | no | Comment id for get/remove-comment. |
| `version` | string | no |  | no | Request-body parameter `version` for operation: update-comment. |
| `id` | string | no |  | no | create-connector path id (1–36). |
| `secrets` | object | no |  | no | Request-body parameter `secrets` for operation: create-connector. |
| `name` | string | no |  | no | Request-body parameter `name` for operation: create-connector. Required by: create-connector. |
| `connector_type_id` | string | no |  | no | Request-body parameter `connector_type_id` for operation: create-connector. Required by: create-connector. |
| `config` | object | no |  | no | Request-body parameter `config` for operation: create-connector. |
| `timeout_seconds` | number | no | 25 | no | Per-request HTTP timeout. Positive; <=0 → 25. |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Notes

- `GET /api/cases/status` was removed in Kibana 9. `get-case-status` uses `_find` counts (`count_open_cases`, `count_in_progress_cases`, `count_closed_cases`).
- There is no dedicated tag-add/remove API. `add-tag` / `remove-tag` are the same PATCH as `update-case`; send the full `tags` array with `id` + `version`.
- Spaces: prefix by putting `/s/{spaceId}` in `kibanaHost` (placeholder encoding applies).
- `listen` does not open a Cases subscription. Use a BPMN message event; operators who need Kibana-originated pushes configure a Case Management connector against StudioQuack ingress.

## Engine gaps

- Mutating Kibana calls require header `kbn-xsrf`. OfficialApiNode does not send extra headers besides auth.
- Path placeholders are URL-encoded (`:` in `:5601` becomes `%3A`). Prefer TLS on 443 or a reverse proxy.
