# Shippo

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/shippo/`
- **Researched:** 2026-08-20
- **Official API:** Shippo REST API version `2018-02-08`
- **Docs:** https://docs.goshippo.com/shippoapi/public-api/overview ; auth https://docs.goshippo.com/docs/guides_general/authentication ; rate limits https://docs.goshippo.com/api-concepts/rate-limits
- **Auth:** api_key — secret binding: `api_token`

## Scope

One node for the merchant’s own Shippo account (multi-carrier rates, labels, tracking). Calls `https://api.goshippo.com/` with `Authorization: ShippoToken {api_token}` and `SHIPPO-API-VERSION: 2018-02-08` (or `Shippo-API-Version`). `env` selects **test** (`shippo_test_…`) vs **live** (`shippo_live_…`) tokens — same host, different keys. Labels are returned as `label_url`; the handler downloads once and emits type `file` (ref-id), never inline PDF/ZPL. Customer owns the Shippo merchant account. OAuth exists for partner apps; this node uses a merchant token. **Do not build:** unofficial UI scrapes; mixing a different API version without setting the header.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `address_validate` | POST | `/addresses/` | Create an address with validation (`validate` / `validate_address` per current reference). |
| `shipment_create` | POST | `/shipments/` | Create a shipment (from/to/parcels); rates are attached on the shipment object. |
| `rate_get` | GET | `/rates/{object_id}` | Fetch one rate by id (from `shipment_create`). |
| `label_create` | POST | `/transactions/` | Purchase a label for a rate (`rate` object_id). Output label as `file`. |
| `track_get` | GET | `/tracks/{carrier}/{tracking_number}` | Retrieve tracking status (also POST `/tracks/` to register). |
| `trigger` | POST | `/webhooks` | Subscribe (`transaction_created`, `transaction_updated`, `track_updated`, `batch_created`, `batch_purchased`, or `all`). Inbound events are delivered through the StudioQuack engine's message-event ingress. |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `address_validate` | no | Chooses the row above |
| `api_token` | password | yes | | yes | `shippo_test_…` or `shippo_live_…`. Never log. |
| `env` | select | no | `test` | no | `test` \| `live`. Must match token prefix; live buys real postage. |
| `api_version` | string | no | `2018-02-08` | no | Sent as `SHIPPO-API-VERSION`. |
| `label_file_type` | select | no | `PDF` | no | `PDF`, `PDF_4x6`, `ZPLII`, `PNG`, … as published. |
| `webhook_url` | string | no | | no | HTTPS ingress URL for `trigger`. |
| `timeout_ms` | number | no | `120000` | no | Label purchase can wait on the carrier. |

File payloads (upload, download, convert) use type `file` (platform ref-id),
never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Address PII only as needed. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Label as `file`. Failures never include secrets. |

FUNCTION ports: none unless this node must invoke another node N times.

## Input fields (by operation)

### `operation = address_validate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | no | Contact name. |
| `company` | string | no | Company. |
| `street1` | string | yes | Address line 1. |
| `street2` | string | no | Line 2. |
| `city` | string | yes | City. |
| `state` | string | no | State/province. |
| `zip` | string | yes | Postal code. |
| `country` | string | yes | ISO2 country. |
| `phone` / `email` | string | no | Contact. Do not log. |
| `is_residential` | boolean | no | Residential flag. |
| `validate` | boolean | no | Request validation (default true for this operation). |

### `operation = shipment_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `address_from` | object | yes | Ship-from (object_id or inline address). |
| `address_to` | object | yes | Ship-to. |
| `parcels` | object[] | yes | `length`, `width`, `height`, `distance_unit`, `weight`, `mass_unit`. |
| `address_return` | object | no | Return address. |
| `customs_declaration` | object | no | International. |
| `async` | boolean | no | Async shipment (rates via webhook/poll). |
| `carrier_accounts` | string[] | no | Restrict carriers. |

### `operation = rate_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `rate_id` | string | yes | Rate `object_id`. |

### `operation = label_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `rate` | string | yes | Rate `object_id` to purchase. |
| `label_file_type` | string | no | Overrides config. |
| `async` | boolean | no | `true` recommended: wait for `transaction_created` webhook instead of blocking. |
| `metadata` | string | no | Up to 100 chars. |

### `operation = track_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `carrier` | string | yes | Carrier token (e.g. as documented for the account). |
| `tracking_number` | string | yes | Tracking number. |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | select | no | `subscribe` (default), `list`, `unsubscribe`. |
| `event` | string | no | `track_updated`, `transaction_created`, `transaction_updated`, `batch_created`, `batch_purchased`, `all`. |
| `url` | string | no | HTTPS callback; defaults to `webhook_url`. |
| `is_test` | boolean | no | Test webhook flag. |
| `webhook_id` | string | no | For unsubscribe (`DELETE /webhooks/{id}`). |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `object_id` | string | most | Shippo object id. |
| `validation_results` | object | `address_validate` | Validity / messages (no extra PII in logs). |
| `rates` | object[] | `shipment_create` | Rate ids, amount, currency, provider, servicelevel — pick one for `label_create`. |
| `rate` | object | `rate_get` | Single rate. |
| `label` | file | `label_create` when `SUCCESS` | Downloaded label as ref-id. Never pass through `label_url` query secrets in logs. |
| `tracking_number` | string | label / track | Tracking. |
| `tracking_status` | object | `track_get` | Status + checkpoints (minimize buyer PII). |
| `transaction_state` | string | `label_create` | `SUCCESS`, `QUEUED`, `ERROR`, … |
| `webhook` | object | `trigger` | `{ object_id, event, url, active }`. |
| `error` | object | failure | `{ message, http_status }` — no token, no label bytes. |

## Legal

- ToS / developer policy URL: Shippo legal hub / Terms of Use (confirm live URL at build: typically linked from [goshippo.com](https://goshippo.com) and the API portal). Developer reference: https://docs.goshippo.com/shippoapi/public-api/overview
- Rate limits / quota notes (as published, per minute, live / test): Address/Parcel/Shipment/Rate/Transaction POST **500 / 50**; Tracking POST **750 / 50**; list GETs much lower (e.g. 50 / 10). HTTP **429** when exceeded.
- PII / residency / prohibited uses: Addresses and tracking pages contain buyer PII — do not log. TLS 1.2+. PCI: labels are not card capture. Cross-ref `docs/catalog/finance-accounting.md` only if a Finance node charges a card.
- Official-API-first: no scrape, no unofficial Discord/web wrapper

## Implementation notes

- JDK HTTP only (no vendor SDK) unless a reason is stated.
- Timeouts, size caps, retry posture. Label buy **120s** (or `async=true` + `trigger`). Download `label_url` once; cap 5 MiB; store `file`; discard bytes. Retry 429 with backoff. Do not retry live `POST /transactions/` on unknown success (postage).
- Error mapping: HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. 401 bodies include `Token does not exist` / missing credentials — do not echo the token.
- Headers: `Authorization: ShippoToken {api_token}`, `Content-Type: application/json`, `SHIPPO-API-VERSION: {api_version}`.
- Reject mismatched `env` vs token prefix (`shippo_test_` vs `shippo_live_`) with `{ error }` before calling live.
- Addresses/parcels/shipments/rates/transactions are **disposable** (no PUT except carrier accounts). To change a value, create a new object.
