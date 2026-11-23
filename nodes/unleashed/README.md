# Unleashed

Official Unleashed node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/unleashed/`
- **Official docs:** https://apidocs.unleashedsoftware.com/
- **Auth:** `header` via `api_id` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `product_get` | GET | `/Products/{productGuid}` | Required: productGuid |
| `sales_order_get` | GET | `/SalesOrders/{orderGuid}` | Required: orderGuid |
| `purchase_order_get` | GET | `/PurchaseOrders/{orderGuid}` | Required: orderGuid |
| `stock_on_hand` | GET | `/StockOnHand` | Required: — |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `api_id` | password | Header `api-auth-id` |
| `client_type` | string | Required header `client-type` identifying the integration, `<account_name>/<app_name>` in lowercase without spaces (e.g. `yourcompany/studioquack`) |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of Unleashed apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
