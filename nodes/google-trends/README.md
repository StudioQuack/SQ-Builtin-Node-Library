# Google Trends Search

In-process StudioQuack builtin (`templateId` `8c7d24b0-8434-4a06-8660-ff49c1d0788c`, override with `SQ_TEMPLATE_ID_GOOGLE_TRENDS`). Handler: `com.studioquack.nodelibrary.nodes.data.GoogleTrendsNode`. Java port of [pytrends](https://github.com/GeneralMills/pytrends).

## Overview

The node compares search interest, lists related queries/topics, ranks regions, reads daily hot searches, or autocompletes a seed term.

**There is no official Google Trends API and no API key.** Google does not publish a public Trends REST product. This handler calls the same unofficial `trends.google.com` HTTP endpoints pytrends uses (cookie/NID warmup, `/api/explore` widget tokens, then widgetdata / hottrends / autocomplete). Completely free. Soft practical quota **~30 requests/hour**; bursts typically return HTTP 429.

This is **not** Google Ads, Search Console, YouTube Analytics, or any authenticated Google Cloud API. Google may change, empty-out, or block these endpoints without notice (especially `RELATED_TOPICS` for NID/scraper sessions). See **Security & legal** — use is gated behind `accept_google_terms`.

Provider / reference links:

- Trends UI (what these endpoints back): [trends.google.com](https://trends.google.com/trends)
- Google Terms of Service: [policies.google.com/terms](https://policies.google.com/terms)
- pytrends (unofficial client this node ports): [github.com/GeneralMills/pytrends](https://github.com/GeneralMills/pytrends)
- Prefer official alternatives when they cover the job: [Google Ads Keyword Plan](https://developers.google.com/google-ads/api/docs/keyword-planning/overview), [Search Console API](https://developers.google.com/webmaster-tools)

Needs outbound HTTPS egress to `trends.google.com`. `jobType` is `custom-script-executor`.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | Action + keywords (JSON **array** of 1–5 strings — required even for `trending_searches` and `suggestions`). Optional timeframe/geo/category/country/resolution. |
| `output` | OUTPUT | object | yes | Success (`status=ok` + action-specific keys) or `{error}`. Cookies and widget tokens are never placed here. |

`DataSupport.readInput` unwraps `inputVariables.input`. The handler never throws. There is no separate error port.

`keywords` **must** be a JSON list. A lone string or CSV is rejected (`keywords is required and must be a list of strings (max 5).`).

### `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `action` | string | no | `interest_over_time` | `interest_over_time` \| `related_queries` \| `related_topics` \| `interest_by_region` \| `trending_searches` \| `suggestions` (case-sensitive) | Which unofficial family to call after NID warmup. Other strings → `output.error` listing the six names. |
| `keywords` | list | **yes** | — | JSON array of 1–5 strings (trimmed; blanks dropped; extra entries cut to 5) | `comparisonItem[].keyword` and the autocomplete seed. Required even when `action` is `trending_searches` or `suggestions`. `suggestions` uses `keywords[0]` only. |
| `timeframe` | string | no | `now 7-d` | Exact Google strings (spaces/case matter): `now 1-H`, `now 4-H`, `now 1-d`, `now 7-d`, `today 1-m`, `today 3-m`, `today 12-m`, `today 5-y`, `all`, or `YYYY-MM-DD YYYY-MM-DD` | Copied into every `comparisonItem[].time` on POST `/api/explore`. Ignored by `trending_searches` / `suggestions`. |
| `geo` | string | no | `""` (worldwide) | ISO 3166-1 alpha-2 (`US`, `GB`, `DE`, …); some subdivisions e.g. `US-CA` | `comparisonItem[].geo`. Do **not** pass hottrends names like `united_states` here. |
| `category` | number | no | `0` (all) | non-negative Trends category id (same ids pytrends uses). Common: 5 Computers, 7 Finance, 8 Games, 16 Science, 20 Sports | Explore `req.category`. |
| `country` | string | no | `united_states` | lowercase underscore English **pn** keys: `united_states`, `united_kingdom`, `germany`, `france`, `canada`, `australia`, `india`, `japan`, `brazil`, … | Used **only** for `trending_searches`. Not ISO alpha-2. Wrong: `US` or `United States`. |
| `resolution` | string | no | `COUNTRY` | `COUNTRY` \| `REGION` \| `CITY` \| `DMA` (case-insensitive; sent uppercase) | Used **only** for `interest_by_region` (`GEO_MAP.request.resolution`). `includeLowSearchVolumeGeos` is always `true`. |

### `output` schema

| Field | Type | When present | Purpose |
| --- | --- | --- | --- |
| `status` | string | success | Always `ok` (including empty related lists and `message: "No data returned."`). |
| `data_points` | number | `interest_over_time` with rows | Count of timeline rows. Omitted when `data` is empty. |
| `data` | array | `interest_over_time`, `interest_by_region` | Timeline `{date: YYYY-MM-DD, <kw>: int}` (`isPartial` dropped) or region `{geoName, geoCode, <kw>: int}` (any keyword > 0, sorted by `geoName`, cap 50). |
| `related_queries` | object | `related_queries` | Per-keyword `{top:[{query,value}], rising:[…]}`. Missing widget → empty arrays. |
| `related_topics` | object | `related_topics` | Per-keyword `{top:[flattened maps], rising:[…]}`. Nested keys flattened with `_` (pandas `json_normalize` sep=`_`), e.g. `topic.mid` → `topic_mid`. Empty lists are **success** when Google omits the widget for scraper sessions. |
| `regions_returned` | number | `interest_by_region` with geoMapData | Length of `data` after filter/cap (0–50). |
| `trending` | array of string | `trending_searches` | First 20 entries under the `country` (pn) key. Nested-array rows use the first cell. |
| `suggestions` | array | `suggestions` | `default.topics` from autocomplete (`{mid, title, type}`). |
| `message` | string | empty over-time / region | Exact value `No data returned.` with `status=ok` and `data=[]`. |
| `error` | string | failure only | ToS gate (exact text in How it works), `keywords is required…`, unknown action, `Google Trends rate limited. Wait 60 seconds…`, unofficial handshake failure, or missing hottrends `country` key. |

## Configuration parameters

| Name | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `accept_google_terms` | boolean | no | **`false`** | Truthy: JSON `true`, `"true"`, `"yes"`, `"1"`. Falsy: omitted, `null`, `false`, `"false"`, other strings. | **Legal opt-in.** Read from `configurationParameters` via `DataSupport.inBool(cfg, "accept_google_terms", false)` **before** any Trends HTTP. Default closed. |
| `timeout_seconds` | number | no | `30` | positive integer. not-positive / blank → 30 | Connect+read timeout for warmup, POST `/api/explore`, and widget/hottrends/autocomplete GETs. Raising this does not raise Google's ~30 req/h quota. |

There is no API-key parameter. Cookies are not configuration; they live only in the per-invoke `CookieManager`.

## How it works

1. Dispatch: POST `/invoke` with `templateId` `8c7d24b0-8434-4a06-8660-ff49c1d0788c` → `GoogleTrendsNode`.
2. **ToS gate (runs before input parse).** If `accept_google_terms` is not true, the handler returns this `output.error` and **does not contact Google**:

   > Google Trends is an unofficial endpoint and is not called unless you accept Google's Terms of Service. Set configurationParameter accept_google_terms to true only if you accept https://policies.google.com/terms and will use the data as those terms allow. Prefer official Google products (Google Ads API, Search Console API, or licensed data partners). No request was sent.

3. Validate `keywords` as a list of 1–5 strings. Unknown `action` fails before HTTP (after the ToS gate).
4. Each invoke builds its own JDK `HttpClient` with a dedicated `CookieManager` (`CookiePolicy.ACCEPT_ALL`). The shared `DataSupport` client is **not** used, so NID cookies cannot leak across tenants.
5. **NID warmup:** GET `https://trends.google.com/trends/explore/?geo=US`. Status is ignored; `Set-Cookie` (especially **NID**) stays in the per-request jar. If that response is HTTP 429, non-2xx, or a huge HTML page (>100k chars), also GET `https://trends.google.com/_/TrendsUi/data/batchexecute`.
6. **Explore tokens** (actions that need widgets): POST `https://trends.google.com/trends/api/explore?hl=en-US&tz=0&req={json}` where `req` is `{"comparisonItem":[{"keyword","geo","time"},…],"category":N,"property":""}` (max 5 keywords). Headers: `accept-language: en-US` (Python `TrendReq(hl="en-US", tz=0)`). Response `widgets[]` supplies `id`, `request`, and `token`.
7. **Widget data** GET with `req=Json.write(widget.request)`, `token=widget.token`, `tz=0`, `hl=en-US`:
   - `TIMESERIES` → `/api/widgetdata/multiline`
   - `GEO_MAP` → `/api/widgetdata/comparedgeo` (`resolution` `COUNTRY|REGION|CITY|DMA`, `includeLowSearchVolumeGeos=true`)
   - related → `/api/widgetdata/relatedsearches`
   - `trending_searches` → GET `/hottrends/visualize/internal/data` (no explore tokens)
   - `suggestions` → GET `/api/autocomplete/{urlEncoded keywords[0]}` (no explore tokens)
8. **XSSI prefix.** Google often prefixes JSON with `)]}'` (sometimes `)]}',`). `DataSupport.parseJson` skips to the first `{` or `[`.
9. **HTTP 429:** `Google Trends rate limited. Wait 60 seconds before retrying. Error: …`. Practical cap **~30 req/h**; wait at least a minute. This node does not sleep between invokes — the workflow must space them.
10. Handshake failure (missing widget token, non-JSON, non-2xx on explore): `Google Trends unofficial handshake failed for action '{action}' (cookie/token). …`. `related_topics` treats a missing widget / non-JSON / non-2xx (other than 429) as **success with empty `top`/`rising` lists**.

Cookies and widget tokens are never logged.

## Security & legal

**Unofficial scraping of Google Trends.** Google publishes no public Trends API. Calling `trends.google.com/trends/api/*` and related widget/hottrends URLs is an undocumented consumer-UI protocol. That use can conflict with [Google's Terms of Service](https://policies.google.com/terms).

- The node is **gated** behind instance `accept_google_terms` (default **false**). Until the operator opts in, there is no outbound call — only an error that explains the ToS situation.
- **The workflow operator bears ToS responsibility**, not StudioQuack. StudioQuack does not hold a Trends key, a Google contract for this surface, or an SLA. This README is not legal advice.
- Prefer **official** Google products when they cover the use case: [Google Ads API](https://developers.google.com/google-ads/api/docs/start), [Search Console API](https://developers.google.com/webmaster-tools), or a licensed data partner. Use this node only after the operator has accepted Google's terms.
- No credentials. Isolation is the per-invoke `CookieManager` so one tenant's NID cannot be reused for another.
- Consent / bot walls: warmup may land on a consent interstitial; explore then fails the cookie/token handshake. Retry later or from a network Google accepts — do not loop on 429.
- Outbound calls leave the **customer's** sandbox toward Google. The customer owns that egress.

## Examples

### ToS gate (default config)

`accept_google_terms` omitted or `false`. Any input (even valid keywords) yields:

```json
{
  "error": "Google Trends is an unofficial endpoint and is not called unless you accept Google's Terms of Service. Set configurationParameter accept_google_terms to true only if you accept https://policies.google.com/terms and will use the data as those terms allow. Prefer official Google products (Google Ads API, Search Console API, or licensed data partners). No request was sent."
}
```

No HTTP to `trends.google.com`.

### `interest_over_time`

Instance config: `accept_google_terms` = `true`, `timeout_seconds` = 30.

Input:

```json
{
  "action": "interest_over_time",
  "keywords": ["studioquack", "workflow engine"],
  "timeframe": "now 7-d",
  "geo": "US",
  "category": 0
}
```

Output (shape):

```json
{
  "status": "ok",
  "data_points": 2,
  "data": [
    { "date": "2026-08-13", "studioquack": 12, "workflow engine": 88 },
    { "date": "2026-08-14", "studioquack": 15, "workflow engine": 90 }
  ]
}
```

### `interest_by_region` and `trending_searches`

```json
{
  "action": "interest_by_region",
  "keywords": ["AI automation"],
  "geo": "US",
  "resolution": "REGION",
  "timeframe": "today 3-m"
}
```

```json
{
  "action": "trending_searches",
  "keywords": ["placeholder"],
  "country": "united_states"
}
```

`trending_searches` still requires a `keywords` list (parity with the Python node) even though the hottrends payload is keyed only by `country`.
