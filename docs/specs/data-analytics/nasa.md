# NASA

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/nasa/`
- **Researched:** 2026-08-20
- **Official API:** NASA Open APIs (`api.nasa.gov`)
- **Docs:** https://api.nasa.gov/
- **Auth:** api_key query `api_key` — secret binding: `api_key` (`DEMO_KEY` allowed but rate-limited)

## Scope

Federated NASA APIs behind `https://api.nasa.gov`. **Drop archived products:**

- **Earth imagery / assets** (`/planetary/earth/imagery`, `/planetary/earth/assets`) — archived; replaced by **Earthdata GIBS** (WMTS/WMS at `gibs.earthdata.nasa.gov`). Do not call the archived Earth API.
- **Mars Rover Photos API** — archived per api.nasa.gov banner. Omit.

Keep APOD, NeoWs, DONKI, EPIC. Optional `gibs_capabilities` as a thin official WMTS GetCapabilities GET (no key) if we still want Earth imagery; tile PNG download uses type `file` and is easy to DoS — v1 can omit tiles and only expose GetCapabilities URL + layer list.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `apod` | GET | `/planetary/apod` | Astronomy Picture of the Day |
| `neo_feed` | GET | `/neo/rest/v1/feed` | NEOs by close-approach date |
| `neo_lookup` | GET | `/neo/rest/v1/neo/{id}` | One asteroid |
| `neo_browse` | GET | `/neo/rest/v1/neo/browse` | Browse dataset |
| `donki_cme` | GET | `/DONKI/CME` | Coronal mass ejections |
| `donki_flares` | GET | `/DONKI/FLR` | Solar flares |
| `donki_notifications` | GET | `/DONKI/notifications` | Space-weather notices |
| `epic_natural` | GET | `/EPIC/api/natural` | EPIC natural imagery metadata (not the archived Earth Landsat API) |

Confirm extra DONKI paths (`GST`, `HSS`, etc.) only if still listed on https://api.nasa.gov/#DONKI — add as operations if present; do not invent.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `apod` | no | |
| `api_key` | password | yes | empty | yes | Query `api_key`. Do not default to `DEMO_KEY` in production templates. |
| `timeout_seconds` | number | no | `25` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Image URLs, not inline bytes. Optional later: `file` download of APOD `hdurl`. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = apod`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `date` | string | no | `YYYY-MM-DD` (xor start/end) |
| `start_date` | string | no | Range start |
| `end_date` | string | no | Range end |
| `count` | number | no | Random count 1–100 (xor date) |
| `thumbs` | boolean | no | Video thumbnail URL |

### `operation = neo_feed`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `start_date` | string | yes | `YYYY-MM-DD` (API max 7-day window) |
| `end_date` | string | no | Default start+7 days, clamp 7 |

### `operation = neo_lookup`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `asteroid_id` | string | yes | JPL small-body id |

### `operation = neo_browse`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | |
| `size` | number | no | Page size cap |

### `operation = donki_cme` | `donki_flares` | `donki_notifications`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `start_date` | string | no | |
| `end_date` | string | no | |
| `type` | string | no | Notifications `type` if documented |

### `operation = epic_natural`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `date` | string | no | `YYYY-MM-DD` if the EPIC path still accepts it |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `title` / `explanation` / `url` / `hdurl` / `media_type` | mixed | apod | Metadata + image URL (not bytes) |
| `near_earth_objects` | object | neo_feed | Capped list |
| `neo` | object | lookup | |
| `events` | array | DONKI | |
| `images` | array | EPIC | Metadata + constructed image URLs per EPIC docs |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | DEMO_KEY 429 is expected |

## Legal

- https://api.nasa.gov/ · NASA Open Data / API ToS on that portal
- Imagery remains NASA/public-domain **US government work** unless a caption names a contractor; still attribute NASA.
- DEMO_KEY: hourly rate limit shared globally — production must use a registered key.
- Do not scrape nasa.gov HTML galleries.

## Implementation notes

- Base `https://api.nasa.gov`. Query `api_key` on every call. Never log it.
- Earth/Mars operations if requested → `{ error: "NASA Earth imagery and Mars Rover APIs were archived. Use EPIC or Earthdata GIBS." }`
- APOD `hdurl` download is optional v2 with type `file` and size cap; v1 return URLs only.
- Body cap 2 MiB (NEO feed). Timeout 25 s. TLS verified.
- Clamp neo_feed window to 7 days before HTTP.
