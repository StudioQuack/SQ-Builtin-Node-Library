# Giphy

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/giphy/`
- **Researched:** 2026-08-20
- **Official API:** GIPHY API v1 (`https://api.giphy.com`)
- **Docs:** https://developers.giphy.com/docs/api/ — endpoints: https://developers.giphy.com/docs/api/endpoint/ — schema: https://developers.giphy.com/docs/api/schema/ — renditions/ratings: https://developers.giphy.com/docs/optional-settings/
- **Auth:** api_key — secret binding: `api_key` (query `api_key`, required)

## Scope

One node for the **official GIPHY API v1 GIF** surface. The designer picks `operation`; the handler calls HTTPS `GET https://api.giphy.com/v1/gifs/…` with the operator’s key. Use this to search, list trending GIFs, translate a phrase to one GIF, pick a random GIF, or fetch one GIF by id.

**GIFs only.** Stickers live at `/v1/stickers/*` (same verbs). Do not add sticker operations in this version. A later `media_type=gif|sticker` switch that only changes the path prefix is acceptable if catalog space remains; until then, never call `/v1/stickers/*`.

Do **not** scrape `giphy.com` HTML. Do **not** ship a GIPHY vendor SDK (`@giphy/js-fetch-api`, iOS/Android SDK). Do **not** add upload, emoji v2, autocomplete, channel search, or analytics pingback as operations.

GIPHY documents that Search/Trending “be made from the client side” and forbids proxying API or media requests. This node still uses the **official REST API** from the daemon (not a site scrape). Operators whose GIPHY key agreement forbids server-side use must not enable the node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | GET | `/v1/gifs/search` | Keyword / phrase search (`q` required, max 50 chars) |
| `trending` | GET | `/v1/gifs/trending` | Current trending GIF feed |
| `translate` | GET | `/v1/gifs/translate` | One GIF for a phrase (`s` required) |
| `random` | GET | `/v1/gifs/random` | One random GIF; optional `tag` |
| `gif_get` | GET | `/v1/gifs/{gif_id}` | Metadata for one GIF id |

Base origin: `https://api.giphy.com`. Prefer HTTPS. Never `http://`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | GIPHY API key. Query `api_key` only. Never INPUT, never logged. |
| `timeout_seconds` | number | no | `25` | no | Connect + read timeout. `<= 0` → 25. Floor 1 s. |
| `default_rating` | select | no | empty | no | Default `rating` when input omits it: `g` \| `pg` \| `pg-13` \| `r`. Empty → GIPHY returns all ratings. |
| `default_limit` | number | no | `25` | no | Default `limit` for `search` / `trending` (GIPHY default 25). Beta keys: max 50 on search. |
| `bundle` | string | no | empty | no | Optional renditions-on-demand bundle (e.g. `messaging_non_clips`). Omit → full `images` object. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Wire `q`. Exact user terms; do not spell-correct. Max 50 chars. `@user` searches a public channel. Alias `q`. Empty → `{ error }` no HTTP. |
| `limit` | number | no | Objects to return. Default config `default_limit`. Clamp 1–50 (beta max 50). |
| `offset` | number | no | Start index. Default 0. Max **4999**. |
| `rating` | string | no | `g` \| `pg` \| `pg-13` \| `r`. Else config `default_rating`. |
| `lang` | string | no | ISO 639-1 (see optional-settings language list). |
| `bundle` | string | no | Overrides config `bundle`. |
| `remove_low_contrast` | boolean | no | `true` → exclude low-contrast items. |

### `operation = trending`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Default 25. |
| `offset` | number | no | Default 0. Max **499**. |
| `rating` | string | no | Same as search. |
| `bundle` | string | no | Overrides config `bundle`. |
| `remove_low_contrast` | boolean | no | Same as search. |

### `operation = translate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `phrase` | string | yes | Wire `s`. Alias `s`. Empty → `{ error }` no HTTP. |
| `rating` | string | no | Same as search. |

### `operation = random`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `tag` | string | no | Optional tag filter. Omit → fully random. |
| `rating` | string | no | Same as search. |

### `operation = gif_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `gif_id` | string | yes | Path `{gif_id}`. Alias `id`. Empty → `{ error }` no HTTP. |
| `rating` | string | no | If the GIF’s rating is higher than this, GIPHY returns empty data + 4xx meta. |

URL-encode all query values. Do not re-encode characters the user typed into `q` beyond standard query encoding.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `gifs` | array | `search` / `trending` | Normalized GIF objects (see below). |
| `gif` | object | `translate` / `random` / `gif_get` | Single normalized GIF. |
| `pagination` | object | list ops | `{ total_count, count, offset }` from GIPHY Pagination Object. |
| `response_id` | string | 2xx | `meta.response_id` when present. |
| `error` | string | failure | Actionable; never the API key. |
| `http_status` | number | HTTP error | Provider / meta status. |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` if present; else 60 on 429, 30 on 5xx/timeout. |

Normalized GIF object (subset of the [GIF Object](https://developers.giphy.com/docs/api/schema/)): `id`, `title`, `url` (giphy.com page; prefer HTTPS), `embed_url`, `rating`, `username`, `alt_text`, `preview_url` (`images.fixed_height.url` or `.webp` / `.mp4` when present), `mp4_url` (`images.original.mp4` or `fixed_height.mp4`), `webp_url`, `analytics` (passthrough of `onload`/`onclick`/`onsent` URLs — do not log). Do **not** strip or rewrite query strings on media URLs.

Empty `data` on `search`/`trending` is **success** (`gifs: []`). `gif_get` with empty data + 4xx meta → `{ error }`.

## Legal

- GIPHY API Terms of Service: https://support.giphy.com/hc/en-us/articles/360028134111-GIPHY-API-Terms-of-Service
- Production-key conditions (attribution): https://support.giphy.com/hc/en-us/articles/360035158592-What-conditions-does-my-app-project-need-to-meet-in-order-to-get-a-production-API-Key
- Developer docs / branding: https://developers.giphy.com/docs/api/ and Brand Guidelines section on https://developers.giphy.com/docs/ — conspicuously display **“Powered By GIPHY”** and approved logo marks wherever results are shown. Do not imply GIPHY endorsement. Do not use lookalike names.
- Some GIFs are licensed by **third parties** (studios, leagues, creators). API access is not a redistribution licence. Do not cache or store GIPHY media unless GIPHY has **written** approval and the required cache-revalidation pattern is implemented. Do not mix GIPHY results with other GIF providers in one grid. Do not reorder or filter out items from search/trending responses.
- **Rate limits (published on developers.giphy.com, 2026-08-20):** all keys start as **beta** — about **100 API calls/hour**. Search `limit` max **50** on beta keys. Production keys require dashboard approval and may be priced. Do not tight-loop on 429.
- Official-API-first: **no scrape** of `giphy.com`. JDK HTTP to `api.giphy.com` only.
- PII: do not put personal data in `q`/`s`/`tag`. Logs: operation, status, counts, sizes — never `api_key`, never query text beyond length.

## Implementation notes

- JDK `HttpClient` only. No GIPHY SDK. TLS verified. `Redirect.NEVER`.
- Headers: `Accept: application/json`, `User-Agent: StudioQuack/1.0 (giphy; support@studioquack.com)`.
- Always HTTPS `https://api.giphy.com`. Query: `api_key` plus operation fields.
- Empty `api_key` → `{ error: "Missing GIPHY API key. Bind api_key in node parameters." }` — no HTTP.
- Body cap **1_000_000** bytes. Timeout default 25 s.
- Retry: none on 4xx except a **single** 429 that honors `Retry-After` once. No inner retry on 5xx.
- HTTP 401/403 → invalid or unapproved key. HTTP 429 → quota (beta ~100/hour).
- Failures go on OUTPUT `{ error }`. Never throw past dispatch.
- Do not download GIF bytes in this node; return URLs only.
