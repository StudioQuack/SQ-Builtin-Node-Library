# Wikipedia

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/wikipedia/`
- **Researched:** 2026-08-20
- **Official API:** MediaWiki Action API + Wikimedia REST page summary
- **Docs:** Action API https://www.mediawiki.org/wiki/API:Main_page — REST https://www.mediawiki.org/wiki/API:REST_API — etiquette https://www.mediawiki.org/wiki/API:Etiquette — UA policy https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy — rate limits https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits
- **Auth:** none (public read)

## Scope

One IN/OUT node (also usable as an Agent tool) for **official** Wikipedia read APIs: search, page wikitext/HTML extract, REST summary, and Action API extracts. Language wiki is selected via `language` (default `en`) → `https://{lang}.wikipedia.org`.

Required descriptive **User-Agent with contact**. Do not scrape wikipedia.org HTML. Do not use generic UA strings (`Java-http-client`) — Wikimedia may 403.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | GET | `/w/api.php?action=query&list=search` | Title/text search. |
| `get_page` | GET | `/w/api.php?action=query&prop=revisions&rvslots=main&rvprop=content` | Full wikitext for a title. Prefer extracts/summary for display. |
| `get_summary` | GET | `/api/rest_v1/page/summary/{title}` | Short extract + thumbnail (REST). |
| `get_extract` | GET | `/w/api.php?action=query&prop=extracts` | Plaintext or HTML extract (`exintro`, `explaintext`). |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `user_agent` | string | yes | empty | no | Must include app name + contact URL or email. Sent as `User-Agent`. Blank → `{ error }` no HTTP. |
| `language` | string | no | `en` | no | Wiki subdomain (`en`,`de`,`fr`, …). Allowlist `[a-z]{2,3}`. |
| `timeout_seconds` | number | no | `25` | no | `<= 0` → 25. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Titles, search query |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Include attribution fields |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Wire `srsearch`. Alias `q`. |
| `limit` | number | no | Wire `srlimit` (1–50). Default 10. |
| `offset` | number | no | Wire `sroffset`. |

Always send `action=query&list=search&format=json&formatversion=2`.

### `operation = get_page` / `get_extract` / `get_summary`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Page title. URL-encode for REST. |
| `exintro` | boolean | no | `get_extract` only. Intro only. Default true. |
| `explaintext` | boolean | no | `get_extract` only. Plaintext. Default true. |

`get_page`: `prop=revisions&rvslots=main&rvprop=content&titles={title}`.
`get_extract`: `prop=extracts&exintro=&explaintext=&titles={title}` as configured.
`get_summary`: REST path; `Accept: application/json`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `title` | string | page ops | Canonical title. |
| `pageid` | number | when present | MediaWiki page id. |
| `search` | array | `search` | `title`,`pageid`,`snippet`,`wordcount`. |
| `extract` | string | extract/summary | Plaintext/HTML extract. |
| `wikitext` | string | `get_page` | Slot content. Cap length (e.g. 200_000 chars). |
| `description` | string | `get_summary` | REST `description`. |
| `thumbnail` | object | `get_summary` | REST thumbnail (`source`,`width`,`height`) if any. |
| `content_url` | string | success | Canonical article URL for attribution (`https://{lang}.wikipedia.org/wiki/{Title}`). |
| `license` | string | success | `CC BY-SA 4.0` (Wikipedia text). |
| `error` | string | failure | Actionable. |

## Legal

- Terms of Use: https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use
- Reuse: https://www.mediawiki.org/wiki/Wikimedia_APIs/Reusing_free_content
- **Wikipedia article text is CC BY-SA 4.0** (and historically GFDL). **Attribution is mandatory** (authors + article URL). Adaptations must be share-alike.
- Wikimedia **trademarks** (Wikipedia logo, puzzle globe) are **not** licensed by CC BY-SA. Do not brand the operator’s product as Wikipedia.
- User-Agent policy: https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy — contact in UA or risk block.
- Rate limits (subject to change): unidentified ~10 req/min; UA-compliant unauthenticated ~200 req/min. Max ~3 concurrent. Honor `Retry-After` on 429. https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits
- Official-API-first: Action API + REST only. No HTML scrape.

## Implementation notes

- JDK `HttpClient` only. `Redirect.NEVER` or bounded. TLS verified.
- Always `format=json` on Action API. `origin=*` not required server-side.
- Serialize requests (etiquette: wait for one to finish before the next from this node instance).
- Body cap ~8 MiB. Timeout 25 s.
- Failures on OUTPUT `{ error }`. Never throw past dispatch.
