# Printful

Official Printful node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/printful/`
- **Official docs:** https://developers.printful.com/docs/v2-beta/ · https://help.printful.com/hc/en-us/articles/10293184543260-What-should-I-know-about-Printful-s-API-v2
- **Auth:** `bearer` via `api_token`. extraHeader `X-PF-Store-Id` from config `store_id` when set (required for account-level tokens). Never log tokens, storefront passwords, or order PII.
- **Version:** **API v2 Open Beta** at `https://api.printful.com/v2`. Production-usable; Printful may still refine endpoints before GA. v1 remains available.
- **Card capture:** not this node.

## Migration

Orders, files, and shipping rates use `/v2` paths (`/orders`, `/files`, `/shipping-rates`). **Store product sync is not in v2 yet** — `sync_product` stays on v1 `POST https://api.printful.com/store/products` via per-op `baseUrl`.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `sync_product` | POST | v1 `/store/products` | Body: `sync_product` {name}, `sync_variants` |
| `order_create` | POST | `/v2/orders` | Body: `recipient`, `order_items` (alias `items`), `external_id`, `shipping` |
| `order_get` | GET | `/v2/orders/{id}` | Required: id |
| `file_upload` | POST | `/v2/files` | Body: `url` (required), `filename`, `role` |
| `shipping_rates` | POST | `/v2/shipping-rates` | Body: `recipient`, `order_items` |
| `trigger` | ingress | BPMN message event | no socket |

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `api_token` | password | Bearer private token |
| `store_id` | string | X-PF-Store-Id when set |
| `timeout_seconds` | number | Per-request timeout |

## Legal

Merchant ToS of Printful apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
