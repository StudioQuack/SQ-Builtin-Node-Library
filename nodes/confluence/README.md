# Confluence


Page/blog writes keep `body` (and `version` on page_update) as escape-hatch envelopes; `representation` / `value` / `version_number` merge into them via bodyMap.

Confluence Cloud REST v2 at `https://api.atlassian.com/ex/confluence/{cloud_id}/wiki/api/v2/…`. Docs: [REST v2](https://developer.atlassian.com/cloud/confluence/rest/v2/intro/)

Auth: HTTP Basic (`email` + `api_token`).

Page/blog writes take official body fields `representation` (`storage` | `atlas_doc_format` | `wiki`) and `value` (HTML / ADF / wiki), nested as `body.representation` / `body.value`. `body` remains an escape hatch. `page_update` requires `version_number` (current + 1).

`attach_file` is v1 multipart (`X-Atlassian-Token: nocheck`) to `/wiki/rest/api/content/{id}/child/attachment`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `page_create` | POST | `/{cloud_id}/wiki/api/v2/pages` |
| `page_get` | GET | `/{cloud_id}/wiki/api/v2/pages/{id}` |
| `page_update` | PUT | `/{cloud_id}/wiki/api/v2/pages/{id}` |
| `page_delete` | DELETE | `/{cloud_id}/wiki/api/v2/pages/{id}` |
| `blog_create` | POST | `/{cloud_id}/wiki/api/v2/blogposts` |
| `blog_get` | GET | `/{cloud_id}/wiki/api/v2/blogposts/{id}` |
| `blog_delete` | DELETE | `/{cloud_id}/wiki/api/v2/blogposts/{id}` |
| `space_get` | GET | `/{cloud_id}/wiki/api/v2/spaces/{id}` |
| `search` | GET | `/{cloud_id}/wiki/api/v2/pages` |
| `attach_file` | POST | `/{cloud_id}/wiki/rest/api/content/{id}/child/attachment` |
| `listen` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `api_token` (password secret), `email`, **`cloud_id` (required)**, `timeout_seconds`.

`api_token` and `email` stay in configuration only — never on INPUT. **`cloud_id`** belongs here for typical instances (one Confluence Cloud site per environment); INPUT `cloud_id` overrides per call when you need multiple tenants from one node.

Missing `cloud_id` on both INPUT and config yields `cloud_id is required for operation … (set on INPUT or in configurationParameters)`.
