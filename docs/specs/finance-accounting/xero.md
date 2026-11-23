# Xero

- **Status:** planned
- **Catalog:** `docs/catalog/finance-accounting.md`
- **Folder when built:** `nodes/xero/`
- **Researched:** 2026-08-20
- **Official API:** Xero Accounting API **2.0** (OpenAPI ~17.x)
- **Docs:** https://developer.xero.com/documentation/api/accounting/overview
- **Auth:** bearer `access_token` minted outside StudioQuack. Header `Xero-Tenant-Id` from config `tenant_id`. Engine has no OAuth.

## Scope

One node for Xero **Accounting** contacts, invoices, payments, and webhooks (`trigger`). Base `https://api.xero.com/api.xro/2.0`. Every Accounting call sends header `Xero-Tenant-Id` (except Custom Connections, which bind one org). OAuth 2: authorize `https://login.xero.com/identity/connect/authorize`, token `https://identity.xero.com/connect/token`. Access ~30 min; unused refresh ~60 days. Granular scopes for apps created from 2 Mar 2026 (broad scopes until Sep 2027).

**Do not build:** Expense Claims and Receipts (deprecated; Classic Expenses decommission). Payroll, Files, Assets, Bank Feeds as this node. PAN on INPUT. OAuth refresh on INPUT.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `contact_create` | PUT or POST | `/Contacts` | Create contact(s) |
| `contact_get` | GET | `/Contacts/{ContactID}` | Read one contact |
| `contact_list` | GET | `/Contacts` | Page/filter contacts (`If-Modified-Since` optional) |
| `contact_update` | POST | `/Contacts` | Update contact |
| `invoice_create` | PUT or POST | `/Invoices` | Create ACCREC invoice (or ACCPAY bill) |
| `invoice_get` | GET | `/Invoices/{InvoiceID}` | Read invoice (line items when paged/single) |
| `invoice_list` | GET | `/Invoices` | Page invoices (`page`, `where`, `Statuses`) |
| `invoice_update` | POST | `/Invoices` | Update invoice |
| `payment_create` | PUT | `/Payments` | Allocate payment to invoice (**amount only**, no PAN) |
| `trigger` | inbound | Xero webhooks POST | HMAC `x-xero-signature`; Intent to Receive |

Custom Connections (client credentials, partner/premium): no refresh token; token from `client_id`+`client_secret`. Tenant header optional. Requires Custom Connections purchase + app review where Xero mandates it.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `invoice_get` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | OAuth access token minted outside StudioQuack. Never INPUT, never logs |
| `tenant_id` | string | yes | empty | no | Sent as `Xero-Tenant-Id` on every Accounting API call |
| `timeout_seconds` | number | no | `25` | no | Connect + read timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No secrets, no PAN |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact_create` | `contact_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contacts` | array | yes | `{ Name, EmailAddress, … }` per Accounting Contacts schema. Update includes `ContactID` |

### `operation = contact_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | ContactID UUID |

### `operation = contact_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page (100/page typical) |
| `where` | string | no | Xero `where` filter (no injection of extra query keys) |
| `if_modified_since` | string | no | RFC 1123 / ISO for `If-Modified-Since` |

### `operation = invoice_create` | `invoice_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `invoices` | array | yes | `{ Type, Contact, LineItems, Date, DueDate, … }`. Type `ACCREC` or `ACCPAY`. No card fields |

### `operation = invoice_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `invoice_id` | string | yes | InvoiceID UUID |

### `operation = invoice_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Pagination (line items on paged GETs) |
| `where` | string | no | Filter |
| `statuses` | string | no | e.g. `DRAFT,AUTHORISED` |
| `if_modified_since` | string | no | Incremental |

### `operation = payment_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `invoice_id` | string | yes | Invoice to allocate |
| `account_id` | string | yes | Bank account UUID |
| `amount` | number | yes | Payment amount (not a card number) |
| `date` | string | no | `YYYY-MM-DD` |
| `reference` | string | no | Processor/token reference string only |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | yes | Exact JSON bytes |
| `headers` | object | yes | `x-xero-signature` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `contacts` | array | contact ops | Contacts payload |
| `invoices` | array | invoice ops | Invoices payload |
| `payments` | array | `payment_create` | Created payments |
| `event` | object | `trigger` | `{ events[], firstEventSequence, lastEventSequence, entropy }` |
| `error` | string | failure | No secrets |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 | Honor `Retry-After` |

## Legal

- Developer terms / app review: https://developer.xero.com
- Accounting overview: https://developer.xero.com/documentation/api/accounting/overview
- Webhooks: https://developer.xero.com/documentation/guides/webhooks/overview/
- Expense Claims (deprecated): https://developer.xero.com/documentation/api/accounting/expenseclaims — **omit**
- Limits: concurrent **5**, **60**/min/tenant, daily **1000** (Starter) / **5000** (Core+), app **10_000**/min. Headers `X-MinLimit-Remaining`, `X-DayLimit-Remaining`. 429 + `Retry-After`.
- Custom Connections: AU/NZ/UK/US orgs; marketplace/partner rules. Financial PII; no PAN.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. `Authorization: Bearer`, `Accept: application/json`, `Xero-Tenant-Id: {tenant_id}`. Access token minted outside StudioQuack.
- `trigger` is ingress (BPMN message event). Webhook signature verification is not a node config parameter.
- Body cap **3_500_000** (Xero practical node limit ~50 objects / 3.5 MB; max request **10 MB** — cap handler at 3.5 MB). List page size 100.
- 429 → `{ error, retry_after_seconds }` from header. 4xx → `{ error }` 240 chars, redacted.
- Logs: operation, tenant_id present yes/no, status — never tokens.
