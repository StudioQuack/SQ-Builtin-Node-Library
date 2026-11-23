# NATS

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/nats/`
- **Researched:** 2026-08-20
- **Official API:** NATS **client protocol** (docs version **2.14**). Core: `INFO` / `CONNECT` / `PUB` / `HPUB` / `SUB` / `MSG`. JetStream pull: `$JS.API.CONSUMER.MSG.NEXT.{stream}.{consumer}`.
- **Docs:** https://docs.nats.io/reference/protocols/client · Request-reply: https://docs.nats.io/concepts/request-reply · JetStream: https://docs.nats.io/concepts/jetstream · Get next: https://docs.nats.io/reference/jetstream/api/consumer/get-next · NKey: CONNECT `nkey` + `sig` fields on https://docs.nats.io/reference/protocols/client
- **Auth:** `token` | `nkey` | `userPass` — secret binding: `token` / `nkeySeed` / `password`

## Scope

Talk to a **customer-owned** NATS server (or leaf) using the official text protocol over TCP (default **4222**). **Core NATS and JetStream are one node.**

**No `credsFile` and no host path.** Catalog historically listed `credsFile` — **do not implement it**. NKey material is a **seed string secret** (`nkeySeed`). Token and user/password are secrets. Do not read `*.creds` / `*.nk` from the daemon filesystem.

CONNECT fields used (official): `auth_token`, `user`/`pass`, or `nkey` + `sig` (sign the `nonce` from `INFO` with the seed). Optional `tls_required`.

**Not HTTP.** Monitoring port (`/varz`) is out of scope.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `core.publish` | — (NATS TCP) | `PUB` / `HPUB` | Fire-and-forget to a subject |
| `core.request` | — (NATS TCP) | `PUB` with inbox reply-to; wait for `MSG` | Request-reply (official inbox pattern) |
| `jetstream.publish` | — (NATS TCP) | `PUB` to a stream-captured subject and wait for PubAck on the reply | Persistent publish |
| `jetstream.consume` | — (NATS TCP) | Request `$JS.API.CONSUMER.MSG.NEXT.{stream}.{consumer}` | One pull batch (`batch` / `expires`) |
| `trigger.listen` | — (NATS TCP) | Core: `SUB` wait for `MSG`. JetStream: pull loop on `MSG.NEXT` | One invoke = one wait window |

JetStream consume **does not create** streams/consumers in v1 unless the operator already created them. Missing consumer → `{ error }`. Do not invent admin CRUD ops.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `core.publish` | no | Row above |
| `auth` | select | yes | `token` | no | `token` \| `nkey` \| `userPass` |
| `servers` | string | yes | | no | Comma-separated `host:port` or `nats://host:port` / `tls://host:port`. Example: `nats.example.com:4222` |
| `token` | password | if `token` | | yes | CONNECT `auth_token` |
| `nkeySeed` | password | if `nkey` | | yes | **Seed string** (NATS user seed, typically `SU…`). Not a file path. Derive public nkey; sign `INFO.nonce` → CONNECT `sig` |
| `user` | string | if `userPass` | | no | CONNECT `user` |
| `password` | password | if `userPass` | | yes | CONNECT `pass` |
| `tls` | boolean | no | `false` | no | TLS. Honor `tls_required` from `INFO` even if this is false — fail closed if the server requires TLS and we cannot upgrade |
| `tlsVerify` | boolean | no | `true` | no | Verify server cert |
| `subject` | string | if core ops | | no | Default subject |
| `stream` | string | if JetStream consume/listen-js | | no | Stream name in `MSG.NEXT` |
| `consumer` | string | if JetStream consume/listen-js | | no | Durable / consumer name |
| `listenMode` | select | if listen | `core` | no | `core` (SUB) \| `jetstream` (pull) |
| `connectTimeoutSeconds` | number | no | `10` | no | TCP connect |
| `requestTimeoutSeconds` | number | no | `30` | no | Request-reply / PubAck / pull `expires` |
| `listenTimeoutSeconds` | number | no | `25` | no | Listen cap this invoke. Max `120` |
| `maxPayloadBytes` | number | no | `1048576` | no | Cap; also honor `INFO.max_payload` (often 1 MiB). Fail if body > min(cap, max_payload) |
| `maxRetries` | number | no | `3` | no | Reconnect / stale connection only |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No seeds/tokens |
| `output` | OUTPUT | object — success or `{ error }` | No secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = core.publish`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subject` | string | no | Override |
| `body` | string | yes | Payload UTF-8 (or `bodyBase64`) |
| `bodyBase64` | string | no | Raw bytes |
| `headers` | object | no | If set, use `HPUB` (`NATS/1.0` headers). String map |
| `replyTo` | string | no | Optional reply subject on `PUB` |

### `operation = core.request`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subject` | string | no | Request subject |
| `body` / `bodyBase64` / `headers` | (same) | | |
| `timeoutSeconds` | number | no | Override `requestTimeoutSeconds`. No responders → official `-ERR` / no-responders |

### `operation = jetstream.publish`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subject` | string | no | Must be captured by a stream |
| `body` / `bodyBase64` / `headers` | (same) | | |
| `msgId` | string | no | `Nats-Msg-Id` header for dedup when the operator uses it |

### `operation = jetstream.consume`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `stream` | string | no | Override |
| `consumer` | string | no | Override |
| `batch` | number | no | Official pull `batch` (docs max **256**). Default 1 |
| `expiresNs` | number | no | Official `expires` in **nanoseconds**. If omitted, set from `requestTimeoutSeconds` |
| `noWait` | boolean | no | Official `no_wait`. Default false |
| `ack` | boolean | no | Default **true**: ack the message (`+ACK` per JetStream ack protocol) after OUTPUT |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subject` | string | if `listenMode=core` | SUB filter (wildcards allowed per NATS subject rules) |
| `queueGroup` | string | no | Core queue group on `SUB` |
| `stream` / `consumer` / `batch` / `ack` | | if `jetstream` | Same as consume |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` or `empty` |
| `subject` | string | consume / listen / reply | Subject of the message |
| `body` | string | | UTF-8 if valid |
| `bodyBase64` | string | | Raw |
| `headers` | object | | NATS headers if `HMSG` |
| `replyTo` | string | | Reply subject if present |
| `stream` | string | JetStream | From PubAck / message meta if present |
| `sequence` | number | JetStream | Stream sequence if present |
| `duplicate` | boolean | JetStream publish | PubAck duplicate flag if present |
| `error` | string | failure | `-ERR` text or timeout. Never seed/token/password |

## `trigger.listen` mapping (poll vs connection)

Do not add BPMN.

1. **Core (`listenMode=core`):** long-lived **TCP + SUB**. Prefer a stateful instance. This invoke waits for the next `MSG`/`HMSG` or `listenTimeoutSeconds`, then OUTPUT. UNSUB when the instance stops, not after every invoke if the subscription is kept.
2. **JetStream:** official pull is **request/response** on `$JS.API.CONSUMER.MSG.NEXT.{stream}.{consumer}` with JSON `{ "batch", "expires", "no_wait", … }`. Listen = repeat pull until a message or timeout. Empty pull (`408` / `404` status headers on the official pull protocol) is `status=empty`, not a crash.
3. Never a public HTTP webhook.

## Legal

- NATS Server is Apache License 2.0. Protocol: https://docs.nats.io/reference/protocols/client
- If the operator uses a hosted NATS (e.g. Synadia Cloud), **their** cloud agreement applies. This node does not sign that agreement.
- Subjects and payloads may be PII. Customer owns the server. Do not log bodies or `nkeySeed`.
- Official protocol only. No scrape of the monitoring UI.

## Implementation notes

- **Binary/text protocol on TCP**, not REST. Default `max_payload` in the public `INFO` example is 1048576.
- **nkeySeed is a string secret.** Parse seed in memory, sign nonce, drop the seed from logs. Never `nkeySeed=/var/secrets/user.nk`.
- Timeouts: connect; request-reply; pull `expires` ≤ invoke budget.
- Size: reject PUB if payload > cap / `max_payload`.
- Retry: reconnect on stale socket. Authorization Violation is not retriable with the same secrets.
- Errors → `{ error }` on OUTPUT. Never throw past dispatch.
- Customer owns the broker. Secrets in the daemon.
