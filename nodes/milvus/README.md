# Milvus

Official [Milvus RESTful v2](https://milvus.io/api-reference/restful/v2.6.x/v2/Vector%20(v2)/Insert.md). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Milvus / Zilliz cluster. Minimize: send only the records this operation needs. `token` is never logged or returned.

- **Docs:** https://milvus.io/api-reference/restful/v2.6.x/v2/Vector%20(v2)/Insert.md
- **Base:** `http://localhost:19530`
- **Auth:** Bearer `token` (secret). Official form `username:password` (example `root:Milvus`) or a Zilliz Cloud token.

## Engine gaps

`baseUrl` is `http://localhost:19530`. Remote / Zilliz URIs require changing `api_spec.baseUrl`. Body keys are official camelCase (`collectionName`, `data`, `filter`).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `collection.create` | POST | `/v2/vectordb/collections/create` |
| `collection.insert` | POST | `/v2/vectordb/entities/insert` |
| `collection.search` | POST | `/v2/vectordb/entities/search` |
| `collection.query` | POST | `/v2/vectordb/entities/query` |
| `collection.delete` | POST | `/v2/vectordb/entities/delete` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `token` | password | yes | Milvus REST token. Sent as Authorization: Bearer. Official username:password (e.g. root:Milvus) or Zilliz API key. Never on INPUT. |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `token`.

## Legal

- Customer is the controller of vectors, payloads, and metadata they submit.
- Provider terms: https://milvus.io (Apache 2.0 self-host; Zilliz Cloud terms when using Zilliz)
- Official API only. Do not scrape consoles or dashboards.
