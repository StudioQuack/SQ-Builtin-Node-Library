# Agile CRM

Official Agile CRM REST (unversioned `/dev/api/…`; HTTPS-only). Reference: vendor GitHub README linked from https://www.agilecrm.com/api. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://www.agilecrm.com/api · https://github.com/agilecrm/rest-api
- **Base:** `https://{domain}.agilecrm.com/dev`
- **Auth:** HTTP Basic `email` + `rest_api_key`. Customer owns the account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `contact.create` | POST | `{domain}.agilecrm.com/dev/api/contacts` |
| `contact.get` | GET | `{domain}.agilecrm.com/dev/api/contacts/{id}` |
| `contact.list` | GET | `{domain}.agilecrm.com/dev/api/contacts` |
| `contact.update` | PUT | `{domain}.agilecrm.com/dev/api/contacts/edit-properties` |
| `contact.delete` | DELETE | `{domain}.agilecrm.com/dev/api/contacts/{id}` |
| `company.create` | POST | `{domain}.agilecrm.com/dev/api/contacts` |
| `company.get` | GET | `{domain}.agilecrm.com/dev/api/contacts/{id}` |
| `company.list` | POST | `{domain}.agilecrm.com/dev/api/contacts/companies/list` |
| `company.update` | PUT | `{domain}.agilecrm.com/dev/api/contacts/edit-properties` |
| `company.delete` | DELETE | `{domain}.agilecrm.com/dev/api/contacts/{id}` |
| `deal.create` | POST | `{domain}.agilecrm.com/dev/api/opportunity` |
| `deal.get` | GET | `{domain}.agilecrm.com/dev/api/opportunity/{id}` |
| `deal.list` | GET | `{domain}.agilecrm.com/dev/api/opportunity` |
| `deal.update` | PUT | `{domain}.agilecrm.com/dev/api/opportunity/partial-update` |
| `deal.delete` | DELETE | `{domain}.agilecrm.com/dev/api/opportunity/{id}` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| auth secret | password | yes | Provider credential |
| `timeout_seconds` | number | no | Default 25 |

**Host:** `{domain}.agilecrm.com` (config `domain` is the subdomain only). Contact.create must send `type=PERSON` (API defaults PERSON). Company ops must send `type=COMPANY`. Config `email` is the Agile **user** (Basic username). INPUT `email` is the **contact** property — never put the REST key on INPUT. `deal.create` / `deal.update` accept `pipeline_id` (track id; `milestone` must exist in that track) and `owner_id` (domain user id), both listed as mandatory for deals by Agile CRM. No `trigger.listen` — none is documented.

## Ports

`input` / `output` only. Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- https://www.agilecrm.com — HTTPS only. Emails/names are case-sensitive.
- Official API only. Do not scrape the vendor dashboard.
