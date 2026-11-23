# monday.com

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/monday/`
- **Researched:** 2026-08-20
- **Official API:** monday.com platform **GraphQL** API, current version **`2026-07`** (default from 2026-07-01; maintenance `2026-04`; RC `2026-10`). Single HTTP endpoint — **not REST**.
- **Docs:** https://developer.monday.com/api-reference/docs/getting-started · versioning https://developer.monday.com/api-reference/docs/api-versioning · GraphQL https://developer.monday.com/api-reference/docs/introduction-to-graphql · webhooks https://developer.monday.com/api-reference/reference/webhooks · items-by-column https://developer.monday.com/api-reference/reference/items-page-by-column-values
- **Auth:** personal API token or OAuth access token minted outside StudioQuack — secret binding: `token`. Header `Authorization: {token}` (no `Bearer` prefix).

## Scope

One node for monday.com boards/items. **Sales CRM is a board template** on this same API — there is no separate Sales REST. All operations POST JSON `{ query, variables }` to `https://api.monday.com/v2` with header `API-Version: 2026-07`.

Do not call monday.com as REST resource URLs. Do not scrape the product UI. Files use monday’s multipart file endpoint only if a later op is added (out of this catalog row).

## Operations (`operation` config values)

All rows: `POST https://api.monday.com/v2`. GraphQL names below.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `board.create` | POST | `create_board` | New board (`board_name`, `board_kind`, optional `workspace_id`) |
| `board.get` | POST | `boards(ids:)` | One board + columns/groups |
| `board.list` | POST | `boards(limit:)` | Page boards |
| `board.archive` | POST | `archive_board` | Archive |
| `column.create` | POST | `create_column` | Add column |
| `column.list` | POST | `boards { columns }` | Column schema |
| `group.create` | POST | `create_group` | Add group |
| `group.list` | POST | `boards { groups }` | Groups |
| `group.delete` | POST | `delete_group` | Delete group |
| `item.create` | POST | `create_item` | Create item (`column_values` JSON **string**) |
| `item.get` | POST | `items(ids:)` | One item |
| `item.list` | POST | `boards { items_page }` | Cursor page (max 500) |
| `item.update` | POST | `change_multiple_column_values` | Multi-column update (preferred) |
| `item.delete` | POST | `delete_item` | Delete item |
| `item.move` | POST | `move_item_to_group` | Move group |
| `item.changeColumn` | POST | `change_column_value` | Single column |
| `item.changeColumns` | POST | `change_multiple_column_values` | Alias of `item.update` |
| `item.addUpdate` | POST | `create_update` | Item comment/update |
| `item.byColumn` | POST | `items_page_by_column_values` | Filter by column values (`items_by_column_values` **removed**) |
| `trigger.listen` | POST | `create_webhook` | Board webhook → ingress URL |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `board.list` | no | Chooses the row above |
| `api_version` | string | no | `2026-07` | no | `API-Version` header (`YYYY-MM`) |
| `token` | password | yes | empty | yes | Personal token **or** OAuth access token minted outside StudioQuack |
| `timeout_seconds` | number | no | `30` | no | Complexity-heavy queries may need 45 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No token |
| `output` | OUTPUT | `{ status, data, … }` or `{ error }` | GraphQL `errors[]` → `{ error }` even if HTTP 200 |

FUNCTION ports: none.

## Input fields (by operation)

### `board.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_name` | string | yes | Name |
| `board_kind` | select | no | `public` \| `private` \| `share` |
| `workspace_id` | string | no | Target workspace |
| `description` | string | no | Board description |

### `board.get` | `column.list` | `group.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board id |

### `board.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Default 25, max 100 |
| `page` | number | no | If the version still pages boards this way |

### `board.archive`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board id |

### `column.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `title` | string | yes | Column title |
| `column_type` | string | yes | e.g. `text`, `status`, `date` |

### `group.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `group_name` | string | yes | Group title |

### `group.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `group_id` | string | yes | Group id string |

### `item.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `item_name` | string | yes | Item name |
| `group_id` | string | no | Target group |
| `column_values` | object | no | Map of column id → value; handler JSON-stringifies for GraphQL |

### `item.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `item_id` | string | yes | Item id |

### `item.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `limit` | number | no | 1–500 (default 25) |
| `cursor` | string | no | `items_page` / `next_items_page` cursor |

### `item.update` | `item.changeColumns`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `item_id` | string | yes | Item |
| `column_values` | object | yes | Multi-column map |

### `item.changeColumn`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `item_id` | string | yes | Item |
| `column_id` | string | yes | Column id |
| `value` | object | yes | Type-specific JSON (status `{ "label": "Done" }`) |

### `item.delete` | `item.addUpdate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `item_id` | string | yes | Item |
| `body` | string | `addUpdate` | Update HTML/text |

### `item.move`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `item_id` | string | yes | Item |
| `group_id` | string | yes | Destination group |

### `item.byColumn`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board |
| `columns` | array | yes unless `cursor` | `{ column_id, column_values[] }` |
| `limit` | number | no | Max 500 |
| `cursor` | string | paging | Do not send with `columns` |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Board (parent board for subitem events) |
| `url` | string | yes | HTTPS ingress ≤ 255 chars |
| `event` | string | yes | `WebhookEventType` e.g. `create_item`, `change_column_value` |
| `config` | object | some events | e.g. `{ "columnId": "status" }` for `change_specific_column_value` |

Ingress must echo monday `challenge` JSON on verify POST.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | GraphQL success | `ok` |
| `id` | string | mutations | Board/item/column/group/webhook id |
| `board` / `item` / `boards` / `items` / `columns` / `groups` | object/array | queries | Requested fields only |
| `cursor` | string | item.list / byColumn | Next page |
| `webhook_id` | string | trigger.listen | `create_webhook.id` |
| `complexity` | object | if queried | `before` / `after` — do not log tokens |
| `error` | string | GraphQL or HTTP failure | First `errors[].message` |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | monday complexity / daily limits |

## Legal

- monday.com API terms / marketplace ToS for OAuth apps.
- Board items often contain PII (CRM template). Minimize queried fields. Never log `column_values` or update bodies.
- Customer owns the monday account. Complexity budgets (points/minute) and daily call caps are plan-specific.
- Webhooks retry once per minute for 30 minutes (provider). Ingress must be idempotent.

## Implementation notes

- JDK `HttpClient` only. No monday SDK.
- Headers: `Authorization: {token}`, `Content-Type: application/json`, `API-Version: 2026-07`.
- Always send pinned `API-Version`. HTTP 200 + `errors` array is a failed node (`{ error }`).
- `column_values` must be a **JSON string** in the GraphQL variable (`JSON!`).
- Body cap **1_000_000** bytes. Timeout 30 s.
- Logs: operation, version, board/item ids, HTTP status — never token or column payloads.
