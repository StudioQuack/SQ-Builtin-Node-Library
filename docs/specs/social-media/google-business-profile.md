# Google Business Profile

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/google-business-profile/`
- **Researched:** 2026-08-20
- **Official API:** Federated **Google Business Profile APIs** (not a single monolithic GMB v4 app as the only host)
- **Docs:** https://developers.google.com/my-business · https://developers.google.com/my-business/content/overview · https://developers.google.com/my-business/content/basic-setup
- **Auth:** oauth2 — secret bindings: `client_secret`, `refresh_token`, `access_token` (`client_id` is not a secret)

## Scope

One node for Google Business Profile listings (local business presence on Search/Maps — **not** a social graph). OAuth scope **`https://www.googleapis.com/auth/business.manage`** (legacy `plus.business.manage` still accepted on some v4 methods; request the current scope).

APIs are **federated**. Enable the ones this node calls in Google Cloud Console. **New projects start at zero quota** until Google approves [API access](https://developers.google.com/my-business/content/overview). If quota is 0, skip the HTTP call with `{ error: "Google Business Profile API quota is 0. Request access and enable the APIs in Google Cloud." }`.

Do not scrape Maps HTML. Do not use unofficial GMB scrapers. Do not treat GMB API v4.9 as the only surface for locations (locations live on Business Information v1).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `account_list` | GET | `https://mybusinessaccountmanagement.googleapis.com/v1/accounts` | List accounts for the token user |
| `location_list` | GET | `https://mybusinessbusinessinformation.googleapis.com/v1/{parent=accounts/*}/locations` | Locations under an account |
| `location_get` | GET | `https://mybusinessbusinessinformation.googleapis.com/v1/{name=locations/*}` | One location (`locations/{locationId}`) |
| `review_list` | GET | `https://mybusiness.googleapis.com/v4/{parent=accounts/*/locations/*}/reviews` | Reviews for a **verified** location (pageSize max **50**) |
| `review_reply` | PUT | `https://mybusiness.googleapis.com/v4/{name=accounts/*/locations/*/reviews/*}/reply` | Create/update reply |
| `post_create` | POST | `https://mybusiness.googleapis.com/v4/{parent=accounts/*/locations/*}/localPosts` | Local post |
| `trigger` | GET / PATCH | `https://mybusinessnotifications.googleapis.com/v1/accounts/{accountId}/notificationSetting` | Pub/Sub notification setting; `ingest` for push bodies |

Reviews, local posts, and some v4 location-scoped methods remain on **`mybusiness.googleapis.com/v4`**. Account list/get: **`mybusinessaccountmanagement.googleapis.com/v1`**. Location get/list: **`mybusinessbusinessinformation.googleapis.com/v1`**. Notifications: **`mybusinessnotifications.googleapis.com/v1`**.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `location_get` | no | Chooses the row above |
| `client_id` | string | yes | empty | no | OAuth client id |
| `client_secret` | password | yes | empty | yes | OAuth client secret |
| `refresh_token` | password | yes | empty | yes | Offline access refresh. Never on INPUT. |
| `access_token` | password | no | empty | yes | Optional short-lived Bearer; handler refreshes via `refresh_token` on 401 |
| `default_account_id` | string | no | empty | no | Numeric/account resource id (`accounts/{id}`) |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never include OAuth tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = account_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_size` | number | no | Provider page size |
| `page_token` | string | no | Next page |

### `operation = location_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes if config default empty | `accounts/{id}` parent |
| `read_mask` | string | no | Field mask as documented |
| `page_size` | number | no | Page size |
| `page_token` | string | no | Cursor |
| `filter` | string | no | If Business Information documents `filter` |

### `operation = location_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `location_name` | string | yes | Resource `locations/{locationId}` |
| `read_mask` | string | no | Field mask |

### `operation = review_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes if config default empty | Account id |
| `location_id` | string | yes | Location id |
| `page_size` | number | no | Max **50** |
| `page_token` | string | no | Cursor |
| `order_by` | string | no | `rating`, `rating desc`, `updateTime desc` |

### `operation = review_reply`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes if config default empty | Account |
| `location_id` | string | yes | Location |
| `review_id` | string | yes | Review |
| `comment` | string | yes | Reply body (`ReviewReply.comment`) |

Fake or incentivized reviews via API violate Google policy — the node sends only the operator-supplied reply text.

### `operation = post_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes if config default empty | Account |
| `location_id` | string | yes | Location |
| `language_code` | string | no | LocalPost language |
| `summary` | string | yes | Post text |
| `topic_type` | string | no | e.g. `STANDARD` as documented on LocalPost |
| `call_to_action` | object | no | CTA as documented |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | string | yes | `get` \| `subscribe` \| `unsubscribe` \| `ingest` |
| `account_id` | string | yes except `ingest` | Account |
| `pubsub_topic` | string | `subscribe` | Full Cloud Pub/Sub topic name. Grant `pubsub.topics.publish` to `mybusiness-api-pubsub@system.gserviceaccount.com` |
| `notification_types` | array | no | Types from NotificationType (new reviews, Google updates, etc.) |
| `payload` | object | `ingest` | Pub/Sub push JSON (operator’s push subscription targets StudioQuack ingress) |

`unsubscribe` = PATCH with empty `pubsubTopic` and `updateMask=pubsubTopic`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `accounts` | array | `account_list` | `{ name, account_name, type }` |
| `locations` | array | `location_list` | `{ name, title }` |
| `location` | object | `location_get` | Location resource (capped) |
| `reviews` | array | `review_list` | `{ review_id, star_rating, comment, create_time, reviewer_display_name }` |
| `average_rating` | number | `review_list` | Provider average |
| `total_review_count` | number | `review_list` | Total |
| `next_page_token` | string | lists | Cursor |
| `reply` | object | `review_reply` | ReviewReply |
| `local_post` | object | `post_create` | Created LocalPost name |
| `notification_setting` | object | `trigger` get/subscribe | `{ name, pubsub_topic }` — topic name only |
| `event` | object | `trigger` ingest | Normalized notification type + resource name |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After`; else 60 / 30 |

## Legal

- [Overview / eligibility](https://developers.google.com/my-business/content/overview) · [Basic setup](https://developers.google.com/my-business/content/basic-setup) · [Reviews](https://developers.google.com/my-business/content/review-data) · [Notifications](https://developers.google.com/my-business/content/notification-setup) · [Google APIs Terms](https://developers.google.com/terms) · GBP API policies (prohibited uses — confirm live policy URL)
- Access request + Cloud project + business website URL required. Workspace orgs must have Business Profile enabled or calls return **403**.
- Reviews contain customer PII (display names, comments, photos). Minimize; do not log review `comment`.
- Rate limits are project/quota in Cloud Console (not a single public QPS on the overview page). Honor 429 / `RESOURCE_EXHAUSTED`.
- Pub/Sub is Google Cloud: operator owns the topic. StudioQuack does not create GCP projects.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. Google client libraries are **not** used.
- `Authorization: Bearer {access_token}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Refresh: `POST https://oauth2.googleapis.com/token` (`grant_type=refresh_token`). One refresh on 401, then fail.
- Empty `refresh_token` (and empty `access_token`) → `{ error }` no GBP HTTP.
- JSON cap **1_000_000** bytes. Timeout 30 s.
- Location resource names on v1 are `locations/{id}` (not `accounts/*/locations/*`). Reviews v4 still use `accounts/{accountId}/locations/{locationId}/reviews`.
- No in-handler retry loop.
- Logs: operation, account/location resource names, counts — never tokens or review text.
