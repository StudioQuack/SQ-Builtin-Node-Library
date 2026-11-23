# NewsAPI

- **Status:** done (shipped) — cross-ref only
- **Catalog:** `docs/catalog/data-analytics.md` (duplicate index of News & Reference)
- **Folder when built:** `nodes/newsapi/`
- **Researched:** 2026-08-20
- **Official API:** NewsAPI.org REST
- **Docs:** https://newsapi.org/docs
- **Auth:** API key header `X-Api-Key` — secret binding: `api_key`

## Scope

**Do not build a second node.** NewsAPI already ships. Implementer contract: [`nodes/newsapi/README.md`](../../../nodes/newsapi/README.md).

## Operations (`operation` config values)

Shipped as `endpoint`: `everything`, `top-headlines`.

## Legal

- https://newsapi.org/terms
- Developer tier: attribution required; **no production/commercial republish without a paid plan**.
