# RSS

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md` (canonical; News & Reference and Utilities RSS rows cross-ref this node)
- **Folder when built:** `nodes/rss/`
- **Researched:** 2026-08-20
- **Official API:** RSS 2.0 + Atom 1.0 (protocols, not a vendor)
- **Docs:** https://www.rssboard.org/rss-specification · https://www.rfc-editor.org/rfc/rfc4287.html
- **Auth:** none, or HTTP Basic — secret binding: `basic_password`

## Scope

One node for **feed parse** and **feed listen**. HTTP Request can GET the bytes; this node still ships because it must parse RSS 2.0 / Atom, normalize items, and optionally poll.

- `read` — GET the feed URL once, parse, return items.
- `trigger.listen` — same GET/parse on a schedule. **Not a separate node.** Engine timer / message event invokes this operation; the handler returns new items since last `etag` / `last_modified` / item `guid`. Do not open a standing socket. Do not scrape HTML pages when the URL is not a feed.

Honor copyright of feed contents. Do not follow item links into article HTML (that is HTTP Request / Web Browser). `ignore_tls` defaults **false**; enabling it is a lab-only operator choice and must not be the production default.

JSON Feed (`https://www.jsonfeed.org/version/1.1`) is optional if `Content-Type` is JSON and the body has `items` — only because it is a documented feed format, not HTML scrape. If adding it increases scope, ship RSS+Atom first.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `read` | GET | `{url}` | One-shot parse |
| `trigger.listen` | GET | `{url}` | Poll; return **new** items vs cursor |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `read` | no | |
| `url` | string | yes | empty | no | Default feed URL (`http`/`https` only). Input may override. |
| `basic_username` | string | no | empty | no | Optional Basic auth |
| `basic_password` | password | no | empty | yes | Optional Basic auth |
| `poll_seconds` | number | no | `300` | no | Hint for `trigger.listen` (min 60). Engine timer should use this. |
| `ignore_tls` | boolean | no | `false` | no | **Default verify TLS.** `true` only for lab feeds. |
| `timeout_seconds` | number | no | `20` | no | |
| `max_items` | number | no | `50` | no | Cap items on output (1–200) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, items }` or `{ error }` | No passwords |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = read`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | no | Override instance `url` |
| `max_items` | number | no | Override cap |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | no | Override |
| `etag` | string | no | Prior `ETag` (If-None-Match) |
| `last_modified` | string | no | Prior Last-Modified (If-Modified-Since) |
| `seen_guids` | array | no | Item ids already emitted (cap 500) |
| `max_items` | number | no | |

HTTP 304 → `{ status: "ok", items: [], not_modified: true }`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx/304 | `ok` |
| `feed_title` | string | parsed | Channel/feed title |
| `feed_url` | string | parsed | Self link if present |
| `etag` | string | if header | Persist for next listen |
| `last_modified` | string | if header | Persist |
| `not_modified` | boolean | 304 | |
| `items` | array | parsed | `{ guid, title, url, published, summary, author }` — `summary` from `description` / `atom:summary` / `content` truncated |
| `error` | string | failure | Not-a-feed / SSRF / HTTP |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

Strip HTML from summaries (entities decoded, tags dropped). Do not inline enclosure bytes; enclosure URL only. If an enclosure must be downloaded, that is HTTP Request with type `file` — out of this node’s v1.

## Legal

- RSS 2.0: https://www.rssboard.org/rss-specification
- Atom: https://www.rfc-editor.org/rfc/rfc4287.html
- Feed copyright remains with the publisher. Redistribution follows the feed’s licence / site terms.
- Do not use this node to scrape HTML indexes. If `Content-Type` is `text/html` and the body is not RSS/Atom, fail closed.
- `robots.txt` does not replace feed terms; still do not hammer (`poll_seconds` min 60).
- Basic auth credentials never logged.

## Implementation notes

- JDK `HttpClient`. TLS verified unless `ignore_tls=true` (log a warning shape only: `tls_insecure=true`, not the URL query string if it has tokens).
- SSRF: `http`/`https` only; the daemon's per-engine sandbox posture still applies.
- Body cap **1_000_000** bytes. XML bomb: disable DTDs / external entities in the parser (JDK `XMLInputFactory` `SUPPORT_DTD=false`, `IS_SUPPORTING_EXTERNAL_ENTITIES=false`).
- Detect RSS (`rss`/`channel`/`item`) vs Atom (`feed`/`entry` in `http://www.w3.org/2005/Atom`).
- `trigger.listen` is stateless per invoke: caller (engine/workflow) stores `etag` / `seen_guids`. Handler does not persist across runs.
- User-Agent: `StudioQuack-RSS/1.0`.
- Timeout 20 s. No inner retry. 304 is success.
