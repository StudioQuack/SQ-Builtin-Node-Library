# FreshBooks

- **Status:** planned
- **Catalog:** `docs/catalog/finance-accounting.md`
- **Folder when built:** `nodes/freshbooks/`
- **Researched:** 2026-08-20
- **Official API:** FreshBooks REST (`api.freshbooks.com`)
- **Docs:** https://www.freshbooks.com/api/start
- **Auth:** oauth2 — secret bindings: `client_id`, `client_secret`, `refresh_token` (never on INPUT)

## Scope

One node for FreshBooks **accounting** clients, invoices, expenses, payments, and webhook callbacks (`trigger`). Accounting resources live under `/accounting/account/{accountid}/`. Identity vs accounting: `accountid` is required for this node (not `businessId` time-tracking). OAuth 2: authorize `https://auth.freshbooks.com/oauth/authorize/`, token `POST https://api.freshbooks.com/auth/oauth/token`. Access tokens expire; refresh tokens are **rotating and one-at-a-time** — persist every new refresh.

**Do not build:** PAN / gateway card numbers; time-tracking/projects as extra nodes; OAuth refresh on INPUT.

## Operations (`operation` config values)

Base: `https://api.freshbooks.com`.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `client_create` | POST | `/accounting/account/{accountid}/users/clients` | Create client |
| `invoice_create` | POST | `/accounting/account/{accountid}/invoices/invoices` | Create invoice |
| `invoice_get` | GET | `/accounting/account/{accountid}/invoices/invoices/{id}` | Read invoice |
| `invoice_list` | GET | `/accounting/account/{accountid}/invoices/invoices` | List invoices (max **100**/page) |
| `payment_create` | POST | `/accounting/account/{accountid}/payments/payments` | Record payment (amount + type; **no PAN**) |
| `expense_create` | POST | `/accounting/account/{accountid}/expenses/expenses` | Create expense |
| `trigger` | inbound | FreshBooks callback POST | Event notify; handshake `verifier` |

Webhooks: https://www.freshbooks.com/api/webhooks — `POST /events/account/{accountid}/events/callbacks`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `invoice_get` | no | Chooses the row above |
| `client_id` | password | yes | empty | yes | OAuth app id |
| `client_secret` | password | yes | empty | yes | OAuth app secret |
| `refresh_token` | password | yes | empty | yes | Rotating refresh. Never INPUT |
| `account_id` | string | yes | empty | no | Accounting `accountid` |
| `timeout_seconds` | number | no | `25` | no | Connect + read timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No secrets, no PAN |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = client_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `client` | object | yes | `{ fname, lname, email, organization, … }` per Clients API |

### `operation = invoice_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `invoice` | object | yes | Invoice JSON (`lines`, `customerid`, amounts). No card numbers |

### `operation = invoice_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `invoice_id` | number | yes | Invoice id |

### `operation = invoice_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page (per_page ignored above 100 by FreshBooks) |
| `search` | object | no | Documented search query params only |

### `operation = payment_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payment` | object | yes | `{ invoiceid, amount, date, type, note }`. `note` may hold a **processor reference**, never PAN. `type` e.g. Check/Credit/Cash as documented |

### `operation = expense_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `expense` | object | yes | `{ amount, date, categoryid, staffid, notes, … }` |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | yes | Form-urlencoded or JSON as received |
| `headers` | object | no | Content-Type |
| `form` | object | no | Parsed fields: `event`, `object_id`, `user_id`, `account_id`, `verifier`, `callback_id` |

Handshake: if `verifier` present, handler must `PUT /events/account/{accountid}/events/callbacks/{callback_id}` with `{ callback: { verifier } }` using OAuth, then `{ status: "verified" }`. Event payloads are **not** HMAC-signed in official docs — treat as untrusted until the operator’s callback URI is the StudioQuack ingress URL only.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` or `verified` |
| `response` | object | accounting ops | FreshBooks `response` envelope (`result`) |
| `event` | object | `trigger` | Normalized `{ event, object_id, account_id, user_id }` |
| `error` | string | failure | No tokens |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 | Honor `Retry-After` if present; else 60 |

## Legal

- Intro: https://www.freshbooks.com/api/start
- Auth: https://www.freshbooks.com/api/authentication
- Webhooks: https://www.freshbooks.com/api/webhooks
- Payments: https://www.freshbooks.com/api/payments
- Limits: https://www.freshbooks.com/api/limits — **no published daily cap**; short-window throttle → 429; list max **100** rows regardless of `per_page`.
- App store / unique app name. Accounting PII. No PAN.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. `Authorization: Bearer`, `Content-Type: application/json`, `User-Agent: StudioQuack/1.0`.
- Token POST `grant_type=refresh_token` to `/auth/oauth/token`. **Always** store the new refresh; old one is invalid.
- Create bodies wrap resource: `{ "invoice": {…} }`, `{ "expense": {…} }`, `{ "payment": {…} }`, `{ "client": {…} }`.
- Body cap **1_000_000**. Timeout 25 s. 429 → `{ error }` + retry_after. Other 4xx → `{ error }` 240 chars.
- Logs: operation, account_id, status — never tokens or verifier codes.
