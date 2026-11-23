# Narvar

Official Narvar node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/narvar/`
- **Official docs:** https://www.narvar.com/
- **Auth:** `basic` via `api_key` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `order_push` | POST | `/orders` | Required: — |
| `shipment_push` | PUT | `/orders/{order_number}/shipments` | Required: order_number |
| `return_create` | POST | `/{returns_path}` | Required: returns_path |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `api_key` | password | Secret binding |
| `retailer_moniker` | string | Hub retailer slug. |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of Narvar apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
