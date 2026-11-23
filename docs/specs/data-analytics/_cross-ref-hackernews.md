# Hacker News

- **Status:** done (shipped) — cross-ref only
- **Catalog:** `docs/catalog/data-analytics.md` (duplicate index of News & Reference)
- **Folder when built:** `nodes/hackernews/`
- **Researched:** 2026-08-20
- **Official API:** HN Search (Algolia) `hn.algolia.com/api/v1`
- **Docs:** https://hn.algolia.com/api
- **Auth:** none

## Scope

**Do not build a second node.** Hacker News already ships. Implementer contract: [`nodes/hackernews/README.md`](../../../nodes/hackernews/README.md).

## Operations (`operation` config values)

Shipped as `action`: `search`, `search_by_date`. Item/user GET via Firebase HN API can be added later **on this node**, not a second node.

## Legal

- https://news.ycombinator.com/newsguidelines.html
- Do not scrape news.ycombinator.com HTML.
