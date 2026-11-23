# pgvector

- **Status:** planned — **do not build a second node**
- **Catalog:** `docs/catalog/databases-storage.md`
- **Canonical node:** [`postgresql.md`](postgresql.md) operations `vector.upsert` / `vector.query` / `vector.delete`
- **Folder when built:** `nodes/postgresql/`
- **Researched:** 2026-08-20
- **Official API:** `vector` PostgreSQL extension (SQL types + operators)
- **Docs:** https://github.com/pgvector/pgvector
- **Auth:** same as PostgreSQL (`password`)

## Scope

pgvector is **not a vendor**. It is a Postgres extension. Vector upsert/query/delete are operations on the **PostgreSQL** node. **Do not invent a pgvector node** and do not duplicate it under AI & Machine Learning.

Requires `CREATE EXTENSION vector` on the customer database. If missing, `vector.*` fails closed with a clear error.

## Operations (`operation` config values)

See PostgreSQL `vector.*`. Operators: cosine `<=>`, L2 `<->`, inner product `<#>`.

## Configuration parameters

PostgreSQL `vector_table`, `vector_column`, `vector_id_column`, `vector_metric`. Same host/user/password.

## Ports

Same as PostgreSQL.

## Legal

- Extension license: PostgreSQL License (pgvector README)
- Data (embeddings) may be personal data; customer owns the table

## Implementation notes

- SQL over pgwire only. Do not log embedding arrays.
- Dimension mismatch → `{ error }` from the server.
