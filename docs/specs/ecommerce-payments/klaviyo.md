# Klaviyo

- **Status:** planned — **CROSS-REF ONLY**
- **Catalog:** `docs/catalog/ecommerce-payments.md` (duplicate index of CRM)
- **Folder when built:** `nodes/klaviyo/`
- **Researched:** 2026-08-20
- **Official API:** Klaviyo API (revision header). Latest GA snapshot researched: **`2026-07-15`**. Catalog still says “v2024+”; pin `revision` on the **CRM** node.
- **Docs:** https://developers.klaviyo.com/en/docs/changelog_ · versioning: https://developers.klaviyo.com/en/docs/api_versioning_and_deprecation_policy · API: `https://a.klaviyo.com/api/`
- **Auth:** private API key (`Authorization: Klaviyo-API-Key …`) and/or OAuth — secret binding lives on the **CRM** spec/catalog row (`private_api_key` / OAuth)

## Scope

**Do not fork.** Implementer builds **ONE** node from the CRM catalog row in [`docs/catalog/crm-marketing.md`](../../catalog/crm-marketing.md) (Klaviyo). There is no `docs/specs/crm-marketing/klaviyo.md` yet — that CRM row is the contract until a CRM spec is written. E-commerce placed-order metrics are operations on **that same node**.

Do not duplicate the CRM operation list here. Card capture is not Klaviyo — Finance catalog (`docs/catalog/finance-accounting.md`) owns Stripe / PayPal / Square / Braintree / Adyen.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| — | — | — | **Not listed here.** Use CRM catalog: `profile.*`, `event.create`, `list.*`, `catalog.item.upsert`, `campaign.*`, `trigger.listen`, … |

Placed-order style payloads: `POST /api/events/` with metric names the merchant configured (often “Placed Order”) under revision `2026-07-15` (or the revision the CRM node pins). Historical replay: `backfill` flag on Create Event / Bulk Create Events in that revision.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | per CRM spec | no | Defined on the CRM node only |
| `private_api_key` | password | yes | empty | yes | CRM node secret — do not add a second binding |
| `revision` | string | no | `2026-07-15` | no | Header `revision` (ISO date). Confirm latest GA at implement time |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Same as CRM node — 1 INPUT |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Same as CRM node — 1 OUTPUT |

FUNCTION ports: none.

## Input fields (by operation)

Defined only on the CRM node. Ecom workflows pass event attributes (`value`, `order_id`, line items) into `event.create` — never PAN.

## Output fields (by operation)

Defined only on the CRM node.

## Legal

- Klaviyo developer terms / API: https://developers.klaviyo.com/
- Double opt-in / GDPR / CAN-SPAM on the merchant. Profile PII never in daemon logs.
- Revisions are supported **2 years**; do not pin a retired `revision` without reading the changelog.
- Card capture is **not** Klaviyo — use Finance catalog PSP nodes (Stripe, PayPal, Square, …).

## Implementation notes

- Implement once under `nodes/klaviyo/` per **CRM** catalog. This ecom file is an index pointer only.
- JDK `HttpClient` only when that node is built. Header `revision: 2026-07-15` (or CRM default).
- Do not copy operation tables from CRM into this folder later — update CRM spec/catalog instead.
