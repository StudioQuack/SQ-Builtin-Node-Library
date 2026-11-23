# Google Calendar

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/google-calendar/api_spec.json`.

## Overview

Official Calendar API v3. Domain-wide delegation is an operator OAuth concern; this node sends a bearer token only.

- **Official API:** Google Calendar API v3
- **Docs:** https://developers.google.com/workspace/calendar/api/v3/reference
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/google-calendar/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `availability` | POST | `https://www.googleapis.com/calendar/v3/freeBusy` | Official |
| `create_event` | POST | `https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events` | Official |
| `delete_event` | DELETE | `https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events/{eventId}` | Official |
| `get_event` | GET | `https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events/{eventId}` | Official |
| `list_events` | GET | `https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events` | Official |
| `update_event` | PATCH | `https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events/{eventId}` | Official |
| `list_calendars` | GET | `https://www.googleapis.com/calendar/v3/users/me/calendarList` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` | Errors raise a BPMN error; secrets are redacted |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | availability | no | Default official API call for this instance when input.operation is blank. EXACT accepted values: availability, create_event, delete_event, get_event, list_events, update_event,... |
| `access_token` | password | yes |  | yes | Provider credential for auth.type=bearer (configKey access_token). REQUIRED secret — StudioQuack secret binding only; never put this on INPUT. Never logged. Example: 'redacted-t... |
| `calendarId` | string | no | primary | no | Default calendarId when INPUT is empty. Accepted: primary or a calendar email / id. |
| `timeMin` | string | no |  | no | Request field for operations that declare `timeMin` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `timeZone` | string | no |  | no | IANA time zone for list_events / get_event / availability. Example: America/Chicago. |
| `items` | string | no |  | no | Request field for operations that declare `items` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `calendarExpansionMax` | number | no |  | no | freeBusy calendarExpansionMax. availability. Max 50. |
| `timeMax` | string | no |  | no | Request field for operations that declare `timeMax` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `reminders` | object | no |  | no | Event.reminders {useDefault, overrides:[{method, minutes}]}. create_event / update_event. Example: {"useDefault":true}. |
| `end` | string | no |  | no | Request field for operations that declare `end` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `conferenceData` | object | no |  | no | Event.conferenceData (Meet). Set conferenceDataVersion=1 on the query. create_event / update_event. |
| `conferenceDataVersion` | number | no |  | no | Query conferenceDataVersion. Set 1 when sending conferenceData. create_event / update_event. |
| `status` | string | no |  | no | Event.status. EXACT: confirmed / tentative / cancelled. create_event / update_event. |
| `visibility` | string | no |  | no | Event.visibility. EXACT: default / public / private / confidential. create_event / update_event. |
| `colorId` | string | no |  | no | Event.colorId (1–11). create_event / update_event. Example: 7. |
| `location` | string | no |  | no | Event.location for create_event / update_event. Example: 10 Main St, Austin. |
| `description` | string | no |  | no | Request field for operations that declare `description` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `attendees` | list | no |  | no | Event.attendees [{email, optional?, responseStatus?}]. create_event / update_event. Example: [{"email":"ada@example.com"}]. |
| `transparency` | string | no |  | no | Event.transparency. EXACT: opaque / transparent. create_event / update_event. |
| `summary` | string | no |  | no | Request field for operations that declare `summary` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `start` | string | no |  | no | Request field for operations that declare `start` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `guestsCanModify` | boolean | no |  | no | Event.guestsCanModify. create_event / update_event. |
| `sendUpdates` | string | no |  | no | Query sendUpdates for create/update/delete. EXACT: all / externalOnly / none. |
| `recurrence` | list | no |  | no | Event.recurrence RRULE/RDATE/EXDATE strings. create_event / update_event. Example: ["RRULE:FREQ=WEEKLY;COUNT=4"]. |
| `eventId` | string | no |  | no | Request field for operations that declare `eventId` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `maxAttendees` | number | no |  | no | list_events / get_event maxAttendees. |
| `q` | string | no |  | no | Request field for operations that declare `q` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `maxResults` | string | no |  | no | Request field for operations that declare `maxResults` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `updatedMin` | string | no |  | no | list_events updatedMin RFC3339. Example: 2026-01-01T00:00:00Z. |
| `singleEvents` | string | no |  | no | Request field for operations that declare `singleEvents` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `orderBy` | string | no |  | no | list_events orderBy. EXACT: startTime / updated. startTime requires singleEvents=true. |
| `showDeleted` | boolean | no |  | no | list_events showDeleted. |
| `pageToken` | string | no |  | no | Request field for operations that declare `pageToken` as a path, query, or JSON body key. Omitted/blank is dropped. Example: 'example-value'. |
| `timeout_seconds` | number | no | 25 | no | Connect+read timeout for the official HTTPS call. Positive integer. Blank/0/negative → 25. Example: 25. |

## Legal

- Terms: https://developers.google.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx raise a BPMN error (`HTTP_<status>` / `AUTH_FAILED` / `RATE_LIMITED`).
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
