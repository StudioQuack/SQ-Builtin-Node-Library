# Jina AI

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/jina-ai/`
- **Researched:** 2026-08-20
- **Official API:** Jina Reader `r.jina.ai` + Search `s.jina.ai` (Reader API 0.5.x OpenAPI)
- **Docs:** https://jina.ai/reader/ · https://r.jina.ai/docs · https://docs.jina.ai/fundamentals/document/
- **Auth:** Bearer API key — secret binding: `api_key` (required for Search; strongly recommended for Reader)

## Scope

Official Jina Search Foundation APIs:

- **`read`** — convert a **caller-supplied URL** to LLM-friendly markdown/JSON via `https://r.jina.ai/{url}` or POST `https://r.jina.ai/`.
- **`search`** — web search + fetched contents via `https://s.jina.ai/` (`q` or path query).
- **`deep_research`** — only if Jina still documents a research endpoint on 2026-08-20 live docs; otherwise **omit**. Catalog listed `research.deep`; do not invent a scrape crawler.

EU residency: optional origin `https://eu.r.jina.ai` / `https://eu.s.jina.ai` via `base_url` / `region=eu`.

This is **not** a paywall/robots bypass. Reader fetches URLs the operator provides; blocked/paywalled sites fail honestly. Honor daemon SSRF/egress. Do not fetch `file:`, `localhost`, or link-local unless the daemon policy already allows it (default deny).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `read` | GET or POST | `https://r.jina.ai/{encodedUrl}` or POST `/` JSON `{ url }` | URL → markdown/JSON |
| `search` | GET or POST | `https://s.jina.ai/` | Query → results + contents |

Prefer POST JSON `{ "url": "https://…" }` / `{ "q": "…" }` with `Accept: application/json` so the handler does not stuff secrets into URLs.

Drop `deep_research` unless https://docs.jina.ai still lists it at implement time.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `read` | no | |
| `api_key` | password | yes for `search`; recommended for `read` | empty | yes | `Authorization: Bearer`. Search without key is blocked on current plans. |
| `region` | select | no | `global` | no | `global` → `r.jina.ai`/`s.jina.ai`; `eu` → `eu.r.jina.ai`/`eu.s.jina.ai` |
| `timeout_seconds` | number | no | `60` | no | Reader can be slow (multi-second fetches) |
| `default_accept` | select | no | `application/json` | no | `application/json` or `text/plain` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No Bearer |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = read`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | Absolute `http`/`https` URL to read. Not a search query. |
| `respond_with` | string | no | If Jina still documents `X-Respond-With` / markdown vs html — pass only documented headers from an allow-list. |

Reject non-http(s) URLs before HTTP.

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Search query (`q`) |
| `site` | string | no | If documented as a filter |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `title` | string | `read` JSON | Page title |
| `url` | string | `read` | Final URL |
| `content` | string | `read` / `search` | Markdown or concatenated results. Size-capped. |
| `results` | array | `search` JSON | `{ title, url, content }` when JSON mode |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

If `Accept: text/plain`, wrap the body in `content` (still cap).

## Legal

- https://jina.ai/reader/
- OpenAPI: https://r.jina.ai/docs
- Operator accepts Jina terms. Token billing is based on output size — cap `content`.
- Target-site terms and robots still apply to the URL being read. This node does not disable robots or authenticate to paywalls.
- Queries and page text may be PII; they leave the host to Jina.

## Implementation notes

- JDK HTTP. Headers: `Authorization: Bearer {api_key}`, `Accept: application/json`, `Content-Type: application/json` on POST, `User-Agent: StudioQuack/1.0`.
- Body cap **2_000_000** bytes (Reader pages are large). Truncate `content` in output if needed and set `truncated: true`.
- Timeout 60 s default. No inner retry.
- Empty `api_key` on `search` → error, no HTTP. On `read`, still prefer requiring a key so production is not on the 20 RPM anonymous pool.
- SSRF: parse URL; allow only `http`/`https`; the daemon's per-engine sandbox posture still applies.
