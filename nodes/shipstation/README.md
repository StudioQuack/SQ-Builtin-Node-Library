# ShipStation

Official ShipStation node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/shipstation/`
- **Official docs:** https://docs.shipstation.com/authentication
- **Auth:** `header` `API-Key` via `api_key` (StudioQuack secret). Not Bearer. Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `order_list` | GET | `/v2/shipments` | Query: page, page_size, shipment_status, batch_id |
| `shipment_create` | POST | `/v2/shipments` | Required: shipments[] (ship_to, packages, ship_from or warehouse_id). V2 has no sales-order endpoints; the former `order_create` alias of this call was removed. |
| `label_create` | POST | `/v2/labels` | shipment {carrier_id, service_code, ship_to, ship_from, packages} or rate_id / shipment_id |
| `trigger` | ingress | BPMN message event | Storefront/provider webhooks bind here; no socket. |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `api_key` | password | Secret binding |

| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of ShipStation apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
