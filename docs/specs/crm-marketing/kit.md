# Kit

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/kit/`
- **Researched:** 2026-08-20
- **Official API:** Kit API **V4** (`https://api.kit.com/v4`)
- **Docs:** https://developers.kit.com/api-reference/overview · Auth: https://developers.kit.com/api-reference/authentication
- **Auth:** api_key (`X-Kit-Api-Key`) or oauth2 Bearer — secret bindings: `api_key` or `client_secret` + `refresh_token`

## Scope

One node for Kit (formerly ConvertKit; Creator.com rebrand = Kit). New work uses **V4 only**. V3 (`api.kit.com/v3` with `api_secret`) is deprecated and will be sunset — do not build V3. Apps for other creators must use OAuth; a V4 API key is for the operator’s own account. Cursor pagination (`after` / `before`). Do not scrape app.kit.com.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `subscriber.addToForm` | POST | `/v4/forms/{form_id}/subscribers` | Add/upsert subscriber onto a form by `email_address` |
| `subscriber.addToSequence` | POST | `/v4/sequences/{sequence_id}/subscribers` | Add subscriber to a sequence (by email or id as documented) |
| `subscriber.tag.add` | POST | `/v4/tags/{tag_id}/subscribers` | Tag a subscriber |
| `subscriber.tag.remove` | DELETE | `/v4/tags/{tag_id}/subscribers/{subscriber_id}` | Untag |
| `subscriber.listByForm` | GET | `/v4/forms/{form_id}/subscribers` | Subscribers on a form |
| `subscriber.listBySequence` | GET | `/v4/sequences/{sequence_id}/subscribers` | Subscribers on a sequence |
| `subscriber.listByTag` | GET | `/v4/tags/{tag_id}/subscribers` | Subscribers with a tag |
| `form.list` | GET | `/v4/forms` | List forms |
| `sequence.list` | GET | `/v4/sequences` | List sequences |
| `tag.create` | POST | `/v4/tags` | Create tag |
| `tag.list` | GET | `/v4/tags` | List tags |
| `customField.create` | POST | `/v4/custom_fields` | Create custom field |
| `customField.list` | GET | `/v4/custom_fields` | List custom fields |
| `customField.update` | PUT | `/v4/custom_fields/{id}` | Update custom field |
| `customField.delete` | DELETE | `/v4/custom_fields/{id}` | Delete custom field |
| `trigger.listen` | POST | `/v4/webhooks` | Register subscriber-event webhook |

Base origin: `https://api.kit.com`. OAuth: `https://api.kit.com/v4/oauth/authorize` and `/v4/oauth/token`. Scopes: `read`, `write`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `form.list` | no | Chooses the row above |
| `auth_mode` | select | yes | `api_key` | no | `api_key` \| `oauth2` |
| `api_key` | password | if api_key | empty | yes | V4 key. Header `X-Kit-Api-Key`. Never on INPUT. |
| `oauth_access_token` | password | if oauth2 | empty | yes | Bearer token (daemon-held) |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |

Bulk / purchase-create endpoints require OAuth — this node does not expose those verbs.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include keys |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = subscriber.addToForm`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | number | yes | Form id |
| `email_address` | string | yes | Subscriber email |
| `first_name` | string | no | First name |
| `fields` | object | no | Custom field **keys** (not labels). Unknown keys → warnings / 4xx per Kit. Max 140 fields. |
| `state` | string | no | `active` (default) \| `cancelled` \| `bounced` \| `complained` \| `inactive` |

### `operation = subscriber.addToSequence`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sequence_id` | number | yes | Sequence id |
| `email_address` | string | yes unless `subscriber_id` | Email |
| `subscriber_id` | number | yes unless email | Existing subscriber |

### `operation = subscriber.tag.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `tag_id` | number | yes | Tag id |
| `email_address` | string | yes unless `subscriber_id` | Email (creates/tags per endpoint variant) |
| `subscriber_id` | number | yes unless email | Existing subscriber |

### `operation = subscriber.tag.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `tag_id` | number | yes | Tag id |
| `subscriber_id` | number | yes unless `email_address` | Subscriber id |
| `email_address` | string | yes unless `subscriber_id` | Alternate: `DELETE /v4/tags/{tag_id}/subscribers?email_address=` |

### `operation = subscriber.listByForm` | `subscriber.listBySequence` | `subscriber.listByTag`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` / `sequence_id` / `tag_id` | number | yes | Resource id matching the operation |
| `after` | string | no | `pagination.end_cursor` from previous page |
| `before` | string | no | `pagination.start_cursor` |
| `per_page` | number | no | Default 500, max 1000 |

### `operation = form.list` | `sequence.list` | `tag.list` | `customField.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `after` | string | no | Next-page cursor |
| `before` | string | no | Previous-page cursor |
| `per_page` | number | no | Default 500, max 1000 |

### `operation = tag.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Tag name |

### `operation = customField.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `label` | string | yes | Display label (Kit derives `key`) |

### `operation = customField.update` | `customField.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `custom_field_id` | number | yes | Path id |
| `label` | string | update only | New label |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target_url` | string | yes | StudioQuack message-event ingress (`callback_url` alias) |
| `event_name` | string | yes | One of: `subscriber.subscriber_activate`, `subscriber.subscriber_unsubscribe`, `subscriber.subscriber_bounce`, `subscriber.subscriber_complain`, `subscriber.form_subscribe`, `subscriber.course_subscribe`, `subscriber.course_complete`, `subscriber.link_click`, `subscriber.product_purchase`, `subscriber.tag_add`, `subscriber.tag_remove`, `purchase.purchase_create`, `custom_field.field_created`, `custom_field.field_deleted`, `custom_field.field_value_updated` |
| `form_id` | number | if form_subscribe | Required for that event |
| `tag_id` | number | if tag_add/remove | Required for those events |
| `sequence_id` | number | if course_* | Required |
| `product_id` | number | if product_purchase | Required |
| `initiator_value` | string | if link_click | Link URL |
| `custom_field_id` | number | if field_value_updated | Required |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `subscriber` | object | subscriber write | `{ id, email_address, state, first_name, created_at, fields }` — do not log email |
| `items` | array | list ops | `forms` / `sequences` / `tags` / `custom_fields` / `subscribers` array from Kit |
| `pagination` | object | list | `has_next_page`, `end_cursor`, `start_cursor`, `per_page` |
| `warnings` | array | 201 with unknown field keys | Kit warning strings |
| `id` | number | create tag/field/webhook | Resource id |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429 unless `Retry-After` |

## Legal

- Docs: https://developers.kit.com/api-reference/overview
- Plan eligibility: https://kit.com/pricing (API access is plan-gated)
- Rate limits (documented): **120 requests / rolling 60 s** per API key; **600 / 60 s** for OAuth.
- Subscribers are PII. Caller owns GDPR / CAN-SPAM / CASL consent. Customer owns the Kit account.
- Never log `api_key`, Bearer tokens, or email addresses.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- `api_key` mode: header `X-Kit-Api-Key`. OAuth: `Authorization: Bearer`. Empty credential → `{ error }` no HTTP.
- `Accept: application/json`, `Content-Type: application/json` on POST/PUT.
- Response cap **1_000_000** bytes. Timeout default 25 s.
- 422 (invalid email, inactive sequence) → `{ error }` with truncated `errors[]` (240 chars).
- 429 → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry inside the handler.
- Logs: operation, status, item counts, cursor presence — never emails or keys.

Body: `{ "target_url", "event": { "name", optional ids } }` (https://developers.kit.com/api-reference/webhooks/create-a-webhook). 201 `{ webhook: { id, account_id, event, target_url } }`.
