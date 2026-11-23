# Shopify

Official Shopify node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/shopify/`
- **Official docs:** https://shopify.dev/docs/api/admin-graphql/2026-07
- **Auth:** `header` via `access_token` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `order_create` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `order_delete` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `order_get` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `order_list` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `order_update` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `product_create` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `product_delete` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `product_get` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `product_list` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `product_update` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `customer_get` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `inventory_adjust` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `fulfillment_create` | POST | `{shop}.myshopify.com/admin/api/{api_version}/graphql.json` | Required: query |
| `trigger` | ingress | BPMN message event | Storefront/provider webhooks bind here; no socket. |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `access_token` | password | Secret binding |
| `shop` | string | Shop subdomain only (example → example.myshopify.com). |
| `api_version` | string | Year-month Admin API version. Pin. Do not send unstable. |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of Shopify apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
