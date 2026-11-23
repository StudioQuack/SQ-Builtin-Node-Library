# Amazon SNS

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/amazon-sns/`
- **Researched:** 2026-08-20
- **Official API:** Amazon SNS Query API **Version=2010-03-31**, action `Publish`. Auth: **AWS Signature Version 4**. HTTPS required.
- **Docs:** https://docs.aws.amazon.com/sns/latest/api/API_Publish.html · HTTP/HTTPS subscribers: https://docs.aws.amazon.com/sns/latest/dg/sns-http-https-endpoint-as-subscriber.html · Subscription confirmation: https://docs.aws.amazon.com/sns/latest/dg/SendMessageToHttp.confirm.html
- **Auth:** `awsKeys` | `iamRole` — secret binding: `secretAccessKey` (and optional `sessionToken`)

## Scope

**Publish** to an Amazon SNS topic (and the same `Publish` action’s official alternate targets). This is **not** Amazon SQS (`amazon-sqs.md`).

### DO NOT BUILD `trigger.listen`

SNS has **no pull API**. Official delivery to an application is **push**:

- HTTP/HTTPS subscription: AWS **POSTs** to a URL it can reach on the public internet, then requires confirmation via `SubscribeURL` GET or `ConfirmSubscription`.
- Amazon documents: **SNS does not support private HTTP(S) endpoints**.
- Email, SMS, mobile push, SQS, Lambda, Firehose are other subscription protocols — none of them is “open a connection from the daemon and listen.”

A self-hosted daemon behind NAT cannot receive those POSTs. StudioQuack message-event ingress is a **different** JSON contract (`X-SQ-API-KEY`, BPMN correlation) and is **not** an SNS confirmation endpoint. Implementing `trigger.listen` would mean a new public HTTPS receiver and subscription handshake — **out of scope**. Do not register `trigger.listen`.

Operators who need inbound messages: subscribe the SNS topic to an **SQS queue** (official) and use `amazon-sqs` `trigger.listen`.

Catalog still lists `trigger.listen`; this spec **withdraws** it.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `topic.publish` | POST (or GET) | `https://sns.{region}.amazonaws.com/` · `Action=Publish` · `Version=2010-03-31` | Publish one message to a topic (or official PhoneNumber / TargetArn) |

**Not built:** `trigger.listen`, `Subscribe`, `ConfirmSubscription`, `Unsubscribe`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `topic.publish` | no | Only `topic.publish` |
| `auth` | select | yes | `awsKeys` | no | `awsKeys` \| `iamRole` |
| `accessKeyId` | string | if `awsKeys` | | no | AWS access key id |
| `secretAccessKey` | password | if `awsKeys` | | yes | AWS secret access key |
| `sessionToken` | password | no | | yes | STS session token |
| `region` | string | yes | | no | AWS region (publish is same-region only, per Publish docs) |
| `topicArn` | string | yes | | no | Default `TopicArn`. Example: `arn:aws:sns:us-east-1:123456789012:orders` |
| `endpoint` | string | no | `https://sns.{region}.amazonaws.com` | no | HTTPS override |
| `connectTimeoutSeconds` | number | no | `10` | no | TLS connect |
| `requestTimeoutSeconds` | number | no | `30` | no | HTTP timeout |
| `maxPayloadBytes` | number | no | `262144` | no | Official non-SMS max **256 KB (262,144 bytes)** |
| `maxRetries` | number | no | `3` | no | Retry 429 / 5xx / network only |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `topic.publish` fields | No AWS keys. Phone numbers are PII |
| `output` | OUTPUT | object — `{ status, messageId }` or `{ error }` | No secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = topic.publish`

Official `Publish` requires **one of** `TopicArn`, `TargetArn`, or `PhoneNumber`.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topicArn` | string | one of three | Override config. Topic publish |
| `targetArn` | string | one of three | Platform endpoint ARN (mobile push). Same `Publish` action |
| `phoneNumber` | string | one of three | E.164 SMS. **PII.** Prefer not to store in workflow history |
| `message` | string | yes | `Message`. UTF-8. Size cap above (SMS has a separate 140-character / 1,600-character rule on the same action) |
| `subject` | string | no | Email subject when email subscriptions exist. **PII.** Max 100 characters, no line breaks (official) |
| `messageStructure` | string | no | Official value `json` to send per-protocol payloads. If set, `message` must be JSON with a `default` string key |
| `messageAttributes` | object | no | SNS message attributes |
| `messageGroupId` | string | FIFO topic: yes | FIFO grouping |
| `messageDeduplicationId` | string | FIFO: if no content-based dedup | FIFO dedup |

Default designer path: `topicArn` + `message` only.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `messageId` | string | success | SNS `MessageId` (max 100 characters) |
| `sequenceNumber` | string | FIFO success | FIFO sequence |
| `error` | string | failure | Map `AuthorizationError`, `NotFound`, `InvalidParameter`, `InvalidSecurity`, KMS errors — no secrets |

## `trigger.listen`

**DO NOT BUILD.** See Scope. There is no official listen/pull from the daemon outbound.

## Legal

- AWS Customer Agreement: https://aws.amazon.com/agreement/
- Service Terms: https://aws.amazon.com/service-terms/
- Publish size / SMS splitting: https://docs.aws.amazon.com/sns/latest/api/API_Publish.html
- **SMS and email deliveries are PII** (phone numbers, mailbox content). Customer is the data controller. Minimize logging: log `messageId` and byte size, never `Message`, `PhoneNumber`, or `Subject`.
- IAM: `sns:Publish` on the topic. HTTPS + SigV4 required (`InvalidSecurity` otherwise).
- Do not scrape the SNS console.

## Implementation notes

- JDK HTTPS + SigV4 on the Query API (`Action=Publish&Version=2010-03-31`). Prefer POST `application/x-www-form-urlencoded` so the message is not in the URL. Service name `sns`.
- Timeouts: connect 10 s, request 30 s.
- Size: reject if encoded `Message` > 262,144 bytes (non-SMS). SMS: enforce official character limits or let SNS error and map `{ error }`.
- Retry: throttling / 5xx / network only. Do not retry `NotFound` or `AuthorizationError`.
- Errors → `{ error }` on OUTPUT. Never throw past dispatch.
- Customer owns the AWS account. Secrets in the daemon.
