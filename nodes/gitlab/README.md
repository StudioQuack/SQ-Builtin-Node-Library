# GitLab

Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://docs.gitlab.com/api/notes/ and https://docs.gitlab.com/api/rest/
- **Base:** `https://gitlab.com/api/v4` (self-managed: change `api_spec.baseUrl`)
- **Auth:** config `access_token` as `PRIVATE-TOKEN`. Never on INPUT.

## Operations

Project, file, issue, MR, pipeline, job, release, branch, tag CRUD as in `api_spec.json`. `issue_note` is `POST /projects/{id}/issues/{iid}/notes` with official fields `body` (required), `internal`, `created_at`.

File write `content` is the file text (string), not a platform file. AI tools expose every INPUT shortCode (`branch`, `commit_message`, `title`, `body`, `job_id`, `pipeline_id`, etc.); `content` maps to `description` / `body` / branch or tag names when the operation-specific field is omitted.

`listen` is a BPMN message event.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
