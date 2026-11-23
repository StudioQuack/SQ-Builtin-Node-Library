# PostHog

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/posthog/`
- **Researched:** 2026-08-20
- **Official API:** PostHog Capture (`POST /i/v0/e`, `POST /batch`) + Flags (`POST /flags?v=2`) + private REST (`/api/projects/:project_id/…`)
- **Docs:** https://posthog.com/docs/api/capture · https://posthog.com/docs/api · https://posthog.com/docs/api/flags · https://posthog.com/docs/api/persons · https://posthog.com/docs/api/insights
- **Auth:** project token (capture/flags) and personal API key (private REST) — secret bindings: `project_api_key`, `personal_api_key`

## Scope

One node for the customer’s PostHog Cloud or **self-hosted** instance. Capture, identify, alias, `$pageview`, and `$screen` go to the public POST-only ingest host. Person and insight reads go to the private REST host with a personal API key. Feature-flag evaluation uses public `POST /flags?v=2` with the project token. Config `host` is the instance URL the operator owns (Cloud US/EU or self-host). No unofficial scrape. No SDK.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `event.capture` | POST | `{ingest}/i/v0/e/` | Custom event ingest |
| `identify.create` | POST | `{ingest}/i/v0/e/` | `$identify` + `$set` person properties |
| `alias.create` | POST | `{ingest}/i/v0/e/` | `$create_alias` merge distinct IDs |
| `track.page` | POST | `{ingest}/i/v0/e/` | `$pageview` |
| `track.screen` | POST | `{ingest}/i/v0/e/` | `$screen` |
| `person.get` | GET | `{app}/api/projects/{project_id}/persons/` | Lookup by `distinct_id` / email |
| `insight.get` | GET | `{app}/api/projects/{project_id}/insights/{id}/` | Saved insight definition + last result |
| `featureFlag.isEnabled` | POST | `{ingest}/flags?v=2` | Evaluate flags for a `distinct_id` |

No `trigger.listen`. Capture is fire-and-forget ingest, not a provider webhook. Batch (`POST /batch`, body &lt; 20 MB) is an implementation detail of `event.capture` when input `events` is an array — not a separate catalog verb.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `event.capture` | no | Chooses the row above |
| `host` | string | yes | `https://us.i.posthog.com` | no | OfficialApiNode baseUrl. Cloud US ingest `https://us.i.posthog.com`; EU ingest `https://eu.i.posthog.com`; app hosts `https://us.posthog.com` / `https://eu.posthog.com` for private REST; self-host the operator URL (no trailing slash). |
| `project_api_key` | password | yes | empty | yes | Project token in capture/flags JSON `api_key`. Never on INPUT. |
| `personal_api_key` | password | no | empty | yes | `Authorization: Bearer` for `person.get` / `insight.get`. Required for those ops. |
| `project_id` | string | no | empty | no | Numeric project id for private REST. Required for `person.get` / `insight.get`. |
| `timeout_seconds` | number | no | `30` | no | Cap at sandbox request budget |

**Host mapping (official Cloud):** public ingest/flags use `https://us.i.posthog.com` or `https://eu.i.posthog.com`. Private REST uses `https://us.posthog.com` or `https://eu.posthog.com`. If `host` is a self-hosted origin, use **that same origin** for both ingest and REST. If `host` is `https://us.posthog.com` / `https://eu.posthog.com`, derive the matching `*.i.posthog.com` ingest host. Do not hardcode IPs.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo tokens or person property values in logs |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = event.capture`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event` | string | yes (single) | Event name. Omit if `events` is set |
| `distinct_id` | string | yes (single) | Max 200 chars (API truncates longer) |
| `properties` | object | no | Event properties. Do not put the project token here |
| `timestamp` | string | no | ISO 8601 |
| `process_person_profile` | boolean | no | Maps to `$process_person_profile` (anonymous = `false`) |
| `events` | array | no | Batch payload; each item `{ event, distinct_id, properties?, timestamp? }`. Whole body must be &lt; 20 MB |

### `operation = identify.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `distinct_id` | string | yes | Person to update |
| `set` | object | yes | Mapped to `properties.$set` on event `$identify` |
| `timestamp` | string | no | ISO 8601 |

### `operation = alias.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `distinct_id` | string | yes | ID being merged **from** (docs: `123` merged into `456`) |
| `alias` | string | yes | Canonical `distinct_id` (`properties.alias`) |

### `operation = track.page`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `distinct_id` | string | yes | |
| `current_url` | string | no | `$current_url` |
| `session_id` | string | no | `$session_id` (needed for web analytics completeness) |
| `properties` | object | no | Extra `$`-prefixed or custom props |

### `operation = track.screen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `distinct_id` | string | yes | |
| `screen_name` | string | yes | `$screen_name` |
| `properties` | object | no | |

### `operation = person.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `distinct_id` | string | no | Query `distinct_id=` — one of this or `email` required |
| `email` | string | no | Query `email=` |
| `limit` | number | no | Page size |

### `operation = insight.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `insight_id` | string | yes | Path id |

### `operation = featureFlag.isEnabled`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `distinct_id` | string | yes | User to evaluate |
| `flag_key` | string | no | If set, output only that flag; else return full `flags` map |
| `groups` | object | no | Group-flag evaluation (`group_type` → group id) |
| `person_properties` | object | no | Override person properties for this eval |

## Output fields (by operation)

### Capture / identify / alias / page / screen

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `ingest_status` | string | 2xx | Provider body `status` (often `Ok`) |
| `quota_limited` | array | 200 with quota | Official field when project is over quota; still HTTP 200 |
| `error` | string | failure | No secrets |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` |

Capture may return HTTP 200 even when an event is dropped (missing name / empty `distinct_id`). Validate required fields **before** POST.

### `person.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `results` | array | 2xx | Person objects (ids, distinct_ids). Do not log emails |
| `next` | string | pagination | Next cursor URL |
| `error` | string | failure | |

### `insight.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `insight` | object | 2xx | Saved insight JSON as returned |
| `error` | string | failure | |

### `featureFlag.isEnabled`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `enabled` | boolean | `flag_key` set | True if that flag is on / matching variant is active |
| `flag_value` | any | `flag_key` set | Boolean or multivariate value |
| `flags` | object | no `flag_key` | Full evaluation map |
| `error` | string | failure | |

## Legal

- Docs: https://posthog.com/docs/api/capture · https://posthog.com/docs/api
- Operator holds the PostHog org and accepts PostHog Cloud / self-host terms.
- Capture events and person properties are **customer analytics PII**. Minimize; customer owns the account and the lawful basis (GDPR/CCPA).
- Never log `project_api_key`, `personal_api_key`, `distinct_id` emails, `$set` bodies, or event property values. Log operation, HTTP status, body size, result counts.
- Public capture/flags have no request-level rate limit. Private REST: analytics **240/min and 1200/hour**; general CRUD **480/min and 4800/hour** (team-wide). Honor 429.

## Implementation notes

- JDK `HttpClient` only. JSON POST/GET. No PostHog SDK.
- Capture Content-Type `application/json`. Project token in JSON `api_key` only (not a header).
- Private REST: `Authorization: Bearer {personal_api_key}`. Empty personal key on `person.get` / `insight.get` → `{ error }` without HTTP.
- Empty `project_api_key` → `{ error }`, no HTTP.
- Capture body cap **20_000_000** bytes (official default). Reject larger batches closed.
- Timeouts: capture/flags default **15 s**; private REST default **30 s**; never exceed `sqs.sandbox.oci.requestTimeoutMs`.
- Retry: capture is at-least-once; on timeout do **not** blindly retry without an operator `message_id` / idempotency — prefer `{ error }` with timeout. Retry private REST 429/5xx up to 2 times with `Retry-After`.
- SSRF: `host` must be https (or the operator’s self-host URL allowed by daemon egress). No redirect to arbitrary IPs.
- `featureFlag.isEnabled`: parse `flags` object; missing key → `enabled=false` with `status=ok` (flag off / unknown), not a throw.
