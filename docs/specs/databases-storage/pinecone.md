# Pinecone

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/pinecone/`
- **Researched:** 2026-08-20
- **Official API:** Pinecone REST **2026-04** (control plane `api.pinecone.io` + data plane index host)
- **Docs:** https://docs.pinecone.io/reference/api/versioning
- **Auth:** `apiKey` — secret binding: `apiKey`

## Scope

Manages indexes on the Pinecone **control plane** and reads/writes vectors on the **data plane**. Control plane host is `https://api.pinecone.io`. Data plane host is the index `host` from create/list/describe (e.g. `{index}.svc.{region}.pinecone.io`) — never send vector bodies to `api.pinecone.io`. Every request must include `Api-Key` and **`X-Pinecone-Api-Version: 2026-04`** (verified 2026-08-20; omitting the header falls back to the oldest supported stable version). The customer owns the Pinecone project and indexes. Catalog `environment` is legacy (pre-serverless init); do not send it as a header. Do not use unofficial proxies or scrape the console.

This node is the only Pinecone vector-store spec (not duplicated under AI & ML).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `index.create` | POST | `https://api.pinecone.io/indexes` | Create a serverless (or pod) index |
| `index.list` | GET | `https://api.pinecone.io/indexes` | List indexes in the project |
| `index.delete` | DELETE | `https://api.pinecone.io/indexes/{index_name}` | Delete an index (HTTP 202 accepted) |
| `vector.upsert` | POST | `https://{indexHost}/vectors/upsert` | Upsert ≤ 1000 vectors per request |
| `vector.query` | POST | `https://{indexHost}/query` | Nearest-neighbor search |
| `vector.fetch` | GET | `https://{indexHost}/vectors/fetch` | Fetch by `ids` query params |
| `vector.delete` | POST | `https://{indexHost}/vectors/delete` | Delete by ids or metadata filter |
| `vector.update` | POST | `https://{indexHost}/vectors/update` | Update vector and/or metadata by id |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `vector.query` | no | Chooses the row above |
| `auth` | select | yes | `apiKey` | no | `apiKey` only |
| `apiKey` | password | yes | | yes | Project API key (`Api-Key` header) |
| `indexHost` | string | data-plane ops | | no | Data plane host from index describe/list (no scheme or with `https://`) |
| `environment` | string | no | | no | **Legacy.** Ignore for REST 2026-04. Do not send. |
| `requestTimeoutMs` | number | no | `30000` | no | Per HTTP call timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Vectors are number arrays, not files |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo `Api-Key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = index.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Index name |
| `dimension` | number | yes (dense) | Vector dimension |
| `metric` | string | yes | e.g. `cosine`, `euclidean`, `dotproduct` |
| `cloud` | string | serverless | `aws` \| `gcp` \| `azure` inside `spec.serverless` |
| `region` | string | serverless | e.g. `us-east-1` |
| `spec` | object | no | Full official `spec` if not using cloud/region shortcuts |

### `operation = index.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Uses API key only |

### `operation = index.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Index name to delete |

### `operation = vector.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vectors` | array | yes | `{ id, values: number[], metadata? }[]` — max 1000 |
| `namespace` | string | no | Namespace (default empty) |

### `operation = vector.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vector` | array | yes* | Query embedding (`number[]`) |
| `id` | string | yes* | Query by existing vector id instead of `vector` |
| `topK` | number | yes | Neighbors to return (`top_k`) |
| `namespace` | string | no | Namespace |
| `filter` | object | no | Metadata filter |
| `includeValues` | boolean | no | Return embeddings |
| `includeMetadata` | boolean | no | Return metadata |

\*One of `vector` or `id`.

### `operation = vector.fetch`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ids` | array | yes | Vector ids (`ids` query params; no spaces in ids) |
| `namespace` | string | no | Namespace |

### `operation = vector.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ids` | array | one of ids/filter/deleteAll | Ids to delete |
| `filter` | object | one of | Metadata filter delete |
| `deleteAll` | boolean | one of | Delete all in the namespace |
| `namespace` | string | no | Namespace |

### `operation = vector.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Record id |
| `values` | array | no | Replacement dense vector (`number[]`) |
| `setMetadata` | object | no | Metadata fields to set |
| `namespace` | string | no | Namespace |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `SUCCEEDED` |
| `indexes` | array | list | Index models including `host` |
| `index` | object | create | Created index (capture `host` for data plane) |
| `upsertedCount` | number | upsert | From upsert response |
| `matches` | array | query | `{ id, score, values?, metadata? }` |
| `vectors` | object | fetch | Map of id → record |
| `error` | object | failure | `{ message, httpStatus, code }` — no API key |

## Legal

- Pinecone Master Subscription Agreement: https://www.pinecone.io/legal/master-subscription-agreement/
- Auth: https://docs.pinecone.io/reference/api/authentication
- Versioning: https://docs.pinecone.io/reference/api/versioning
- Usage and storage are billed to the customer’s Pinecone project. **Unverified (2026-08-20)** per-plan RPS/quota numbers — read current Pinecone limits at implement time.
- Embeddings and metadata may be personal data. Residency follows the index cloud/region in `spec.serverless`.
- Official REST 2026-04 only.

## Implementation notes

- JDK HTTP only. Allow `api.pinecone.io` and `*.pinecone.io` (data plane).
- Headers on **every** call: `Api-Key: {apiKey}`, `X-Pinecone-Api-Version: 2026-04`, `Content-Type: application/json` on POST.
- Resolve `indexHost` from config or from list/create `host`. Require it for vector ops.
- Timeouts: connect 10s; `requestTimeoutMs` (query/upsert may need 30s+).
- Retry 429 (`RESOURCE_EXHAUSTED` / `QUOTA_EXCEEDED`) and 5xx with jitter. Do not retry 401/403/404. Upsert batches: split at 1000.
- Cap request/response size (default 4 MiB). Reject oversized `values` arrays before send.
- Errors → `{ error }`. Never log `Api-Key` or vector `values`. Log operation, index name, HTTP status, counts, byte sizes.
- INPUT embeddings are JSON numbers, not files. Do not read host filesystem paths.
