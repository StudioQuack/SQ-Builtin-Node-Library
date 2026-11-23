# TikTok

Official TikTok Open API (Content Posting v2, Display v2, Research v2). Handler: `OfficialApiNode` via `api_spec.json`. **Researched:** 2026-08-20.

- **Docs:** https://developers.tiktok.com/doc/content-posting-api-get-started — Guidelines: https://developers.tiktok.com/doc/content-sharing-guidelines
- **Base:** `https://open.tiktokapis.com`
- **Auth secret:** `access_token` → `Authorization: Bearer` (user `act.` for posting/display; Research `clt.` for `research_query`)

## Operations

| operation | HTTP | Path |
|---|---|---|
| `creator_info` | POST | `/v2/post/publish/creator_info/query/` |
| `direct_post_video` | POST | `/v2/post/publish/video/init/` |
| `direct_post_photo` | POST | `/v2/post/publish/content/init/` |
| `upload_inbox` | POST | `/v2/post/publish/inbox/video/init/` |
| `publish_status` | POST | `/v2/post/publish/status/fetch/` |
| `video_list` | POST | `/v2/video/list/` |
| `insights_get` | POST | `/v2/video/query/` |
| `research_query` | POST | `/v2/research/video/query/` |
| `trigger` | ingress | BPMN message start / catch — this node does not open a socket |

`video_list` query: `fields`. `media` is type `file` (platform ref-id). Photo init is PULL_FROM_URL + verified domain. Call `creator_info` before direct post. Treat provider `error.code != ok` as failure even on HTTP 200.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`creator_info`) |
| `access_token` | password | yes | Bearer token |
| `timeout_seconds` | number | no | Default 30 |

Ports: `input` / `output` only. Success `{ status, http_status, data }`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. Do not log `upload_url` query tokens or captions.

## Legal

- Direct post: https://developers.tiktok.com/doc/content-posting-api-reference-direct-post
- Status + webhooks: https://developers.tiktok.com/doc/content-posting-api-reference-get-video-status
- Inbox: https://developers.tiktok.com/doc/content-posting-api-reference-upload-video
- Display: https://developers.tiktok.com/doc/tiktok-api-v2-video-list — https://developers.tiktok.com/doc/tiktok-api-v2-video-query
- Research: https://developers.tiktok.com/doc/research-api-get-started

Unaudited Content Posting: **SELF_ONLY**, **5 publishing users / 24 h**. Research is academic-approval only. `video.init` 6 req/min; status 30 req/min. No watermarks/logos on creator media. Customer owns the TT4D app. **Official APIs only — no unofficial mobile/web scrapers.** Init is one HTTP call (no follow-up PUT to `upload_url`). `trigger` is ingress (webhooks are registered in TT4D).
