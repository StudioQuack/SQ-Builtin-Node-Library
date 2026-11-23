# Semantic Scholar

- **Status:** done (shipped) — cross-ref only
- **Catalog:** `docs/catalog/data-analytics.md` (duplicate index of News & Reference)
- **Folder when built:** `nodes/semantic-scholar/`
- **Researched:** 2026-08-20
- **Official API:** Academic Graph API v1 (`api.semanticscholar.org`)
- **Docs:** https://api.semanticscholar.org/api-docs/
- **Auth:** optional API key header `x-api-key` — secret binding: `api_key`

## Scope

**Do not build a second node.** Semantic Scholar already ships. Implementer contract: [`nodes/semantic-scholar/README.md`](../../../nodes/semantic-scholar/README.md).

## Operations (`operation` config values)

Shipped as `action`: `search`, `paper_details`, `citations`, `references`, `author`, `batch`.

## Legal

- https://www.semanticscholar.org/product/api/license
- Unauthenticated rate limits are tight; key recommended for production.
