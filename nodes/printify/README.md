# Printify

Official Printify node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/printify/`
- **Official docs:** https://developers.printify.com/
- **Auth:** `bearer` via `api_token` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `shop_list` | GET | `/v1/shops.json` | Required: — |
| `product_create` | POST | `/v1/shops/{shop_id}/products.json` | `title`, `blueprint_id`, `print_provider_id`, `variants`, `print_areas` |
| `product_publish` | POST | `/v1/shops/{shop_id}/products/{product_id}/publish.json` | Publish flags: title, description, images, variants, tags, keyFeatures, shipping_template |
| `order_submit` | POST | `/v1/shops/{shop_id}/orders.json` | `line_items`, `shipping_method`, `address_to`, `external_id` |
| `order_get` | GET | `/v1/shops/{shop_id}/orders/{order_id}.json` | Required: order_id |
| `trigger` | ingress | BPMN message event | Storefront/provider webhooks bind here; no socket. |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `api_token` | password | Secret binding |
| `shop_id` | string | Printify shop id. |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of Printify apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
