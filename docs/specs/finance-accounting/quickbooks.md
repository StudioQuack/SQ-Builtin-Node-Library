# QuickBooks

- **Status:** planned
- **Catalog:** `docs/catalog/finance-accounting.md`
- **Folder when built:** `nodes/quickbooks/`
- **Researched:** 2026-08-20
- **Official API:** QuickBooks Online Accounting API **v3**, pin `minorversion=75`
- **Docs:** https://developer.intuit.com/app/developer/qbo/docs/api/accounting/all-entities/invoice
- **Auth:** bearer `access_token` minted outside StudioQuack (never on INPUT)

## Scope

One node for QuickBooks **Online** books: bills, customers, employees (payroll/HR name-list, **not** Intuit login users), estimates, invoices, payments, purchases, vendors, TransactionList report, and inbound Intuit webhooks (`trigger`). Calls `https://quickbooks.api.intuit.com/v3/company/{realmId}/…` (sandbox: `https://sandbox-quickbooks.api.intuit.com`). Scope `com.intuit.quickbooks.accounting`. Access token must be minted outside StudioQuack.

**Do not build:** QuickBooks Desktop SOAP/QBXML; Payments API card capture; PAN / bank account numbers on INPUT; OAuth refresh on INPUT; Employee as a User/login record.

## Operations (`operation` config values)

Base: `{host}/v3/company/{realmId}`. Every Accounting call appends `?minorversion=75` (1–74 ignored/deprecated Aug 2025). Sparse **update** is POST of the entity with `Id` + `SyncToken`. List ops use Query.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `bill_create` | POST | `/bill` | Create a bill |
| `bill_get` | GET | `/bill/{id}` | Read one bill |
| `bill_list` | GET | `/query?query=SELECT…FROM Bill` | Page bills |
| `bill_update` | POST | `/bill` | Sparse update |
| `bill_delete` | POST | `/bill?operation=delete` | Delete bill |
| `customer_create` | POST | `/customer` | Create customer |
| `customer_get` | GET | `/customer/{id}` | Read customer |
| `customer_list` | GET | `/query?query=SELECT…FROM Customer` | Page customers |
| `customer_update` | POST | `/customer` | Sparse update |
| `employee_create` | POST | `/employee` | Payroll/HR employee record |
| `employee_get` | GET | `/employee/{id}` | Read employee |
| `employee_list` | GET | `/query?query=SELECT…FROM Employee` | Page employees |
| `estimate_create` | POST | `/estimate` | Create estimate |
| `estimate_get` | GET | `/estimate/{id}` | Read estimate |
| `estimate_list` | GET | `/query?query=SELECT…FROM Estimate` | Page estimates |
| `estimate_update` | POST | `/estimate` | Sparse update |
| `estimate_send` | POST | `/estimate/{id}/send` | Email estimate |
| `invoice_create` | POST | `/invoice` | Create invoice |
| `invoice_get` | GET | `/invoice/{id}` | Read invoice |
| `invoice_list` | GET | `/query?query=SELECT…FROM Invoice` | Page invoices |
| `invoice_update` | POST | `/invoice` | Sparse update |
| `invoice_send` | POST | `/invoice/{id}/send` | Email invoice (`sendTo` optional) |
| `payment_create` | POST | `/payment` | Record payment (amount + linked txn; **no PAN**) |
| `payment_get` | GET | `/payment/{id}` | Read payment |
| `payment_list` | GET | `/query?query=SELECT…FROM Payment` | Page payments |
| `payment_update` | POST | `/payment` | Sparse update |
| `payment_delete` | POST | `/payment?operation=delete` | Delete payment |
| `payment_void` | POST | `/payment?operation=void` | Void payment |
| `payment_send` | POST | `/payment/{id}/send` | Email payment receipt |
| `purchase_get` | GET | `/purchase/{id}` | Read expense/purchase |
| `purchase_list` | GET | `/query?query=SELECT…FROM Purchase` | Page purchases |
| `vendor_create` | POST | `/vendor` | Create vendor |
| `vendor_get` | GET | `/vendor/{id}` | Read vendor |
| `vendor_list` | GET | `/query?query=SELECT…FROM Vendor` | Page vendors |
| `vendor_update` | POST | `/vendor` | Sparse update |
| `report_transaction` | GET | `/reports/TransactionList` | Transaction List report |
| `trigger` | inbound | Intuit webhooks POST | Verify `intuit-signature`, emit entity change |

Authorize: `https://appcenter.intuit.com/connect/oauth2`. Token: `https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer`. CDC poll (`GET /cdc`) is **not** a separate operation; use it internally if reconciling missed webhooks.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `invoice_get` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | Bearer minted outside StudioQuack |
| `realm_id` | string | yes | empty | no | Company ID (`realmId`) |
| `env` | select | no | `production` | no | `production` \| `sandbox` |
| `webhook_verifier` | password | if `trigger` | empty | yes | App verifier token for HMAC |
| `timeout_seconds` | number | no | `25` | no | Connect + read timeout. Floor 1 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No tokens, no PAN |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `*_create` / `*_update` (entity JSON)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `body` | object | yes | Entity JSON per Intuit schema (`Line`, `CustomerRef`, …). **No card numbers.** Update must include `Id` and `SyncToken` |
| `send_to` | string | no | `*_send` only: `sendTo` email. Overwrites stored BillEmail if set |

### `*_get` / `*_delete` / `*_void` / `*_send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Entity Id |

### `*_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `where` | string | no | Optional SQL-like WHERE fragment only (no `;`, no second statement). Empty → `SELECT * FROM {Entity}` |
| `start_position` | number | no | Query `STARTPOSITION` (1-based) |
| `max_results` | number | no | Cap **1–1000** (Intuit max 1000). Default 100 |

### `operation = report_transaction`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `start_date` | string | no | `YYYY-MM-DD` |
| `end_date` | string | no | `YYYY-MM-DD` |
| `columns` | string | no | Report `columns` query as documented |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | yes | Exact webhook JSON bytes as UTF-8 string |
| `headers` | object | yes | Must include `intuit-signature` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx / verified | `ok` |
| `entity` | object | write/get | Provider entity (`Invoice`, `Customer`, …) |
| `query_response` | object | list | `QueryResponse` payload, truncated to cap |
| `report` | object | `report_transaction` | Report JSON |
| `event` | object | `trigger` | Parsed `{ realmId, name, id, operation, lastUpdated }` from notification |
| `error` | string | failure | Actionable; no tokens |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After`; else 60 / 30 |

## Legal

- Intuit Developer / QBO terms: https://developer.intuit.com
- OAuth: https://developer.intuit.com/app/developer/qbo/docs/develop/authentication-and-authorization/oauth-2.0
- Minor versions: https://developer.intuit.com/app/developer/qbo/docs/learn/explore-the-quickbooks-online-api/minor-versions
- Webhooks: https://developer.intuit.com/app/developer/qbo/docs/develop/webhooks
- Production throttle (Intuit help): **500 req/min per realm**, **10 concurrent/s per realm**, **40 batch/min**. HTTP 429 → no tight loop.
- Accounting PII (names, emails, amounts). No cardholder data. PCI: payments are amounts + linked txn ids, not PAN.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. `Accept: application/json`. `Authorization: Bearer {access}`. `User-Agent: StudioQuack/1.0`.
- Engine does not refresh tokens. Supply a current `access_token` minted outside StudioQuack.
- Query: GET with URL-encoded `query`; if URI > 2k chars, POST `application/text` body (Intuit query POST).
- `*_send`: `Content-Type: application/octet-stream`. **UNVERIFIED:** empty `{}` body vs truly empty — confirm against Invoice entity “Send” in API Explorer at build time.
- `trigger`: HMAC-SHA256 of **raw** body with `webhook_verifier`, Base64, constant-time compare to `intuit-signature`. Invalid → `{ error }` HTTP 401 mapping, no fetch.
- Body cap **1_000_000** bytes. Query `MAXRESULTS` clamp 1000. Timeout default 25 s.
- 429 → `{ error, http_status: 429, retry_after_seconds }` (default 60). Other 4xx → `{ error }` truncated 240 chars, tokens redacted.
- Logs: operation, realm_id length, status — never refresh/access tokens, verifier, or PAN-like fields.
