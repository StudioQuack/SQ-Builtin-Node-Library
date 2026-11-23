# PostHog

Official PostHog Capture + Flags + private REST. Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the PostHog Cloud or self-hosted project.

- **Docs:** https://posthog.com/docs/api/capture · https://posthog.com/docs/api · https://posthog.com/docs/api/flags · https://posthog.com/docs/api/persons · https://posthog.com/docs/api/insights
- **API (2026-08-20):** Capture `POST /i/v0/e/` and Flags `POST /flags?v=2` on the public ingest host. Private REST `/api/projects/{project_id}/…` on the app host.
- **Base (default):** `https://us.i.posthog.com` (US Cloud ingest). EU Cloud ingest: `https://eu.i.posthog.com`. App hosts: `https://us.posthog.com` / `https://eu.posthog.com`. Self-host: the operator origin (no trailing slash).
- **Auth:** Project token as query `api_key` (`OfficialApiNode` query auth). Never log or place on INPUT. Private REST (`person.get`, `insight.get`) sends `Authorization: Bearer {personal_api_key}` and needs the **app** host in config `host`.

Config `host` is interpolated as `api_spec.json` `baseUrl` (default `https://us.i.posthog.com`). You may set **either** ingest (`https://us.i.posthog.com`) **or** app (`https://us.posthog.com`) — the handler swaps to the correct origin per operation (`/i/…` and `/flags` → ingest; `/api/…` → app). EU: `eu.i.posthog.com` ↔ `eu.posthog.com`.

`person.get` / `insight.get` require config or INPUT `project_id` plus `personal_api_key` (Bearer).

No `trigger.listen` — capture is ingest, not a provider webhook.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `event.capture` | POST | `/i/v0/e/` |
| `identify.create` | POST | `/i/v0/e/` (official event `$identify`; send `event` + `set`) |
| `alias.create` | POST | `/i/v0/e/` (official event `$create_alias`) |
| `track.page` | POST | `/i/v0/e/` (official event `$pageview`) |
| `track.screen` | POST | `/i/v0/e/` (official event `$screen`) |
| `person.get` | GET | `/api/projects/{project_id}/persons/` |
| `insight.get` | GET | `/api/projects/{project_id}/insights/{insight_id}/` |
| `featureFlag.isEnabled` | POST | `/flags?v=2` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `host` | string | no | OfficialApiNode baseUrl (US/EU/self-host). Default `https://us.i.posthog.com` |
| `api_key` | password | yes | Project token (query `api_key`) |
| `personal_api_key` | password | yes | Bearer on `person.get` / `insight.get` |
| `project_id` | string | no | Numeric project id for private REST paths |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes tokens or person property values.

## Legal

- Customer is the controller of analytics identifiers they submit. Minimize: send only the `distinct_id` / event fields needed.
- Provider terms: https://posthog.com/terms (current ToS on that site).
- Official API only. Do not scrape the product UI.
