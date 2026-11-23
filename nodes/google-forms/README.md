# Google Forms

Official **Google Forms API v1**. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** REST https://developers.google.com/forms/api/reference/rest · guides https://developers.google.com/forms/api/guides · push https://developers.google.com/forms/api/guides/push-notifications
- **Base:** `https://forms.googleapis.com`
- **Auth:** Bearer `access_token` (customer supplies a current OAuth token).

**Watches → customer Cloud Pub/Sub**, not Drive push. `forms.watches.create` publishes to `projects/{p}/topics/{t}` in the caller’s GCP project. Watches last **seven days** and must be renewed. Grant `forms-notifications@system.gserviceaccount.com` **Pub/Sub Publisher**. SQ does not host Pub/Sub; a subscription must forward into message-event ingress. `trigger.listen` is ingress (BPMN message event).

`question.list` is the same GET as `form.get` (project `items[]`).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `form.get` | GET | `/v1/forms/{formId}` |
| `form.create` | POST | `/v1/forms` | `title` / `documentTitle` → `info.*` |
| `form.update` | POST | `/v1/forms/{formId}:batchUpdate` |
| `question.list` | GET | `/v1/forms/{formId}` |
| `response.list` | GET | `/v1/forms/{formId}/responses` |
| `response.get` | GET | `/v1/forms/{formId}/responses/{responseId}` |
| `watch.responses` | POST | `/v1/forms/{formId}/watches` | `eventType` / `target` → `watch.*` |
| `trigger.listen` | — | ingress — BPMN message event |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | OAuth Bearer |
| `formId` | string | no | Default form id |
| `timeout_seconds` | number | no | Default 25 |

Scopes (minimum): `https://www.googleapis.com/auth/forms.body` (or `.readonly`), `https://www.googleapis.com/auth/forms.responses.readonly` for responses/watches.

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Customer owns the Google account / Workspace. Forms responses are personal data. Minimize.
- Google APIs ToS: https://developers.google.com/terms
- Workspace user-data policy: https://developers.google.com/workspace/workspace-api-user-data-developer-policy
- Pub/Sub topics stay in the **customer’s** GCP project (not StudioQuack’s).
- Official API only (`forms.googleapis.com`). No Drive push, no HTML scrape of `docs.google.com/forms`.
- `trigger.listen` is not a StudioQuack Webhook node — use a BPMN message event.
