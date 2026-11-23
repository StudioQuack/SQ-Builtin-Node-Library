# Tumblr

Official Tumblr API **v2** (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_TUMBLR`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

- **Docs:** https://www.tumblr.com/docs/api · source https://github.com/tumblr/docs/blob/master/api.md · NPF https://www.tumblr.com/docs/npf
- **Base:** `https://api.tumblr.com/v2`
- **Auth:** StudioQuack secret `access_token` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `post_create` | POST | `/blog/{blog_identifier}/posts` body `content`, `layout`, `tags`, `state` |
| `post_get` | GET | `/blog/{blog_identifier}/posts/{post_id}` query `npf` |
| `blog_info` | GET | `/blog/{blog_identifier}/info` |
| `user_dashboard` | GET | `/user/dashboard` query `limit`, `offset` |

No `trigger`: no official webhook/event API on the pages researched 2026-08-20. Prefer NPF (`content` blocks); send `npf=true` on `post_get`. OfficialApiNode JSON-encodes `bodyFrom` (no multipart chain).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`blog_info`) |
| `access_token` | password | yes | Bearer |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. HTTP 200 with non-200 `meta.status` still appears in `data` — inspect `meta`.

## Legal

- Terms: https://www.tumblr.com/policy/terms-of-service
- User Guidelines: https://www.tumblr.com/docs/en/community_guidelines
- Published rate limits include 1,000 calls/hour and 5,000/day per consumer key; HTTP 429 Limit Exceeded. Daily windows follow Tumblr’s clocks.
- Community Guidelines forbid spam and programmatic abuse. Label mature content as required.
- Posts and dashboard feeds contain third-party works and PII. Minimize retention. Official API only — do not scrape HTML.
