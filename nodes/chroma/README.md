# Chroma

Official [Chroma HTTP v2](https://docs.trychroma.com/reference/chroma-api/record/add-records). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Chroma server. Minimize: send only the records this operation needs. `token` is never logged or returned.

- **Docs:** https://docs.trychroma.com/reference/chroma-api/record/add-records
- **Base:** `http://localhost:8000`
- **Auth:** header `X-Chroma-Token` from secret `token` (empty prefix). Official OpenAPI security scheme is apiKey header `x-chroma-token` (not Authorization Bearer). HTTP header names are case-insensitive.

## Engine gaps

`baseUrl` is `http://localhost:8000`. Chroma Cloud requires changing `api_spec.baseUrl`. Auth is **not** Bearer: official OpenAPI `ApiKeyAuth` header is `x-chroma-token`. This node sends `X-Chroma-Token`. Self-host default tenant/database names are `default_tenant` / `default_database`. Official OpenAPI still lists `ids` + `embeddings` as required on add ([add-records](https://docs.trychroma.com/reference/chroma-api/record/add-records)); client [add-data](https://docs.trychroma.com/docs/collections/add-data) also allows documents-only when an embedding function is configured.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `collection.add` | POST | `/api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/add` |
| `collection.query` | POST | `/api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/query` |
| `collection.get` | POST | `/api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/get` |
| `collection.update` | POST | `/api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/update` |
| `collection.delete` | POST | `/api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/delete` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `token` | password | yes | Chroma token. Sent as X-Chroma-Token (official OpenAPI: x-chroma-token). Not Bearer. StudioQuack secret. Never on INPUT. Cloud dashboard token; local open servers ignore the header. |
| `tenant` | string | no | Tenant path segment. Self-host default default_tenant. Cloud: tenant from the dashboard. |
| `database` | string | no | Database path segment. Self-host default default_database. |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes `token`.

## Legal

- Customer is the controller of vectors, payloads, and metadata they submit.
- Provider terms: https://www.trychroma.com (current terms on that site)
- Official API only. Do not scrape consoles or dashboards.
