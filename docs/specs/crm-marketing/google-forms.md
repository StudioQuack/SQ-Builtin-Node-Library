# Google Forms

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/google-forms/`
- **Researched:** 2026-08-20
- **Official API:** Google Forms API **v1**
- **Docs:** REST https://developers.google.com/forms/api/reference/rest — guides https://developers.google.com/forms/api/guides — push https://developers.google.com/forms/api/guides/push-notifications
- **Auth:** OAuth2 — secrets: `clientSecret`, `refreshToken`

## Scope

One node for **Google Forms API v1** at `https://forms.googleapis.com`. Create/get/update forms, list/get responses (respondent PII), and **watches**. Official watches publish to a **Cloud Pub/Sub** topic (`forms.watches.create`); watches last **seven days** and must be `renew`ed. Catalog/legacy note “Watch via Drive push” is **not** the Forms API contract — Drive `files.watch` is a different API and is **not** used by this node. **Customer owns the Google account / Workspace.** Google APIs ToS and Google Workspace APIs terms apply. Do not scrape `docs.google.com/forms`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `form.get` | GET | `/v1/forms/{formId}` | Form schema + settings |
| `form.create` | POST | `/v1/forms` | Create form (title in body) |
| `form.update` | POST | `/v1/forms/{formId}:batchUpdate` | Questions/settings batch |
| `question.list` | GET | `/v1/forms/{formId}` | Same GET; project `items[]` as questions |
| `response.list` | GET | `/v1/forms/{formId}/responses` | Page form responses |
| `response.get` | GET | `/v1/forms/{formId}/responses/{responseId}` | One response |
| `watch.responses` | POST | `/v1/forms/{formId}/watches` | Watch `RESPONSES` → Pub/Sub |
| `trigger.listen` | POST | `/v1/forms/{formId}/watches` | Same as watch; eventType from config (`RESPONSES` or `SCHEMA`) |

`question.list` is not a separate RPC; it is `forms.get` with a projected output.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `form.get` | no | Chooses the row above |
| `clientId` | string | yes | | no | OAuth client |
| `clientSecret` | password | yes | | yes | OAuth secret |
| `refreshToken` | password | yes | | yes | Offline token |
| `formId` | string | no | | no | Default form id |
| `eventType` | select | listen | `RESPONSES` | no | `RESPONSES` or `SCHEMA` |

Scopes (request the minimum): `https://www.googleapis.com/auth/forms.body` (or `.readonly`), `https://www.googleapis.com/auth/forms.responses.readonly` for responses/watches.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | File upload answers: type `file` if the handler materializes Drive files |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = form.get` / `question.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `formId` | string | yes | Form id |

### `operation = form.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Form title (`info.title`) |
| `documentTitle` | string | no | Drive document title if supported on create |

Create is title-only; add questions with `form.update`.

### `operation = form.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `formId` | string | yes | Form id |
| `requests` | object[] | yes | `batchUpdate` requests (createItem, updateItem, …) |
| `includeFormInResponse` | boolean | no | Return updated form |

Cap `requests.length`. Validate each request has a known oneof key.

### `operation = response.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `formId` | string | yes | Form id |
| `pageSize` | number | no | Cap 5000; default 100 |
| `pageToken` | string | no | Next page |
| `filter` | string | no | `timestamp > …` per Forms list filter syntax |

### `operation = response.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `formId` | string | yes | Form id |
| `responseId` | string | yes | Response id |

### `operation = watch.responses` / `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `formId` | string | yes | Form id |
| `topicName` | string | yes | `projects/{p}/topics/{t}` owned by the caller’s GCP project |
| `eventType` | string | no | Default `RESPONSES`; `SCHEMA` for form edits |

Operator must grant `forms-notifications@system.gserviceaccount.com` **Pub/Sub Publisher** on the topic. SQ does not host Pub/Sub; a pull/push subscription must forward into message-event ingress. Renew watches before 7 days (`POST /v1/forms/{formId}/watches/{watchId}:renew`) via a scheduled BPMN timer — not this node’s job unless a later `watch.renew` verb is added.

## Output fields (by operation)

### `form.get` / `form.create` / `form.update` / `question.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `formId` | string | success | Form id |
| `form` | object | get/create/update | Full form resource |
| `questions` | object[] | `question.list` | `items` that are questions |
| `error` | object | failure | `{ status, code, message }` |

### `response.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `responses` | object[] | success | Response summaries (PII) |
| `nextPageToken` | string | when present | Pagination |
| `error` | object | failure | `{ status, code, message }` |

### `response.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `responseId` | string | success | Id |
| `response` | object | success | Answers (PII) |
| `error` | object | failure | `{ status, code, message }` |

### `watch.responses` / `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `watchId` | string | success | Watch id |
| `expireTime` | string | success | RFC3339 (~7 days) |
| `eventType` | string | success | `RESPONSES` / `SCHEMA` |
| `error` | object | failure | `{ status, code, message }` |

## Legal

- Google APIs ToS: https://developers.google.com/terms
- Google Workspace APIs user data policy: https://developers.google.com/workspace/workspace-api-user-data-developer-policy
- Forms responses are personal data. Minimize. Customer is controller.
- Pub/Sub topics must stay in the **customer’s** GCP project (not StudioQuack’s), so a compromised daemon cannot publish into another tenant’s topic.
- Official-API-first: `forms.googleapis.com` (+ OAuth token endpoint). No Drive push, no HTML scrape.

## Implementation notes

- JDK `HttpClient` only.
- Timeouts: connect 10s, read 30s. Body cap 2 MiB.
- Retry: `429` honor `Retry-After`; `5xx` backoff + jitter, max 3.
- File upload answers: if the API returns Drive file ids, resolve bytes via Drive only when a later verb is specified; this node returns ids, not bytes, unless type `file` is explicitly requested and Drive scope is granted.
- Errors → `{ error }` on OUTPUT.
- **DO NOT BUILD** in this task.
