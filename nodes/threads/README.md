# Threads

Official Threads API **v1.0** (Meta; not Facebook Graph). Handler: `OfficialApiNode` via `api_spec.json`. Two-step publish (container then `threads_publish`), media get, replies, media insights. **Researched:** 2026-08-22.

- **Docs:** https://developers.facebook.com/docs/threads/overview/ — Posts: https://developers.facebook.com/docs/threads/posts/
- **Base:** `https://graph.threads.net/v1.0` (equivalent host `graph.threads.com`)
- **Auth secret:** `threads_user_token` → `Authorization: Bearer`

Do not call `graph.facebook.com` for Threads objects. Instagram remains its own node.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `thread_create` | POST | `/{threads_user_id}/threads` |
| `thread_publish` | POST | `/{threads_user_id}/threads_publish` |
| `thread_get` | GET | `/{media_id}` |
| `reply_create` | POST | `/{threads_user_id}/threads` |
| `insights_get` | GET | `/{media_id}/insights` |
| `trigger` | ingress | BPMN message start / catch — this node does not open a socket |

`thread_create` body: `media_type`, `text`, `image_url`, `video_url`. `thread_publish` body: `creation_id` (container id from `thread_create`; wait ~30s for media). `reply_create` body: `media_type`, `text`, `reply_to_id`. `insights_get` query: `metric`. Meta ingest is a **public URL**.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`thread_create`) |
| `threads_user_token` | password | yes | Threads user token |
| `timeout_seconds` | number | no | Default 30 |

Ports: `input` / `output` only. Success `{ status, http_status, data }`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. Do not log post text.

## Legal

- Insights: https://developers.facebook.com/docs/threads/insights/
- Webhooks: https://developers.facebook.com/docs/threads/webhooks/
- Troubleshooting / quotas: https://developers.facebook.com/docs/threads/troubleshooting/
- Meta Platform Terms: https://developers.facebook.com/terms

**250** API-published posts / 24 h per profile. App Review + Threads permissions (`threads_basic`, `threads_content_publish`, `threads_manage_replies`, `threads_read_replies`, `threads_manage_insights`) required for live. Content and handles are personal data. Customer owns the Meta Threads app. **Official API only — do not scrape threads.net.** `trigger` is ingress (HMAC webhooks are not verified by this node).
