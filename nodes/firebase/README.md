# Firebase

Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://cloud.google.com/firestore/docs/reference/rest/v1/projects.databases.documents/createDocument
- **Base:** `https://firestore.googleapis.com`
- **Auth:** config `oauth_access_token` as `Authorization: Bearer`. Never on INPUT.

## Operations

| operation | HTTP | Path / notes |
|---|---|---|
| `firestore.document.get` | GET | `/v1/projects/{projectId}/databases/{databaseId}/documents/{collectionId}/{documentId}` optional `mask` |
| `firestore.document.create` | POST | `/v1/projects/{projectId}/databases/{databaseId}/documents/{collectionId}` body `{fields}` query `documentId` |
| `firestore.document.delete` | DELETE | same path as get |
| `firestore.document.list` | GET | collection path; `pageSize`, `pageToken`, `orderBy`, `readTime`, `showMissing` |
| `trigger.listen` | ingress | BPMN message event, not a socket |

`fields` is the official Firestore Document.fields map of Value objects, not a free-form JSON document.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
