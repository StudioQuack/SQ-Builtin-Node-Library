# monday

Official **monday.com platform GraphQL API**. extraHeaders pin `API-Version: {api_version}` (default **`2026-07`**, Current until **2026-10-01**, then `2026-10`). Maintenance `2026-04`. RC `2026-10`. https://developer.monday.com/api-reference/docs/api-versioning. **Not REST.** Sales CRM is a board template on this same API.

- **Docs:** https://developer.monday.com/api-reference/docs/getting-started · versioning https://developer.monday.com/api-reference/docs/api-versioning · GraphQL https://developer.monday.com/api-reference/docs/introduction-to-graphql · webhooks https://developer.monday.com/api-reference/reference/webhooks
- **Base:** `POST https://api.monday.com/v2`
- **Auth:** header `Authorization: {token}` (raw token, **no** `Bearer` prefix). Personal API token or OAuth `tokenV2`. Customer owns the monday account.

## Operations

All HTTP rows: `POST /v2` with JSON `{ query, variables }`. `trigger.listen` is ingress.

| operation | GraphQL (operator supplies `query`) |
|---|---|
| `board.create` | `create_board` |
| `board.get` / `board.list` | `boards` |
| `board.archive` | `archive_board` |
| `column.create` / `column.list` | `create_column` / `columns` |
| `group.create` / `group.list` / `group.delete` | `create_group` / `groups` / `delete_group` |
| `item.create` / `item.get` / `item.list` | `create_item` / `items` / `items_page` |
| `item.update` / `item.changeColumns` | `change_multiple_column_values` |
| `item.changeColumn` | `change_column_value` |
| `item.delete` / `item.move` / `item.addUpdate` | `delete_item` / `move_item_to_group` / `create_update` |
| `item.byColumn` | `items_page_by_column_values` (`items_by_column_values` **removed**) |
| `trigger.listen` | BPMN message event (`create_webhook` is not called) |

Example `board.list` INPUT: `{"query":"query { boards(limit: 25) { id name } }"}`.

`column_values` in variables must be a **JSON string** (`JSON!`).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Designer intent + required-field checks |
| `token` | password | yes | Raw Authorization token |
| `api_version` | string | no | extraHeaders `API-Version` (default `2026-07`, Current until 2026-10-01) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. HTTP 200 + GraphQL `errors[]` is still returned as `data` by OfficialApiNode (it does not map GraphQL errors to `{ error }` unless HTTP is non-2xx).

Never log `token` or `column_values`.

## Legal

- monday.com API terms / marketplace ToS for OAuth apps.
- Board items often contain PII (CRM template). Minimize queried fields.
- Complexity budgets (points/minute) and daily call caps are plan-specific. Honor 429.

## OfficialApiNode notes

- Does not inject canned GraphQL per `operation`. INPUT **must** include `query` (and `variables` as needed).
- `API-Version` header is not attached. Provider default from 2026-07-01 is `2026-07`.
- `trigger.listen`: use a BPMN message event. Ingress must echo monday `challenge` JSON on verify POST (platform ingress, not this node).
