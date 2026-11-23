# LinkedIn

Official LinkedIn Community Management Posts API. Handler: `OfficialApiNode` via `api_spec.json`. Organic person/organization posts, Images initializeUpload, organization lookup, OpenID `userinfo`. **Researched:** 2026-08-22.

- **Docs:** https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/posts-api?view=li-lms-2026-08
- **Base:** `https://api.linkedin.com` — paths `/rest/posts`, `/rest/images`, `/rest/organizations/{organization_id}`, `/v2/userinfo`
- **Auth secret:** `access_token` (OAuth 2 member token) → `Authorization: Bearer`
- **Headers:** extraHeaders send `Linkedin-Version: 202608` and `X-Restli-Protocol-Version: 2.0.0` (https://learn.microsoft.com/en-us/linkedin/marketing/versioning?view=li-lms-2026-08)

`member_get` is `GET /v2/userinfo` on the same host (OpenID `openid`+`profile`). Do not call a different host prefix.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `post_create` | POST | `/rest/posts` |
| `post_get` | GET | `/rest/posts/{post_urn}` |
| `image_upload` | POST | `/rest/images?action=initializeUpload` |
| `organization_get` | GET | `/rest/organizations/{organization_id}` |
| `member_get` | GET | `/v2/userinfo` |
| `trigger` | ingress | BPMN message start / catch — this node does not open a socket |

Body (`post_create`): `commentary`, `visibility`, `lifecycle_state`, `author` (`urn:li:person:` or `urn:li:organization:`). `media` is type `file` (platform ref-id).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`post_create`) |
| `access_token` | password | yes | Bearer token |
| `timeout_seconds` | number | no | Default 30 |

Ports: `input` / `output` only. Success `{ status, http_status, data }`. Failure `{status,http_status,data}` — never includes `access_token`.

## Legal

- Marketing API Terms: https://www.linkedin.com/legal/l/marketing-api-terms
- Images: https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/images-api?view=li-lms-2026-08
- Organizations: https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/organization-lookup-api?view=li-lms-2026-08
- OpenID userinfo: https://learn.microsoft.com/en-us/linkedin/consumer/integrations/self-serve/sign-in-with-linkedin-v2
- Webhooks: https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/organization-social-action-notifications?view=li-lms-2026-08
- Versioning (`Linkedin-Version: 202608`): https://learn.microsoft.com/en-us/linkedin/marketing/versioning?view=li-lms-2026-08

Community Management is organization commercial access (vetted). Development tier: 500 calls/app/24h, 100 calls/member/24h; webhooks disabled. Store only authenticated-member data. Customer owns the developer app and token. **Official API only — do not scrape linkedin.com HTML.** No sponsored/ad campaign ops on this node.

extraHeaders send `Linkedin-Version: 202608` and `X-Restli-Protocol-Version: 2.0.0` after Bearer (https://learn.microsoft.com/en-us/linkedin/marketing/versioning?view=li-lms-2026-08). `image_upload` is initializeUpload only (no follow-up binary PUT). `trigger` is ingress, not REST subscribe.
