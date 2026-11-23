# PGVector

Generic [pgvector](https://github.com/pgvector/pgvector) node on customer-owned PostgreSQL. Handler: `PgVectorNode` (PostgreSQL v3 simple-query; no JDBC). Password is never logged or returned. Failures raise a BPMN error (INVALID_INPUT, REQUEST_FAILED); secrets are redacted.

- **Wire to AI Agent `data`:** the agent sends `{operation:"QUERY", query, embedding?, topK, filter}`.
- Existing HTTP vector nodes (Pinecone, Chroma, Qdrant, Milvus, Weaviate) stay available; the agent maps QUERY onto their declared fields.

## Operations

| operation | SQL | Notes |
|---|---|---|
| `QUERY` | `SELECT … ORDER BY embedding <=> $vec LIMIT topK` | Empty embedding → `position(query in document) > 0` |
| `UPSERT` | `INSERT … ON CONFLICT (id) DO UPDATE` | Requires `id` + `embedding` |
| `DELETE` | `DELETE … WHERE id and/or filter RETURNING id` | |

Identifiers are quoted and restricted to `[A-Za-z_][A-Za-z0-9_]*`. Values use `$sq$…$sq$` dollar quotes.

## Config

Host, port, database, user, password (secret), schema, table, column names, `topK`, `sslmode` (`disable`/`require`), `verify_tls`, `timeout_seconds`.

## Legal

Customer is the controller of vectors and documents (may include PHI). StudioQuack does not host Postgres. Operators must comply with their own database and data-protection terms.
