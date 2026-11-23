# AWS Secrets Manager

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/aws-secrets-manager/`
- **Researched:** 2026-08-20
- **Official API:** AWS Secrets Manager API version **2017-10-17**
- **Docs:** https://docs.aws.amazon.com/secretsmanager/latest/apireference/Welcome.html
- **Auth:** `awsAccessKey` or `iamRole` — secret bindings: `accessKeyId`, `secretAccessKey`, `sessionToken` (optional), `externalId` (optional). Config: `region`; optional KMS key on create/update.

## Scope

Create, read, update, delete, list, describe, and rotate secrets in one Region. **Retrieved secret values never appear on OUTPUT and never in logs.** On `get-secret`, the handler writes `SecretString` / `SecretBinary` into a StudioQuack secret binding and returns `secretRef` plus metadata (`ARN`, `Name`, `VersionId`, `VersionStages`, `CreatedDate`). Put/update **input** values come from SQ secret bindings, never plaintext in graph JSON.

IAM on the caller must be least privilege: `secretsmanager:GetSecretValue` (and `kms:Decrypt` if a CMK is used) scoped to named secret ARNs — not `Resource: "*"`.

**DO NOT BUILD:** `BatchGetSecretValue` (would dump many values into one dispatch), `GetRandomPassword`, resource-policy APIs, replica APIs, `RestoreSecret` (not in catalog). Do not add a plaintext `SecretString` config field.

## Wire protocol

[Welcome](https://docs.aws.amazon.com/secretsmanager/latest/apireference/Welcome.html) documents a Query API (`Action` + HTTPS GET/POST) **and** JSON request/response strings. Current **action pages** show JSON 1.1 (implement this — it matches samples and avoids URL size limits):

- URL: `https://secretsmanager.{region}.amazonaws.com/` (path `/`)
- Method: `POST`
- `Content-Type: application/x-amz-json-1.1`
- `X-Amz-Target: secretsmanager.{ActionName}` (e.g. `secretsmanager.GetSecretValue`)
- Body: single-line JSON (pretty JSON in docs is for humans)
- Auth: [Signature Version 4](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html) — service `secretsmanager`
- HTTPS required

JDK HTTP only. **No AWS SDK** — JSON + SigV4 is sufficient. Query `Action=` is acceptable only if a JSON call fails to document an edge; prefer the sample POST/JSON.

`iamRole`: STS Query `AssumeRole` (`Action=AssumeRole&Version=2011-06-15`) then sign Secrets Manager. Temporary credentials stay in memory and the redaction set.

Raw HTTP **must** send `ClientRequestToken` (UUID, 32–64 chars) on create / put / update / rotate when the SDK would have generated one.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `get-secret` | POST | `X-Amz-Target: secretsmanager.GetSecretValue` | Read value → SQ `secretRef` + metadata |
| `put-secret` | POST | `…CreateSecret` | Create a **new** named secret |
| `update-secret` | POST | `…UpdateSecret` | Metadata and/or new version of an existing secret |
| `delete-secret` | POST | `…DeleteSecret` | Schedule delete (recovery window) or force |
| `list-secrets` | POST | `…ListSecrets` | Metadata list (no values) |
| `rotate-secret` | POST | `…RotateSecret` | Configure and/or start rotation |
| `describe-secret` | POST | `…DescribeSecret` | Metadata only (no value) |

Action docs: [GetSecretValue](https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html), [CreateSecret](https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_CreateSecret.html), [UpdateSecret](https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_UpdateSecret.html), [DeleteSecret](https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_DeleteSecret.html), [ListSecrets](https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_ListSecrets.html), [RotateSecret](https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_RotateSecret.html), [DescribeSecret](https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_DescribeSecret.html).

`PutSecretValue` is **not** a catalog verb. v1 value writes on existing secrets use `UpdateSecret`. Do not expose `PutSecretValue` as a second operation. (AWS also documents PutSecretValue; using it internally for value-only updates is allowed if `UpdateSecret` is not needed for description/KMS — still one catalog op: `update-secret`.)

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `get-secret` | no | Row above |
| `auth` | select | yes | `awsAccessKey` | no | `awsAccessKey` or `iamRole` |
| `accessKeyId` | password | if `awsAccessKey` | | yes | Access key id |
| `secretAccessKey` | password | if `awsAccessKey` | | yes | Secret access key |
| `sessionToken` | password | no | | yes | STS session token |
| `roleArn` | string | if `iamRole` | | no | Role to assume |
| `roleSessionName` | string | no | `studioquack-secretsmanager` | no | STS session name |
| `externalId` | password | no | | yes | STS ExternalId |
| `region` | string | yes | | no | Secrets Manager Region |
| `kmsKeyId` | string | no | | no | Default CMK id/ARN/alias for create/update when INPUT omits `KmsKeyId` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secret **values** only as password/secret-binding fields |
| `output` | OUTPUT | object — `{ status, secretRef?, … }` or `{ error }` | **Never** `SecretString` or `SecretBinary` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = get-secret` → `GetSecretValue`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `SecretId` | string | yes | Name or ARN (ARN required for cross-account). 1–2048 |
| `VersionId` | string | no | Version UUID (32–64). Must match `VersionStage` if both set |
| `VersionStage` | string | no | e.g. `AWSCURRENT` (default), `AWSPREVIOUS`. 1–256 |
| `secretBindingName` | string | no | **SQ-only** (not an AWS parameter). Binding name for the retrieved value; default `aws-sm:{Name}:{VersionId}` |

### `operation = put-secret` → `CreateSecret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `Name` | string | yes | New secret name (1–512). Do not end with hyphen + six chars |
| `SecretString` | password | one of string/binary | SQ secret binding. JSON object recommended by AWS. Max 65536 |
| `SecretBinary` | password | one of string/binary | SQ binding; send as base64 per API. Not both with `SecretString` |
| `Description` | string | no | Max 2048 |
| `KmsKeyId` | string | no | Else config `kmsKeyId`; else `aws/secretsmanager` |
| `ClientRequestToken` | string | no | UUID; handler generates if omitted |
| `Tags` | array of `{ Key, Value }` | no | Requires `secretsmanager:TagResource` |
| `Type` | string | no | External-partner type string |
| `AddReplicaRegions` | array of `{ Region, KmsKeyId }` | no | Optional replicas (`secretsmanager:ReplicateSecretToRegions`) |
This node requires `SecretString` or `SecretBinary` (not both) so `put-secret` actually stores a value. AWS `CreateSecret` can omit both; do not omit them here.

### `operation = update-secret` → `UpdateSecret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `SecretId` | string | yes | Name or ARN |
| `Description` | string | no | New description |
| `KmsKeyId` | string | no | New CMK; empty string → `aws/secretsmanager` |
| `SecretString` | password | no | SQ binding; new version if set |
| `SecretBinary` | password | no | SQ binding; not both with `SecretString` |
| `ClientRequestToken` | string | no | Required on raw HTTP when creating a version; handler generates UUID |
| `Type` | string | no | External-partner type |

Do not call value updates more than once per 10 minutes sustained ([quotas](https://docs.aws.amazon.com/secretsmanager/latest/userguide/reference_limits.html)).

### `operation = delete-secret` → `DeleteSecret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `SecretId` | string | yes | Name or ARN |
| `RecoveryWindowInDays` | number | no | 7–30; default 30 if neither force nor window set |
| `ForceDeleteWithoutRecovery` | boolean | no | Permanent delete; **cannot** combine with `RecoveryWindowInDays` |

Prefer recovery window. Force-delete is irreversible.

### `operation = list-secrets` → `ListSecrets`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `MaxResults` | number | no | 1–100 |
| `NextToken` | string | no | Pagination |
| `Filters` | array of `{ Key, Values }` | no | Max 10. `Key`: `description` \| `name` \| `tag-key` \| `tag-value` \| `primary-region` \| `owning-service` \| `all`. `Values`: 1–10 strings (max 512); prefix `!` negates |
| `IncludePlannedDeletion` | boolean | no | Include secrets scheduled for deletion |
| `SortBy` | string | no | `created-date` \| `last-accessed-date` \| `last-changed-date` \| `name` |
| `SortOrder` | string | no | `asc` \| `desc` |

List is eventually consistent (~5 minutes). No secret values in this response.

### `operation = rotate-secret` → `RotateSecret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `SecretId` | string | yes | Name or ARN |
| `RotationLambdaARN` | string | no | Lambda rotator; omit for managed rotation |
| `RotationRules` | object | no | `AutomaticallyAfterDays`, `Duration`, `ScheduleExpression` |
| `RotateImmediately` | boolean | no | Default `true` if omitted by AWS |
| `ClientRequestToken` | string | no | Handler generates UUID |
| `ExternalSecretRotationRoleArn` | string | no | Partner-managed rotation role |
| `ExternalSecretRotationMetadata` | array of `{ Key, Value }` | no | Partner metadata |

Caller also needs `lambda:InvokeFunction` on the rotator when using Lambda rotation.

### `operation = describe-secret` → `DescribeSecret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `SecretId` | string | yes | Name or ARN |

## Output fields (by operation)

### `get-secret` — **strip value fields**

AWS returns `SecretString` and/or `SecretBinary`. The node **must not** copy them onto OUTPUT.

| Field | Type | When | Purpose |
|---|---|---|---|
| `ARN` | string | success | Secret ARN |
| `Name` | string | success | Friendly name |
| `VersionId` | string | success | Version id |
| `VersionStages` | array of string | success | Staging labels |
| `CreatedDate` | number | success | Version created |
| `secretRef` | string | success | SQ secret binding holding the value |
| `hasSecretString` | boolean | success | Whether AWS returned string (not the string) |
| `hasSecretBinary` | boolean | success | Whether AWS returned binary (not the bytes) |

Immediately add the raw `SecretString` / decoded `SecretBinary` to the redaction set, write the binding, drop locals.

### `put-secret`

| Field | Type | When | Purpose |
|---|---|---|---|
| `ARN` | string | success | New ARN (name + six random chars) |
| `Name` | string | success | Name |
| `VersionId` | string | success | Initial version |
| `ReplicationStatus` | array | if replicas | Replica statuses |

### `update-secret` / `rotate-secret`

| Field | Type | When | Purpose |
|---|---|---|---|
| `ARN` | string | success | ARN |
| `Name` | string | success | Name |
| `VersionId` | string | if new version | New version id |

### `delete-secret`

| Field | Type | When | Purpose |
|---|---|---|---|
| `ARN` | string | success | ARN |
| `Name` | string | success | Name |
| `DeletionDate` | number | success | When permanent delete may occur |

### `list-secrets`

| Field | Type | When | Purpose |
|---|---|---|---|
| `SecretList` | array | success | `SecretListEntry` metadata: `ARN`, `Name`, `Description`, `KmsKeyId`, `LastChangedDate`, `LastAccessedDate`, `LastRotatedDate`, `NextRotationDate`, `RotationEnabled`, `RotationLambdaARN`, `RotationRules`, `SecretVersionsToStages`, `Tags`, `OwningService`, `PrimaryRegion`, `DeletedDate`, `CreatedDate`, `Type`, `ExternalSecretRotationMetadata`, `ExternalSecretRotationRoleArn`. **No values** |
| `NextToken` | string | more pages | Pass back |

### `describe-secret`

| Field | Type | When | Purpose |
|---|---|---|---|
| `ARN` | string | success | ARN |
| `Name` | string | success | Name |
| `Description` | string | if set | Description |
| `KmsKeyId` | string | if CMK | Omitted for `aws/secretsmanager` |
| `CreatedDate` | number | success | Created |
| `DeletedDate` | number | if pending delete | Schedule |
| `LastAccessedDate` | number | if accessed | Last access in Region |
| `LastChangedDate` | number | if set | Last change |
| `LastRotatedDate` | number | if rotated | Last rotation |
| `NextRotationDate` | number | if rotation on | Next rotation bound |
| `RotationEnabled` | boolean | if ever configured | Rotation on/off |
| `RotationLambdaARN` | string | if set | Rotator |
| `RotationRules` | object | if set | Schedule |
| `VersionIdsToStages` | object | success | version id → labels |
| `Tags` | array | if set | Tags |
| `OwningService` | string | if service-managed | Owner service |
| `PrimaryRegion` | string | if set | Primary Region |
| `ReplicationStatus` | array | if replicas | Replica status |
| `Type` | string | if set | External type |
| `ExternalSecretRotationRoleArn` | string | if set | Partner role |
| `ExternalSecretRotationMetadata` | array | if set | Partner metadata |

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/)
- [AWS Service Terms](https://aws.amazon.com/service-terms/)
- [AWS Acceptable Use Policy](https://aws.amazon.com/aup/)
- [AWS Privacy Notice](https://aws.amazon.com/privacy/)
- Secret **values** are credentials / possibly PHI. StudioQuack custody: OS secret manager + encrypted store; REF-only engine vars; never plaintext in graph JSON, logs, or OUTPUT. Rotation of the SQ binding must not require redeploying the graph (stable refs).
- CloudTrail: AWS logs these APIs; do not put values in request fields that CloudTrail keeps (AWS marks `SecretString` / `SecretBinary` sensitive — still never log them on the daemon).
- **Rate limits** ([Secrets Manager quotas](https://docs.aws.amazon.com/secretsmanager/latest/userguide/reference_limits.html), per Region, not adjustable): `GetSecretValue` 10,000 rps; `DescribeSecret` 40,000 rps; `ListSecrets` 100 rps; `CreateSecret` 50 rps; `DeleteSecret` 50 rps; combined Put/Update/replica/version-stage 50 rps; combined Rotate/CancelRotate 50 rps. Secret value max 65,536 bytes; 500,000 secrets per Region; 100 versions per secret. Cross-account calls count against the **caller’s** account quota.
- Official-API-first.

## Implementation notes

- JDK HTTP + SigV4. No AWS SDK.
- Connect 10 s; read 30 s; cap body at **128 KiB** on OUTPUT paths after stripping values. In-memory AWS response for GetSecretValue must be dropped after binding write (value max 64 KiB).
- Retry 429 / `ThrottlingException` / `InternalServiceError` / 5xx max 3 with jitter. Do not retry `ResourceExistsException`, `ResourceNotFoundException`, `InvalidRequestException` (e.g. secret pending deletion), `DecryptionFailure`.
- Error mapping: 4xx/5xx → `{ error: { httpStatus, code, message, requestId } }`. Typical: `ResourceNotFoundException` 400, `InvalidParameterException` 400, `InvalidRequestException` 400, `DecryptionFailure` 400, `EncryptionFailure` 400, `LimitExceededException` 400, `ResourceExistsException` 400, `InternalServiceError` 500. Never include secret material in `error.message` if AWS echoed it — redact using the literal set.
- Log `ARN`, `Name`, `VersionId`, HTTP status, payload **sizes** — never values.
- UNVERIFIED APIs: none of the seven catalog-mapped actions. Catalog `put-secret` → `CreateSecret` (not `PutSecretValue`). Catalog ops otherwise 1:1 with the table above.
