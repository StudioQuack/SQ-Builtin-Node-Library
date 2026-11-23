# Stamped

Official Stamped node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/stamped/`
- **Official docs:** https://developers.stamped.io/docs/getting-started
- **Auth:** `basic` via `api_key`:`api_secret` (help-center private V2 endpoints). V2 will be deprecated once V3 Reviews is released. Never log reviewer PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `review_list` | GET | `/api/v2/{store_hash}/dashboard/reviews` | Required: — |
| `review_create` | POST | `/api/reviews3` | Required: — |
| `nps` | GET | `/api/v2/{store_hash}/dashboard/nps/` | Required: — |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `api_secret` | password | Secret binding |
| `store_hash` | string | ShopId / store hash. |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of Stamped apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
