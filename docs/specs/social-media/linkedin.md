# LinkedIn

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/linkedin/`
- **Researched:** 2026-08-20
- **Official API:** LinkedIn Marketing / Community Management Posts API, header `Linkedin-Version: 202608` (August 2026; latest LMS moniker `li-lms-2026-08`)
- **Docs:** https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/posts-api?view=li-lms-2026-08
- **Auth:** oauth2 — secret binding: `access_token` (webhook HMAC: `client_secret`)

## Scope

One node for organic LinkedIn posts on a **person** (`urn:li:person:{id}`) or **organization** (`urn:li:organization:{id}`). Calls the versioned REST surface `https://api.linkedin.com/rest/` with `Linkedin-Version: 202608` and `X-Restli-Protocol-Version: 2.0.0`. Posts API **replaced** `ugcPosts`; do not call `/v2/ugcPosts`. Image assets use the Images API; video assets use the Videos API then Posts API. Partner/product access (Community Management API) is required for organization posting and social-action webhooks. Member identity uses current OpenID Connect `GET /v2/userinfo`, not the restricted Profile API for third-party profiles.

Do not scrape linkedin.com HTML. Do not ship sponsored/ad campaign ops on this node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `post_create` | POST | `/rest/posts` | Publish organic text / article / image / video / document post |
| `post_get` | GET | `/rest/posts/{encodedUrn}` | Fetch one post by share or ugcPost URN |
| `image_upload` | POST then PUT | `/rest/images?action=initializeUpload` then `uploadUrl` | Register + upload image bytes; returns `urn:li:image:{id}` |
| `organization_get` | GET | `/rest/organizations/{id}` | Look up a company page by numeric org id |
| `member_get` | GET | `/v2/userinfo` | Authenticated member lite profile (OpenID `openid`+`profile`) |
| `trigger` | PUT + inbound | `/rest/eventSubscriptions/(…)` + webhook | Organization social-action notifications (`LIKE`/`COMMENT`/`SHARE` on PUBLIC posts) |

Base origin: `https://api.linkedin.com`. Rest.li 2.0 encodes resource keys (`:` → `%3A`). Video files on `post_create.media` run Videos API (`/rest/videos?action=initializeUpload`, PUT parts, `/rest/videos?action=finalizeUpload`) then Posts API — not a second catalog verb. Finder `GET /rest/posts?q=author` is allowed inside `post_get` when `author_urn` is set and `post_urn` is omitted.

Retired: `ugcPosts`, unversioned `/v2/posts`, protocol 1.0 without `X-Restli-Protocol-Version`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `post_create` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | OAuth 2 member token. Header `Authorization: Bearer`. Never on INPUT. |
| `linkedin_version` | string | no | `202608` | no | `Linkedin-Version` YYYYMM. Missing header → LinkedIn error. |
| `person_urn` | string | no | empty | no | Default `urn:li:person:…` (catalog). Used when posting as the member |
| `org_urn` | string | no | empty | no | Default `urn:li:organization:…` (catalog). Used when posting as a page |
| `author_urn` | string | no | empty | no | Alias: if set, overrides `person_urn` / `org_urn` for Posts `author` |
| `client_secret` | password | no | empty | yes | App secret for LinkedIn webhook challenge HMAC-SHA256 |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s. Uploads may use 120 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = post_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `commentary` | string | yes (unless media-only as API allows) | Post body (`commentary`) |
| `author_urn` | string | no | Overrides config author |
| `visibility` | string | no | `PUBLIC` (default) or documented enum |
| `lifecycle_state` | string | no | `PUBLISHED` (default) |
| `article_url` | string | no | Article content |
| `media` | file | no | Image or video (type `file`). Image → Images API if `media_urn` absent. Video → Videos API (max **5 GB**) then post |
| `media_urn` | string | no | Existing `urn:li:image:` / `urn:li:video:` / `urn:li:document:` |

### `operation = post_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `post_urn` | string | yes if not listing | Share or ugcPost URN (URL-encode for Rest.li 2.0) |
| `author_urn` | string | yes if listing | Finder `q=author`; `count` default 10 |

### `operation = image_upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `media` | file | yes | Image bytes. Owner = `author_urn` |
| `owner_urn` | string | no | Else config `author_urn` |

### `operation = organization_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `organization_id` | string | yes | Numeric org id (not vanity unless using documented `q=vanityName`) |

### `operation = member_get`

No input fields. Uses the access token.

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | yes | `subscribe` \| `challenge` \| `event` |
| `organization_urn` | string | `subscribe` | Entity in the subscription key |
| `developer_application_urn` | string | `subscribe` | `urn:li:developerApplication:{id}` |
| `webhook_url` | string | `subscribe` | Must already be registered/validated in the developer portal |
| `challenge_code` | string | `challenge` | Query from LinkedIn GET validation |
| `payload` | object | `event` | Inbound JSON; verify per webhook docs |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `post_urn` | string | create/get | From `x-restli-id` or body `id` |
| `image_urn` | string | `image_upload` | `urn:li:image:…` |
| `organization` | object | `organization_get` | Provider JSON (capped) |
| `member` | object | `member_get` | `sub`, `name`, `given_name`, `family_name`, `picture`, optional `email` |
| `event` | object | `trigger` | Notification envelope; no secrets |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` if present |

## Legal

- Marketing API Terms: https://www.linkedin.com/legal/l/marketing-api-terms
- Posts API: https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/posts-api?view=li-lms-2026-08
- Images API: https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/images-api?view=li-lms-2026-08
- Videos API: https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/videos-api?view=li-lms-2026-08
- Organization Lookup: https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/organization-lookup-api?view=li-lms-2026-08
- OpenID userinfo: https://learn.microsoft.com/en-us/linkedin/consumer/integrations/self-serve/sign-in-with-linkedin-v2
- Social-action webhooks: https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/organization-social-action-notifications?view=li-lms-2026-08
- Webhook validation: https://learn.microsoft.com/en-us/linkedin/shared/api-guide/webhook-validation
- Versioning (latest header **202608**): https://learn.microsoft.com/en-us/linkedin/marketing/versioning?view=li-lms-2026-08
- Community Management is **organization-only commercial** access, vetted. Development tier: **500** calls/app/24h, **100** calls/member/24h; **BATCH_GET disabled**; **webhooks for social actions disabled**. Standard tier required for `trigger` push. `r_member_social` is restricted. Store only authenticated-member data per LinkedIn storing-member-data rules. No feed HTML scrape.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Every `/rest/` call: `Authorization: Bearer`, `Linkedin-Version: {linkedin_version}`, `X-Restli-Protocol-Version: 2.0.0`, `Content-Type: application/json` (except binary PUT). `GET /v2/userinfo` is OpenID (no `Linkedin-Version`). Resolve author as `author_urn` else `org_urn` else `person_urn`.
- Empty `access_token` → `{ error }` no HTTP. 201 create: read `x-restli-id`.
- JSON body cap **1_000_000** bytes. Image PUT size: follow Images API; video initialize requires `fileSizeBytes`, max **5 GB**.
- Timeouts: 30 s JSON; 120 s media PUT. 429/5xx → `{ error, http_status, retry_after_seconds }`; honor `Retry-After`; **no unbounded retry** inside the handler.
- HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. Logs: operation, status, URN ids, sizes — never tokens or commentary/PII bodies.
- `trigger` challenge: `challengeResponse = hex(HMAC-SHA256(challengeCode, client_secret))`. Development-tier apps must not claim webhook delivery.
