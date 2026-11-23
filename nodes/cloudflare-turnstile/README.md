# Cloudflare Turnstile

Official [Turnstile Siteverify](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/). Server-side validation of a widget token. This is not the Cloudflare DNS/zone node.

- **Docs:** https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
- **Base:** `https://challenges.cloudflare.com`
- **Auth:** none — widget secret is config `secret`, sent as Siteverify body field `secret`
- **Handler:** `OfficialApiNode` via `api_spec.json`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `verify` | POST | `/turnstile/v0/siteverify` |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `secret` | password | yes | Siteverify body field `secret` |
| `timeout_seconds` | number | no | Default 15 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

Siteverify accepts JSON `{ secret, response, remoteip?, idempotency_key? }` or form-urlencoded. Config `secret` underlays `bodyFrom` when INPUT omits it.

Tokens: max 2048 chars; 300 s; single-use.
