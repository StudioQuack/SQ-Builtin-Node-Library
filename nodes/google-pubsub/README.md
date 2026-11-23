# Google Pub/Sub

Official [Cloud Pub/Sub REST v1](https://cloud.google.com/pubsub/docs/reference/rest). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the GCP project. Minimize: send only the records this operation needs. `oauth_access_token` is never logged or returned.

- **Docs:** https://cloud.google.com/pubsub/docs/reference/rest
- **Base:** `https://pubsub.googleapis.com`
- **Auth:** Bearer `oauth_access_token` (secret). Scope `https://www.googleapis.com/auth/pubsub` or `cloud-platform`.

## trigger.listen

`kind: ingress`. OfficialApiNode does **not** open a socket, run StreamingPull (gRPC), or register a push endpoint. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. Unary `subscription.pull` is the HTTP data path.

## Engine gaps

This node does not mint tokens from a service-account PEM (OfficialApiNode is Bearer-only). `messages[].data` must already be Base64 per REST. Do not set deprecated `returnImmediately`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `topic.publish` | POST | `/v1/projects/{projectId}/topics/{topic}:publish` |
| `subscription.pull` | POST | `/v1/projects/{projectId}/subscriptions/{subscription}:pull` |
| `trigger.listen` | ingress | — | BPMN message event (no socket) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `oauth_access_token` | password | yes | Google OAuth 2.0 access token. Sent as Authorization: Bearer. StudioQuack secret. Never on INPUT. Operator-supplied; this node does not run an OAuth UI or JWT grant. |
| `projectId` | string | no | GCP project id in the resource path projects/{projectId}/… |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Customer is the controller of vectors, payloads, and metadata they submit.
- Provider terms: https://cloud.google.com/terms · https://developers.google.com/terms
- Official API only. Do not scrape consoles or dashboards.
