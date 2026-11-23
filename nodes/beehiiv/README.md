# Beehiiv

Official Beehiiv REST API v2. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developers.beehiiv.com/api-reference/subscriptions/index · https://developers.beehiiv.com/api-reference
- **Base:** `https://api.beehiiv.com`
- **Auth:** Bearer `api_token`. Customer owns the account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `subscription.create` | POST | `/v2/publications/{publicationId}/subscriptions` |
| `subscription.get` | GET | `/v2/publications/{publicationId}/subscriptions/{subscriptionId}` |
| `subscription.update` | PUT | `/v2/publications/{publicationId}/subscriptions/{subscriptionId}` |
| `subscription.delete` | DELETE | `/v2/publications/{publicationId}/subscriptions/{subscriptionId}` |
| `post.list` | GET | `/v2/publications/{publicationId}/posts` |
| `post.get` | GET | `/v2/publications/{publicationId}/posts/{postId}` |
| `publication.get` | GET | `/v2/publications/{publicationId}` |
| `webhook.create` | POST | `/v2/publications/{publicationId}/webhooks` |
| `trigger.listen` | ingress | BPMN message event (no socket) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| auth secret | password | yes | Provider credential |
| `timeout_seconds` | number | no | Default 25 |

Shared production host: `api.beehiiv.com` (no regional API host). Cursor pagination is preferred over deprecated `page` (max 100 pages) — https://developers.beehiiv.com/welcome/pagination. Webhooks require Scale+. `trigger.listen` is ingress — `webhook.create` only registers the URL.

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- Beehiiv developer terms. Caller holds consent for subscription.create. Prefer unsubscribe over delete.
- Official API only. Do not scrape the vendor dashboard.
