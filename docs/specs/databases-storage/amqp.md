# AMQP 1.0

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/amqp/`
- **Researched:** 2026-08-20
- **Official API:** OASIS **AMQP Version 1.0** (29 October 2012), ISO/IEC **19464:2014**. Used by Azure Service Bus, Apache Qpid, Apache ActiveMQ (AMQP 1.0 module).
- **Docs:** https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-overview-v1.0-os.html · Transport: https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-transport-v1.0-os.html · Security: https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-security-v1.0-os.html · Azure Service Bus AMQP: https://learn.microsoft.com/en-us/azure/service-bus-messaging/service-bus-amqp-protocol-guide · SAS: https://learn.microsoft.com/en-us/azure/service-bus-messaging/service-bus-sas
- **Auth:** `userPass` | `sas` — secret binding: `password` or `sasKey` / `sasToken`

## Scope

Send and receive on an **AMQP 1.0** broker: **connections, sessions, links** (sender/receiver), not AMQP 0-9-1 channels/exchanges.

**Distinct from `rabbitmq.md`.** RabbitMQ’s node is **AMQP 0-9-1** only. Do not use 0-9-1 `basic.publish` here even if the host is RabbitMQ with 1.0 enabled — operators who want 0-9-1 use `rabbitmq`.

Azure Service Bus: TLS on **5671** (or 5672 with mandatory TLS upgrade). SASL **PLAIN** (Shared Access Policy **name** + **key**) or **CBS** SAS token to `$cbs` (`put-token`, type `servicebus.windows.net:sastoken`). SBMP is being retired (Microsoft: 30 September 2026) — this node uses AMQP 1.0 only.

**Not HTTP.** Service Bus REST/HTTPS data plane is a different API; do not mix it into this node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `message.send` | — (AMQP 1.0) | Attach **sender** link to `address`; transfer one message; settle | Send to a queue or topic path |
| `trigger.listen` | — (AMQP 1.0) | Attach **receiver** link; wait for transfer | Next message on that address |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `message.send` | no | `message.send` \| `trigger.listen` |
| `auth` | select | yes | `userPass` | no | `userPass` (SASL PLAIN) \| `sas` (Azure SAS / CBS) |
| `host` | string | yes | | no | Broker hostname. Service Bus: `{namespace}.servicebus.windows.net` |
| `port` | number | no | `5672` (`5671` if `tls=true`) | no | AMQP 1.0. Azure: **5671** TLS |
| `tls` | boolean | no | `false` (`true` recommended; Azure requires TLS) | no | TLS before AMQP (Azure 5671) or AMQP upgrade |
| `tlsVerify` | boolean | no | `true` | no | Verify server certificate |
| `user` | string | if `userPass` or SASL PLAIN SAS | | no | SASL PLAIN username. Azure: **policy name** |
| `password` | password | if `userPass` | | yes | SASL PLAIN password. Azure: policy **key** (Base64 text) |
| `sasKeyName` | string | if `sas` and building a token | | no | Shared Access Policy name |
| `sasKey` | password | if `sas` and building a token | | yes | Policy key. Daemon secret |
| `sasToken` | password | if `sas` and pre-built | | yes | Full SAS token string for CBS `put-token`. If set, do not require `sasKey` |
| `entityPath` | string | no | | no | Default address (queue name, or `topic`, or `topic/subscriptions/sub`) |
| `connectTimeoutSeconds` | number | no | `10` | no | TCP/TLS + AMQP open |
| `requestTimeoutSeconds` | number | no | `30` | no | Link attach / disposition |
| `listenTimeoutSeconds` | number | no | `25` | no | Receiver wait this invoke. Max `120` |
| `maxPayloadBytes` | number | no | `1048576` | no | Cap on application-data. Also honor `max-message-size` from `open` if smaller |
| `maxRetries` | number | no | `3` | no | Reconnect on dropped connection only |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No keys/tokens |
| `output` | OUTPUT | object — success or `{ error }` | No secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = message.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `address` | string | no | Link target. Override `entityPath`. Example queue: `orders`. Service Bus topic: `orders`. Subscription receive uses listen, not send |
| `body` | string | yes | AMQP value / data UTF-8 (or `bodyBase64`) |
| `bodyBase64` | string | no | `amqp:data` binary |
| `contentType` | string | no | Properties `content-type` |
| `correlationId` | string | no | Properties `correlation-id` |
| `messageId` | string | no | Properties `message-id` |
| `durable` | boolean | no | Header durable. Default true |
| `applicationProperties` | object | no | String map → AMQP application-properties |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `address` | string | no | Receiver source. Service Bus subscription: `{topic}/Subscriptions/{name}` (exact path as the broker documents). Queue: queue name |
| `settlement` | select | no | `accept` (default, after OUTPUT) \| `release` on handler failure |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` or `empty` |
| `address` | string | | Link address |
| `body` | string | listen | UTF-8 if the data section is text |
| `bodyBase64` | string | listen | Raw application data |
| `messageId` | string | listen | If present |
| `correlationId` | string | listen | If present |
| `contentType` | string | listen | If present |
| `applicationProperties` | object | listen | Map of simple types |
| `error` | string | failure | AMQP error condition / description, never SAS key |

## `trigger.listen` mapping (poll vs connection)

AMQP 1.0 **pushes** transfers on a receiver link over a long-lived connection.

1. Prefer a **stateful instance**: `open` connection + session, attach receiver once, `flow` credit 1 (or a small window). This invoke waits for one transfer or `listenTimeoutSeconds`, then `accept` after OUTPUT.
2. Ephemeral: connect, attach, wait once, detach, close. Bound by listen timeout and the 300 s sandbox deadline.
3. Not HTTP. Azure WebSockets on 443 are optional later; **not** in this spec unless the operator cannot use 5671 — **Unverified (2026-08-20)** whether this node must offer AMQP-WebSockets. Do not implement WebSockets in v1.

## Legal

- OASIS AMQP 1.0: https://www.oasis-open.org/standard/amqp/
- Azure Service Bus: Microsoft Product Terms / customer agreement for the Azure subscription. AMQP guide: https://learn.microsoft.com/en-us/azure/service-bus-messaging/service-bus-amqp-protocol-guide
- Older Azure Service Bus SDKs / SBMP retirement notice (30 September 2026) is on that page — this node does not use SBMP.
- Payloads and SAS names may be PII or credentials. Customer owns the namespace/broker. Do not log bodies or SAS keys.
- Official AMQP 1.0 only. No scrape of Azure Portal.

## Implementation notes

- **Binary AMQP frames.** JDK HTTP is not sufficient.
- Azure `sas`: TLS; SASL ANONYMOUS + CBS to `$cbs` **or** SASL PLAIN with policy name + key (both are documented). Implement at least SASL PLAIN (`user`/`password`) which Azure documents as policy name + Base64 key; add CBS `put-token` when `sasToken` is set.
- Qpid / ActiveMQ: typically SASL PLAIN `userPass` on 5672/5671. Address is the queue/topic name the broker defines.
- Timeouts: open, attach, listen cap. Honor idle timeouts from `open`.
- Size: reject send if body > cap / peer `max-message-size`.
- Retry: reconnect on connection error. Do not retry `amqp:unauthorized-access` in a loop.
- Errors → `{ error }` on OUTPUT. Never throw past dispatch.
- Customer owns the broker. Secrets in the daemon. No host FS paths.
