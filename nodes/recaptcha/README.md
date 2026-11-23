# reCAPTCHA

Official [siteverify](https://developers.google.com/recaptcha/docs/verify) for **Classic** v2/v3. Server-side only. The widget secret must never appear in frontend graphs. Tokens are valid for two minutes and can be verified once. New custom integrations: Google recommends reCAPTCHA Enterprise `projects.assessments.create` (not this node).

- **Docs:** https://developers.google.com/recaptcha/docs/verify
- **Base:** `https://www.google.com`
- **Auth:** none (config `secret` is posted as form field `secret`)
- **Handler:** `OfficialApiNode` via `api_spec.json`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `verify` | POST | `/recaptcha/api/siteverify` |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `secret` | password | yes | Posted as form field `secret` |
| `timeout_seconds` | number | no | Default 15 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes secrets.

`verify` posts `application/x-www-form-urlencoded` fields `secret` (from config), `response`, optional `remoteip`.

Not this node: reCAPTCHA Enterprise `projects.assessments.create`.
