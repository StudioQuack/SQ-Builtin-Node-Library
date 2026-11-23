# Mailchimp

Official **Mailchimp Marketing API 3.0** at `https://{dc}.api.mailchimp.com/3.0/`. Handler: `OfficialApiNode` via `api_spec.json`. **Mandrill / Transactional Email is not this node.**

- **Docs:** https://mailchimp.com/developer/marketing/docs/fundamentals/ · Reference: https://mailchimp.com/developer/marketing/api/
- **Base:** `https://{dc}.api.mailchimp.com/3.0` (`dc` is `us1`…`usX`; also the suffix of the API key after `-`)
- **Auth:** `Authorization: Bearer {api_key}` (officially accepted for both API keys and OAuth tokens). OAuth apps discover `dc` via https://login.mailchimp.com/oauth2/metadata.

`OfficialApiNode` does not template `baseUrl`, so each path is `/{dc}.api.mailchimp.com/3.0/…` against base `https:/` (same host pattern as other per-account APIs).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `member.create` | POST | `/lists/{list_id}/members` |
| `member.get` | GET | `/lists/{list_id}/members/{subscriber_hash}` |
| `member.list` | GET | `/lists/{list_id}/members` |
| `member.update` | PATCH | `/lists/{list_id}/members/{subscriber_hash}` |
| `member.delete` | DELETE | `/lists/{list_id}/members/{subscriber_hash}` |
| `memberTag.add` / `remove` | POST | `/lists/{list_id}/members/{subscriber_hash}/tags` (`status` active/inactive) |
| `list.group.list` | GET | `/lists/{list_id}/interest-categories` |
| `campaign.get` / `list` / `create` / `delete` | GET/POST/DELETE | `/campaigns` |
| `campaign.send` | POST | `/campaigns/{campaign_id}/actions/send` |
| `campaign.replicate` | POST | `/campaigns/{campaign_id}/actions/replicate` |
| `campaign.resendNonOpeners` | POST | `/campaigns/{campaign_id}/actions/create-resend` |
| `template.list` | GET | `/templates` |
| `trigger.listen` | ingress | BPMN message start / catch / boundary |

`subscriber_hash` is MD5 of the **lowercase** email (Mailchimp’s identifier, not a security control). OfficialApiNode cannot hash — pass the hash on input.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Marketing API key (Bearer token) |
| `dc` | string | no | Data center subdomain (`us21`, …) |
| `timeout_seconds` | number | no | Default 30 (provider cap 120 s) |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `api_key`. campaign.create purpose fields merge into `recipients` / `settings` via bodyMap; keep those envelopes as escape hatches. `merge_fields` on member.create/update is the official Mailchimp merge map.

## Legal

- Caller owns CAN-SPAM / CASL / GDPR consent (`status=subscribed` only with lawful basis). Customer owns the Mailchimp account.
- 10 simultaneous connections. Do not call Mandrill from this node.
