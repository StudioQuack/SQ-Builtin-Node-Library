# Embedding stores

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** *none — do not create `nodes/embedding-stores/`*
- **Researched:** 2026-08-20
- **Official API:** none (this row is an index, not a provider)
- **Docs:** [`docs/catalog/databases-storage.md`](../../catalog/databases-storage.md)
- **Auth:** per store — see that catalog’s node (never on this row)

## Scope

**DO NOT BUILD** a combined “embedding stores” node.

Vector `upsert` / `query` / `delete` live on the **databases** provider node for that store. This AI-catalog row exists only so designers can find those stores from the AI & Machine Learning index. Duplicating Pinecone / Qdrant / Weaviate / Chroma / pgvector / Milvus / Redis / MongoDB / Supabase HTTP here would create a second node per API, which the catalog forbids.

In-memory / “simple” vector stores are a databases concern (sandbox process memory is not a product API). Do not add one under AI.

## Operations (`operation` config values)

*None on this node.* Use the databases node:

| Store | Databases catalog node | Vector-related operations |
|---|---|---|
| Pinecone | `pinecone` | `vector.upsert`, `vector.query`, `vector.delete` (plus `vector.fetch` / `vector.update`, index CRUD) |
| Weaviate | `weaviate` | `collection.insert`, `collection.query`, `collection.hybrid`, `collection.delete` |
| Qdrant | `qdrant` | `collection.upsert`, `collection.search`, `collection.delete` |
| Milvus / Zilliz | `milvus` | `collection.insert`, `collection.search`, `collection.query`, `collection.delete` |
| Chroma | `chroma` | `collection.add`, `collection.query`, `collection.delete` |
| pgvector | **not a separate node** — `postgresql` (`vector.*`) | `vector.upsert`, `vector.query`, `vector.delete` |
| Redis (Query Engine) | `redis` | `vector.upsert`, `vector.query` |
| MongoDB Atlas | `mongodb` | document CRUD + `searchIndex.*` (Atlas Search / vector indexes). No Atlas Data API (EOL). |
| Supabase | `supabase` | `vector.query` (writes via `row.*` / `rpc.call`) |

## Gaps (do not invent AI-category nodes)

**Azure AI Search** and **Zep** appear on the AI catalog embedding-stores row but are **not** rows in `docs/catalog/databases-storage.md` as of this research date. If they are added later, they belong in **databases-storage**, not as AI sibling nodes.

## Ports

None. There is no handler.

## Legal

- Terms are those of the **store you actually call** (Pinecone, Weaviate, …). This file adds no vendor contract.
- Official-API-first: no scrape of vendor consoles; no unofficial Discord/web wrappers.

## Implementation notes

Do not ship `templateId` / handler / `nodes/embedding-stores/`. Point the designer at the databases node. Embeddings themselves are produced by a **model** node (`embeddings` on OpenAI Models, Gemini, Cohere, …), then written with that store’s `upsert` / `insert` / `add`.
