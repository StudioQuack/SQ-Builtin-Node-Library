# MongoDB

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/mongodb/`
- **Researched:** 2026-08-20
- **Official API:** MongoDB Wire Protocol — **OP_MSG** (opcode `2013`). Commands as of Database Manual **v8.2**. Atlas Search index commands since server **7.0** (also 6.0.7+)
- **Docs:** https://www.mongodb.com/docs/manual/reference/mongodb-wire-protocol/ · commands: https://www.mongodb.com/docs/manual/reference/command/ · search indexes: https://www.mongodb.com/docs/manual/reference/command/createSearchIndexes/
- **Auth:** `connectionString` / `x509` / `awsIam` — secret binding: `uri` (password embedded), `password`, `tlsCaPem`, `x509Pem`, `awsSecretAccessKey`, `awsSessionToken`

## Scope

Talks to the **customer’s** `mongod` / `mongos` / Atlas cluster over TCP (default **27017**) using **OP_MSG only**. Legacy opcodes (`OP_QUERY`, `OP_INSERT`, `OP_UPDATE`, `OP_DELETE`, `OP_GET_MORE`, `OP_REPLY`, `OP_KILL_CURSORS`) were removed in MongoDB 5.1 and must not be sent.

**Do not call Atlas Data API or any App Services HTTPS endpoint.** Atlas App Services (Data API, HTTPS Endpoints, GraphQL, Device Sync) reached **EOL 2025-09-30**. Official: https://www.mongodb.com/docs/atlas/app-services/deprecation/ and https://www.mongodb.com/docs/atlas/app-services/data-api/data-api-deprecation/. There is no HTTPS CRUD fallback.

Atlas Search / Vector Search **index** ops (`createSearchIndexes`, `$listSearchIndexes`, `updateSearchIndex`, `dropSearchIndex`) are **database commands / aggregation** on the wire. They run only on **Atlas** (typically M10+). On Community/Enterprise self-host, fail with `{ error }` saying Search indexes require Atlas.

Customer owns the database. Atlas still requires the daemon egress IP on the [project IP access list](https://www.mongodb.com/docs/atlas/security/ip-access-list/).

## Operations (`operation` config values)

All rows are OP_MSG command documents (BSON body section kind 0). Not HTTP.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `document.aggregate` | — | `aggregate` command | Pipeline aggregation (including `$vectorSearch` / `$search` on Atlas) |
| `document.find` | — | `find` command | Filter + projection + limit |
| `document.insert` | — | `insert` command | Insert one or more documents |
| `document.update` | — | `update` command | `update` + `updates[]` with filter/u/multi/upsert |
| `document.findAndUpdate` | — | `findAndModify` (`update` operators) | Atomic find + modify; return pre/post image |
| `document.findAndReplace` | — | `findAndModify` (replacement doc) | Atomic find + replace entire document |
| `document.delete` | — | `delete` command | Delete matching documents |
| `searchIndex.create` | — | `createSearchIndexes` | Atlas Search or Vector Search index |
| `searchIndex.list` | — | `aggregate` + `$listSearchIndexes` | List Atlas search indexes on a collection |
| `searchIndex.update` | — | `updateSearchIndex` | Replace an Atlas search index definition |
| `searchIndex.drop` | — | `dropSearchIndex` | Drop an Atlas search index |

There is **no** `listSearchIndexes` database command; listing is the `$listSearchIndexes` aggregation stage (https://www.mongodb.com/docs/manual/reference/operator/aggregation/listsearchindexes/).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `document.find` | no | Chooses the row above |
| `auth` | select | yes | `connectionString` | no | `connectionString` \| `x509` \| `awsIam` |
| `uri` | password | if `connectionString` | | yes | Standard connection string (`mongodb://` / `mongodb+srv://`). Password lives here, not on INPUT |
| `hosts` | string | if not `uri` | | no | `host:port` list when not using a URI |
| `database` | string | yes | | no | Auth/default database name; collection ops use `input.database` override if set |
| `user` | string | if `connectionString` without user in URI | | no | SCRAM user |
| `password` | password | if user without password in URI | | yes | SCRAM password |
| `authSource` | string | no | `admin` | no | Authentication database |
| `authMechanism` | select | no | from URI / SCRAM-SHA-256 | no | `SCRAM-SHA-256` \| `SCRAM-SHA-1` \| `MONGODB-X509` \| `MONGODB-AWS` |
| `x509Pem` | password | if `x509` | | yes | Client certificate **PEM string** (cert + key). Not a host path |
| `tls` | boolean | no | `true` if `mongodb+srv` else `false` | no | TLS to the server |
| `tlsCaPem` | password | no | | yes | CA bundle **PEM string**. Never a filesystem path |
| `tlsAllowInvalidHostnames` | boolean | no | `false` | no | Only for broken lab certs; default off |
| `awsAccessKeyId` | string | if `awsIam` and not default chain | | no | `MONGODB-AWS` |
| `awsSecretAccessKey` | password | if `awsIam` | | yes | `MONGODB-AWS` |
| `awsSessionToken` | password | no | | yes | STS session token |
| `replicaSet` | string | no | | no | Replica set name if not in URI |
| `readPreference` | select | no | `primary` | no | `primary` \| `primaryPreferred` \| `secondary` \| `secondaryPreferred` \| `nearest` |
| `connectTimeoutMs` | number | no | `10000` | no | TCP + handshake timeout |
| `socketTimeoutMs` | number | no | `30000` | no | Per-command read timeout |
| `maxBsonBytes` | number | no | `16777216` | no | Cap BSON payload (16 MiB MongoDB default). Hard cap 16 MiB |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ ok, … }` or `{ error }` | Failures never include URI/password/PEM |

FUNCTION ports: none.

## Input fields (by operation)

Shared: `database` (string, optional override), `collection` (string, required for all listed ops).

### `operation = document.aggregate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pipeline` | array | yes | Aggregation pipeline documents |
| `batchSize` | number | no | Cursor batch size (default 100, max 1000) |
| `maxTimeMS` | number | no | Server-side cap |

### `operation = document.find`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `filter` | object | no | Query filter (default `{}`) |
| `projection` | object | no | Field projection |
| `sort` | object | no | Sort document |
| `limit` | number | no | Max docs (default 100, max 1000) |
| `skip` | number | no | Skip |
| `maxTimeMS` | number | no | Server-side cap |

### `operation = document.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `documents` | array | yes | One or more documents (max 100 per call; each ≤ 16 MiB) |
| `ordered` | boolean | no | Default `true` |

### `operation = document.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `updates` | array | yes | `{ q, u, multi?, upsert?, arrayFilters? }` per server `update` command |
| `ordered` | boolean | no | Default `true` |

### `operation = document.findAndUpdate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | object | yes | `findAndModify.query` |
| `update` | object | yes | Update operators (`$set`, …) — not a replacement |
| `sort` | object | no | Which match to modify |
| `new` | boolean | no | Return modified doc (`true`) or original |
| `upsert` | boolean | no | Default `false` |
| `fields` | object | no | Projection of returned doc |

### `operation = document.findAndReplace`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | object | yes | `findAndModify.query` |
| `replacement` | object | yes | Full replacement (no `$` operators) |
| `sort` | object | no | |
| `new` | boolean | no | |
| `upsert` | boolean | no | |

### `operation = document.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `deletes` | array | yes | `{ q, limit }` where `limit` is `0` (all) or `1` |
| `ordered` | boolean | no | Default `true` |

### `operation = searchIndex.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `indexes` | array | yes | `{ name?, type?: search\|vectorSearch, definition }` per `createSearchIndexes` |

### `operation = searchIndex.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | no | Filter by index name (XOR `id`) |
| `id` | string | no | Filter by index id |

### `operation = searchIndex.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | conditional | Index name XOR `id` |
| `id` | string | conditional | Index id XOR `name` |
| `definition` | object | yes | Replacement definition |

### `operation = searchIndex.drop`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | conditional | Index name XOR `id` |
| `id` | string | conditional | Index id XOR `name` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `ok` | number | success | Command `ok: 1` |
| `documents` | array | find / aggregate / findAnd* | Result docs (capped) |
| `cursorId` | string | find / aggregate with leftover | Remaining cursor; handler **must** `killCursors` if not fully drained within timeout |
| `n` / `nModified` / `nUpserted` | number | insert/update/delete | Server counts |
| `insertedIds` | array | insert | `_id`s |
| `value` | object | findAnd* | Returned document |
| `indexesCreated` | array | searchIndex.create | `{ id, name }` |
| `indexes` | array | searchIndex.list | `$listSearchIndexes` docs |
| `error` | object | failure | `{ message, code }` — no secrets, no PEM, no URI |

Drain cursors: loop `getMore` until exhausted or `maxDocuments` / `maxBsonBytes` hit, then `killCursors`. Never leave server cursors open after the node returns.

## Legal

- MongoDB ToS: https://www.mongodb.com/legal/terms-of-use
- Atlas: https://www.mongodb.com/legal/atlas-terms-of-service
- Wire protocol spec is CC-BY-NC-SA 3.0 US and forbids adapting it to offer a commercial DBaaS. This node is a **client** of the operator’s server, not a MongoDB service.
- Atlas quotas / cluster tier limits apply to the operator’s project. Atlas Search index commands require Atlas.
- Documents may contain PII/PHI. Customer owns residency and encryption. Logs: command name, collection, counts, sizes — never document bodies or credentials.
- Official wire protocol only. No Data API, no unofficial HTTP.

## Implementation notes

- **Not JDK HTTP.** OP_MSG is a little-endian TCP/BSON framing protocol (opcode 2013); HTTPS Data API is EOL. Implement OP_MSG (+ optional `OP_COMPRESSED` zlib/zstd if the handshake offers it). TLS when `tls` / `mongodb+srv`.
- Handshake: `hello` (legacy `OP_QUERY` `hello`/`isMaster` is still allowed **only** for the handshake per the wire spec). Then SASL (SCRAM) / x509 / AWS as configured.
- Timeouts: `connectTimeoutMs` / `socketTimeoutMs` as config. Hard cap read **60s**.
- Size: enforce 16 MiB BSON object cap; `limit`/`batchSize` defaults above. Reject oversized INPUT.
- Retries: retry **once** on network reset / `NotPrimary` after rediscovery for idempotent `find`/`aggregate`/`searchIndex.list` only. Do not retry insert/update/delete.
- Errors: map server `{ ok: 0, errmsg, code }` to `{ error }`. Strip `uri`, passwords, PEM from any message.
- Atlas IP access list: if connect fails with Atlas-style network error, `{ error }` must mention allowing the **client daemon egress IP** on the project IP access list.
- `tlsCaPem` / `x509Pem`: parse PEM from the secret string into an in-memory keystore. **No host filesystem paths.**
