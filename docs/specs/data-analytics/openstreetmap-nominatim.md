# OpenStreetMap / Nominatim

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/openstreetmap-nominatim/`
- **Researched:** 2026-08-20
- **Official API:** Nominatim search/reverse/lookup + Overpass QL
- **Docs:** https://nominatim.org/release-docs/latest/api/Overview/ · https://operations.osmfoundation.org/policies/nominatim/ · https://wiki.openstreetmap.org/wiki/Overpass_API
- **Auth:** none — identifying `user_agent` with contact required

## Scope

Geocoding via **Nominatim** and optional OSM query via **Overpass**. Official-API-first: no tile/HTML scrape of openstreetmap.org.

**Do not default to `nominatim.openstreetmap.org`.** OSMF Nominatim usage policy (2026) forbids building the public API into no-code / workflow platforms as a generic geocoder. `nominatim_url` is **required** and must be an instance the operator is allowed to automate (self-hosted Nominatim or a commercial Nominatim they contract). If the operator points at the public OSMF host, document the 1 req/s cap, no autocomplete, no bulk, ODbL share-alike — but **do not bake that host in as the default**.

Overpass: default empty; operator sets `overpass_url` (e.g. their instance). Public `overpass-api.de` has its own fair-use policy — do not hardcode it.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `nominatim_search` | GET | `{nominatim_url}/search` | Forward geocode |
| `nominatim_reverse` | GET | `{nominatim_url}/reverse` | Reverse geocode |
| `nominatim_lookup` | GET | `{nominatim_url}/lookup` | OSM type+id → details |
| `overpass_query` | POST | `{overpass_url}/api/interpreter` | Overpass QL |

Nominatim JSON: `format=jsonv2`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `nominatim_search` | no | |
| `nominatim_url` | string | yes (Nominatim ops) | **empty** | no | Origin of **operator’s** Nominatim. No default public host. |
| `overpass_url` | string | yes (`overpass_query`) | **empty** | no | Overpass origin. No default public host. |
| `user_agent` | string | yes | empty | no | Must identify app + contact. Empty → no HTTP. |
| `email` | string | no | empty | no | Nominatim `email` courtesy param |
| `timeout_seconds` | number | no | `25` | no | Overpass may need 60 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = nominatim_search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | `q` |
| `limit` | number | no | Max 10 on public policy; still cap 10 |
| `countrycodes` | string | no | ISO list |
| `viewbox` | string | no | Bounding box |
| `addressdetails` | boolean | no | `1`/`0` |

**Do not implement autocomplete** (OSMF forbids client-side autocomplete against public Nominatim).

### `operation = nominatim_reverse`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `lat` | number | yes | |
| `lon` | number | yes | |
| `zoom` | number | no | Detail level |
| `addressdetails` | boolean | no | |

### `operation = nominatim_lookup`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `osm_ids` | string | yes | e.g. `R146656,W104393803,N240109189` |

### `operation = overpass_query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Overpass QL. Size-cap 100 KB. Reject `[out:xml]` huge dumps; prefer `out json` + `out:json`. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `results` | array | search/lookup | `{ display_name, lat, lon, osm_type, osm_id, address? }` |
| `result` | object | reverse | Same shape |
| `elements` | array | overpass | Overpass JSON `elements` (cap 500) |
| `error` | string | failure | Missing URL / 429 / 403 |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx | Public OSMF: wait ≥ 1 s; bulk scripts 4/min |

## Legal

- Nominatim policy: https://operations.osmfoundation.org/policies/nominatim/
- OSMF Terms: https://osmfoundation.org/wiki/Terms_of_Use
- ODbL: https://opendatacommons.org/licenses/odbl/
- Attribution required when displaying OSM data.
- Public API: max **1 req/s**, no heavy/bulk, no autocomplete, no systematic grid reverse, no reselling geocoding as a product.
- **Workflow-platform constraint:** do not advertise OSMF public Nominatim as the built-in geocoder. Operator must choose a host they may automate.
- Do not scrape openstreetmap.org tiles or HTML.

## Implementation notes

- Empty `nominatim_url` / `user_agent` → error, no HTTP.
- Validate origins `http`/`https` only (SSRF). Daemon egress still applies.
- Headers: `User-Agent: {user_agent}`, optional `Referer` not required if UA is identifying.
- Client-side min interval: 1 s between Nominatim calls **in this handler** (no burst loop). Workflow-level loops are the operator’s duty.
- Body cap 1 MiB. TLS verified.
- XML external entities off if parsing XML Overpass (prefer JSON).
