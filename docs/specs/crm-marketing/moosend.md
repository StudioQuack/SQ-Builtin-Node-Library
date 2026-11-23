# Moosend

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/moosend/`
- **Researched:** 2026-08-20
- **Official API:** Moosend REST **v3** (`https://api.moosend.com/v3`)
- **Docs:** https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54561-Introduction-to-the-Moosend-API · params https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54553-Request-and-response-parameters · API key https://docs.moosend.com/user-guide/articles/KnowledgeBase/54498-use-the-api-key-to-connect-to-the-moosend-web-api · subscribe https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54573-Add-a-new-subscriber · lists https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54570-Create-a-mailing-list · unsubscribe https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54577-Unsubscribe-a-subscriber-from-a-mailing-list
- **Auth:** api_key — secret binding: `api_key`. **Query** `apikey=` on every request (Moosend does not use `Authorization`).

## Scope

One node for Moosend email lists, subscribers, and campaigns. Format suffix is always `.json`. Handler never uses XML.

**Webhooks:** official REST articles covering subscribers/campaigns/lists do **not** document a subscribe-webhook endpoint. Catalog `trigger.listen` is implemented as **not available** (`{ error }` with that message) unless Moosend publishes a webhook API later — do not scrape the app.

## Operations (`operation` config values)

Host: `https://api.moosend.com` (docs also write `{hostname}`; default this host).

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `subscriber.add` | POST | `/v3/subscribers/{list_id}/subscribe.json?apikey=` | Add or update by email |
| `subscriber.get` | GET | `/v3/subscribers/{list_id}/view.json?apikey=&Email=` | Lookup by email (official “Get subscriber by email address”) |
| `subscriber.unsubscribe` | POST | `/v3/subscribers/{list_id}/unsubscribe.json?apikey=` | Unsubscribe (keep on suppression per account settings) |
| `subscriber.remove` | POST | `/v3/subscribers/{list_id}/remove.json?apikey=` | Remove from list |
| `list.create` | POST | `/v3/lists/create.json?apikey=` | Create mailing list |
| `list.get` | GET | `/v3/lists/{list_id}/details.json?apikey=` | List details (`WithStatistics` query optional) |
| `campaign.create` | POST | `/v3/campaigns/create.json?apikey=` | Create draft. Official wrapper: POST `/campaigns/create.{Format}` ([CreatingADraftCampaignRequest](https://github.com/moosend/api-wrappers-java/blob/master/docs/CreatingADraftCampaignRequest.md)). Live KB slug for create-draft currently 404s |
| `campaign.send` | POST | `/v3/campaigns/{campaign_id}/send.json?apikey=` | Send draft immediately |
| `campaign.get` | GET | `/v3/campaigns/{campaign_id}/view.json?apikey=` | Campaign details |
| `trigger.listen` | — | none | No official webhook API found 2026-08-20 → `{ error }` |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `subscriber.add` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Account API key (Settings → API key). Query only |
| `timeout_seconds` | number | no | `25` | no | Floor 1 s |

Never put `apikey` on INPUT. Never log the query string.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Emails are PII; not secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Moosend `Code != 0` is failure even on HTTP 200 |

FUNCTION ports: none.

## Input fields (by operation)

### `subscriber.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | `MailingListID` GUID |
| `email` | string | yes | Subscriber email |
| `name` | string | no | Name |
| `has_external_double_opt_in` | boolean | no | Consent already collected elsewhere |
| `custom_fields` | array | no | `"Name=Value"` strings as documented. **Omitted fields clear on update** |
| `tags` | array | no | Tag strings |

### `subscriber.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | List GUID |
| `email` | string | yes | Query `Email` |

### `subscriber.unsubscribe` | `subscriber.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | List GUID |
| `email` | string | yes | Body `{ "Email": "…" }` |

### `list.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | List name |
| `confirmation_page` | string | no | Post-subscribe URL |
| `redirect_after_unsubscribe_page` | string | no | Unsubscribe redirect |

`Preferences` is **required** ([article 54570](https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54570-Create-a-mailing-list)): `{ "SelectType": "SingleSelect", "Options": [ "option a", "option b" ] }` (max 10 options). If INPUT omits it, send that minimal object.

### `list.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | List GUID |

### `campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Body `Name` |
| `subject` | string | yes | Body `Subject` |
| `sender_email` | string | yes | Body `SenderEmail` (must be a verified sender) |
| `reply_to_email` | string | no | Body `ReplyToEmail` |
| `confirmation_to_email` | string | no | Body `ConfirmationToEmail` |
| `web_location` | string | no | Body `WebLocation` — URL of HTML; wrapper: “We'll automatically move all CSS inline.” No inline HTML field on create |
| `mailing_list_id` | string | no | First `MailingLists[].MailingListID` (wrapper optional) |
| `segment_id` | string | no | Wrapper `segmentID` — filter list if set |

### `campaign.send` | `campaign.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | Campaign GUID |

### `trigger.listen`

No fields. Immediate `{ error: "Moosend REST v3 has no documented webhook subscribe endpoint. Use a scheduled campaign.get or subscriber.get poll, or wait for an official webhook API." }`

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | `Code === 0` | `ok` |
| `code` | number | always | Provider `Code` |
| `context` | object | success | Subscriber / list id / campaign payload. Do not log emails |
| `id` | string | create/add | GUID from Context |
| `error` | string | `Code != 0` or HTTP error | Provider `Error` or HTTP text |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | Plan-specific; default 60 |

Published limits (third-party summaries): per-endpoint, e.g. campaign stats as low as 2/60s. Honor HTTP 429.

## Legal

- Moosend account ToS + privacy policy. Operator must have CAN-SPAM/CASL/GDPR consent. `HasExternalDoubleOptIn` does not invent consent.
- API key is full account access — daemon secret store only.
- Customer owns the Moosend account and mailing lists (PII).

## Implementation notes

- JDK `HttpClient` only. `Redirect.NEVER`.
- Build URL with `apikey` query from config; strip `apikey` from any logged URI.
- `Accept: application/json`, `Content-Type: application/json`.
- Treat HTTP 200 + `Code != 0` as `{ error }`.
- Body cap **500_000** bytes. Timeout 25 s.
- `subscriber.get`: GET `/v3/subscribers/{list_id}/view.json?apikey=&Email=` ([article 54581](https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54581-Get-subscriber-by-email-address)).
- `list.get`: GET `/v3/lists/{list_id}/details.json` ([article 54571](https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54571-Get-mailing-list-details)).
- `campaign.get`: GET `/v3/campaigns/{id}/view.json` ([article 54586](https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54586-Get-campaign-details)).
- `campaign.send`: POST `/v3/campaigns/{id}/send.json` ([article 54595](https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54595-Send-a-campaign)).
- `campaign.create`: POST `/v3/campaigns/create.json`. Body PascalCase per wrappers: `Name`, `Subject`, `SenderEmail`, `ReplyToEmail`, `ConfirmationToEmail`, `WebLocation`, `MailingLists`. Live KB create-draft article 404s; treat wrapper + update-draft (54598) as the contract.
- Logs: operation, list/campaign GUID, `Code`, HTTP status — never `apikey` or subscriber email.
