# Valkey

- **Status:** planned — **do not build a second node**
- **Catalog:** `docs/catalog/databases-storage.md`
- **Canonical node:** [`redis.md`](redis.md) (`engine=valkey`)
- **Folder when built:** `nodes/redis/`
- **Researched:** 2026-08-20
- **Official API:** RESP2 / RESP3 (identical wire format to Redis OSS 7.2 fork baseline)
- **Docs:** https://valkey.io/topics/protocol/ · https://valkey.io/topics/migration/
- **Auth:** AUTH / ACL — Redis node `password`

## Scope

Valkey forked Redis OSS 7.2.4 and speaks the same RESP protocol. Existing Redis clients connect unchanged. **Do not invent a Valkey node.**

Command-set divergence after the fork: Redis 8 bundles Query Engine / JSON / vector sets in core; Valkey ships similar features as **modules**. `vector.*` on `engine=valkey` requires the module to be loaded; otherwise fail closed.

## Operations (`operation` config values)

Same as Redis for keys, pub/sub, and streams. Vector ops gated on module presence.

## Configuration parameters

Redis config with `engine=valkey`.

## Ports

Same as Redis.

## Legal

- https://valkey.io — Linux Foundation governance
- Customer-owned server / cloud SKU ToS

## Implementation notes

- Same RESP3 client as Redis. `HELLO 3`.
