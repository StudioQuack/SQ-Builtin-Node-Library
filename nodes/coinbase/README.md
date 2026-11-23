# Coinbase

In-process StudioQuack builtin (`nodes/coinbase/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_COINBASE`.

## Overview

Coinbase Advanced Trade REST v3 (CDP) for **spot and US futures**.

Official docs: https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/introduction · International derivatives cutover: https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/guides/derivatives/overview · Technical: https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/guides/derivatives/technical

Not investment advice. Geo restrictions; travel-rule on withdrawals. Coinbase Pro retired 2023-12-01. Bind a CDP JWT as `api_key` (engine does not sign CB-ACCESS headers).

Do not place secrets on INPUT. Never log tokens or account numbers.

## 2026-09-09 international derivatives cutover

On **2026-09-09** Coinbase Advanced moves international derivatives off INTX onto a Deribit-powered JSON-RPC gateway (`https://drb.coinbase.com/api/v2`). Hard cutover — no parallel run. INTX REST (`/api/v3/brokerage/intx/`) stops serving. Open orders are cancelled; positions/balances migrate. Symbology `{BASE}-PERP-INTX` → `{BASE}_USDC-PERPETUAL`.

**This node does not call INTX or the new JSON-RPC gateway.** Ops below stay on Advanced Trade REST v3 (spot / US futures). Do not send INTX product ids on `order_create` / `price_get` after cutover.

## Configuration parameters

`operation` (required): `account_list` | `transaction_list` | `order_create` | `order_get` | `price_get` | `trigger`

Auth: `{"type": "bearer", "configKey": "api_key"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom. All paths are `/api/v3/brokerage/...` (not `/intx/`).

## Legal

- Terms: https://www.coinbase.com/legal/user_agreement
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
