# Microsoft Excel

- **Status:** planned
- **Catalog:** `docs/catalog/productivity-collaboration.md`
- **Folder when built:** `nodes/microsoft-excel/`
- **Researched:** 2026-08-20
- **Official API:** Microsoft Graph REST **v1.0** Excel / workbook APIs (`workbook`, `workbookWorksheet`, `workbookTable`, `workbookRange`) plus Drive `driveItem` for list/delete of `.xlsx` files. **Not beta.**
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/excel?view=graph-rest-1.0
- **Auth:** `oauth2_delegated` (required for `/workbook/*`) | catalog also lists `entra_app_only` — **app-only cannot call Excel workbook APIs** (Graph: Application = Not supported). Secret binding: `client_secret`, `refresh_token`

## Scope

One node for Excel **workbooks stored in OneDrive for Business / SharePoint document libraries** (and Microsoft 365 group drives). `site_id` and/or `drive_id` select the store; `item_id` (or path) selects the `.xlsx` / `.xlsm` file. The designer sets `operation`; the handler calls the matching Graph v1.0 path with JDK `HttpClient` (no Graph SDK).

Excel REST supports **Office Open XML only** (`.xlsx` / `.xlsm`). `.xls` is not supported. **OneDrive consumer (personal) workbooks are not supported** by Excel REST; work/school (business) only.

`workbook_list` and `workbook_delete` are **Drive** `driveItem` calls (there is no Graph “list workbooks” Excel resource). All other verbs hit `/workbook/…`.

Do not scrape the Excel web UI. Do not call the legacy Excel Services SOAP/ODATA endpoints. This is not a mail node.

## Operations (`operation` config values)

Paths below are relative to `{graph_base_url}/v1.0`. `{item}` is the drive-item prefix in [Workbook addressing](#workbook-addressing).

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `workbook_list` | GET | `/drives/{drive_id}/root/search(q='xlsx')` (or `/me/drive/root/search(q='xlsx')`) | List Excel files in the selected drive. **Drive API**, not `/workbook`. |
| `workbook_delete` | DELETE | `{item}` | Delete the workbook **file**. **Drive API**. |
| `workbook_add_sheet` | POST | `{item}/workbook/worksheets/add` | Add a worksheet. Official: [worksheetCollection: add](https://learn.microsoft.com/en-us/graph/api/worksheetcollection-add?view=graph-rest-1.0) |
| `worksheet_list` | GET | `{item}/workbook/worksheets` | List sheets. [workbook: list worksheets](https://learn.microsoft.com/en-us/graph/api/workbook-list-worksheets?view=graph-rest-1.0) |
| `worksheet_get` | GET | `{item}/workbook/worksheets/{id\|name}` | Sheet metadata. Range values: GET `…/worksheets/{id}/usedRange(valuesOnly=true)` |
| `worksheet_append` | GET + PATCH | `…/usedRange(valuesOnly=true)` then `…/range(address='…')` | **Composite.** Append rows after used range. No native “append worksheet” RPC. |
| `worksheet_append_or_update` | GET + PATCH or append | usedRange + range PATCH | **Composite.** Match `key_column` then PATCH or append. |
| `worksheet_update` | PATCH | `{item}/workbook/worksheets/{id\|name}/range(address='{a1}')` | Write cell values. [Update range](https://learn.microsoft.com/en-us/graph/api/range-update?view=graph-rest-1.0) |
| `worksheet_clear` | POST | `{item}/workbook/worksheets/{id\|name}/range(address='{a1}')/clear` | Clear contents/formats. [Range: clear](https://learn.microsoft.com/en-us/graph/api/range-clear?view=graph-rest-1.0) |
| `worksheet_delete` | DELETE | `{item}/workbook/worksheets/{id\|name}` | Remove a sheet. [Worksheet: delete](https://learn.microsoft.com/en-us/graph/api/worksheet-delete?view=graph-rest-1.0) |
| `table_create` | POST | `{item}/workbook/tables/add` | Create a table over an A1 range. [tableCollection: add](https://learn.microsoft.com/en-us/graph/api/tablecollection-add?view=graph-rest-1.0) |
| `table_delete` | DELETE | `{item}/workbook/tables/{id\|name}` | Delete table (data cells remain). [Table: delete](https://learn.microsoft.com/en-us/graph/api/table-delete?view=graph-rest-1.0) |
| `table_get_columns` | GET | `{item}/workbook/tables/{id\|name}/columns` | List columns. [List columns](https://learn.microsoft.com/en-us/graph/api/table-list-columns?view=graph-rest-1.0) |
| `table_get_rows` | GET | `{item}/workbook/tables/{id\|name}/rows` | Page rows with `$top`/`$skip`. [List rows](https://learn.microsoft.com/en-us/graph/api/table-list-rows?view=graph-rest-1.0) |
| `table_append` | POST | `{item}/workbook/tables/{id\|name}/rows/add` | Append row(s). [TableRowCollection: add](https://learn.microsoft.com/en-us/graph/api/tablerowcollection-add?view=graph-rest-1.0) |
| `table_lookup` | GET (+ optional PATCH) | `…/tables/{id}/columns` + `…/rows` | **Composite.** First row where `lookup_column` equals `lookup_value`. Graph has no VLOOKUP RPC. |

Dropped (not in current Graph Excel v1.0 as a first-class method): none of the catalog seeds. `table_lookup`, `worksheet_append`, and `worksheet_append_or_update` are composites over official GET/PATCH/`rows/add` only.

### Workbook addressing

Resolve `{item}` in this order (input overrides config):

1. `site_id` **and** `drive_id` → `/sites/{site_id}/drives/{drive_id}/items/{item_id}`
2. `drive_id` only → `/drives/{drive_id}/items/{item_id}`
3. `site_id` only → `/sites/{site_id}/drive/items/{item_id}` (default document library)
4. Else, **delegated only** → `/me/drive/items/{item_id}`
5. Path form (optional `item_path`): `/me/drive/root:/{item_path}:` or `/drives/{drive_id}/root:/{item_path}:`

URL-encode worksheet ids that contain `{` `}` (`%7B` / `%7D`). Prefer sheet **name** in the path.

National clouds: Excel `/workbook/*` is **not** available in Microsoft Graph China (21Vianet). Global, US Gov L4, US Gov L5 (DoD) are documented as supported. Set `graph_base_url` accordingly ([deployments](https://learn.microsoft.com/en-us/graph/deployments)).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `worksheet_list` | no | Chooses the row above |
| `auth_type` | select | yes | `oauth2_delegated` | no | `oauth2_delegated` \| `entra_app_only`. App-only is valid only for `workbook_list` / `workbook_delete`. All `/workbook/*` ops require delegated. |
| `tenant_id` | string | yes | empty | no | Entra tenant GUID or `organizations`. Token URL path segment. |
| `client_id` | string | yes | empty | no | App registration id (not a password). |
| `client_secret` | password | yes | empty | yes | App secret. Token POST only. Never on INPUT, never logged. |
| `refresh_token` | password | if delegated | empty | yes | OAuth2 refresh token. Required for `oauth2_delegated`. |
| `graph_base_url` | string | no | `https://graph.microsoft.com` | no | Graph origin, no trailing slash. National clouds: `https://graph.microsoft.us`, `https://dod-graph.microsoft.us`, `https://microsoftgraph.chinacloudapi.cn`. |
| `login_base_url` | string | no | derived | no | Token origin. Default: `https://login.microsoftonline.com` (global), `https://login.microsoftonline.us` (US Gov / DoD), `https://login.chinacloudapi.cn` (China). |
| `site_id` | string | no | empty | no | SharePoint site id (`hostname,spsite-id,spweb-id` or hostname path). Selects SharePoint store. |
| `drive_id` | string | no | empty | no | Drive id (OneDrive or a specific library). |
| `item_id` | string | no | empty | no | Default workbook driveItem id. Input `item_id` overrides. |
| `persist_changes` | boolean | no | `true` | no | Excel session `persistChanges`. `false` = scratch session (not saved). |
| `timeout_seconds` | number | no | `60` | no | Connect+read timeout. Floor 1. Excel session create can be slow; 60 s default (not 25). |
| `max_rows` | number | no | `500` | no | Cap for table/worksheet row payloads per invoke. Clamp 1–5000. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. `item_id` / `site_id` / `drive_id` override config. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `client_secret`, `refresh_token`, or Bearer tokens |

FUNCTION ports: none.

## Input fields (by operation)

Common optional on every op: `item_id` (string), `item_path` (string), `site_id`, `drive_id` — override config.

### `operation = workbook_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Search `q`. Default `xlsx`. |
| `top` | number | no | `$top` page size (default 50, max 200). |

### `operation = workbook_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `item_id` | string | if config blank | DriveItem id of the workbook file. |

### `operation = workbook_add_sheet`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | no | Sheet name; omit → Excel assigns. Must be unique in the workbook. |

### `operation = worksheet_list`

No extra fields.

### `operation = worksheet_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `worksheet` | string | yes | Sheet id or name. |
| `include_values` | boolean | no | `true` → also GET `usedRange(valuesOnly=true)` (can be large). Default `false`. |

### `operation = worksheet_append`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `worksheet` | string | yes | Sheet id or name. |
| `values` | array | yes | 2D array of rows (string/number/boolean/null). Cap `max_rows`. |

### `operation = worksheet_append_or_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `worksheet` | string | yes | Sheet id or name. |
| `key_column` | number | yes | 0-based column index to match. |
| `key_value` | string | yes | Match against that column (string compare after Graph `values`). |
| `values` | array | yes | One row (1×N) to write. If no match, append. |

### `operation = worksheet_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `worksheet` | string | yes | Sheet id or name. |
| `address` | string | yes | A1 range (`A1:C10`). |
| `values` | array | yes | 2D values matching the range. |
| `formulas` | array | no | Optional 2D formulas (Graph `formulas`). |

### `operation = worksheet_clear`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `worksheet` | string | yes | Sheet id or name. |
| `address` | string | yes | A1 range to clear. |
| `apply_to` | string | no | Graph `applyTo`: `All` (default) \| `Formats` \| `Contents`. |

### `operation = worksheet_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `worksheet` | string | yes | Sheet id or name. Cannot delete the last sheet. |

### `operation = table_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `address` | string | yes | A1 including sheet, e.g. `Sheet1!A1:D20`. |
| `has_headers` | boolean | yes | Graph `hasHeaders`. |

### `operation = table_delete` | `table_get_columns`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table id or name. |
| `worksheet` | string | no | Optional worksheet qualifier in the path. |

### `operation = table_get_rows`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table id or name. |
| `top` | number | no | `$top` (default 100, max 1000). |
| `skip` | number | no | `$skip` for paging. |

### `operation = table_append`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table id or name. |
| `values` | array | yes | 2D rows. Batch many rows in **one** call (Graph guidance). Cap `max_rows`. |
| `index` | number | no | Insert index; omit → append at end. |

### `operation = table_lookup`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table id or name. |
| `lookup_column` | string | yes | Column name (preferred) or 0-based index as string digits. |
| `lookup_value` | string | yes | Value to match (stringified). |
| `return_columns` | array | no | Column names to include; omit → full row. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `workbooks` | array | `workbook_list` | `{ id, name, web_url, size, last_modified }` for Excel files only |
| `item_id` | string | delete / most writes | DriveItem id |
| `worksheet` | object | add/get sheet | `{ id, name, position, visibility }` |
| `worksheets` | array | `worksheet_list` | Same shape |
| `address` | string | range/usedRange | Graph range address |
| `values` | array | get/append/update/lookup | 2D cell values |
| `updated` | boolean | `worksheet_append_or_update` | `true` if matched existing row |
| `appended` | boolean | `worksheet_append_or_update` | `true` if a new row was written |
| `table` | object | `table_create` | `{ id, name, showHeaders, style }` |
| `columns` | array | `table_get_columns` | `{ id, name, index }` |
| `rows` | array | `table_get_rows` / `table_append` | `{ index, values }` |
| `row` | object | `table_lookup` | Matched row or omitted if none |
| `found` | boolean | `table_lookup` | Whether a row matched |
| `error` | string | failure | Actionable; secrets redacted to `***` |
| `http_status` | number | HTTP error | Graph status |
| `retry_after_seconds` | number | 429 / 504 / timeout | Honor `Retry-After` if present; else 60 on 429, 30 on 5xx/timeout |

## Legal

- Microsoft APIs Terms of Use: https://learn.microsoft.com/en-us/legal/microsoft-apis/terms-of-use
- Microsoft Graph / identity: operator must register an Entra app and accept those terms. Graph tokens are Microsoft identity platform tokens.
- Scopes (delegated, least privilege): `Files.ReadWrite` for `/workbook/*`. SharePoint-hosted files may need `Sites.ReadWrite.All` or **prefer** `Sites.Selected` (admin grants the app to that site). **`Files.ReadWrite.All` is high privilege** — do not request it unless the customer’s tenant admin requires tenant-wide file access.
- App-only: Excel workbook APIs document **Application: Not supported**. Do not mint client-credentials tokens expecting `/workbook` to work.
- Workbook cells may contain PII / PHI. Traffic leaves the daemon to Graph. Customer is data controller. Do not log cell `values`.
- Throttling: https://learn.microsoft.com/en-us/graph/throttling — HTTP 429 + `Retry-After`. Excel `rows/add`, `tables/add`, and `createSession` may return **504**; Graph says repeat the request.
- China cloud: Excel REST ❌. Do not call `/workbook` there.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER` except following Graph `302` is not used here (Excel JSON). No Microsoft Graph SDK.
- **Token (delegated):** `POST {login_base_url}/{tenant_id}/oauth2/v2.0/token` with `grant_type=refresh_token`, `client_id`, `client_secret`, `refresh_token`, `scope={graph_base_url}/.default`. Cache access token in-memory per invoke only (not on the instance).
- **Token (app-only):** `grant_type=client_credentials`, same `scope`. Use only for Drive list/delete.
- Empty `client_secret` / missing `refresh_token` when delegated → `{ error }` and **no HTTP**.
- Excel session (recommended for writes): `POST {item}/workbook/createSession` body `{ "persistChanges": true }` → header `Workbook-Session-Id` on subsequent calls. Official: https://learn.microsoft.com/en-us/graph/api/workbook-createsession?view=graph-rest-1.0 . Persistent session idle ~5 minutes. On 404 session expired, create a new session once. `POST {item}/workbook/closeSession` when the invoke finishes.
- `Prefer: respond-async` on createSession if Graph returns 202; poll `Location` (workbook operations). Cap poll loops (e.g. 20 × 1 s).
- Worksheet append: GET `usedRange(valuesOnly=true)`; if empty, start at `A1`; else next row = `rowIndex + rowCount`; PATCH that A1 window. Do not load the entire sheet without `valuesOnly`.
- `table_lookup`: GET columns (map name→index), page rows with `$top=100` until match or exhausted. Do not download unbounded tables; respect `max_rows` then `{ error: "Table exceeds max_rows; narrow the table or raise max_rows." }`.
- JSON body cap **1_000_000** bytes. `values` row cap = `max_rows`.
- 429 → `{ error, http_status: 429, retry_after_seconds }` from `Retry-After` (default 60). **Do not tight-loop 429.** One retry allowed only for documented Excel **504** on createSession / tables/add / rows/add.
- Logs: operation, item id, sheet/table names, status, sizes. Never tokens, never cell contents.
- Redact `client_secret`, `refresh_token`, `access_token` from error strings (`***`).
