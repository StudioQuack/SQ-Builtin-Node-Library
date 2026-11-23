# RabbitMQ

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/rabbitmq/`
- **Researched:** 2026-08-20
- **Official API:** AMQP **0-9-1** (13 November 2008) as implemented by RabbitMQ. Current RabbitMQ docs tree **4.3**.
- **Docs:** https://www.rabbitmq.com/docs/amqp-0-9-1-protocol · https://www.rabbitmq.com/amqp-0-9-1-reference.html · https://www.rabbitmq.com/docs/connections · spec PDF: https://www.rabbitmq.com/resources/specs/amqp0-9-1.pdf
- **Auth:** `userPass` — secret binding: `password`

## Scope

Publish and consume on a **customer-owned** RabbitMQ node using **AMQP 0-9-1 only** (channels, exchanges, routing keys, `basic.publish` / `basic.get` / `basic.consume`).

**AMQP 1.0 is a different protocol and a different node** (`amqp.md`: Azure Service Bus, Qpid, ActiveMQ). RabbitMQ can speak AMQP 1.0 on the same ports; this node must still use 0-9-1 framing (connection.start / tune / open, then channels). Do not mix 1.0 sessions/links here.

**Not HTTP.** The Management plugin (`:15672`) is out of scope. JDK HTTP cannot speak AMQP.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `queue.send` | — (AMQP 0-9-1) | `basic.publish` to the **default exchange** (`""`) with `routing-key` = queue name | Put a message on a named queue |
| `exchange.send` | — (AMQP 0-9-1) | `basic.publish` to a named exchange + routing key | Publish to direct / topic / fanout / headers |
| `queue.get` | — (AMQP 0-9-1) | `basic.get` | Synchronous single-message poll (`get-ok` or `get-empty`) |
| `trigger.listen` | — (AMQP 0-9-1) | `basic.consume` (preferred) or repeated `basic.get` | Wait for the next delivery on a queue |

`immediate` on `basic.publish` is not supported by modern RabbitMQ; do not send it. `mandatory` is optional (see inputs).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `queue.send` | no | Row above |
| `auth` | select | yes | `userPass` | no | Only `userPass` (AMQP PLAIN). Virtual-host access is the broker ACL |
| `host` | string | yes | | no | Broker hostname. Example: `rabbitmq.internal.example.com` |
| `port` | number | no | `5672` (`5671` if `tls=true`) | no | AMQP port. Official defaults: **5672** clear, **5671** TLS |
| `vhost` | string | no | `/` | no | Virtual host. Must be percent-encoded in URIs; pass the raw name here |
| `user` | string | yes | | no | AMQP username |
| `password` | password | yes | | yes | AMQP password. Daemon secret. Never on INPUT |
| `tls` | boolean | no | `false` | no | AMQPS. When true, default port 5671 |
| `tlsVerify` | boolean | no | `true` | no | Verify server certificate |
| `clientCert` | file | no | | no | Optional mTLS cert, platform `file` ref-id. Never a host path |
| `clientKeyPem` | password | no | | yes | Optional mTLS key PEM |
| `heartbeatSeconds` | number | no | `30` | no | AMQP heartbeat (negotiated). 0 = off (not recommended) |
| `connectTimeoutSeconds` | number | no | `10` | no | TCP connect timeout |
| `requestTimeoutSeconds` | number | no | `30` | no | Publish confirm / get wait |
| `listenTimeoutSeconds` | number | no | `25` | no | `trigger.listen` cap for this invoke. Max `120` |
| `maxPayloadBytes` | number | no | `1048576` | no | Cap on message body (1 MiB). Fail closed if exceeded |
| `publisherConfirms` | boolean | no | `true` | no | Wait for `basic.ack` on publish when the broker supports confirms |
| `maxRetries` | number | no | `3` | no | Retries on connect / unexpected close only — not on 403 ACCESS_REFUSED |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — success or `{ error }` | Never echo password or PEM |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = queue.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `queue` | string | yes | Queue name = routing key on the default exchange |
| `body` | string | yes | UTF-8 body (or use `bodyBase64`) |
| `bodyBase64` | string | no | If set, decode as body bytes |
| `contentType` | string | no | AMQP `content-type` property. Example: `application/json` |
| `persistent` | boolean | no | `delivery-mode` 2 if true (default true) |
| `correlationId` | string | no | Property `correlation-id` |
| `replyTo` | string | no | Property `reply-to` |
| `headers` | object | no | AMQP headers table (string/number/boolean values only) |
| `mandatory` | boolean | no | `basic.publish` mandatory bit. Unroutable messages surface as `{ error }` if confirms/returns are collected |

### `operation = exchange.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `exchange` | string | yes | Exchange name (not the default `""` — use `queue.send` for that) |
| `routingKey` | string | no | Routing key. Empty string is valid (fanout) |
| `body` / `bodyBase64` / `contentType` / `persistent` / `correlationId` / `replyTo` / `headers` / `mandatory` | (same as `queue.send`) | | |

### `operation = queue.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `queue` | string | yes | Queue to poll |
| `autoAck` | boolean | no | `no-ack=true` if true (default **false** — ack after successful OUTPUT) |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `queue` | string | yes | Queue to consume |
| `autoAck` | boolean | no | Default **false**. Ack (`basic.ack`) only after successful OUTPUT |
| `prefetch` | number | no | `basic.qos` prefetch-count (default 1) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` or `empty` (`queue.get` / listen timeout with no delivery) |
| `exchange` | string | consume | Exchange from `basic.deliver` / `get-ok` |
| `routingKey` | string | consume | Routing key |
| `deliveryTag` | number | consume | Delivery tag (do not log as a secret; still do not put on other systems as auth) |
| `redelivered` | boolean | consume | Redelivered flag |
| `body` | string | consume | UTF-8 body if valid |
| `bodyBase64` | string | consume | Raw body |
| `contentType` | string | consume | Property if present |
| `correlationId` | string | consume | Property if present |
| `messageCount` | number | `queue.get` | Remaining count from `get-ok` |
| `error` | string | failure | Non-secret |

## `trigger.listen` mapping (poll vs connection)

Do not add a new BPMN construct. One invoke is one **delivery window**.

1. Prefer a **stateful instance** so the AMQP **connection + channel** stay open. On invoke: `basic.qos`, `basic.consume` (or keep an existing consumer), wait until one delivery **or** `listenTimeoutSeconds`. Then `basic.ack` after OUTPUT. Cancel the consumer only when the instance stops.
2. Ephemeral job: connect, consume/get once, ack, disconnect. Bound by listen timeout and the 300 s sandbox deadline.
3. AMQP 0-9-1 **pushes** deliveries on a live channel (`basic.deliver`). That is a long-lived TCP connection, not an HTTP poll. `queue.get` is the poll equivalent and may be used if consume is unavailable; `trigger.listen` should still prefer `basic.consume` with a wait cap so the invoke returns.

## Legal

- AMQP 0-9-1 specification (AMQP license as published by RabbitMQ): https://www.rabbitmq.com/amqp-0-9-1-protocol
- RabbitMQ commercial/support terms are the operator’s (VMware/Broadcom offering or self-host). Product docs: https://www.rabbitmq.com/docs
- Queue payloads may be PII/PHI. Customer owns the broker. Do not log bodies.
- Official AMQP 0-9-1 only. Do not scrape the Management UI.

## Implementation notes

- **Binary protocol:** AMQP 0-9-1 over TCP. Default ports **5672** / **5671** (TLS) per RabbitMQ networking docs (4.3).
- Distinct from `amqp.md` (OASIS AMQP 1.0).
- Timeouts: connect, heartbeat, and per-invoke listen cap. Do not block past `listenTimeoutSeconds`.
- Size: reject publish if body > `maxPayloadBytes`.
- Retry: reconnect with backoff on unexpected TCP close. Do not retry ACCESS_REFUSED / NOT_FOUND as if they were transient without a cap (max 1 retry on 404-style missing queue, then `{ error }`).
- Errors: channel/connection exceptions → `{ error }` with reply-code/text, never the password. Never throw past dispatch.
- Customer owns the broker. Secrets in the daemon. No host FS paths.
