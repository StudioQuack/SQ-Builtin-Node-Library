# Medium

- **Status:** planned — blocked
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** none — **DO NOT BUILD**
- **Researched:** 2026-08-20
- **Official API:** Medium OAuth2 REST `https://api.medium.com/v1` — **unsupported; closed to new integrations**
- **Docs:** https://github.com/Medium/medium-api-docs (archived 2023-03-02)
- **Auth:** n/a for new StudioQuack nodes — Medium does not issue new integration tokens

## Scope

**DO NOT BUILD.** Medium’s own GitHub documentation (the only remaining first-party API reference) states: **“The Medium API is no longer supported. We do not recommend using it.”** The same document’s OAuth section states: **“We don't allow any new integrations with our API.”** Browser-based OAuth is documented as existing-integrations-only. Self-issued “integration tokens” are described as a narrow desktop/plugin case and are no longer a supported developer programme.

This node must not ship. Do not register a handler. Do not call `api.medium.com`. Do not scrape medium.com HTML. Do not drive a logged-in browser session. Do not wrap third-party “Medium API” proxies, import-bookmarklet clones, or unofficial GraphQL/web-app endpoints.

Catalog verbs `post_create` and `publication_list` are **retired for StudioQuack**. There is no replacement official publishing surface as of 2026-08-20.

If a workflow still names this node, fail closed with `{ error }` and no HTTP. Prefer omitting the node from the designer catalog once the catalog row is marked blocked.

## Operations (`operation` config values)

Historical verbs from the archived docs — **do not expose, do not call**:

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `post_create` | POST | `/v1/users/{userId}/posts` or `/v1/publications/{publicationId}/posts` | **Do not implement.** Was “create a post”. |
| `publication_list` | GET | `/v1/users/{userId}/publications` | **Do not implement.** Was “list publications related to the user”. |

Also documented historically (not in the catalog row; still forbidden): `GET /v1/me`, `POST /v1/images`.

## Configuration parameters

None. Do not add `integration_token`, OAuth `client_id` / `client_secret`, or a Medium base URL.

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | — | — | no | Do not ship a select. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | n/a | Do not register. |
| `output` | OUTPUT | `{ error }` only if a stub is required | Failures never include secrets |

FUNCTION ports: none.

## Input fields (by operation)

Not applicable. Do not accept `title`, `contentFormat`, `content`, `tags`, `publishStatus`, or `publicationId`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `error` | string | always if stubbed | Stable message: Medium API is unsupported and closed to new integrations. |

## Legal

Evidence (fetched / attempted 2026-08-20):

1. Official API docs repo (archived, read-only): https://github.com/Medium/medium-api-docs — warning **“The Medium API is no longer supported. We do not recommend using it.”**
2. Same README, §2.2 Browser-based authentication: **“We don't allow any new integrations with our API.”**
3. Historical base: `https://api.medium.com/v1` (HTTPS only). Developer agreement in that README pointed at Medium terms: https://medium.com/@feerst/2b405a832a2f
4. Help Center article cited for token policy: https://help.medium.com/hc/en-us/articles/213480228-API-Importing — **live body not retrieved** (Cloudflare JavaScript challenge on 2026-08-20). Title/path is the official “API Importing” help article; do not treat the unreadable body as a license to integrate. Cross-check against (1) and (2).

Forbidden workarounds (LEGAL.md official-API-first):

- Unofficial scrapers, RSS-to-Medium relays that POST to undocumented web routes, session-cookie clients, Playwright/Puppeteer publishing, third-party “Medium API” SaaS proxies.

Rate limits / quota: unpublished for new apps because new apps are not allowed.

PII: do not collect Medium cookies, email, or session tokens.

## Implementation notes

- Handler: **do not register**.
- JDK HTTP: unused.
- If a stub is required for catalog honesty, return `{ error: "Medium API is no longer supported and is closed to new integrations (official docs, archived 2023-03-02). Do not publish via scrape, browser session, or third-party proxy." }` with no outbound request.
- Never throw past dispatch.
