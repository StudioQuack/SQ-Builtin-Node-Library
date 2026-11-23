# GetResponse

Official GetResponse **API 3.0** (`/v3`). Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://apidocs.getresponse.com/v3 · auth https://apidocs.getresponse.com/v3/authentication · contacts https://apidocs.getresponse.com/v3/case-study/adding-contacts
- **Base:** `https://api.getresponse.com/v3` (retail)
- **Auth:** `X-Auth-Token: api-key <apiKey>`. Customer owns the GetResponse account. Unused keys expire after 90 days.

In GetResponse a **campaign is a list**. Use `{ "campaign": { "campaignId": "<token>" } }` — not the 8-digit UI number.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `contact.create` | POST | `/contacts` (`202` = queued) |
| `contact.get` | GET | `/contacts/{contactId}` |
| `contact.list` | GET | `/contacts` |
| `contact.update` | POST | `/contacts/{contactId}` (POST, not PUT) |
| `contact.delete` | DELETE | `/contacts/{contactId}` |
| `campaign.list` | GET | `/campaigns` |
| `newsletter.send` | POST | `/newsletters` |
| `tag.add` | POST | `/contacts/{contactId}` |
| `trigger.listen` | — | ingress — BPMN message event; also configure GetResponse Webhooks UI |

Newer Webhooks (subscribe/unsubscribe/click/open) are configured in the GetResponse Webhooks UI. Ingress must return **2xx and JSON `{"status":"OK"}`**.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `apiKey` | password | yes | `X-Auth-Token` |
| `timeout_seconds` | number | no | Default 25 |

OAuth Bearer is an official alternate; this node uses api-key (one auth type).

`newsletter.send` requires `subject`, `fromField`, `campaign`, `content` and `sendSettings` (audience: `selectedCampaigns` / `selectedSegments` / `selectedContacts`); optional `sendOn` is written into `sendSettings.sendOn`.

## GetResponse MAX

This node packages **retail** ops only (`https://api.getresponse.com/v3`). MAX is the same `/v3` resources on a different host plus `X-Domain`:

- Hosts: `https://api3.getresponse360.com/v3` (US) or `https://api3.getresponse360.pl/v3` (PL) — confirm with the MAX account manager (https://apidocs.getresponse.com/v3/quick-start).
- Header: `X-Domain: example.com` (domain only, no `https://` or `www.`) on every request (https://apidocs.getresponse.com/v3/authentication).

There are no MAX-specific operations in this node. Operators on MAX must point `baseUrl` at the 360 host and send `X-Domain` themselves; this retail package does not inject that header.

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes `apiKey`.

## Legal

- Operator must have GDPR/CCPA/CAN-SPAM/CASL basis before `contact.create` / `newsletter.send`.
- Legal: https://www.getresponse.com/legal · Privacy: https://www.getresponse.com/legal/privacy
- Rate limits: 30,000 calls / 10 minutes and 80 calls/second; 10 parallel. HTTP `429`.
- Official API only. Do not scrape the app UI.
- `trigger.listen` is not a StudioQuack Webhook node — use a BPMN message event.
