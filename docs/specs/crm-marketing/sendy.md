# Sendy

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/sendy/`
- **Researched:** 2026-08-20
- **Official API:** Sendy per-install HTTP POST API (current Sendy **7.1.4**). Not a multi-tenant SaaS API.
- **Docs:** https://sendy.co/api
- **Auth:** API key in POST body `api_key` — secret binding: `api_key`

## Scope

One node that calls the **customer-hosted** Sendy PHP endpoints under `install_url`. Sendy sends mail through the customer’s **Amazon SES** (BYO). StudioQuack does not host Sendy and does not hold SES keys — those stay on the Sendy server. Responses are mostly **plain text**, not JSON. No official webhook/listen API — **no `trigger.listen`**.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `subscriber.add` | POST | `{install_url}/subscribe` | Add or update a subscriber |
| `subscriber.unsubscribe` | POST | `{install_url}/unsubscribe` | Unsubscribe |
| `subscriber.delete` | POST | `{install_url}/api/subscribers/delete.php` | Delete from a list |
| `subscriber.status` | POST | `{install_url}/api/subscribers/subscription-status.php` | Status string |
| `subscriber.count` | POST | `{install_url}/api/subscribers/active-subscriber-count.php` | Active count |
| `campaign.create` | POST | `{install_url}/api/campaigns/create.php` | Draft, send, or schedule |

Official extras (not in the catalog row; omit from v1 unless needed): `GET` lists/brands via `/api/lists/get-lists.php` and `/api/brands/get-brands.php`. Do not add unofficial routes.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `subscriber.add` | no | |
| `install_url` | string | yes | empty | no | Origin of the Sendy install, e.g. `https://sendy.example.com`. No trailing slash. HTTPS required in production |
| `api_key` | password | yes | empty | yes | Settings → API key. POST field `api_key` (not used on `/unsubscribe` per official list — still required by this node for all ops except as documented) |
| `timeout_seconds` | number | no | `30` | no | Campaign create may be slower |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Plain-text mapped; never echo `api_key` |

FUNCTION ports: none. File ports: none (HTML body is a string, not a `file`).

## Input fields (by operation)

### `operation = subscriber.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Subscriber email (PII) |
| `list` | string | yes | List id (`list` POST field) |
| `name` | string | no | |
| `country` | string | no | ISO 2-letter |
| `ipaddress` | string | no | Do not invent IPs |
| `referrer` | string | no | Signup URL |
| `gdpr` | boolean | no | Official `gdpr=true` for EU signup |
| `silent` | boolean | no | Bypass double opt-in (`silent=true`) — operator responsibility |
| `custom_fields` | object | no | Keys = custom field tags (without brackets) |

Always POST `boolean=true` so Sendy returns plain text (`true` / error string).

### `operation = subscriber.unsubscribe`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | |
| `list` | string | yes | POST `list` |

Official unsubscribe parameters are `email`, `list`, `boolean` — **no `api_key` on this route**. Still require config `api_key` present but do not send it unless a future Sendy version documents it.

### `operation = subscriber.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | |
| `list_id` | string | yes | POST `list_id` |

### `operation = subscriber.status`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | |
| `list_id` | string | yes | |

### `operation = subscriber.count`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | |

### `operation = campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from_name` | string | yes | |
| `from_email` | string | yes | Must be SES-verified on the Sendy host |
| `reply_to` | string | yes | |
| `title` | string | yes | |
| `subject` | string | yes | |
| `html_text` | string | yes | HTML body |
| `plain_text` | string | no | |
| `list_ids` | string | send | Comma-separated list ids if `send_campaign=1` and no segments |
| `segment_ids` | string | send | Alternative to `list_ids` |
| `exclude_list_ids` | string | no | |
| `exclude_segments_ids` | string | no | Official spelling |
| `brand_id` | string | draft | Required when `send_campaign` is 0 |
| `query_string` | string | no | Analytics tags |
| `track_opens` | number | no | `0` / `1` / `2` (anonymous) |
| `track_clicks` | number | no | `0` / `1` / `2` |
| `send_campaign` | number | no | `1` send now; default `0` draft |
| `schedule_date_time` | string | no | e.g. `June 15, 2021 6:05pm`; minutes in **5** increments |
| `schedule_timezone` | string | no | PHP timezone id |

Cron must be configured on the Sendy host for send/schedule (official note).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success text | `ok` |
| `result` | string | 2xx | Raw success: `true`, `Subscribed`, `Campaign created`, … |
| `count` | number | count | Parsed integer |
| `subscription_status` | string | status | `Subscribed` / `Unsubscribed` / `Unconfirmed` / `Bounced` / `Soft bounced` / `Complained` |
| `error` | string | error text or HTTP | Map known error strings (`Invalid API key`, `Already subscribed.`, …) |
| `http_status` | number | non-200 | |

Treat HTTP 200 with body matching official **Error:** strings as `{ error }`.

## Legal

- Product/API: https://sendy.co/api · https://sendy.co/
- Customer hosts Sendy and Amazon SES. Operator accepts Sendy license + **AWS SES** acceptable use, CAN-SPAM/CASL/GDPR for list mail.
- `silent=true` skips double opt-in — only when the operator already has consent.
- PII: emails, names, IP. Never log `api_key`, email, or HTML body. Log operation and HTTP status only.
- SSRF: `install_url` is operator-controlled. Daemon egress + SSRF guard still apply (no link-local, no metadata IPs).

## Implementation notes

- JDK HTTP POST `application/x-www-form-urlencoded` (not JSON). Charset UTF-8.
- Validate `install_url` is absolute http(s) with host; strip trailing slash; reject userinfo.
- Empty `api_key` → `{ error }`, no HTTP.
- Timeout **30 s** (campaign **60 s**). Body cap **2_000_000** bytes (`html_text`).
- Retry: only HTTP 429/502/503, max 2. Do not retry `Already subscribed.` / `Invalid API key`.
- No vendor PHP SDK.
