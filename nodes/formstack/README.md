# Formstack

Official Formstack Forms API **V2025**. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developers.formstack.com/reference · overview https://developers.formstack.com/reference/api-overview
- **Base:** `https://www.formstack.com/api/v2025`
- **Auth:** Bearer `personalAccessToken` (`fs_pat_…`). Customer owns the Formstack account. V2 OAuth exists but this node targets V2025.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `form.list` | GET | `/forms` |
| `form.get` | GET | `/forms/{formId}` |
| `submission.list` | GET | `/forms/{formId}/submissions` |
| `submission.get` | GET | `/submissions/{submissionId}` |
| `trigger.listen` | — | ingress — BPMN message event; this node does not open a socket |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `personalAccessToken` | password | yes | Bearer PAT |
| `formId` | number | no | Default form id |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes the PAT.

## Legal

- Submissions are respondent PII (and may be PHI on HIPAA accounts). Minimize. Customer owns the Formstack org and consent records.
- Legal: https://www.formstack.com/legal
- Daily rate limits are plan-specific; `429` when the token quota is exceeded.
- Official API only (`www.formstack.com/api/v2025`). Do not scrape the builder.
- Encrypted forms may withhold field values — surface `{ error }` rather than guessing.
- `trigger.listen` is not a StudioQuack Webhook node — use a BPMN message event.
