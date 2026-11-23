# Amazon SP-API

Official Amazon SP-API node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/amazon-sp-api/`
- **Official docs:** https://developer-docs.amazon.com/sp-api/docs/orders-api-migration-guide
- **Auth:** `header` via `access_token` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `order_list` | GET | `/orders/2026-01-01/orders` | searchOrders. Query: marketplaceIds, createdAfter, fulfillmentStatuses (v0 PascalCase names kept for BC, retired on this version). |
| `order_get` | GET | `/orders/2026-01-01/orders/{orderId}` | Required: orderId |
| `catalog_get` | GET | `/catalog/2022-04-01/items/{asin}` | Required: asin |
| `inventory_get` | GET | `/fba/inventory/v1/summaries` | Required: — |
| `feed_create` | POST | `/feeds/2021-06-30/feeds` | Required: — |
| `report_get` | GET | `/reports/2021-06-30/reports/{reportId}` | Required: reportId |
| `trigger` | ingress | BPMN message event | Storefront/provider webhooks bind here; no socket. |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `access_token` | password | Secret binding |
| `region` | select | SP-API endpoint region: `na` (North America, default), `eu` (Europe/Middle East/India), `fe` (Far East). Host is `sellingpartnerapi-{region}.amazon.com`. |
| `marketplaceIds` | string | Default query `marketplaceIds` (e.g. ATVPDKIKX0DER) when INPUT omits it. |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of Amazon SP-API apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
