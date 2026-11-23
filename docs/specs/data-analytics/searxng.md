# SearXNG

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/searxng/`
- **Researched:** 2026-08-20
- **Official API:** SearXNG Search API (`GET|POST /search`, `format=json`)
- **Docs:** https://docs.searxng.org/dev/search_api.html (docs dated 2026.8.20)
- **Auth:** none, or HTTP Basic if the operator’s instance requires it — secret binding: `basic_password`

## Scope

Talks to **an instance the operator is allowed to automate** (self-hosted or a private instance with JSON enabled). Official params: `q`, `format=json`, optional `categories`, `language`, `pageno`, `time_range`, `safesearch`.

JSON is **off by default** on SearXNG (`search.formats` in `settings.yml`). Requesting JSON when disabled returns **HTTP 403**. Many **public** instances disable JSON specifically to block bots. **Do not ship a default public instance URL.** Do not scrape HTML when JSON is 403.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | GET | `{base_url}/search` | JSON search |

POST `application/x-www-form-urlencoded` to `/search` is equivalent; GET is enough.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | |
| `base_url` | string | yes | empty | no | Origin of **the operator’s** instance, e.g. `https://searx.example.org`. No trailing `/search`. Empty → error. |
| `basic_username` | string | no | empty | no | Optional Basic auth user |
| `basic_password` | password | no | empty | yes | Optional Basic auth password |
| `timeout_seconds` | number | no | `30` | no | Metasearch can be slow |
| `default_language` | string | no | empty | no | `language` query |
| `default_safesearch` | select | no | `1` | no | `0` \| `1` \| `2` per SearXNG |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | `q` |
| `categories` | string | no | Comma-separated categories |
| `language` | string | no | Override default |
| `pageno` | number | no | Default 1 |
| `time_range` | string | no | `day` \| `month` \| `year` |
| `safesearch` | number | no | `0` `1` `2` |

Always send `format=json`. Do not send `format=html`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx JSON | `ok` |
| `query` | string | 2xx | Echo |
| `number_of_results` | number | 2xx | If present |
| `results` | array | 2xx | `{ title, url, content, engine }` from `results[]` |
| `error` | string | failure | 403 must say JSON format is disabled on this instance |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

If `Content-Type` is HTML despite `format=json`, fail with `{ error: "SearXNG returned HTML. Enable JSON on the instance (search.formats) or use an instance you operate." }` — do not parse HTML results.

## Legal

- https://docs.searxng.org/dev/search_api.html
- Operator must have permission to automate the instance. Public-instance ToS often **forbid** automated JSON clients.
- Upstream engine terms still apply to aggregated hits.
- Do not hardcode a public instance from searx.space.

## Implementation notes

- Validate `base_url` is absolute `http`/`https`; strip trailing slashes; **do not** allow a baked-in default host.
- GET `{base_url}/search?q=…&format=json&…`. Optional `Authorization: Basic`.
- SSRF: the daemon's per-engine egress posture still applies (private network denied). Document that pointing `base_url` at a link-local or private address is subject to those host rules.
- Body cap 1 MiB. Timeout 30 s. TLS verified (no `ignoreTls` — if an operator needs a lab CA, that is daemon config, not this node).
- `User-Agent: StudioQuack/1.0` (identifiable; do not impersonate a browser).
