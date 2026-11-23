# Google Pub/Sub

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/google-pubsub/`
- **Researched:** 2026-08-20
- **Official API:** Cloud Pub/Sub **REST v1**, host `https://pubsub.googleapis.com`. Discovery: Pub/Sub API v1.
- **Docs:** https://cloud.google.com/pubsub/docs/reference/rest · Publish: https://cloud.google.com/pubsub/docs/reference/rest/v1/projects.topics/publish · Pull: https://cloud.google.com/pubsub/docs/reference/rest/v1/projects.subscriptions/pull · Acknowledge: https://cloud.google.com/pubsub/docs/reference/rest/v1/projects.subscriptions/acknowledge · Pull vs StreamingPull: https://cloud.google.com/pubsub/docs/pull · IAM: https://cloud.google.com/pubsub/docs/access-control · Quotas: https://cloud.google.com/pubsub/quotas
- **Auth:** `oauth2` | `serviceAccount` — secret binding: `oauthAccessToken` or `privateKeyPem`

## Scope

Call **Google Cloud Pub/Sub REST v1** with a customer-owned GCP project. One node for publish, pull, and listen-as-pull-loop.

**StreamingPull is gRPC**, not a REST method. This node’s `trigger.listen` is a **REST `subscriptions.pull` loop** (JDK HTTPS). Do not require a gRPC stack.

Push subscriptions (Pub/Sub POSTs to a public HTTPS endpoint) are **out of scope** — same NAT problem as SNS. Do not create push configs from this node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `topic.publish` | POST | `/v1/{topic}:publish` · `topic` = `projects/{project}/topics/{topic}` | Publish one or more messages |
| `subscription.pull` | POST | `/v1/{subscription}:pull` · `subscription` = `projects/{project}/subscriptions/{sub}` | One unary pull |
| `trigger.listen` | POST | same `…:pull` in a loop, then `…:acknowledge` | Wait for a message within this invoke |

Acknowledge (used by pull/listen, not a catalog operation): `POST /v1/{subscription}:acknowledge`.

OAuth scopes published on publish/pull: `https://www.googleapis.com/auth/pubsub` or `https://www.googleapis.com/auth/cloud-platform`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `topic.publish` | no | Row above |
| `auth` | select | yes | `serviceAccount` | no | `oauth2` (pre-issued access token) \| `serviceAccount` (JWT assertion) |
| `projectId` | string | yes | | no | GCP project id in resource names |
| `clientEmail` | string | if `serviceAccount` | no | Service account email |
| `privateKeyPem` | password | if `serviceAccount` | yes | PKCS#8 PEM (`-----BEGIN PRIVATE KEY-----`). Daemon secret. Never a host path or JSON file path |
| `oauthAccessToken` | password | if `oauth2` | yes | Bearer access token. Operator-supplied; this node does not run an interactive OAuth UI |
| `tokenEndpoint` | string | no | `https://oauth2.googleapis.com/token` | no | Official Google OAuth 2.0 token URL (service-account JWT grant) |
| `topicId` | string | if publish | | no | Topic id (not the full resource name). Combined as `projects/{projectId}/topics/{topicId}` |
| `subscriptionId` | string | if pull/listen | | no | Subscription id → `projects/{projectId}/subscriptions/{subscriptionId}` |
| `connectTimeoutSeconds` | number | no | `10` | no | TLS connect |
| `requestTimeoutSeconds` | number | no | `30` | no | Per HTTP call. Pull may block server-side; keep this ≥ expected wait |
| `listenTimeoutSeconds` | number | no | `25` | no | Overall listen cap. Max `120` |
| `maxPayloadBytes` | number | no | `10485760` | no | Official `data` field max **10 MB**. Fail closed if exceeded |
| `maxRetries` | number | no | `3` | no | Retry 429 / 5xx / network only |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No PEM / tokens |
| `output` | OUTPUT | object — success or `{ error }` | No secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = topic.publish`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topicId` | string | no | Override config |
| `data` | string | yes | Message payload UTF-8. Encoded as Base64 in JSON `messages[].data` per REST |
| `dataBase64` | string | no | If set, use as `data` already Base64 |
| `attributes` | object | no | String map. Official: ≤ 100 attributes, key ≤ 256 B, value ≤ 1024 B |
| `orderingKey` | string | no | Optional ordering key (throughput cap 1 MBps per key) |

Publish request body: `{ "messages": [ { "data": "<base64>", "attributes": { }, "orderingKey": "" } ] }`. Response: `{ "messageIds": [ "…" ] }`.

### `operation = subscription.pull`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscriptionId` | string | no | Override |
| `maxMessages` | number | no | Official required positive integer. Default 1. Unary pull response max **1000 messages / 10 MB** |
| `returnImmediately` | boolean | no | **Do not set true.** Official field is **deprecated** and harms pull performance |
| `autoAck` | boolean | no | Default **true**: `acknowledge` after successful OUTPUT |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscriptionId` | string | no | Override |
| `maxMessages` | number | no | Default 1 |
| `autoAck` | boolean | no | Default **true** |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` or `empty` |
| `messageIds` | list | publish | Server ids, same order as messages |
| `messages` | list | pull / listen | Each: `ackId`, `messageId`, `data` (UTF-8 if valid), `dataBase64`, `attributes`, `publishTime` |
| `ackIds` | list | pull / listen if not auto-acked | For a later ack (not a catalog op — auto-ack is the default) |
| `error` | string | failure | HTTP status + Pub/Sub error reason, no token/PEM |

## `trigger.listen` mapping (poll vs connection)

1. Loop REST `POST …:pull` with `maxMessages` until a non-empty `receivedMessages` **or** `listenTimeoutSeconds`. Then `POST …:acknowledge` with those `ackIds` if `autoAck`.
2. This is an **HTTP poll loop**, not StreamingPull. A stateful instance reuses TLS connections.
3. Do not open a push endpoint on the daemon.

StreamingPull (bidirectional gRPC) is documented for high throughput; it is **not** this node’s listen implementation.

## Legal

- Google APIs Terms of Service: https://developers.google.com/terms
- Google Cloud Terms: https://cloud.google.com/terms
- IAM: publisher role `roles/pubsub.publisher` (`pubsub.topics.publish`); subscriber `roles/pubsub.subscriber` (`pubsub.subscriptions.consume`). https://cloud.google.com/pubsub/docs/access-control
- Quotas (regional publisher/subscriber throughput, 10 MB message `data`): https://cloud.google.com/pubsub/quotas
- Message `data` and attributes may be PII/PHI. Customer owns the GCP project. Do not log payloads.
- Official REST only. No scrape of Cloud Console.

## Implementation notes

- JDK HTTPS. Service-account auth: RS256 JWT (`iss`=`clientEmail`, `scope`=`https://www.googleapis.com/auth/pubsub`, `aud`=`tokenEndpoint`) → `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`. Cache access token until `exp` minus 60 s. Never persist the PEM outside the daemon secret store.
- Timeouts: connect 10 s; pull HTTP timeout must cover server wait (do not set `returnImmediately=true`).
- Size: reject publish if decoded `data` > 10 MB or `maxPayloadBytes`.
- Retry: 429 / 503 / 500 / network with backoff + jitter. Honor `Retry-After`. Do not retry 401/403/404 without a single token refresh on 401 for service-account mode.
- Errors → `{ error }` on OUTPUT. Never throw past dispatch.
- Customer owns the project. Secrets in the daemon. No host FS paths.
