# PlanetScale

- **Status:** planned — **do not build a dedicated node**
- **Catalog:** `docs/catalog/databases-storage.md`
- **Canonical nodes:** Vitess/MySQL → [`mysql.md`](mysql.md) (`engine=planetscale`); Postgres product → [`postgresql.md`](postgresql.md)
- **Folder when built:** `nodes/mysql/` or `nodes/postgresql/`
- **Researched:** 2026-08-20
- **Official API:** MySQL protocol 3306 (Vitess) · PostgreSQL wire (PlanetScale Postgres)
- **Docs:** https://planetscale.com/docs/postgres-vs-vitess · https://planetscale.com/docs/vitess/connecting/connection-strings · https://planetscale.com/docs/postgres/connecting
- **Auth:** branch password / role credentials — secrets on the MySQL or PostgreSQL node

## Scope

PlanetScale offers **two engines**. They are not a third protocol:

1. **Vitess (MySQL-compatible)** — same operations as MySQL over TCP 3306 + TLS. `engine=planetscale` on the MySQL node. `procedure.call` **disabled** (not supported through VTGate).
2. **PlanetScale Postgres** — unmodified PostgreSQL wire (direct `5432` or PgBouncer `6432`). Use the PostgreSQL node (no special engine required; `engine=postgres`).

**Do not build** a PlanetScale HTTP serverless-driver node. The daemon sandbox can use TCP. Branch/deploy-request HTTP APIs are a different auth surface and are out of catalog SQL ops.

## Operations (`operation` config values)

See MySQL or PostgreSQL. Not duplicated.

## Configuration parameters

MySQL or PostgreSQL. SSL required for Vitess.

## Ports

1 INPUT / 1 OUTPUT on the canonical node.

## Legal

- https://planetscale.com/legal/agreements
- Customer-owned database. Credentials are secrets.

## Implementation notes

- Vitess: no stored procedures, limited `GET_LOCK` / cross-shard semantics — server errors map to `{ error }`.
- Postgres product: same as Neon-style pooler caveats on port `6432` (transaction pooling).
