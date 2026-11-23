# SurveyMonkey

Official SurveyMonkey REST API **v3**. Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the SurveyMonkey account. Private app OAuth scopes; response bodies are respondent PII.

- **Docs:** https://api.surveymonkey.com/v3/docs · https://developer.surveymonkey.com/api/v3/
- **API (2026-08-20):** REST v3 at `https://api.surveymonkey.com/v3`. Surveys, collectors, responses (including `/responses/bulk`), webhooks (`POST /webhooks`).
- **Base:** `https://api.surveymonkey.com/v3`
- **Auth:** Customer supplies an OAuth **access token** as Bearer (`access_token`). This node does not run the OAuth code exchange. Scopes: `surveys_read`, `collectors_read`, `responses_read` / `responses_read_detail` (answer values), `webhooks_write` for listen registration outside ingress.

`trigger.listen` is `kind: ingress`. Create a webhook pointing at the StudioQuack message-event ingress (HEAD 200 required by the provider). The node does not open a socket. Use a BPMN message start / catch / boundary event.

`response.list` uses `/surveys/{survey_id}/responses/bulk` so answers are included when the token has `responses_read_detail`.

**Agent / tool testing:** Grant OAuth scopes `surveys_read`, `collectors_read`, `responses_read`, and `responses_read_detail` or positive reads return HTTP 403. `survey_id` is validated locally on every path op (`^[1-9][0-9]*$`). `trigger.listen` is BPMN ingress — do not invoke from the tool.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `survey.list` | GET | `/surveys` |
| `survey.get` | GET | `/surveys/{survey_id}` |
| `response.get` | GET | `/surveys/{survey_id}/responses/{response_id}` |
| `response.list` | GET | `/surveys/{survey_id}/responses/bulk` |
| `collector.list` | GET | `/surveys/{survey_id}/collectors` |
| `trigger.listen` | ingress | Webhook → BPMN message event |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | OAuth Bearer token. Never on INPUT |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer is the controller of survey responses. Minimize: request only the survey/response ids needed.
- Provider developer terms on https://developer.surveymonkey.com/api/v3/
- Official REST only. Do not scrape the builder UI.
