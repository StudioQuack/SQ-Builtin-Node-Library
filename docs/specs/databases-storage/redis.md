# Redis

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/redis/`
- **Researched:** 2026-08-20
- **Official API:** RESP3 via `HELLO 3` (Redis 6+; docs example server **8.8.0**). Standalone / Cluster / Sentinel. Redis Query Engine vector search is **core in Redis 8+**. Valkey: same RESP; search is **valkey-search** module
- **Docs:** https://redis.io/docs/latest/develop/reference/protocol-spec/ · `HELLO`: https://redis.io/docs/latest/commands/hello/ · vectors: https://redis.io/docs/latest/develop/ai/search-and-query/vectors/ · Valkey protocol: https://valkey.io/topics/protocol/ · Valkey Search: https://valkey.io/topics/search/
- **Auth:** `password` / `acl` — secret binding: `password`

## Scope

Key-value, pub/sub, Streams, and vector query against the **customer’s** Redis or Valkey server. One node; `engine=redis|valkey`. Streams are Redis/Valkey commands (`XADD` / `XREAD` / `XRANGE` / `XACK`), not extra nodes. Vector ops stay in this catalog (not AI/ML).

Handshake: send **`HELLO 3`** (optionally `AUTH username password`) so replies use RESP3 maps/sets. If the server rejects proto 3, fail with `{ error }` (do not silently stay on RESP2).

**Vector / `FT.SEARCH` gate**

- `engine=redis`: require Redis Query Engine (`FT.SEARCH`). Redis 8+ includes it in core. If `FT.SEARCH` returns unknown command, fail: Redis Query Engine not available.
- `engine=valkey`: require **valkey-search** (`FT.SEARCH` in `HELLO` `modules` or a probe). If missing, fail: install/enable valkey-search.

`pubsub.subscribe` is the provider listen operation (`trigger.listen`).

`key.keys` **must not** call `KEYS`. Implement with bounded **`SCAN`**.

No host filesystem paths. Customer owns the server.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `key.get` | — | `GET key` | Read string value |
| `key.set` | — | `SET key value [EX seconds] [PX ms] [NX\|XX]` | Write string |
| `key.delete` | — | `DEL key [key …]` | Delete keys |
| `key.keys` | — | `SCAN` loop (`MATCH`, `COUNT`) | List keys by pattern — **not** `KEYS` |
| `key.incr` | — | `INCR` / `INCRBY` | Integer increment |
| `server.info` | — | `INFO [section]` | Server sections (redact if needed) |
| `pubsub.publish` | — | `PUBLISH channel message` | Publish |
| `pubsub.subscribe` | — | `SUBSCRIBE` / `PSUBSCRIBE` | Trigger listen (hold connection) |
| `stream.add` | — | `XADD key [NOMKSTREAM] [*] field value …` | Append stream entry |
| `stream.read` | — | `XREAD` or `XREADGROUP` | Read new entries |
| `stream.range` | — | `XRANGE key start end [COUNT]` | Range by ID |
| `stream.ack` | — | `XACK key group id [id …]` | Ack pending consumer-group entries |
| `vector.upsert` | — | `HSET` (or `JSON.SET` if index is ON JSON) of vector + metadata | Upsert a vector record into an **existing** index prefix |
| `vector.query` | — | `FT.SEARCH index query PARAMS … DIALECT 2` | KNN vector query |

Retired: do not expose `KEYS`, `FLUSHALL`, `CONFIG SET`, or module load.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `key.get` | no | Chooses the row above |
| `engine` | select | yes | `redis` | no | `redis` \| `valkey` |
| `mode` | select | yes | `standalone` | no | `standalone` \| `cluster` \| `sentinel` |
| `auth` | select | yes | `password` | no | `password` (requirepass / `default` user) \| `acl` |
| `host` | string | if standalone | | no | Hostname |
| `port` | number | no | `6379` | no | TCP port |
| `hosts` | string | if cluster | | no | Comma-separated `host:port` seeds |
| `sentinelHosts` | string | if sentinel | | no | Sentinel `host:port` list |
| `sentinelMasterName` | string | if sentinel | | no | `SENTINEL get-master-addr-by-name` |
| `user` | string | if `acl` | `default` | no | ACL username (`HELLO 3 AUTH user password`) |
| `password` | password | if auth enabled | | yes | requirepass or ACL password |
| `tls` | boolean | no | `false` | no | TLS (Redis Cloud / stunnel) |
| `tlsCaPem` | password | no | | yes | CA **PEM string**, never a host path |
| `db` | number | no | `0` | no | `SELECT` index (not valid on cluster — fail if `mode=cluster` and `db≠0`) |
| `connectTimeoutMs` | number | no | `10000` | no | TCP connect |
| `commandTimeoutMs` | number | no | `30000` | no | Per-command |
| `maxReplyBytes` | number | no | `8388608` | no | 8 MiB reply cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No passwords |

FUNCTION ports: none. `pubsub.subscribe` is a trigger operation on this node.

## Input fields (by operation)

### `operation = key.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `key` | string | yes | Key |

### `operation = key.set`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `key` | string | yes | |
| `value` | string | yes | String payload (size-capped) |
| `exSeconds` | number | no | `EX` |
| `pxMilliseconds` | number | no | `PX` (XOR `exSeconds`) |
| `nx` | boolean | no | `NX` |
| `xx` | boolean | no | `XX` |

### `operation = key.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keys` | array | yes | 1–100 keys |

### `operation = key.keys`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `match` | string | no | `SCAN MATCH` (default `*`) |
| `count` | number | no | Hint per `SCAN` (default 100, max 1000) |
| `maxKeys` | number | no | Stop after N keys (default 1000, hard max 10000) |

### `operation = key.incr`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `key` | string | yes | |
| `by` | number | no | `INCRBY` amount (default 1) |

### `operation = server.info`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `section` | string | no | e.g. `server`, `memory`, `replication` |

### `operation = pubsub.publish`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel` | string | yes | |
| `message` | string | yes | Payload (capped) |

### `operation = pubsub.subscribe`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channels` | array | conditional | `SUBSCRIBE` list (XOR `patterns`) |
| `patterns` | array | conditional | `PSUBSCRIBE` |
| `maxMessages` | number | no | Complete after N messages (default 1, max 100) |
| `idleTimeoutMs` | number | no | End listen (default 60000, max 300000) |

### `operation = stream.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `key` | string | yes | Stream key |
| `fields` | object | yes | Field/value pairs |
| `id` | string | no | Explicit ID; default `*` |
| `maxlen` | number | no | Approximate `MAXLEN ~` |

### `operation = stream.read`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `streams` | object | yes | Map of stream key → start ID (`$` / `>` / last ID) |
| `count` | number | no | Default 10, max 100 |
| `blockMs` | number | no | `BLOCK` (cap 30000) |
| `group` | string | no | If set, `XREADGROUP` |
| `consumer` | string | if `group` | Consumer name |

### `operation = stream.range`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `key` | string | yes | |
| `start` | string | no | Default `-` |
| `end` | string | no | Default `+` |
| `count` | number | no | Default 100, max 1000 |

### `operation = stream.ack`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `key` | string | yes | |
| `group` | string | yes | |
| `ids` | array | yes | Entry IDs |

### `operation = vector.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `key` | string | yes | Hash/JSON key (must match index `PREFIX`) |
| `vectorField` | string | yes | Field name declared `VECTOR` on the index |
| `vector` | array | yes | Float32 components; length must equal index `DIM` |
| `fields` | object | no | Extra hash/JSON metadata fields |
| `index` | string | no | Unused for write; documented so operators know the index must already exist |

Index creation (`FT.CREATE`) is **not** a catalog operation. If `HSET` succeeds but the key is outside the index prefix, Query Engine will not see it — document that in `{ error }` only when `FT.SEARCH` later fails, not by inventing `FT.CREATE`.

### `operation = vector.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `index` | string | yes | `FT.SEARCH` index name |
| `vectorField` | string | yes | `@field` in KNN clause |
| `vector` | array | yes | Query vector (float32, little-endian blob on the wire) |
| `k` | number | no | Top K (default 10, max 100) |
| `filter` | string | no | Primary filter (`*` if omitted) |
| `returnFields` | array | no | `RETURN` |

Query shape (official):  
`FT.SEARCH <index> <filter>=>[KNN <k> @<vectorField> $vec] PARAMS 2 vec <blob> SORTBY <dist> DIALECT 2`

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `value` | string/null | key.get | Bulk string or null |
| `ok` | boolean | key.set | |
| `deleted` | number | key.delete | `DEL` count |
| `keys` | array | key.keys | SCAN results (capped) |
| `cursorExhausted` | boolean | key.keys | `true` if SCAN finished; `false` if hit `maxKeys` |
| `value` | number | key.incr | New integer |
| `info` | string | server.info | Raw INFO (no AUTH dump) |
| `receivers` | number | pubsub.publish | Subscriber count |
| `messages` | array | pubsub.subscribe | `{ channel, payload }` |
| `id` | string | stream.add | Entry ID |
| `entries` | array | stream.read / range | Stream records |
| `acked` | number | stream.ack | |
| `ok` | boolean | vector.upsert | |
| `hits` | array | vector.query | Keys, score, fields |
| `error` | object | failure | `{ message, code }` — no password |

## Legal

- Redis software / cloud terms: operator’s Redis Ltd. or self-host license (https://redis.io/legal — confirm live URL). Redis 8 source is dual-license; operator chooses the server they run.
- Valkey: Linux Foundation; https://valkey.io
- Quotas: Redis Cloud / Memorystore / ElastiCache limits belong to the operator.
- Keys, streams, and vectors may hold PII. Customer owns residency. Logs: command names, key **counts**, payload **sizes** — never values or passwords.
- Official RESP commands only.

## Implementation notes

- **Not JDK HTTP.** RESP is a TCP text/binary protocol (`*n\r\n$…`). Cluster needs `CLUSTER SLOTS` / MOVED/ASK redirects; Sentinel needs `SENTINEL get-master-addr-by-name`. TLS wrap if `tls`.
- Timeouts: connect 10s, command 30s; subscribe idle cap as input. Hard cap 60s per non-subscribe command.
- Size: `maxReplyBytes` 8 MiB; reject larger SET/PUBLISH/XADD bodies.
- Retries: once on connect failure for standalone; on `MOVED` follow once (cluster). No retry on `WRITE` after unknown outcome.
- `key.keys`: `SCAN 0 MATCH … COUNT …` until cursor `0` or `maxKeys`. Never `KEYS`.
- Vector gate before `vector.*`: `FT.SEARCH` probe or `HELLO` modules. Fail closed.
- Errors: `-ERR` / `-NOAUTH` / `-NOPERM` → `{ error }`. Strip passwords from `HELLO AUTH` logs.
