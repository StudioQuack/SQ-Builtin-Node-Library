# Notion

- **Status:** planned
- **Catalog:** `docs/catalog/productivity-collaboration.md`
- **Folder when built:** `nodes/notion/`
- **Researched:** 2026-08-20
- **Official API:** Notion REST API `v1` (URL prefix; not the version header), pin **`Notion-Version: 2026-03-11`**
- **Docs:** https://developers.notion.com/reference
- **Auth:** `internal_token` | `oauth2` — secret binding: `integration_token` (internal/PAT) or `oauth_client_secret` + stored `oauth_access_token`

## Scope

One node for Notion pages, databases, data sources, blocks, and users. The designer picks `operation`; the handler calls the matching official REST path on `https://api.notion.com` with JDK HTTP only. Every request **must** send `Authorization: Bearer …` and `Notion-Version: 2026-03-11`. Pages, databases, and data sources are visible to the token **only after they are shared with the connection** (or granted during public OAuth). `listen` uses Notion’s official webhooks (HMAC `X-Notion-Signature`), not HTML scrape and not polling unless webhook delivery is unavailable for that environment.

Do not send `archived` on `2026-03-11` (removed; use `in_trash`). Do not use deprecated `POST /v1/databases/{id}/query` as the list path — query the **data source**. There is no separate `/v1/blocks/{id}/markdown` path; `block_get_markdown` reuses retrieve-page-markdown with a block id as documented.

## Operations (`operation` config values)

Base origin: `https://api.notion.com`. Required headers: `Authorization: Bearer {token}`, `Notion-Version: 2026-03-11`, `Content-Type: application/json` on POST/PATCH.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `page_create` | POST | `/v1/pages` | Create a page under a page parent (`parent.page_id`). Optional `markdown` body instead of `children`. |
| `page_archive` | PATCH | `/v1/pages/{page_id}` | Trash a page: body `{ "in_trash": true }`. Restore with `in_trash: false`. Permanent delete is **not** supported. |
| `page_get_markdown` | GET | `/v1/pages/{page_id}/markdown` | Full page body as enhanced markdown. |
| `page_update_markdown` | PATCH | `/v1/pages/{page_id}/markdown` | Insert/replace page content via enhanced markdown commands. |
| `page_search` | POST | `/v1/search` | Title search of pages shared with the connection (`filter.property=object`, `value=page`). |
| `database_get` | GET | `/v1/databases/{database_id}` | Database object (title, data source ids, views). Not row query. |
| `database_page_create` | POST | `/v1/pages` | Create a row: `parent.data_source_id` (preferred on 2025-09-03+) or legacy `parent.database_id` only if the workspace still uses a single-source database. |
| `database_page_get` | GET | `/v1/pages/{page_id}` | One database row (page properties). |
| `database_page_list` | POST | `/v1/data_sources/{data_source_id}/query` | Filter/sort/paginate rows in a data source. |
| `database_page_update` | PATCH | `/v1/pages/{page_id}` | Update row properties (not markdown body). |
| `block_append` | PATCH | `/v1/blocks/{block_id}/children` | Append block children. On `2026-03-11` placement uses `position`, not `after`. |
| `block_list` | GET | `/v1/blocks/{block_id}/children` | Paginated child blocks. |
| `block_get_markdown` | GET | `/v1/pages/{block_id}/markdown` | Same markdown endpoint; Notion documents `page_id` as page **or** block id for truncated subtrees. |
| `user_get` | GET | `/v1/users/{user_id}` | One user. `user_id=me` is not this path — use GET `/v1/users/me` when input `user_id` is omitted or `me`. |
| `user_list` | GET | `/v1/users` | Paginated workspace users. |
| `data_source_get` | GET | `/v1/data_sources/{data_source_id}` | Schema (properties) of one data source. |
| `data_source_search` | POST | `/v1/search` | Title search of data sources (`filter.value=data_source`). |
| `listen` | POST (inbound) | Notion webhooks to StudioQuack ingress | Official webhook events (`page.*`, `database.*`, `data_source.*`, comments). Not a scrape. |

Dropped (unofficial, retired, or superseded — do not implement):

- Body field `archived` (removed in `2026-03-11`; use `in_trash`).
- Primary list via `POST /v1/databases/{database_id}/query` (superseded by data-source query after `2025-09-03`).
- HTML scrape of `notion.so` / `app.notion.com`.
- Notion JavaScript SDK.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `page_get_markdown` | no | Chooses the row above |
| `auth_type` | select | yes | `internal_token` | no | `internal_token` (internal connection token or PAT) \| `oauth2` |
| `integration_token` | password | if `internal_token` | empty | yes | Bearer token. Never on INPUT. Never logged. |
| `oauth_client_id` | string | if `oauth2` | empty | no | Public OAuth client id |
| `oauth_client_secret` | password | if `oauth2` | empty | yes | OAuth client secret |
| `oauth_access_token` | password | if `oauth2` | empty | yes | Stored access token after code exchange (`POST /v1/oauth/token`) |
| `oauth_refresh_token` | password | if `oauth2` | empty | yes | Refresh token when Notion issued one |
| `notion_version` | string | no | `2026-03-11` | no | `Notion-Version` header. Reject unknown values; do not omit. |
| `timeout_seconds` | number | no | `25` | no | Connect + read timeout. Floor 1. Notion may take up to 60 s server-side before 503. |
| `default_page_size` | number | no | `100` | no | Pagination size for list/search/query (Notion max 100). |
| `webhook_verification_token` | password | `listen` | empty | yes | Token from Notion subscription verification payload; used to verify `X-Notion-Signature`. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No tokens |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include bearer tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = page_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `parent_page_id` | string | yes | Parent page UUID (`parent.type=page_id`) |
| `title` | string | no | Title rich-text; if omitted, properties.title or markdown H1 may supply it |
| `properties` | object | no | Page properties JSON as Notion documents |
| `markdown` | string | no | Enhanced markdown instead of `children` |
| `children` | array | no | Block objects; ignore if `markdown` set |
| `icon` | object | no | Emoji or file icon |
| `cover` | object | no | Cover file/external |

### `operation = page_archive`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_id` | string | yes | Page UUID |
| `in_trash` | boolean | no | Default `true`. `false` restores. |

### `operation = page_get_markdown` | `block_get_markdown`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_id` | string | yes for page op | Page UUID |
| `block_id` | string | yes for block op | Block UUID (alias of path id) |
| `include_transcript` | boolean | no | Meeting-note transcripts (`true`/`false`, default false) |

### `operation = page_update_markdown`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_id` | string | yes | Page UUID |
| `type` | string | yes | Markdown command: `insert_content` \| `replace_content` \| `replace_content_range` \| `update_content` (as published) |
| `content` | string | yes | Enhanced markdown payload |
| `position` | object | no | `{ "type": "start" }` or `{ "type": "end" }` for insert |
| `after` | string | no | Ellipsis selection `"start…end"`; cannot combine with `position` |
| `content_range` | string | no | Range selection for replace-range commands |
| `allow_deleting_content` | boolean | no | Required `true` if the command would delete child pages/databases |

### `operation = page_search` | `data_source_search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Title substring; empty lists all shared objects of that type |
| `start_cursor` | string | no | Opaque `next_cursor` |
| `page_size` | number | no | 1–100 |
| `in_trash` | boolean | no | Search trash when true |
| `sort` | object | no | Notion search sort object if provided |

Handler sets `filter: { property: "object", value: "page" }` or `"data_source"` from the operation. Do not invent other filter shapes.

### `operation = database_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `database_id` | string | yes | Database UUID |

### `operation = database_page_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `data_source_id` | string | yes unless `database_id` | Parent data source |
| `database_id` | string | if no data source | Resolve first data source via `database_get` then create; prefer explicit `data_source_id` |
| `properties` | object | yes | Property values keyed by name or id (title required by schema) |
| `markdown` | string | no | Initial body as markdown |
| `children` | array | no | Initial blocks if no markdown |
| `icon` | object | no | Icon |
| `cover` | object | no | Cover |

### `operation = database_page_get` | `database_page_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_id` | string | yes | Row page UUID |
| `properties` | object | `database_page_update` | Properties to PATCH |
| `icon` | object | no | Icon |
| `cover` | object | no | Cover |
| `in_trash` | boolean | no | Trash/restore the row |

### `operation = database_page_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `data_source_id` | string | yes | Data source to query |
| `filter` | object | no | Notion filter object |
| `sorts` | array | no | Sort objects |
| `start_cursor` | string | no | Opaque cursor — pass verbatim, never parse |
| `page_size` | number | no | 1–100 |
| `filter_properties` | array | no | Property ids to return (query param) |

### `operation = block_append`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `block_id` | string | yes | Parent block or page id |
| `children` | array | yes | Block objects to append (cap 100 blocks / 500 KB request) |
| `position` | object | no | `2026-03-11` placement (`start` / `end` / relative). Do not send `after`. |

### `operation = block_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `block_id` | string | yes | Parent id |
| `start_cursor` | string | no | Opaque cursor |
| `page_size` | number | no | 1–100 |

### `operation = user_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user_id` | string | no | User UUID; omit or `me` → GET `/v1/users/me` |

### `operation = user_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `start_cursor` | string | no | Opaque cursor |
| `page_size` | number | no | 1–100 |

### `operation = data_source_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `data_source_id` | string | yes | Data source UUID |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `receive` (default) \| `verify_subscription` |
| `raw_body` | string | `receive` / verify | Exact inbound JSON bytes as received (do not re-serialize before HMAC) |
| `signature_header` | string | `receive` | `X-Notion-Signature` value (`sha256=…`) |
| `event_types` | array | no | Documented types to accept (`page.content_updated`, `page.created`, `data_source.schema_updated`, …). Unknown types → `{ error }` no side effects. |

Webhook **subscription creation** is done in the Notion connection Webhooks UI (public HTTPS callback = StudioQuack ingress). This node does not scrape and does not invent a private webhook REST if Notion has not published one for that connection type.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/get/update/trash | Notion object id |
| `object` | string | most | `page` \| `database` \| `data_source` \| `user` \| `list` \| `block` |
| `url` | string | page/db | Notion URL (not a stable id — store `id`) |
| `in_trash` | boolean | page ops | Trash flag |
| `markdown` | string | markdown GET | Enhanced markdown body. Cap; never log. |
| `properties` | object | page get/update | Property values |
| `results` | array | list/search/query/block_list | Page of objects |
| `has_more` | boolean | paginated | More pages exist |
| `next_cursor` | string | paginated | Opaque; pass back as `start_cursor` |
| `title` | string | database/page | Plain title when present |
| `data_sources` | array | `database_get` | Child data source ids |
| `event_type` | string | `listen` | Notion event name |
| `entity` | object | `listen` | Event entity id/type from payload |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `notion_code` | string | JSON error body | `code` (`missing_version`, `object_not_found`, `rate_limited`, …) |
| `retry_after_seconds` | number | 429 / 529 / 5xx | From `Retry-After` or 60 / 30 fallback |

## Legal

- Developer Terms: https://www.notion.so/notion/Developer-Terms-ba4131408d0844e08330da2cbb225c20
- Terms & Privacy (API OpenAPI `termsOfService`): https://notion.notion.site/Terms-and-Privacy-28ffdd083dc3473e9c2da6ec011b58ac
- Rate limits (published): average **3 requests/second per connection**, plus a **per-workspace** cap scaled to plan. HTTP 429 / 529 with `Retry-After`. Size: 1000 blocks / 500 KB payload; rich-text `text.content` 2000 chars. https://developers.notion.com/reference/request-limits
- **Share requirement:** HTTP 404 `object_not_found` often means the page/database was not shared with the integration. Operator must share each target.
- Public OAuth distribution may require Notion marketplace / partnership review. Internal tokens are workspace-admin issued.
- PII: page bodies, properties, comments, and user emails leave the sandbox to Notion. Minimize. Do not log markdown or property values.
- Official-API-first: no scrape of Notion HTML; no unofficial export wrappers.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Always send `Notion-Version`. Missing header → Notion `400 missing_version`; handler should fail closed before the call if config version is blank.
- Ignore unknown JSON fields (additive changes apply to every version).
- Treat `next_cursor` as opaque. Never parse UUIDs out of it.
- Empty token → `{ error }` no HTTP.
- Body cap **500_000** bytes (Notion request limit). Response cap **1_000_000** bytes.
- 404: surface “share this page with the integration” in `error` without echoing the token.
- 429/529: honor `Retry-After`; do not tight-loop inside the handler (one invoke = one HTTP attempt unless the platform retry policy says otherwise).
- HMAC for `listen`: SHA-256 over **raw** body with `webhook_verification_token`; compare to `X-Notion-Signature` in constant time. Reject on mismatch.
- Logs: operation, HTTP status, result counts, sizes — never token, never page markdown, never property PII.
- Timeouts: default 25 s; Notion 503 if work exceeds ~60 s.
