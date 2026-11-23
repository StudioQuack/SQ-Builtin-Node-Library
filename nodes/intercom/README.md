# Intercom


conversation.create / message.send take `from_type` / `from_id` / `to_type` / `to_id` merged into the official `from` / `to` objects via bodyMap. Docs: https://developers.intercom.com/docs/references/rest-api/api.intercom.io/conversations/createconversation

Official [Intercom REST](https://developers.intercom.com/docs/build-an-integration/learn-more/rest-apis). Handler: `OfficialApiNode` via `api_spec.json`. Contacts (unified user/lead `role`), companies, conversations, messages, tags. Legacy `/users` and `/leads` collections are **retired** — not called.

- **Docs:** https://developers.intercom.com/docs/references/rest-api/api.intercom.io/contacts
- **Base:** `https://{region}` — `api.intercom.io` (US) / `api.eu.intercom.io` / `api.au.intercom.io`
- **API:** extraHeaders send `Intercom-Version: {api_version}` (default **`2.16`**; https://developers.intercom.com/docs/references/introduction).
- **Auth:** Bearer `access_token` (private app or OAuth).
- **Region:** config `region` is the API host without scheme. `api_spec.placeholderDefaults` fall back to `api.intercom.io` and Intercom-Version `2.16` when config omits them.

If every call fails with `HTTP transport failed` (status −1, no HTTP body), the JDK client never reached Intercom — fix **daemon/sandbox egress** to `api.intercom.io` (or EU/AU host), DNS, and TLS; this is not an Intercom 401/404.

## Operations

Catalog CRM/Inbox verbs. `trigger.listen` is **ingress**. Webhook REST subscribe is **retired** — register the HTTPS URL in Developer Hub (HEAD probe). Verify `X-Hub-Signature` (HMAC-SHA1 of body with the app client secret) on the ingress path, not in this node.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer |
| `region` | select | no | `api.intercom.io` / `api.eu.intercom.io` / `api.au.intercom.io` |
| `api_version` | string | no | Documented `2.16` |
| `timeout_seconds` | number | no | Default 25; answer webhooks in ~5s on ingress |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `access_token` or message bodies in logs.

## Legal

- Terms: https://www.intercom.com/legal/terms-and-policies
- Webhooks: https://developers.intercom.com/docs/webhooks
- Conversations and contacts are PII. Customer owns the workspace.
- Honor `X-RateLimit-*`. Official API only.
