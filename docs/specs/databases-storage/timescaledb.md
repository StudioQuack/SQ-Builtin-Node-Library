# TimescaleDB

- **Status:** planned — **do not build a second node**
- **Catalog:** `docs/catalog/databases-storage.md`
- **Canonical node:** [`postgresql.md`](postgresql.md) (`engine=timescale`)
- **Folder when built:** `nodes/postgresql/`
- **Researched:** 2026-08-20
- **Official API:** PostgreSQL wire + Timescale extension (hypertables are SQL)
- **Docs:** https://docs.timescale.com/use-timescale/latest/hypertables/
- **Auth:** userPass — PostgreSQL node secrets

## Scope

TimescaleDB is PostgreSQL with an extension. Protocol and operations match PostgreSQL. **Do not invent a Timescale node.** `engine=timescale` is documentation/defaults only (same driver).

Hypertables, compression, and continuous aggregates are ordinary SQL via `execute.query`.

## Operations (`operation` config values)

Same as PostgreSQL (including pgvector if installed).

## Configuration parameters

PostgreSQL config. Cloud: SSL + service URI.

## Ports

Same as PostgreSQL.

## Legal

- https://www.timescale.com/legal
- Customer-owned DB

## Implementation notes

- Same driver as PostgreSQL. No extra HTTP API required for listed ops.
