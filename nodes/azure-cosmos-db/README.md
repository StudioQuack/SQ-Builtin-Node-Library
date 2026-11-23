# Azure Cosmos DB

Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://learn.microsoft.com/en-us/rest/api/cosmos-db/
- **Verified official HTTP:** `https://{account}.documents.azure.com` (researched 2026-08-20)
- **Auth:** `access_token` (password secret). Bearer. AAD is not supported.
- Customer owns the database / project. StudioQuack stores only the secret.

Verified 2026-08-22: official Cosmos DB REST. The host is `https://{account}.documents.azure.com`, built from the `account` config. extraHeaders send `x-ms-version: 2018-12-31` (latest listed data-plane REST version) and `x-ms-date` RFC 1123 ([common headers](https://learn.microsoft.com/en-us/rest/api/cosmos-db/common-cosmosdb-rest-request-headers)); `partition_key` (JSON array, e.g. `["ada"]`) is sent as `x-ms-documentdb-partitionkey` on get/create/query, required by partitioned containers. `item.query` sends `Content-Type: application/query+json` and `x-ms-documentdb-isquery: True`. **AAD/RBAC is not supported:** official AAD header is `type=aad&ver=1.0&sig=` (URL-encoded token), not `Bearer`. This node still sends Bearer.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `item.get` | GET | `/dbs/{db}/colls/{coll}/docs/{id}` |
| `item.create` | POST | `/dbs/{db}/colls/{coll}/docs` (body = `document`) |
| `item.query` | POST | `/dbs/{db}/colls/{coll}/docs` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Official credential |
| `account` | string | no | Account name in `https://{account}.documents.azure.com` |
| `db` / `coll` | string | no | Database and container ids (path) |
| `id` | string | no | Item id for `item.get` |
| `partition_key` | string | no | JSON array partition key value, e.g. `["ada"]` |
| `document` | object | no | `item.create` body; must contain `id` |
| `query` / `parameters` | string / list | no | `item.query` SQL and `[{name, value}]` |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `access_token`.

## Legal

- Customer is the controller of data they query or write.
- Minimize: send only the SQL / rows / ids required for the step.
- Provider terms apply on the caller account.
- Official HTTP/SQL API only. No invented wire-over-HTTP proxy.
