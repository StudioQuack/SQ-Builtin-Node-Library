# Coinbase

- **Status:** planned
- **Catalog:** `docs/catalog/finance-accounting.md`
- **Folder when built:** `nodes/coinbase/`
- **Researched:** 2026-08-20
- **Official API:** Coinbase Advanced Trade REST `v3` (`/api/v3/brokerage`)
- **Docs:** https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/introduction
- **Auth:** CDP JWT minted outside StudioQuack — secret binding: `api_key` (Bearer)

## Scope

One node for **Coinbase Advanced Trade** (spot and CFTC-regulated US derivatives) over REST at `https://api.coinbase.com/api/v3/brokerage`. Authenticated calls use a short-lived JWT minted from a Coinbase Developer Platform (CDP) Secret API key. Public market endpoints under `/market/...` need no JWT.

**Not investment advice.** This node does not provide recommendations, suitability analysis, or unlicensed advisory. The operator holds the Coinbase account, KYC, geo eligibility, and travel-rule obligations.

**DO NOT BUILD** a persistent WebSocket consumer (`wss://advanced-trade-ws.coinbase.com` / `wss://advanced-trade-ws-user.coinbase.com`) as `trigger`. StudioQuack triggers are HTTP webhook ingress only.

**DO NOT BUILD** CDP Notifications as `trigger` on this node: official CDP webhook event types (2026-08-20) are wallets, custodial transfers/payouts, onramp/offramp, and onchain activity — **not** Advanced Trade order/fill lifecycle. Operators who need fills should poll `transaction_list` / `order_get`.

**DO NOT BUILD** the legacy Exchange HMAC + passphrase API (`api.exchange.coinbase.com`, `CB-ACCESS-PASSPHRASE`) unless a later official doc requires it for a remaining retail path. CDP JWT is the auth for Advanced Trade.

**DO NOT BUILD** the international-derivatives Deribit/Starbase JSON-RPC gateway (`https://drb.coinbase.com/api/v2`, cutover **2026-09-09**) in this node.

**DO NOT BUILD** unofficial HTML scrapers of coinbase.com.

Cardholder data: N/A (no PAN). Never log JWT or PEM. Engine does not bind `api_secret`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `account_list` | GET | `/api/v3/brokerage/accounts` | List trading accounts (balances). Optional `resource=portfolios` → `GET /api/v3/brokerage/portfolios` |
| `transaction_list` | GET | `/api/v3/brokerage/orders/historical/fills` | Fills / executed trades (not wallet ledger) |
| `order_create` | POST | `/api/v3/brokerage/orders` | Place an order (`trade` permission) |
| `order_get` | GET | `/api/v3/brokerage/orders/historical/{order_id}` | Single order by UUID |
| `price_get` | GET | `/api/v3/brokerage/market/products/{product_id}/ticker` | Public last trades + bid/ask. `mode=candles` → `GET .../market/products/{product_id}/candles` |

Retired / omitted: `trigger`; Exchange passphrase auth; User WebSocket; INTX/Deribit gateway.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `account_list` | no | Chooses the row above |
| `api_key` | password | yes (private ops) | empty | yes | CDP JWT minted outside StudioQuack. Bearer Authorization. Never on INPUT, never logged |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s |
| `default_product_id` | string | no | empty | no | Default `product_id` (e.g. `BTC-USD`) for price/order when input omits it |

`price_get` may run without secrets (public `/market/` paths). Private ops with empty secrets → `{ error }` no HTTP.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets, no PEM, no JWT |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = account_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `resource` | string | no | `accounts` (default) or `portfolios` |
| `limit` | number | no | Page size (docs: default 49, max 250) |
| `cursor` | string | no | Pagination cursor |
| `retail_portfolio_id` | string | no | Legacy key filter only; CDP keys ignore / default to the key’s portfolio. **Deprecated** on the API |

### `operation = transaction_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_ids` | string or array | no | Canonical product ids (equities: product_id, not display ticker) |
| `order_ids` | string or array | no | Filter fills |
| `trade_ids` | string or array | no | Filter fills |
| `start_sequence_timestamp` | string | no | RFC3339 — fills after |
| `end_sequence_timestamp` | string | no | RFC3339 — fills before |
| `limit` | number | no | Page size as documented |
| `cursor` | string | no | Pagination |

### `operation = order_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_id` | string | yes | Canonical product (else `default_product_id`) |
| `side` | string | yes | `BUY` or `SELL` |
| `client_order_id` | string | yes | Client UUID; generate if omitted in designer only after validation — Coinbase requires it |
| `order_configuration` | object | yes | Advanced Trade order body (`market_market_ioc`, `limit_limit_gtc`, `limit_limit_gtd`, stop variants as documented). Do not invent undocumented types |
| `equity_order_metadata` | object | no | Equities session / TIF metadata when trading Coinbase equities |

Empty `order_configuration` → `{ error }` no HTTP.

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_id` | string | yes | Path UUID |

### `operation = price_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_id` | string | yes | e.g. `BTC-USD` |
| `mode` | string | no | `ticker` (default) or `candles` |
| `limit` | number | conditional | Ticker: number of trades (**required** by public ticker docs). Default `1` if omitted |
| `start` | string | candles yes | UNIX timestamp |
| `end` | string | candles yes | UNIX timestamp |
| `granularity` | string | candles yes | `ONE_MINUTE` \| `FIVE_MINUTE` \| `FIFTEEN_MINUTE` \| `THIRTY_MINUTE` \| `ONE_HOUR` \| `TWO_HOUR` \| `FOUR_HOUR` \| `SIX_HOUR` \| `ONE_DAY` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `accounts` | array | `account_list` accounts | Provider account objects (uuid, currency, available/hold) |
| `portfolios` | array | `account_list` portfolios | Portfolio list |
| `fills` | array | `transaction_list` | Fill objects |
| `order` | object | `order_create` / `order_get` | Order / `success`+`order_id` from create |
| `ticker` | object | `price_get` ticker | Trades + best bid/ask as returned |
| `candles` | array | `price_get` candles | OHLCV buckets |
| `cursor` | string | paginated | Next cursor when present |
| `error` | string | failure | Actionable, no secret |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After`; else 60 / 30 |

## Legal

- Advanced Trade intro: https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/introduction
- REST index: https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/rest-api
- JWT auth: https://docs.cdp.coinbase.com/coinbase-app/authentication-authorization/api-key-authentication
- CDP webhooks (not used as this node’s trigger): https://docs.cdp.coinbase.com/webhooks/overview
- Operator must complete Coinbase KYC and accept Coinbase / CDP terms. **Geo restrictions** apply (product availability differs by country; US vs international derivatives).
- **Travel Rule** applies to crypto withdrawals above jurisdictional thresholds — this node does not implement withdraw; do not add unofficial withdraw workarounds.
- **Not investment advice.** Market data and order routing are execution/data only.
- Rate limits: published per endpoint / key; HTTP 429 must not tight-loop.
- Redistribution of market data is subject to Coinbase terms.

## Implementation notes

- JDK `HttpClient` only. No Coinbase SDK. TLS verified. `Redirect.NEVER`.
- **JWT (private ops):** For each request, mint a compact JWS **ES256** (ECDSA P-256) with JDK `java.security` (not HMAC). Claims: `sub` = `api_key_id`, `iss` = `cdp`, `nbf` = now, `exp` = now + 120 s, `uri` = `{METHOD} api.coinbase.com{path}` (path includes `/api/v3/brokerage/...`, no query). Header includes `kid` = key id and a random `nonce`. `Authorization: Bearer {jwt}`. JWT TTL is **2 minutes**; mint per request. Never log the JWT.
- Public `price_get`: no Authorization header.
- Headers: `Accept: application/json`, `User-Agent: StudioQuack/1.0`. Optional `cache-control: no-cache` on public market GETs if the operator needs uncached data (Coinbase documents 1 s public cache).
- Body cap **1_000_000** bytes. Timeout default 25 s.
- 429 → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry inside the handler (orders are not idempotent without `client_order_id` replay policy — still do not auto-retry create).
- Logs: operation, product_id, http_status, sizes — never PEM, JWT, or key id beyond length.
- `retail_portfolio_id` is documented as **deprecated** for CDP keys.
