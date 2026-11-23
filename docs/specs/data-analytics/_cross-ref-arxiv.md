# arXiv

- **Status:** done (shipped) — cross-ref only
- **Catalog:** `docs/catalog/data-analytics.md` (duplicate index of News & Reference)
- **Folder when built:** `nodes/arxiv/`
- **Researched:** 2026-08-20
- **Official API:** arXiv Atom API (`export.arxiv.org/api/query`)
- **Docs:** https://info.arxiv.org/help/api/user-manual.html
- **Auth:** none

## Scope

**Do not build a second node.** arXiv already ships. Implementer contract: [`nodes/arxiv/README.md`](../../../nodes/arxiv/README.md).

## Operations (`operation` config values)

Shipped as `action`: `search`, `get_paper`.

## Legal

- https://info.arxiv.org/help/api/tou.html
- Courtesy throttle (~1 request / 3 s). Bulk via arXiv S3, not this API.
