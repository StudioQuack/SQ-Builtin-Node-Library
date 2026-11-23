# Google Programmable Search

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/google-programmable-search/`
- **Researched:** 2026-08-20
- **Official API:** Custom Search JSON API v1 (`customsearch.googleapis.com`)
- **Docs:** https://developers.google.com/custom-search/v1/overview
- **Auth:** api_key + Programmable Search Engine id (`cx`) — secret binding: `api_key`

## Scope

Calls Google’s **Custom Search JSON API** (`cse.list`) against an operator-owned Programmable Search Engine (`cx`). This is **not** the retired Bing API and **not** unofficial Google HTML scrape.

**Sunset (verified 2026-08-20):** Google states the JSON API is **closed to new customers** and existing pricing applies only until **discontinuation on 2027-01-01**. Implement only for operators who already have a CSE + Cloud API key. Operators without CSE access should use Brave Search or shipped Web Search (Tavily).

Do not implement `cse.siterestrict` unless the operator’s engine is configured for site-restrict; optional second operation is acceptable because it is the same official API.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `cse_list` | GET | `https://customsearch.googleapis.com/customsearch/v1` | Web (or image) results for `q` against `cx` |
| `cse_siterestrict` | GET | `https://customsearch.googleapis.com/customsearch/v1/siterestrict` | Same, for engines limited to URL patterns |

Legacy host `https://www.googleapis.com/customsearch/v1` is documented as equivalent; prefer `customsearch.googleapis.com`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `cse_list` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Google Cloud API key (`key` query param). Never on INPUT. |
| `cx` | string | yes | empty | no | Programmable Search Engine ID |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout |
| `default_num` | number | no | `10` | no | Default page size (Google max **10** per request) |

OAuth scope `https://www.googleapis.com/auth/cse` exists on the method; API key + `cx` is the documented app path. Do not require OAuth unless Google later mandates it for the operator’s project.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-invoke query; `cx` may override instance `cx` |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No API key |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = cse_list` | `cse_siterestrict`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | `q`. Alias `q`. Empty → error, no HTTP. |
| `num` | number | no | 1–10 inclusive (Google hard max) |
| `start` | number | no | 1-based index of first result. `start + num` must be ≤ 100. |
| `cx` | string | no | Override instance engine id |
| `search_type` | string | no | `image` for image search; omit for web |
| `lr` | string | no | Language restrict (`lang_en`, …) |
| `cr` | string | no | Country restrict |
| `safe` | string | no | `active` \| `off` |
| `date_restrict` | string | no | e.g. `d7`, `m1` as documented |
| `site_search` | string | no | Restrict to a site |
| `file_type` | string | no | File type filter |
| `sort` | string | no | Sort expression as documented |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `search_information` | object | 2xx | `total_results`, `search_time` from `searchInformation` |
| `items` | array | 2xx | Each: `title`, `link`, `snippet`, `display_link`, optional `pagemap` / `image` |
| `error` | string | failure | Includes sunset/closed-project hints on 403 |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 / 30 |

## Legal

- Overview + pricing/sunset: https://developers.google.com/custom-search/v1/overview
- Method `cse.list`: https://developers.google.com/custom-search/v1/reference/rest/v1/cse/list
- REST usage: https://developers.google.com/custom-search/v1/using_rest
- Google APIs Terms: https://developers.google.com/terms
- 100 free queries/day historically; paid $5 / 1000 up to 10k/day **for existing customers until 2027-01-01**. Confirm live Cloud billing.
- Snippets are Google search results; redistribution follows Programmable Search / Google terms. Do not scrape google.com/search.

## Implementation notes

- JDK HTTP GET. Query params: `key`, `cx`, `q`, plus optional filters. **Never log `key`.**
- `num` clamp 1–10. `start + num > 100` → `{ error }` without calling Google (documented hard cap).
- Body cap 1 MiB. Timeout 25 s. TLS verified. `Redirect.NEVER`.
- 429 / 5xx / timeout mapping as Brave spec. No inner retry loop.
