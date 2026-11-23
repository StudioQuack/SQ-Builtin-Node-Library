# Google Trends

- **Status:** done (shipped) — cross-ref only
- **Catalog:** `docs/catalog/data-analytics.md` (duplicate index of News & Reference)
- **Folder when built:** `nodes/google-trends/`
- **Researched:** 2026-08-20
- **Official API:** none — unofficial `trends.google.com` widgets (opt-in)
- **Docs:** shipped README only; prefer Google Ads API / Search Console if a future official path is needed
- **Auth:** none (NID warmup)

## Scope

**Do not build a second node and do not add HTML scraping.** Google Trends already ships behind `accept_google_terms`. Implementer contract: [`nodes/google-trends/README.md`](../../../nodes/google-trends/README.md). There is still **no official Google Trends API** as of 2026-08-20.

## Operations (`operation` config values)

Shipped as `action`: `interest_over_time`, `related_queries`, `related_topics`, `interest_by_region`, `trending_searches`, `suggestions`.

## Legal

- https://policies.google.com/terms
- Unofficial, opt-in only (`LEGAL.md`). Google may 429/block. Not Ads or Search Console.
