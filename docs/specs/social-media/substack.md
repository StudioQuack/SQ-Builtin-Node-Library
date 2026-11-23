# Substack

- **Status:** planned — blocked
- **Catalog:** `docs/catalog/social-media.md` (discovery) — **canonical CRM note:** `docs/catalog/crm-marketing.md`
- **Folder when built:** none — **DO NOT BUILD** a Substack provider node
- **Researched:** 2026-08-20
- **Official API:** Substack Developer API (gated, **profile lookup only** — not a publishing API). No official post/create/list REST for publications.
- **Docs:** Developer API Terms https://substack.com/api-tos (last updated 2026-01-08). Technical support article (linked from those terms): https://support.substack.com/hc/en-us/articles/45099095296916-Substack-Developer-API — **live body not retrieved** (timeout / bot challenge 2026-08-20).
- **Auth:** n/a for catalog ops `rss_get` / `post_list_public` as a StudioQuack Substack node

## Scope

**DO NOT BUILD** a Social Media (or CRM) Substack node for catalog operations `rss_get` / `post_list_public` (Social) or `publication.rss.get` / `post.listPublic` / `subscriber.exportManual` (CRM).

Reasons:

1. **No official publishing API.** Substack’s first-party Developer API Terms (fetched 2026-08-20) license access solely to **Authorized Data** on public creator profiles: name, LinkedIn URL, social identity URLs, subscriber count, bestseller/leaderboard flags, profile summary, profile URL, publication URL. Permitted uses are display, discovery, and linking — **not** creating posts, listing private posts, managing subscribers, or scheduling.
2. **RSS is not a developer programme.** Public feeds (`https://{publication}.substack.com/feed`) are a syndication format for already-public posts. Shipping an RSS-only “Substack” node would misrepresent a first-party integration, duplicate a generic HTTP/RSS capability, and still fail the catalog’s publishing/list intent. It is **not sufficient to ship this row**.
3. **Scrapers and cookies are forbidden.** Substack Terms of Use (effective 2025-04-21, fetched 2026-08-20) prohibit crawling, scraping, or spidering any page, data, or portion of Substack by manual or automated means. Do not send `Cookie: connect.sid` / `substack.sid`. Do not call unofficial `https://substack.com/api/v1/*` or `https://{publication}.substack.com/api/v1/*` web-app endpoints.
4. **Cross-ref CRM.** Newsletter/subscriber workflows belong with CRM rows that have official APIs (for example Kit or Beehiiv on `docs/catalog/crm-marketing.md`). Do not fork a second Substack node there either.

If a workflow still names this node, fail closed with `{ error }` and no HTTP.

## Operations (`operation` config values)

Catalog verbs — **do not expose, do not call**:

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `rss_get` | GET | `https://{publication}.substack.com/feed` | **Do not implement as a Substack node.** Public RSS is not an official publishing API. |
| `post_list_public` | — | unofficial web JSON or RSS parse | **Do not implement.** |

Not a catalog op; documented so it is not “built later” by mistake:

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| LinkedIn-handle → public profile (Developer API) | undocumented in retrieved first-party HTML | Gated; apply + token per https://substack.com/api-tos §14 | **Not this node.** It is not publishing. Endpoint path, auth header, and error contract were **not** readable from the support article on 2026-08-20. Do not guess. Do not ship under Social Media. |

Do not spec unofficial `POST /api/v1/drafts`, `POST /api/v1/posts`, subscriber export, Notes, or chat.

## Configuration parameters

None for a Substack publishing node.

Do not add `rss_url`, session cookie, `connect.sid`, or a “developer API” token on this Social Media row. A future **separate** CRM/enrichment node for the gated profile-lookup API would need its own spec after the support article is readable and only if product asks for LinkedIn-handle lookup — out of scope here.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | n/a | Do not register. |
| `output` | OUTPUT | `{ error }` only if a stub is required | Failures never include secrets |

FUNCTION ports: none.

## Input fields (by operation)

Not applicable. Do not accept `publication_url`, `rss_url`, cookies, or HTML scrape targets.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `error` | string | always if stubbed | Substack has no official publishing API; RSS-only is not this node; scrapers/cookies forbidden. |

## Legal

- Terms of Use: https://substack.com/tos — includes **“Crawls,” “scrapes,” or “spiders”** prohibition (Acceptable Use).
- Developer API Terms: https://substack.com/api-tos (2026-01-08) — Authorized Data is public profile fields only; rate limits at Substack’s discretion; no standalone dataset resale; no replicating core platform features.
- Privacy: https://substack.com/privacy
- Technical docs URL (body unverified 2026-08-20): https://support.substack.com/hc/en-us/articles/45099095296916-Substack-Developer-API
- Public RSS (not a developer API): `https://{handle}.substack.com/feed`
- Official-API-first: no scrape, no cookie session, no reverse-engineered `api/v1`.

PII: subscriber lists, emails, and paid-post bodies are not Authorized Data. Do not collect them.

## Implementation notes

- Handler: **do not register**.
- JDK HTTP: unused for this row.
- If a stub is required: `{ error: "Substack has no official publishing API. The gated Developer API is public-profile lookup only. Do not scrape, use session cookies, or call unofficial api/v1. Use CRM providers that publish an official API, or HTTP Request against a public RSS URL you own." }`
- Never throw past dispatch.
