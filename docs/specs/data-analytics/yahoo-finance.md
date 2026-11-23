# Yahoo Finance

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** none
- **Researched:** 2026-08-20
- **Official API:** none supported
- **Docs:** Yahoo does not publish a public Finance developer API. Unofficial `query1`/`query2.finance.yahoo.com` crumb endpoints are not official.
- **Auth:** n/a

## Scope

**Do not ship a Yahoo Finance node.** There is no official public Yahoo Finance API as of 2026-08-20. Community clients (`yfinance`, `yahoo-finance2`, crumb/cookie scrape of `query2.finance.yahoo.com`) are unofficial, unversioned, and conflict with Yahoo’s site terms. LEGAL.md forbids unofficial scrape except the existing Google Trends opt-in.

Operators who need quotes, candles, or FX should use **Alpha Vantage**, **Finnhub**, **Polygon.io**, or **Twelve Data** (this catalog).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| *(none)* | — | — | Do not expose `quote`, `chart`, or `search`. |

## Configuration parameters

None.

## Ports

Not applicable.

## Input fields (by operation)

Not applicable.

## Output fields (by operation)

Not applicable.

## Legal

- Yahoo Terms of Service (site use): https://legal.yahoo.com/us/en/yahoo/terms/otos/index.html
- Unofficial crumb APIs and HTML scrape: **do not ship**.
- Market data redistribution is licensed; even official vendors restrict display/resale.

## Implementation notes

- Handler: **do not register**.
- Do not call `query1.finance.yahoo.com` or `query2.finance.yahoo.com`.
