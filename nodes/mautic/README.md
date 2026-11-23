# Mautic

Official Mautic 7.x **API Platform** REST **`/api/v2`**. Handler: `OfficialApiNode` via `api_spec.json`. Classic **`/api`** (contacts/new, /edit, /delete suffixes) is **legacy** and is used only for action ops (points, DNC, campaign/segment membership, send email). API is off by default (`Configuration → API Settings`).

- **Docs:** https://devdocs.mautic.org/en/7.1/rest_api/api_v2.html · Auth: https://devdocs.mautic.org/en/7.1/rest_api/authentication.html · classic contacts: https://devdocs.mautic.org/en/7.1/rest_api/contacts.html
- **Base:** `https://{instance_host}/api/v2` (`instance_host` hostname only). Action ops override `baseUrl` to classic `https://{instance_host}/api`.
- **Auth:** Bearer `oauth_access_token` (preferred). Token URL: `{origin}/oauth/v2/token`. **Do not put access tokens on the query string.** HTTP Basic (`username:password`) exists in 7.1 if `api_enable_basic_auth` is on; some Cloud hosts deny it. This spec uses Bearer.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `contact.create` | POST | `/contacts` (`/api/v2`) |
| `contact.get` | GET | `/contacts/{contact_id}` |
| `contact.list` | GET | `/contacts` |
| `contact.update` | PATCH | `/contacts/{contact_id}` (`/api/v2`) |
| `contact.delete` | DELETE | `/contacts/{contact_id}` (`/api/v2`) |
| `contact.points` | POST | `/contacts/{contact_id}/points/{direction}/{delta}` (`plus` \| `minus`) |
| `contact.dnc` | POST | `/contacts/{contact_id}/dnc/{channel}/{action}` (`add` \| `remove`) |
| `contact.sendEmail` | POST | `/emails/{email_id}/contact/{contact_id}/send` |
| `company.create` / `get` / `list` / `update` / `delete` | POST/GET/PATCH/DELETE | `/companies` |
| `companyContact.add` / `remove` | POST | `/companies/{company_id}/contact/{contact_id}/add\|remove` |
| `campaignContact.add` / `remove` | POST | `/campaigns/{campaign_id}/contact/{contact_id}/add\|remove` |
| `segmentContact.add` / `remove` | POST | `/segments/{segment_id}/contact/{contact_id}/add\|remove` |
| `segmentEmail.send` | POST | `/emails/{email_id}/send` |
| `trigger.listen` | ingress | BPMN message start / catch / boundary |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `oauth_access_token` | password | yes | Bearer token |
| `instance_host` | string | no | Host only, e.g. `mautic.example.com` |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes tokens.

## Legal

- Contacts are PII. Caller owns CAN-SPAM / CASL / GDPR before `contact.sendEmail` / `segmentEmail.send`.
- Self-host: operator enables API and egress. Cloud: customer’s Mautic Cloud contract. Customer owns the Mautic account.
