# Substack

- **Status:** **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** none — do not create `nodes/substack/`
- **Researched:** 2026-08-20
- **Official API:** **None** for publications, posts, subscribers, or analytics.
- **Docs:** https://support.substack.com/hc/en-us/articles/360038433912-Does-Substack-have-an-API · https://support.substack.com/hc/en-us/articles/360038239391-Is-there-an-RSS-feed-for-my-publication · https://substack.com/tos
- **Auth:** none (RSS). Cookie / session impersonation is **forbidden**.

## Scope

**Do not ship a Substack node.** Substack’s own Help Center (fetched via search 2026-08-20) states they do **not** have a public API and have **no timeline** for one. Catalog verbs `publication.rss.get`, `post.listPublic`, and `subscriber.exportManual` must **not** be implemented as a built-in node.

The only documented public surface is **per-publication RSS** at `https://{publication}.substack.com/feed` (or `{custom-domain}/feed`). That is a feed for readers, not a developer platform. It is **not** a reason to add a first-party Substack node: there is no auth, no subscriber API, no webhooks, and no ToS-sanctioned scrape of `api/v1/posts` or similar app JSON.

Prefer **Kit** or **Beehiiv** (official REST, already catalogued in CRM & Marketing) when the workflow needs newsletter subscribe/post APIs.

## Operations (`operation` config values)

None. Do not implement.

| (non-operation) | HTTP | Path | Why it is not a node |
|---|---|---|---|
| Public RSS (reference only) | GET | `https://{host}/feed` | Documented help article; reader syndication only. Do not wrap as a StudioQuack provider node. |
| Undocumented JSON (`/api/v1/posts`, `/api/v1/archive`, …) | — | — | **Forbidden.** App endpoints are not a public API. |
| Session cookie / `sid` / login scrape | — | — | **Forbidden.** ToS acceptable-use: crawls/scrapes/spiders. |
| Private subscriber RSS token URLs | GET | `/feed/private/{token}` | Secret equivalent to a password; not an official integration API; do not store or log. |

## Configuration parameters

Do not add. Any `cookie` / `session` / `csrf` parameter would be a ToS violation.

## Ports

Do not add.

## Input fields (by operation)

None.

## Output fields (by operation)

None.

## Legal

- **No public API:** https://support.substack.com/hc/en-us/articles/360038433912-Does-Substack-have-an-API  
  Quote (Help Center): they don’t have a public API and don’t have a timeline for when/if it would become available.
- **RSS:** https://support.substack.com/hc/en-us/articles/360038239391-Is-there-an-RSS-feed-for-my-publication — `https://your.substack.com/feed`.
- **Terms of Use** (effective **April 21, 2025**, fetched 2026-08-20): https://substack.com/tos  
  Acceptable Use forbids: **“Crawls,” “scrapes,” or “spiders” any page, data, or portion of Substack (through use of manual or automated means).**
- Third-party write-ups (2026) describe a narrowly scoped “Developer API” for public profile lookup by LinkedIn handle. That is **not** a publications/posts/subscribers API. **UNVERIFIED** against the Help Center “no public API” article. Even if an operator later obtains that token, **this catalog row stays DO NOT BUILD** for CRM/newsletter automation.
- Subscriber lists and paid content are publisher-owned. No export API.

## Implementation notes

- Do not register a handler. Do not add unofficial opt-in.
- If a workflow needs public post titles/links, the operator may use the generic HTTP Request or RSS-capable feed tools against a **publication they are allowed to read**, honoring robots/ToS — that is not a Substack built-in node.
- Never document cookie headers, `substack.sid`, or GraphQL impersonation in this library.
