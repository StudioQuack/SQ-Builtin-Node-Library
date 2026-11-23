# Moosend

Official **Moosend REST v3** via `OfficialApiNode` / `api_spec.json`. Format suffix is always `.json` (never XML).

- **Docs:** https://docs.moosend.com/api-documentation/articles/KnowledgeBase/54561-Introduction-to-the-Moosend-API · API key https://docs.moosend.com/user-guide/articles/KnowledgeBase/54498-use-the-api-key-to-connect-to-the-moosend-web-api
- **Base:** `https://api.moosend.com`
- **Auth:** query `apikey=` on every request (Moosend does not use `Authorization`). Config key **`apikey`** matches the query name OfficialApiNode emits. Customer owns the Moosend account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `subscriber.add` | POST | `/v3/subscribers/{list_id}/subscribe.json` |
| `subscriber.get` | GET | `/v3/subscribers/{list_id}/find/{email}.json` |
| `subscriber.unsubscribe` | POST | `/v3/subscribers/{list_id}/unsubscribe.json` |
| `subscriber.remove` | POST | `/v3/subscribers/{list_id}/remove.json` |
| `list.create` | POST | `/v3/lists/create.json` |
| `list.get` | GET | `/v3/lists/{list_id}/details.json` |
| `campaign.create` | POST | `/v3/campaigns/create.json` |
| `campaign.send` | POST | `/v3/campaigns/{campaign_id}/send.json` |
| `campaign.get` | GET | `/v3/campaigns/{campaign_id}/view.json` |
| `trigger.listen` | ingress | BPMN message event |

// ⚠️ verify: `find` / `details` / `view` path names against live “Get … details” articles if a 404 appears (Moosend path names have historically varied).

REST v3 articles covering subscribers/campaigns/lists do **not** document a subscribe-webhook endpoint as of 2026-08-20. `trigger.listen` is ingress (not a fake HTTP subscribe).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `subscriber.add` |
| `apikey` | password | yes | Query `apikey=` |
| `timeout_seconds` | number | no | Default 25 |

Never put `apikey` on INPUT. Never log the query string.

Provider JSON often uses PascalCase (`Email`, `Name`). Send those keys on INPUT (schema lists both snake_case and PascalCase). `HasExternalDoubleOptIn` does not invent consent.

## Ports

`input` / `output` only. OfficialApiNode treats HTTP 2xx as `status=ok` even when Moosend `Code != 0` — inspect `data.Code` / `data.Error`.

## Legal

- Operator must have CAN-SPAM/CASL/GDPR consent.
- API key is full account access — daemon secret store only.
- Customer owns mailing lists (PII).
