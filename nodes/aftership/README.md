# AfterShip

Official AfterShip node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/aftership/`
- **Official docs:** https://www.aftership.com/docs/tracking/quickstart/api-quick-start
- **Auth:** `header` `as-api-key` via `api_key`. Never log tokens or buyer PII.
- **Body:** Official create examples wrap fields in `{ "tracking": { ... } }`. OfficialApiNode `bodyFrom` sends top-level keys.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `tracking_create` | POST | `/trackings` | Required: — |
| `tracking_get` | GET | `/trackings/{id}` | Required: id |
| `tracking_update` | PUT | `/trackings/{id}` | Required: id |
| `courier_detect` | POST | `/couriers/detect` | Required: — |
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

Merchant ToS of AfterShip apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
