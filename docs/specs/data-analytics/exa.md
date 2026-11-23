# Exa

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/exa/`
- **Researched:** 2026-08-20
- **Official API:** Exa Search API (`https://api.exa.ai`)
- **Docs:** https://docs.exa.ai/reference/search
- **Auth:** api_key — secret binding: `api_key` (header `x-api-key` or `Authorization: Bearer`)

## Scope

Neural/keyword web search for agents. Official REST: `POST /search`, `POST /findSimilar`, `POST /contents`, `POST /answer`. Prefer header `x-api-key` as shown in current curl examples. Force `stream=false`. Omit deprecated body fields (`startCrawlDate`, `endCrawlDate`, `context`).

Do not scrape the open web as a fallback. `contents` fetches pages **Exa already indexed / was asked to retrieve**, not a robots bypass the operator can aim at arbitrary paywalls without Exa’s own fetching rules.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | POST | `/search` | Query → ranked URLs (+ optional contents) |
| `find_similar` | POST | `/findSimilar` | URL → similar URLs |
| `contents` | POST | `/contents` | Fetch text for result ids/URLs |
| `answer` | POST | `/answer` | Grounded short answer (if the live OpenAPI still lists `/answer`; drop if removed) |

Base: `https://api.exa.ai`. Confirm `/answer` on https://docs.exa.ai at implement time.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | |
| `api_key` | password | yes | empty | yes | `x-api-key`. Never on INPUT. |
| `timeout_seconds` | number | no | `45` | no | `deep` / `deep-reasoning` need more time |
| `default_num_results` | number | no | `10` | no | 1–100 (public max 100) |
| `default_type` | select | no | `auto` | no | `instant` \| `fast` \| `auto` \| `deep-lite` \| `deep` \| `deep-reasoning` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No key |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Search query (min length 1) |
| `num_results` | number | no | 1–100 |
| `type` | string | no | Search mode (see config) |
| `category` | string | no | `company` \| `publication` \| `news` \| `personal site` \| `financial report` \| `people` |
| `include_domains` | array | no | Max 1200 host/path filters |
| `exclude_domains` | array | no | Same |
| `start_published_date` | string | no | ISO-8601; **not** for `company`/`people` (400) |
| `end_published_date` | string | no | Same |
| `user_location` | string | no | ISO country (`US`) |
| `include_text` | boolean | no | If true, set `contents.text=true` |
| `include_highlights` | boolean | no | `contents.highlights=true` |
| `moderation` | boolean | no | Filter unsafe content |

Do not send `stream=true`. Do not send deprecated crawl-date fields.

### `operation = find_similar`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | Source URL (`http`/`https` only) |
| `num_results` | number | no | |
| `exclude_source_domain` | boolean | no | As documented |
| `include_text` | boolean | no | Contents on similar hits |

### `operation = contents`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ids` | array | yes | Document ids from a prior search (strings). Some docs also accept URLs — follow live `/contents` schema. |
| `include_text` | boolean | no | |
| `include_highlights` | boolean | no | |

### `operation = answer`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Question |
| `stream` | boolean | no | Force `false` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `request_id` | string | 2xx | Exa `requestId` |
| `results` | array | search/similar/contents | `{ id, title, url, published_date, author, text?, highlights? }` |
| `answer` | string | `answer` | Answer text |
| `citations` | array | `answer` | URLs if present |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

Do not log `costDollars` as a billing source of truth; optional pass-through is fine. Strip API key if echoed.

## Legal

- https://docs.exa.ai/reference/search
- OpenAPI: https://exa.mintlify.app/search-api/openapi-specification
- Operator accepts Exa terms. Retrieved page text is third-party content.
- `compliance=hipaa` is enterprise-only; do not expose unless the operator’s Exa contract includes it (omit from v1).

## Implementation notes

- POST JSON. Headers: `x-api-key`, `Content-Type: application/json`, `User-Agent: StudioQuack/1.0`.
- `deep` / `deep-reasoning`: timeout default 120 s, still under sandbox cap.
- Body cap 2 MiB for contents-heavy responses.
- SSRF: `find_similar` `url` must be `http`/`https`; daemon egress still applies.
- No vendor SDK.
