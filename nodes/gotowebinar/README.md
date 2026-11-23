# GoToWebinar

Official **GoTo Webinar REST API v2**. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developer.goto.com/GoToWebinarV2 · auth https://developer.goto.com/guides/Authentication/New_Token_Retrieval_Migration_Guide/ · webhooks https://developer.goto.com/guides/GoToWebinar/07_HOW_WebHooksOverview/
- **Base:** `https://api.getgo.com/G2W/rest/v2`
- **Auth:** Bearer `access_token` (customer supplies a current OAuth token; ~60 minute lifetime). Refresh at `https://authentication.logmeininc.com/oauth/token`.

Registrant/attendee emails are PII. `joinUrl` on `registrant.create` is sensitive — do not log.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `webinar.create` | POST | `/organizers/{organizerKey}/webinars` |
| `webinar.get` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}` |
| `webinar.list` | GET | `/organizers/{organizerKey}/webinars` |
| `webinar.update` | PUT | `/organizers/{organizerKey}/webinars/{webinarKey}` |
| `session.get` / `session.details` | GET | `…/sessions/{sessionKey}` |
| `session.list` | GET | `…/sessions` |
| `registrant.create` | POST | `…/registrants` |
| `registrant.get` / `registrant.delete` | GET / DELETE | `…/registrants/{registrantKey}` |
| `registrant.list` | GET | `…/registrants` |
| `attendee.get` / `attendee.details` | GET | `…/sessions/{sessionKey}/attendees/{registrantKey}` |
| `attendee.list` | GET | `…/sessions/{sessionKey}/attendees` |
| `panelist.create` | POST | `…/panelists` |
| `panelist.list` | GET | `…/panelists` |
| `panelist.delete` | DELETE | `…/panelists/{panelistKey}` |
| `panelist.reinvite` | POST | `…/panelists/{panelistKey}/resendInvitation` |
| `coOrganizer.create` | POST | `…/coorganizers` |
| `coOrganizer.list` | GET | `…/coorganizers` |
| `coOrganizer.delete` | DELETE | `…/coorganizers/{coorganizerKey}` |
| `coOrganizer.reinvite` | POST | `…/coorganizers/{coorganizerKey}/resendInvitation` |
| `trigger.listen` | — | ingress — BPMN message event |

Official webhook event names: `registrant.added`, `registrant.joined`, `webinar.created`, `webinar.changed`, `survey.submitted`. Verify `X-Webhook-Signature` on ingress.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | OAuth Bearer |
| `organizerKey` | string | no | Path organizer key |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Customer owns the GoTo account. Registrant/attendee/survey payloads are PII.
- GoTo legal: https://www.goto.com/company/legal
- Developer terms on https://developer.goto.com/
- Official API only: `api.getgo.com` and `authentication.logmeininc.com`.
- `trigger.listen` is not a StudioQuack Webhook node — use a BPMN message event.
