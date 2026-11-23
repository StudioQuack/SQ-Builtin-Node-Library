# Customer.io

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/customer-io/`
- **Researched:** 2026-08-20
- **Official API:** Customer.io **Track API v1** + **App API v1** (catalog: App + Track). Docs updated 2026-08-20.
- **Docs:** https://docs.customer.io/integrations/api/customerio-apis/ · Track identify: https://docs.customer.io/integrations/api/track-vs-cdp-api/ · OpenAPI Track: https://docs.customer.io/files/journeys-track.json · OpenAPI App: https://docs.customer.io/files/journeys-app.json · Reporting webhooks: https://docs.customer.io/integrations/api/webhooks/ · Webhook payload spec: https://docs.customer.io/files/journeys-webhooks.json
- **Auth:** Shipped node is App only: `Authorization: Bearer {app_api_key}`. Track Basic is a different host and is not this node.

## Scope

One node. **Track** identifies people and records events (and manual segment membership). **App** reads campaigns/metrics. Pipelines/CDP API (`cdp.customer.io`) is **not** this node — Customer.io’s future-facing ingest; catalog is Track+App. `trigger.listen` consumes **reporting webhooks** (message sent/opened/etc.) at StudioQuack ingress. EU residency: `track-eu.customer.io` and `api-eu.customer.io`.

Percent-encode identifiers in paths (`+` → `%2B`). Track may return HTTP 200 with empty/no-op on bad encoding — treat empty as `{ error }` only when the operator expected a match **and** a lookup was performed via App; identify/track successes are often empty 200.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `customer.upsert` | PUT | Track `/api/v1/customers/{identifier}` | Identify / create / update profile |
| `customer.delete` | DELETE | Track `/api/v1/customers/{identifier}` | Delete person |
| `event.track` | POST | Track `/api/v1/customers/{identifier}/events` | Named event for a known person |
| `event.trackAnonymous` | POST | Track `/api/v1/events` | Anonymous event (`anonymous_id`) |
| `campaign.list` | GET | App `https://api.customer.io/v1/campaigns` | Confirm path in [journeys-app.json](https://docs.customer.io/files/journeys-app.json) at build (this research’s OpenAPI download truncated). Broadcast trigger (not a catalog verb) is `POST /v1/api/campaigns/{id}/triggers`. Do **not** use the CLI UI route `/v1/environments/{id}/campaigns`.
| `campaign.get` | GET | App `/v1/campaigns/{campaign_id}` | One campaign |
| `campaign.metrics` | GET | App `/v1/campaigns/{campaign_id}/metrics` | Campaign metrics |
| `segment.add` | POST | Track `/api/v1/segments/{segment_id}/add_customers` | Manual segment only (≤1000 ids) |
| `segment.remove` | POST | Track `/api/v1/segments/{segment_id}/remove_customers` | Manual segment only |
| `trigger.listen` | — | Reporting webhooks (workspace URL + HMAC) | Message lifecycle events |

Do not implement Pipelines semantic events (`Delete Person`, etc.) on this node.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `customer.upsert` | no | Chooses the row above |
| `region` | select | yes | `us` | no | `us` or `eu` |
| `app_api_key` | password | yes | | yes | App Bearer token. Shipped node is App-only (`nodes/customer-io/`). Track Basic (`site_id` / `track_api_key`) is not a config on this node. |
| `webhookSigningKey` | password | trigger.listen | | yes | Reporting webhook HMAC secret |
| `timeoutMs` | number | no | `30000` | no | Read timeout |
| `maxResponseBytes` | number | no | `1048576` | no | Response cap |

Hosts:

- US Track `https://track.customer.io` · US App `https://api.customer.io`
- EU Track `https://track-eu.customer.io` · EU App `https://api-eu.customer.io`

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo keys |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = customer.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identifier` | string | yes | `id`, email, or `cio_…` (prefix required for cio_id) |
| `email` | string | no | Attribute / identifier |
| `createdAt` | number | no | Unix timestamp |
| `attributes` | object | no | Remaining profile attributes (minimize PII) |

Do not send health data or government IDs.

### `operation = customer.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identifier` | string | yes | Person identifier |

### `operation = event.track`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identifier` | string | yes | Person |
| `name` | string | yes | Event name |
| `data` | object | no | Event properties (minimize) |
| `timestamp` | number | no | Unix time |

### `operation = event.trackAnonymous`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `anonymousId` | string | yes | Anonymous id |
| `name` | string | yes | Event name |
| `data` | object | no | Properties |

### `operation = campaign.get` / `campaign.metrics`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignId` | number | yes | Campaign id |
| `period` | string | metrics optional | Metrics period if documented on current App spec |

### `operation = campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `filter` | object | no | Query params from current App OpenAPI only |

### `operation = segment.add` / `segment.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `segmentId` | number | yes | **Manual** segment id |
| `ids` | string[] | yes | Up to 1000 |
| `idType` | select | no | `id` / `email` / `cio_id` (query `id_type`; default `id`) |

Cannot modify data-driven segments. Workspaces that use both email and id as identifiers may be unsupported on these endpoints (documented NOTE in Track OpenAPI).

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `eventTypes` | string[] | yes | Reporting events to accept (`email_sent`, `email_opened`, … per webhook spec) |
| `ingressUrl` | string | yes | Environment message-event URL the operator pastes into Customer.io Reporting webhooks |

As of 2026-08-20, reporting webhook **CRUD is workspace UI**, not a Track identify call. The handler validates `webhookSigningKey` is bound and returns the ingress URL + accepted event types. Inbound POSTs verify HMAC then correlate on BPMN.

## Output fields (by operation)

### `operation = customer.upsert` / `customer.delete` / `event.track` / `event.trackAnonymous`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 2xx | Often empty 200 on Track writes |
| `ok` | boolean | 2xx | `true` |

### `operation = campaign.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `campaigns` | object[] | 200 | List |

### `operation = campaign.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `campaign` | object | 200 | Campaign |

### `operation = campaign.metrics`

| Field | Type | When | Purpose |
|---|---|---|---|
| `metrics` | object | 200 | Metrics payload |

### `operation = segment.add` / `segment.remove`

| Field | Type | When | Purpose |
|---|---|---|---|
| `ok` | boolean | 200 | Manual segment only |

### `operation = trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `ingressUrl` | string | ready | Echo |
| `eventTypes` | string[] | ready | Filter |

Failures: `{ error }`.

## Legal

- Terms: https://customer.io/legal/terms-of-service/ · Privacy: https://customer.io/legal/privacy-policy/
- DPA / EU region: choose `eu` so profiles stay in EU. Customer is the **controller**. StudioQuack does not accept Customer.io ToS.
- Profiles and events are PII. Never log identifiers, attribute maps, or webhook bodies.
- Email/SMS consent and unsubscribe are the customer’s CAN-SPAM/CASL/GDPR duty.
- Official APIs only — no undocumented admin hosts.

**UNVERIFIED (implementer check):** exact App GET paths for `campaign.list` / `campaign.get` / `campaign.metrics` (`/v1/campaigns` vs `/v1/api/campaigns`). Track identify/delete/events and segment add/remove paths are in the fetched Track OpenAPI (`journeys-track.json`).

## Implementation notes

- JDK HTTP only. No Customer.io SDK.
- Track: Basic auth, JSON body. App: Bearer, JSON.
- Timeouts 10s/`timeoutMs`; request ≤ 256 KiB; response ≤ `maxResponseBytes`.
- Retry `429`/`5xx` max 3. App broadcast trigger (if added later) is 1 req / 10s — not in this catalog set.
- 4xx/5xx → `{ error }` on OUTPUT.
