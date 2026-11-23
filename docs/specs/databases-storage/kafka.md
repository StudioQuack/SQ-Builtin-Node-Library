# Kafka

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/kafka/`
- **Researched:** 2026-08-20
- **Official API:** Apache Kafka TCP protocol (current docs tree **4.3**, `kafka.apache.org/43`). Produce **API key 0**, Fetch **API key 1**, plus consumer-group APIs (FindCoordinator, JoinGroup / consumer protocol, Heartbeat, OffsetCommit, OffsetFetch). Optional Confluent Schema Registry **REST API v1**.
- **Docs:** https://kafka.apache.org/43/design/protocol/ · https://kafka.apache.org/43/security/authentication-using-sasl/ · Schema Registry: https://docs.confluent.io/platform/current/schema-registry/develop/api.html
- **Auth:** `none` | `saslPlain` | `saslScram` | `ssl` — secret binding: `password` (SASL); optional `schemaRegistryPassword` / `schemaRegistryBearer`

## Scope

Talk to a **customer-owned** Kafka cluster (Apache Kafka or any broker that speaks the Kafka protocol, including Confluent Cloud) over the official **binary TCP protocol**. `message.send` is Produce. `trigger.listen` is a consumer-group Fetch. Confluent Cloud is the **same protocol**, not a second node.

Optional **Schema Registry HTTP** is a distinct auth surface on **this** node (`schemaRegistryUrl` plus basic or bearer secret). It is not a second catalog row.

Do **not** call Kafka REST Proxy, ksqlDB, or Connect. Do **not** take a host filesystem path for truststores, keytabs, or JAAS files. Client certificates, if used, are platform `file` ref-ids or PEM secrets.

**Not HTTP.** JDK HTTP cannot speak Produce/Fetch. Implement the Kafka protocol (or an official Kafka client library that speaks it). Schema Registry, when configured, is ordinary HTTPS.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `message.send` | — (TCP) | Produce (API key 0) to `topic` | Append one record (optional key) to a topic |
| `trigger.listen` | — (TCP) | Fetch (API key 1) + group membership | Long-lived consumer: wait for the next record(s) in `groupId` |

Schema Registry (only when `schemaRegistryUrl` is set; not separate operations):

| Call | HTTP | Path | When |
|---|---|---|---|
| Lookup schema | GET | `/schemas/ids/{id}` | Consume: resolve schema id from the payload prefix |
| Register schema | POST | `/subjects/{subject}/versions` | Produce: operator supplied `schemaJson` and wants a new id |

Content-Type / Accept: `application/vnd.schemaregistry.v1+json` (v1 is the published API version).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `message.send` | no | `message.send` \| `trigger.listen` |
| `auth` | select | yes | `none` | no | `none` (PLAINTEXT) \| `saslPlain` (SASL PLAIN) \| `saslScram` (SCRAM-SHA-256 or SCRAM-SHA-512) \| `ssl` (TLS, optional mTLS) |
| `brokers` | string | yes | | no | Bootstrap `host:port` list, comma-separated. Example: `broker-1.example.com:9092,broker-2.example.com:9092` |
| `topic` | string | yes | | no | Topic name. May be overridden per invoke on INPUT |
| `groupId` | string | if listen | | no | Consumer `group.id`. Required for `trigger.listen` |
| `username` | string | if SASL | | no | SASL username |
| `password` | password | if SASL | | yes | SASL password. Daemon secret. Never on INPUT |
| `saslMechanism` | select | if `saslScram` | `SCRAM-SHA-256` | no | `SCRAM-SHA-256` \| `SCRAM-SHA-512` (official Kafka SASL). Ignored unless `auth=saslScram` |
| `tls` | boolean | no | `false` (`true` when `auth=ssl` or SASL over TLS) | no | TLS to brokers. SASL without TLS (`SASL_PLAINTEXT`) is allowed only if the operator sets `tls=false`; prefer `tls=true` (`SASL_SSL`) |
| `tlsVerify` | boolean | no | `true` | no | Verify broker certificate. `false` is a documented operator opt-in, not a default |
| `clientCert` | file | if mTLS | | no | Client certificate, platform `file` ref-id. Never a host path |
| `clientKeyPem` | password | if mTLS | | yes | Client private key PEM |
| `schemaRegistryUrl` | string | no | | no | Optional Schema Registry base URL, e.g. `https://psrc-xxxxx.region.aws.confluent.cloud`. Empty = skip HTTP |
| `schemaRegistryAuth` | select | if URL set | `none` | no | `none` \| `basic` \| `bearer` |
| `schemaRegistryUsername` | string | if basic | | no | HTTP Basic user (Confluent Cloud API key) |
| `schemaRegistryPassword` | password | if basic | | yes | HTTP Basic password (Confluent Cloud API secret) |
| `schemaRegistryBearer` | password | if bearer | | yes | `Authorization: Bearer` token (Confluent Cloud OAuth/OIDC) |
| `acks` | select | no | `1` | no | Produce `acks`: `0` \| `1` \| `all` |
| `connectTimeoutSeconds` | number | no | `10` | no | TCP connect timeout |
| `requestTimeoutSeconds` | number | no | `30` | no | Produce / Fetch request timeout |
| `listenTimeoutSeconds` | number | no | `25` | no | `trigger.listen` wait cap for **this invoke** (see listen mapping). Max `120` |
| `maxPayloadBytes` | number | no | `1048576` | no | Cap on record value size (1 MiB). Fail closed if exceeded. Broker `message.max.bytes` may be lower |
| `maxRetries` | number | no | `3` | no | Retries on network / retriable broker errors only |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — success fields or `{ error }` | Failures never include secrets, SASL material, or PEM |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = message.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topic` | string | no | Override config `topic` |
| `key` | string | no | Record key (UTF-8). Empty = null key |
| `value` | string | yes | Record value (UTF-8). For binary, send Base64 in `valueBase64` instead |
| `valueBase64` | string | no | If set, decode as the value bytes and ignore `value` |
| `headers` | object | no | Optional record headers: string map, each value ≤ 256 bytes, ≤ 16 keys |
| `partition` | number | no | Explicit partition. Omit for default partitioner |
| `schemaJson` | string | no | If Schema Registry URL is set, POST this schema under subject `{topic}-value` then use the returned id. Omit to send raw bytes |
| `schemaId` | number | no | If set, prefix the value with the Schema Registry header for this id. **Unverified (2026-08-20):** exact Confluent prefix bytes (magic + id). Follow the current serdes “wire format” section; do not invent a layout |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topic` | string | no | Override config `topic` |
| `maxRecords` | number | no | Max records to return this invoke (1–100, default 1) |
| `autoOffsetReset` | select | no | `earliest` \| `latest` (default `latest`) when the group has no committed offset |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `topic` | string | success | Topic written / read |
| `partition` | number | success | Partition |
| `offset` | number | success | Produced offset, or last consumed offset |
| `timestamp` | string | listen success | Record timestamp (ISO-8601 if known) |
| `key` | string | listen | Record key, UTF-8 if valid, else omit |
| `value` | string | listen | Record value as UTF-8 if valid |
| `valueBase64` | string | listen | Always set for the raw value bytes |
| `headers` | object | listen | Record headers (string map; binary headers as Base64 values) |
| `schemaId` | number | listen, if registry prefix parsed | Schema id from payload prefix |
| `schemaJson` | string | listen, if registry lookup succeeded | Body of `GET /schemas/ids/{id}` `schema` field |
| `error` | string | failure | Actionable, non-secret message |

## `trigger.listen` mapping (poll vs connection)

Do not add a new BPMN construct. One invoke is one **Fetch window**.

1. Prefer a **stateful instance** of this library image so the TCP session (and group membership) can survive across invokes. Each invoke issues Fetch (and Heartbeat / OffsetCommit as required) and returns when `maxRecords` arrive **or** `listenTimeoutSeconds` elapses (empty success: `status=ok` with no `value`, so the engine can invoke again).
2. If the job is ephemeral, open the connection for this invoke only, Fetch once, commit offsets **after** a successful OUTPUT, then leave the group / close. Bound by `listenTimeoutSeconds` and the sandbox request deadline (platform default 300 s). Never wait unbounded.
3. This is a **pull** protocol (Fetch), not a broker-push HTTP webhook. Offsets commit only after OUTPUT is produced. On handler failure, do not commit (message is redelivered to the group).

## Legal

- Apache Kafka is Apache License 2.0. Protocol: https://kafka.apache.org/43/design/protocol/
- Operator accepts the terms of **their** cluster (self-hosted Apache Kafka, Confluent Cloud, MSK, etc.). Confluent Cloud Services Agreement (index): https://www.confluent.io/contracts/ · redirect: https://confluent.io/confluent-cloud-tos
- Schema Registry REST: https://docs.confluent.io/platform/current/schema-registry/develop/api.html — Confluent Platform / Cloud terms apply when using Confluent’s registry.
- Message keys/values may be PII/PHI. Customer owns the brokers and the payload. Do not log values.
- Official protocol and official Schema Registry HTTP only. No scrape of Control Center UI.

## Implementation notes

- **Binary protocol:** Kafka clients use a versioned TCP protocol (default port 9092, TLS often 9093). JDK HTTP is only for optional Schema Registry.
- SASL mechanisms published for Kafka 4.3: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI, OAUTHBEARER. This node implements **PLAIN**, **SCRAM-SHA-256/512**, and **ssl**. Do not add GSSAPI (keytab host path) or OAUTHBEARER unless a later spec adds a secret-only token.
- Timeouts: connect and request as configured. Fetch `max.wait.ms` ≤ `listenTimeoutSeconds`.
- Size: reject produce if encoded record > `maxPayloadBytes`. Truncate neither key nor value.
- Retry produce on retriable errors (network, NOT_LEADER_OR_FOLLOWER, REQUEST_TIMED_OUT) up to `maxRetries` with exponential backoff + jitter. Do not retry `TOPIC_AUTHORIZATION_FAILED`, `SASL_AUTHENTICATION_FAILED`, or invalid-record errors.
- Schema Registry HTTP: connect 10 s, read 15 s, retries on 429/5xx only. 401/403 → `{ error }` with no body echo.
- Errors: map broker error codes and HTTP 4xx/5xx to `{ error }` on OUTPUT. Never throw past dispatch. Never return passwords, tokens, or PEM.
- Customer owns the broker. Egress is the daemon policy. No host FS paths.
