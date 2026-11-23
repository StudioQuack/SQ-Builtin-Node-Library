# Squarespace Commerce

Official Squarespace Commerce node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/squarespace/`
- **Official docs:** https://developers.squarespace.com/commerce-apis/versioning
- **Auth:** `bearer` via `api_key` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `order_list` | GET | `/1.0/commerce/orders` | Required: — |
| `order_get` | GET | `/1.0/commerce/orders/{id}` | Required: id |
| `inventory_get` | GET | `/1.0/commerce/inventory` | Required: — |
| `product_list` | GET | `/v2/commerce/products` | Required: — |
| `trigger` | ingress | BPMN message event | Storefront/provider webhooks bind here; no socket. |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `api_key` | password | Secret binding |

| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

**Agent / tool testing:** `trigger` is ingress only (not callable from the tool). `order_get` needs a real order `id` from `order_list` — empty stores block the happy path (no write op to seed orders). Blank or whitespace `id` fails local validation as missing.

## Legal

Merchant ToS of Squarespace Commerce apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
