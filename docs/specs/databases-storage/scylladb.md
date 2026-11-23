# ScyllaDB

- **Status:** planned — **do not build a second node**
- **Catalog:** `docs/catalog/databases-storage.md`
- **Canonical node:** [`cassandra.md`](cassandra.md) (`engine=scylla`)
- **Folder when built:** `nodes/cassandra/`
- **Researched:** 2026-08-20
- **Official API:** CQL native protocol **v4** (drop-in for Cassandra 3.11 + selected 4.0 features)
- **Docs:** https://docs.scylladb.com/manual/stable/using-scylla/cassandra-compatibility.html
- **Auth:** userPass — Cassandra node `password`

## Scope

ScyllaDB is CQL-compatible on protocol v4. Catalog operations match Cassandra. **Do not invent a Scylla node.** Shard-aware is a driver setting, not a second API.

Thrift is not supported (dropped in ScyllaDB 6.0). Protocol v5 is **not** fully implemented — stay on v4.

## Operations (`operation` config values)

Same as Cassandra.

## Configuration parameters

Cassandra config, `engine=scylla`.

## Ports

Same as Cassandra.

## Legal

- https://www.scylladb.com/legal/
- Customer-owned cluster

## Implementation notes

- Same CQL driver, protocol v4. Optional shard-aware for latency — not required for correctness.
