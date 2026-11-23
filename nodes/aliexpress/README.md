# AliExpress

Official AliExpress node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/aliexpress/`
- **Official docs:** https://openservice.aliexpress.com/
- **Auth:** `query` via `access_token` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `product_query` | POST | `` | Required: — |
| `order_list` | POST | `` | Required: — |
| `affiliate_link` | POST | `` | Required: — |
| `tracking` | POST | `` | Required: — |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `access_token` | password | Secret binding |
| `app_key` | string | Open Platform app_key (query-string; input then config). |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of AliExpress apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
