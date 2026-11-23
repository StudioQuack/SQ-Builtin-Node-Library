# Product Hunt

- **Status:** done (shipped) — cross-ref only
- **Catalog:** `docs/catalog/data-analytics.md` (duplicate index of News & Reference)
- **Folder when built:** `nodes/producthunt/`
- **Researched:** 2026-08-20
- **Official API:** Product Hunt GraphQL v2
- **Docs:** https://api.producthunt.com/v2/docs
- **Auth:** developer token (preferred) — secret binding: `developer_token`

## Scope

**Do not build a second node.** Product Hunt already ships. Implementer contract: [`nodes/producthunt/README.md`](../../../nodes/producthunt/README.md).

## Operations (`operation` config values)

Shipped: list posts (filters `posted_after`, `topic`, `order`). Read-only.

## Legal

- https://www.producthunt.com/legal/terms-of-service
- Official GraphQL only. No HTML scrape of producthunt.com.
