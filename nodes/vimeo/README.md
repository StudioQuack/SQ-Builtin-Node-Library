# Vimeo

Official Vimeo REST API **3.4**. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developer.vimeo.com/api/guides/start · https://developer.vimeo.com/api/reference · https://developer.vimeo.com/api/changelog
- **Base:** `https://api.vimeo.com`
- **Auth:** `Authorization: Bearer {access_token}` (prefix `Bearer `; Vimeo examples often show lowercase `bearer `). Never on INPUT.
- **Researched:** 2026-08-22

extraHeaders send `Accept: application/vnd.vimeo.*+json;version=3.4` (https://developer.vimeo.com/api/common-formats). Folders are `/me/projects`.

**Input normalization:** `video_get` strips a leading `/videos/` from `video_id` before calling the API. `video_list` / `folder_list` validate `per_page` (1–100) and `page` (1–10000) locally. `video_upload` requires `name` before auth.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `video_get` | GET | `/videos/{video_id}` |
| `video_list` | GET | `/me/videos` |
| `video_upload` | POST | `/me/videos` (placeholder JSON — tus binary is out of band) |
| `folder_list` | GET | `/me/projects` |
| `trigger` | ingress | BPMN message start / catch — this node does not open a socket |

`video_upload` accepts `name` / `description`. OfficialApiNode does **not** stream tus bytes.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer token |
| `timeout_seconds` | number | no | Default 60 |

Ports: `input` / `output` only. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- API terms: https://developer.vimeo.com/guidelines/terms
- Customer owns the Vimeo app and token. Official API only — do not scrape vimeo.com.

## Upload

`video_upload` creates the video and returns a tus `upload_link`. Binary upload is a second HTTP call (tus) the engine does not perform.
