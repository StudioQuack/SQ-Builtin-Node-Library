# Amazon Redshift

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/amazon-redshift/`
- **Researched:** 2026-08-20
- **Official API:** Amazon Redshift Data API (`redshift-data`, API date `2019-12-20`)
- **Docs:** https://docs.aws.amazon.com/redshift/latest/mgmt/data-api.html
- **Auth:** `awsKeys` | `iamRole` | `userPass` (Data API `DbUser` + IAM) — secret binding: `secretAccessKey` / `sessionToken` / `secretArn`

## Scope

Runs SQL on the customer’s provisioned cluster **or** Serverless workgroup through the **Redshift Data API**, not the Postgres wire protocol. This node must not share a handler with PostgreSQL. SigV4-signed JSON calls to `https://redshift-data.{region}.amazonaws.com` with `Content-Type: application/x-amz-json-1.1` and `X-Amz-Target: RedshiftData.{Action}`. The customer owns the cluster/workgroup, IAM principal, and data. Database passwords are never sent as Data API fields; `userPass` means IAM temporary credentials **as** `DbUser` (`redshift:GetClusterCredentials`). Password-in-Secrets-Manager uses official `SecretArn`.

Required IAM on the caller (least privilege, not `*` in production): `redshift-data:ExecuteStatement`, `redshift-data:BatchExecuteStatement`, `redshift-data:DescribeStatement`, `redshift-data:GetStatementResult`, plus `redshift:GetClusterCredentials` / `redshift:GetClusterCredentialsWithIAM` or `redshift-serverless:GetCredentials` as applicable.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.sql` | POST | `X-Amz-Target: RedshiftData.ExecuteStatement` then `DescribeStatement` + `GetStatementResult` | Single SQL statement (DML or DDL). |
| `statement.describe` | POST | `X-Amz-Target: RedshiftData.DescribeStatement` | Status / stats for a statement `Id` returned earlier. |
| `table.insert` | POST | `ExecuteStatement` or `BatchExecuteStatement` | INSERT via parameterized SQL. Batch: up to 40 statements (`Sqls`). |

Do not call the Postgres port. `GetStatementResult` is internal after `execute.sql` / `table.insert` when status is `FINISHED`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.sql` | no | Chooses the row above |
| `auth` | select | yes | `awsKeys` | no | `awsKeys` \| `iamRole` \| `userPass` |
| `region` | string | yes | | no | AWS region of the Data API endpoint |
| `clusterId` | string | one of cluster/workgroup | | no | Provisioned `ClusterIdentifier` |
| `workgroup` | string | one of cluster/workgroup | | no | Serverless `WorkgroupName` or workgroup ARN |
| `database` | string | yes (except session reuse) | | no | Database name |
| `dbUser` | string | if `auth=userPass` on a cluster | | no | Data API `DbUser` (temp credentials as that DB user) |
| `accessKeyId` | string | if `auth=awsKeys` | | no | IAM access key |
| `secretAccessKey` | password | if `auth=awsKeys` | | yes | IAM secret key |
| `sessionToken` | password | no | | yes | STS session token when using temporary keys |
| `secretArn` | password | no | | yes | Secrets Manager ARN (`SecretArn`) instead of `DbUser` temp credentials |
| `requestTimeoutMs` | number | no | `60000` | no | Per HTTP call timeout |

Exactly one of `clusterId` or `workgroup` per call. Do not send both.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | SQL / statement id. No keys. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secret keys or `SecretArn` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = execute.sql`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | Single statement. Use named parameters — Data API `Parameters` `{ name, value }`. |
| `parameters` | array | no | `[{ "name": "id", "value": "1" }, …]` bound into the SQL |
| `statementName` | string | no | Optional label (max 2048) |
| `clientToken` | string | no | Idempotency token (1–64 chars) |
| `waitTimeSeconds` | number | no | 1–30: Data API wait before returning current status |
| `resultFormat` | string | no | `JSON` (default) or `CSV` |

### `operation = statement.describe`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | UUID from Execute/BatchExecute (`…:n` suffix for batch members) |
| `waitTimeSeconds` | number | no | 1–30 |

### `operation = table.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes (single) | Parameterized `INSERT …` |
| `sqls` | array | yes (batch) | 1–40 INSERT strings for `BatchExecuteStatement` |
| `parameters` | array | no | Shared across the batch when using `Sqls` (official: every parameter must be used by at least one statement) |
| `executionMode` | string | no | Batch only: `TRANSACTION` (default) or `AUTO_COMMIT` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Data API status: `SUBMITTED` \| `PICKED` \| `STARTED` \| `FINISHED` \| `ABORTED` \| `FAILED` |
| `id` | string | execute / insert | Statement UUID |
| `hasResultSet` | boolean | execute | Whether to fetch rows |
| `records` | array | execute when FINISHED | `GetStatementResult.Records` (cap pages) |
| `columnMetadata` | array | execute when FINISHED | Column types |
| `totalNumRows` | number | execute when FINISHED | Result size |
| `nextToken` | string | more pages | Pagination for `GetStatementResult` |
| `duration` | number | describe | Runtime stats when present |
| `error` | object | failure | `{ message, httpStatus, code }` — no AWS keys |

Handler for `execute.sql` / `table.insert`: call Execute/Batch, then poll `DescribeStatement` until `FINISHED`/`FAILED`/`ABORTED` (jitter, wall-clock cap), then `GetStatementResult` when `HasResultSet` and format is JSON.

## Legal

- AWS Customer Agreement / Service Terms: https://aws.amazon.com/service-terms/
- Data API guide: https://docs.aws.amazon.com/redshift/latest/mgmt/data-api.html
- ExecuteStatement: https://docs.aws.amazon.com/redshift-data/latest/APIReference/API_ExecuteStatement.html
- DescribeStatement: https://docs.aws.amazon.com/redshift-data/latest/APIReference/API_DescribeStatement.html
- BatchExecuteStatement: https://docs.aws.amazon.com/redshift-data/latest/APIReference/API_BatchExecuteStatement.html
- GetStatementResult: https://docs.aws.amazon.com/redshift-data/latest/APIReference/API_GetStatementResult.html
- Redshift and Data API usage is billed to the customer’s AWS account. **Unverified (2026-08-20)** exact Data API TPS quotas — confirm on AWS Service Quotas at implement time.
- SQL and results may be personal data. Residency follows the cluster/workgroup region.
- Official Data API only. No postgres:// scrape, no query-editor HTML.

## Implementation notes

- JDK HTTP + SigV4 (`service` = `redshift-data`). No AWS SDK required if SigV4 is already in the daemon HTTP stack; do not add a vendor SDK solely for this node.
- `iamRole`: use the daemon host / assumed-role credentials. `awsKeys`: static or session keys from secrets. Never log them.
- `userPass`: set `DbUser` + `ClusterIdentifier`/`Database`; IAM still signs the request. Do not send a database password field to ExecuteStatement.
- Cluster must be in a VPC (Data API requirement).
- Timeouts: connect 10s; `requestTimeoutMs` per call; poll Describe with `WaitTimeSeconds` ≤ 30 plus client backoff.
- Retry 429 / 5xx with jitter. Reuse `ClientToken` on Execute retry. Do not retry 400 `ValidationException`.
- Cap result download (default 8 MiB across `NextToken` pages).
- Errors → `{ error }`. Redact `Authorization`, keys, `SecretArn`.
- Do not log SQL or `Records`. Log statement `Id`, status, row counts, region.
