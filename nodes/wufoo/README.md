# Wufoo

Official [Wufoo REST API v3](https://wufoo.github.io/docs/). Handler: `OfficialApiNode` via `api_spec.json`. JSON only (`.json` suffix). Entries are respondent PII.

- **Docs:** https://wufoo.github.io/docs/ · https://help.surveymonkey.com/en/wufoo/integrations/wufoo-api/
- **Base:** `https://{subdomain}.wufoo.com/api/v3/`
- **Auth:** HTTP Basic (`api_key` as username). Customer owns the Wufoo account.

`OfficialApiNode` interpolates `{subdomain}` into the host (`https://{subdomain}.wufoo.com/...`) via `baseUrl` `https:/` plus path `/{subdomain}.wufoo.com/api/v3/…`. Allowlist `^[a-z0-9-]+$`. Do not use `.xml`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `form.list` | GET | `/forms.json` |
| `form.get` | GET | `/forms/{identifier}.json` |
| `entry.list` | GET | `/forms/{identifier}/entries.json` |
| `entry.submit` | POST | `/forms/{identifier}/entries.json` |
| `webhook.register` | PUT | `/forms/{identifier}/webhooks.json` |
| `trigger.listen` | ingress | BPMN message event |

`entry.submit` is documented as `application/x-www-form-urlencoded` (`Field{N}=value`). This handler JSON-encodes `fields`. If submit fails with a content-type error, map `fields` through an HTTP Request node with form encoding.

`trigger.listen` does not open a socket. Use `webhook.register` with the StudioQuack ingress URL and INPUT `handshakeKey`. Inbound POSTs echo `HandshakeKey` — reject unless it matches (constant-time). Then correlate a BPMN message event.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `subdomain` | string | no | Account subdomain (required) |
| `api_key` | password | yes | Basic username (16-char key) |
| `identifier` | string | no | Default form hash |
| `timeout_seconds` | number | no | Default 25 |

Wufoo accepts any non-empty Basic password; this node sends `api_key` as both username and password.

## Ports

`input` / `output` only. File fields: type `file`. Failures: `{ error }` — never includes `api_key` or `handshakeKey`.

## Legal

- Terms: https://www.wufoo.com/terms-of-use/
- Customer owns the Wufoo account and consent. Entries and webhook payloads are PII. Minimize. Never log keys or field values.
- Handshake key (`handshakeKey` on webhook.register) is a shared secret: constant-time compare on ingress; never log.
- Official API only: `{subdomain}.wufoo.com/api/v3/`. Do not scrape the form builder.
