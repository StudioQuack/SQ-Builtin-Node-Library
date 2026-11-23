# Meta Ads

Official Meta **Marketing API v26.0** via `OfficialApiNode` / `api_spec.json`. Paid ads and Lead Ads only. Graph page posting, comments, and organic feed belong in the Social catalog — not this node.

- **Docs:** https://developers.facebook.com/docs/marketing-api/ · changelog https://developers.facebook.com/docs/graph-api/changelog/versions/ · Lead Ads retrieve https://developers.facebook.com/docs/marketing-api/guides/lead-ads/retrieving/ · leadgen webhooks https://developers.facebook.com/docs/graph-api/webhooks/getting-started/webhooks-for-leadgen/
- **Base:** `https://graph.facebook.com/v26.0`
- **Auth:** Bearer `access_token` (secret). Customer owns the ad account and Page.

Unversioned Graph calls fail for Marketing API. Pinned to **v26.0** because the Marketing API versioning page names that as current as of 2026-08-22 (https://developers.facebook.com/docs/marketing-api/overview/versioning/). Changelog: https://developers.facebook.com/docs/marketing-api/marketing-api-changelog/versions/

## Operations

| operation | HTTP | Path |
|---|---|---|
| `campaign.create` | POST | `/act_{ad_account_id}/campaigns` |
| `campaign.get` | GET | `/{id}` |
| `campaign.list` | GET | `/act_{ad_account_id}/campaigns` |
| `campaign.update` | POST | `/{id}` |
| `adset.create` | POST | `/act_{ad_account_id}/adsets` |
| `adset.get` | GET | `/{id}` |
| `adset.list` | GET | `/act_{ad_account_id}/adsets` |
| `ad.create` | POST | `/act_{ad_account_id}/ads` |
| `ad.get` | GET | `/{id}` |
| `insights.get` | GET | `/{object_id}/insights` |
| `audience.create` | POST | `/act_{ad_account_id}/customaudiences` |
| `leadForm.list` | GET | `/{page_id}/leadgen_forms` |
| `lead.get` | GET | `/{id}` |
| `customConversion.create` | POST | `/act_{ad_account_id}/customconversions` |
| `trigger.leadgen` | ingress | BPMN message event (leadgen) |
| `trigger.listen` | ingress | BPMN message event (default leadgen) |

`ad_account_id` is numeric **without** `act_`. Do not accept raw emails on `audience.create`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer (minted outside StudioQuack) |
| `ad_account_id` | string | no | Path fallback |
| `page_id` | string | no | Lead forms path fallback |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes tokens or lead `field_data`.

## Legal

- Customer owns the ad account and Page. StudioQuack is a processor for the workflow operator.
- Platform Terms: https://developers.facebook.com/terms · Lead Generation Terms: https://www.facebook.com/legal/leadgen/tos
- Minimize PII. Do not sell lead data. Do not persist leads longer than the operator’s stated purpose.
- App Review required for production `leads_retrieval` / ads Advanced Access.
- Rate limiting: honor HTTP 429; do not tight-loop.

## OfficialApiNode notes

- `kind: ingress` does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events.
- `time_range` object is not a queryFrom string — omit or pass documented query fields on a later handler.
- Optional `appsecret_proof` HMAC is not computed (OfficialApiNode has no HMAC).
