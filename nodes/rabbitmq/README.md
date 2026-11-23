# RabbitMQ

Official [RabbitMQ Management HTTP API](https://www.rabbitmq.com/docs/management). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the RabbitMQ broker. Minimize: send only the records this operation needs. `password` is never logged or returned.

- **Docs:** https://www.rabbitmq.com/docs/management
- **Base:** `http://localhost:15672`
- **Auth:** HTTP basic. Username from config `user`; password from secret `password`.

## Distinct protocols

This package is **Management HTTP** only (`:15672`). It is **not** AMQP 0-9-1 binary (`:5672`) and **not** AMQP 1.0 (the separate `amqp` node). HTTP publish is documented as inefficient versus a messaging protocol; use it for operator/integration HTTP, not high-rate production publish.

Official HTTP API Reference lists **PUT** `/api/exchanges/{vhost}/{name}/publish` (all keys mandatory). `queue.send` publishes to the default exchange `amq.default` with `routing_key` = queue name.

## trigger.listen

`kind: ingress`. The management HTTP API is not a consumer. Use a BPMN message event; StudioQuack webhook ingress correlates onto those events.

## Engine gaps

`baseUrl` is `http://localhost:15672`. Remote brokers require changing `api_spec.baseUrl`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `exchange.send` | PUT | `/api/exchanges/{vhost}/{name}/publish` |
| `queue.send` | PUT | `/api/exchanges/{vhost}/amq.default/publish` |
| `queue.get` | POST | `/api/queues/{vhost}/{name}/get` |
| `trigger.listen` | ingress | — | BPMN message event (no socket) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `user` | string | no | Management HTTP username (basic auth userKey). Not a secret field; password is separate. |
| `password` | password | yes | Management HTTP password (same user database as the broker). StudioQuack secret. Never on INPUT. Default guest/guest is loopback-only. |
| `vhost` | string | no | Virtual host. Path-encoded (`/` → %2F). Default /. |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `password`.

## Legal

- Customer is the controller of vectors, payloads, and metadata they submit.
- Provider terms: https://www.rabbitmq.com/docs (operator’s broker terms)
- Official API only. Do not scrape consoles or dashboards.
