# Shippo

Official Shippo node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/shippo/`
- **Official docs:** https://docs.goshippo.com/guides/authentication
- **Auth:** `Authorization: ShippoToken <api_token>` (`bearer` type + prefix `ShippoToken `). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `address_validate` | POST | `/addresses/` | Required: name, street1, city, zip, country. Optional: validate=true |
| `shipment_create` | POST | `/shipments/` | Required: address_from, address_to, parcels |
| `rate_get` | GET | `/rates/{object_id}` | Required: object_id |
| `label_create` | POST | `/transactions/` | rate (or instalabel shipment + carrier_account + servicelevel_token) |
| `track_get` | GET | `/tracks/{carrier}/{tracking_number}` | Required: carrier, tracking_number |
| `trigger` | ingress | BPMN message event | Storefront/provider webhooks bind here; no socket. |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `api_token` | password | Secret binding |

| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

**AI Agent tools:** DuckTape may map only `operation`; `ToolCatalog` still exposes the full INPUT schema. `AgentPortMapping` forwards every peer INPUT shortCode from the tool call (not only aliased fields). Nested wrappers (`payload`, `request`, `body`, …) are flattened before validation.

## Legal

Merchant ToS of Shippo apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
