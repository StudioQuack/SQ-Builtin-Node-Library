# Tapfiliate

Official Tapfiliate node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/tapfiliate/`
- **Official docs:** https://tapfiliate.com/docs/rest/
- **Auth:** `header` via `api_key` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `affiliate_create` | POST | `/affiliates/` | Required: — |
| `click_create` | POST | `/clicks/` | Required: — |
| `conversion_create` | POST | `/conversions/` | Required: — |
| `program_list` | GET | `/programs/` | Required: — |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `api_key` | password | Secret binding |

| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of Tapfiliate apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
