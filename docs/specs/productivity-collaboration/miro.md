# Miro

- **Status:** planned
- **Catalog:** `docs/catalog/productivity-collaboration.md`
- **Folder when built:** `nodes/miro/`
- **Researched:** 2026-08-20
- **Official API:** Miro REST API **v2.0** (`https://api.miro.com`)
- **Docs:** https://developers.miro.com/reference · comparison/reference map: https://developers.miro.com/docs/rest-api-reference-guide · rate limits: https://developers.miro.com/reference/rate-limiting
- **Auth:** oauth2 — secret bindings: `client_id`, `client_secret`, `refresh_token` (password). Runtime calls use `Authorization: Bearer` with an access token minted from the refresh grant.

## Scope

One node for Miro boards and board items through the **official REST API v2**. The designer picks `operation`; the handler calls the matching `/v2/…` path with JDK HTTP. OAuth 2.0 authorization-code tokens are exchanged at the **v1 OAuth host** (unchanged across REST versions): authorize `https://miro.com/oauth/authorize`, token `https://api.miro.com/v1/oauth/token`. Required scopes: `boards:read` for gets/lists, `boards:write` for create/update/copy/shape.

**Do not build `listen`.** Experimental board webhooks (`/v2-experimental/webhooks/board_subscriptions`) were **shut down on 2025-12-05** (Miro changelog: https://developers.miro.com/changelog/removed-experimental-webhooks-support). There is no replacement production event API as of this research date. Do not poll boards as a fake webhook. Do not call REST v1 widget endpoints. Do not scrape miro.com HTML. Canvas-rich item types beyond **shape** are out of this catalog seed (sticky notes, cards, frames, etc. exist on v2 as separate paths — omit until catalogued).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `board_create` | POST | `/v2/boards` | Create a board (name, description, team, sharing policy) |
| `board_get` | GET | `/v2/boards/{board_id}` | Board metadata and policy |
| `board_update` | PATCH | `/v2/boards/{board_id}` | Rename / description / policy / team |
| `board_copy` | PUT | `/v2/boards?copy_from={board_id}` | Copy an existing board (Level 4) |
| `board_list` | GET | `/v2/boards` | Boards the token can see (cursor page) |
| `item_create_shape` | POST | `/v2/boards/{board_id}/shapes` | Add a shape item on a board |
| `item_get` | GET | `/v2/boards/{board_id}/items/{item_id}` | One item (any type the API returns) |
| `item_list` | GET | `/v2/boards/{board_id}/items` | Items on a board; optional `type` filter |

Dropped from catalog seed: **`listen`** (no official event surface after experimental webhook removal).

Base origin: `https://api.miro.com`. Do not use `https://api-gov.miro.com` unless Miro publishes a separate government host (not documented on the public v2 reference as of 2026-08-20 — **unverified**).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `board_get` | no | Chooses the row above |
| `client_id` | password | yes | empty | yes | OAuth app id. Never on INPUT, never logged. |
| `client_secret` | password | yes | empty | yes | OAuth app secret. Never on INPUT, never logged. |
| `refresh_token` | password | yes | empty | yes | Long-lived refresh credential from the OAuth grant. Handler exchanges for a short-lived access token. Never on INPUT, never logged. |
| `team_id` | string | no | empty | no | Default team for `board_create` / `board_list` when input omits it |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. Cap 120 s. |

Access tokens are stack-local only (mint per invoke or reuse within the invoke). Do not persist access tokens on the node instance.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

Shared: `board_id` is the Miro board id from the board URL (`miro.com/app/board/{board_id}/`). Reject placeholder tokens (`YOUR_`, `EXAMPLE`, `TODO`, `PLACEHOLDER`).

### `operation = board_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Board title. Trim; empty → `{ error }` no HTTP. Cap 120 chars. |
| `description` | string | no | Board description. Cap 2000 chars. |
| `team_id` | string | if config blank | Team that owns the board |
| `sharing_access` | string | no | `policy.sharingPolicy.access` as Miro documents (`private`, `view`, `comment`, `edit`, …) |
| `team_access` | string | no | `policy.sharingPolicy.teamAccess` |

### `operation = board_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board to fetch |

### `operation = board_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board to patch |
| `name` | string | no | New title |
| `description` | string | no | New description |
| `team_id` | string | no | Move to another team when the token allows |
| `sharing_access` | string | no | Sharing policy `access` |
| `team_access` | string | no | Sharing policy `teamAccess` |

At least one mutable field required; otherwise `{ error }` no HTTP.

### `operation = board_copy`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Source board (`copy_from` query) |
| `name` | string | no | Name of the copy |
| `description` | string | no | Description of the copy |
| `team_id` | string | no | Destination team |

### `operation = board_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `team_id` | string | no | Filter to a team; else config `team_id` |
| `limit` | number | no | Page size. Clamp to Miro’s documented range for this method (typically 10–50). Default 20. |
| `cursor` | string | no | Next-page cursor from a previous list |
| `query` | string | no | Title search when the endpoint documents `query` |

### `operation = item_create_shape`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Target board |
| `shape` | string | yes | Miro `data.shape` (`rectangle`, `circle`, `triangle`, `rhombus`, `round_rectangle`, …). Unknown strings are forwarded (HTTP 400). |
| `content` | string | no | Text inside the shape (`data.content`). Cap 8000 chars. |
| `x` | number | no | `position.x` (board coords). Default `0`. |
| `y` | number | no | `position.y`. Default `0`. |
| `width` | number | no | Geometry width when Miro accepts it |
| `height` | number | no | Geometry height when Miro accepts it |
| `fill_color` | string | no | `style.fillColor` (hex) |

### `operation = item_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `item_id` | string | yes | Item id |

### `operation = item_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `type` | string | no | Filter (`shape`, `sticky_note`, `card`, `text`, `frame`, …) as Miro documents |
| `parent_item_id` | string | no | List children of a parent item when the API documents it |
| `limit` | number | no | 10–50 (Miro min 10, max 50). Default 20. |
| `cursor` | string | no | Pagination cursor |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `board_id` | string | board ops / item ops | Board id |
| `board` | object | `board_create`, `board_get`, `board_update`, `board_copy` | `{ id, name, description, viewLink, createdAt, modifiedAt, team? }` — no tokens |
| `boards` | array | `board_list` | Same shape, page |
| `item_id` | string | item ops | Item id |
| `item` | object | `item_create_shape`, `item_get` | `{ id, type, data, position, geometry?, style?, createdAt, modifiedAt }` |
| `items` | array | `item_list` | Item summaries |
| `cursor` | string | list ops | Next cursor when Miro returns one |
| `error` | string | failure | Actionable; tokens redacted to `***` |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` when present; else 60 on 429, 30 on 5xx/timeout |

Do not put full board JSON dumps of every nested item on `board` output — `board_get` is metadata. Item pixels/export images are not this node.

## Legal

- Developer Terms of Use: https://miro.com/legal/developer-terms-of-use/
- App development / platform policies: https://developers.miro.com/docs/policies
- Customer terms: https://miro.com/legal/terms-of-service/
- Privacy: https://miro.com/legal/privacy-policy/
- REST reference: https://developers.miro.com/reference
- Rate limits (published 2026-03-19, “subject to change at any time”): per-user **per-application** credit budget. Method **tiers**: Level 1 = 50 credits / ~2000 rpm; Level 2 = 100 credits / ~1000 rpm; Level 3 = 500 credits / ~200 rpm; Level 4 = 2000 credits / ~50 rpm. Global **100,000 credits per minute**. Headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. HTTP **429** body `{ status, code: "tooManyRequests", message, type: "error" }`. Enterprise can request higher limits. `board_copy` is Level 4; `item_list` / shape create are Level 2.
- Production OAuth apps require Miro app review before marketplace / broad distribution. Private apps still need a registered Miro developer app.
- Board content may include personal data, confidential diagrams, and export-restricted material. Outbound HTTPS leaves the customer sandbox toward Miro. Operator is the Miro account holder and data controller for that content.
- PHI: do not put electronic protected health information on a Miro board unless the customer’s Miro contract / BAA covers it. This node adds no encryption beyond TLS to Miro.
- Official-API-first: no scrape, no undocumented private endpoints, no Web SDK from this node.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. No Miro SDK.
- Token exchange: POST `application/x-www-form-urlencoded` to `https://api.miro.com/v1/oauth/token` with `grant_type=refresh_token`, `client_id`, `client_secret`, `refresh_token`. Read `access_token` (and a rotated `refresh_token` if returned — persist only through the secret store, never logs). REST calls: `Authorization: Bearer {access_token}`, `Accept: application/json`, `Content-Type: application/json` on writes, `User-Agent: StudioQuack/1.0`.
- Empty `client_id` / `client_secret` / `refresh_token` → `{ error: "Missing Miro OAuth credentials. Bind client_id, client_secret, and refresh_token." }` — no HTTP.
- Response body cap **2_000_000** bytes. Timeout default 25 s (floor 1, cap 120). Token POST shares the same timeout.
- 429 → `{ error, http_status: 429, retry_after_seconds }` from `Retry-After` or `X-RateLimit-Reset`. 5xx / timeout → `retry_after_seconds: 30`. Other 4xx → `{ error }` with truncated body (240 chars), tokens redacted. **Do not tight-loop retry inside the handler.**
- Path-encode `board_id` / `item_id`. JSON body for create/update/shape; query `copy_from` for copy.
- Logs: operation, HTTP status, board/item ids, result counts, body sizes — **never** access tokens, refresh tokens, client secret, board item `content` text, or descriptions.
- Failures never throw past dispatch. OUTPUT is `{ error }` (and optional `http_status`).
