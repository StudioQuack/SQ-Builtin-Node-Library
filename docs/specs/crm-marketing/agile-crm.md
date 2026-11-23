# Agile CRM

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/agile-crm/`
- **Researched:** 2026-08-20
- **Official API:** Agile CRM REST API (unversioned path `/dev/api/…`). HTTPS-only. Official reference is the vendor README on GitHub (linked from https://www.agilecrm.com/api), still the published developer surface as of 2026-08-20.
- **Docs:** https://www.agilecrm.com/api · https://github.com/agilecrm/rest-api · raw contract https://raw.githubusercontent.com/agilecrm/rest-api/master/README.md
- **Auth:** HTTP Basic — secrets: `email` (username) + `restApiKey` (password). Domain is not a secret.

## Scope

One node for Agile CRM contacts, companies, and deals (opportunities). Base `https://{domain}.agilecrm.com/dev/`. Contacts and companies share the `/api/contacts` resource and are distinguished by `type` (`PERSON` vs `COMPANY`). Deals use `/api/opportunity`. Emails, names, and property values are **case-sensitive**. Official Java/PHP wrappers exist; this node uses JDK HTTP only.

No public webhook subscription API is documented in the REST README; do not invent `trigger.listen`.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `contact.create` | POST | `/dev/api/contacts` | Create `type=PERSON` |
| `contact.get` | GET | `/dev/api/contacts/{id}` | Fetch one contact |
| `contact.list` | GET | `/dev/api/contacts` | Page contacts (`page_size`, `cursor`) |
| `contact.update` | PUT | `/dev/api/contacts/edit-properties` | Partial property update by id |
| `contact.delete` | DELETE | `/dev/api/contacts/{id}` | Delete contact |
| `company.create` | POST | `/dev/api/contacts` | Create `type=COMPANY` |
| `company.get` | GET | `/dev/api/contacts/{id}` | Fetch company |
| `company.list` | POST | `/dev/api/contacts/companies/list` | List companies (`application/x-www-form-urlencoded` `page_size`, `cursor`, `global_sort_key`) |
| `company.update` | PUT | `/dev/api/contacts/edit-properties` | Partial update `type=COMPANY` |
| `company.delete` | DELETE | `/dev/api/contacts/{id}` | Delete company |
| `deal.create` | POST | `/dev/api/opportunity` | Create a deal |
| `deal.get` | GET | `/dev/api/opportunity/{id}` | Fetch one deal |
| `deal.list` | GET | `/dev/api/opportunity` | Page deals (`page_size`, `cursor`) |
| `deal.update` | PUT | `/dev/api/opportunity/partial-update` | Partial deal update |
| `deal.delete` | DELETE | `/dev/api/opportunity/{id}` | Delete deal |

Search-by-email (`GET /dev/api/contacts/search/email/{email}`) may be used internally to resolve ids; do not expose a separate catalog verb.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.create` | no | Chooses the row above |
| `domain` | string | yes | | no | Subdomain only (`acme` → `https://acme.agilecrm.com`) |
| `email` | password | yes | | **yes** | Basic auth **username** (Agile user email) |
| `restApiKey` | password | yes | | **yes** | REST client API key (Admin Settings → API & Analytics — first key). Basic auth **password** |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, resource }` or `{ error }` | Never echo Basic credentials |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact.create | company.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `firstName` | string | contact create | SYSTEM property `first_name` |
| `lastName` | string | no | `last_name` |
| `email` | string | no | SYSTEM `email` (subtype `work` / `personal`) |
| `phone` | string | no | SYSTEM `phone` |
| `companyName` | string | company create | Company display name property |
| `properties` | object[] | no | Extra `{ name, type: SYSTEM\|CUSTOM, subtype, value }` |
| `tags` | string[] | no | Contacts only; start with a letter |
| `leadScore` | number | no | Contacts only |
| `starValue` | number | no | 0–5 |
| `contactCompanyId` | number | no | Contact → company id |

### `operation = contact.get | contact.delete | company.get | company.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Contact/company id |

### `operation = contact.list | company.list | deal.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pageSize` | number | no | Page size (company.list: form field `page_size`) |
| `cursor` | string | no | Opaque cursor from previous page |
| `globalSortKey` | string | no | company.list only, e.g. `-created_time` |

### `operation = contact.update | company.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Id in the edit-properties body |
| `properties` | object[] | yes | Properties to patch |
| `tags` | string[] | no | Optional tag replace via tags endpoints if needed |

### `operation = deal.create | deal.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | update | Deal id |
| `name` | string | create | Deal name |
| `contactIds` | number[] | no | Related contacts |
| `milestone` | string | no | Pipeline milestone |
| `expectedValue` | number | no | Value |
| `probability` | number | no | 0–100 |
| `closeDate` | number | no | Epoch seconds |

### `operation = deal.get | deal.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Deal id |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `resource` | object | CRUD | Contact/company/deal JSON (`id`, `properties`, `tags`, …) |
| `id` | number | create/get | Agile id |
| `cursor` | string | list | Next-page cursor when present |
| `error` | object | 4xx/5xx | `{ code, message, status }` — no email, no API key |

## Legal

- Product/API: https://www.agilecrm.com/api and the GitHub README. Support: care@agilecrm.com. Confirm live ToS at https://www.agilecrm.com before production use.
- HTTPS only. Rate limits are not numerically published in the README; treat `429`/`503` as retryable and keep a conservative 2 rps until the operator’s account documents otherwise.
- PII: names, emails, phones, notes, deal values. **(a)** Minimize properties sent. **(b)** Customer owns the Agile CRM domain and is the controller. **(c)** StudioQuack does not accept vendor ToS on their behalf. **(d)** Never log Basic credentials or property values.
- Official-API-first: `{domain}.agilecrm.com/dev/api` only. Do not scrape the web UI.

## Implementation notes

- JDK `HttpClient` only. Ignore official language wrappers. `Accept: application/json`. Basic `email:restApiKey`.
- Validate `domain` as a hostname label (no scheme, no path, no `agilecrm.com` suffix injection / SSRF).
- Timeouts: connect 10s, read 30s. Body cap 1 MiB.
- Retry: 429/5xx, max 3, exponential backoff. No retry on 401/403.
- Errors: 4xx/5xx → `{ error }` on OUTPUT. Never throw past dispatch.
- `type` defaults to `PERSON` if omitted — company ops **must** send `type=COMPANY`.
