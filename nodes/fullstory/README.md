# FullStory

Official Fullstory **Server API V2**. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developer.fullstory.com/server/getting-started/ · Users https://developer.fullstory.com/server/users/introduction/ · Sessions https://developer.fullstory.com/server/sessions/list-sessions/
- **Base:** `https://api.fullstory.com`
- **Auth:** `Authorization: Basic {apiKey}` (V2 Users intro). Customer owns the Fullstory org and DPA.

**Session replay = highly sensitive.** This node does not download raw replay video; `session.search` returns replay URLs and `event.search` returns event JSON (Anywhere: Activation). `segment.list` is omitted (V1 Segment Export only).

`session.search` / `event.search` return replay URLs / event JSON. OfficialApiNode cannot refuse the HTTP call — select those operations only when the customer DPA covers recordings/events.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `user.get` | GET | `/v2/users/{id}` |
| `user.search` | GET | `/v2/users` |
| `session.search` | GET | `/v2/sessions` |
| `event.search` | GET | `/v2/sessions/{sessionId}/events` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `apiKey` | password | yes | Basic token |
| `timeout_seconds` | number | no | Default 25; use 60 for `event.search` |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes `apiKey`. Never log session URLs, emails, `uid`, or event bodies.

## Legal

- Terms: https://www.fullstory.com/legal/terms-of-service/
- Privacy / DPA: https://www.fullstory.com/legal/privacy-policy/ (operator must have a DPA for session data)
- Recordings may include health, financial, or authentication data if the customer’s privacy rules failed. StudioQuack must not retain replay URLs in logs or long-lived stores.
- Official API only (`api.fullstory.com`). ToS forbids scraping the replay player.
- Region follows the key prefix / login host (`app.fullstory.com` vs `app.eu1.fullstory.com`).
