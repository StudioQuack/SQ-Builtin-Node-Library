# Pinecone

Official [Pinecone REST 2026-04](https://docs.pinecone.io/reference/api/versioning). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Pinecone project. Minimize: send only the records this operation needs. `api_key` is never logged or returned.

- **Docs:** https://docs.pinecone.io/reference/api/versioning
- **Base:** `https://api.pinecone.io`
- **Auth:** header `Api-Key` from secret `api_key` (empty prefix).

## Headers

- **Version header:** every request sends `X-Pinecone-Api-Version: 2026-04` via spec `extraHeaders` ([versioning](https://docs.pinecone.io/reference/api/versioning)).

## Engine gaps (OfficialApiNode)

- **Two origins:** `index.*` use `https://api.pinecone.io`. `vector.*` use per-op `baseUrl` `https://{host}` where `host` is the index host from describe_index (INPUT or config).
- **Fetch ids:** official GET repeats `ids` query params. The handler emits a single `ids=` value.

Still use the official paths above; do not invent a proxy.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `index.list` | GET | `/indexes` |
| `index.create` | POST | `/indexes` (required body: `name`, `spec`; `dimension` only for dense) |
| `index.delete` | DELETE | `/indexes/{name}` |
| `vector.upsert` | POST | `/vectors/upsert` |
| `vector.query` | POST | `/query` |
| `vector.fetch` | GET | `/vectors/fetch` |
| `vector.delete` | POST | `/vectors/delete` |
| `vector.update` | POST | `/vectors/update` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Pinecone project API key. Sent as header Api-Key. StudioQuack secret. Never on INPUT. Console: https://app.pinecone.io/ |
| `host` | string | no | Default index host for vector.* |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `api_key`.

## Legal

- Customer is the controller of vectors, payloads, and metadata they submit.
- Provider terms: https://www.pinecone.io/legal/master-subscription-agreement/
- Official API only. Do not scrape consoles or dashboards.
