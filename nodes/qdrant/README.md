# Qdrant

Official [Qdrant REST](https://qdrant.tech/documentation/). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Qdrant cluster. Minimize: send only the records this operation needs. `api_key` is never logged or returned.

- **Docs:** https://qdrant.tech/documentation/
- **Base:** `http://localhost:6333`
- **Auth:** header `api-key` from secret `api_key` (empty prefix). Cloud Database API key required.

## Engine gaps

`baseUrl` is `http://localhost:6333`. Cloud hosts (e.g. `https://xyz.cloud.qdrant.io:6333`) require changing `api_spec.baseUrl`. Search uses the current Query API `POST /points/query`, not legacy `/points/search`. Delete body uses official `points` (ids) and/or `filter`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `collection.upsert` | PUT | `/collections/{collection}/points` |
| `collection.search` | POST | `/collections/{collection}/points/query` |
| `collection.get` | POST | `/collections/{collection}/points` |
| `collection.scroll` | POST | `/collections/{collection}/points/scroll` |
| `collection.delete` | POST | `/collections/{collection}/points/delete` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Qdrant API key. Sent as header api-key. StudioQuack secret. Never on INPUT. Cloud: cluster API keys. |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `api_key`.

## Legal

- Customer is the controller of vectors, payloads, and metadata they submit.
- Provider terms: https://qdrant.tech (Cloud terms on that site); self-host Apache 2.0
- Official API only. Do not scrape consoles or dashboards.
