# Kit

Official Kit API **V4** at `https://api.kit.com/v4` (ConvertKit / Creator.com rebrand = Kit). Handler: `OfficialApiNode` via `api_spec.json`. Do not build V3 (`api_secret`).

- **Docs:** https://developers.kit.com/api-reference/overview · Auth: https://developers.kit.com/api-reference/authentication
- **Base:** `https://api.kit.com`
- **Auth:** header `X-Kit-Api-Key` from config `api_key` (secret). A V4 API key is for the operator’s own account. Apps for other creators must use OAuth Bearer (not this spec’s auth type). Bulk/purchase verbs are out of scope.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `subscriber.addToForm` | POST | `/v4/forms/{form_id}/subscribers` |
| `subscriber.addToSequence` | POST | `/v4/sequences/{sequence_id}/subscribers` |
| `subscriber.tag.add` | POST | `/v4/tags/{tag_id}/subscribers` |
| `subscriber.tag.remove` | DELETE | `/v4/tags/{tag_id}/subscribers/{subscriber_id}` |
| `subscriber.listByForm` / `listBySequence` / `listByTag` | GET | `/v4/{forms\|sequences\|tags}/{id}/subscribers` |
| `form.list` | GET | `/v4/forms` |
| `sequence.list` | GET | `/v4/sequences` |
| `tag.create` / `tag.list` | POST/GET | `/v4/tags` |
| `customField.create` / `list` / `update` / `delete` | POST/GET/PUT/DELETE | `/v4/custom_fields` |
| `trigger.listen` | ingress | BPMN message start / catch / boundary |

Cursor pagination: `after` / `before` / `per_page` (default 500, max 1000). Rate limits: 120 requests / 60 s per API key.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | V4 key (`X-Kit-Api-Key`) |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `api_key`.

## Legal

- Subscribers are PII. Caller owns GDPR / CAN-SPAM / CASL consent. Customer owns the Kit account.
- Plan eligibility: https://kit.com/pricing (API access is plan-gated).
- Official API only. Do not scrape app.kit.com.
