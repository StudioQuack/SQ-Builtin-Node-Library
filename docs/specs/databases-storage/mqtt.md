# MQTT / HiveMQ

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/mqtt/`
- **Researched:** 2026-08-20
- **Official API:** OASIS **MQTT 3.1.1** (protocol level **4**) and **MQTT 5.0**. HiveMQ Cloud uses the **same MQTT protocol** (TLS + username/password on port **8883**), not a second node.
- **Docs:** MQTT 5.0: https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html · MQTT 3.1.1: https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html · index: https://mqtt.org/mqtt-specification/ · HiveMQ Cloud connect: https://docs.hivemq.com/hivemq-cloud/quick-start-guide.html · HiveMQ Cloud auth: https://docs.hivemq.com/hivemq-cloud/authn-authz.html
- **Auth:** `userPass` | `mtls` — secret binding: `password` and/or `clientKeyPem`

## Scope

CONNECT, PUBLISH, and SUBSCRIBE on a **customer-owned** MQTT broker (or HiveMQ Cloud). **One node.** `hivemqCloud=true` is a convenience flag: force TLS, default port **8883**, require username/password (HiveMQ Cloud Access Credentials). It does not change the protocol.

MQTT-SN, MQTT over WebSocket, and broker admin HTTP APIs are **out of scope**.

**Not HTTP.** Control packets on TCP.

Certificates: platform **`file` ref-id** or **PEM secrets**. **Never host paths** (`/etc/ssl/client.pem`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `message.publish` | — (MQTT) | CONNECT then PUBLISH (QoS 0/1/2) then DISCONNECT (or keep-alive on stateful instance) | Publish one payload to a topic |
| `trigger.listen` | — (MQTT) | CONNECT then SUBSCRIBE; wait for PUBLISH from the server | Next matching message |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `message.publish` | no | `message.publish` \| `trigger.listen` |
| `auth` | select | yes | `userPass` | no | `userPass` \| `mtls` (may combine: mTLS plus user/pass if the broker requires both) |
| `mqttVersion` | select | no | `5` | no | `3.1.1` (protocol level 4) \| `5` |
| `host` | string | yes | | no | Broker hostname. HiveMQ Cloud example shape: `{id}.s2.eu.hivemq.cloud` |
| `port` | number | no | `1883` (`8883` if `tls` or `hivemqCloud`) | no | Official defaults: 1883 clear, 8883 MQTT-TLS |
| `clientId` | string | yes | | no | CONNECT Client Identifier. Must be unique per concurrent connection on most brokers |
| `user` | string | if `userPass` or `hivemqCloud` | | no | CONNECT User Name |
| `password` | password | if password flag | | yes | CONNECT Password |
| `tls` | boolean | no | `false` | no | TLS. HiveMQ Cloud **requires** TLS |
| `tlsVerify` | boolean | no | `true` | no | Verify broker cert |
| `hivemqCloud` | boolean | no | `false` | no | If true: `tls=true`, default port 8883, `auth` must supply user/pass. Same MQTT 3.1.1/5 packets |
| `clientCert` | file | if `mtls` | | no | Client cert, platform `file` type (ref-id) |
| `clientCertPem` | password | if `mtls` and no file | | yes | PEM cert alternative to `clientCert` |
| `clientKeyPem` | password | if `mtls` | | yes | Client private key PEM |
| `caCert` | file | no | | no | Optional CA, `file` ref-id — not a host path |
| `connectTimeoutSeconds` | number | no | `10` | no | TCP/TLS + CONNACK wait |
| `keepAliveSeconds` | number | no | `30` | no | CONNECT Keep Alive |
| `requestTimeoutSeconds` | number | no | `30` | no | PUBACK/PUBCOMP / SUBACK wait |
| `listenTimeoutSeconds` | number | no | `25` | no | Listen cap this invoke. Max `120` |
| `maxPayloadBytes` | number | no | `1048576` | no | Cap on application payload. MQTT 5 Maximum Packet Size may be lower; honor CONNACK. Fail closed if exceeded |
| `maxRetries` | number | no | `3` | no | Reconnect on dropped TCP only |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No passwords/PEM |
| `output` | OUTPUT | object — success or `{ error }` | No secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = message.publish`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topic` | string | yes | PUBLISH topic name. MQTT topic rules (no wildcard on publish) |
| `payload` | string | yes | UTF-8 (or `payloadBase64`) |
| `payloadBase64` | string | no | Raw bytes |
| `qos` | number | no | 0, 1, or 2 (official). Default 1 |
| `retain` | boolean | no | RETAIN flag. Default false |
| `responseTopic` | string | no | MQTT 5 property |
| `correlationDataBase64` | string | no | MQTT 5 Correlation Data |
| `userProperties` | object | no | MQTT 5 User Property string map |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topicFilter` | string | yes | SUBSCRIBE filter (`+` / `#` allowed per spec) |
| `qos` | number | no | Maximum QoS for the subscription. Default 1 |
| `cleanSession` | boolean | no | 3.1.1 Clean Session / 5 Clean Start. Default **true** for ephemeral; prefer **false** on a stateful instance with a stable `clientId` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` or `empty` (listen timeout) |
| `topic` | string | listen / publish echo | Topic of the message |
| `payload` | string | listen | UTF-8 if valid |
| `payloadBase64` | string | listen | Raw |
| `qos` | number | listen | QoS of the incoming PUBLISH |
| `retain` | boolean | listen | RETAIN |
| `dup` | boolean | listen | DUP flag |
| `error` | string | failure | CONNACK return code / reason string, not the password |

## `trigger.listen` mapping (poll vs connection)

MQTT is a **long-lived TCP connection**. The server **pushes** PUBLISH packets after SUBSCRIBE.

1. Prefer a **stateful instance**: CONNECT once (stable `clientId`), SUBSCRIBE, keep PINGREQ/PINGRESP. Each invoke waits until the next matching PUBLISH or `listenTimeoutSeconds`, then OUTPUT. Complete QoS 1/2 handshake (PUBACK/PUBCOMP) after OUTPUT.
2. Ephemeral: CONNECT, SUBSCRIBE, wait once, DISCONNECT. Bound by listen timeout and the 300 s sandbox deadline.
3. Not HTTP long-poll. HiveMQ Cloud is the same mapping over TLS:8883.

## Legal

- MQTT 3.1.1 and 5.0 are OASIS standards (see URLs above).
- HiveMQ Cloud: operator accepts HiveMQ Cloud terms. Auth docs: https://docs.hivemq.com/hivemq-cloud/authn-authz.html · Quick start: https://docs.hivemq.com/hivemq-cloud/quick-start-guide.html. **Unverified (2026-08-20):** exact current HiveMQ Cloud ToS URL for UI copy — use the legal link HiveMQ publishes in the cloud console, not a guessed path.
- Topic payloads (including IoT telemetry) may be PII. Customer owns the broker. Do not log payloads or passwords.
- Official MQTT packets only. Do not scrape HiveMQ Control Center.

## Implementation notes

- **Binary MQTT packets**, not JDK HTTP.
- HiveMQ Cloud: TLS + username/password; cluster URL from their console; MQTT over TLS port **8883** (WebSocket **8884** is out of scope).
- Timeouts: CONNACK, keep-alive, listen cap. Do not wait unbounded.
- Size: reject if payload > `maxPayloadBytes` or negotiated Maximum Packet Size.
- Retry: reconnect + resubscribe on drop. Do not retry CONNACK “not authorized” in a tight loop (storm).
- Errors → `{ error }` on OUTPUT. Never throw past dispatch.
- Customer owns the broker. Secrets and cert bytes in the daemon / `file` store. No host FS paths.
