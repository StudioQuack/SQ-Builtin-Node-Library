# Sendcloud

Official Sendcloud node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/sendcloud/`
- **Official docs:** https://sendcloud.dev/docs/getting-started/api-version-guide · https://sendcloud.dev/docs/getting-started/migration-guidelines-for-api-v3 · https://sendcloud.dev/docs/getting-started/authentication
- **Auth:** `basic` via `public_key`:`secret_key` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Version:** **API v3** at `https://panel.sendcloud.sc/api/v3`. v2 entered maintenance mode April 2026 (parcels create closed to new users).
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Migration

| Former v2 | Current v3 |
|---|---|
| POST `/parcels` | POST `/shipments/announce` (sync create-and-announce) |
| GET `/parcels/{id}/label` | GET `/parcels/{id}/documents/label` |
| GET `/shipping_methods` | POST `/shipping-options` |

v2 request bodies (flat `name`/`address`/`shipment.id`) are not valid on v3. Use `to_address`, `parcels[]`, and `ship_with.properties.shipping_option_code`.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `parcel_create` | POST | `/shipments/announce` | Required: ship_with, to_address, from_address, parcels (1–15) |
| `label_create` | GET | `/parcels/{id}/documents/label` | Required: id. Optional accept: application/pdf \| application/zpl \| image/png |
| `shipping_method_list` | POST | `/shipping-options` | Optional from_address, to_address, parcels, functionalities |
| `trigger` | ingress | BPMN message event | Storefront/provider webhooks bind here; no socket. |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `secret_key` | password | Basic password |
| `public_key` | password | Basic username |
| `timeout_seconds` | number | Per-request timeout |

## Legal

Merchant ToS of Sendcloud apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
