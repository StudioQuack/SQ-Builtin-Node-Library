# Facebook Pages

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/facebook-pages/`
- **Researched:** 2026-08-20
- **Official API:** Meta Graph API **v26.0** (released 2026-07-29) — Page Feed, Page Insights, Leadgen Forms, Page webhooks
- **Docs:** https://developers.facebook.com/docs/graph-api/changelog/version26.0/ — Page Feed: https://developers.facebook.com/docs/graph-api/reference/page/feed/
- **Auth:** oauth2 (Page access token) — secret binding: `page_access_token` (`app_secret` for webhook HMAC)

## Scope

One node for a **Facebook Page** on Graph `https://graph.facebook.com/{version}/`. Default version **v26.0**. Covers generic Graph GET/POST/DELETE (Pages paths only), Page feed publish/list, Page/post insights, Lead Ads form listing, and Page webhook listen (`feed`, `leadgen`, …). **Instagram remains the shipped Instagram node** — do not add IG professional media ops here.

Do not scrape facebook.com. Do not call Commerce Order Management (removed on v26.0+, 47 endpoints, no replacement). Do not use a vendor Graph SDK.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `graph_get` | GET | `/{path}` | Read a Page-scoped Graph object (allowlisted path) |
| `graph_post` | POST | `/{path}` | Write a Page-scoped Graph edge |
| `graph_delete` | DELETE | `/{object-id}` | Delete a Page-owned object the token may delete |
| `page_post_create` | POST | `/{page-id}/feed` (photos: `/{page-id}/photos`) | Publish as the Page; `message` or `link` required for feed |
| `page_feed_get` | GET | `/{page-id}/feed` | List Page posts (~600 ranked published posts/year) |
| `insights_get` | GET | `/{page-id}/insights` or `/{page-post-id}/insights` | Page or post metrics |
| `lead_list` | GET | `/{page-id}/leadgen_forms` then `/{form-id}/leads` | List Lead Ads forms and/or leads |
| `trigger` | GET verify + POST events | App webhook + `POST /{page-id}/subscribed_apps` | Page `feed` / `leadgen` (and documented Page fields) |

Host: `graph.facebook.com` only. `graph_get`/`graph_post` must reject paths that target Instagram, User personal feed, or non-Page ads-account trees. Unpublished posts appear on `/feed`; use `is_published` to filter.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `page_post_create` | no | Chooses the row above |
| `page_access_token` | password | yes | empty | yes | Page token (`pages_manage_posts`, `pages_read_engagement`, `pages_manage_metadata`, `leads_retrieval` as needed). Never on INPUT. |
| `app_secret` | password | no | empty | yes | HMAC key for `X-Hub-Signature-256` and webhook verify |
| `page_id` | string | yes | empty | no | Default Page id |
| `version` | string | no | `v26.0` | no | Graph version prefix. Do not default below v26. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = graph_get` | `graph_post` | `graph_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes | Graph path after version, e.g. `{page-id}/feed`. No scheme/host. Reject `..`, `http`, and non-allowlisted prefixes |
| `fields` | string | no | Graph `fields=` (GET) |
| `body` | object | `graph_post` | JSON/form fields (no access_token key) |
| `object_id` | string | `graph_delete` | Object to delete |

### `operation = page_post_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message` | string | yes unless `link` | Status text |
| `link` | string | yes unless `message` | URL; Graph must be able to fetch it |
| `published` | boolean | no | `false` → unpublished post |
| `scheduled_publish_time` | number | no | Unix time; 10 minutes–75 days out |

### `operation = page_feed_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `fields` | string | no | Default `id,message,created_time,permalink_url,is_published` |
| `limit` | number | no | Page size; clamp to Graph paging |
| `after` | string | no | Cursor |

### `operation = insights_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `object_id` | string | no | Page id (default config) or `{page-id}_{post-id}` |
| `metric` | string | yes | Comma-separated metrics still valid on **v26.0**. Do not send metrics Meta marked invalid after **2026-06-15**. `page_impressions_unique` is deprecated above v25 — omit on v26 |
| `period` | string | no | `day` \| `week` \| `days_28` \| `lifetime` as documented |
| `since` / `until` | string | no | Unix or date as Graph accepts |

### `operation = lead_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | no | If set, `GET /{form-id}/leads`; else list forms |
| `fields` | string | no | Form/lead field projection |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | yes | `subscribe` \| `verify` \| `event` |
| `object` | string | no | `page` (default). `leadgen` is a Page field, not a second node |
| `subscribed_fields` | string | `subscribe` | e.g. `feed,leadgen` |
| `hub_mode` / `hub_verify_token` / `hub_challenge` | string | `verify` | Graph webhook handshake |
| `payload` | object | `event` | Raw JSON; verify `X-Hub-Signature-256` with `app_secret` over **raw** bytes |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/delete | Graph object id |
| `data` | array/object | GET | Provider body (capped). Paging `cursors` preserved |
| `leads` | array | `lead_list` | Lead records (PII) — do not log |
| `event` | object | `trigger` | Parsed `entry[].changes[]` |
| `error` | string | failure | Graph `error.message` truncated; no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` / usage headers |

## Legal

- Platform Terms: https://developers.facebook.com/terms
- Commercial Terms: https://www.facebook.com/legal/commercial_terms
- Graph overview: https://developers.facebook.com/docs/graph-api/
- v26.0 changelog: https://developers.facebook.com/docs/graph-api/changelog/version26.0/
- Page Feed: https://developers.facebook.com/docs/graph-api/reference/page/feed/
- Page Insights: https://developers.facebook.com/docs/graph-api/reference/insights/
- Leadgen forms: https://developers.facebook.com/docs/graph-api/reference/page/leadgen_forms/
- Page webhooks: https://developers.facebook.com/docs/graph-api/webhooks/reference/page/
- Leadgen webhooks: https://developers.facebook.com/docs/graph-api/webhooks/getting-started/webhooks-for-leadgen/
- Operator needs a Meta app, Page token with the right tasks (`CREATE_CONTENT`, `ADVERTISE` for leads), and App Review for live. Lead payloads are PII (email, phone); minimize, do not log, lawful basis required (GDPR). Rate limits: Platform + Page usage headers (`x-app-usage`, `x-page-usage`); 429 must not tight-loop. No personal-profile scrape; no session cookies.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. Host allowlist: `graph.facebook.com`.
- Auth as query `access_token` **or** header; never echo token on OUTPUT.
- Empty `page_access_token` → `{ error }` no HTTP. Response body cap **1_000_000** bytes. Timeout 25 s (photos/videos 120 s).
- v26 protocol: do not send `date_format`, `pretty`, `debug`, or `GET /?ids=`. Compact JSON.
- 429/5xx → `{ error, http_status, retry_after_seconds }`; honor `Retry-After`; **no unbounded retry**. Other 4xx → `{ error }` from Graph error object.
- HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. Logs: operation, page_id, status, sizes — never tokens, lead fields, or message bodies.
- Webhook: compare HMAC-SHA256(app_secret, raw body) to `X-Hub-Signature-256` (`sha256=`). Self-signed callback certs are not supported by Meta.
