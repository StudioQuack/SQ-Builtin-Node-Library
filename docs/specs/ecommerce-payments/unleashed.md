# Unleashed

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/unleashed/`
- **Researched:** 2026-08-20
- **Official API:** Unleashed Software REST (`https://api.unleashedsoftware.com`) — JSON or XML via `Content-Type` / `Accept`
- **Docs:** https://apidocs.unleashedsoftware.com/ · authentication (HMAC): https://apidocs.unleashedsoftware.com/AuthenticationHelp · Products: https://apidocs.unleashedsoftware.com/Products · Sales orders: https://apidocs.unleashedsoftware.com/SalesOrders · Purchase orders: https://apidocs.unleashedsoftware.com/Purchases · Stock: https://apidocs.unleashedsoftware.com/StockOnHand · API access (account owner): https://help-unleashed.theaccessgroup.com/en/articles/11582293-unleashed-api-access
- **Auth:** Official scheme is `api_id` + HMAC-SHA256 of the query string. OfficialApiNode sends header `api-auth-id` from `api_id` only — HMAC is not applied.

## Scope

One node for Unleashed wholesale inventory / orders. The customer owns the Unleashed company (API id/key are **per company**, account-owner only). Prefer `application/json`.

**Signature scheme (implementers):**

1. Take the URL query string **without** `?` and **without** the path. Example: request `/SalesOrders?startDate=2011-01-01&endDate=2011-12-31` → sign `startDate=2011-01-01&endDate=2011-12-31`.
2. If there is **no** query string, sign the **empty string**.
3. HMAC-SHA256 with the Unleashed API key as the HMAC secret and UTF-8 query bytes as the message; **Base64** the digest. OfficialApiNode cannot compute this.
4. Send headers: `api-auth-id` = `api_id`, `api-auth-signature` = Base64 HMAC, `Content-Type` and `Accept` = `application/json`, `client-type` = `{partner}/StudioQuack` (Unleashed requires `client-type`).

Wrong signature → **403 Forbidden**.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `product_get` | GET | `/Products/{productGuid}` or `/Products` | One product by GUID, or list/filter (`productCode`, `pageSize`, …) |
| `sales_order_get` | GET | `/SalesOrders/{orderGuid}` or `/SalesOrders` | One SO by GUID, or list (`startDate`/`endDate`, `orderNumber`, …) |
| `purchase_order_get` | GET | `/PurchaseOrders/{orderGuid}` or `/PurchaseOrders` | One PO by GUID, or list |
| `stock_on_hand` | GET | `/StockOnHand` or `/StockOnHand/{productGuid}` | Warehouse quantities (`warehouseCode`, `asAtDate`, …) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `product_get` | no | Chooses the row above |
| `api_id` | password | yes | empty | yes | Header `api-auth-id` |
| `client_type` | string | no | `StudioQuack/unleashed` | no | Header `client-type` (`{account_or_partner}/{app}`) |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = product_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_guid` | string | no | If set, GET `/Products/{guid}`; else list |
| `product_code` | string | no | List filter `productCode` |
| `page` | number | no | Pagination page |
| `page_size` | number | no | Default 200 |
| `modified_since` | string | no | `YYYY-MM-DD` |
| `include_obsolete` | boolean | no | `includeObsolete` |

### `operation = sales_order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_guid` | string | no | If set, GET one SO |
| `order_number` | string | no | List filter |
| `start_date` | string | no | `startDate` |
| `end_date` | string | no | `endDate` |
| `page` | number | no | Pagination |

### `operation = purchase_order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_guid` | string | no | If set, GET one PO |
| `order_number` | string | no | Overrides other list filters when set |
| `start_date` | string | no | `startDate` |
| `end_date` | string | no | `endDate` |
| `order_status` | string | no | Parked, Placed, Complete, … |
| `page` | number | no | Pagination |

### `operation = stock_on_hand`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_guid` | string | no | GET `/StockOnHand/{guid}` (optional `/AllWarehouses` via `all_warehouses`) |
| `all_warehouses` | boolean | no | Append `/AllWarehouses` when guid set |
| `warehouse_code` | string | no | Filter |
| `as_at_date` | string | no | `asAtDate` `YYYY-MM-DD` |
| `page` | number | no | List pagination |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `item` | object | single GET | Product / SO / PO / SOH object |
| `items` | array | list | `Items` array |
| `pagination` | object | list | `NumberOfItems`, `PageNumber`, `NumberOfPages`, `PageSize` |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status (403 = bad HMAC) |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 / 30 |

## Legal

- Unleashed / The Access Group merchant terms. API credentials are account-owner scoped per company.
- Wholesale pricing and supplier data may be confidential — do not log unit costs.
- Official-API-first: no scrape of go.unleashedsoftware.com. **No partial updates** on write endpoints (out of starting ops) — Unleashed overwrites omitted fields with blank.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Canonical query string for HMAC: same order and encoding as the actual request URL. Do not include the path. Empty query → HMAC("").
- 403 → `{ error: "Unleashed rejected the HMAC signature or API id." }` — do not retry with the same string until query encoding is fixed.
- Body cap **1_000_000** bytes. Timeout default 30 s. Reduce `pageSize` if requests time out.
- Logs: operation, HTTP status, page, item counts — never secrets, signature, or cost fields.
