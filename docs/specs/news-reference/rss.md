# RSS

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/rss/`
- **Researched:** 2026-08-20
- **Official API:** RSS 2.0 (RSS Advisory Board) + Atom (IETF RFC 4287) over HTTP(S)
- **Docs:** RSS 2.0 https://www.rssboard.org/rss-specification — Atom https://www.rfc-editor.org/rfc/rfc4287
- **Auth:** none, or HTTP Basic / `Authorization` header for private feeds — secret bindings: `username`+`password` or `authorization`

## Scope

Protocol node (not a vendor). One node collapses feed **read** and feed **listen**. HTTP GET a feed URL, parse **RSS 2.0** or **Atom 1.0**, return items.

RSS 2.0 is a Harvard / RSS Advisory Board specification — **not an IETF RFC**. Atom is **RFC 4287**. Do not scrape HTML article bodies. Do not disable the daemon SSRF/egress guard. `ignore_ssl` is lab-only and default false.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `read` | GET | operator-supplied feed URL | One-shot fetch + parse. |
| `listen` | GET | same URL, polled | Trigger-style: poll `poll_interval_seconds`, emit **new** items only (dedupe `guid` / Atom `id` / `link`). Back off. Stop when the workflow stops. Do not busy-loop. |

No `trigger` verb — catalog + NODE_CATALOG use `listen`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `read` | no | `read` \| `listen` |
| `url` | string | no | empty | no | Default feed URL if input `url` blank. Must be `http`/`https`. |
| `poll_interval_seconds` | number | no | `300` | no | `listen` only. Min 60. Floor invalid values to 300. |
| `ignore_ssl` | boolean | no | `false` | no | **Lab only.** `true` skips TLS verify. Production must stay `false`. |
| `username` | string | no | empty | no | HTTP Basic user (with `password`). |
| `password` | password | no | empty | yes | HTTP Basic password. |
| `authorization` | password | no | empty | yes | Raw `Authorization` header value if not Basic (e.g. `Bearer …`). XOR with Basic: if set, do not also send Basic. |
| `timeout_seconds` | number | no | `25` | no | Per-request timeout. `<= 0` → 25. |
| `max_items` | number | no | `50` | no | Cap items returned/emitted (1–200). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | `url` may override config |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo passwords |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = read` and `listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | if config `url` blank | Absolute `http`/`https` feed URL. Invalid scheme → `{ error }` no HTTP. |

`listen` uses the same fields; polling is driven by config, not by extra input.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `format` | string | success | `rss2` \| `atom` |
| `feed_title` | string | success | Channel / feed title. |
| `feed_link` | string | success | Channel link / feed html link. |
| `items_returned` | number | success | `items.length`. |
| `items` | array | success | Mapped: `id` (guid/id), `title`, `link`, `published`, `author`, `summary` (description/content truncated), `enclosures[]` (`url`,`type`,`length`). |
| `error` | string | failure | Actionable. |

`listen` success payloads contain **only new** items since last poll (may be empty array with `status=ok`).

## Legal

- Each feed is copyrighted by its publisher. Parsing a feed is not a licence to republish full articles. Honor the publisher’s terms and `robots.txt` by default.
- Size-cap the response (default **1 MiB** feed body; 8 MiB hard max). Reject huge feeds with `{ error }`.
- No HTML scrape of `item.link`. No paywall bypass. No cookie jars for subscription sites.
- `ignore_ssl=true` is a security exception (MITM). Document it as lab-only.
- SSRF: daemon egress policy still applies. Private-network URLs fail closed unless the operator’s egress policy allows them.

## Implementation notes

- JDK `HttpClient` only. `Accept: application/rss+xml, application/atom+xml, application/xml, text/xml`.
- `User-Agent: StudioQuack/1.0 (rss; support@studioquack.com)`.
- Detect format from root element (`rss` vs Atom `feed`) not from Content-Type alone.
- `listen` persistence: store last-seen ids **in the node/runtime listen store**, not engine vars (REF-only). Cap stored ids (e.g. 500).
- `Redirect.NORMAL` max 3, still SSRF-checked per hop.
- TLS verify unless `ignore_ssl` (lab).
- Failures on OUTPUT `{ error }`. Never throw past dispatch.
