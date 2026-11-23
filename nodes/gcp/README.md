# GCP

Handler: `OfficialApiNode` via `api_spec.json`. Hosts are per-operation (`storage.googleapis.com`, `pubsub.googleapis.com`, `cloudfunctions.googleapis.com`, …).

- **Auth:** config `access_token` as Bearer. `projectId` is account-level config.

## Operations

Storage object get/put/delete/list; Pub/Sub publish/pull; Cloud Run invoke; Cloud Functions v1 `:call` (`data` string); Compute/GKE/Secret Manager/IAM gets; `listen` ingress.

`object_put` sends INPUT `file` as the raw media body (`uploadType=media`) with object name in query `name`. `functions_call` body is official `{data}` — a JSON-encoded string.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
