# ShipStation

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/shipstation/`
- **Researched:** 2026-08-20
- **Official API:** ShipStation API V2 (default) + ShipStation API V1 (legacy, still official)
- **Docs:** https://docs.shipstation.com/api-overview ; V2 https://docs.shipstation.com/apis/openapi ; V1 https://docs.shipstation.com/apis/shipstation-v1/docs/start-here/requirements ; auth https://docs.shipstation.com/authentication
- **Auth:** api_key — secret binding: `api_key` (V2); V1 also `api_secret`

## Scope

One node for the merchant’s own ShipStation account. Official surfaces:

| Surface | Base URL | Auth | Status |
|---|---|---|---|
| **V2 (default)** | `https://api.shipstation.com/v2` | Header `API-Key` (one V2 key) | Current recommended for Standard/Premium ShipStation plans. |
| **V1 (legacy)** | `https://ssapi.shipstation.com` | HTTP Basic `api_key:api_secret` | Still supported; **deprecated**, will be removed. Catalog’s key+secret pair matches V1. |
| ShipStation API formerly ShipEngine | `https://api.shipengine.com/v1` | Separate product | **Do not build** on this node. |

V2 has **no sandbox** for ShipStation platform users; calls are production and may incur postage. V2 has no distinct “orders” resource — docs state v2 **shipments** are what v1 called orders. Map `order_list` / `order_create` to `/v2/shipments` when `api_version=v2`. Labels download as `file` (ref-id), never inline PDF/ZPL in logs. Customer owns the ShipStation merchant account. **Do not build:** Endicia Label Server as this node; scraping the ShipStation UI.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `order_list` | GET | V2: `/v2/shipments` · V1: `/orders` | List orders (V1) or shipments (V2). |
| `order_create` | POST | V2: `/v2/shipments` · V1: `/orders/createorder` | Create an order (V1) or shipment (V2). V1 upserts when `orderKey` matches. |
| `shipment_create` | POST | V2: `/v2/shipments` · V1: N/A as a distinct resource | V2: create shipment without buying postage. V1: shipments appear when a label is purchased — use `label_create`. |
| `label_create` | POST | V2: `/v2/labels` · V1: `/shipments/createlabel` or `/orders/createlabelfororder` | Buy postage; return label as `file`. |
| `trigger` | POST | V2: `/v2/environment/webhooks` · V1: `/webhooks/subscribe` | Register official webhooks (`label_created_v2`, `shipment_created_v2`, V1 store events). Inbound events are delivered through the StudioQuack engine's message-event ingress. |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_list` | no | Chooses the row above |
| `api_version` | select | no | `v2` | no | `v2` (recommended) or `v1` (legacy). |
| `api_key` | password | yes | | yes | V2: `API-Key` header. V1: Basic username. |
| `api_secret` | password | no | | yes | V1 Basic password. Required when `api_version=v1`. Unused on V2. |
| `webhook_url` | string | no | | no | HTTPS StudioQuack ingress URL for `trigger`. |
| `timeout_ms` | number | no | `120000` | no | Label buy can be slow. |

File payloads (upload, download, convert) use type `file` (platform ref-id),
never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Addresses/PII only as needed. Label output is `file`. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets. Never echo `labelData` base64. |

FUNCTION ports: none unless this node must invoke another node N times.

## Input fields (by operation)

### `operation = order_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page index. |
| `page_size` | number | no | Page size (respect provider max). |
| `order_status` | string | no | V1 filter (`awaiting_shipment`, …). |
| `create_date_start` / `create_date_end` | string | no | V1 date window (PST/PDT `yyyy-mm-dd hh:mm:ss`). |
| `shipment_status` | string | no | V2 filter (`pending`, `processing`, `label_purchased`, `cancelled`). |

### `operation = order_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_number` | string | V1 yes | V1 order number. |
| `order_key` | string | no | V1 upsert key. |
| `order_date` | string | V1 yes | V1 order date. |
| `order_status` | string | V1 yes | e.g. `awaiting_shipment`. |
| `bill_to` / `ship_to` | object | V1 yes | Name + address. |
| `items` | object[] | no | Line items. |
| `ship_to` / `ship_from` | object | V2 yes | V2 shipment addresses. |
| `packages` | object[] | V2 yes | Weight/dimensions (min 1). |
| `external_shipment_id` | string | no | V2 merchant id (max 50). |
| `carrier_id` / `service_code` | string | no | V2 carrier/service. |

### `operation = shipment_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ship_to` | object | yes | Recipient. |
| `ship_from` | object | yes | Origin. |
| `packages` | object[] | yes | Weight/dims. |
| `external_shipment_id` | string | no | Merchant id. |
| `carrier_id` / `service_code` | string | no | Optional preselect. |
| `items` | object[] | no | Line items. |
| `customs` | object | no | International. |

V1: if `api_version=v1`, return `{ error }` explaining shipments are created by `label_create` (no distinct create-shipment resource).

### `operation = label_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `shipment_id` | string | no | V2: buy for existing shipment (`POST /v2/labels/shipment` variant when only id). |
| `shipment` | object | V2 often yes | Inline shipment for `POST /v2/labels`. |
| `order_id` | number | V1 if labeling an order | `POST /orders/createlabelfororder`. |
| `carrier_code` / `service_code` / `package_code` | string | V1 yes | V1 standalone `/shipments/createlabel`. |
| `ship_date` | string | V1 yes | Ship date. |
| `weight` / `dimensions` | object | V1 often | Package. |
| `label_format` | string | no | `pdf` / `zpl` / `png` as supported. |
| `test_label` | boolean | no | V1 USPS test labels only. |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | select | no | `subscribe` (default), `list`, `unsubscribe`. |
| `event` | string | no | V2 event (e.g. `label_created_v2`). V1 webhook type + optional `store_id`. |
| `url` | string | no | HTTPS callback; defaults to `webhook_url`. |
| `webhook_id` | string | no | For unsubscribe/update. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `orders` / `shipments` | object[] | list/create | Ids, status, tracking — no extra PII in logs. |
| `order_id` / `shipment_id` | string | create | Provider id. |
| `label` | file | `label_create` | Label PDF/ZPL/PNG as platform ref-id. Never `labelData` base64 on OUTPUT. |
| `tracking_number` | string | label | Carrier tracking. |
| `label_id` | string | V2 label | `se-…` id. |
| `cost` | object | label | Postage amount/currency (not card data). |
| `webhook` | object | `trigger` | Subscription id/url/event. |
| `error` | object | failure | `{ message, http_status }` — no keys, no label bytes. |

## Legal

- ToS / developer policy URL: [ShipStation API Terms](https://www.shipengine.com/api-terms-of-service/); platform overview https://docs.shipstation.com/api-overview ; V1 requirements https://docs.shipstation.com/apis/shipstation-v1/docs/start-here/requirements
- Rate limits / quota notes (as published): **V2: 200 requests / minute** default; 429 includes `Retry-After`. Carrier/third-party limits may appear as `error_source`. **V1: 40 requests / minute** per key+secret; headers `X-Rate-Limit-Limit/Remaining/Reset`.
- PII / residency / prohibited uses: TLS 1.2+ required. Do not log buyer addresses beyond the call, or label bytes. PCI: labels are **not** card capture. Cross-ref `docs/catalog/finance-accounting.md` only if a Finance PSP node charges the buyer.
- Official-API-first: no scrape, no unofficial Discord/web wrapper

## Implementation notes

- JDK HTTP only (no vendor SDK) unless a reason is stated.
- Timeouts, size caps, retry posture. Label buy: **120s** timeout. Cap label download (suggest 5 MiB) then store as `file`. Retry 429 using `Retry-After` / `X-Rate-Limit-Reset` globally (avoid thundering herd). Do not retry successful-unknown label purchases (postage).
- Error mapping: HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch.
- V2 headers: `API-Key`, `Content-Type: application/json`. V1: `Authorization: Basic base64(key:secret)`.
- V1 `labelData` is base64 PDF: decode in-process, write `file` ref, drop the string. V2 `label_download` URLs: GET the bytes once, store as `file`, do not log the URL query secrets if present.
- V2 `trigger`: `POST /v2/environment/webhooks`. V1: `POST /webhooks/subscribe`. Verify provider signatures if documented for that version before correlating ingress.
