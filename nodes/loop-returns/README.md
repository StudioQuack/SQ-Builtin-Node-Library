# Loop Returns

Official Loop Returns node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/loop-returns/`
- **Official docs:** https://docs.loopreturns.com/api-reference/authentication
- **Auth:** `header` `X-Authorization` via `api_key`. Never log tokens or buyer PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `return_list` | GET | `/warehouse/return/list` | Required: — |
| `return_get` | GET | `/warehouse/return/details` | Required: — |
| `return_process` | POST | `/warehouse/return/{id}/process` | Required: id |
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

Merchant ToS of Loop Returns apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
