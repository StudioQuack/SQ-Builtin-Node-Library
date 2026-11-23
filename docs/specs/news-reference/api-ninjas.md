# API Ninjas

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/api-ninjas/`
- **Researched:** 2026-08-20
- **Official API:** API Ninjas REST (`api.api-ninjas.com`) — **reference subset only**
- **Docs:** https://api-ninjas.com/api (directory); per-op pages under https://api-ninjas.com/api/{quotes|trivia|facts|jokes|riddles|bucketlist}
- **Auth:** api_key — secret binding: `api_key` (header `X-Api-Key`)

## Scope

One node for API Ninjas **reference / entertainment** endpoints only: quotes, trivia, facts, jokes, riddles, and bucket list. Do **not** wrap the rest of the ~140-API catalog (finance, geo, nutrition, etc.).

Call documented HTTPS JSON on `https://api.api-ninjas.com`. Quotes currently live on **`/v2/…`**; the other five ops use **`/v1/…`**. Do not scrape api-ninjas.com HTML. Do not register additional verbs from the directory.

Quote text may still be third-party copyright; this node is a transport, not a licence grant.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `quotes` | GET | `/v2/quotes` (default); `/v2/randomquotes`; `/v2/quoteoftheday` | Aphoristic quotes. `variant` selects the documented path. |
| `trivia` | GET | `/v1/trivia` (default); `/v1/triviaoftheday` | Trivia Q&A. |
| `facts` | GET | `/v1/facts` (default); `/v1/factoftheday` | Random facts. |
| `jokes` | GET | `/v1/jokes` (default); `/v1/jokeoftheday` | Jokes. |
| `riddles` | GET | `/v1/riddles` | Riddles (no of-the-day path documented). |
| `bucketlist` | GET | `/v1/bucketlist` | One random bucket-list idea. No query params. |

Base origin: `https://api.api-ninjas.com`.

Omit `/v2/quoteauthors` (premium author list) — not in the catalog ops. Omit Dad Jokes / Chuck Norris / Advice / Historical Figures.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `quotes` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Header `X-Api-Key` only. Never on INPUT, never logged. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |
| `default_limit` | number | no | `1` | no | Default `limit` when input omits it (premium-gated on several endpoints). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = quotes`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `variant` | string | no | `list` (default) → `/v2/quotes`; `random` → `/v2/randomquotes`; `of_the_day` → `/v2/quoteoftheday`. Unknown → `{ error }` no HTTP. |
| `categories` | string | no | Comma-separated include tags (`wisdom`, `philosophy`, `life`, `truth`, `inspirational`, `relationships`, `love`, `faith`, `humor`, `success`, `courage`, `happiness`, `art`, `writing`, `fear`, `nature`, `time`, `freedom`, `death`, `leadership`). Ignored for `of_the_day`. |
| `exclude_categories` | string | no | Comma-separated exclude tags. Ignored for `of_the_day`. |
| `author` | string | no | Partial author match. Ignored for `of_the_day`. |
| `work` | string | no | Partial work/title match. Ignored for `of_the_day`. |
| `limit` | number | no | 1–100. Premium-only on `/v2/quotes` and `/v2/randomquotes`. Default 1. |
| `offset` | number | no | Pagination skip. Premium-only on `/v2/quotes`. |

### `operation = trivia`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `of_the_day` | boolean | no | `true` → `/v1/triviaoftheday` (no filters). |
| `category` | string | no | Premium. One of: `artliterature`, `language`, `sciencenature`, `general`, `fooddrink`, `peopleplaces`, `geography`, `historyholidays`, `entertainment`, `toysgames`, `music`, `mathematics`, `religionmythology`, `sportsleisure`. |
| `limit` | number | no | 1–30. Premium. Default 1. |

### `operation = facts`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `of_the_day` | boolean | no | `true` → `/v1/factoftheday`. |
| `limit` | number | no | 1–100. Premium-only. Default 1. |

### `operation = jokes`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `of_the_day` | boolean | no | `true` → `/v1/jokeoftheday`. |
| `limit` | number | no | 1–100. Premium-only. Default 1. |

### `operation = riddles`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | 1–20. Premium-only. Default 1. |

### `operation = bucketlist`

No input fields. Empty object is valid.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `items` | array | success | Normalized rows (see below). Always an array, even when the provider returns one object (`bucketlist`). |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx/timeout |

Normalize each item:

- `quotes`: `quote`, `author`, `work`, `categories`
- `trivia`: `category`, `question`, `answer`
- `facts`: `fact`
- `jokes`: `joke`
- `riddles`: `title`, `question`, `answer`
- `bucketlist`: `item` (from provider object `{ item }`)

## Legal

- Terms: https://api-ninjas.com/tos (Last Updated: April 27, 2026)
- Pricing / commercial: https://api-ninjas.com/pricing — **Free plan: no commercial use**, 3 000 calls/month, 100 calls/hour, **attribution required**, caching/storing not allowed. Paid plans: commercial use while the subscription is active.
- Do not scrape the website. Call only `api.api-ninjas.com` with a key.
- Do not redistribute or resell raw Output except as permitted display/analysis on an **active paid** plan. Free plan must not share Output with third parties.
- Quote/joke/riddle text may still be third-party copyright; operator is responsible for display and redistribution.
- Do not build a competing API from Output (ToS §3.3 / §7.3).
- Secrets: key in config only. Logs: operation, status, item count, sizes — never `api_key` or full quote/joke bodies.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `X-Api-Key: {api_key}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Empty `api_key` → `{ error: "Missing API Ninjas key. Bind api_key in node parameters." }` — no HTTP.
- Response body cap **1_000_000** bytes. Timeout default 25 s (floor 1).
- 401/403 → `{ error }` truncated body (240 chars), key redacted to `***`.
- 429 → `{ error, http_status: 429, retry_after_seconds: 60 }` (honor `Retry-After` if present). 5xx / timeout → `retry_after_seconds: 30`.
- Do not auto-retry inside the handler.
- Premium-only query params (`limit`, `offset`, `category` on trivia): send them when the operator set them; if the provider returns a plan error, map to `{ error }` with the truncated body. Do not probe plan tier.
- `bucketlist` returns a JSON **object**; wrap as `items: [ { item } ]`.
