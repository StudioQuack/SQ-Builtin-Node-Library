# Zoho CRM

Official [Zoho CRM REST API v8](https://www.zoho.com/crm/developer/docs/api/v8/). Handler: `OfficialApiNode` via `api_spec.json`. Authorization header `Zoho-oauthtoken` (not RFC 6750 `Bearer`). Records are PII.

- **Docs:** https://www.zoho.com/crm/developer/docs/api/v8/ · Auth: https://www.zoho.com/crm/developer/docs/api/v8/access-refresh.html · Multi-DC: https://www.zoho.com/crm/developer/docs/api/v8/multi-dc.html
- **Base (US default):** `https://www.zohoapis.com/crm/v8`. `api_spec.json` `baseUrl` is `https://{api_host}/crm/v8` so OfficialApiNode interpolates the DC host.
- **Auth:** `Authorization: Zoho-oauthtoken {access_token}`. Customer owns the Zoho CRM org.

v8 is the pinned path (v6/v7 remain documented in maintenance; do not mix versions in one node). Refresh tokens outside this node (access tokens expire ~1 hour).

## Data-center hosts

Set config `api_host` (hostname only) from the OAuth token JSON `api_domain`. OfficialApiNode interpolates it into the URL. The host **must** match the token’s `api_domain`.

| DC | Accounts | `api_host` (typical) |
|---|---|---|
| US (default) | `accounts.zoho.com` | `www.zohoapis.com` |
| EU | `accounts.zoho.eu` | `www.zohoapis.eu` |
| IN | `accounts.zoho.in` | `www.zohoapis.in` |
| AU | `accounts.zoho.com.au` | `www.zohoapis.com.au` |
| CN | `accounts.zoho.com.cn` | `www.zohoapis.com.cn` |
| JP | `accounts.zoho.jp` | `www.zohoapis.jp` |
| CA | `accounts.zohocloud.ca` | `www.zohoapis.ca` |
| SA | `accounts.zoho.sa` | use the token `api_domain` hostname |

Calling the wrong DC returns an authorization error even with a valid token.

## Operations

Catalog verbs on modules Accounts, Contacts, Deals, Leads, Products, Invoices, Quotes, Sales_Orders, Purchase_Orders, Vendors:

| verb | HTTP | Path |
|---|---|---|
| `{module}.list` | GET | `/crm/v8/{Module}` |
| `{module}.get` | GET | `/crm/v8/{Module}/{id}` |
| `{module}.create` | POST | `/crm/v8/{Module}` |
| `{module}.upsert` | POST | `/crm/v8/{Module}/upsert` |
| `{module}.update` | PUT | `/crm/v8/{Module}/{id}` |
| `{module}.delete` | DELETE | `/crm/v8/{Module}/{id}` |
| `lead.fields` | GET | `/crm/v8/settings/fields?module=` |
| `trigger.listen` | ingress | BPMN message event |

`quote`, `salesOrder`, and `purchaseOrder` have no upsert in the catalog. Create/upsert/update take per-module fields (`Last_Name`, `Email`, `Company`, `Account_Name`, `Deal_Name`, `Stage`, `Product_Name`, `Subject`, `Vendor_Name`, …) nested into `data[0]`. `data` remains a one-record object escape hatch for custom fields.

`trigger.listen` does not open a socket — catch Zoho notifications on a BPMN message event.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | OAuth access token (`Zoho-oauthtoken` prefix) |
| `api_host` | string | no | DC hostname; default `www.zohoapis.com` |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer owns the Zoho CRM org and is the controller of CRM PII. Minimize. Never log tokens or field values.
- API credits are metered per org. Official API only — do not scrape the CRM UI.

## Record body

Zoho CRM create/upsert/update sends `{"data":[{...}]}`. Purpose fields map to `data[0].Last_Name` / `Email` / `Company` / …; custom module fields go on the `data` object escape hatch. Docs: https://www.zoho.com/crm/developer/docs/api/v8/
