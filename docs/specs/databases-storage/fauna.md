# Fauna

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** none
- **Researched:** 2026-08-20
- **Official API:** hosted Fauna FQL HTTP — **EOL 2025-05-30**
- **Docs:** https://docs.fauna.com/fauna/current/eol-faq/ · https://fauna.com/blog/the-future-of-fauna
- **Auth:** n/a — hosted product decommissioned

## Scope

**Do not implement a Fauna node** and **do not call fauna.com**.

Hosted Fauna (the FQL HTTP API at fauna.com) shut down on **2025-05-30 at 12:00 PT**. After that date accounts and data were deleted. Official FAQ: [Fauna Service End of Life FAQ](https://docs.fauna.com/fauna/current/eol-faq/). Announcement: [The future of Fauna](https://fauna.com/blog/the-future-of-fauna).

An OSS FaunaDB tree exists on GitHub. It is **not** the same hosted FQL HTTP API this catalog row described. Do not treat OSS Fauna as a drop-in replacement for fauna.com, and do not ship a node against either surface.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| *(none)* | — | — | Hosted API gone. Do not expose `query.fql` / `document.*`. |

## Configuration parameters

None. Do not add `secret` or a fauna.com `endpoint`.

## Ports

Not applicable.

## Input fields (by operation)

Not applicable.

## Output fields (by operation)

Not applicable.

## Legal

- EOL FAQ: https://docs.fauna.com/fauna/current/eol-faq/
- Announcement: https://fauna.com/blog/the-future-of-fauna
- Do not scrape fauna.com or invent a compatibility HTTP client.

## Implementation notes

- Handler: **do not register**.
- If a workflow still names this node, fail closed with `{ error: "Hosted Fauna shut down on 2025-05-30. This node is not implemented." }`.
