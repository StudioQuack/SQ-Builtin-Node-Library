# Queue

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/queue/`
- **Researched:** 2026-08-22
- **Official API:** Redis serialization protocol (RESP2)
- **Docs:** https://redis.io/docs/latest/develop/reference/protocol-spec/
- **Auth:** url password (Redis) — secret binding: `url`
- **Handler:** `QueueNode` (`com.studioquack.nodelibrary.nodes.protocol.QueueNode`)

## Scope

Talks to the operator’s Redis (`redis://` or `rediss://`, prefer TLS) or a process-local in-memory list. `memory` is lab / single-replica only and is not shared across replicas. `listen` tells the operator to use a BPMN message event — the handler does not open a consumer socket.

AMQP is not a backend on this node. Use the `rabbitmq` node for AMQP 0-9-1.

## Operations (`operation` config values)

- `enqueue` — Redis `LPUSH` (then fail if `LLEN` was already at `max_len`); memory append
- `dequeue` — Redis `RPOP` (non-blocking); memory pop. Empty queue returns `value: null`
- `peek` — Redis `LINDEX key -1`; memory head
- `length` — Redis `LLEN`; memory size
- `listen` — ingress; no socket

Optional `ttl_seconds` after enqueue: Redis `EXPIRE` on the list key; memory expires that item.

## Configuration parameters

- `operation` (select, required, default enqueue) — verb
- `backend` (select, default redis) — `redis` or `memory`
- `url` (password, secret) — `redis://` or `rediss://` (may embed password). Unused for memory
- `key` (string) — queue / list name
- `ttl_seconds` (number, default 0) — optional TTL
- `max_len` (number, default 10000) — cap; enqueue fails when full
- `timeout_seconds` (number, default 25) — connect+read budget

## Ports

- INPUT `input`: operation, value (enqueue payload), key (override). Input wins over config.
- OUTPUT `output`: `{ status, data }`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, REQUEST_FAILED); secrets are redacted.

FUNCTION ports: none.

## Caps

- `max_len` default and hard cap: 10000
- Value size: 1000000 characters (RESP bulk cap)
- Memory backend: 64 distinct keys, process-local

## Legal

- Official Redis RESP. Customer broker.
- URL may contain a password — secret manager only, never logged.

## Implementation notes

- Redis uses the shared `RedisResp` codec (same as `RedisCacheNode`). One TCP/TLS connection per invoke.
- `rediss://` verifies the certificate (HTTPS endpoint identification). `redis://` is the explicit plain flag.
- `listen` raises INVALID_INPUT pointing at a BPMN message event (same text as OfficialApiNode).
- Timeouts from `timeout_seconds` (default 25, max 60).
