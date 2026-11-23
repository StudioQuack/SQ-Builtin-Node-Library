# OpenAlex

- **Status:** done (shipped) — cross-ref only
- **Catalog:** `docs/catalog/data-analytics.md` (duplicate index of News & Reference)
- **Folder when built:** `nodes/openalex/`
- **Researched:** 2026-08-20
- **Official API:** OpenAlex REST
- **Docs:** https://docs.openalex.org
- **Auth:** optional `api_key` XOR polite `mailto` — secret binding: `api_key`

## Scope

**Do not build a second node.** OpenAlex already ships. Implementer contract: [`nodes/openalex/README.md`](../../../nodes/openalex/README.md). This Data & Analytics catalog row is the same provider as the News & Reference shipped node.

## Operations (`operation` config values)

Shipped as `action`: `search_works`, `get_work`, `search_authors`. Do not add a parallel spec.

## Legal

- https://openalex.org/about
- Polite pool via `mailto` or `api_key` (mutually exclusive).
