# AWS

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS`). Handler: `OfficialApiNode` + `api_spec.json`. Multi-service AWS HTTP with **SigV4 `serviceConfigKey: service`** — set config `service` to the SigV4 service code that matches the operation family for this instance.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put secrets here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

S3 ops on this node use **path-style** `https://s3.{region}.amazonaws.com/{Bucket}/…`. Dedicated Amazon S3 node is the same style. Virtual-hosted style is `{Bucket}.s3.{region}.amazonaws.com/{Key}` — see [Virtual hosting](https://docs.aws.amazon.com/AmazonS3/latest/userguide/VirtualHosting.html).

| operation | HTTP | Host / path | service | Notes |
|---|---|---|---|---|
| `bucket_create` | PUT | `s3.{region}.amazonaws.com/{Bucket}` | `s3` | Path-style |
| `bucket_delete` | DELETE | `/{Bucket}` | `s3` | Path-style |
| `bucket_list` | GET | `/` | `s3` | Path-style |
| `object_get` | GET | `/{Bucket}/{Key}` | `s3` | Path-style |
| `object_put` | PUT | `/{Bucket}/{Key}` | `s3` | `bodyFormat: raw`, `bodyParam: Body` |
| `object_delete` | DELETE | `/{Bucket}/{Key}` | `s3` | Path-style |
| `object_copy` | PUT | `/{Bucket}/{Key}` | `s3` | `x-amz-copy-source: {CopySource}` |
| `object_list` | GET | `/{Bucket}` | `s3` | Path-style |
| `lambda_invoke` | POST | `lambda.{region}.amazonaws.com/2015-03-31/functions/{FunctionName}/invocations` | `lambda` | REST; `Payload` object is the whole request body |
| `sqs_send` | POST | `sqs.{region}.amazonaws.com/?Action=SendMessage&Version=2012-11-05` | `sqs` | Query form |
| `sqs_receive` | POST | `/?Action=ReceiveMessage&Version=2012-11-05` | `sqs` | Query form |
| `sqs_delete` | POST | `/?Action=DeleteMessage&Version=2012-11-05` | `sqs` | Query form |
| `sns_publish` | POST | `sns.{region}.amazonaws.com/?Action=Publish&Version=2010-03-31` | `sns` | Query form; `Message` + one of `TopicArn` / `TargetArn` / `PhoneNumber` |
| `dynamodb_get` | POST | `dynamodb.{region}.amazonaws.com/` | `dynamodb` | `DynamoDB_20120810.GetItem` (`application/x-amz-json-1.0`); `TableName` + `ItemKey` (sent as `Key`) |
| `dynamodb_put` | POST | `/` | `dynamodb` | `DynamoDB_20120810.PutItem`; `TableName` + `Item` |
| `dynamodb_query` | POST | `/` | `dynamodb` | `DynamoDB_20120810.Query`; `TableName` + `KeyConditionExpression` (+ `ExpressionAttributeValues`) |
| `ses_send` | POST | `email.{region}.amazonaws.com/?Action=SendEmail&Version=2010-12-01` | `ses` | Classic SES Query (dedicated node uses SESv2 REST); `Source`, `ToAddress`, `Subject`, `TextBody` / `HtmlBody` |
| `iam_get` | POST | `iam.amazonaws.com/?Action=GetUser&Version=2010-05-08` | `iam` | Sign with region `us-east-1`; optional `UserName` |
| `iam_get_role` | POST | `iam.amazonaws.com/?Action=GetRole&Version=2010-05-08` | `iam` | Sign with region `us-east-1`; `RoleName` |
| `secretsmanager_get` | POST | `secretsmanager.{region}.amazonaws.com/` | `secretsmanager` | `secretsmanager.GetSecretValue` |
| `cloudwatch_put` | POST | `monitoring.{region}.amazonaws.com/?Action=PutMetricData&Version=2010-08-01` | `monitoring` | Query form; one datapoint: `Namespace`, `MetricName`, `MetricValue`, optional `MetricUnit` |
| `ecs_run` | POST | `ecs.{region}.amazonaws.com/` | `ecs` | `AmazonEC2ContainerServiceV20141113.RunTask`; `taskDefinition` |
| `sts_assume` | POST | `sts.{region}.amazonaws.com/?Action=AssumeRole&Version=2011-06-15` | `sts` | Query form; `RoleArn` + `RoleSessionName` |
| `eventbridge_put` | POST | `events.{region}.amazonaws.com/` | `events` | `AWSEvents.PutEvents`; `Entries` |
| `listen` | ingress | — | — | BPMN message event |

Docs: [DynamoDB GetItem](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_GetItem.html) · [Lambda Invoke](https://docs.aws.amazon.com/lambda/latest/api/API_Invoke.html) · [IAM CreateUser / Query](https://docs.aws.amazon.com/IAM/latest/APIReference/API_CreateUser.html)

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `access_key_id` | password | yes | yes | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | yes | yes | IAM secret access key (SigV4 signing key) |
| `session_token` | password | no | yes | Optional STS session token (`x-amz-security-token`) |
| `region` | string | yes | no | Signing region (and most hosts) |
| `service` | string | yes | no | SigV4 service code for **this instance** (`s3`, `lambda`, `sqs`, …). Must match the operation family. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. OfficialApiNode signs after the body is final. `service` comes from instance config (`serviceConfigKey`).
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters`. Never logged, never returned.
- [AWS docs](https://docs.aws.amazon.com/). Terms: https://aws.amazon.com/service-terms/.
- AWS Customer Agreement. One node instance: one `service` + one operation family. Never log keys.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
