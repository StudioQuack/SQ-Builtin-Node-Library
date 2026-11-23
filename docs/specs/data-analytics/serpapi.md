# SerpAPI

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/serpapi/`
- **Researched:** 2026-08-20
- **Official API:** SerpApi Search API (`GET https://serpapi.com/search`)
- **Docs:** https://serpapi.com/search-api
- **Auth:** api_key — secret binding: `api_key` (query param `api_key`)

## Scope

Calls **SerpApi’s** documented HTTP API (a licensed SERP-as-a-service). One node: `operation` selects the SerpApi `engine` (or a thin alias). This is not Google’s official API and not HTML scrape from this library — the HTTP peer is `serpapi.com`. The **operator** remains responsible for destination-engine terms (Google, Bing, etc.) and for SerpApi’s own terms.

Do not add unofficial Google/Bing HTML scrapers. Do not send `api_key` on INPUT.

Supported engines are whatever SerpApi documents live; implement the engines below as first-class `operation` values and map each to `engine=`. Unknown `operation` → `{ error }` without HTTP.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `google` | GET | `https://serpapi.com/search` `engine=google` | Google organic SERP JSON |
| `google_news` | GET | same `engine=google_news` | Google News |
| `google_images` | GET | same `engine=google_images` | Google Images |
| `google_scholar` | GET | same `engine=google_scholar` | Google Scholar |
| `google_maps` | GET | same `engine=google_maps` | Maps/local |
| `google_shopping` | GET | same `engine=google_shopping` | Shopping |
| `bing` | GET | same `engine=bing` | Bing via SerpApi (not retired Azure Bing v7) |

Default `output=json`. Do not request `output=html` (raw SERP HTML) from this node.

Confirm each `engine=` slug on https://serpapi.com (engine list) at implement time; drop any slug SerpApi has retired.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `google` | no | Maps to `engine` |
| `api_key` | password | yes | empty | yes | SerpApi private key (`api_key` query). Never logged. |
| `timeout_seconds` | number | no | `30` | no | SERP fetches are slower than first-party search APIs |
| `no_cache` | boolean | no | `false` | no | SerpApi `no_cache` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Engine-specific query fields |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = google` | `google_news` | `google_images` | `google_scholar` | `google_shopping`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | `q`. Empty → error. |
| `location` | string | no | Geographic origin |
| `gl` | string | no | Country code |
| `hl` | string | no | Interface language |
| `google_domain` | string | no | e.g. `google.com` |
| `start` | number | no | Pagination offset |
| `num` | number | no | Result count where the engine documents it |
| `tbm` | string | no | Only if the chosen engine still documents it; prefer dedicated operations instead |
| `device` | string | no | `desktop` \| `tablet` \| `mobile` |

### `operation = google_maps`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | `q` |
| `ll` | string | no | Lat/lng `@lat,lng,zoom` as SerpApi documents |
| `type` | string | no | Maps search type if documented |
| `hl` | string | no | Language |

### `operation = bing`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Bing query param as SerpApi Bing engine documents (`q`) |
| `cc` | string | no | Country if documented |
| `mkt` | string | no | Market if documented |

Pass through additional documented engine params from input only if they are in an allow-list copied from SerpApi’s engine page (prevents leaking secrets as query args). Never forward `api_key` from input.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `search_metadata` | object | 2xx | Engine, id, status from SerpApi (strip any echoed key) |
| `organic_results` | array | google/bing | Normalized `{ title, link, snippet, position }` |
| `news_results` | array | news | Same shape |
| `images_results` | array | images | `{ title, original, thumbnail, link }` |
| `shopping_results` | array | shopping | Title/price/link |
| `scholar_results` | array | scholar | Title/link/snippet/cited_by |
| `local_results` | array | maps | Places list as returned |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

Do not return `html` or full raw SERP dumps. Cap JSON at 1 MiB.

## Legal

- https://serpapi.com/search-api
- https://serpapi.com/legal (terms — confirm live URL)
- Operator must comply with **SerpApi terms** and with the **destination search engine’s terms**. This node does not grant Google/Bing scrape rights.
- Not a replacement for Bing Search API v7 (retired); Bing here is SerpApi’s Bing engine only.
- Queries may contain PII; operator is controller.

## Implementation notes

- GET `https://serpapi.com/search` with `api_key`, `engine`, `output=json`, plus allow-listed fields. Prefer `search.json` only if docs still treat it as identical.
- JDK HTTP. TLS verified. Body cap 1 MiB. Timeout 30 s.
- Empty key → error, no HTTP.
- Redact `api_key` if SerpApi echoes it in error JSON.
- No inner retry loop. 429 → `retry_after_seconds` from `Retry-After` or 60.
