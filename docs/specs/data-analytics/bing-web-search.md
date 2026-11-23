# Bing Web Search

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** none
- **Researched:** 2026-08-20
- **Official API:** Bing Search API v7 — **retired 2025-08-11** (HTTP 410)
- **Docs:** https://learn.microsoft.com/en-us/lifecycle/announcements/bing-search-api-retirement
- **Auth:** n/a — product decommissioned

## Scope

**Do not implement Bing Search API v7** (`api.bing.microsoft.com` / Azure Cognitive Services Bing Search). Microsoft retired all Bing Search APIs on 2025-08-11: existing instances were decommissioned and new signup is closed. Calls return HTTP 410 Gone.

Microsoft’s published successor is **Grounding with Bing Search** inside Azure AI Foundry Agent Service — an LLM-agent tool, not a standalone SERP JSON API. That is a different product (Azure project + agent runtime). It is **not** a drop-in search node and must not be wrapped as “Bing Web Search v7”.

Operators who need web search should use shipped **Web Search (Tavily)** or planned **Brave Search** / **Google Programmable Search** (existing CSE customers only; sunset 2027-01-01).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| *(none)* | — | — | Retired. Do not expose `search` / `news` / `images` / `videos`. |

## Configuration parameters

None. Do not add `subscriptionKey` or a Bing v7 `endpoint`.

## Ports

Not applicable.

## Input fields (by operation)

Not applicable.

## Output fields (by operation)

Not applicable.

## Legal

- Retirement notice: https://learn.microsoft.com/en-us/lifecycle/announcements/bing-search-api-retirement
- Grounding with Bing Search (Agents only): https://learn.microsoft.com/en-us/azure/foundry/agents/how-to/tools/web-overview
- Do not scrape bing.com HTML as a workaround.

## Implementation notes

- Handler: **do not register**.
- If a workflow still names this node, fail closed with `{ error: "Bing Search API v7 was retired on 2025-08-11. Use Web Search or Brave Search." }` — only if a stub is required for catalog honesty; prefer omitting the node entirely.
