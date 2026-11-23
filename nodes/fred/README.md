# FRED

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/fred` |
| `templateId` | `057c725b-0ea8-5c6b-a2fc-9d84945c76a4` |
| Env override | `SQ_TEMPLATE_ID_FRED` |
| Category | Data & Analytics |
| Official docs | https://fred.stlouisfed.org/docs/api/fred/ |
| Auth | Query parameter `api_key` from **configurationParameters** (never INPUT) |

## API key

Register a free key at https://fred.stlouisfed.org/docs/api/api_key.html. The value must be exactly **32 lowercase alphanumeric characters** (`[a-z0-9]{32}`). The node rejects malformed keys locally with `INVALID_INPUT` instead of a raw HTTP 400.

## Operations

| operation | HTTP | Path | Required INPUT |
|---|---|---|---|
| `series_search` | GET | `/series/search?file_type=json` | `search_text` |
| `series_get` | GET | `/series?file_type=json` | `series_id` |
| `series_observations` | GET | `/series/observations?file_type=json` | `series_id` |
| `category_get` | GET | `/category?file_type=json` | — (`category_id` optional) |
| `release_get` | GET | `/release?file_type=json` | `release_id` |

Default config operation is **`series_search`** when INPUT omits `operation`.

### `series_search` pagination

- `limit`: 1–1000 (validated locally).
- `offset`: ≥ 0.

### `series_observations` filters

- `observation_start` / `observation_end`: `YYYY-MM-DD`; start must be ≤ end.
- `units`: `lin` \| `chg` \| `ch1` \| `pch` \| `pc1` \| `pca` \| `cch` \| `cca` \| `log`.
- `frequency`: FRED aggregation code (e.g. `d`, `m`, `q`, `a`, `bw`, …).

## Ports

One INPUT `input`, one OUTPUT `output`. Success: `{ status: "ok", http_status, data }`. Errors: BPMN boundary codes (`INVALID_INPUT`, `HTTP_<status>`, …).

## Legal

St. Louis Fed FRED API. Free key. Attribution to FRED / source agency. `file_type=json` is baked into paths.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
