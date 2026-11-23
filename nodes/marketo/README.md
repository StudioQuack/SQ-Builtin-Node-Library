# Marketo

Official **Adobe Marketo Engage REST** (Lead Database `/rest/v1/` + Asset `/rest/asset/v1/`) plus **Identity**. Handler: `OfficialApiNode` via `api_spec.json`. SOAP is deprecated (2026-07-31).

- **Docs:** REST: https://experienceleague.adobe.com/en/docs/marketo-developer/marketo/rest/rest-api · Auth: https://experienceleague.adobe.com/en/docs/marketo-developer/marketo/rest/authentication (updated 2026-08-04)
- **REST host:** `https://{munchkin_id}.mktorest.com` (from Admin → Web Services). Paths are `/{munchkin_id}.mktorest.com/rest/…` against base `https:/` because OfficialApiNode does not template `baseUrl`.
- **Auth:** **`Authorization: Bearer {access_token}` only.** Passing `access_token` as a **query or form parameter is removed on 2026-08-31**. Do not implement query-string tokens.

## Access token (2-legged)

OfficialApiNode does not call Identity. The customer supplies a current access token, **or** obtain one outside the node:

1. Admin → Integration → LaunchPoint → custom service → Client Id / Client Secret (API-only user).
2. Admin → Integration → Web Services → Identity URL.
3. `GET` or `POST` `{identityUrl}/oauth/token?grant_type=client_credentials&client_id=…&client_secret=…`
4. Response: `access_token`, `expires_in` (3600 s), `token_type=bearer`.
5. On REST error **601** (invalid) or **602** (expired), renew at Identity then retry once.

Never append `access_token=` to REST URLs.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `lead.create` | POST | `/rest/v1/leads.json` (`action=createOnly` in body) |
| `lead.upsert` | POST | `/rest/v1/leads.json` (`action=createOrUpdate`) |
| `lead.get` | GET | `/rest/v1/leads.json` (`filterType`, `filterValues`) |
| `lead.describe` | GET | `/rest/v1/leads/describe.json` |
| `list.add` | POST | `/rest/v1/lists/{list_id}/leads.json` |
| `list.remove` | DELETE | `/rest/v1/lists/{list_id}/leads.json` |
| `campaign.request` | POST | `/rest/v1/campaigns/{campaign_id}/trigger.json` |
| `activity.get` | GET | `/rest/v1/activities.json` |
| `form.submit` | POST | `/rest/v1/leads/submitForm.json` |
| `email.sendSample` | POST | `/rest/asset/v1/email/{email_id}/sendSample.json` |
| `asset.email.get` | GET | `/rest/asset/v1/email/{email_id}.json` |
| `trigger.listen` | ingress | BPMN message event. Marketo has no general REST webhook catalog — use a Webhook flow step pointing at StudioQuack ingress, or poll `activity.get`. |

Lead writes send body keys `action`, `lookupField`, `input` (array of lead objects).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer token (not a query param) |
| `munchkin_id` | string | no | Subscription host prefix |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes tokens. HTTP 200 + `success: false` is still an error at the provider.

## Legal

- Quotas: 50,000 calls/day; 100 calls / 20 s; 10 concurrent.
- Leads are PII. Caller owns consent for send-sample and form capture. Customer owns the Marketo subscription.
