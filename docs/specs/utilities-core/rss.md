# RSS

- **Status:** planned
- **Catalog:** `docs/catalog/utilities-core.md` (canonical — protocol node). Also indexed from `docs/catalog/news-reference.md` and `docs/catalog/data-analytics.md`.
- **Folder when built:** `nodes/rss/`
- **Researched:** 2026-08-20
- **Official API:** RSS 2.0 (RSS Advisory Board / Berkman Klein Center) + Atom (IETF RFC 4287). Protocol node, not a vendor.
- **Docs:** https://www.rssboard.org/rss-specification · https://www.rfc-editor.org/rfc/rfc4287
- **Auth:** none | HTTP Basic | `Authorization` header — secret bindings: `basic_password`, `authorization_header`

## Scope

One node that **reads** and **listens** to a syndication feed over HTTP(S). It is a **protocol** node (RSS 2.0 XML and Atom XML), not a publisher product. `read` GETs the configured feed URL once, parses the document, and returns items. `listen` uses the same parser on a poll cadence and emits **only new** items (guid / id / link dedupe). Both operations live on this node — there is no separate feed-trigger node, and the operation name is `listen` (**not** `trigger`).

RSS 2.0 is **not** an IETF RFC. The current RSS 2.0 specification (version 2.0.11, 30 March 2009) is published by the [RSS Advisory Board](https://www.rssboard.org/rss-specification). That document is a Creative Commons Attribution/Share Alike derivative of Dave Winer’s RSS 2.0, originally published by the Berkman Klein Center for Internet & Society at Harvard. Atom is IETF **RFC 4287**. Fetching uses HTTP(S); IRI handling may follow RFC 3987.

Do **not** scrape HTML article bodies. Do **not** follow `item.link` / `atom:link` into a paywall or full-text page. Do **not** add unofficial site scrapers. JSON Feed and RSS 1.0/RDF are out of scope (unsupported feed type → `{ error }`). Honor `robots.txt` by default. The daemon SSRF / egress guard **always** applies; this node must not disable it.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `read` | GET | operator-supplied feed URL | One-shot parse of the current feed |
| `listen` | GET (polled) | same URL | Trigger-style: emit only items not seen before |

Retired / unofficial verbs (`trigger`, HTML scrape, full-text extract) are omitted.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `read` | no | `read` \| `listen` |
| `url` | string | no | empty | no | Default feed URL (`http`/`https` only). Input `url` overrides when present. At least one of config or input must be set. |
| `poll_interval_seconds` | number | no | `300` | no | `listen` only. Floor **15**. Do not busy-loop. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout per GET (robots.txt and feed). Floor 1. |
| `ignore_ssl` | boolean | no | `false` | no | **Lab-only.** `true` skips TLS certificate verification. Default **false**. Production must stay verified. Does **not** disable SSRF/egress. |
| `respect_robots_txt` | boolean | no | `true` | no | Honor origin `robots.txt` before the feed GET (same fail-open posture as Web Browser). |
| `basic_user` | string | no | empty | no | HTTP Basic username for private feeds. |
| `basic_password` | password | no | empty | yes | HTTP Basic password. Never on INPUT, never logged, never on OUTPUT. |
| `authorization_header` | password | no | empty | yes | Full `Authorization` header **value** (e.g. `Bearer …`). If set, do **not** also send Basic. Never on INPUT. |
| `user_agent` | string | no | `StudioQuack-RSS/1.0` | no | Sent on robots.txt and feed GET. Used for robots matching. |
| `max_items` | number | no | `200` | no | Cap items on OUTPUT. Clamp 1–500. |
| `feed_body_max_bytes` | number | no | `1000000` | no | Reject the feed if the body would exceed this (DoS). Clamp ≤ 2_000_000. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include passwords or `Authorization` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = read`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | no | Feed URL; overrides config `url`. Empty + empty config → `{ error }` no HTTP. `http`/`https` only. |
| `max_items` | number | no | Overrides config cap for this invoke. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | no | Same as `read` |
| `max_items` | number | no | Max **new** items to emit this poll |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | always on success path | `ok` (items present or `listen` idle) |
| `format` | string | 2xx parsed | `rss2` \| `atom` |
| `feed` | object | 2xx | Channel/feed metadata: `title`, `link`, `id`, `updated`, `language`, `copyright` (omit missing) |
| `items` | array | 2xx | Normalized entries (see mapping). `listen` with nothing new → `[]` |
| `emitted_count` | number | `listen` | Count of new items this poll |
| `robots` | string | when robots consulted | `allowed` \| `disallowed` \| `unreachable` \| `skipped` |
| `error` | string | failure | Actionable; no secrets |
| `http_status` | number | HTTP error | Provider/origin status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` when present; else 60 on 429, 30 on 5xx/timeout |

### Item object (every item in `items`)

| Field | Type | Source |
|---|---|---|
| `title` | string | RSS `title` / Atom `atom:title` |
| `link` | string | RSS `link` / Atom `atom:link` `rel=alternate` (else first `href`) |
| `id` | string | RSS `guid` / Atom `atom:id` |
| `published` | string | RSS `pubDate` (RFC 822) / Atom `atom:published` else `atom:updated` (RFC 3339). Pass through; do not invent. |
| `author` | string | RSS `author` / Atom `atom:author/atom:name` (else email) |
| `summary` | string | RSS `description` / Atom `atom:summary` else text of `atom:content` **already in the feed**. Strip tags; cap 8_000 chars. **Never** GET the article URL. |
| `enclosures` | array | RSS `enclosure` (`url`, `length`, `type`) / Atom `atom:link` `rel=enclosure` (`href`, `length`, `type`) |

## Legal

- RSS 2.0 spec (not an IETF RFC): https://www.rssboard.org/rss-specification — RSS Advisory Board; Harvard Berkman Klein Center lineage (Winer).
- Atom: [RFC 4287](https://www.rfc-editor.org/rfc/rfc4287).
- Fetch/IRI (optional): [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110) HTTP, [RFC 3987](https://www.rfc-editor.org/rfc/rfc3987) IRI, [RFC 9309](https://www.rfc-editor.org/rfc/rfc9309.html) robots.txt.
- The **feed publisher** owns the items. Honor that publisher’s copyright, terms, and `channel/copyright` / `atom:rights`. Syndication is not a licence to republish full articles or to bypass a paywall.
- `robots.txt` is honored by default. Turning it off is an operator decision (logged warning), not a library bypass.
- Size-cap payloads. Reject huge feeds. Do not HTML-scrape article bodies.
- `ignore_ssl=true` is a **security exception for lab hosts only** (self-signed). It enables MITM against the feed and against Basic/`Authorization` secrets. Default false.
- Daemon **SSRF / egress guard still applies**. Private-feed auth does not expand reachability.

## Implementation notes

- JDK `HttpClient` only. No RSS/Atom vendor SDK. Parse with JDK XML (`DocumentBuilderFactory` / StAX) with **XXE disabled** (`setExpandEntityReferences(false)`, no external DTDs, no XInclude).
- Detect format by root element (`rss` vs `feed` in the Atom namespace `http://www.w3.org/2005/Atom`), not by `Content-Type` alone. Accept `application/rss+xml`, `application/atom+xml`, `application/xml`, `text/xml`.
- TLS verified unless `ignore_ssl=true`. `Redirect`: follow `http`/`https` only; **re-check SSRF on every hop**. Never `file:`, `ftp:` fetch of the feed document.
- Headers: `Accept: application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.1`, `User-Agent` as configured. Auth: `Authorization: Basic {base64(user:pass)}` **or** the `authorization_header` value. Never log the header.
- Empty URL → `{ error: "Feed url is required." }` — no HTTP. Non-http(s) → `{ error }` no HTTP.
- `respect_robots_txt=true` (default): GET `{origin}/robots.txt` first (cap 524_288 bytes), reuse the Web Browser `RobotsTxt` helper. Disallowed → `{ error, robots: "disallowed" }` and **do not** GET the feed. Unreachable robots.txt → fail-open (`robots: "unreachable"`).
- Body cap `feed_body_max_bytes`. If the cap is hit, `{ error: "Feed exceeded size cap." }` — do not parse a truncated document.
- HTTP 4xx/5xx / timeout → `{ error }` on OUTPUT, never throw past dispatch. No in-handler tight retry.
- Logs: operation, host (not full URL query if it may carry tokens), status, item counts, byte sizes. Never passwords, `Authorization`, or item HTML.

### `listen` — engine trigger contract

Honor the engine listen contract:

1. **Do not busy-loop.** Wait `poll_interval_seconds` on a **cancellable** wait between polls.
2. **Backoff** on 429 / 5xx / timeout (exponential, cap 3600 s). Honor `Retry-After`.
3. **Stop** when the workflow / run stops (cancellation, interrupt, engine stop). No dangling poller thread.
4. Preferred: **one GET + diff per engine invoke**; return `{ status: "ok", items }`; the engine must not re-invoke sooner than `poll_interval_seconds`. If the engine keeps the handler resident, apply the same wait / backoff / stop rules inside the handler.
5. **Dedupe:** key = RSS `guid` else Atom `atom:id` else `link` else SHA-256 hex of `title|published`. Store a bounded LRU per environment + canonical feed URL (cap 10_000 keys). First successful poll **seeds** the set and emits nothing. Later polls emit only unseen items, oldest-first, truncated to `max_items`.
6. Do not implement RSS Cloud / `rssCloud` push in this node.
