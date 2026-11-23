# Pinterest

Official Pinterest API **v5** for organic pins, boards, and the authenticated user account. Handler: `OfficialApiNode` via `api_spec.json`. **Researched:** 2026-08-20.

- **Docs:** https://developers.pinterest.com/docs/api/v5/introduction/
- **Auth:** https://developers.pinterest.com/docs/getting-started/set-up-authentication-and-authorization/
- **Base:** `https://api.pinterest.com/v5`
- **Auth secret:** `access_token` → `Authorization: Bearer`

## Operations

| operation | HTTP | Path |
|---|---|---|
| `pin_create` | POST | `/pins` |
| `pin_get` | GET | `/pins/{pin_id}` |
| `board_create` | POST | `/boards` |
| `board_list` | GET | `/boards` |
| `user_get` | GET | `/user_account` |
| `trigger` | ingress | BPMN message start / catch — no official organic pin webhook |

`pin_create` body: `board_id`, `title`, `description`, `link`. `board_create` body: `name`, `description`, `privacy`. `board_list` query: `page_size`, `bookmark`.

Scopes: `boards:read`, `boards:write`, `pins:read`, `pins:write`, `user_accounts:read`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`pin_create`) |
| `access_token` | password | yes | Bearer token |
| `timeout_seconds` | number | no | Default 25 |

Ports: `input` / `output` only. Success `{ status, http_status, data }`. Failure `{ error }` — never includes `access_token`. Do not log pin descriptions (may contain PII).

## Legal

- Developer & API Terms: https://developers.pinterest.com/terms/
- Developer Guidelines: https://policy.pinterest.com/developer-guidelines/
- Access tiers: https://developers.pinterest.com/docs/key-concepts/access-tiers/
- Rate limits: https://developers.pinterest.com/docs/reference/rate-limits/
- Pin create: https://developers.pinterest.com/docs/api/v5/pins-create/

Trial: **1000 req/day/app**; Trial-created pins/boards are not publicly visible. Standard: **100 req/s/user/app**. Honor `x-ratelimit-*`. Ads/conversion scopes are out of this node. Customer owns the Pinterest app. **Official API only — do not scrape pinterest.com or collect passwords/session cookies.** `trigger` is ingress (poll with a BPMN timer + `board_list` / pin reads if needed).
