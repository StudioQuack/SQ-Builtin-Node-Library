# Amazon SQS

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/amazon-sqs/`
- **Researched:** 2026-08-20
- **Official API:** Amazon SQS API, query protocol dated **2012-11-05**; **AWS JSON protocol is the documented default** (`Content-Type: application/x-amz-json-1.0`, `X-Amz-Target: AmazonSQS.<Action>`). Auth: **AWS Signature Version 4**.
- **Docs:** https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/Welcome.html · SendMessage: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html · ReceiveMessage: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html · DeleteMessage: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteMessage.html · Long polling: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html · Quotas: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html · JSON requests: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-making-api-requests-json.html
- **Auth:** `awsKeys` | `iamRole` — secret binding: `secretAccessKey` (and optional `sessionToken`)

## Scope

Call **Amazon SQS** (not SNS, not EventBridge). SNS is `amazon-sns.md`.

Use HTTPS `POST https://sqs.{region}.amazonaws.com/` with SigV4. Prefer the **JSON protocol** as published (default in current SendMessage examples):

- Header `Content-Type: application/x-amz-json-1.0`
- Header `X-Amz-Target: AmazonSQS.SendMessage` | `AmazonSQS.ReceiveMessage` | `AmazonSQS.DeleteMessage`
- Body: JSON object with `QueueUrl` and action fields

The query protocol (`Action=SendMessage`, `application/x-www-form-urlencoded`) remains documented; implement JSON unless a regional endpoint rejects it.

`iamRole` means the daemon host’s default AWS credential chain (instance profile / env). Keys never go on INPUT.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `message.send` | POST | `/` · `AmazonSQS.SendMessage` | Enqueue one message |
| `message.receive` | POST | `/` · `AmazonSQS.ReceiveMessage` | One ReceiveMessage call (short or long poll) |
| `message.delete` | POST | `/` · `AmazonSQS.DeleteMessage` | Delete by `ReceiptHandle` from a prior receive |
| `trigger.listen` | POST | `/` · `AmazonSQS.ReceiveMessage` with long poll | Wait for a message (loop of long polls until timeout) |

Do not implement SendMessageBatch / DeleteMessageBatch in v1 (not in the catalog). Extended Client (S3 payload pointer) is **out of scope**.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `message.send` | no | Row above |
| `auth` | select | yes | `awsKeys` | no | `awsKeys` \| `iamRole` |
| `accessKeyId` | string | if `awsKeys` | | no | AWS access key id |
| `secretAccessKey` | password | if `awsKeys` | | yes | AWS secret access key |
| `sessionToken` | password | no | | yes | STS session token when using temporary keys |
| `region` | string | yes | | no | AWS region, e.g. `us-east-1`. Used as SigV4 region and in the SQS hostname |
| `queueUrl` | string | yes | | no | Full queue URL (case-sensitive). Example: `https://sqs.us-east-1.amazonaws.com/123456789012/orders` |
| `endpoint` | string | no | `https://sqs.{region}.amazonaws.com` | no | Override for VPC / compatible endpoints. Must be `https` |
| `connectTimeoutSeconds` | number | no | `10` | no | TLS connect timeout |
| `requestTimeoutSeconds` | number | no | `35` | no | HTTP timeout. Must exceed `WaitTimeSeconds` (max 20) plus margin |
| `listenTimeoutSeconds` | number | no | `25` | no | `trigger.listen` overall wait. Max `120` |
| `waitTimeSeconds` | number | no | `20` | no | ReceiveMessage long-poll wait. Official range **0–20**. Default 20 for listen |
| `maxPayloadBytes` | number | no | `1048576` | no | Official max message size **1,048,576 bytes (1 MiB)** (quotas page, 2026-08-20) |
| `maxRetries` | number | no | `3` | no | Retry 429 / 5xx / throttling / network only |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No AWS keys |
| `output` | OUTPUT | object — success or `{ error }` | Never return secret keys or full `Authorization` header |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = message.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `queueUrl` | string | no | Override config URL |
| `messageBody` | string | yes | `MessageBody`. Official: XML/JSON/unformatted text; Unicode set as in SendMessage. Min 1 character |
| `delaySeconds` | number | no | 0–900. FIFO: per-message delay is not allowed (queue-level only) |
| `messageAttributes` | object | no | Up to **10** attributes. Each: `{ dataType, stringValue }` (`String` \| `Number` \| `Binary`). Binary values as Base64 in `binaryValue` |
| `messageGroupId` | string | FIFO: yes | `MessageGroupId` (max 128). Required for FIFO queues |
| `messageDeduplicationId` | string | FIFO: if no content-based dedup | `MessageDeduplicationId` (max 128) |

### `operation = message.receive`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `queueUrl` | string | no | Override |
| `maxNumberOfMessages` | number | no | 1–10 (official). Default 1 |
| `waitTimeSeconds` | number | no | 0–20. Override config |
| `visibilityTimeout` | number | no | Seconds the message stays invisible. Official max 12 hours |
| `attributeNames` | list | no | As ReceiveMessage `AttributeNames` / system attributes. Default `["All"]` if omitted — **Unverified (2026-08-20)** whether `AttributeNames` is still accepted vs `MessageSystemAttributeNames` on all regions; send what the live ReceiveMessage page lists |

### `operation = message.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `queueUrl` | string | no | Override |
| `receiptHandle` | string | yes | `ReceiptHandle` from the receive that delivered the message (not `MessageId`) |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `queueUrl` | string | no | Override |
| `visibilityTimeout` | number | no | Same as receive |
| `autoDelete` | boolean | no | Default **true**: call DeleteMessage after successful OUTPUT. `false` = return `receiptHandle` for a later `message.delete` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` or `empty` (no messages) |
| `messageId` | string | send / receive | SQS `MessageId` |
| `md5OfMessageBody` | string | send | Integrity check from SendMessage |
| `sequenceNumber` | string | FIFO send | FIFO sequence |
| `receiptHandle` | string | receive / listen if not auto-deleted | Needed for delete |
| `body` | string | receive / listen | Message body |
| `attributes` | object | receive / listen | System attributes map |
| `messageAttributes` | object | receive / listen | User attributes |
| `error` | string | failure | Non-secret. Map `RequestThrottled`, `QueueDoesNotExist`, `InvalidMessageContents`, etc. |

## `trigger.listen` mapping (poll vs connection)

SQS has **no push to the daemon**. Official consume is **ReceiveMessage**.

1. One invoke: loop `ReceiveMessage` with `WaitTimeSeconds` (1–20, default 20) until a message arrives or `listenTimeoutSeconds` elapses. HTTP read timeout must be **greater than** `WaitTimeSeconds` (AWS docs: avoid HTTP errors by setting the client timeout longer than the wait).
2. This is **HTTP long poll**, not a standing TCP consumer. A stateful instance still helps (connection reuse) but each poll is a new HTTP request.
3. Do not open a public HTTPS endpoint. Do not use SNS HTTP subscriptions here.

## Legal

- AWS Customer Agreement: https://aws.amazon.com/agreement/
- Service Terms: https://aws.amazon.com/service-terms/
- SQS quotas (1 MiB body, 10 attributes, long poll 20 s, FIFO TPS): https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html
- Message bodies may be PII/PHI. Customer owns the AWS account and queue. Do not log bodies or receipt handles in INFO logs (ids and sizes only).
- IAM least privilege: `sqs:SendMessage`, `sqs:ReceiveMessage`, `sqs:DeleteMessage` on the queue ARN. Official auth requires HTTPS + SigV4 (`InvalidSecurity` otherwise).

## Implementation notes

- JDK HTTPS + SigV4 is sufficient (JSON protocol). No vendor SDK required.
- SigV4 service name: `sqs`. Region from config. Do not send over clear HTTP.
- Timeouts: connect 10 s; request ≥ waitTime + 5 s.
- Size: reject send if UTF-8 body (+ attributes, per AWS size rules) > `maxPayloadBytes` or official 1 MiB.
- Retry: 429 / `RequestThrottled` / 5xx / network, exponential backoff + jitter, `maxRetries`. Do not retry `QueueDoesNotExist` or `InvalidMessageContents`. Receive is safe to retry; processing must be idempotent (standard queues can deliver duplicates).
- Errors: HTTP 4xx/5xx and SQS error shapes → `{ error }` on OUTPUT. Never throw past dispatch. Never return `secretAccessKey`.
- Customer owns the queue. Secrets in the daemon.
