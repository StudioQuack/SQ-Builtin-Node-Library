# Wikipedia / Wikidata

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md` (canonical; News & Reference Wikipedia / Wikidata rows should cross-ref this node)
- **Folder when built:** `nodes/wikipedia-wikidata/`
- **Researched:** 2026-08-20
- **Official API:** MediaWiki Action API + REST v1 + Wikibase `wbgetentities`
- **Docs:** https://www.mediawiki.org/wiki/API:Main_page · https://www.mediawiki.org/wiki/API:REST_API/Reference · https://www.wikidata.org/wiki/Wikibase/API
- **Auth:** none for public read (OAuth optional, omit in v1) — no secret. Contact lives in `user_agent`.

## Scope

One node for Wikipedia article search/page/summary and Wikidata entity GET. SPARQL belongs on **Wikidata SPARQL** (`wikidata-sparql.md`), not here.

Required Wikimedia User-Agent with **contact** (email, site, or wiki user). Generic agents (`Java-http-client`, `curl`) are blocked. Do not scrape `wikipedia.org` HTML. Do not impersonate a browser UA.

CC BY-SA for Wikipedia prose; Wikidata statements are CC0. Attribution required when displaying article text.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `wiki_search` | GET | `https://{lang}.wikipedia.org/w/api.php` `action=query&list=search` | Full-text search |
| `wiki_page` | GET | same `action=parse` or `action=query&prop=extracts\|revisions` | One page extract / wikitext |
| `wiki_summary` | GET | `https://{lang}.wikipedia.org/api/rest_v1/page/summary/{title}` | Lead extract + thumbnail |
| `wikidata_entity` | GET | `https://www.wikidata.org/w/api.php` `action=wbgetentities` | Entity JSON by Q/P id |

Prefer `format=json` + `formatversion=2` on Action API. REST summary is the right call for previews (not HTML scrape).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `wiki_search` | no | |
| `user_agent` | string | yes | empty | no | Must match WMF policy: `StudioQuack-Wikipedia/1.0 (https://…; ops@…)` plus optional library token. Empty → error, no HTTP. |
| `language` | string | no | `en` | no | Wiki subdomain (`en`, `de`, …) interpolated into `https://{language}.wikipedia.org`. |
| `timeout_seconds` | number | no | `20` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = wiki_search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Search string (`srsearch`) |
| `limit` | number | no | `srlimit` 1–50 (default 10) |
| `language` | string | no | Override instance language |
| `offset` | number | no | `sroffset` |

### `operation = wiki_page`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Page title |
| `language` | string | no | |
| `intro_only` | boolean | no | `exintro=1` when using extracts |

Cap extract length. Do not return full HTML dump (`prop=revisions` wikitext only if `format=wikitext` requested; default plaintext extracts).

### `operation = wiki_summary`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | URL-encoded title (`Albert_Einstein`) |
| `language` | string | no | |
| `redirect` | boolean | no | REST `redirect` query; default follow |

### `operation = wikidata_entity`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ids` | string or array | yes | Q/P ids (`Q42` or `Q42\|Q43`) |
| `props` | string | no | `wbgetentities` `props` (default `labels\|descriptions\|claims`) |
| `languages` | string | no | Label language codes |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `results` | array | `wiki_search` | `{ title, snippet, pageid, wordcount }` |
| `title` | string | page/summary | Canonical title |
| `extract` | string | page/summary | Plain-text lead / extract |
| `url` | string | page/summary | Canonical content URL |
| `thumbnail` | object | summary | `{ source, width, height }` if present |
| `entities` | object | `wikidata_entity` | `entities` map from Wikibase |
| `error` | string | failure | Include 403 UA-policy hint |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` |

## Legal

- Action API: https://www.mediawiki.org/wiki/API:Main_page · etiquette https://www.mediawiki.org/wiki/API:Etiquette
- REST: https://www.mediawiki.org/wiki/API:REST_API/Reference
- UA policy: https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy
- Wikibase API: https://www.wikidata.org/wiki/Wikibase/API
- Wikipedia text: CC BY-SA 4.0; link the article. Wikidata: CC0.
- Rate-limit: courtesy; `maxlag` on Action API (`maxlag=5`). Heavy SPARQL → other node.
- Do not scrape HTML. Do not use Wikimedia as a general web crawler.

## Implementation notes

- Always send the configured `user_agent` as `User-Agent` (and `Accept-Encoding: gzip`). Never a browser UA. Host is `https://{language}.wikipedia.org`.
- `User-Agent` empty or matching `Java-http-client` / `curl` → `{ error: "Wikimedia requires an identifying User-Agent with contact." }`
- Body cap 1 MiB. Timeout 20 s. `maxlag=5` on Action API.
- REST 302 on redirects: `Redirect.NEVER` then follow at most one redirect to the same host if `redirect` not false — or pass REST `redirect=false` and return the redirect target title.
- Logs: language, title length, status — not extract text.
