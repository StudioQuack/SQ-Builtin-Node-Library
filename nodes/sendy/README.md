# Sendy

Official Sendy **per-install** HTTP POST API (Sendy **7.1.4**). Handler: `OfficialApiNode` via `api_spec.json`. Not a multi-tenant SaaS API. Customer hosts Sendy and Amazon SES. StudioQuack does not host Sendy and does not hold SES keys.

- **Docs:** https://sendy.co/api
- **API (2026-08-22):** Form POST to `{install_url}/subscribe`, `/unsubscribe`, `/api/subscribers/*.php`, `/api/campaigns/create.php`. Responses are mostly **plain text**.
- **Base:** `{install_url}` from config (no trailing slash, HTTPS in production).
- **Auth:** Official is POST form field `api_key` (https://sendy.co/api). Spec `auth.type=none`; every op includes `api_key` in `bodyFrom` with `bodyFormat: form`. Unsubscribe officially omits `api_key` on the public route; this node still sends it.

No official webhook API — **no `trigger.listen`**.

Set input `boolean=true` so Sendy returns plain text (`true` / error string). `silent=true` skips double opt-in — only when the operator already has consent.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `subscriber.add` | POST | `/subscribe` |
| `subscriber.unsubscribe` | POST | `/unsubscribe` |
| `subscriber.delete` | POST | `/api/subscribers/delete.php` |
| `subscriber.status` | POST | `/api/subscribers/subscription-status.php` |
| `subscriber.count` | POST | `/api/subscribers/active-subscriber-count.php` |
| `campaign.create` | POST | `/api/campaigns/create.php` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `install_url` | string | no | Customer origin (document). Placeholder base in `api_spec.json` |
| `api_key` | password | yes | Settings → API key |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Product/API: https://sendy.co/api · https://sendy.co/
- Customer accepts the Sendy license + Amazon SES acceptable use, CAN-SPAM/CASL/GDPR for list mail.
- Customer is the controller of subscriber addresses. Minimize: send only the address and list id needed.
- Official per-install API only. Do not scrape the Sendy UI.
