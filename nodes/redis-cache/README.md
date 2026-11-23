# Redis Cache

Generic Redis key/value and list node (RESP2). Handler: `RedisCacheNode`. Customer owns the Redis. Password and `redis(s)://` userinfo are never logged or returned. Failures raise a BPMN error (INVALID_INPUT, REQUEST_FAILED); secrets are redacted.

- **Wire to AI Agent `memory`:** the agent sends `RPUSH` / `LRANGE` with `key=sq:memory:<uuid>`.
- **Not** the Queue node (enqueue/dequeue) and **not** a vector store.

## Operations

| operation | Redis | Notes |
|---|---|---|
| `SET` | `SET` + optional `EXPIRE` | Objects are JSON-encoded |
| `GET` | `GET` | `data.value` is the string or null |
| `DEL` | `DEL` | |
| `LPUSH` / `RPUSH` | list push + optional `EXPIRE` | |
| `LRANGE` | `LRANGE start stop` | Default `0` / `-1` |
| `LTRIM` | `LTRIM` | |
| `EXPIRE` | `EXPIRE` | Requires positive `ttl_seconds` |

## Config

| Name | Secret | Purpose |
|---|---|---|
| `url` | yes if password embedded | `redis://` or `rediss://` |
| `host` / `port` / `db` | no | Used when `url` is blank |
| `password` | yes | `AUTH` |
| `verify_tls` | no | `rediss://` only; `false` is lab-only |
| `timeout_seconds` | no | Default 25, cap 60 |

## Legal

Customer is the controller of cached values (may include chat history / PHI). StudioQuack does not host Redis.
