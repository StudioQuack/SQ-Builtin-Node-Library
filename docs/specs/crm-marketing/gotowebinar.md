# GoToWebinar

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/gotowebinar/`
- **Researched:** 2026-08-20
- **Official API:** GoTo Webinar **REST API v2**
- **Docs:** https://developer.goto.com/GoToWebinarV2 — auth https://developer.goto.com/guides/Authentication/New_Token_Retrieval_Migration_Guide/ — refresh https://developer.goto.com/guides/Authentication/05_HOW_refreshToken — webhooks https://developer.goto.com/guides/GoToWebinar/07_HOW_WebHooksOverview/ — how-to https://developer.goto.com/guides/GoToWebinar/08_HOW_webhooks/
- **Auth:** OAuth2 — secrets: `clientSecret`, `refreshToken`. Token host `https://authentication.logmeininc.com` (GoTo / LogMeIn identity). Product scope typically `collab:`.

## Scope

One node for **GoTo Webinar** (GoTo, formerly LogMeIn). Base **`https://api.getgo.com/G2W/rest/v2`**. Organizer-scoped resources: webinars, sessions, registrants, attendees, panelists, co-organizers. Access tokens last **~60 minutes**; refresh tokens rotate on a ~30-day window (replace stored refresh when a new one is returned). Registrant/attendee emails are PII. **Customer owns the GoTo account.** Official webhooks: `registrant.added`, `registrant.joined`, `webinar.created`, `webinar.changed`, `survey.submitted`. Catalog omitted `trigger.listen`; this spec **adds** it because the provider has an official webhook API.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `webinar.create` | POST | `/organizers/{organizerKey}/webinars` | Create webinar |
| `webinar.get` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}` | One webinar |
| `webinar.list` | GET | `/organizers/{organizerKey}/webinars` | List webinars (`fromTime`/`toTime`) |
| `webinar.update` | PUT | `/organizers/{organizerKey}/webinars/{webinarKey}` | Update webinar |
| `session.get` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}/sessions/{sessionKey}` | One session |
| `session.list` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}/sessions` | Sessions for a webinar |
| `session.details` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}/sessions/{sessionKey}` | Same resource as `session.get` (full session body) |
| `registrant.create` | POST | `/organizers/{organizerKey}/webinars/{webinarKey}/registrants` | Register a person |
| `registrant.get` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}/registrants/{registrantKey}` | One registrant |
| `registrant.list` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}/registrants` | All registrants |
| `registrant.delete` | DELETE | `/organizers/{organizerKey}/webinars/{webinarKey}/registrants/{registrantKey}` | Cancel registration |
| `attendee.get` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}/sessions/{sessionKey}/attendees/{registrantKey}` | One session attendee |
| `attendee.list` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}/sessions/{sessionKey}/attendees` | Session attendees (also: GET `…/webinars/{webinarKey}/attendees` for all sessions) |
| `attendee.details` | GET | same as `attendee.get` | Join/leave/`attendanceTimeInSeconds` |
| `panelist.create` | POST | `/organizers/{organizerKey}/webinars/{webinarKey}/panelists` | Body: `[{ name, email }]` |
| `panelist.list` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}/panelists` | List panelists |
| `panelist.delete` | DELETE | `/organizers/{organizerKey}/webinars/{webinarKey}/panelists/{panelistKey}` | Remove panelist |
| `panelist.reinvite` | POST | `/organizers/{organizerKey}/webinars/{webinarKey}/panelists/{panelistKey}/resendInvitation` | Resend panelist invite |
| `coOrganizer.create` | POST | `/organizers/{organizerKey}/webinars/{webinarKey}/coorganizers` | Body: array of co-organizers |
| `coOrganizer.list` | GET | `/organizers/{organizerKey}/webinars/{webinarKey}/coorganizers` | List (original organizer omitted) |
| `coOrganizer.delete` | DELETE | `/organizers/{organizerKey}/webinars/{webinarKey}/coorganizers/{coorganizerKey}` | Query `external=true` for external co-organizers |
| `coOrganizer.reinvite` | POST | `/organizers/{organizerKey}/webinars/{webinarKey}/coorganizers/{coorganizerKey}/resendInvitation` | Query `external` as for delete |
| `trigger.listen` | POST | `/webhooks` then activate + user subscribe | Official G2W webhooks |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `webinar.list` | no | Chooses the row above |
| `clientId` | string | yes | | no | GoTo OAuth client |
| `clientSecret` | password | yes | | yes | OAuth secret |
| `refreshToken` | password | yes | | yes | Refresh token |
| `organizerKey` | string | yes | | no | Organizer key (often from token / accounts API) |
| `accountKey` | string | no | | no | Account key when the API requires it |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `webinar.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subject` | string | yes | Title |
| `description` | string | no | Description |
| `times` | object[] | yes | `{ startTime, endTime }` ISO-8601 |
| `timeZone` | string | yes | IANA zone |
| `type` | string | no | Single / sequence / series per V2 enum |
| `isPasswordProtected` | boolean | no | Gate |

### `webinar.get` / `webinar.update` / `webinar.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webinarKey` | string | get/update | Webinar key |
| `fromTime` / `toTime` | string | list | ISO range |
| `subject` / `times` / `description` | mixed | update | Fields to change |

### `session.get` / `session.list` / `session.details`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webinarKey` | string | yes | Webinar |
| `sessionKey` | string | get/details | Session |

### `registrant.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webinarKey` | string | yes | Webinar |
| `firstName` | string | yes | Given name |
| `lastName` | string | yes | Family name |
| `email` | string | yes | Email |
| `resendConfirmation` | boolean | no | Query flag |

### `registrant.get` / `registrant.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webinarKey` | string | yes | Webinar |
| `registrantKey` | string | yes | Registrant |

### `attendee.list` / `attendee.get` / `attendee.details`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webinarKey` | string | yes | Webinar |
| `sessionKey` | string | yes | Session |
| `registrantKey` | string | get/details | Attendee |

### `panelist.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webinarKey` | string | yes | Webinar |
| `panelists` | object[] | yes | `{ name, email }[]` |

### `panelist.list` / `panelist.delete` / `panelist.reinvite`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webinarKey` | string | yes | Webinar |
| `panelistKey` | string | delete/reinvite | Panelist |

### `coOrganizer.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webinarKey` | string | yes | Webinar |
| `coOrganizers` | object[] | yes | Internal: `{ external: false, organizerKey }`. External: `{ external: true, givenName, surname, email }` |

### `coOrganizer.list` / `coOrganizer.delete` / `coOrganizer.reinvite`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webinarKey` | string | yes | Webinar |
| `coorganizerKey` | string | delete/reinvite | Co-organizer key |
| `external` | boolean | delete/reinvite | Required `true` when the co-organizer has no GoTo account |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callbackUrl` | string | yes | HTTPS SQ message-event URL |
| `eventName` | string | yes | `registrant.added` \| `registrant.joined` \| `webinar.created` \| `webinar.changed` \| `survey.submitted` |
| `eventVersion` | string | no | `1.0.0` |
| `webhookSecret` | password | yes | HMAC secret created in the GoTo developer portal |

Flow (official how-to): create webhook (inactive) → activate → subscribe the organizer user. Verify `X-Webhook-Signature` on ingress. Never log the secret.

## Output fields (by operation)

### Webinar / session reads and writes

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `webinarKey` / `sessionKey` | string | when present | Keys |
| `resource` | object | success | Provider JSON (minimize logging) |
| `items` | object[] | list | Arrays |
| `error` | object | failure | `{ status, code, message }` |

### `registrant.create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | **201** |
| `registrantKey` | string | success | Key |
| `joinUrl` | string | when approved | Join link — treat as secret-ish; do not log |
| `error` | object | failure | `{ status, code, message }` |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `webhookKey` | string | success | Webhook key |
| `eventName` | string | success | Subscribed event |
| `error` | object | failure | `{ status, code, message }` |

## Legal

- GoTo legal: https://www.goto.com/company/legal
- Developer terms on https://developer.goto.com/ (confirm live ToS at build)
- API access is included with a GoTo Webinar subscription (no separate API fee in current developer materials).
- Registrant/attendee/survey payloads are PII. GDPR: customer is controller. Minimize.
- Official-API-first: `api.getgo.com` and `authentication.logmeininc.com` only.

## Implementation notes

- JDK `HttpClient` only. `Authorization: Bearer <access_token>`.
- Token POST: `https://authentication.logmeininc.com/oauth/token` with Basic `clientId:clientSecret`, `grant_type=refresh_token`.
- Timeouts: connect 10s, read 30s. Body cap 2 MiB.
- Retry: `429`/`503` backoff + jitter, max 3. Refresh once on `401`.
- Download the live OpenAPI from https://developer.goto.com/GoToWebinarV2 at implementation and pin paths (the portal is a JS app; do not scrape HTML).
- Errors → `{ error }` on OUTPUT.
- **DO NOT BUILD** in this task.
