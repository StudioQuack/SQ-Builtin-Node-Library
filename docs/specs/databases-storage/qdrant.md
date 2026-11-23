# Qdrant

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/qdrant/`
- **Researched:** 2026-08-20
- **Official API:** Qdrant REST (default `:6333`). OpenAPI at https://api.qdrant.tech
- **Docs:** https://qdrant.tech/documentation/ · https://qdrant.tech/documentation/cloud/authentication/ · https://api.qdrant.tech/api-reference/points/upsert-points
- **Auth:** api_key — secret binding: `api_key` (header `api-key` or `Authorization: Bearer`)

## Scope

One node for **customer-owned** Qdrant Cloud or self-hosted clusters. Vector search lives here (not AI & ML). Prefer REST over gRPC for JDK HTTP.

Self-host may run without a key (operator risk); Cloud requires a Database API key.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `collection.upsert` | PUT | `/collections/{collection_name}/points` | Insert/overwrite points |
| `collection.search` | POST | `/collections/{collection_name}/points/query` | Query / k-NN (current Query API). Legacy `points/search` still exists — prefer `/points/query` |
| `collection.get` | POST | `/collections/{collection_name}/points` | Get points by id |
| `collection.scroll` | POST | `/collections/{collection_name}/points/scroll` | Paginated scroll |
| `collection.delete` | POST | `/collections/{collection_name}/points/delete` | Delete by ids / filter |

Base URL: operator `url` (e.g. `https://xyz.cloud.qdrant.io:6333`).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `collection.search` | no | Chooses the row above |
| `url` | string | yes | empty | no | Origin including scheme and port |
| `api_key` | password | no | empty | yes | Cloud: required. Header `api-key` |
| `timeout_seconds` | number | no | `30` | no | HTTP timeout |
| `wait` | boolean | no | `true` | no | `wait=true` query param on writes |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No API key |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No API key |

FUNCTION ports: none.

## Input fields (by operation)

### `collection.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `collection` | string | yes | Collection name |
| `points` | array | yes | `{ id, vector, payload? }` max 100; do not log vectors |

### `collection.search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `collection` | string | yes | Collection |
| `vector` | array | yes | Query vector |
| `limit` | number | no | Default 10, max 100 |
| `filter` | object | no | Qdrant filter |
| `with_payload` | boolean | no | Default true |
| `with_vector` | boolean | no | Default false |

Request body uses the official Query API (`query` field). If `/points/query` returns 404, **Unverified fallback:** POST `/points/search` with `vector`/`limit` — mark in logs as fallback; prefer query.

### `collection.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `collection` | string | yes | Collection |
| `ids` | array | yes | Point ids (max 100) |
| `with_payload` | boolean | no | Default true |
| `with_vector` | boolean | no | Default false |

### `collection.scroll`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `collection` | string | yes | Collection |
| `limit` | number | no | Default 64, max 100 |
| `offset` | string/number | no | Scroll offset |
| `filter` | object | no | Filter |

### `collection.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `collection` | string | yes | Collection |
| `ids` | array | no | Point ids |
| `filter` | object | no | Filter. Empty ids+filter is an error |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` (Qdrant also returns `{ status: ok, result }`) |
| `result` | object/array | success | Provider result |
| `points` | array | get/scroll/search | Normalized `{ id, score?, payload, vector? }` |
| `next_page_offset` | any | scroll | Next offset |
| `error` | string | failure | No API key |
| `http_status` | number | HTTP error | |

## Legal

- Cloud ToS: https://qdrant.tech (see Cloud terms)
- Self-host: Apache 2.0 Qdrant
- Payloads may be PII; customer owns the cluster
- Official REST only

## Implementation notes

- JDK HTTP. Header `api-key: {secret}` (Bearer also accepted).
- SSRF: only the configured `url` origin.
- Caps: 100 points/upsert; search limit 100. Do not log vectors.
- Retry 429/5xx once. Timeouts 30 s.
- Errors → `{ error }` on OUTPUT.
