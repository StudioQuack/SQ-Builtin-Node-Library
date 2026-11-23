# Amazon SP-API

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/amazon-sp-api/`
- **Researched:** 2026-08-20
- **Official API:** Amazon Selling Partner API (SP-API) — Orders v2026-01-01; Catalog Items v2022-04-01; FBA Inventory v1; Feeds v2021-06-30; Reports v2021-06-30; Notifications v1
- **Docs:** https://developer-docs.amazon.com/sp-api/docs/welcome · Connect: https://developer-docs.amazon.com/sp-api/docs/connecting-to-the-selling-partner-api · Orders: https://developer-docs.amazon.com/sp-api/docs/orders-api · Catalog: https://developer-docs.amazon.com/sp-api/docs/catalog-items-api-v2022-04-01-use-case-guide · Notifications: https://developer-docs.amazon.com/sp-api/docs/notifications-api
- **Auth:** oauth2 (Login with Amazon) — secret binding: `lwa_client_id`, `lwa_client_secret`, `lwa_refresh_token`

## Scope

Seller-owned Amazon selling-partner automation: orders, catalog lookup, FBA inventory summaries, feed submit, report download, and Notifications API subscriptions. The customer owns the Seller Central / Vendor Central account and the SP-API application. **AWS IAM roles and SigV4 are not required** (removed 2023-10-02; LWA access token in `x-amz-access-token` is sufficient). **DO NOT BUILD** Seller Central HTML scraping, MWS, Orders API **v0** (`GET /orders/v0/orders` is deprecated — use v2026-01-01), or card capture (Amazon retains PCI). Notifications destinations are **SQS or EventBridge only** (not a raw HTTPS webhook). `trigger` registers that destination + subscription; to wake a StudioQuack workflow the operator bridges SQS/EventBridge to the environment message-event ingress (EventBridge API destination is an AWS product, not SP-API).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `order_list` | GET | `/orders/2026-01-01/orders` | `searchOrders`. Exactly one of `createdAfter` or `lastUpdatedAfter`. |
| `order_get` | GET | `/orders/2026-01-01/orders/{orderId}` | `getOrder`. Items are included; PII datasets via `includedData`. |
| `catalog_get` | GET | `/catalog/2022-04-01/items/{asin}` | `getCatalogItem` for one ASIN. |
| `inventory_get` | GET | `/fba/inventory/v1/summaries` | FBA `getInventorySummaries` at marketplace granularity. |
| `feed_create` | POST + PUT | `/feeds/2021-06-30/documents` then PUT `url`, then `POST /feeds/2021-06-30/feeds` | Upload a feed document (`file`) and start processing. |
| `report_get` | GET | `/reports/2021-06-30/reports/{reportId}` then `/reports/2021-06-30/documents/{reportDocumentId}` | Fetch report metadata and download the document as `file`. |
| `trigger` | POST | `/notifications/v1/destinations` and `/notifications/v1/subscriptions/{notificationType}` | Create destination + `ORDER_CHANGE` (and optionally feed/report finished) subscription. |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_list` | no | Chooses the row above |
| `lwa_client_id` | password | yes | | yes | LWA client id from app registration |
| `lwa_client_secret` | password | yes | | yes | LWA client secret (rotate per Amazon policy) |
| `lwa_refresh_token` | password | yes (seller ops) | | yes | Selling-partner refresh token. Grantless Notifications destination calls use `client_credentials` instead |
| `marketplaceIds` | string | yes | | no | Amazon marketplace id (e.g. `ATVPDKIKX0DER` US). See [Marketplace IDs](https://developer-docs.amazon.com/sp-api/docs/marketplace-ids) |
| `sp_api_endpoint` | select | yes | `na` | no | `na` → `https://sellingpartnerapi-na.amazon.com`; `eu` → `…-eu.amazon.com`; `fe` → `…-fe.amazon.com` |
| `aws_region_hint` | string | no | derived from endpoint | no | Informational only (NA `us-east-1`, EU `eu-west-1`, FE `us-west-2`). **Not used for signing** |

**Dropped vs catalog:** `AWS IAM role` is not a config param. Do not collect AWS access keys.

File payloads (upload, download, convert) use type `file` (platform ref-id),
never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `feed_document` / `report_file` are `file` |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none unless this node must invoke another node N times.

## Input fields (by operation)

### `operation = order_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `createdAfter` | string | one of created/lastUpdated | ISO-8601. Mutually exclusive with `lastUpdatedAfter` |
| `createdBefore` | string | no | ISO-8601; ≥ `createdAfter`; ≥ 2 minutes before now |
| `lastUpdatedAfter` | string | one of created/lastUpdated | ISO-8601 |
| `lastUpdatedBefore` | string | no | With `lastUpdatedAfter` |
| `fulfillmentStatuses` | string[] | no | `PENDING`, `UNSHIPPED`, `PARTIALLY_SHIPPED`, `SHIPPED`, `CANCELLED`, … |
| `fulfilledBy` | string[] | no | `MERCHANT`, `AMAZON` |
| `maxResultsPerPage` | number | no | 1–100, default 100 |
| `paginationToken` | string | no | Prior `nextToken` (expires 24h) |
| `includedData` | string[] | no | Default omit PII. `BUYER` / `RECIPIENT` need PII roles. Prefer `FULFILLMENT` without buyer address unless required |

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `orderId` | string | yes | Amazon order id |
| `includedData` | string[] | no | Same datasets as list. Request only what the workflow needs |

### `operation = catalog_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `asin` | string | yes | Catalog ASIN |
| `includedData` | string[] | no | Catalog datasets (`summaries`, `attributes`, `identifiers`, `images`, …) per Catalog Items reference |

### `operation = inventory_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `granularityType` | string | yes | Use `Marketplace` |
| `sellerSkus` | string[] | no | Filter SKUs |
| `details` | boolean | no | Include summarized quantities when true |
| `nextToken` | string | no | Pagination |

### `operation = feed_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `feedType` | string | yes | Official feed type (e.g. inventory / pricing JSON). XML listing feeds are deprecated — do not use them |
| `feed_document` | file | yes | Feed body (ref-id). Stream to Amazon’s upload `url` |
| `contentType` | string | yes | Matches `createFeedDocument` (e.g. `text/tab-separated-values; charset=UTF-8`) |
| `marketplaceIds` | string[] | no | Defaults to config `marketplaceIds` |

### `operation = report_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `reportId` | string | yes | From a prior `createReport` or notification. Starting set is **get**, not create |
| *(download)* | | | Handler follows `reportDocumentId` → document URL; decrypt if `encryptionDetails` present; store as `file` |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | string | yes | `create_destination` \| `create_subscription` \| `get_subscription` |
| `destinationName` | string | `create_destination` | Destination name |
| `sqsArn` | string | SQS dest | Customer-owned SQS queue ARN |
| `eventBridge` | object | EventBridge dest | `{ region, accountId }` per Notifications createDestination |
| `notificationType` | string | `create_subscription` | Starting: `ORDER_CHANGE`. Also allowed: `FEED_PROCESSING_FINISHED`, `REPORT_PROCESSING_FINISHED` |
| `payloadVersion` | string | no | Per notification-type docs |
| `destinationId` | string | `create_subscription` | From createDestination |
| `eventFilter` | object | no | `ORDER_CHANGE` supports `orderChangeTypes` |

Grantless token (`grant_type=client_credentials`, `scope=sellingpartnerapi::notifications`) for destination CRUD. Subscriptions that are selling-partner-scoped need the refresh-token LWA grant plus the role for that notification type.

## Output fields (by operation)

### `operation = order_list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `orders` | object[] | success | Order summaries; strip buyer/recipient from logs |
| `pagination.nextToken` | string | more pages | Pass back as `paginationToken` |

### `operation = order_get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `orderId` | string | success | Order id |
| `order` | object | success | Order including items |

### `operation = catalog_get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `asin` | string | success | ASIN |
| `item` | object | success | Catalog item |

### `operation = inventory_get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `inventorySummaries` | object[] | success | FBA summaries |
| `nextToken` | string | more pages | Pagination |

### `operation = feed_create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | Last HTTP status |
| `feedId` | string | success | Processing feed id |
| `feedDocumentId` | string | success | Uploaded document id |

### `operation = report_get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `processingStatus` | string | success | `IN_QUEUE` / `IN_PROGRESS` / `DONE` / `CANCELLED` / `FATAL` |
| `report_file` | file | `DONE` | Downloaded (and decrypted) report. Do not hold the whole body in engine vars |
| `reportDocumentId` | string | success | Document id |

### `operation = trigger`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `destinationId` | string | destination ops | Destination id |
| `subscriptionId` | string | subscription ops | Subscription id |
| `payload` | object | success | Amazon resource (no AWS account secrets) |

All operations: 4xx/5xx → `{ error }`. Never log LWA refresh tokens, client secrets, document download URLs with signatures, or buyer PII.

## Legal

- ToS / developer policy: [Policies and Agreements](https://developer-docs.amazon.com/sp-api/docs/policies-and-agreements) (Amazon Services API Developer Agreement, Data Protection Policy, Acceptable Use Policy). Public apps: Solution Provider Portal / Appstore listing rules.
- Rate limits: token-bucket per operation and selling-partner+app pair. Read `x-amzn-RateLimit-Limit` when present. 429 is retryable with backoff. Dynamic plans exist; do not hard-code RPS. Prefer notifications over polling.
- PII / residency / prohibited uses: order buyer/recipient are PII. v2026-01-01 uses **roles**, not Restricted Data Tokens, for those datasets — still minimize `includedData`. Do not scrape Seller Central. Marketplace is selected by `marketplaceIds` + regional SP-API host (NA/EU/FE). Orders older than two years are omitted except JP/AU/SG (from 2016). Customer owns the merchant account.
- Official-API-first: no scrape, no unofficial Discord/web wrapper
- PCI: no PAN/CVC. Payouts/charges stay with Amazon. If a workflow needs an external PSP, use Finance nodes (`docs/catalog/finance-accounting.md` Stripe / PayPal) — not this node.

## Implementation notes

- JDK HTTP only. Do **not** ship the Amazon SP-API Java SDK (signing is gone; SDK is unnecessary).
- LWA: POST `https://api.amazon.com/auth/o2/token` (`application/x-www-form-urlencoded`). Seller ops: `grant_type=refresh_token`. Grantless notifications: `grant_type=client_credentials&scope=sellingpartnerapi::notifications`. Access token ~3600s, max 2048 bytes. Cache in-memory for the dispatch only.
- Headers on SP-API: `x-amz-access-token`, `x-amz-date`, `user-agent` (app name/version + `Language=Java` — identify the daemon, not a fake browser).
- Timeouts: connect 10s; read 60s orders/catalog/inventory; feed PUT and report GET stream with 120s read and **size cap 50 MiB** (offload to `file`; never buffer unbounded).
- Retry: 429 / 503 with jitter, max 4; honor `Retry-After` if present. Do not retry 403 without refreshing LWA once.
- Reports: if `encryptionDetails` is present, AES-decrypt with the provided key/IV per Reports API (JDK `Cipher`), then store `file`. Never log the key.
- Error mapping: HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch.
- `user-agent` must not contain secrets.
