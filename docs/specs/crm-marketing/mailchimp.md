# Mailchimp

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/mailchimp/`
- **Researched:** 2026-08-20
- **Official API:** Mailchimp **Marketing API 3.0** (`https://{dc}.api.mailchimp.com/3.0/`)
- **Docs:** https://mailchimp.com/developer/marketing/docs/fundamentals/ · Reference: https://mailchimp.com/developer/marketing/api/ · OAuth: https://mailchimp.com/developer/marketing/guides/access-user-data-oauth-2/
- **Auth:** api_key (HTTP Basic `anystring:key`) or oauth2 Bearer — secret bindings: `api_key` or `client_secret` + access token

## Scope

One node for **Marketing API only** (audiences, tags, campaigns, templates, list webhooks). **Mandrill / Transactional Email is not this node** — it lives in the Communication catalog. Hostname **must** include the account data center (`us6`, `us21`, …): `https://us6.api.mailchimp.com/3.0/`. The suffix of the API key (`key-us6`) **is** the dc; OAuth uses the metadata endpoint for `dc`. Marketing API 2.0 is deprecated. No CORS/browser keys.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `member.create` | POST | `/lists/{list_id}/members` | Add member |
| `member.get` | GET | `/lists/{list_id}/members/{subscriber_hash}` | Fetch member (`subscriber_hash` = MD5 of lowercase email) |
| `member.list` | GET | `/lists/{list_id}/members` | Page members |
| `member.update` | PATCH | `/lists/{list_id}/members/{subscriber_hash}` | Patch member |
| `member.delete` | DELETE | `/lists/{list_id}/members/{subscriber_hash}` | Archive/delete per API |
| `memberTag.add` | POST | `/lists/{list_id}/members/{subscriber_hash}/tags` | Tags with `status: active` |
| `memberTag.remove` | POST | `/lists/{list_id}/members/{subscriber_hash}/tags` | Same path, `status: inactive` |
| `list.group.list` | GET | `/lists/{list_id}/interest-categories` | Groups / interest categories |
| `campaign.get` | GET | `/campaigns/{campaign_id}` | Fetch campaign |
| `campaign.list` | GET | `/campaigns` | List campaigns |
| `campaign.send` | POST | `/campaigns/{campaign_id}/actions/send` | Send now (RSS follows schedule) |
| `campaign.replicate` | POST | `/campaigns/{campaign_id}/actions/replicate` | Duplicate |
| `campaign.resendNonOpeners` | POST | `/campaigns/{campaign_id}/actions/create-resend` | Shortcut to replicate/resend to common segments (non-openers, new subscribers) |
| `campaign.delete` | DELETE | `/campaigns/{campaign_id}` | Delete |
| `campaign.create` | POST | `/campaigns` | Create campaign |
| `template.list` | GET | `/templates` | List templates |
| `trigger.listen` | POST | `/lists/{list_id}/webhooks` | Audience webhook |

PUT `/lists/{list_id}/members/{subscriber_hash}` is the official add-or-update; `member.create` may use PUT when `email` is known (upsert). Prefer PUT for create+update to avoid duplicate-email 400s.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `member.get` | no | Chooses the row above |
| `auth_mode` | select | yes | `api_key` | no | `api_key` \| `oauth2` |
| `api_key` | password | if api_key | empty | yes | Marketing API key (`…-usX`) |
| `dc` | string | yes | empty | no | Data center subdomain (`us1`…`usX`). Required. Derive from key suffix if omitted **only** for api_key mode. |
| `oauth_access_token` | password | if oauth2 | empty | yes | Bearer token |
| `timeout_seconds` | number | no | `30` | no | Floor 1. Provider socket timeout is 120 s — do not set above 120. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include keys |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = member.create` | `member.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | Audience id |
| `email_address` | string | yes | Email. Hash = MD5(lowercase) for path |
| `status` | string | create yes | `subscribed` \| `unsubscribed` \| `cleaned` \| `pending` \| `transactional` |
| `merge_fields` | object | no | `FNAME`, `LNAME`, … |
| `tags` | array | no | Tag names on create |
| `language` | string | no | Locale |
| `ip_signup` | string | no | Consent evidence |

### `operation = member.get` | `member.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | Audience |
| `email_address` | string | yes | Used to compute `subscriber_hash` |

### `operation = member.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | Audience |
| `count` | number | no | Default 10, max 1000 |
| `offset` | number | no | Offset |
| `status` | string | no | Filter |
| `fields` | string | no | Partial response |

### `operation = memberTag.add` | `memberTag.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | Audience |
| `email_address` | string | yes | Member |
| `tag_names` | array | yes | `{ name, status: active\|inactive }` |

### `operation = list.group.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | Audience |

### `operation = campaign.get` | `campaign.delete` | `campaign.send` | `campaign.replicate` | `campaign.resendNonOpeners`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | Campaign id |

### `operation = campaign.list` | `template.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `count` | number | no | Page size |
| `offset` | number | no | Offset |
| `type` | string | no | Campaign type filter |
| `status` | string | no | Campaign status |

### `operation = campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `type` | string | yes | e.g. `regular` |
| `list_id` | string | yes | Recipients.list_id |
| `subject_line` | string | yes | Settings |
| `from_name` | string | yes | From |
| `reply_to` | string | yes | Reply |
| `title` | string | no | Internal title |
| `template_id` | number | no | Template |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | Audience |
| `url` | string | yes | StudioQuack message-event ingress |
| `events` | object | yes | Subscribe/unsubscribe/profile/cleaned flags as documented |
| `sources` | object | no | `user`, `admin`, `api` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/get | Member id, campaign id, webhook id |
| `resource` | object | 2xx | Provider JSON (capped). Do not log email. |
| `items` | array | list | `members` / `campaigns` / `templates` / categories |
| `total_items` | number | list | Mailchimp `total_items` |
| `error` | string | failure | `detail` from JSON error object, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 403 throttle | Honor `Retry-After`; else 60 |

## Legal

- Fundamentals: https://mailchimp.com/developer/marketing/docs/fundamentals/
- Limits: **10 simultaneous connections**; 429 if exceeded; at high volume 429/403 may have **no JSON body**. Stream timeout **120 s**.
- Role of the key’s owner limits verbs (403). Mailchimp does not support CORS.
- Caller owns CAN-SPAM / CASL / GDPR consent (`status=subscribed` only with lawful basis). Customer owns the Mailchimp account.
- Never log API keys, OAuth tokens, or member emails.
- Do not call Mandrill from this node.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Origin: `https://{dc}.api.mailchimp.com/3.0`. Wrong dc → errors.
- API key: Basic `anystring:{api_key}` **or** `Authorization: Bearer {token}`.
- `subscriber_hash`: MD5 of **lowercase** email (US-ASCII). Document that MD5 is Mailchimp’s identifier, not a security control.
- Response cap **1_000_000** bytes. Prefer `fields` / `count` to stay small.
- 429 → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry inside the handler. Do not open >10 concurrent sockets to Mailchimp from one key.
- Logs: operation, dc, list_id, http_status, sizes — never emails or keys.

`create-resend` is the official “Resend campaign” action (https://mailchimp.com/developer/marketing/api/campaigns/resend-campaign/). Body may select segment type; if omitted, Mailchimp’s default common segments apply — confirm request schema on that page at implement time.
