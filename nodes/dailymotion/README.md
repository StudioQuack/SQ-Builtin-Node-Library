# Dailymotion

Dailymotion API v2 via `OfficialApiNode`. Docs: [API](https://developers.dailymotion.com/api/)

Auth: Bearer `access_token`.

`video_create` POSTs official form fields: `title` (required), `channel`, `published`, `tags`, `description`, `language`, `private`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `video_get` | GET | `/video/{video_id}` |
| `video_list` | GET | `/videos` |
| `video_create` | POST | `/videos` |
| `channel_get` | GET | `/profiles/{profile_id}` |
| `trigger` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `access_token` (password), `timeout_seconds`.
