# Klaviyo

Official Klaviyo REST JSON:API on **`https://a.klaviyo.com`**. Handler: `OfficialApiNode` via `api_spec.json`. Server `/api` only — not `/client`.

- **Docs:** https://developers.klaviyo.com/en/docs/ · Versioning: https://developers.klaviyo.com/en/docs/api_versioning_and_deprecation_policy · Auth: https://developers.klaviyo.com/en/docs/authenticate_
- **Base:** `https://a.klaviyo.com`
- **Auth:** type `header`, header `Authorization`, prefix `Klaviyo-API-Key ` (trailing space), configKey `private_api_key` (`pk_…`).
- **Revision:** header `revision` from config/INPUT (default `2026-07-15`). Invalid revision → 404.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `profile.create` | POST | `/api/profiles/` |
| `profile.upsert` | POST | `/api/profile-import/` |
| `profile.get` | GET | `/api/profiles/{profile_id}/` |
| `profile.update` | PATCH | `/api/profiles/{profile_id}/` |
| `profile.search` | GET | `/api/profiles/` (`filter`) |
| `list.addProfiles` | POST | `/api/lists/{list_id}/relationships/profiles/` |
| `list.removeProfiles` | DELETE | `/api/lists/{list_id}/relationships/profiles/` |
| `list.get` | GET | `/api/lists/{list_id}/` |
| `event.create` | POST | `/api/events/` |
| `campaign.get` / `campaign.list` | GET | `/api/campaigns/` |
| `campaign.send` | POST | `/api/campaign-send-jobs/` |
| `segment.get` | GET | `/api/segments/{segment_id}/` |
| `template.list` | GET | `/api/templates/` |
| `catalog.item.upsert` | POST | `/api/catalog-item-bulk-create-jobs/` |
| `trigger.listen` | ingress | BPMN message start / catch / boundary |

Writes accept purpose fields (`email`, `first_name`, `metric_name`, `campaign_id`, …) nested into JSON:API `data` via bodyMap; `data` remains the escape hatch. `list.addProfiles` / `list.removeProfiles` map `profile_id` → `data[0].id` (and `type` → `data[0].type`; send `profile`). `catalog.item.upsert` maps `external_id`/`title`/`description`/`url`/`image_full_url`/`price`/`published` into `data.attributes.items.data[0].attributes`. Does **not** grant marketing consent.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `private_api_key` | password | yes | `Klaviyo-API-Key` private key |
| `revision` | string | no | Header `revision` (default `2026-07-15`) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- API Terms: https://www.klaviyo.com/legal/api-terms
- Email/SMS: double opt-in / GDPR consent is the caller’s duty. Customer owns the Klaviyo account.
- Rate limits are per-endpoint. 429 must not tight-loop.
