# Neon

- **Status:** planned — **do not build a second node**
- **Catalog:** `docs/catalog/databases-storage.md`
- **Canonical node:** [`postgresql.md`](postgresql.md) (`engine=neon`)
- **Folder when built:** `nodes/postgresql/`
- **Researched:** 2026-08-20
- **Official API:** PostgreSQL wire (Neon runs unmodified Postgres)
- **Docs:** https://neon.com/docs/connect/connect-from-any-app · https://neon.com/faqs/postgres-services-wire-protocol-compatible
- **Auth:** userPass on the wire — secrets on the PostgreSQL node (`password`)

## Scope

Neon is **wire-protocol compatible** with PostgreSQL and exposes the same SQL operations. **Do not invent a Neon node.** Operators set `engine=neon` on PostgreSQL.

Neon Cloud (project/branch) HTTP API is a **different** auth surface (`neonApiKey`) and is **out of scope** for this catalog’s SQL operations.

Pooled connections append `-pooler` to the endpoint hostname (PgBouncer transaction mode). Use pooled for `execute.query` / row ops. `trigger.listen` (`LISTEN`/`NOTIFY`) requires the **direct** endpoint.

## Operations (`operation` config values)

Same as PostgreSQL. Not duplicated here.

## Configuration parameters

Use PostgreSQL config. Typical Neon: `sslMode=require`, host from the console connection string, port `5432`.

## Ports

Same as PostgreSQL (1 INPUT / 1 OUTPUT).

## Legal

- https://neon.tech/terms
- Customer owns the Neon project. Credentials are secrets.

## Implementation notes

- Same driver as PostgreSQL. No JDK HTTP.
- SNI required for routing (modern TLS clients).
