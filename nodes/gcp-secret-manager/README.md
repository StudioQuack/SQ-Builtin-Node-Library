# GCP Secret Manager

Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://cloud.google.com/secret-manager/docs/reference/rest/v1/projects.secrets/addVersion
- **Base:** `https://secretmanager.googleapis.com/v1`
- **Auth:** config `access_token` as Bearer. Never on INPUT.

## Operations

| operation | HTTP | Notes |
|---|---|---|
| `access-secret-version` | GET | `.../secrets/{secretId}/versions/{version}:access` |
| `add-secret-version` | POST | body `{payload:{data, dataCrc32c}}` from INPUT `data` / `dataCrc32c` (or `payload`) |
| `create-secret` | POST | query `secretId`; body `replication`, `labels`, `expireTime`, `ttl`, `annotations` |
| `get-secret` | GET | |
| `list-secrets` | GET | `pageSize`, `pageToken`, `filter` |
| `destroy-secret-version` | POST | optional body `etag` |
| `delete-secret` | DELETE | optional query `etag` |
| `listen` | ingress | BPMN message event |

`data` on INPUT is the secret payload (password-typed) because this node stores secret values.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
