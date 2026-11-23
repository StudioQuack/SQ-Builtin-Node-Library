# Lemlist

Official lemlist REST **v2** at `https://api.lemlist.com/api`. Handler: `OfficialApiNode` via `api_spec.json`. Do not use Bearer tokens.

- **Docs:** https://developer.lemlist.com/api-reference/getting-started/overview · Auth: https://developer.lemlist.com/api-reference/getting-started/authentication
- **Base:** `https://api.lemlist.com/api`
- **Auth:** HTTP Basic with **empty username** and API key as password (`Authorization: Basic` of `:{api_key}`).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `lead.create` | POST | `/campaigns/{campaign_id}/leads/` |
| `lead.get` | GET | `/leads/{email_or_id}` |
| `lead.delete` | DELETE | `/campaigns/{campaign_id}/leads/{email_or_id}` |
| `lead.unsubscribe` | DELETE | `/campaigns/{campaign_id}/leads/{email}` |
| `campaign.list` | GET | `/campaigns` (send `version=v2`) |
| `campaign.stats` | GET | `/campaigns/{campaign_id}/stats` |
| `activity.list` | GET | `/activities` |
| `enrichment.get` | GET | `/enrich/{enrichment_id}` |
| `enrichment.enrichLead` | POST | `/leads/{lead_id}/enrich` (flags are query: `findEmail`, `findPhone`, `verifyEmail`, `linkedinEnrichment`) |
| `enrichment.enrichPerson` | POST | `/enrich` (person fields and flags are query, not a JSON body) |
| `team.get` / `team.credits` | GET | `/team` · `/team/credits` |
| `unsubscribe.add` | POST | `/v2/unsubscribes/variables/{value}` (`email` or `domain` fills `value`) |
| `unsubscribe.delete` | DELETE | `/v2/unsubscribes/variables/{value}` (re-subscribe; LEAD/ABUSE sources return 409) |
| `unsubscribe.list` | GET | `/v2/unsubscribes/variables` |
| `trigger.listen` | ingress | BPMN message start / catch / boundary |

Set input/config `version` to `v2` on `campaign.list`. Unsubscribe list is already `/v2/unsubscribes/variables` and does not take `version`.

## LinkedIn URL enrichment

`linkedinUrl` / `linkedin_url` inputs are **LinkedIn profile URLs** used only as data you already hold. This node does **not** scrape LinkedIn. Using LinkedIn URLs is subject to LinkedIn’s user agreement and developer terms — that duty sits with the operator.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Integration key (Basic password) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `api_key`.

## Legal

- Rate limit: **20 requests / 2 seconds** per API key.
- Enrichment consumes credits. Cold email: caller owns CAN-SPAM / CASL / GDPR consent. Customer owns the lemlist account.
- Never log API keys, emails, phones, or LinkedIn URLs.
