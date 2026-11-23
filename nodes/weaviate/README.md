# Weaviate

Official [Weaviate REST + GraphQL](https://docs.weaviate.io/weaviate/api/rest). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Weaviate cluster. Minimize: send only the records this operation needs. `api_key` is never logged or returned.

- **Docs:** https://docs.weaviate.io/weaviate/api/rest
- **Base:** `{base_url}` config (default `http://localhost:8080`; Weaviate Cloud: the cluster REST endpoint)
- **Auth:** Bearer `api_key` (secret). Cloud requires a key; anonymous self-host still needs a non-blank secret in this handler (engine gap).

## Engine gaps

Hybrid search is GraphQL (`hybrid` argument) on the same POST `/v1/graphql` as `collection.query`. `collection.delete` (`DELETE /v1/objects/{id}`) is deprecated; official current path is `DELETE /v1/objects/{className}/{id}` (`collection.deleteByClass`).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `collection.insert` | POST | `/v1/objects` |
| `collection.getMany` | GET | `/v1/objects` |
| `collection.query` | POST | `/v1/graphql` |
| `collection.hybrid` | POST | `/v1/graphql` |
| `collection.delete` | DELETE | `/v1/objects/{id}` (deprecated by Weaviate — prefer `collection.deleteByClass`) |
| `collection.deleteByClass` | DELETE | `/v1/objects/{class}/{id}` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Weaviate API key. Sent as Authorization: Bearer. StudioQuack secret. Never on INPUT. Cloud Console or AUTHENTICATION_APIKEY_ALLOWED_KEYS. |
| `base_url` | string | no | Weaviate origin, no trailing slash. Default `http://localhost:8080`. |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `api_key`.

## Legal

- Customer is the controller of vectors, payloads, and metadata they submit.
- Provider terms: https://weaviate.io/service-agreements (current terms on that site)
- Official API only. Do not scrape consoles or dashboards.
