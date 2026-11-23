# CockroachDB

- **Status:** planned — **do not build a second node**
- **Catalog:** `docs/catalog/databases-storage.md`
- **Canonical node:** [`postgresql.md`](postgresql.md) (`engine=cockroach`)
- **Folder when built:** `nodes/postgresql/`
- **Researched:** 2026-08-20
- **Official API:** PostgreSQL wire protocol **3.0** (Cockroach pgwire)
- **Docs:** https://www.cockroachlabs.com/docs/stable/postgresql-compatibility
- **Auth:** userPass / cert — secrets on the PostgreSQL node (`password`; optional `sslRootCertPem`)

## Scope

CockroachDB speaks pgwire and the catalog SQL operations (query / row CRUD). **Do not invent a CockroachDB node.** Set `engine=cockroach` on PostgreSQL.

Some PostgreSQL features are unsupported in distributed SQL; the server returns errors. That is not a second API.

## Operations (`operation` config values)

Same as PostgreSQL. `trigger.listen` only if the cluster supports `LISTEN`/`NOTIFY` for that version — otherwise `{ error }` from the server.

## Configuration parameters

PostgreSQL config. Cloud: TLS required; routing hostname from the cluster UI.

## Ports

Same as PostgreSQL.

## Legal

- https://www.cockroachlabs.com/docs/stable/licensing-faqs
- Customer-owned cluster / Cockroach Cloud ToS when hosted

## Implementation notes

- Same Postgres driver. Prefer `sslMode=require` or `verify-full`.
